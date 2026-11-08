package com.dbzserver.dragonball;

/** 1個のボールの台帳データ (JSON で保存される)。 */
public final class BallRecord {
	public int number;
	/** 現在有効な個体ID。これと違うIDのボールは「古い複製」として削除される。 */
	public String serial = "";
	public Kind kind = Kind.NONE;
	public String dimension = "minecraft:overworld";
	public double x;
	public double y;
	public double z;
	public String holder = "";
	public String holderName = "";

	/** 最初に置かれたチェストの場所 (/db return で戻す先)。 */
	public boolean hasHome;
	public String homeDimension = "minecraft:overworld";
	public int homeX;
	public int homeY;
	public int homeZ;

	/** まだワールドに置けていない (場所探し待ち)。 */
	public boolean pending;

	/** 護衛襲撃が進行中 (敵が生存している間はボールを取得できない)。 */
	public boolean raiding;
	/** 護衛の敵を全滅させた (もう触れると取得できる)。 */
	public boolean cleared;

	/**
	 * 死亡ドロップした竜玉のロスト期限 (その次元の gameTime)。0 なら設定なし。
	 * この時刻までに誰も拾わなかったら消失扱い (別の場所へ再配置) になる。
	 */
	public long lostDeadline;

	/**
	 * 過去に一度でもプレイヤーが入手したことがあるか。
	 * true の竜玉は、手放して再びワールドに浮いても襲撃イベントを発動させない
	 * (触れるだけで取得できる)。消失して再配置されてもこの扱いは続く。
	 */
	public boolean everCollected;

	/** 連続して見つからなかった回数 (保存しない)。 */
	public transient int missing;

	public BallRecord() {
	}

	public BallRecord(int number) {
		this.number = number;
	}
}
