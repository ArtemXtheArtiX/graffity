package ru.yourname.client;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ru.yourname.GraffitiMod;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class TextField {
	private static final int MAX_DIMENSION = 512;

	public int x, y, width, height;
	public String imageUrlOrPath;
	public boolean isGif;
	public float alpha = 1.0f;
	public float speed = 1.0f;
	
	public int originalWidth = 0, originalHeight = 0;
	public boolean keepAspect = false;
	private final boolean isPreview; // НОВОЕ: флаг режима предпросмотра

	private Identifier textureId;
	private List<Identifier> gifTextureIds;
	private List<Integer> frameDelays;
	private int currentFrame = 0;
	private long lastFrameTime = 0;
	private boolean isLoaded = false;

	// ОБНОВЛЕНО: добавлен параметр isPreview
	public TextField(int x, int y, int width, int height, String urlOrPath, boolean isGif, boolean isPreview) {
		this.x = x; this.y = y; this.width = width; this.height = height;
		this.imageUrlOrPath = urlOrPath; 
		this.isGif = isGif;
		this.isPreview = isPreview;
		loadImageAsync();
	}

	private void loadImageAsync() {
		new Thread(() -> {
			try {
				InputStream is = imageUrlOrPath.startsWith("http") ? new URL(imageUrlOrPath).openStream() : new java.io.FileInputStream(imageUrlOrPath);
				if (isGif) loadGif(is); else loadStatic(is);
				isLoaded = true;
			} catch (Exception e) { GraffitiMod.LOGGER.error("Failed to load: " + imageUrlOrPath, e); }
		}).start();
	}

	private void loadStatic(InputStream is) throws Exception {
		NativeImage originalImg = NativeImage.read(is);
		originalWidth = originalImg.getWidth();
		originalHeight = originalImg.getHeight();
		
		final NativeImage finalImg;
		if (originalImg.getWidth() > MAX_DIMENSION || originalImg.getHeight() > MAX_DIMENSION) {
			finalImg = resizeImage(originalImg, MAX_DIMENSION, MAX_DIMENSION);
			originalImg.close();
		} else {
			finalImg = originalImg;
		}
		MinecraftClient.getInstance().execute(() -> {
			NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "graffity", finalImg);
			textureId = Identifier.of("graffity", "preview_" + imageUrlOrPath.hashCode());
			MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, tex);
			if (keepAspect && originalWidth > 0 && originalHeight > 0) adjustSizeToAspect();
		});
	}

	private void loadGif(InputStream is) throws Exception {
		ImageInputStream iis = ImageIO.createImageInputStream(is);
		ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
		reader.setInput(iis);
		int numFrames = reader.getNumImages(true);
		
		MinecraftClient.getInstance().execute(() -> {
			try {
				if (isPreview) {
					// ОПТИМИЗАЦИЯ: для предпросмотра загружаем ТОЛЬКО первый кадр как статичную картинку
					java.awt.image.BufferedImage bImg = reader.read(0);
					originalWidth = bImg.getWidth();
					originalHeight = bImg.getHeight();
					
					NativeImage originalImg = NativeImage.read(new java.io.ByteArrayOutputStream() {{
						ImageIO.write(bImg, "png", this);
					}}.toByteArray());
					
					NativeImage finalImg;
					int maxDim = Math.max(originalImg.getWidth(), originalImg.getHeight());
					int targetRes = 512; // Ограничиваем предпросмотр 512px для максимальной производительности
					if (maxDim > targetRes) {
						finalImg = resizeImage(originalImg, targetRes, targetRes);
						originalImg.close();
					} else {
						finalImg = originalImg;
					}
					
					NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "graffity", finalImg);
					textureId = Identifier.of("graffity", "preview_" + imageUrlOrPath.hashCode());
					MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, tex);
					
					this.isGif = false; // Принудительно отключаем анимацию для предпросмотра
				} else {
					// Полная загрузка GIF для реального размещения в мире
					gifTextureIds = new ArrayList<>();
					frameDelays = new ArrayList<>();
					for (int i = 0; i < numFrames; i++) {
						java.awt.image.BufferedImage bImg = reader.read(i);
						if (i == 0) {
							originalWidth = bImg.getWidth();
							originalHeight = bImg.getHeight();
						}
						
						NativeImage originalImg = NativeImage.read(new java.io.ByteArrayOutputStream() {{
							ImageIO.write(bImg, "png", this);
						}}.toByteArray());

						NativeImage finalImg;
						int maxDim = Math.max(originalImg.getWidth(), originalImg.getHeight());
						int targetRes = 1024;
						if (maxDim > targetRes) {
							finalImg = resizeImage(originalImg, targetRes, targetRes);
							originalImg.close();
						} else {
							finalImg = originalImg;
						}

						NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "graffity", finalImg);
						Identifier loc = Identifier.of("graffity", "graffiti_gif_" + imageUrlOrPath.hashCode() + "_" + i);
						MinecraftClient.getInstance().getTextureManager().registerTexture(loc, tex);
						gifTextureIds.add(loc);
						
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
						frameDelays.add(Math.max(50, delay));
					}
				}
			} catch (Exception e) {
				GraffitiMod.LOGGER.warn("Failed to load GIF: " + imageUrlOrPath, e);
			} finally {
				reader.dispose();
			}
			if (keepAspect && originalWidth > 0 && originalHeight > 0) adjustSizeToAspect();
		});
	}

	private void adjustSizeToAspect() {
		float aspect = (float) originalWidth / originalHeight;
		if (aspect > 1) height = (int) (width / aspect);
		else width = (int) (height * aspect);
	}

	private NativeImage resizeImage(NativeImage img, int maxW, int maxH) {
		float scaleX = (float) maxW / img.getWidth();
		float scaleY = (float) maxH / img.getHeight();
		float scale = Math.min(scaleX, scaleY);
		if (scale >= 1.0f) return img;
		int newW = (int) (img.getWidth() * scale), newH = (int) (img.getHeight() * scale);
		NativeImage resized = new NativeImage(NativeImage.Format.RGBA, newW, newH, false);
		for (int y = 0; y < newH; y++) {
			for (int x = 0; x < newW; x++) {
				resized.setColorArgb(x, y, img.getColorArgb((int)(x / scale), (int)(y / scale)));
			}
		}
		return resized;
	}

	public void render(DrawContext context, boolean editMode) {
		if (!isLoaded) return;
		int color = ((int)(this.alpha * 255) << 24) | 0x00FFFFFF;
		int drawW = width, drawH = height;
		if (keepAspect && originalWidth > 0 && originalHeight > 0) {
			float scale = Math.min((float) width / originalWidth, (float) height / originalHeight);
			drawW = (int) (originalWidth * scale);
			drawH = (int) (originalHeight * scale);
		}

		if (isGif && gifTextureIds != null && !gifTextureIds.isEmpty()) {
			long now = System.currentTimeMillis();
			if (now - lastFrameTime >= (frameDelays.get(currentFrame) / speed)) {
				currentFrame = (currentFrame + 1) % gifTextureIds.size(); 
				lastFrameTime = now;
			}
			context.drawTexture(RenderPipelines.GUI_TEXTURED, gifTextureIds.get(currentFrame), x, y, 0.0f, 0.0f, drawW, drawH, drawW, drawH, color);
		} else if (textureId != null) {
			context.drawTexture(RenderPipelines.GUI_TEXTURED, textureId, x, y, 0.0f, 0.0f, drawW, drawH, drawW, drawH, color);
		}
	}

	public void cleanup() {
		gifTextureIds = null;
		textureId = null;
	}
}
