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
		long now = System.currentTimeMillis();
		if (lastFrameTime == 0) lastFrameTime = now;
		int delay = delays[currentFrame];
		if (now - lastFrameTime >= delay) {
			currentFrame = (currentFrame + 1) % frames.size();
			lastFrameTime = now;
		}
		return frames.get(currentFrame);
	}
}
