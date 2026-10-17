package ru.yourname.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import ru.yourname.GraffitiMod;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Превью изображения/гифки в GUI менеджера.
 *
 * ИСПРАВЛЕНО:
 *  - Превью гифок теперь реально анимированное (раньше рисовался только первый кадр,
 *    флаг isGif нигде не использовался). Кадры декодируются в фоновом потоке,
 *    в клиентский поток передаются только готовые NativeImage.
 *  - Единый качественный пайплин обработки с GraffitiManager (bilinear-ресайз,
 *    сохранение пропорций, корректные disposal-политики GIF).
 */
public class GraffitiPreview {
    private static final AtomicLong UID = new AtomicLong();

    public int x, y, width, height;
    public String imageUrlOrPath;
    public boolean isGif;
    public float alpha = 1.0f;
    public float speed = 1.0f;

    public int originalWidth = 0, originalHeight = 0;
    public boolean keepAspect = false;

    private final Identifier textureId;
    private volatile boolean isLoaded = false;
    private volatile boolean disposed = false;

    // Анимация (только для GIF)
    private AnimatedGraffitiData animation;
    private final List<Identifier> textureFrames = new ArrayList<>();

    public GraffitiPreview(int x, int y, int width, int height, String urlOrPath, boolean isGif, boolean isPreview) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        this.imageUrlOrPath = urlOrPath;
        this.isGif = isGif;
        this.textureId = Identifier.of("graffity", "preview_" + System.nanoTime() + "_" + UID.incrementAndGet());
        loadImageAsync();
    }

    private void loadImageAsync() {
        Thread t = new Thread(() -> {
            try {
                byte[] bytes = readAll(GraffitiImageLoader.openStream(imageUrlOrPath));
                if (bytes.length == 0) return;

                BufferedImage firstForSize = ImageIO.read(new ByteArrayInputStream(bytes));
                if (firstForSize != null) {
                    originalWidth = firstForSize.getWidth();
                    originalHeight = firstForSize.getHeight();
                }

                boolean gif = GraffitiImageLoader.looksLikeGif(imageUrlOrPath, bytes);
                final int previewRes = 512;

                if (gif) {
                    GraffitiImageLoader.GifData data =
                            GraffitiImageLoader.decodeGif(new ByteArrayInputStream(bytes), previewRes);
                    GraffitiImageLoader.onClient(() -> {
                        if (disposed) {
                            for (NativeImage ni : data.frames()) ni.close();
                            return;
                        }
                        List<Identifier> frames = new ArrayList<>();
                        for (int i = 0; i < data.frames().size(); i++) {
                            Identifier loc = Identifier.of("graffity", "preview_gif_" + textureId.getPath() + "_" + i);
                            GraffitiImageLoader.registerTexture(loc, data.frames().get(i));
                            frames.add(loc);
                        }
                        animation = new AnimatedGraffitiData(frames, data.delaysMs());
                        textureFrames.addAll(frames);
                        isLoaded = true;
                    });
                } else {
                    NativeImage img = GraffitiImageLoader.decodeStatic(new ByteArrayInputStream(bytes), previewRes);
                    GraffitiImageLoader.onClient(() -> {
                        if (disposed) { img.close(); return; }
                        GraffitiImageLoader.registerTexture(textureId, img);
                        textureFrames.add(textureId);
                        isLoaded = true;
                    });
                }
            } catch (Exception e) {
                GraffitiMod.LOGGER.error("Failed to load preview: " + imageUrlOrPath, e);
            }
        }, "Graffity-Preview");
        t.setDaemon(true);
        t.start();
    }

    private static byte[] readAll(InputStream is) throws Exception {
        try (is; ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
            return baos.toByteArray();
        }
    }

    public void render(DrawContext context, boolean editMode) {
        if (!isLoaded) return;

        Identifier tex = animation != null ? animation.getCurrentTexture() : textureId;
        if (tex == null) return;

        int color = ((int) (this.alpha * 255) << 24) | 0x00FFFFFF;
        int drawW = width, drawH = height;

        if (keepAspect && originalWidth > 0 && originalHeight > 0) {
            float scale = Math.min((float) width / originalWidth, (float) height / originalHeight);
            drawW = Math.max(1, (int) (originalWidth * scale));
            drawH = Math.max(1, (int) (originalHeight * scale));
        }

        context.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 0.0f, 0.0f, drawW, drawH, drawW, drawH, color);
    }

    public void cleanup() {
        disposed = true;
        MinecraftClient client = MinecraftClient.getInstance();
        for (Identifier id : textureFrames) client.getTextureManager().destroyTexture(id);
        textureFrames.clear();
        animation = null;
        isLoaded = false;
    }
}
