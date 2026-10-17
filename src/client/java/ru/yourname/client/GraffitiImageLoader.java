package ru.yourname.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ru.yourname.GraffitiMod;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Единый качественный загрузчик изображений и GIF для граффити.
 *
 * Что улучшено по сравнению со старым кодом:
 *  - Правильная раскладка кадра GIF по offset-ам (старый код читал кадры "как есть",
 *    из-за чего у GIF с частичными кадрами съезжала картинка).
 *  - Корректная обработка disposal methods GIF (restore-to-background / previous) —
 *    больше нет "шлейфов" и чёрных квадратов на анимации.
 *  - Нормализация полностью прозрачного индекса палитры в каждом кадре.
 *  - Ресайз с high-quality сглаживанием (bilinear), вместо дефолтного грубого масштабирования.
 *  - Сохранение пропорций (letterbox по центру, прозрачные поля) без искажений.
 *  - Чтение задержек кадров из metadata с учётом delayTime=0 (такие кадры склеиваются).
 *  - Загрузка выполняется В ФОНОВОМ ПОТОКЕ, в клиент только передаются готовые NativeImage
 *    (старый GIF-код декодировал все кадры прямо в клиентском потоке — фризы при каждой установке).
 */
public final class GraffitiImageLoader {

    private static final AtomicLong ID_COUNTER = new AtomicLong();

    private GraffitiImageLoader() {}

    public static Identifier nextId(String prefix) {
        return Identifier.of("graffity", prefix + System.currentTimeMillis() + "_" + ID_COUNTER.incrementAndGet());
    }

