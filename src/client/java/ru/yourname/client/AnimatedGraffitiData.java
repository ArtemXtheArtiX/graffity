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
        this.delays = delays;
    }

    public Identifier getCurrentTexture() {
        if (frames.isEmpty()) return null;
        
        long now = System.currentTimeMillis();
        if (lastFrameTime == 0) {
            lastFrameTime = now;
            return frames.get(currentFrame);
        }

        int delay = delays[currentFrame];
        if (delay <= 0) delay = 100;

        // ИСПРАВЛЕНО: предотвращаем накопление лагов и замедление гифки
        // Используем цикл while, чтобы "догнать" время при просадке FPS
        while (now - lastFrameTime >= delay) {
            lastFrameTime += delay;
            currentFrame = (currentFrame + 1) % frames.size();
        }
        
        return frames.get(currentFrame);
    }
}
