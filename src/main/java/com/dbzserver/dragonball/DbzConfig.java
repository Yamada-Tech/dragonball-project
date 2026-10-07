package com.dbzserver.dragonball;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 設定ファイル (config/dragonball.properties) の読み込み。
 * ファイルが無ければ、説明コメント付きの初期ファイルを自動生成します。
 * 変更後は、サーバー内で「/db reload」を実行すると再起動なしで反映されます。
 */
public final class DbzConfig {
	// ---- ボールの配置 ----
	public static int centerX = 0;
	public static int centerZ = 0;
	public static int minRadius = 500;
	public static int maxRadius = 5000;
	public static int minBallDistance = 300;
	public static int maxAttempts = 80;
	public static boolean avoidOcean = true;
	public static boolean avoidRiver = true;
	public static boolean waxedChest = true;

	// ---- レーダー ----
	public static int radarUpdateTicks = 20;
	public static int radarMaxRange = 0;

	// ---- 整合性チェック ----
	public static int auditIntervalTicks = 100;
	public static int lostGraceAudits = 3;

	// ---- ログ・告知 ----
	public static boolean logEvents = true;
	public static boolean broadcastPickup = false;

	private DbzConfig() {
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("dragonball.properties");
	}

	public static void load() {
		Path file = path();
		try {
			if (!Files.exists(file)) {
				Files.createDirectories(file.getParent());
				Files.writeString(file, DEFAULT_FILE, StandardCharsets.UTF_8);
				DragonBallMod.LOGGER.info("設定ファイルを新規作成しました: {}", file);
			}
			Properties p = new Properties();
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				p.load(r);
			}
			centerX = getInt(p, "spawn.center_x", 0);
			centerZ = getInt(p, "spawn.center_z", 0);
			minRadius = Math.max(0, getInt(p, "spawn.min_radius", 500));
			maxRadius = Math.max(1, getInt(p, "spawn.max_radius", 5000));
			if (maxRadius <= minRadius) {
				DragonBallMod.LOGGER.warn("spawn.max_radius が spawn.min_radius 以下です。max_radius を min_radius+1000 に補正します。");
				maxRadius = minRadius + 1000;
			}
			minBallDistance = Math.max(0, getInt(p, "spawn.min_distance_between_balls", 300));
			maxAttempts = Math.max(1, getInt(p, "spawn.max_attempts", 80));
			avoidOcean = getBool(p, "spawn.avoid_ocean", true);
			avoidRiver = getBool(p, "spawn.avoid_river", true);
			waxedChest = getBool(p, "spawn.waxed_chest", true);

			radarUpdateTicks = Math.max(1, getInt(p, "radar.update_interval_ticks", 20));
			radarMaxRange = Math.max(0, getInt(p, "radar.max_range_blocks", 0));

			auditIntervalTicks = Math.max(20, getInt(p, "audit.interval_ticks", 100));
			lostGraceAudits = Math.max(1, getInt(p, "audit.lost_grace_audits", 3));

			logEvents = getBool(p, "log.events", true);
			broadcastPickup = getBool(p, "announce.pickup", false);
		} catch (IOException e) {
			DragonBallMod.LOGGER.error("設定ファイルの読み込みに失敗しました。初期値を使います。", e);
		}
	}

	private static int getInt(Properties p, String key, int def) {
		String v = p.getProperty(key);
		if (v == null || v.isBlank()) {
			return def;
		}
		try {
			return Integer.parseInt(v.trim());
		} catch (NumberFormatException e) {
			DragonBallMod.LOGGER.warn("設定 {} の値 '{}' が数値ではありません。初期値 {} を使います。", key, v, def);
			return def;
		}
	}

	private static boolean getBool(Properties p, String key, boolean def) {
		String v = p.getProperty(key);
		if (v == null || v.isBlank()) {
			return def;
		}
		return Boolean.parseBoolean(v.trim());
	}

	private static final String DEFAULT_FILE = """
			# ==========================================================
			#  ドラゴンボールMOD 設定ファイル
			# ==========================================================
			#  ・「#」で始まる行はコメントです。
			#  ・数値や true/false を書き換えて保存し、ゲーム内で /db reload を実行すると反映されます。
			#  ・座標はすべて「ブロック」単位、オーバーワールドの座標です。
			# ==========================================================


			# ----------------------------------------------------------
			#  ボールの配置
			# ----------------------------------------------------------

			# ボールを配置する範囲の「中心」の座標 (X, Z)。
			# ワールドスポーン付近を中心にしたい場合は、スポーン地点の座標を入れてください。
			spawn.center_x=0
			spawn.center_z=0

			# 中心からの距離の下限 (これより近い場所には置かない)
			spawn.min_radius=500

			# 中心からの距離の上限 (これより遠い場所には置かない)
			# 例: min=500, max=5000 なら、中心から 500〜5000 ブロックの輪の中にランダムで置かれます。
			spawn.max_radius=5000

			# ボール同士の最低距離。近くに固まって置かれるのを防ぎます。
			spawn.min_distance_between_balls=300

			# 置ける場所が見つかるまで、ランダムな場所を探し直す最大回数。
			# 場所が見つからない場合は数値を増やすか、範囲を広げてください。
			spawn.max_attempts=80

			# true にすると、海のバイオームには置きません。
			spawn.avoid_ocean=true

			# true にすると、川のバイオームには置きません。
			spawn.avoid_river=true

			# true にすると「ワックス掛け済みの銅チェスト」を置きます (時間が経っても錆びません)。
			# false にすると普通の銅チェストになり、時間とともに色が変わっていきます。
			spawn.waxed_chest=true


			# ----------------------------------------------------------
			#  ドラゴンレーダー
			# ----------------------------------------------------------

			# レーダーが「一番近いボール」を再計算する間隔 (tick。20tick = 1秒)。
			# 小さいほど反応が早く、大きいほど軽くなります。
			radar.update_interval_ticks=20

			# レーダーが反応する最大距離 (ブロック)。0 なら無制限です。
			# 例: 3000 にすると、3000ブロック以内にボールがあるときだけ針が反応します。
			radar.max_range_blocks=0


			# ----------------------------------------------------------
			#  整合性チェック (ボールが消えたり増えたりしていないか確認する仕組み)
			# ----------------------------------------------------------

			# チェックを行う間隔 (tick。100tick = 5秒)。小さいほど早く検知しますが少し重くなります。
			audit.interval_ticks=100

			# ボールが見つからない状態が、何回連続のチェックで続いたら「消失」とみなすか。
			# 一時的に見えない状態 (移動中など) を誤判定しないための猶予です。
			audit.lost_grace_audits=3


			# ----------------------------------------------------------
			#  ログ・告知
			# ----------------------------------------------------------

			# true にすると、ボールの移動・入手・復旧などをサーバーログに記録します。
			log.events=true

			# true にすると、誰かがボールを入手したとき、全員にチャットで知らせます。
			announce.pickup=false
			""";
}
