package ru.yourname.client;

import net.minecraft.util.Identifier;
import java.util.List;

public class AnimatedGraffitiData {
    public final List<Identifier> frames;
    private final int[] delays;
    private long lastFrameTime = 0;
    private int currentFrame = 0;

    public AnimatedGraffitiData(List<Identifier> frames, int[] delays) {
        this.frames = frames;
        // Кадров может быть меньше, чем задержек (сбойные кадры пропускались) — защищаемся
        this.delays = new int[frames.size()];
        for (int i = 0; i < this.delays.length; i++) {
            int d = (delays != null && i < delays.length) ? delays[i] : 100;
            this.delays[i] = Math.max(40, d);
        }
    }

    public Identifier getCurrentTexture() {
        if (frames.isEmpty()) return null;

        long now = System.currentTimeMillis();
        if (lastFrameTime == 0) {
            lastFrameTime = now;
            return frames.get(currentFrame);
        }

        // ИСПРАВЛЕНО: «догоняем» время не более чем на один полный цикл анимации.
        // Раньше после долгого фриза/сворачивания окна while-цикл мог крутиться
        // бесконечно долго (тысячи итераций), подвешивая рендер.
        long elapsed = now - lastFrameTime;
        long cycleMs = 0;
        for (int d : delays) cycleMs += d;
        if (cycleMs > 0 && elapsed > 4 * cycleMs) {
            lastFrameTime = now; // просто синхронизируемся, без перебора кадров
            return frames.get(currentFrame);
        }

        while (elapsed >= delays[currentFrame]) {
            elapsed -= delays[currentFrame];
            lastFrameTime += delays[currentFrame];
            currentFrame = (currentFrame + 1) % frames.size();
        }

        return frames.get(currentFrame);
    }
}
