package com.dbzserver.dragonball;

import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;

/**
 * Minecraft のバージョン間で名前が変わった機能を、どの版でも動かすための橋渡し。
 */
public final class Compat {
	/** 無敵にするメソッド。26.1 では setInvulnerable、26.3 では setPermanentlyInvulnerable。 */
	private static final Method INVULNERABLE = findInvulnerable();

	private Compat() {
	}

	private static Method findInvulnerable() {
		for (String name : new String[]{"setPermanentlyInvulnerable", "setInvulnerable"}) {
			try {
				return Entity.class.getMethod(name, boolean.class);
			} catch (NoSuchMethodException ignored) {
				// 次の候補を試す
			}
		}
		DragonBallMod.LOGGER.warn("エンティティを無敵にするメソッドが見つかりませんでした。爆発などでボールが壊れる可能性があります (壊れても整合性チェックで復旧します)。");
		return null;
	}

	/** エンティティを無敵にする。メソッドが無い版では何もしない。 */
	public static void makeInvulnerable(Entity entity) {
		if (INVULNERABLE == null) {
			return;
		}
		try {
			INVULNERABLE.invoke(entity, true);
		} catch (ReflectiveOperationException e) {
			// 失敗しても致命的ではない
		}
	}
}
