package com.dbzserver.dragonball;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.item.ItemEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** MOD のエントリポイント。 */
public class DragonBallMod implements ModInitializer {
	public static final String MOD_ID = "dbzmod";
	public static final Logger LOGGER = LoggerFactory.getLogger("DragonBall");

	private static BallManager manager;

	/** サーバー起動中のみ非 null。 */
	public static BallManager manager() {
		return manager;
	}

	@Override
	public void onInitialize() {
		DbzConfig.load();
		ModItems.register();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DbzCommands.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			manager = new BallManager(server);
			manager.load();
			manager.ensureAll();
			LOGGER.info("ドラゴンボールMODを開始しました。");
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (manager != null) {
				manager.save();
				manager = null;
			}
			BallManager.clearTracked();
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (manager != null) {
				manager.tick();
			}
		});

		// 起動直後 (マネージャー生成前) に読み込まれるチェストも拾えるよう、常に登録する
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> BallManager.onBlockEntityLoad(blockEntity));
		ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> BallManager.onBlockEntityUnload(blockEntity));

		// ボールが落ちたとき、時間経過で消えないようにする
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity itemEntity && BallStacks.numberOf(itemEntity.getItem()) > 0) {
				itemEntity.setUnlimitedLifetime();
				Compat.makeInvulnerable(itemEntity);
			}
		});
	}
}
