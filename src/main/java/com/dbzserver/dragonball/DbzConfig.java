package com.dbzserver.dragonball;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
	/** ボールがふわふわ浮いている高さ (地面の上からの高さ。ブロック単位)。 */
	public static double floatHeight = 0.8;

	/** true にすると、マップ上に配置されている竜玉 (未入手のもの) が淡く光って見つけやすくなる。 */
	public static boolean ballGlow = true;

	// ---- レーダー ----
	public static int radarUpdateTicks = 20;
	public static int radarMaxRange = 0;

	// ---- 整合性チェック ----
	public static int auditIntervalTicks = 100;
	public static int lostGraceAudits = 3;

	// ---- ログ・告知 ----
	public static boolean logEvents = true;
	public static boolean broadcastPickup = false;

	// ---- 竜玉の襲撃イベント (浮遊ボールに触れると護衛が現れ、全滅で再触れて取得) ----
	public static boolean raidEnabled = true;
	/** 襲撃中に所持者が死亡した際、「近くに他のプレイヤーがいる」とみなす距離 (ブロック)。 */
	public static double raidDeathDropRadius = 32.0;
	/** 触れたと判定される距離 (ブロック) */
	public static double raidTriggerRadius = 1.2;
	/** 出す敵の数 (基準値。 scaling が有効なときは、取得済みの数に応じて増える) */
	public static int raidMinions = 6;
	/** 襲撃の敵がプレイヤーに気づくまで見せる距離 (ブロック)。この距離の内外の輪から突撃開始 */
	public static double raidSpawnRadius = 30.0;
	/** 襲撃中の敵がこの数以下になったら、残りの数をカウントして全員に知らせる。0 で無効。 */
	public static int raidCountdownThreshold = 3;
	/** 敵をプレイヤーから最低これだけ離して出現させる距離 (ブロック)。 */
	public static double raidMinSpawnDistance = 20.0;
	/** true にすると、護衛の敵は太陽の光で燃えない (火炎耐性を付与)。 */
	public static boolean raidPreventBurning = true;
	/** 出す敵のID (カンマ区切り。この中からランダムに選ばれる) */
	public static String raidEnemies = "minecraft:pillager,minecraft:vindicator,minecraft:ravager,minecraft:witch,minecraft:evoker";
	/** true にすると、取得済みの数に応じて「同じ強さの敵の数」が増える (敵自体は強くならない) */
	public static boolean raidScaling = true;
	/** 取得済みボール1個ごとに増える護衛の数 (基準値に加算) */
	public static int raidExtraMinionsPerBall = 2;
	/** 敵に騎乗 (ラヴェイジャーに騎乗するなど) をランダムで付与する確率 */
	public static double raidRandomMountChance = 0.2;
	/** 敵に近接武器 (剣・斧) をランダムで持たせる確率 */
	public static double raidRandomWeaponChance = 0.3;
	/** 騎乗に使うMob (この中のIDからランダム。空なら騎乗なし) */
	public static String raidMounts = "minecraft:ravager,minecraft:horse";
	/** ランダムで持たせる武器 (この中のIDからランダム。空なら武器なし) */
	public static String raidWeapons = "minecraft:iron_sword,minecraft:iron_axe,minecraft:diamond_sword";
	/** 襲撃の敵を1方向の集団として出現させる角度の幅 (度。0 なら全周バラバラ) */
	public static double raidFormationSpreadDegrees = 50.0;

	// ---- ギャルのパンティ (頭に被ったときの能力補正)。0 の項目は付かない。反映にはサーバーの再起動が必要 ----
	public static double pantiesAttackDamage = 10.0;
	public static double pantiesSpeedMultiplier = 1.5;
	public static double pantiesJumpStrength = 0.25;
	public static double pantiesStepHeight = 1.4;
	public static boolean pantiesNoFallDamage = true;
	public static double pantiesMaxHealth = 20.0;
	public static double pantiesKnockbackResistance = 1.0;
	public static double pantiesMiningSpeedMultiplier = 2.0;

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
			floatHeight = Math.max(0.0, Math.min(10.0, getDouble(p, "spawn.float_height", 0.8)));

			radarUpdateTicks = Math.max(1, getInt(p, "radar.update_interval_ticks", 20));
			radarMaxRange = Math.max(0, getInt(p, "radar.max_range_blocks", 0));

			ballGlow = getBool(p, "ball.glow", true);

			auditIntervalTicks = Math.max(20, getInt(p, "audit.interval_ticks", 100));
			lostGraceAudits = Math.max(1, getInt(p, "audit.lost_grace_audits", 3));

			logEvents = getBool(p, "log.events", true);
			broadcastPickup = getBool(p, "announce.pickup", false);

			raidEnabled = getBool(p, "raid.enabled", true);
			raidDeathDropRadius = Math.max(0.0, getDouble(p, "raid.death_drop_radius", 32.0));
			raidTriggerRadius = Math.max(0.5, getDouble(p, "raid.trigger_radius", 1.2));
			raidMinions = Math.max(1, Math.min(24, getInt(p, "raid.minions", 6)));
			raidSpawnRadius = Math.max(4.0, Math.min(64.0, getDouble(p, "raid.spawn_radius", 30.0)));
			raidCountdownThreshold = Math.max(0, Math.min(12, getInt(p, "raid.countdown_threshold", 3)));
			raidMinSpawnDistance = Math.max(0.0,
					Math.min(raidSpawnRadius - 1.0, getDouble(p, "raid.min_spawn_distance", 20.0)));
			raidPreventBurning = getBool(p, "raid.prevent_burning", true);
			String enemies = p.getProperty("raid.enemies");
			if (enemies == null || enemies.isBlank()) {
				enemies = "minecraft:pillager,minecraft:vindicator,minecraft:ravager,minecraft:witch,minecraft:evoker";
			}
			raidEnemies = enemies.trim();
			raidScaling = getBool(p, "raid.scaling", true);
			raidExtraMinionsPerBall = Math.max(0, Math.min(12, getInt(p, "raid.extra_minions_per_ball", 2)));
			raidRandomMountChance = Math.max(0.0, Math.min(1.0, getDouble(p, "raid.random_mount_chance", 0.2)));
			raidRandomWeaponChance = Math.max(0.0, Math.min(1.0, getDouble(p, "raid.random_weapon_chance", 0.3)));
			String mounts = p.getProperty("raid.mounts");
			raidMounts = (mounts == null) ? "minecraft:ravager,minecraft:horse" : mounts.trim();
			String weapons = p.getProperty("raid.weapons");
			raidWeapons = (weapons == null) ? "minecraft:iron_sword,minecraft:iron_axe,minecraft:diamond_sword"
					: weapons.trim();
			raidFormationSpreadDegrees = Math.max(0.0, Math.min(360.0,
					getDouble(p, "raid.formation_spread_degrees", 50.0)));

			pantiesAttackDamage = getDouble(p, "panties.attack_damage", 10.0);
			pantiesSpeedMultiplier = getDouble(p, "panties.speed_multiplier", 1.5);
			pantiesJumpStrength = getDouble(p, "panties.jump_strength", 0.25);
			pantiesStepHeight = getDouble(p, "panties.step_height", 1.4);
			pantiesNoFallDamage = getBool(p, "panties.no_fall_damage", true);
			pantiesMaxHealth = getDouble(p, "panties.max_health", 20.0);
			pantiesKnockbackResistance = getDouble(p, "panties.knockback_resistance", 1.0);
			pantiesMiningSpeedMultiplier = getDouble(p, "panties.mining_speed_multiplier", 2.0);

			// 古い設定ファイルには、パンティの項目が無い。説明つきで末尾に追記する
			if (!p.containsKey("panties.attack_damage")) {
				Files.writeString(file, PANTIES_SECTION, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
				DragonBallMod.LOGGER.info("設定ファイルに、ギャルのパンティの項目を追記しました: {}", file);
			}
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

	private static double getDouble(Properties p, String key, double def) {
		String v = p.getProperty(key);
		if (v == null || v.isBlank()) {
			return def;
		}
		try {
			return Double.parseDouble(v.trim());
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

	/** ギャルのパンティの能力補正の説明。初回の設定ファイル作成時と、古い設定ファイルへの追記に使う。 */
	private static final String PANTIES_SECTION = """


			# ----------------------------------------------------------
			#  ギャルのパンティ (頭に被ったときの能力補正)
			# ----------------------------------------------------------
			#  頭に被っている間だけ効き、外すと元に戻ります。アイテムの説明文には表示されません。
			#  ※ この項目の変更は、サーバーを再起動すると反映されます (/db reload では反映されません)。
			#  ※ 0 にすると、その効果は付きません。

			# 攻撃力に足す値。素手の攻撃力は 1 なので、10 なら素手で 11 (ネザライトの剣が 8)。
			panties.attack_damage=10

			# 移動速度の増加率。1.5 なら +150% (2.5 倍)。
			panties.speed_multiplier=1.5

			# ジャンプ力に足す値。バニラは約 1.25 ブロックの高さ。0.25 で約 3 ブロック。
			panties.jump_strength=0.25

			# 歩いて登れる段差の高さに足す値。バニラは 0.6。1.4 なら 2.0 ブロックの段差を、ジャンプせず登れる。
			panties.step_height=1.4

			# true にすると、落下ダメージを受けない。
			panties.no_fall_damage=true

			# 最大体力に足す値。20 なら +20 (ハート10個分)。
			panties.max_health=20

			# ノックバック耐性。1.0 で、殴られても全く吹っ飛ばない。
			panties.knockback_resistance=1.0

			# ブロックを掘る速さの増加率。2.0 なら +200% (3 倍)。
			panties.mining_speed_multiplier=2.0
			""";

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

			# ボールがふわふわ浮いている高さを決めます (配置場所の地面からの高さ。ブロック単位)。
			# 0.8 は、操作キャラクターの頭の高さくらいです。数値を大きくするともっと高い位置に浮きます。
			# ※ 変更の反映には /db reload のあと、/db respawn でボールを置き直してください。
			spawn.float_height=0.8


			# ----------------------------------------------------------
			#  配置中の竜玉の光
			# ----------------------------------------------------------

			# true にすると、マップ上に浮いている (未入手の) 竜玉が淡く光り、
			# 縁取りとパーティクルで見つけやすくなります。入手済みには効果はありません。
			ball.glow=true


			# ----------------------------------------------------------
			#  竜玉捜索機 (ドラゴンレーダー)
			# ----------------------------------------------------------

			# 捜索機が「一番近いボール」を再計算する間隔 (tick。20tick = 1秒)。
			# 小さいほど反応が早く、大きいほど軽くなります。
			radar.update_interval_ticks=20

			# 捜索機が反応する最大距離 (ブロック)。0 なら無制限です。
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


			# ----------------------------------------------------------
			#  竜玉の襲撃イベント
			# ----------------------------------------------------------
			#  浮いている竜玉に触れると、その竜玉を守る護衛 (敵) が現れます。
			#  護衛を全部倒すと、もう一度竜玉に触れて取得できるようになります。

			# true にすると襲撃イベントが有効になります。
			# false にすると、従来どおり触れるだけで取得できます。
			raid.enabled=true

			# 触れたと判定される距離 (ブロック)。
			raid.trigger_radius=1.2

			# 現れる護衛の数。
			raid.minions=6

			# 護衛の集団が出現する一番遠い距離 (ブロック)。プレイヤーからこの距離以内の隊列に出ます。
			raid.spawn_radius=30.0

			# 護衛をプレイヤーから最低これだけ離して出現させる距離 (ブロック)。
			# これより近くには出現しません。気づく前に遠くから突っ込んできます。
			# spawn_radius より大きくはできません。
			raid.min_spawn_distance=20.0

			# 護衛がこの数以下になったら、残り数をカウントして全員に知らせる (3 → 2 → 1 …)。
			# 0 にするとカウントしません。
			raid.countdown_threshold=3

			# true にすると、護衛の敵は太陽の光で燃えません (ゾンビ・スケルトンが昼間でも死なない)。
			raid.prevent_burning=true

			# 襲撃中に竜玉の所持者が死亡したとき、「近くに他のプレイヤーがいる」とみなす距離 (ブロック)。
			# 近くに誰かがいれば竜玉はその場にドロップ、誰もいなければ消失 (別の場所へ再配置) 扱いになります。
			raid.death_drop_radius=32.0

			# 護衛として出す敵 (カンマ区切りで、この中からランダムに選ばれます)。
			# 書式: <敵ID>[#baby][#charged][#rider:<敵ID>][#passenger:<MobID>][#effect:<効果ID>[:秒[:レベル]]]
			#   #baby      ... 赤ちゃん版
			#   #charged   ... 帯電クリーパー
			#   #rider:    ... その敵がこの敵の上に騎乗する (スケルトンホースマンなど)
			#   #passenger:... この敵がそのMobの上に騎乗する
			#   #effect:   ... モブ効果を掛けて出す (初期値: 60秒・レベル1)
			# 使えるID・効果ID・書き方の例の完全な一覧は、ゲーム内で /db enemies と入力すると見られます。
			raid.enemies=minecraft:pillager,minecraft:vindicator,minecraft:ravager,minecraft:witch,minecraft:evoker

			# 護衛にランダムで騎乗を付ける確率と、騎乗に使う Mob (空なら騎乗なし)。
			raid.random_mount_chance=0.2
			raid.mounts=minecraft:ravager,minecraft:horse

			# 護衛にランダムで武器 (剣・斧) を持たせる確率と、持たせる武器のID (空なら武器なし)。
			raid.random_weapon_chance=0.3
			raid.weapons=minecraft:iron_sword,minecraft:iron_axe,minecraft:diamond_sword

			# 護衛はプレイヤーの周りにバラバラと沸くのではなく、
			# 1方向へ向く幅◯度の扇の隊列で現れて、そのまま集団で突っ込んできます。
			# 360 にすると全方位にばらけて出現します。0 にするとほぼ1列の密集隊列。
			raid.formation_spread_degrees=50.0

			# ----------------------------------------------------------
			#  襲撃の難易度スケーリング
			# ----------------------------------------------------------
			#  敵そのものを強くするのではなく、「同じ強さの敵の数」を増やして強くします。
			#  true にすると、集めた竜玉の数に応じて護衛の数が増えます (1個目が最も弱い)。

			raid.scaling=true

			# 集めた竜玉1個ごとに増える護衛の数。
			# 例: minions=6, extra=2 なら 1個目=8体, 2個目=10体 ... 6個目=18体。
			raid.extra_minions_per_ball=2
			""" + PANTIES_SECTION;
}
