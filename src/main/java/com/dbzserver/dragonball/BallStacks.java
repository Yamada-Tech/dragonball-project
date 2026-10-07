package com.dbzserver.dragonball;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** ボールのアイテムスタックを扱うユーティリティ。 */
public final class BallStacks {
	private static final String SERIAL_KEY = "dbz_serial";

	private BallStacks() {
	}

	/** ボールの番号 (1〜7)。ボールでなければ 0。 */
	public static int numberOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		Item item = stack.getItem();
		for (int i = 0; i < ModItems.BALLS.length; i++) {
			if (ModItems.BALLS[i] == item) {
				return i + 1;
			}
		}
		return 0;
	}

	/** スタックに刻まれた個体ID。無ければ空文字。 */
	public static String serialOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return "";
		}
		return data.copyTag().getString(SERIAL_KEY).orElse("");
	}

	/** 個体IDつきのボールを1個作る。 */
	public static ItemStack create(int number, String serial) {
		ItemStack stack = new ItemStack(ModItems.BALLS[number - 1]);
		CompoundTag tag = new CompoundTag();
		tag.putString(SERIAL_KEY, serial);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}
}
