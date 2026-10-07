package com.dbzserver.dragonball;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** レーダーが指す座標の計算。 */
public final class Radar {
	private Radar() {
	}

	/**
	 * プレイヤーにとっての目標座標。
	 * オーバーワールドで、追跡可能なボールがあれば最寄りのボール。
	 * 無い場合 (または他ディメンション) は、別ディメンションの座標を返して針をランダムに回転させる。
	 */
	public static GlobalPos targetFor(ServerPlayer player) {
		BallManager manager = DragonBallMod.manager();
		if (player != null && manager != null && player.level().dimension() == Level.OVERWORLD) {
			BlockPos best = manager.nearestRadarTarget(player.blockPosition(), DbzConfig.radarMaxRange);
			if (best != null) {
				return GlobalPos.of(Level.OVERWORLD, best);
			}
		}
		return decoy(player);
	}

	/** 「目標なし」を表す座標。プレイヤーとは別のディメンションにすると針が回り続ける。 */
	private static GlobalPos decoy(ServerPlayer player) {
		ResourceKey<Level> dim = (player != null && player.level().dimension() == Level.NETHER) ? Level.END : Level.NETHER;
		return GlobalPos.of(dim, BlockPos.ZERO);
	}
}
