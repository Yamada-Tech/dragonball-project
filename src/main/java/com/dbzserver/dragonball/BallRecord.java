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

	/** 連続して見つからなかった回数 (保存しない)。 */
	public transient int missing;

	public BallRecord() {
	}

	public BallRecord(int number) {
		this.number = number;
	}
}
