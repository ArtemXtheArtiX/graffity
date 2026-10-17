package ru.yourname.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import ru.yourname.Graffiti;
import ru.yourname.GraffitiConfig;
import ru.yourname.GraffitiMod;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GraffitiManager {
    private static final Map<UUID, Graffiti> graffitiMap = new ConcurrentHashMap<>();
    private static final Map<UUID, Identifier> staticTextures = new ConcurrentHashMap<>();
    private static final Map<UUID, AnimatedGraffitiData> animatedData = new ConcurrentHashMap<>();

    public static void addGraffiti(UUID id, Graffiti g) {
        removeByOwner(g.ownerUUID);
        graffitiMap.put(id, g);
        loadTextureAsync(id, g);
    }

    /**
     * ИСПРАВЛЕНО: теперь ВСЁ декодирование и обработка изображения идут в фоновом потоке.
     * В клиентский поток передаются только готовые NativeImage для загрузки в GPU.
     * Раньше GIF декодировался целиком в клиентском потоке — отсюда фризы при установке.
     */
    private static void loadTextureAsync(UUID id, Graffiti g) {
        int targetRes = Math.max(64, Math.min(4096, g.textureResolution));
        Thread loader = new Thread(() -> {
            try {
                byte[] bytes = readAll(GraffitiImageLoader.openStream(g.imagePath));

                // Определяем GIF по сигнатуре, а не только по расширению (работает и для URL без расширения)
                if (GraffitiImageLoader.looksLikeGif(g.imagePath, bytes)) {
                    GraffitiImageLoader.GifData gif =
                            GraffitiImageLoader.decodeGif(new ByteArrayInputStream(bytes), targetRes);

                    GraffitiImageLoader.onClient(() -> {
                        if (!graffitiMap.containsKey(id)) {
                            // граффити снесли, пока грузилось — освобождаем кадры
                            for (NativeImage ni : gif.frames()) ni.close();
                            return;
                        }
                        List<Identifier> frames = new ArrayList<>();
                        for (int i = 0; i < gif.frames().size(); i++) {
                            Identifier loc = Identifier.of("graffity", "graffiti_gif_" + id + "_" + i);
                            GraffitiImageLoader.registerTexture(loc, gif.frames().get(i));
                            frames.add(loc);
                        }
                        animatedData.put(id, new AnimatedGraffitiData(frames, gif.delaysMs()));
                    });
                } else {
                    NativeImage img = GraffitiImageLoader.decodeStatic(new ByteArrayInputStream(bytes), targetRes);

                    GraffitiImageLoader.onClient(() -> {
                        if (!graffitiMap.containsKey(id)) {
                            img.close();
                            return;
                        }
                        Identifier loc = Identifier.of("graffity", "graffiti_" + id);
                        GraffitiImageLoader.registerTexture(loc, img);
                        staticTextures.put(id, loc);
                    });
                }
            } catch (Exception e) {
                GraffitiMod.LOGGER.error("Failed to load graffiti: " + g.imagePath, e);
            }
        }, "Graffity-ImageLoader");
        loader.setDaemon(true);
        loader.start();
    }

    private static byte[] readAll(InputStream is) throws Exception {
        try (is; ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(16384, is.available()))) {
            byte[] buf = new byte[65536];
            int n;
            long total = 0;
            while ((n = is.read(buf)) > 0) {
                baos.write(buf, 0, n);
                total += n;
                if (total > 64L * 1024 * 1024) throw new IllegalArgumentException("Image too large (>64MB)");
            }
            return baos.toByteArray();
        }
    }

    public static void removeByOwner(UUID owner) {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, Graffiti> entry : graffitiMap.entrySet()) {
            if (entry.getValue().ownerUUID.equals(owner)) toRemove.add(entry.getKey());
        }
        for (UUID id : toRemove) removeGraffiti(id);
    }

    public static void removeGraffiti(UUID id) {
        graffitiMap.remove(id);

        Identifier staticId = staticTextures.remove(id);
        if (staticId != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(staticId);

        AnimatedGraffitiData anim = animatedData.remove(id);
        if (anim != null) {
            for (Identifier loc : anim.frames) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(loc);
            }
        }
    }

    public static Map<UUID, Graffiti> getAll() { return graffitiMap; }

    public static void tick() {
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Graffiti> entry : graffitiMap.entrySet()) {
            if (entry.getValue().isExpired()) expired.add(entry.getKey());
        }
        for (UUID id : expired) removeGraffiti(id);
    }

    public static Identifier getTexture(UUID id) {
        AnimatedGraffitiData anim = animatedData.get(id);
        if (anim != null) {
            Identifier tex = anim.getCurrentTexture();
            if (tex != null) return tex;
        }
        return staticTextures.get(id);
    }
}
