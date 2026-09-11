package ru.yourname.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import ru.yourname.Graffiti;
import ru.yourname.GraffitiConfig;

public class GraffitiClient implements ClientModInitializer {
    public static KeyBinding keyOpenManager;
    public static KeyBinding keyPlaceGraffiti;
    public static final KeyBinding.Category CATEGORY = new KeyBinding.Category(Identifier.of("graffity", "category"));

    @Override
    public void onInitializeClient() {
        // Клавиша для открытия меню (G)
        keyOpenManager = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.graffity.open_manager", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY
        ));

        // Клавиша для мгновенного нанесения граффити (V)
        keyPlaceGraffiti = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.graffity.place", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY
        ));

        WorldRenderEvents.END_MAIN.register(GraffitiRenderer::onRenderWorldLast);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != null) {
                GraffitiManager.tick();
            }
            
            if (client.currentScreen == null && client.player != null && client.world != null) {
                // 1. Открытие меню
                if (keyOpenManager.wasPressed()) {
                    client.setScreen(new GuiGraffitiManager());
                }
                
                // 2. Размещение граффити
                if (keyPlaceGraffiti.wasPressed()) {
                    if (GraffitiConfig.lastImagePath == null || GraffitiConfig.lastImagePath.trim().isEmpty()) {
                        return; 
                    }
                    
                    // Пускаем луч на 5 блоков
                    HitResult hit = client.player.raycast(5.0, 0.0f, false);
                    if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
                        Direction side = blockHit.getSide();
                        
                        Graffiti g = new Graffiti(
                            blockHit.getBlockPos(), 
                            side, 
                            GraffitiConfig.lastImagePath, 
                            GraffitiConfig.defaultBlockSize, 
                            GraffitiConfig.getTargetResolution(), // ИСПРАВЛЕНО: используем метод вычисления целевого разрешения
                            0, // 0 = бесконечное время жизни
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
