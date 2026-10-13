package com.dbzserver.dragonball;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.Equippable;

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

	/**
	 * 頭に装備できるコンポーネントを作る。
	 *
	 * @param assetId       装着時の見た目 (assets/&lt;名前空間&gt;/equipment/&lt;名前&gt;.json)
	 * @param cameraOverlay 装着中の視界オーバーレイ (かぼちゃと同じ仕組み。textures/ 以下のパス)
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Equippable headEquippable(Identifier assetId, Identifier cameraOverlay) {
		// 装備定義のキー。EquipmentAsset の型名に依存しないよう、raw 型で組み立てる
		ResourceKey registry = ResourceKey.createRegistryKey(Identifier.parse("minecraft:equipment_asset"));
		ResourceKey assetKey = ResourceKey.create(registry, assetId);

		var builder = Equippable.builder(EquipmentSlot.HEAD).setAsset(assetKey);

		// 視界オーバーレイの設定。メソッド名が版で違っても、ビルドが通るようリフレクションで呼ぶ
		try {
			Method setOverlay = builder.getClass().getMethod("setCameraOverlay", Identifier.class);
			setOverlay.invoke(builder, cameraOverlay);
		} catch (ReflectiveOperationException e) {
			DragonBallMod.LOGGER.warn("視界オーバーレイを設定できませんでした。被っても視界は狭くなりません: {}", e.toString());
		}
		return builder.build();
	}
}
