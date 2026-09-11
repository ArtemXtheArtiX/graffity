package ru.yourname.client.mixin;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.yourname.client.GraffitiRenderer;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderWorld(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, net.minecraft.client.render.Camera camera, net.minecraft.client.render.GameRenderer gameRenderer, net.minecraft.client.render.LightmapTextureManager lightmapTextureManager, net.minecraft.client.render.VertexConsumerProvider.Immediate immediate, CallbackInfo ci) {
        GraffitiRenderer.renderAll(matrices, camera, immediate);
    }
}
