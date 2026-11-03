package com.dbzserver.dragonball;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft のバージョン間で名前が変わった機能を、どの版でも動かすための橋渡し。
 */
public final class Compat {
	/** 無敵にするメソッド。26.1 では setInvulnerable、26.3 では setPermanentlyInvulnerable。 */
	private static final Method INVULNERABLE = findInvulnerable();
	/** 重力を無くすメソッド (名前は版で変わりうる)。 */
	private static final Method NO_GRAVITY = findNoGravity();
	/** 速度を設定するメソッド (名前は版で変わりうる)。 */
	private static final Method MOTION = findMotion();
	/** エンティティをワールドに追加するメソッド (名前は版で変わりうる)。 */
	private static final Method ADD_ENTITY = findAddEntity();

	private Compat() {
	}

	private static Method findInvulnerable() {
		for (String name : new String[]{"setPermanentlyInvulnerable", "setInvulnerable"}) {
			try {
				return Entity.class.getMethod(name, boolean.class);
			} catch (NoSuchMethodException ignored) {
				// 次の候補を試す
			}
		}
		DragonBallMod.LOGGER.warn("エンティティを無敵にするメソッドが見つかりませんでした。爆発などでボールが壊れる可能性があります (壊れても整合性チェックで復旧します)。");
		return null;
	}

	/** エンティティを無敵にする。メソッドが無い版では何もしない。 */
	public static void makeInvulnerable(Entity entity) {
		if (INVULNERABLE == null) {
			return;
		}
		try {
			INVULNERABLE.invoke(entity, true);
		} catch (ReflectiveOperationException e) {
			// 失敗しても致命的ではない
		}
	}

	private static Method findNoGravity() {
		for (String name : new String[]{"setNoGravity", "setNoGravityEnabled"}) {
			try {
				return Entity.class.getMethod(name, boolean.class);
			} catch (NoSuchMethodException ignored) {
				// 次の候補を試す
			}
		}
		try {
			// setGravity は意味が逆 (true = 重力あり)
			return Entity.class.getMethod("setGravity", boolean.class);
		} catch (NoSuchMethodException e) {
			DragonBallMod.LOGGER.warn("重力を制御するメソッドが見つかりませんでした。ボールが地面に落ちる可能性があります。");
			return null;
		}
	}

	private static Method findMotion() {
		for (String name : new String[]{"setDeltaMovement", "setVelocity", "setMotion"}) {
			try {
				return Entity.class.getMethod(name, Vec3.class);
			} catch (NoSuchMethodException ignored) {
				// 次の候補を試す
			}
		}
		DragonBallMod.LOGGER.warn("速度を設定するメソッドが見つかりませんでした。配置直後のボールが動く可能性があります。");
		return null;
	}

	/** エンティティを重力の影響を受けないようにする (ふわふわ浮かせる)。 */
	public static void makeFloat(Entity entity) {
		if (NO_GRAVITY == null) {
			return;
		}
		try {
			// setGravity は意味が逆なので false を渡す
			NO_GRAVITY.invoke(entity, !NO_GRAVITY.getName().equals("setGravity"));
		} catch (ReflectiveOperationException e) {
			// 失敗しても致命的ではない
		}
	}

	/** エンティティの速度を 0 にする。 */
	public static void stopMotion(Entity entity) {
		if (MOTION == null) {
			return;
		}
		try {
			MOTION.invoke(entity, Vec3.ZERO);
		} catch (ReflectiveOperationException e) {
			// 失敗しても致命的ではない
		}
	}

	private static Method findAddEntity() {
		for (String name : new String[]{"addFreshEntity", "addEntity"}) {
			try {
				return ServerLevel.class.getMethod(name, Entity.class);
			} catch (NoSuchMethodException ignored) {
				// 次の候補を試す
			}
		}
		DragonBallMod.LOGGER.error("エンティティをワールドに追加するメソッドが見つかりませんでした。ボールを配置できません。");
		return null;
	}

