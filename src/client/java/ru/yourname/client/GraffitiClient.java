package ru.yourname.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;
import ru.yourname.Graffiti;
import ru.yourname.GraffitiConfig;

public class GraffitiClient implements ClientModInitializer {
    public static KeyBinding keyOpenManager;
    public static KeyBinding keyPlace; // Новая клавиша для быстрого нанесения
    public static final KeyBinding.Category CATEGORY = new KeyBinding.Category(Identifier.of("graffity", "category"));

    @Override
    public void onInitializeClient() {
        keyOpenManager = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.graffity.open_manager", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY
        ));
        
        // Клавиша V для быстрого нанесения граффити
        keyPlace = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.graffity.place", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY
        ));

        WorldRenderEvents.END_MAIN.register(GraffitiRenderer::onRenderWorldLast);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != null) {
                GraffitiManager.tick();
            }
            
            if (client.currentScreen == null && client.player != null) {
                // Открытие меню
                if (keyOpenManager.wasPressed()) {
                    client.setScreen(new GuiGraffitiManager());
                } 
                // Быстрое нанесение граффити
                else if (keyPlace.wasPressed()) {
                    BlockHitResult hit = (BlockHitResult) client.player.raycast(5.0, 0.0f, false);
                    if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                        Direction side = hit.getSide();
                        
                        // Создаем граффити с текущими настройками из конфига
                        Graffiti g = new Graffiti(
                            hit.getBlockPos(), 
                            side, 
                            GraffitiConfig.lastImagePath, 
                            GraffitiConfig.defaultBlockSize, 
                            GraffitiConfig.defaultTextureResolution, 
                            0, // 0 = навсегда
                            client.player.getUuid()
                        );
                        
                        GraffitiManager.addGraffiti(g.uuid, g);
                        GraffitiConfig.save();
                    }
                }
            }
        });
    }
}
