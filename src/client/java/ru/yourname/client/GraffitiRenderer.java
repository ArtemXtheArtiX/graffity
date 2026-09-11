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
        float off = 0.001f;
        switch (side) {
            case DOWN:  // Пол: смотрим вниз, текстура должна смотреть ВВЕРХ (+Y)
                matrices.translate(0.5, -off, 0.5);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
                break;
            case UP:    // Потолок: смотрим вверх, текстура должна смотреть ВНИЗ (-Y)
                matrices.translate(0.5, 1 + off, 0.5);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
                break;
            case NORTH: // Север: смотрим на -Z
                matrices.translate(0.5, 0.5, -off);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
                break;
            case SOUTH: // Юг: смотрим на +Z (по умолчанию)
                matrices.translate(0.5, 0.5, 1 + off);
                break;
            case WEST:  // Запад: смотрим на -X
                matrices.translate(-off, 0.5, 0.5);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
                break;
            case EAST:  // Восток: смотрим на +X
                matrices.translate(1 + off, 0.5, 0.5);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90));
                break;
        }
        // Центрируем квад относительно блока
        matrices.translate(-0.5, -0.5, 0);
    }
}
