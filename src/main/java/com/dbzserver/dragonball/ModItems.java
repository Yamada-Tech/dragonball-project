package com.dbzserver.dragonball;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** アイテムの登録。 */
public final class ModItems {
	public static final String[] BALL_NAMES = {"一星球", "二星球", "三星球", "四星球", "五星球", "六星球", "七星球"};

	public static final BallItem[] BALLS = new BallItem[7];
	public static RadarItem RADAR;
	/** ギャルのパンティ。見た目だけのアイテム (効果なし)。ボールの管理・整合性チェックの対象外。 */
	public static SimplePolymerItem GAL_PANTIES;

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
					.fireResistant()
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

		Identifier pantiesId = Identifier.fromNamespaceAndPath(DragonBallMod.MOD_ID, "gal_panties");
		Item.Properties pantiesProps = new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, pantiesId))
				.stacksTo(64)
				.component(DataComponents.ITEM_NAME, Component.literal("ギャルのパンティ").withStyle(ChatFormatting.LIGHT_PURPLE));
		GAL_PANTIES = Registry.register(BuiltInRegistries.ITEM, pantiesId, new SimplePolymerItem(pantiesProps));
	}
}
