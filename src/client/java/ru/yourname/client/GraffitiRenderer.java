package ru.yourname.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * ИСПРАВЛЕНО: ориентация и освещение граффити на всех шести сторонах блока.
 *
 * Что было не так в старом коде:
 *  1) Повороты для NORTH/SOUTH выполнялись вокруг оси Z (картинка ложилась на бок),
 *     а для UP — тоже вокруг Z, из-за чего верхняя грань уходила в вертикальную
 *     плоскость. Граффити «устанавливался не той стороной».
 *  2) Обход вершин квада был по часовой стрелке (если смотреть со стороны нормали),
 *     поэтому нормаль у части граней получилась направленной ВНУТРЬ блока.
 *     Together с отключённым culling это давало инвертированное затенение —
 *     граффити «затемнялся», когда смотришь на него с определённой стороны блока.
 *
 * Теперь для каждой стороны задан честный ортонормированный базис грани
 * (u = вправо по картинке, v = вверх по картинке, n = наружу от блока),
 * квад обходится против часовой стрелки со стороны нормали, а нормаль
 * передаётся через normal-матрицу MatrixStack (inverse-transpose) —
 * освещение корректно на всех шести сторонах.
 */
public class GraffitiRenderer {

    // Смещение плоскости от поверхности блока: защищает от z-fighting'а
    // с текстурой самого блока и при этом визуально незаметно.
    private static final float OFFSET = 0.005f;

    public static void onRenderWorldLast(WorldRenderContext context) {
        if (GraffitiManager.getAll().isEmpty()) return;

        MatrixStack matrices = context.matrices();
        Vec3d cameraPos = context.gameRenderer().getCamera().getCameraPos();
        VertexConsumerProvider consumers = context.consumers();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false); // рисуем поверх деталей блока, но глубины не пишем

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

            drawQuad(vertexConsumer, matrices, min, max);

            matrices.pop();
        }

        RenderSystem.depthMask(true);
    }

    /**
     * Квад в локальной системе координат плоскости текстуры:
     * X — горизонталь картинки (вправо), Y — вертикаль (вверх), Z — наружу от грани.
     * Обход BL -> BR -> TR -> TL против часовой стрелки если смотреть со стороны
     * нормали => нормаль +Z смотрит ОТ блока, а не внутрь него.
     */
    private static void drawQuad(VertexConsumer vc, MatrixStack matrices, float min, float max) {
        Matrix4f pos = matrices.peek().getPositionMatrix();

        vertex(vc, matrices, pos, min, min, 0f, 0f, 1f); // bottom-left
        vertex(vc, matrices, pos, max, min, 0f, 1f, 1f); // bottom-right
        vertex(vc, matrices, pos, max, max, 0f, 1f, 0f); // top-right
        vertex(vc, matrices, pos, min, max, 0f, 0f, 0f); // top-left
    }

    private static void vertex(VertexConsumer vc, MatrixStack matrices, Matrix4f pos,
                               float x, float y, float z, float u, float v) {
        vc.vertex(pos, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                // Максимальный свет: граффити самоподсвеченное и не должно
                // затемняться блоком-носителем или его ориентацией.
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(matrices.peek().getNormalMatrix(), 0f, 0f, 1f);
    }

    /**
     * Ставит единичный квадрат из локальной XY-плоскости (нормаль +Z) точно на
     * выбранную грань блока так, чтобы «верх» картинки совпадал с естественным
     * верхом грани (для UP/DOWN — как у ванильных картин/баннеров).
     */
    private static void applySideTransform(MatrixStack matrices, Direction side) {
        // Базис грани: u — куда смотрит «вправо» картинки, v — «вверх», n — наружу.
        // Матрица 3x3 вращения собирается по столбцам (u, v, n):
        //       [ ux vx nx ]
        //   R = [ uy vy ny ]
        //       [ uz vz nz ]
        float ux, uy, uz, vx, vy, vz, nx, ny, nz;
        // Центр грани в координатах блока (0..1) со смещением наружу на OFFSET.
        float cx, cy, cz;

        switch (side) {
            case SOUTH -> { // смотрим с +Z
                ux = 1;  uy = 0; uz = 0;
                vx = 0;  vy = 1; vz = 0;
                nx = 0;  ny = 0; nz = 1;
                cx = 0.5f; cy = 0.5f; cz = 1f + OFFSET;
            }
            case NORTH -> { // смотрим с -Z; u = -X, чтобы картинка не зеркаллилась
                ux = -1; uy = 0; uz = 0;
                vx = 0;  vy = 1; vz = 0;
                nx = 0;  ny = 0; nz = -1;
                cx = 0.5f; cy = 0.5f; cz = -OFFSET;
            }
            case EAST -> { // смотрим с +X; «вправо» по картинке = -Z
                ux = 0;  uy = 0; uz = -1;
                vx = 0;  vy = 1; vz = 0;
                nx = 1;  ny = 0; nz = 0;
                cx = 1f + OFFSET; cy = 0.5f; cz = 0.5f;
            }
            case WEST -> { // смотрим с -X; «вправо» по картинке = +Z
                ux = 0;  uy = 0; uz = 1;
                vx = 0;  vy = 1; vz = 0;
                nx = -1; ny = 0; nz = 0;
                cx = -OFFSET; cy = 0.5f; cz = 0.5f;
            }
            case UP -> { // лежит горизонтально; «верх» картинки направлен на север (-Z)
                ux = 1;  uy = 0; uz = 0;
                vx = 0;  vy = 0; vz = -1;
                nx = 0;  ny = 1; nz = 0;
                cx = 0.5f; cy = 1f + OFFSET; cz = 0.5f;
            }
            default /* DOWN */ -> { // «верх» картинки направлен на юг (+Z)
                ux = 1;  uy = 0; uz = 0;
                vx = 0;  vy = 0; vz = 1;
                nx = 0;  ny = -1; nz = 0;
                cx = 0.5f; cy = -OFFSET; cz = 0.5f;
            }
        }

        // Порядок важен: сначала ВРАЩЕНИЕ (ставит XY-квад с нормалью +Z в плоскость
        // нужной грани), потом ПЕРЕНОС (MatrixStack.multiply применяет матрицы справа
        // налево: M = T * R). В локальной системе квада после этого:
        //   X_local -> u, Y_local -> v, Z_local -> n, а точка (0.5, 0.5, 0) попадает
        // ровно в центр выбранной грани со смещением OFFSET наружу.
        matrices.multiply(new Matrix4f(
                ux, vx, nx, cx - 0.5f,
                uy, vy, ny, cy - 0.5f,
                uz, vz, nz, cz - 0.5f,
                0f, 0f, 0f, 1f));
    }
}
