package ru.yourname.client;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ru.yourname.GraffitiMod;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;

public class GraffitiPreview {
	public int x, y, width, height;
	public String imageUrlOrPath;
	public boolean isGif;
	public float alpha = 1.0f;
	public float speed = 1.0f;
	
	public int originalWidth = 0, originalHeight = 0;
	public boolean keepAspect = false;
	private final boolean isPreview;

	private Identifier textureId;
	private boolean isLoaded = false;

	public GraffitiPreview(int x, int y, int width, int height, String urlOrPath, boolean isGif, boolean isPreview) {
		this.x = x; this.y = y; this.width = width; this.height = height;
		this.imageUrlOrPath = urlOrPath; 
		this.isGif = isGif;
		this.isPreview = isPreview;
		loadImageAsync();
	}

	private void loadImageAsync() {
		new Thread(() -> {
			try {
				InputStream is = imageUrlOrPath.startsWith("http") ? new URL(imageUrlOrPath).openStream() : new FileInputStream(imageUrlOrPath);
				// Для предпросмотра мы всегда грузим как статику, чтобы не грузить все кадры гифки
				loadStaticSafe(is);
				isLoaded = true;
			} catch (Exception e) { 
				GraffitiMod.LOGGER.error("Failed to load preview: " + imageUrlOrPath, e); 
			}
		}).start();
	}

	// ИСПРАВЛЕНО: Безопасная загрузка без ImageIO.write, которая вызывала "Bad PNG Signature"
	private void loadStaticSafe(InputStream is) throws Exception {
		BufferedImage bImg = ImageIO.read(is);
		if (bImg == null) return;

		originalWidth = bImg.getWidth();
		originalHeight = bImg.getHeight();
		
		int targetSize = 512; // Максимум для превью
		double scale = Math.min((double) targetSize / originalWidth, (double) targetSize / originalHeight);
		int drawW = (int) (originalWidth * scale);
		int drawH = (int) (originalHeight * scale);
		int offsetX = (targetSize - drawW) / 2;
		int offsetY = (targetSize - drawH) / 2;

		// Создаем пустой холст и рисуем пиксели напрямую. Это на 100% безопасно для любых JPG/GIF
		NativeImage canvas = new NativeImage(NativeImage.Format.RGBA, targetSize, targetSize, false);
		canvas.fillRect(0, 0, targetSize, targetSize, 0x00000000);

		for (int y = 0; y < drawH; y++) {
			for (int x = 0; x < drawW; x++) {
				int srcX = Math.min((int) (x / scale), originalWidth - 1);
				int srcY = Math.min((int) (y / scale), originalHeight - 1);
				int rgb = bImg.getRGB(srcX, srcY);
				canvas.setColorArgb(offsetX + x, offsetY + y, rgb);
			}
		}

		MinecraftClient.getInstance().execute(() -> {
			NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "graffity", canvas);
			textureId = Identifier.of("graffity", "preview_" + imageUrlOrPath.hashCode());
			MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, tex);
			
			if (keepAspect && originalWidth > 0 && originalHeight > 0) {
				float aspect = (float) originalWidth / originalHeight;
				if (aspect > 1) height = (int) (width / aspect);
				else width = (int) (height * aspect);
			}
		});
	}

	public void render(DrawContext context, boolean editMode) {
		if (!isLoaded || textureId == null) return;
		
		int color = ((int)(this.alpha * 255) << 24) | 0x00FFFFFF;
		int drawW = width, drawH = height;
		
		if (keepAspect && originalWidth > 0 && originalHeight > 0) {
			float scale = Math.min((float) width / originalWidth, (float) height / originalHeight);
			drawW = (int) (originalWidth * scale);
			drawH = (int) (originalHeight * scale);
		}

		context.drawTexture(RenderPipelines.GUI_TEXTURED, textureId, x, y, 0.0f, 0.0f, drawW, drawH, drawW, drawH, color);
	}

	public void cleanup() {
		if (textureId != null) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);
			textureId = null;
		}
	}
}
