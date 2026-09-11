package ru.yourname;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.UUID;

public class Graffiti {
	public final UUID uuid;
	public BlockPos pos;
	public Direction side;
	public String imagePath;
	public int blockSize;
	public int textureResolution;
	public long created;
	public int durationSec;
	public UUID ownerUUID;

	public Graffiti(BlockPos pos, Direction side, String imagePath, int blockSize, int textureResolution, int durationSec, UUID ownerUUID) {
		this.uuid = UUID.randomUUID();
		this.pos = pos;
		this.side = side;
		this.imagePath = imagePath;
		this.blockSize = blockSize;
		this.textureResolution = textureResolution;
		this.durationSec = durationSec;
		this.created = System.currentTimeMillis();
		this.ownerUUID = ownerUUID;
	}

	public boolean isExpired() {
		if (durationSec <= 0) return false;
		return (System.currentTimeMillis() - created) > (durationSec * 1000L);
	}
}
