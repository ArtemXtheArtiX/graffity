package ru.yourname.client;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public class GraffitiRenderer {
    public static void onRenderWorldLast(WorldRenderContext context) {
        if (GraffitiManager.getAll().isEmpty()) return;

        MatrixStack matrices = context.matrices();
        Vec3d cameraPos = context.gameRenderer().getCamera().getCameraPos();
        VertexConsumerProvider consumers = context.consumers();

        for (var entry : GraffitiManager.getAll().entrySet()) {
            ru.yourname.Graffiti g = entry.getValue();
            if (g == null || g.isExpired()) continue;

            Identifier texture = GraffitiManager.getTexture(g.uuid);
            if (texture == null) continue;

            matrices.push();
            matrices.translate(g.pos.getX() - cameraPos.x, g.pos.getY() - cameraPos.y, g.pos.getZ() - cameraPos.z);
            
            // ВОССТАНОВЛЕНА ОРИГИНАЛЬНАЯ ЛОГИКА ПОВОРОТА И СМЕЩЕНИЯ
            applySideTransform(matrices, g.side);

            VertexConsumer vertexConsumer = consumers.getBuffer(RenderLayers.entityTranslucent(texture));
            float offset = (g.blockSize - 1) / 2.0f;
            float min = -offset;
            float max = 1.0f + offset;

            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), min, max, 0.0f).color(255, 255, 255, 255).texture(0.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(matrices.peek(), 0.0f, 0.0f, 1.0f);
            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), max, max, 0.0f).color(255, 255, 255, 255).texture(1.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(matrices.peek(), 0.0f, 0.0f, 1.0f);
            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), max, min, 0.0f).color(255, 255, 255, 255).texture(1.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(matrices.peek(), 0.0f, 0.0f, 1.0f);
            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), min, min, 0.0f).color(255, 255, 255, 255).texture(0.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(matrices.peek(), 0.0f, 0.0f, 1.0f);

            matrices.pop();
        }
    }

    private static void applySideTransform(MatrixStack matrices, Direction side) {
        float off = 0.001f; // Оригинальное значение
        switch (side) {
            case DOWN:
                matrices.translate(0.5, -off, 0.5);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
                break;
            case UP:
                matrices.translate(0.5, 1 + off, 0.5);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
                break;
            case NORTH:
                matrices.translate(0.5, 0.5, -off);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-180)); // Оригинально: rotate(-180, 0, 0, 1)
                break;
            case SOUTH:
                matrices.translate(0.5, 0.5, 1 + off);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180)); // Оригинально: rotate(180, 1, 0, 0)
                break;
            case WEST:
                matrices.translate(-off, 0.5, 0.5);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
                break;
            case EAST:
                matrices.translate(1 + off, 0.5, 0.5);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
                break;
        }
        // Сдвиг для центрирования (центр квада в центре блока)
        matrices.translate(-0.5, -0.5, 0);
    }
}