    /** Открывает поток из файла или URL (http/https). Вызывать из фонового потока. */
    public static InputStream openStream(String pathOrUrl) throws Exception {
        String lower = pathOrUrl.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            URL url = new URL(pathOrUrl);
            // Ставим таймауты, чтобы загрузка "вечного" сервера не вешала поток навсегда
            var conn = url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            return conn.getInputStream();
        }
        return new java.io.FileInputStream(pathOrUrl);
    }

    public static boolean looksLikeGif(String pathOrUrl, byte[] headBytes) {
        if (pathOrUrl.toLowerCase(Locale.ROOT).endsWith(".gif")) return true;
        // Сигнатура GIF87a/GIF89a
        return headBytes.length >= 6
                && headBytes[0] == 'G' && headBytes[1] == 'I' && headBytes[2] == 'F'
                && headBytes[3] == '8';
    }

    // ==================== СТАТИЧНЫЕ ИЗОБРАЖЕНИЯ ====================

    /**
     * Декодирует статичное изображение (png/jpg/bmp/…) и готовит кадр заданного разрешения.
     * Потокобезопасно, вызывать только из фонового потока.
     */
    public static NativeImage decodeStatic(InputStream is, int targetRes) throws Exception {
        BufferedImage img = ImageIO.read(is);
        if (img == null) throw new IllegalArgumentException("Unsupported image format");
        return fitToCanvas(img, targetRes);
    }

    // ==================== GIF ====================

    public record GifData(List<NativeImage> frames, int[] delaysMs, int width, int height) {}

    /**
     * Полностью декодирует GIF в список готовых кадров. Тяёлую работу (декодирование,
     * ресайз, PNG-переупаковку) делает в вызывающем (фоновом) потоке.
     */
    public static GifData decodeGif(InputStream is, int targetRes) throws Exception {
        try (ImageInputStream iis = ImageIO.createImageInputStream(is)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) throw new IllegalArgumentException("No GIF reader available");
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);

                int logicalW = reader.getWidth(0);
                int logicalH = reader.getHeight(0);

                // Кумулятивный холст: кадры GIF часто хранятся как частичные обновления,
                // их нужно накладывать друг на друга с учётом disposal-политик.
                BufferedImage canvas = new BufferedImage(logicalW, logicalH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D cg = canvas.createGraphics();
                BufferedImage backup = null; // состояние для DISPOSE_PREVIOUS

                int numFrames;
                try {
                    numFrames = reader.getNumImages(true);
                } catch (Exception e) {
                    numFrames = reader.getNumImages(false);
                }
                if (numFrames <= 0) numFrames = 1;

                List<NativeImage> outFrames = new java.util.ArrayList<>();
                List<Integer> outDelays = new java.util.ArrayList<>();

                for (int i = 0; i < numFrames; i++) {
                    BufferedImage frame;
                    try {
                        frame = reader.read(i);
                    } catch (Exception e) {
                        GraffitiMod.LOGGER.warn("[Graffity] Skipped broken GIF frame " + i);
                        continue;
                    }
                    if (frame == null) continue;

                    int[] gce = readGce(reader, i); // {delayCs, disposeOp, transpIndex}
                    int delayMs = gce[0] * 10;
                    int disposeOp = gce[1];
                    int transpIndex = gce[2];

                    // Приводим к ARGB и чиним "прозрачный индекс палитры", который ImageIO
                    // иногда оставляет непрозрачным (чёрные/цветные артефакты по краям).
                    BufferedImage argb = toArgb(frame);
                    if (transpIndex >= 0) forceIndexTransparent(argb, frame, transpIndex);

                    int fw = frame.getWidth(), fh = frame.getHeight();
                    // Если кадр меньше логического экрана — размещаем его по offset из metadata
                    int ox = 0, oy = 0;
                    int[] offsets = readImageOffsets(reader, i);
                    if (offsets != null && (fw < logicalW || fh < logicalH)) {
                        ox = offsets[0];
                        oy = offsets[1];
                    } else if (fw > logicalW || fh > logicalH) {
                        // Некоторые кривые GIF имеют кадры крупнее логического размера
                        fw = Math.min(fw, logicalW);
                        fh = Math.min(fh, logicalH);
                    }

                    if (disposeOp == 3) { // restore to previous
                        if (backup == null) {
                            backup = new BufferedImage(logicalW, logicalH, BufferedImage.TYPE_INT_ARGB);
                            copyInto(canvas, backup, 0, 0);
                        }
                    }

                    // Рисуем кадр поверх накопленного состояния (Src — полная замена,
                    // с учётом альфы кадра; AlphaComposite принимает константы int)
                    cg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
                    cg.drawImage(argb, ox, oy, ox + fw, oy + fh, 0, 0, fw, fh, null);

                    // Снимаем готовый кадр на целевое разрешение
                    outFrames.add(fitToCanvas(canvas, targetRes));
                    outDelays.add(delayMs);

                    // Применяем disposal для следующего кадра
                    if (disposeOp == 2) { // restore to background (прозрачный)
                        cg.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
                        cg.fillRect(ox, oy, fw, fh);
                        cg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
                    } else if (disposeOp == 3 && backup != null) {
                        Graphics2D rg = canvas.createGraphics();
                        rg.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
                        rg.fillRect(0, 0, logicalW, logicalH);
                        rg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
                        rg.drawImage(backup, 0, 0, null);
                        rg.dispose();
                    }
                }
                cg.dispose();
                reader.dispose();

                if (outFrames.isEmpty()) throw new IllegalArgumentException("GIF has no decodable frames");

                // delay=0 означает "кадр-обновление", склеиваем с предыдущим временем
                int[] delays = new int[outDelays.size()];
                for (int i = 0; i < delays.length; i++) {
                    int d = outDelays.get(i);
                    if (d <= 0) d = i > 0 ? Math.max(delays[i - 1], 40) : 100;
                    delays[i] = Math.max(40, d); // защита от "мелькания"
                }
                return new GifData(outFrames, delays, logicalW, logicalH);
            } finally {
                try { reader.dispose(); } catch (Exception ignored) {}
            }
        }
    }

    /** Читает delay (в cs), disposal method и transparent color index из GIF metadata. */
    private static int[] readGce(ImageReader reader, int idx) {
        int delayCs = 10, dispose = 0, transp = -1;
        try {
            IIOMetadata meta = reader.getImageMetadata(idx);
            org.w3c.dom.Node tree = meta.getAsTree(meta.getNativeMetadataFormatName());
            org.w3c.dom.NodeList children = tree.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                org.w3c.dom.Node node = children.item(j);
                String name = node.getNodeName();
                if ("GraphicControlExtension".equals(name)) {
                    delayCs = intAttr(node, "delayTime", delayCs);
                    dispose = intAttr(node, "disposalMethod", dispose);
                    if (node.getAttributes().getNamedItem("transparentColorFlag") != null
                            && "true".equals(node.getAttributes().getNamedItem("transparentColorFlag").getNodeValue())) {
                        transp = intAttr(node, "transparentColorIndex", -1);
                    }
                }
            }
        } catch (Exception ignored) {}
        return new int[]{delayCs, dispose, transp};
    }

    private static int[] readImageOffsets(ImageReader reader, int idx) {
        try {
            IIOMetadata meta = reader.getImageMetadata(idx);
            org.w3c.dom.Node tree = meta.getAsTree(meta.getNativeMetadataFormatName());
            org.w3c.dom.NodeList children = tree.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                org.w3c.dom.Node node = children.item(j);
                if ("ImageDescriptor".equals(node.getNodeName())) {
                    return new int[]{
                            intAttr(node, "imageLeftPosition", 0),
                            intAttr(node, "imageTopPosition", 0)
                    };
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static int intAttr(org.w3c.dom.Node node, String attr, int def) {
        org.w3c.dom.NamedNodeMap map = node.getAttributes();
        if (map == null) return def;
        org.w3c.dom.Node a = map.getNamedItem(attr);
        if (a == null) return def;
        try {
            return Integer.parseInt(a.getNodeValue());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Делает указанный индекс палитры реально прозрачным (некоторые декодеры игнорируют GCE). */
    private static void forceIndexTransparent(BufferedImage argbFrame, BufferedImage rawFrame, int transpIndex) {
        if (!(rawFrame.getColorModel() instanceof java.awt.image.IndexColorModel icm)) return;
        if (transpIndex < 0 || transpIndex >= icm.getPaletteSize()) return;
        int rgb = icm.getRGB(transpIndex) & 0x00FFFFFF;
        int[] px = argbFrame.getRGB(0, 0, argbFrame.getWidth(), argbFrame.getHeight(), null, 0, argbFrame.getWidth());
        boolean changed = false;
        for (int i = 0; i < px.length; i++) {
            if ((px[i] & 0x00FFFFFF) == rgb && ((px[i] >>> 24) != 0)) {
                px[i] = px[i] & 0x00FFFFFF; // обнуляем альфу
                changed = true;
            }
        }
        if (changed) argbFrame.setRGB(0, 0, argbFrame.getWidth(), argbFrame.getHeight(), px, 0, argbFrame.getWidth());
    }

    // ==================== УТИЛИТЫ ОБРАБОТКИ ====================

    private static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        // Копируем пиксели 1:1, НЕ смешивая с фоном (SRC, а не SrcOver) — иначе
        // полупрозрачные пиксали GIF «въедаются» в чёрный фон.
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static void copyInto(BufferedImage from, BufferedImage to, int x, int y) {
        Graphics2D g = to.createGraphics();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.COPY));
        g.drawImage(from, x, y, null);
        g.dispose();
    }

    /**
     * Аккуратно вписывает изображение в квадрат targetRes×targetRes с сохранением
     * пропорций (high quality bilinear), прозрачные поля по краям.
     */
    public static NativeImage fitToCanvas(BufferedImage src, int targetRes) throws Exception {
        int w = src.getWidth(), h = src.getHeight();
        double scale = Math.min((double) targetRes / w, (double) targetRes / h);
        int drawW = Math.max(1, (int) Math.round(w * scale));
        int drawH = Math.max(1, (int) Math.round(h * scale));
        int offX = (targetRes - drawW) / 2;
        int offY = (targetRes - drawH) / 2;

        BufferedImage canvas = new BufferedImage(targetRes, targetRes, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));

        // Премотиплицирование альфы даёт чистый результат при последующей работе GPU
        g.setColor(new Color(0f, 0f, 0f, 0f));
        g.fillRect(0, 0, targetRes, targetRes);
        drawScaledPremultiplied(g, src, offX, offY, drawW, drawH);
        g.dispose();

        return toNativeImage(canvas);
    }

    private static void drawScaledPremultiplied(Graphics2D g, BufferedImage src, int x, int y, int w, int h) {
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.scale((double) w / src.getWidth(), (double) h / src.getHeight());
        g.drawImage(src, 0, 0, null);
        g.setTransform(old);
    }

    /** Конвертирует BufferedImage (ARGB_PRE) в NativeImage напрямую через пиксели, без PNG-раундтрипа. */
    public static NativeImage toNativeImage(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        NativeImage ni = new NativeImage(w, h, true);
        boolean pre = img.getColorModel().isAlphaPremultiplied();
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            img.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int argb = row[x];
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF, gg = (argb >> 8) & 0xFF, b = argb & 0xFF;
                if (pre && a != 0 && a != 255) { // обратно unmultiply
                    r = Math.min(255, r * 255 / a);
                    gg = Math.min(255, gg * 255 / a);
                    b = Math.min(255, b * 255 / a);
                }
                ni.setColor(x, y, (a << 24) | (r << 16) | (gg << 8) | b);
            }
        }
        return ni;
    }

    /** Регистрирует NativeImage как текстуру. Вызывать из клиентского потока! */
    public static Identifier registerTexture(Identifier id, NativeImage image) {
        MinecraftClient client = MinecraftClient.getInstance();
        NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "graffity", image);
        client.getTextureManager().registerTexture(id, tex);
        return id;
    }

    /** Безопасное выполнение задачи в клиентском потоке. */
    public static void onClient(Runnable r) {
        MinecraftClient.getInstance().execute(r);
    }

    /** Выполняет BiConsumer над результатом в клиентском потоке. */
    public static <T> void deliver(T value, BiConsumer<T, Throwable> handler) {
        onClient(() -> handler.accept(value, null));
    }
}
