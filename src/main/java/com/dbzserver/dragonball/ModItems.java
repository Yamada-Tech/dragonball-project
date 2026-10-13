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
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;

/** アイテムの登録。 */
public final class ModItems {
	public static final String[] BALL_NAMES = {"一星球", "二星球", "三星球", "四星球", "五星球", "六星球", "七星球"};

	public static final BallItem[] BALLS = new BallItem[7];
	public static RadarItem RADAR;
	/** ギャルのパンティ。頭に被れる装備 (視界が狭くなる)。ボールの管理・整合性チェックの対象外。 */
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
				.component(DataComponents.ITEM_NAME, Component.literal("ギャルのパンティ").withStyle(ChatFormatting.LIGHT_PURPLE))
				// 頭に被っている間だけ効く能力補正 (変態仮面のように、異常に強くなる)
				.attributes(pantiesModifiers())
				// 能力補正の説明文は、アイテムに表示しない
				.component(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true))
				// 頭に被れる。被ると顔にパンティが被さり (equipment/gal_panties.json)、
				// かぼちゃと同じく視界が狭くなる (textures/misc/pantiesblur.png)
				.component(DataComponents.EQUIPPABLE, Compat.headEquippable(
						pantiesId,
						Identifier.fromNamespaceAndPath(DragonBallMod.MOD_ID, "misc/pantiesblur")));
		GAL_PANTIES = Registry.register(BuiltInRegistries.ITEM, pantiesId, new SimplePolymerItem(pantiesProps));
	}

	/**
	 * ギャルのパンティを頭に被ったときの能力補正。設定ファイルの値 (0 の項目は付けない) から組み立てる。
	 * 頭の装備スロットにある間だけ効き、外すと元に戻る。
	 */
	private static ItemAttributeModifiers pantiesModifiers() {
		var builder = ItemAttributeModifiers.builder();
		EquipmentSlotGroup head = EquipmentSlotGroup.HEAD;

		if (DbzConfig.pantiesAttackDamage != 0) {
			builder.add(Attributes.ATTACK_DAMAGE, modifier("panties_attack_damage",
					DbzConfig.pantiesAttackDamage, AttributeModifier.Operation.ADD_VALUE), head);
		}
		if (DbzConfig.pantiesSpeedMultiplier != 0) {
			builder.add(Attributes.MOVEMENT_SPEED, modifier("panties_speed",
					DbzConfig.pantiesSpeedMultiplier, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), head);
		}
		if (DbzConfig.pantiesJumpStrength != 0) {
			builder.add(Attributes.JUMP_STRENGTH, modifier("panties_jump",
					DbzConfig.pantiesJumpStrength, AttributeModifier.Operation.ADD_VALUE), head);
		}
		if (DbzConfig.pantiesStepHeight != 0) {
			builder.add(Attributes.STEP_HEIGHT, modifier("panties_step_height",
					DbzConfig.pantiesStepHeight, AttributeModifier.Operation.ADD_VALUE), head);
		}
		if (DbzConfig.pantiesNoFallDamage) {
			// 落下ダメージの倍率を 1 → 0 にする (-100%)
			builder.add(Attributes.FALL_DAMAGE_MULTIPLIER, modifier("panties_no_fall_damage",
					-1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), head);
		}
		if (DbzConfig.pantiesMaxHealth != 0) {
			builder.add(Attributes.MAX_HEALTH, modifier("panties_max_health",
					DbzConfig.pantiesMaxHealth, AttributeModifier.Operation.ADD_VALUE), head);
		}
		if (DbzConfig.pantiesKnockbackResistance != 0) {
			builder.add(Attributes.KNOCKBACK_RESISTANCE, modifier("panties_knockback_resistance",
					DbzConfig.pantiesKnockbackResistance, AttributeModifier.Operation.ADD_VALUE), head);
		}
		if (DbzConfig.pantiesMiningSpeedMultiplier != 0) {
			builder.add(Attributes.BLOCK_BREAK_SPEED, modifier("panties_mining_speed",
					DbzConfig.pantiesMiningSpeedMultiplier, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), head);
		}
		return builder.build();
	}

	private static AttributeModifier modifier(String name, double amount, AttributeModifier.Operation operation) {
		return new AttributeModifier(Identifier.fromNamespaceAndPath(DragonBallMod.MOD_ID, name), amount, operation);
	}
}
