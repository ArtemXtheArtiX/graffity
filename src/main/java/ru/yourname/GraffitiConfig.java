package ru.yourname;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;

public class GraffitiConfig {
	private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("graffity.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static String lastImagePath = "";
	public static int defaultBlockSize = 2;
	
	// Базовое разрешение (не меняется при переключении масштаба)
	public static int baseTextureResolution = 1024; 
	public static int scalePercent = 100; // 50 или 100

	public static void load() {
		if (!CONFIG_FILE.toFile().exists()) {
			save();
			return;
		}
		try (FileReader r = new FileReader(CONFIG_FILE.toFile())) {
			ConfigData data = GSON.fromJson(r, ConfigData.class);
			if (data != null) {
				lastImagePath = data.lastImagePath;
				defaultBlockSize = Math.max(1, Math.min(3, data.defaultBlockSize));
				baseTextureResolution = Math.max(128, Math.min(4096, data.baseTextureResolution));
				scalePercent = (data.scalePercent == 50) ? 50 : 100;
			}
		} catch (Exception e) {
			GraffitiMod.LOGGER.error("Failed to load config", e);
		}
	}

	public static void save() {
		try (FileWriter w = new FileWriter(CONFIG_FILE.toFile())) {
			GSON.toJson(new ConfigData(), w);
		} catch (Exception e) {
			GraffitiMod.LOGGER.error("Failed to save config", e);
		}
	}

	// Вычисляет разрешение НА ЛЕТУ, не сохраняя деградацию в конфиг
	public static int getTargetResolution() {
		int newRes = baseTextureResolution * scalePercent / 100;
		return Math.max(64, Math.min(4096, newRes));
	}

	private static class ConfigData {
		String lastImagePath = GraffitiConfig.lastImagePath;
		int defaultBlockSize = GraffitiConfig.defaultBlockSize;
		int baseTextureResolution = GraffitiConfig.baseTextureResolution;
		int scalePercent = GraffitiConfig.scalePercent;
	}
}
