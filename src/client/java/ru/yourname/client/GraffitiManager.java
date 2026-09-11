package ru.yourname.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ru.yourname.Graffiti;
import ru.yourname.GraffitiConfig;
import ru.yourname.GraffitiMod;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
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

	// ИСПРАВЛЕНО: Прямая запись пикселей в NativeImage. 
	// Это полностью устраняет "Bad PNG Signature", артефакты и проблемы с JPG/GIF конвертацией.
	private static NativeImage prepareImageDirect(BufferedImage original, int targetSize) {
		int w = original.getWidth();
		int h = original.getHeight();
		double scale = Math.min((double) targetSize / w, (double) targetSize / h);
		int drawW = (int) (w * scale);
		int drawH = (int) (h * scale);
		int offsetX = (targetSize - drawW) / 2;
		int offsetY = (targetSize - drawH) / 2;

		NativeImage canvas = new NativeImage(NativeImage.Format.RGBA, targetSize, targetSize, false);
		// Заполняем прозрачным фоном
		canvas.fillRect(0, 0, targetSize, targetSize, 0x00000000);

		for (int y = 0; y < drawH; y++) {
			for (int x = 0; x < drawW; x++) {
				int srcX = Math.min((int) (x / scale), w - 1);
				int srcY = Math.min((int) (y / scale), h - 1);
				int rgb = original.getRGB(srcX, srcY);
				
				int canvasX = offsetX + x;
				int canvasY = offsetY + y;
				canvas.setColorArgb(canvasX, canvasY, rgb);
			}
		}
		return canvas;
	}

	private static void loadStaticAsync(UUID id, Graffiti g, InputStream is) throws Exception {
		BufferedImage bImg = ImageIO.read(is);
		if (bImg == null) return;
		
		int targetRes = GraffitiConfig.getTargetResolution();
		final NativeImage finalImg = prepareImageDirect(bImg, targetRes);

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
			int targetRes = GraffitiConfig.getTargetResolution();

			for (int i = 0; i < numFrames; i++) {
				try {
					BufferedImage bImg = reader.read(i);
					// Используем тот же надежный метод прямой отрисовки
					NativeImage finalImg = prepareImageDirect(bImg, targetRes);

					NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "graffity", finalImg);
					Identifier loc = Identifier.of("graffity", "graffiti_gif_" + id + "_" + i);
					MinecraftClient.getInstance().getTextureManager().registerTexture(loc, tex);
					frames.add(loc);
					
					int delay = 100;
					try {
						javax.imageio.metadata.IIOMetadata meta = reader.getImageMetadata(i);
						if ("javax_imageio_gif_image_1.0".equals(meta.getNativeMetadataFormatName())) {
							org.w3c.dom.Node tree = meta.getAsTree(meta.getNativeMetadataFormatName());
							for (int j = 0; j < tree.getChildNodes().getLength(); j++) {
								org.w3c.dom.Node node = tree.getChildNodes().item(j);
								if ("GraphicControlExtension".equals(node.getNodeName())) {
									for (int k = 0; k < node.getChildNodes().getLength(); k++) {
										org.w3c.dom.Node attr = node.getChildNodes().item(k);
										if ("delayTime".equals(attr.getNodeName())) {
											delay = Integer.parseInt(attr.getAttributes().getNamedItem("value").getNodeValue()) * 10;
										}
									}
								}
							}
						}
					} catch (Exception e) {}
					delays[i] = Math.max(50, delay);
				} catch (Exception e) {
					GraffitiMod.LOGGER.warn("Skipped GIF frame " + i);
				}
			}
			reader.dispose();
			animatedData.put(id, new AnimatedGraffitiData(frames, delays));
		});
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