	/** エンティティをワールドに追加する。どちらの名前でもない版では失敗を返す。 */
	public static boolean spawnEntity(ServerLevel level, Entity entity) {
		if (ADD_ENTITY == null) {
			return false;
		}
		try {
			return (Boolean) ADD_ENTITY.invoke(level, entity);
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	// ------------------------------------------------------------------
	//  襲撃イベント: 敵のスポーン
	// ------------------------------------------------------------------

	/** 設定で使える敵ID (エンティティタイプの registry ID。クラス名には依存しない)。 */
	private static final List<String> ENEMY_IDS = List.of(
			"minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:zombie_villager",
			"minecraft:skeleton", "minecraft:stray", "minecraft:wither_skeleton", "minecraft:bogged",
			"minecraft:spider", "minecraft:cave_spider", "minecraft:creeper", "minecraft:witch",
			"minecraft:pillager", "minecraft:vindicator", "minecraft:evoker", "minecraft:ravager",
			"minecraft:vex", "minecraft:blaze", "minecraft:enderman", "minecraft:endermite",
			"minecraft:phantom", "minecraft:skeleton_horse", "minecraft:zombie_horse");

	/** 騎乗 (#rider: / #passenger:) に使えるMob。ENEMY_IDS のIDもそのまま使える。 */
	private static final List<String> EXTRA_MOUNT_IDS = List.of(
			"minecraft:chicken", "minecraft:horse", "minecraft:donkey", "minecraft:mule",
			"minecraft:wolf", "minecraft:cow", "minecraft:pig", "minecraft:sheep");

	/** ワールド内のプレイヤー一覧を返すメソッド (players / getPlayers の無引数版)。 */
	private static final Method PLAYERS = findPlayers();

	private static Method findPlayers() {
		for (String name : new String[]{"players", "getPlayers"}) {
			try {
				Method m = ServerLevel.class.getMethod(name);
				if (List.class.isAssignableFrom(m.getReturnType())) {
					return m;
				}
			} catch (NoSuchMethodException ignored) {
				// 次の候補を試す
			}
		}
		DragonBallMod.LOGGER.warn("プレイヤー一覧を取るメソッドが見つかりませんでした。襲撃の接近判定ができません。");
		return null;
	}

	/** ワールド内にいるプレイヤーの一覧 (メソッド名の版差異を吸収)。 */
	@SuppressWarnings("unchecked")
	public static List<ServerPlayer> players(ServerLevel level) {
		if (PLAYERS == null) {
			return List.of();
		}
		try {
			return (List<ServerPlayer>) PLAYERS.invoke(level);
		} catch (ReflectiveOperationException e) {
			DragonBallMod.LOGGER.warn("プレイヤー一覧を取れませんでした: {}", e.toString());
			return List.of();
		}
	}

	/** 位置を設定するメソッド (名前は版で変わりうる)。 */
	private static final Method SET_POS = findSetPos();

	private static Method findSetPos() {
		for (String name : new String[]{"setPosition", "setPos", "setLocation"}) {
			try {
				return Entity.class.getMethod(name, double.class, double.class, double.class);
			} catch (NoSuchMethodException ignored) {
				// 次の候補を試す
			}
		}
		DragonBallMod.LOGGER.warn("位置を設定するメソッドが見つかりませんでした。襲撃の敵を配置できません。");
		return null;
	}

	private static String normalizeId(String id) {
		id = id.trim().toLowerCase();
		return id.contains(":") ? id : "minecraft:" + id;
	}

	/**
	 * 設定の1項目を解析した結果。
	 * 書式: <敵ID>[#baby][#charged][#rider:<敵ID>][#passenger:<敵ID>][#effect:<効果ID>[:秒[:レベル]]]...
	 */
	private record EnemyEntry(String baseId, boolean baby, boolean charged, String riderId, String passengerId,
			List<String[]> effects) {
	}

	private static EnemyEntry parseSpec(String raw) {
		String[] parts = raw.trim().split("#");
		String baseId = normalizeId(parts[0]);
		boolean baby = false;
		boolean charged = false;
		String rider = null;
		String passenger = null;
		List<String[]> effects = new ArrayList<>();
		for (int i = 1; i < parts.length; i++) {
			String m = parts[i].trim();
			if (m.isEmpty()) {
				continue;
			}
			String lower = m.toLowerCase();
			if (lower.equals("baby")) {
				baby = true;
			} else if (lower.equals("charged")) {
				charged = true; // creeper を帯電状態にする
			} else if (lower.startsWith("rider:")) {
				rider = normalizeId(m.substring(6)); // この敵に騎乗する敵
			} else if (lower.startsWith("passenger:")) {
				passenger = normalizeId(m.substring(10)); // この敵が騎乗するMob
			} else if (lower.startsWith("effect:")) {
				effects.add(m.substring(7).trim().split(":"));
			} else {
				DragonBallMod.LOGGER.warn("raid.enemies の分からない修飾子です (無視しました): #{}", m);
			}
		}
		return new EnemyEntry(baseId, baby, charged, rider, passenger, effects);
	}

	/**
	 * 設定項目 (書式は enemyHelpLines() 参照) に対応する敵をスポーンさせる。
	 * 騎乗で追加された敵も含め、スポーンしたすべてのエンティティを spawnedOut に足す。
	 * 失敗時は null (spawnedOut には何も足さない)。
	 */
	public static Entity spawnEnemy(ServerLevel level, String spec, double x, double y, double z, Player target,
			List<Entity> spawnedOut) {
		EnemyEntry entry = parseSpec(spec);
		Entity main = spawnOne(level, entry.baseId, x, y, z, target, entry);
		if (main == null) {
			return null;
		}
		List<Entity> extra = new ArrayList<>();
		// #passenger:<Mob>: この敵が <Mob> の上に騎乗する (例: zombie#passenger:chicken = ニワトリに乗ったゾンビ)
		if (entry.passengerId != null) {
			Entity mount = spawnOne(level, entry.passengerId, x, y, z, target, null);
			if (mount != null) {
				addPassenger(mount, main);
				extra.add(mount);
			}
		}
		// #rider:<敵ID>: <敵ID>がこの敵の上に騎乗する (例: skeleton_horse#rider:skeleton = スケルトンホースマン)
		if (entry.riderId != null) {
			Entity rider = spawnOne(level, entry.riderId, x, y, z, target, null);
			if (rider != null) {
				addPassenger(main, rider);
				extra.add(rider);
			}
		}
		List<Entity> all = new ArrayList<>();
		all.add(main);
		all.addAll(extra);
		spawnedOut.addAll(all);
		return main;
	}

	/** 1体スポーンさせる。flags が null でない場合、baby / charged / effect を適用する。 */
	private static Entity spawnOne(ServerLevel level, String id, double x, double y, double z, Player target,
			EnemyEntry flags) {
		// クラス名は版でマッピングが変わりうるので使わない。registry の ID から EntityType を引く
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(Identifier.parse(id))
				.map(Holder::value).orElse(null);
		if (type == null) {
			DragonBallMod.LOGGER.warn("未知の敵IDです (スキップします): {}", id);
			return null;
		}
		try {
			Entity entity = type.create(level, EntitySpawnReason.TRIGGERED);
			if (entity == null) {
				DragonBallMod.LOGGER.warn("エンティティを作成できませんでした: {}", id);
				return null;
			}
			if (SET_POS != null) {
				SET_POS.invoke(entity, x, y, z);
			}
			if (entity instanceof Mob mob) {
				// チャンクアンロードで消えないようにする
				mob.setPersistenceRequired();
				if (target != null) {
					mob.setTarget(target);
				}
			}
			if (flags != null) {
				if (flags.baby()) {
					applyBaby(entity);
				}
				if (flags.charged()) {
					applyCharged(entity);
				}
				for (String[] eff : flags.effects()) {
					applyEffect(entity, eff);
				}
			}
			if (!spawnEntity(level, entity)) {
				return null;
			}
			return entity;
		} catch (ReflectiveOperationException | LinkageError e) {
			DragonBallMod.LOGGER.warn("敵をスポーンできませんでした ({}): {}", id, e.toString());
			return null;
		}
	}

	/** #baby: 赤ちゃん版にする。 */
	private static void applyBaby(Entity entity) {
		for (Method m : entity.getClass().getMethods()) {
			if (m.getName().equals("setBaby") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class) {
				try {
					m.invoke(entity, true);
				} catch (ReflectiveOperationException ignored) {
					// 赤ちゃん化できなくてもそのまま出す
				}
				return;
			}
		}
	}

	/** #charged: クリーパーを帯電状態にする。 */
	private static void applyCharged(Entity entity) {
		for (Method m : entity.getClass().getMethods()) {
			if (m.getName().equals("setPowered") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class) {
				try {
					m.invoke(entity, true);
				} catch (ReflectiveOperationException ignored) {
					// 帯電できなくてもそのまま出す
				}
				return;
			}
		}
	}

	/** #effect:<効果ID>[:秒[:レベル]]: モブ効果を掛ける (初期値: 60秒・レベル1)。 */
	private static void applyEffect(Entity entity, String[] segments) {
		if (segments.length < 1 || !(entity instanceof LivingEntity living)) {
			return;
		}
		// 効果IDは "minecraft:strength" のようにコロンを含む。
		// 「最初の数値」を境界として、それより前をID、後を秒・レベルとみなす。
		int numStart = segments.length;
		for (int i = 1; i < segments.length; i++) {
			try {
				Integer.parseInt(segments[i].trim());
				numStart = i;
				break;
			} catch (NumberFormatException ignored) {
				// まだIDの一部
			}
		}
		String effectId = String.join(":", java.util.Arrays.copyOfRange(segments, 0, numStart)).trim();
		MobEffect effect = BuiltInRegistries.MOB_EFFECT.getValue(Identifier.parse(normalizeId(effectId)));
		if (effect == null) {
			DragonBallMod.LOGGER.warn("raid.enemies の効果IDが分かりません (無視しました): {}", effectId);
			return;
		}
		int seconds = 60;
		int amplifier = 0;
		try {
			if (segments.length >= numStart + 1) {
				seconds = Integer.parseInt(segments[numStart].trim());
			}
			if (segments.length >= numStart + 2) {
				amplifier = Integer.parseInt(segments[numStart + 1].trim());
			}
		} catch (NumberFormatException e) {
			DragonBallMod.LOGGER.warn("raid.enemies の効果の数値が不正です (無視しました): {}", (Object) segments);
			return;
		}
		int ticks = Math.max(1, seconds) * 20;
		try {
			Object holder = resolveEffectHolder(effect, effectId);
			Object instance = null;
			// MobEffectInstance のコンストラクタは版で (Holder,...) と (MobEffect,...) が混在する
			for (Constructor<?> ctor : MobEffectInstance.class.getConstructors()) {
				Class<?>[] params = ctor.getParameterTypes();
				if (params.length == 3 && params[1] == int.class && params[2] == int.class) {
					if (params[0] == MobEffect.class) {
						instance = ctor.newInstance(effect, ticks, amplifier);
						break;
					}
					if (holder != null) {
						instance = ctor.newInstance(holder, ticks, amplifier);
						break;
					}
				}
			}
			if (instance == null) {
				DragonBallMod.LOGGER.warn("MobEffectInstance を作れませんでした (効果: {})", effectId);
				return;
			}
			Method addEffect = LivingEntity.class.getMethod("addEffect", MobEffectInstance.class);
			addEffect.invoke(living, instance);
		} catch (ReflectiveOperationException e) {
			DragonBallMod.LOGGER.warn("効果を適用できませんでした ({}) : {}", effectId, e.toString());
		}
	}

	/** 効果の Holder を取り出す (Registry のメソッド名は版で違う)。無ければ null。 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Object resolveEffectHolder(MobEffect effect, String effectId) {
		try {
			Registry registry = BuiltInRegistries.MOB_EFFECT;
			// getHolder(<ID型>) -> Optional<Holder> 版
			for (Method m : registry.getClass().getMethods()) {
				if (m.getName().equals("getHolder") && m.getParameterCount() == 1
						&& m.getParameterTypes()[0].isInstance(Identifier.parse(effectId))) {
					Object opt = m.invoke(registry, Identifier.parse(effectId));
					if (opt instanceof java.util.Optional<?> o) {
						return o.orElse(null);
					}
					return opt;
				}
			}
			// wrap / wrapAsHolder(<MobEffect>) 版
			for (Method m : registry.getClass().getMethods()) {
				if ((m.getName().equals("wrap") || m.getName().equals("wrapAsHolder"))
						&& m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(effect)) {
					return m.invoke(registry, effect);
				}
			}
		} catch (ReflectiveOperationException e) {
			DragonBallMod.LOGGER.warn("効果の Holder を取得できませんでした ({}): {}", effectId, e.toString());
		}
		return null;
	}

	/** a に b を騎乗させる (新版では protected なので setAccessible で呼ぶ)。 */
	private static void addPassenger(Entity vehicle, Entity rider) {
		try {
			Method m = Entity.class.getDeclaredMethod("addPassenger", Entity.class);
			m.setAccessible(true);
			m.invoke(vehicle, rider);
		} catch (ReflectiveOperationException | RuntimeException e) {
			DragonBallMod.LOGGER.warn("騎乗を設定できませんでした: {}", e.toString());
		}
	}

	/** 使えるモブ効果IDの一覧 (Registry のメソッド名は版で違うので検索する)。 */
	private static String effectIdList() {
		List<String> effectIds = new ArrayList<>();
		for (String name : new String[]{"holderIds", "keySet", "getKeys", "ids"}) {
			try {
				Method m = BuiltInRegistries.MOB_EFFECT.getClass().getMethod(name);
				Object result = m.invoke(BuiltInRegistries.MOB_EFFECT);
				if (result instanceof Iterable<?> it) {
					for (Object o : it) {
						if (o != null) {
							effectIds.add(o.toString());
						}
					}
					break;
				}
			} catch (ReflectiveOperationException ignored) {
				// 次の候補を試す
			}
		}
		java.util.Collections.sort(effectIds);
		if (effectIds.isEmpty()) {
			return "このバージョンでは一覧を取得できませんでした。minecraft:speed, minecraft:strength などの名前で試してください";
		}
		return String.join(", ", effectIds);
	}

	// ------------------------------------------------------------------
	//  取得遅延 (ItemEntity のメソッド名・フィールド名は版で違う)
	// ------------------------------------------------------------------

	/** 取得遅延を設定するメソッド (無ければ null)。 */
	private static final Method PICKUP_DELAY_SET = findPickupDelaySet();
	/** メソッドが無い版向けのフィールド。 */
	private static final Field PICKUP_DELAY_FIELD = findPickupDelayField();

	private static Method findPickupDelaySet() {
		// 26.x では setPickUpDelay (U が大文字)。旧名の候補も一応残す
		for (String name : new String[]{"setPickUpDelay", "setPickupDelay", "setPickupDelayTicks", "setPickupTime"}) {
			try {
				return ItemEntity.class.getMethod(name, int.class);
			} catch (NoSuchMethodException ignored) {
				// 次の候補を試す
			}
		}
		DragonBallMod.LOGGER.warn("取得遅延を設定するメソッドが見つかりませんでした。襲撃中の竜玉を触れても取得できてしまうことがあります。");
		return null;
	}

	private static Field findPickupDelayField() {
		for (String name : new String[]{"pickupDelay", "pickupDelayTicks", "pickupTime"}) {
			try {
				Field f = ItemEntity.class.getDeclaredField(name);
				f.setAccessible(true);
				return f;
			} catch (NoSuchFieldException ignored) {
				// 次の候補を試す
			}
		}
		return null;
	}

	/** 浮遊ボールの取得遅延を設定する。どちらにも対応できない版では何もしない。 */
	public static void setPickupDelay(ItemEntity item, int ticks) {
		if (PICKUP_DELAY_SET != null) {
			try {
				PICKUP_DELAY_SET.invoke(item, ticks);
			} catch (ReflectiveOperationException ignored) {
				// 何もしない
			}
			return;
		}
		if (PICKUP_DELAY_FIELD != null) {
			try {
				PICKUP_DELAY_FIELD.setInt(item, ticks);
			} catch (ReflectiveOperationException | IllegalArgumentException ignored) {
				// 何もしない
			}
		}
	}

	/** raid.enemies に書ける内容の一覧 (設定ファイルのコメント・ /db enemies 用)。 */
	public static List<String> enemyHelpLines() {
		List<String> out = new ArrayList<>();
		out.add("【基本の敵ID】");
		out.add(String.join(", ", ENEMY_IDS));
		out.add("【騎乗に追加で使えるID (敵ではないMob)】");
		out.add(String.join(", ", EXTRA_MOUNT_IDS));
		out.add("【修飾子】");
		out.add("#baby ... 赤ちゃん版 (ゾンビ系・動物系など)");
		out.add("#charged ... 帯電クリーパー");
		out.add("#rider:<敵ID> ... その敵がこの敵の上に騎乗する");
		out.add("#passenger:<MobID> ... この敵がそのMobの上に騎乗する");
		out.add("#effect:<効果ID>[:秒[:レベル]] ... モブ効果を掛けて出す (初期値: 60秒・レベル1)");
		out.add("【#effect: で使える効果ID】");
		out.add(effectIdList());
		out.add("【例】");
		out.add("minecraft:zombie#passenger:minecraft:chicken ... ニワトリに乗ったゾンビ");
		out.add("minecraft:skeleton_horse#rider:minecraft:skeleton ... スケルトンホースマン");
		out.add("minecraft:spider#rider:minecraft:skeleton ... スパイダージョッキー");
		out.add("minecraft:creeper#charged ... 帯電クリーパー");
		out.add("minecraft:zombie#effect:strength:120:1 ... 力強さIIを120秒掛けたゾンビ");
		out.add("minecraft:zombie#baby#effect:speed ... 素早い赤ちゃんゾンビ");
		return out;
	}

	/**
	 * 頭に装備できるコンポーネントを作る。
	 *
	 * @param assetId       装着時の見た目 (assets/&lt;名前空間&gt;/equipment/&lt;名前&gt;.json)
	 * @param cameraOverlay 装着中の視界オーバーレイ (かぼちゃと同じ仕組み。textures/ 以下のパス)
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Equippable headEquippable(Identifier assetId, Identifier cameraOverlay) {
		// 装備定義のキー。EquipmentAsset の型名に依存しないよう、raw 型で組み立てる
		ResourceKey registry = ResourceKey.createRegistryKey(Identifier.parse("minecraft:equipment_asset"));
		ResourceKey assetKey = ResourceKey.create(registry, assetId);

		var builder = Equippable.builder(EquipmentSlot.HEAD).setAsset(assetKey);

		// 視界オーバーレイの設定。メソッド名が版で違っても、ビルドが通るようリフレクションで呼ぶ
		try {
			Method setOverlay = builder.getClass().getMethod("setCameraOverlay", Identifier.class);
			setOverlay.invoke(builder, cameraOverlay);
		} catch (ReflectiveOperationException e) {
			DragonBallMod.LOGGER.warn("視界オーバーレイを設定できませんでした。被っても視界は狭くなりません: {}", e.toString());
		}
		return builder.build();
	}
}
