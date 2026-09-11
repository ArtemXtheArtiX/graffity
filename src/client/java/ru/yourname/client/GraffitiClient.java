package ru.yourname.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class GraffitiClient implements ClientModInitializer {
    public static KeyBinding keyOpenManager;
    public static final KeyBinding.Category CATEGORY = new KeyBinding.Category(Identifier.of("graffity", "category"));

    @Override
    public void onInitializeClient() {
        keyOpenManager = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.graffity.open_manager", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY
        ));

        WorldRenderEvents.LAST.register(GraffitiRenderer::onRenderWorldLast);

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
