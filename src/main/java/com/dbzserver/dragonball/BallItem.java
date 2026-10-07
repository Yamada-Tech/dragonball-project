package com.dbzserver.dragonball;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.world.item.Item;

/**
 * ドラゴンボール。クライアントには「トライアルキー」+ カスタムアイテムモデルとして見える。
 * (ベースアイテムは Polymer 既定の TRIAL_KEY。クライアント側で特別な使い道がない無害なアイテム)
 */
public class BallItem extends SimplePolymerItem {
	public final int number;

	public BallItem(Item.Properties properties, int number) {
		super(properties);
		this.number = number;
	}

	/**
	 * シュルカーボックスやバンドルへの格納を禁止する (中身を追跡できなくなるため)。
	 * ゲームのバージョンによってこのメソッドが無い場合は、単に効果が無いだけ (@Override は付けない)。
	 */
	public boolean canFitInsideContainerItems() {
		return false;
	}
}
