package ru.yourname.graffiti;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class GraffitiClient implements ClientModInitializer {
	public static KeyBinding keyOpenManager;

	@Override
	public void onInitializeClient() {
		keyOpenManager = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.graffity.open_manager", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.graffity"
		));

		// Регистрация рендера граффити в мире
		WorldRenderEvents.LAST.register(GraffitiRenderer::onRenderWorldLast);

		// Тик для удаления просроченных граффити
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.world != null) {
				GraffitiManager.tick();
			}
			if (client.currentScreen == null && keyOpenManager.wasPressed()) {
				client.setScreen(new GuiGraffitiManager());
			}
		});
	}
}
