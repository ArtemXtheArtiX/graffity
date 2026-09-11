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
import java.awt.Graphics2D;
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
		BufferedImage bImg = ImageIO.read(is);
		if (bImg == null) return;

		// Принудительная конвертация в чистый ARGB
		BufferedImage cleanImg = new BufferedImage(bImg.getWidth(), bImg.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = cleanImg.createGraphics();
		graphics.drawImage(bImg, 0, 0, null);
		graphics.dispose();

		int targetRes = GraffitiConfig.getTargetResolution();
		double scale = Math.min((double) targetRes / cleanImg.getWidth(), (double) targetRes / cleanImg.getHeight());
		int drawW = (int) (cleanImg.getWidth() * scale);
		int drawH = (int) (cleanImg.getHeight() * scale);
		int offsetX = (targetRes - drawW) / 2;
		int offsetY = (targetRes - drawH) / 2;

		BufferedImage canvas = new BufferedImage(targetRes, targetRes, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		g.drawImage(cleanImg, offsetX, offsetY, drawW, drawH, null);
		g.dispose();

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(canvas, "png", baos);
		final NativeImage finalImg = NativeImage.read(baos.toByteArray());

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
					
					// Та же самая защита от артефактов для каждого кадра гифки
					BufferedImage cleanImg = new BufferedImage(bImg.getWidth(), bImg.getHeight(), BufferedImage.TYPE_INT_ARGB);
					Graphics2D graphics = cleanImg.createGraphics();
					graphics.drawImage(bImg, 0, 0, null);
					graphics.dispose();

					double scale = Math.min((double) targetRes / cleanImg.getWidth(), (double) targetRes / cleanImg.getHeight());
					int drawW = (int) (cleanImg.getWidth() * scale);
					int drawH = (int) (cleanImg.getHeight() * scale);
					int offsetX = (targetRes - drawW) / 2;
					int offsetY = (targetRes - drawH) / 2;

					BufferedImage canvas = new BufferedImage(targetRes, targetRes, BufferedImage.TYPE_INT_ARGB);
					Graphics2D g = canvas.createGraphics();
					g.drawImage(cleanImg, offsetX, offsetY, drawW, drawH, null);
					g.dispose();

					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					ImageIO.write(canvas, "png", baos);
					NativeImage finalImg = NativeImage.read(baos.toByteArray());

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
