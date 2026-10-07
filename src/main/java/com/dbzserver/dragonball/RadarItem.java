package com.dbzserver.dragonball;

import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;

import java.util.Optional;

/**
 * ドラゴンレーダー。
 * クライアントへ送るアイテムに「ロードストーン追跡コンポーネント」を付け、
 * 一番近いボールの座標を指させる。針の回転・32コマ表示はバニラクライアントが行う。
 */
public class RadarItem extends SimplePolymerItem {
	public RadarItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		ServerPlayer player = null;
		try {
			player = PolymerCommonUtils.getPlayer(context);
		} catch (Throwable ignored) {
			// プレイヤー不明の文脈 (クリエイティブ用の一覧生成など)。針がランダムに回る表示にする。
		}
		GlobalPos target = Radar.targetFor(player);
		out.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(target), false));
	}
}
