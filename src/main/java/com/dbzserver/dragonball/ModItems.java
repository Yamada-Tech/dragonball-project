package com.dbzserver.dragonball;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.DamageResistant;

/** アイテムの登録。 */
public final class ModItems {
	public static final String[] BALL_NAMES = {"一星球", "二星球", "三星球", "四星球", "五星球", "六星球", "七星球"};

	public static final BallItem[] BALLS = new BallItem[7];
	public static RadarItem RADAR;

	/** 炎・爆発などのダメージを受けないようにするためのタグ (data/dbzmod/tags/damage_type/ball_immune.json)。 */
	private static final TagKey<DamageType> BALL_IMMUNE =
			TagKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(DragonBallMod.MOD_ID, "ball_immune"));

	private ModItems() {
	}

	public static void register() {
		for (int i = 0; i < 7; i++) {
			final int number = i + 1;
			Identifier id = Identifier.fromNamespaceAndPath(DragonBallMod.MOD_ID, "dragonball_" + number);
			Item.Properties props = new Item.Properties()
					.setId(ResourceKey.create(Registries.ITEM, id))
					.stacksTo(1)
					.rarity(Rarity.EPIC)
					.component(DataComponents.DAMAGE_RESISTANT, new DamageResistant(BALL_IMMUNE))
					.component(DataComponents.ITEM_NAME, Component.literal(BALL_NAMES[i]).withStyle(ChatFormatting.GOLD));
			BALLS[i] = Registry.register(BuiltInRegistries.ITEM, id, new BallItem(props, number));
		}

		Identifier radarId = Identifier.fromNamespaceAndPath(DragonBallMod.MOD_ID, "dragon_radar");
		Item.Properties radarProps = new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, radarId))
				.stacksTo(1)
				.rarity(Rarity.RARE)
				.component(DataComponents.ITEM_NAME, Component.literal("ドラゴンレーダー").withStyle(ChatFormatting.AQUA));
		RADAR = Registry.register(BuiltInRegistries.ITEM, radarId, new RadarItem(radarProps));
	}
}
