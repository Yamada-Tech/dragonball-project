package com.dbzserver.dragonball;

/** ボールが今どこにあるか。 */
public enum Kind {
	/** まだ配置されていない / 位置不明 */
	NONE("未配置"),
	/** チェスト・樽などのコンテナの中 */
	CONTAINER("コンテナ内"),
	/** 地面に落ちているアイテム */
	ITEM("落下アイテム"),
	/** モブ・額縁・トロッコ等が保持 */
	ENTITY("エンティティ保持"),
	/** プレイヤーの所持品 (エンダーチェスト含む) */
	PLAYER("プレイヤー所持");

	public final String label;

	Kind(String label) {
		this.label = label;
	}
}
