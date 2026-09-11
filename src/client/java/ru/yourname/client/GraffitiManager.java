package ru.yourname.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ru.yourname.Graffiti;
import ru.yourname.GraffitiMod;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

public class GraffitiManager {
	private static final Map<UUID, Graffiti> graffitiMap = new HashMap<>();
	private static final Map<UUID, Identifier> staticTextures = new HashMap<>();
	private static final Map<UUID, AnimatedGraffitiData> animatedData = new HashMap<>();

	public static void addGraffiti(UUID id, Graffiti g) {
		removeByOwner(g.ownerUUID);
		graffitiMap.put(id, g);
		loadTextureAsync(id, g);
	}

	private static void loadTextureAsync(UUID id, Graffiti g) {
		new Thread(() -> {
			try {
				InputStream is = g.imagePath.startsWith("http") ? new URL(g.imagePath).openStream() : new FileInputStream(g.imagePath);
				if (g.imagePath.toLowerCase().endsWith(".gif")) {
					loadGifAsync(id, g, is);
				} else {
					loadStaticAsync(id, g, is);
				}
			} catch (Exception e) {
				GraffitiMod.LOGGER.error("Failed to load graffiti: " + g.imagePath, e);
			}
		}).start();
	}

	private static void loadStaticAsync(UUID id, Graffiti g, InputStream is) throws Exception {
		NativeImage originalImg = NativeImage.read(is);
		int maxDim = Math.max(originalImg.getWidth(), originalImg.getHeight());
		int targetRes = Math.min(g.textureResolution, 1024);
		
		final NativeImage finalImg;
		if (maxDim > targetRes) {
			finalImg = resizeImage(originalImg, targetRes, targetRes);
			originalImg.close();
		} else {
			finalImg = originalImg;
		}

		MinecraftClient.getInstance().execute(() -> {
			NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "graffity", finalImg);
			Identifier loc = Identifier.of("graffity", "graffiti_" + id);
			MinecraftClient.getInstance().getTextureManager().registerTexture(loc, tex);
			staticTextures.put(id, loc);
		});
	}

	private static void loadGifAsync(UUID id, Graffiti g, InputStream is) throws Exception {
		ImageInputStream iis = ImageIO.createImageInputStream(is);
		ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
		reader.setInput(iis);
		int numFrames = reader.getNumImages(true);
		
		MinecraftClient.getInstance().execute(() -> {
			List<Identifier> frames = new ArrayList<>();
			int[] delays = new int[numFrames];
			int targetRes = Math.min(g.textureResolution, 1024);

			for (int i = 0; i < numFrames; i++) {
				try {
					BufferedImage bImg = reader.read(i);
					NativeImage originalImg = NativeImage.read(new ByteArrayOutputStream() {{
						ImageIO.write(bImg, "png", this);
					}}.toByteArray());

					NativeImage finalImg;
					int maxDim = Math.max(originalImg.getWidth(), originalImg.getHeight());
					if (maxDim > targetRes) {
						finalImg = resizeImage(originalImg, targetRes, targetRes);
						originalImg.close();
					} else {
						finalImg = originalImg;
					}

					NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "graffity", finalImg);
					Identifier loc = Identifier.of("graffity", "graffiti_gif_" + id + "_" + i);
					MinecraftClient.getInstance().getTextureManager().registerTexture(loc, tex);
					frames.add(loc);
					delays[i] = Math.max(50, reader.getDelay(i) * 10);
				} catch (Exception e) {
					GraffitiMod.LOGGER.warn("Skipped GIF frame " + i);
				}
			}
			reader.dispose();
			animatedData.put(id, new AnimatedGraffitiData(frames, delays));
		});
	}

	private static NativeImage resizeImage(NativeImage img, int maxW, int maxH) {
		float scaleX = (float) maxW / img.getWidth();
		float scaleY = (float) maxH / img.getHeight();
		float scale = Math.min(scaleX, scaleY);
		if (scale >= 1.0f) return img;

		int newW = (int) (img.getWidth() * scale);
		int newH = (int) (img.getHeight() * scale);
		NativeImage resized = new NativeImage(NativeImage.Format.RGBA, newW, newH, false);
		for (int y = 0; y < newH; y++) {
			for (int x = 0; x < newW; x++) {
				resized.setColorArgb(x, y, img.getColorArgb((int)(x / scale), (int)(y / scale)));
			}
		}
		return resized;
	}

	public static void removeByOwner(UUID owner) {
		List<UUID> toRemove = new ArrayList<>();
		for (Map.Entry<UUID, Graffiti> entry : graffitiMap.entrySet()) {
			if (entry.getValue().ownerUUID.equals(owner)) toRemove.add(entry.getKey());
		}
		for (UUID id : toRemove) removeGraffiti(id);
	}

	public static void removeGraffiti(UUID id) {
		Identifier staticId = staticTextures.remove(id);
		if (staticId != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(staticId);
		
		AnimatedGraffitiData anim = animatedData.remove(id);
		if (anim != null) {
			for (Identifier loc : anim.frames) {
				MinecraftClient.getInstance().getTextureManager().destroyTexture(loc);
			}
		}
		graffitiMap.remove(id);
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
		if (anim != null) return anim.getCurrentTexture();
		return staticTextures.get(id);
	}
}
