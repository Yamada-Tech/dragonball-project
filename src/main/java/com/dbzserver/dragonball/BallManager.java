package com.dbzserver.dragonball;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * ドラゴンボール7個の管理役。
 * <ul>
 *   <li>台帳 (どのボールが今どこにあるか) の保存と読み込み</li>
 *   <li>銅チェストへの配置 (ランダム座標)</li>
 *   <li>定期的な整合性チェック (重複の削除・消失の復旧)</li>
 *   <li>レーダーの目標更新</li>
 * </ul>
 * すべてサーバースレッドから呼び出されます。
 */
public final class BallManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String OVERWORLD_ID = "minecraft:overworld";

	private final MinecraftServer server;
	private final BallRecord[] records = new BallRecord[8]; // 1..7 を使用
	/** ロード済みのコンテナ。サーバー起動直後に読み込まれたものも拾えるよう static で保持する。 */
	private static final Set<BlockEntity> containers = Collections.newSetFromMap(new IdentityHashMap<>());
	/** 浮遊中のボールエンティティのUUID (番号 -> UUID)。チャンク再読み込み時も更新される。 */
	private static final Map<Integer, UUID> ballEntityIds = new HashMap<>();
	/** 襲撃中の護衛エンティティのUUID (番号 -> 一覧)。保存しない (再起動後は全滅扱い)。 */
	private final Map<Integer, List<UUID>> raidGuards = new HashMap<>();
	/** 襲撃を発生させた (護衛に狙われている) プレイヤー。死亡時の扱いに使う。保存しない。 */
	private final Map<Integer, UUID> raidTriggers = new HashMap<>();
	/** 襲撃で最後にカウントした護衛の数 (番号 -> 数)。残りカウントの通知を1回ずつにするために使う。保存しない。 */
	private final Map<Integer, Integer> raidLastCount = new HashMap<>();
	private final Map<UUID, GlobalPos> lastRadar = new HashMap<>();
	private final Random random = new Random();
	private Path stateFile;
	private int tickCounter;

	public BallManager(MinecraftServer server) {
		this.server = server;
	}

	// ------------------------------------------------------------------
	//  保存・読み込み
	// ------------------------------------------------------------------

	public void load() {
		stateFile = server.getWorldPath(LevelResource.ROOT).resolve("dragonball_state.json");
		if (!Files.exists(stateFile)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8)) {
			BallRecord[] loaded = GSON.fromJson(reader, BallRecord[].class);
			if (loaded != null) {
				for (BallRecord r : loaded) {
					if (r != null && r.number >= 1 && r.number <= 7) {
						if (r.serial == null) r.serial = "";
						if (r.kind == null) r.kind = Kind.NONE;
						records[r.number] = r;
					}
				}
			}
		} catch (Exception e) {
			DragonBallMod.LOGGER.error("ボール台帳の読み込みに失敗しました: {}", stateFile, e);
		}
	}

	public void save() {
		if (stateFile == null) {
			return;
		}
		try {
			List<BallRecord> list = new ArrayList<>();
			for (int n = 1; n <= 7; n++) {
				if (records[n] != null) list.add(records[n]);
			}
			Path tmp = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(list), StandardCharsets.UTF_8);
			Files.move(tmp, stateFile, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			DragonBallMod.LOGGER.error("ボール台帳の保存に失敗しました", e);
		}
	}

	/** 起動時: 台帳に無いボールを新規作成し、未配置のものを配置する。 */
	public void ensureAll() {
		for (int n = 1; n <= 7; n++) {
			if (records[n] == null) {
				records[n] = new BallRecord(n);
			}
			BallRecord r = records[n];
			if (r.serial.isEmpty()) {
				r.serial = newSerial();
				r.pending = true;
				r.kind = Kind.NONE;
			}
		}
		for (int n = 1; n <= 7; n++) {
			if (records[n].pending) {
				tryPlace(records[n]);
			}
		}
		save();
	}

	// ------------------------------------------------------------------
	//  イベントフック
	// ------------------------------------------------------------------

	public static void onBlockEntityLoad(BlockEntity be) {
		if (be instanceof Container) {
			containers.add(be);
		}
	}

	public static void onBlockEntityUnload(BlockEntity be) {
		containers.remove(be);
	}

	/** 浮遊ボールがワールドに現れたとき (配置・チャンク再読み込み)、UUID を記録する。 */
	public static void onBallEntityLoad(int number, UUID id) {
		ballEntityIds.put(number, id);
	}

	/** サーバー停止時に追跡中のコンテナ・ボール・護衛を破棄する。 */
	public static void clearTracked() {
		containers.clear();
		ballEntityIds.clear();
	}

	public void tick() {
		tickCounter++;
		if (tickCounter % DbzConfig.auditIntervalTicks == 0) {
			runAudit();
		}
		if (tickCounter % DbzConfig.radarUpdateTicks == 0) {
			refreshRadars();
		}
		if (tickCounter % 5 == 0) {
			tickRaids();
		}
		if (tickCounter % 10 == 0) {
			tickBallGlow();
		}
	}

	/**
		* マップ上に浮いている未入手の竜玉を淡く光らせる (エッジのアウトライン + きめ細かい粒子)。
		* 一度でも入手した竜玉 (everCollected) には効果を付けず、従来どおりにする。
		*/
	private void tickBallGlow() {
		boolean glowOn = DbzConfig.ballGlow;
		// 粒子は控えめに (20tick ごとに 1 回まばたきさせる)
		boolean sparkle = glowOn && (tickCounter % 20 == 0);
		for (int n = 1; n <= 7; n++) {
			BallRecord r = records[n];
			if (r == null || r.pending || r.kind != Kind.ITEM) {
				continue;
			}
			UUID id = ballEntityIds.get(n);
			if (id == null) {
				continue;
			}
			ServerLevel lvl = levelOf(r.dimension);
			if (lvl == null) {
				continue;
			}
			Entity e = lvl.getEntity(id);
			if (!(e instanceof ItemEntity ie) || !ie.isAlive()) {
				continue;
			}
			boolean glow = glowOn && !r.everCollected;
			if (ie.hasGlowingTag() != glow) {
				ie.setGlowingTag(glow);
			}
			if (sparkle) {
				lvl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
						e.getX(), e.getY(), e.getZ(), 6, 0.25, 0.25, 0.25, 0.015);
			}
		}
	}

	// ------------------------------------------------------------------
	//  整合性チェック
	// ------------------------------------------------------------------

	private static final class Sighting {
		final int number;
		final String serial;
		final Kind kind;
		final ServerLevel level;
		final double x, y, z;
		final ServerPlayer holder;
		final Runnable remover;

		Sighting(int number, String serial, Kind kind, ServerLevel level, double x, double y, double z,
				ServerPlayer holder, Runnable remover) {
			this.number = number;
			this.serial = serial;
			this.kind = kind;
			this.level = level;
			this.x = x;
			this.y = y;
			this.z = z;
			this.holder = holder;
			this.remover = remover;
		}
	}

	public static final class AuditResult {
		public int staleRemoved;
		public int duplicatesRemoved;
		public int regenerated;
		public int placed;
	}

	/** ワールド内のボールを全て探し、台帳と突き合わせる。 */
	public AuditResult runAudit() {
		AuditResult result = new AuditResult();
		@SuppressWarnings("unchecked")
		List<Sighting>[] seen = new List[8];
		for (int i = 0; i < 8; i++) seen[i] = new ArrayList<>();

		// 1) プレイヤー (所持品・エンダーチェスト・カーソル上)
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			ServerLevel pl = (ServerLevel) p.level();
			scanContainer(p.getInventory(), seen, Kind.PLAYER, pl, p.getX(), p.getY(), p.getZ(), p);
			scanContainer(p.getEnderChestInventory(), seen, Kind.PLAYER, pl, p.getX(), p.getY(), p.getZ(), p);
			ItemStack carried = p.containerMenu.getCarried();
			int cn = BallStacks.numberOf(carried);
			if (cn > 0) {
				final ServerPlayer owner = p;
				seen[cn].add(new Sighting(cn, BallStacks.serialOf(carried), Kind.PLAYER, pl, p.getX(), p.getY(), p.getZ(), p,
						() -> owner.containerMenu.setCarried(ItemStack.EMPTY)));
			}
		}

		// 2) ロード済みのコンテナ (チェスト・樽・ホッパーなど)
		for (BlockEntity be : new ArrayList<>(containers)) {
			if (be.isRemoved() || !(be instanceof Container c) || !(be.getLevel() instanceof ServerLevel lvl)) {
				containers.remove(be);
				continue;
			}
			BlockPos pos = be.getBlockPos();
			scanContainer(c, seen, Kind.CONTAINER, lvl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, null);
		}

		// 3) エンティティ (落ちているアイテム・額縁・モブの手持ち・チェスト付きトロッコなど)
		for (ServerLevel lvl : server.getAllLevels()) {
			for (Entity e : lvl.getAllEntities()) {
				if (e instanceof Player) {
					continue;
				}
				if (e instanceof ItemEntity ie) {
					ItemStack s = ie.getItem();
					int n = BallStacks.numberOf(s);
					if (n > 0) {
						ie.setUnlimitedLifetime();
						Compat.makeInvulnerable(ie);
						seen[n].add(new Sighting(n, BallStacks.serialOf(s), Kind.ITEM, lvl, ie.getX(), ie.getY(), ie.getZ(), null,
								ie::discard));
					}
					continue;
				}
				if (e instanceof ItemFrame frame) {
					ItemStack s = frame.getItem();
					int n = BallStacks.numberOf(s);
					if (n > 0) {
						seen[n].add(new Sighting(n, BallStacks.serialOf(s), Kind.ENTITY, lvl, e.getX(), e.getY(), e.getZ(), null,
								() -> frame.setItem(ItemStack.EMPTY)));
					}
				}
				if (e instanceof Container c) {
					scanContainer(c, seen, Kind.ENTITY, lvl, e.getX(), e.getY(), e.getZ(), null);
				}
				if (e instanceof LivingEntity living) {
					for (EquipmentSlot slot : EquipmentSlot.values()) {
						ItemStack s = living.getItemBySlot(slot);
						int n = BallStacks.numberOf(s);
						if (n > 0) {
							final EquipmentSlot theSlot = slot;
							seen[n].add(new Sighting(n, BallStacks.serialOf(s), Kind.ENTITY, lvl, e.getX(), e.getY(), e.getZ(), null,
									() -> living.setItemSlot(theSlot, ItemStack.EMPTY)));
						}
					}
				}
			}
		}

		// 4) 台帳との突き合わせ
		for (int n = 1; n <= 7; n++) {
			BallRecord r = records[n];
			if (r == null) {
				continue;
			}
			List<Sighting> fresh = new ArrayList<>();
			for (Sighting s : seen[n]) {
				if (!r.serial.equals(s.serial)) {
					s.remover.run();
					result.staleRemoved++;
					logEvent("{}番ボールの古い複製を削除しました ({})", n, describeSighting(s));
				} else {
					fresh.add(s);
				}
			}

			if (fresh.size() > 1) {
				Sighting keep = pickBest(fresh, r);
				for (Sighting s : fresh) {
					if (s != keep) {
						s.remover.run();
						result.duplicatesRemoved++;
						logEvent("{}番ボールの重複を削除しました ({})", n, describeSighting(s));
					}
				}
				fresh.clear();
				fresh.add(keep);
			}

			if (!fresh.isEmpty()) {
				applySighting(r, fresh.get(0));
				r.missing = 0;
				r.pending = false;
			} else if (r.pending) {
				if (tryPlace(r)) {
					result.placed++;
				}
			} else if (canJudgeMissing(r)) {
				r.missing++;
				if (r.missing >= DbzConfig.lostGraceAudits) {
					logEvent("{}番ボールが消失したと判断しました (最後の位置: {} / {})。新しい場所に再配置します。",
							n, r.kind.label, formatPos(r));
					r.serial = newSerial();
					r.pending = true;
					r.missing = 0;
					// 消失して新しい場所に再生成された竜玉は、全新的なものとして襲撃が復活する
					r.everCollected = false;
					r.raiding = false;
					r.cleared = false;
					raidGuards.remove(n);
					raidLastCount.remove(n);
					if (tryPlace(r)) {
						result.regenerated++;
					}
				}
			}
		}

		save();
		return result;
	}

	private void scanContainer(Container c, List<Sighting>[] seen, Kind kind, ServerLevel lvl,
			double x, double y, double z, ServerPlayer holder) {
		int size = c.getContainerSize();
		for (int i = 0; i < size; i++) {
			ItemStack s = c.getItem(i);
			int n = BallStacks.numberOf(s);
			if (n > 0) {
				final int slot = i;
				seen[n].add(new Sighting(n, BallStacks.serialOf(s), kind, lvl, x, y, z, holder,
						() -> c.setItem(slot, ItemStack.EMPTY)));
			}
		}
	}

	/** 複数見つかった「有効なボール」のうち、残すものを選ぶ。 */
	private Sighting pickBest(List<Sighting> list, BallRecord r) {
		for (Sighting s : list) {
			if (s.kind == Kind.PLAYER) {
				return s;
			}
		}
		Sighting best = list.get(0);
		double bestDist = Double.MAX_VALUE;
		for (Sighting s : list) {
			if (s.kind != r.kind) continue;
			double dx = s.x - r.x, dy = s.y - r.y, dz = s.z - r.z;
			double d = dx * dx + dy * dy + dz * dz;
			if (d < bestDist) {
				bestDist = d;
				best = s;
			}
		}
		return best;
	}

	private void applySighting(BallRecord r, Sighting s) {
		Kind before = r.kind;
		r.kind = s.kind;
		r.dimension = s.level.dimension().identifier().toString();
		r.x = s.x;
		r.y = s.y;
		r.z = s.z;
		if (s.kind == Kind.PLAYER && s.holder != null) {
			boolean changedHolder = !s.holder.getUUID().toString().equals(r.holder);
			r.holder = s.holder.getUUID().toString();
			r.holderName = s.holder.getName().getString();
			// 一度でも入手した竜玉は、以後どこに浮いても襲撃を発動させない
			if (!r.everCollected) {
				r.everCollected = true;
				save();
			}
			if ((before != Kind.PLAYER || changedHolder)) {
				logEvent("{}番ボールを {} が入手しました", r.number, r.holderName);
				if (DbzConfig.broadcastPickup) {
					server.getPlayerList().broadcastSystemMessage(
							Component.literal("【ドラゴンボール】 " + r.holderName + " が " + ModItems.BALL_NAMES[r.number - 1] + " を手に入れた！"),
							false);
				}
			}
		} else {
			if (before == Kind.PLAYER) {
				logEvent("{}番ボールが {} の手を離れました ({})", r.number, r.holderName, s.kind.label);
			}
			r.holder = "";
			r.holderName = "";
		}
	}

	/** 「見つからない」ことを消失と判断してよい状況か (その場所が確実に読み込まれているか)。 */
	private boolean canJudgeMissing(BallRecord r) {
		if (r.kind == Kind.NONE) {
			return false;
		}
		if (r.kind == Kind.PLAYER) {
			UUID id = parseUuid(r.holder);
			return id != null && server.getPlayerList().getPlayer(id) != null;
		}
		ServerLevel lvl = levelOf(r.dimension);
		if (lvl == null) {
			return false;
		}
		return lvl.isPositionEntityTicking(BlockPos.containing(r.x, r.y, r.z));
	}

	// ------------------------------------------------------------------
	//  配置
	// ------------------------------------------------------------------

	private boolean tryPlace(BallRecord r) {
		ServerLevel level = server.overworld();
		BlockPos spot = findSpot(level, r.number);
		if (spot == null) {
			DragonBallMod.LOGGER.warn("{}番ボールを置ける場所が見つかりませんでした。次回のチェックで再試行します。", r.number);
			r.pending = true;
			return false;
		}
		return placeBall(level, spot, r, true);
	}

	/**
		* 指定位置に、その場でふわふわ浮いているボールをスポーンさせる。
		* 地面の上 (spawn.float_height の高さ) に、重力なし・無敵のアイテムとして出す。
		*/
	private boolean placeBall(ServerLevel level, BlockPos pos, BallRecord r, boolean setHome) {
		level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);

		ItemStack ball = BallStacks.create(r.number, r.serial);
		double x = pos.getX() + 0.5;
		double y = pos.getY() + DbzConfig.floatHeight;
		double z = pos.getZ() + 0.5;

		ItemEntity ballEntity = new ItemEntity(level, x, y, z, ball);
		ballEntity.setUnlimitedLifetime();
		Compat.makeInvulnerable(ballEntity);
		Compat.makeFloat(ballEntity);
		Compat.stopMotion(ballEntity);
		// 一度でも入手済みの竜玉は襲撃なしで取得できる (lock しない)
		if (DbzConfig.raidEnabled && !r.everCollected) {
			// 襲撃をクリアするまで取得できないようにする (tickRaids で常時管理)
			Compat.setPickupDelay(ballEntity, 200);
		} else {
			Compat.setPickupDelay(ballEntity, 0);
		}
		if (!Compat.spawnEntity(level, ballEntity)) {
			DragonBallMod.LOGGER.error("{}番ボールをスポーンできませんでした ({})", r.number, pos);
			r.pending = true;
			return false;
		}
		ballEntityIds.put(r.number, ballEntity.getUUID());

		// 新しいボールは襲撃前からのやり直し (触れるとまた護衛が出る)
		r.raiding = false;
		r.cleared = false;
		raidGuards.remove(r.number);
		raidLastCount.remove(r.number);

		r.kind = Kind.ITEM;
		r.dimension = level.dimension().identifier().toString();
		r.x = x;
		r.y = y;
		r.z = z;
		r.holder = "";
		r.holderName = "";
		r.pending = false;
		r.missing = 0;
		if (setHome) {
			r.hasHome = true;
			r.homeDimension = r.dimension;
			r.homeX = pos.getX();
			r.homeY = pos.getY();
			r.homeZ = pos.getZ();
		}
		logEvent("{}番ボールを配置しました: {}", r.number, formatPos(r));
		return true;
	}

	/** 配置範囲内でボールを置ける地点をランダムに探す。見つからなければ null。 */
	private BlockPos findSpot(ServerLevel level, int number) {
		double min2 = (double) DbzConfig.minRadius * DbzConfig.minRadius;
		double max2 = (double) DbzConfig.maxRadius * DbzConfig.maxRadius;
		for (int attempt = 0; attempt < DbzConfig.maxAttempts; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double radius = Math.sqrt(min2 + random.nextDouble() * (max2 - min2));
			int x = DbzConfig.centerX + (int) Math.round(Math.cos(angle) * radius);
			int z = DbzConfig.centerZ + (int) Math.round(Math.sin(angle) * radius);

			if (tooCloseToOthers(x, z, number)) {
				continue;
			}
			if (!level.getWorldBorder().isWithinBounds(new BlockPos(x, 0, z))) {
				continue;
			}
			level.getChunk(x >> 4, z >> 4); // 地形を読み込む (未生成なら生成される)
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			if (isGoodSpot(level, top)) {
				return top;
			}
		}
		return null;
	}

	private boolean tooCloseToOthers(int x, int z, int number) {
		if (DbzConfig.minBallDistance <= 0) {
			return false;
		}
		double limit2 = (double) DbzConfig.minBallDistance * DbzConfig.minBallDistance;
		for (int n = 1; n <= 7; n++) {
			BallRecord o = records[n];
			if (o == null || n == number || o.pending) continue;
			if (o.kind == Kind.NONE || o.kind == Kind.PLAYER) continue;
			if (!OVERWORLD_ID.equals(o.dimension)) continue;
			double dx = o.x - x, dz = o.z - z;
			if (dx * dx + dz * dz < limit2) {
				return true;
			}
		}
		return false;
	}

	private boolean isGoodSpot(ServerLevel level, BlockPos pos) {
		BlockPos below = pos.below();
		BlockState here = level.getBlockState(pos);
		BlockState under = level.getBlockState(below);
		if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(below).isEmpty()) {
			return false;
		}
		if (!(here.isAir() || here.canBeReplaced())) {
			return false;
		}
		if (!under.isFaceSturdy(level, below, Direction.UP)) {
			return false;
		}
		// 浮かぶ高さに空気 (または置き換えられるブロック) があるか
		BlockPos above = pos.above();
		BlockState airBlock = level.getBlockState(above);
		if (!(airBlock.isAir() || airBlock.canBeReplaced())) {
			return false;
		}
		Holder<Biome> biome = level.getBiome(pos);
		if (DbzConfig.avoidOcean && biome.is(BiomeTags.IS_OCEAN)) {
			return false;
		}
		if (DbzConfig.avoidRiver && biome.is(BiomeTags.IS_RIVER)) {
			return false;
		}
		return true;
	}

	// ------------------------------------------------------------------
	//  レーダー
	// ------------------------------------------------------------------

	/** レーダーの追跡対象。プレイヤー所持中のボールは含まない。 */
	public BlockPos nearestRadarTarget(BlockPos from, int maxRange) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		double limit2 = maxRange > 0 ? (double) maxRange * maxRange : Double.MAX_VALUE;
		for (int n = 1; n <= 7; n++) {
			BallRecord r = records[n];
			if (r == null || r.pending) continue;
			if (r.kind == Kind.NONE || r.kind == Kind.PLAYER) continue;
			if (!OVERWORLD_ID.equals(r.dimension)) continue;
			double dx = r.x - from.getX(), dy = r.y - from.getY(), dz = r.z - from.getZ();
			double d = dx * dx + dy * dy + dz * dz;
			if (d > limit2) continue;
			if (d < bestDist) {
				bestDist = d;
				best = BlockPos.containing(r.x, r.y, r.z);
			}
		}
		return best;
	}

	/** 目標が変わったプレイヤーに、レーダーの表示更新を送る。 */
	private void refreshRadars() {
		Set<UUID> online = new HashSet<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			UUID id = p.getUUID();
			online.add(id);
			if (!hasRadar(p)) {
				lastRadar.remove(id);
				continue;
			}
			GlobalPos target = Radar.targetFor(p);
			if (!target.equals(lastRadar.get(id))) {
				lastRadar.put(id, target);
				p.containerMenu.sendAllDataToRemote();
			}
		}
		lastRadar.keySet().retainAll(online);
	}

	private boolean hasRadar(ServerPlayer p) {
		var inv = p.getInventory();
		int size = inv.getContainerSize();
		for (int i = 0; i < size; i++) {
			if (inv.getItem(i).getItem() == ModItems.RADAR) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------
	//  襲撃イベント (ボールに触れると護衛が現れ、全滅させると取得できる)
	// ------------------------------------------------------------------

	/** 5tickごとに実行: 襲撃の進行管理と、ボールへの接近判定。 */
	private void tickRaids() {
		for (int n = 1; n <= 7; n++) {
			BallRecord r = records[n];
			if (r == null) {
				continue;
			}
			if (r.raiding) {
				checkRaidCleared(n, r);
			}
			if (r.pending || r.kind != Kind.ITEM) {
				continue;
			}
			ServerLevel lvl = levelOf(r.dimension);
			if (lvl == null) {
				continue;
			}
			ItemEntity ie = findBallEntity(lvl, r);
			if (ie == null) {
				continue;
			}
			// 一度でも入手した竜玉は、触れても襲撃を発動させない (そのまま取得できる)
			boolean locked = DbzConfig.raidEnabled && !r.cleared && !r.everCollected;
			// 護衛が片付く (または襲撃が起きない) まで、触れても取得できないようにする。
			// 解除後は取得可能にする。
			Compat.setPickupDelay(ie, locked ? 100 : 0);
			if (locked && !r.raiding) {
				// ボールは floatHeight 分ふわりと浮いているため、記録した地面の座標ではなく
				// エンティティの実位置で接近を測る ( otherwise 半径に入らない )
				ServerPlayer p = nearestPlayerWithin(lvl, ie.getX(), ie.getY(), ie.getZ(),
						DbzConfig.raidTriggerRadius);
				if (p != null) {
					startRaid(n, r, lvl, p);
				}
			}
		}
	}

	/** ワールド上の浮遊ボールエンティティを探す。未読み込みなどで無ければ null。 */
	private ItemEntity findBallEntity(ServerLevel lvl, BallRecord r) {
		UUID id = ballEntityIds.get(r.number);
		if (id != null) {
			Entity e = lvl.getEntity(id);
			if (e instanceof ItemEntity ie && BallStacks.numberOf(ie.getItem()) == r.number) {
				return ie;
			}
		}
		// UUIDが分からなくなっている場合 (チャンク再読み込みなど) は周囲を探す
		var area = new net.minecraft.world.phys.AABB(r.x - 2, r.y - 2, r.z - 2, r.x + 2, r.y + 2, r.z + 2);
		for (ItemEntity ie : lvl.getEntitiesOfClass(ItemEntity.class, area)) {
			if (BallStacks.numberOf(ie.getItem()) == r.number
					&& r.serial.equals(BallStacks.serialOf(ie.getItem()))) {
				ballEntityIds.put(r.number, ie.getUUID());
				return ie;
			}
		}
		return null;
	}

	/** 指定位置の周囲にいる一番近いプレイヤー (半径外なら null)。当たり判定の中心で測る。 */
	private static ServerPlayer nearestPlayerWithin(ServerLevel lvl, double x, double y, double z, double radius) {
		ServerPlayer best = null;
		double bestD = radius * radius;
		for (ServerPlayer p : Compat.players(lvl)) {
			// p.getX/Y/Z は足元なので、胴体中心 (約 +1.0) で測る
			double dx = p.getX() - x;
			double dy = (p.getY() + 1.0) - y;
			double dz = p.getZ() - z;
			double d = dx * dx + dy * dy + dz * dz;
			if (d <= bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	/** 今この時点で「取得済み (プレイヤーが持っている)」ボールの数。 */
	private int countCollected() {
		int c = 0;
		for (int i = 1; i <= 7; i++) {
			BallRecord r = records[i];
			if (r != null && !r.pending && r.kind == Kind.PLAYER) {
				c++;
			}
		}
		return c;
	}

	/**
	 * ボールにプレイヤーが触れた: 護衛の集団をプレイヤーから離れた場所 (raid.min_spawn_distance 〜
	 * raid.spawn_radius 先の扇状の隊列) にスポーンさせる。敵はプレイヤーを追って突っ込んでくる。
	 * 難易度は「同じ強さの敵の数」で上がる (敵自体は強くしない)。
	 * 騎乗 (敵がランダムで敵に騎乗) と武器 (剣・斧をランダムで装備) は設定の確率で付く。
	 */
	private void startRaid(int n, BallRecord r, ServerLevel lvl, ServerPlayer triggerer) {
		String[] pool = DbzConfig.raidEnemies.split(",");
		String[] mountPool = DbzConfig.raidMounts.isBlank() ? new String[0] : DbzConfig.raidMounts.split(",");
		String[] weaponPool = DbzConfig.raidWeapons.isBlank() ? new String[0] : DbzConfig.raidWeapons.split(",");

		int collected = DbzConfig.raidScaling ? countCollected() : 0;
		int minions = DbzConfig.raidMinions
				+ (DbzConfig.raidScaling ? collected * DbzConfig.raidExtraMinionsPerBall : 0);
		minions = Math.min(48, Math.max(1, minions));
		// ラヴェイジャーは「取得済みの数」で増えていく特別枠 (序盤はほぼ出ない)
		int ravagers = DbzConfig.raidScaling
				? DbzConfig.raidRavagerMin + collected * DbzConfig.raidRavagerPerBall
				: DbzConfig.raidRavagerMin;
		ravagers = Math.min(DbzConfig.raidRavagerMax, ravagers);
		int total = minions + ravagers;

		// 襲撃の集団はプレイヤーの周囲に現れる (ボールの位置ではない)
		double px = triggerer.getX();
		double py = triggerer.getY();
		double pz = triggerer.getZ();

		// 集団の中心方向 (ランダムな1方向) と、その周囲の Spread 度の扇に配置する
		double facing = random.nextDouble() * Math.PI * 2.0;
		double spread = Math.toRadians(DbzConfig.raidFormationSpreadDegrees);
		double minD = DbzConfig.raidMinSpawnDistance;
		double maxD = Math.max(minD + 1.0, DbzConfig.raidSpawnRadius);

		String[] riderPool = DbzConfig.raidRavagerRiders.isBlank()
				? new String[0] : DbzConfig.raidRavagerRiders.split(",");
		List<UUID> guards = new ArrayList<>();
		for (int i = 0; i < total; i++) {
			String spec;
			boolean isRavager = i >= minions;
			if (isRavager) {
				// ラヴェイジャー: 設定確率でピリジャーやエヴォーカーを騎乗させて出す
				spec = "minecraft:ravager";
				if (riderPool.length > 0 && random.nextDouble() < DbzConfig.raidRavagerMountChance) {
					spec = spec + "#rider:" + riderPool[random.nextInt(riderPool.length)].trim();
				}
			} else {
				spec = pool[random.nextInt(pool.length)].trim();
				// 雑魚のランダム騎乗 (初期値 0 = 無し)
				if (mountPool.length > 0 && random.nextDouble() < DbzConfig.raidRandomMountChance) {
					spec = spec + "#passenger:" + mountPool[random.nextInt(mountPool.length)].trim();
				}
			}
			// 隊列位置: 正面方向から Spread の範囲、minD〜maxD の奥行き
			// (ラヴェイジャーは集団のやや後ろから登場させる)
			double angle = facing + (random.nextDouble() - 0.5) * spread;
			double dist;
			if (isRavager) {
				dist = minD + (maxD - minD) * (0.6 + random.nextDouble() * 0.4);
			} else {
				dist = minD + random.nextDouble() * (maxD - minD);
			}
			double sx = px + Math.cos(angle) * dist;
			double sz = pz + Math.sin(angle) * dist;
			// スポーン地点の地面を探す (空中出現・地中出現を防ぐ)
			double sy = groundY(lvl, sx, sz);
			if (sy == Double.NEGATIVE_INFINITY) {
				continue; // 置けない場所 (虚空・未読み込み) はスキップ
			}
			List<Entity> spawned = new ArrayList<>();
			Compat.spawnEnemy(lvl, spec, sx, sy, sz, triggerer, spawned);
			// 武器をランダムで装備 (騎乗手を除く近接職のみ。弓持ち・魔法使い・ラヴェイジャーには持たせない)
			if (weaponPool.length > 0) {
				for (Entity e : spawned) {
					if (isRavager || e.isPassenger() || isRangedMob(e)) {
						continue;
					}
					if (random.nextDouble() < DbzConfig.raidRandomWeaponChance) {
						String weapon = weaponPool[random.nextInt(weaponPool.length)].trim();
						Compat.equipWeapon(e, weapon);
					}
				}
			}
			// 騎乗している敵・騎乗馬もすべて護衛として数える
			for (Entity e : spawned) {
				guards.add(e.getUUID());
			}
		}
		if (guards.isEmpty()) {
			// 敵を出せなかったときは、ボールが取れない状態のままにならないようにする
			r.cleared = true;
			logEvent("{}番竜玉の襲撃: 護衛をスポーンできませんでした。ボールは取得可能なままにします。", n);
			save();
			return;
		}
		r.raiding = true;
		r.cleared = false;
		raidGuards.put(n, guards);
		raidLastCount.put(n, guards.size());
		save();
		logEvent("{}番竜玉の襲撃が発生しました (護衛 {} 体 / 取得済み {} 個 / {}〜{} ブロック先から突撃)",
				n, guards.size(), collected, (int) minD, (int) maxD);
		String strengthTag = DbzConfig.raidScaling ? " (取得済み " + collected + " 個)" : "";
		server.getPlayerList().broadcastSystemMessage(
				Component.literal("【襲撃】" + ModItems.BALL_NAMES[n - 1] + " を守る護衛が現れた！護衛をせん滅すると、もう一度触れて取得できる。" + strengthTag),
				false);
	}

	/** 護衛が全滅したかを確認し、全滅していたら襲撃を終了 (ボールが取得可能) にする。 */
	private void checkRaidCleared(int n, BallRecord r) {
		List<UUID> guards = raidGuards.get(n);
		if (guards == null || guards.isEmpty()) {
			// 台帳上は襲撃中だが護衛の記録がない (サーバー再起動など) → 全滅扱い
			endRaid(n, r);
			return;
		}
		ServerLevel lvl = levelOf(r.dimension);
		if (lvl == null) {
			// ディメンションが見つからない。ここでは判断しない
			return;
		}
		// ボールの周囲が読み込まれていない間は判定しない (チャンク外にいる護衛を
		// 死亡と誤判定しないため)
		if (!lvl.hasChunkAt(BlockPos.containing(r.x, r.y, r.z))) {
			return;
		}
		int alive = 0;
		for (UUID id : guards) {
			Entity e = lvl.getEntity(id);
			if (e != null && e.isAlive()) {
				alive++;
			}
		}
		if (alive == 0) {
			endRaid(n, r);
			return;
		}
		// 護衛の数が減ったら記録し、カウント中表示 (閾値以下) で数が変わったとき全員に知らせる
		int last = raidLastCount.getOrDefault(n, alive);
		if (alive < last) {
			raidLastCount.put(n, alive);
			int threshold = DbzConfig.raidCountdownThreshold;
			if (threshold > 0 && alive <= threshold) {
				String msg = Component.literal("【襲撃】" + ModItems.BALL_NAMES[n - 1] + " の護衛、残り "
						+ alive + " 体！").getString();
				server.getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
			}
		}
	}

	/** 襲撃終了: ボールに再触れすると取得できる状態にする。 */
	private void endRaid(int n, BallRecord r) {
		r.raiding = false;
		r.cleared = true;
		raidGuards.remove(n);
		raidLastCount.remove(n);
		save();
		logEvent("{}番竜玉の護衛を全滅。ボールは再触場で取得可能。", n);
		server.getPlayerList().broadcastSystemMessage(
				Component.literal("【襲撃】" + ModItems.BALL_NAMES[n - 1] + " の護衛をせん滅した！もう一度竜玉に触れて取得しよう。"),
				false);
	}

	/**
	 * プレイヤーが死亡したときの竜玉の扱い。
	 * 死亡地点の周囲 (raid.death_drop_radius) に他のプレイヤーがいれば、
	 * 竜玉はその場にドロップ (ふわふわ浮く実体として再出現)。誰もいなければ消失扱い (新しい場所に再配置)。
	 */
	public void handleDeath(ServerPlayer dead) {
		UUID id = dead.getUUID();
		ServerLevel lvl = (ServerLevel) dead.level();
		var inv = dead.getInventory();
		int size = inv.getContainerSize();
		for (int n = 1; n <= 7; n++) {
			BallRecord r = records[n];
			if (r == null || r.pending || r.kind != Kind.PLAYER || !id.equals(parseHolder(r.holder))) {
				continue;
			}
			// 死亡プレイヤーの所持品から該当ボールを抜く (バニラのドロップと重複しないようにする)
			for (int slot = 0; slot < size; slot++) {
				ItemStack s = inv.getItem(slot);
				if (BallStacks.numberOf(s) == n && r.serial.equals(BallStacks.serialOf(s))) {
					inv.setItem(slot, ItemStack.EMPTY);
				}
			}
			// 同じ次元に他のプレイヤーが近くにいるか
			boolean otherNearby = hasOtherPlayerNear(lvl, dead, DbzConfig.raidDeathDropRadius);
			if (otherNearby) {
				dropBallAt(lvl, dead.getX(), dead.getY(), dead.getZ(), r);
				logEvent("{}番竜玉の所持者が死亡。近くに他のプレイヤーがいたためその場にドロップしました。", n);
			} else {
				// 誰もいない → 消失扱い。新しい場所へ再配置 (serial を変え、古い実体は削除される)
				// 消失した竜玉は全新的なものとして、襲撃イベントが復活する
				r.serial = newSerial();
				r.pending = true;
				r.missing = 0;
				r.kind = Kind.NONE;
				r.raiding = false;
				r.cleared = false;
				r.everCollected = false;
				raidGuards.remove(n);
				raidLastCount.remove(n);
				logEvent("{}番竜玉の所持者が死亡し、周囲に他のプレイヤーがいないため消失とみなしました。新しい場所に再配置します。", n);
			}
			save();
		}
	}

	/** 死亡者自身を除き、同じ次元の半径内に他のオンラインプレイヤーがいるか。 */
	private boolean hasOtherPlayerNear(ServerLevel lvl, ServerPlayer dead, double radius) {
		double r2 = radius * radius;
		for (ServerPlayer p : Compat.players(lvl)) {
			if (p == dead || !p.isAlive()) {
				continue;
			}
			if (p.distanceToSqr(dead.getX(), dead.getY(), dead.getZ()) <= r2) {
				return true;
			}
		}
		return false;
	}

	/** 指定位置に、ふわふわ浮く無敵の竜玉としてドロップする (serial はそのまま維持)。 */
	private void dropBallAt(ServerLevel lvl, double x, double y, double z, BallRecord r) {
		ItemStack ball = BallStacks.create(r.number, r.serial);
		ItemEntity ie = new ItemEntity(lvl, x, y + DbzConfig.floatHeight, z, ball);
		ie.setUnlimitedLifetime();
		Compat.makeInvulnerable(ie);
		Compat.makeFloat(ie);
		Compat.stopMotion(ie);
		// 襲撃がまだ終わっていなければ、再び護衛を倒すまで取得できない
		// (一度でも入手済みの竜玉は襲撃なしで取得できる)
		Compat.setPickupDelay(ie, (DbzConfig.raidEnabled && !r.cleared && !r.everCollected) ? 100 : 0);
		if (!Compat.spawnEntity(lvl, ie)) {
			// スポーンに失敗したら消失扱いで再配置に回す
			r.serial = newSerial();
			r.pending = true;
			r.kind = Kind.NONE;
			return;
		}
		ballEntityIds.put(r.number, ie.getUUID());
		r.kind = Kind.ITEM;
		r.dimension = lvl.dimension().identifier().toString();
		r.x = x;
		r.y = y + DbzConfig.floatHeight;
		r.z = z;
		r.holder = "";
		r.holderName = "";
		r.missing = 0;
	}

	private static UUID parseHolder(String holder) {
		try {
			return UUID.fromString(holder);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** 運営用: 襲撃を強制的に終わらせる (敵が行方不明などのとき)。 */
	public boolean forceClear(int n) {
		BallRecord r = records[n];
		if (r == null || !r.raiding) {
			return false;
		}
		endRaid(n, r);
		return true;
	}

	/**
	 * 運営用: 襲撃を再武装する (cleared を戻し、接近でまた護衛が出る状態にする)。
	 * 襲撃が正常に出なかったワールドで、ボールが「取得可能」のまま固定されている場合のリセット用。
	 */
	public boolean rearm(int n) {
		BallRecord r = records[n];
		if (r == null) {
			return false;
		}
		r.raiding = false;
		r.cleared = false;
		raidGuards.remove(n);
		raidLastCount.remove(n);
		save();
		return true;
	}

	// ------------------------------------------------------------------
	//  管理コマンド用
	// ------------------------------------------------------------------

	public BallRecord record(int n) {
		return (n >= 1 && n <= 7) ? records[n] : null;
	}

	/** 新しい場所へ再配置する (古い実体は次のチェックで削除される)。 */
	public boolean respawn(int n) {
		BallRecord r = records[n];
		if (r == null) return false;
		r.serial = newSerial();
		r.pending = true;
		r.missing = 0;
		// 再配置された竜玉は全新的なものとして、襲撃イベントが復活する
		r.everCollected = false;
		r.raiding = false;
		r.cleared = false;
		raidGuards.remove(n);
		raidLastCount.remove(n);
		boolean ok = tryPlace(r);
		save();
		return ok;
	}

	/** 最初に置かれたチェストへ戻す。 */
	public boolean returnHome(int n) {
		BallRecord r = records[n];
		if (r == null) return false;
		if (!r.hasHome) {
			return respawn(n);
		}
		ServerLevel lvl = levelOf(r.homeDimension);
		if (lvl == null) {
			return false;
		}
		r.serial = newSerial();
		boolean ok = placeBall(lvl, new BlockPos(r.homeX, r.homeY, r.homeZ), r, false);
		save();
		return ok;
	}

	/** 指定位置 (その場の浮遊状態) へ移動する。 */
	public boolean moveHere(int n, ServerLevel level, BlockPos pos) {
		BallRecord r = records[n];
		if (r == null) return false;
		BlockState state = level.getBlockState(pos);
		if (!(state.isAir() || state.canBeReplaced())) {
			return false;
		}
		r.serial = newSerial();
		boolean ok = placeBall(level, pos, r, true);
		save();
		return ok;
	}

	/** ボールを1個、既存のものごと移して付与する (複製にはならない)。 */
	public void giveBall(int n, ServerPlayer player) {
		BallRecord r = records[n];
		if (r == null) return;
		r.serial = newSerial();
		r.pending = false;
		r.missing = 0;
		r.raiding = false;
		r.cleared = false;
		r.everCollected = true;
		raidGuards.remove(n);
		ItemStack stack = BallStacks.create(n, r.serial);
		if (!player.getInventory().add(stack)) {
			Block.popResource(player.level(), player.blockPosition(), stack);
		}
		r.kind = Kind.PLAYER;
		r.dimension = player.level().dimension().identifier().toString();
		r.x = player.getX();
		r.y = player.getY();
		r.z = player.getZ();
		r.holder = player.getUUID().toString();
		r.holderName = player.getName().getString();
		save();
	}

	/** 弓・ポーション・魔法を使う敵 (近接武器を持たせない対象)。 */
	private static boolean isRangedMob(Entity e) {
		String key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
				.getKey(e.getType()).toString();
		return switch (key) {
			case "minecraft:pillager", "minecraft:evoker", "minecraft:witch" -> true;
			default -> false;
		};
	}

	/** スポーン地点の地面の高さ。チャンクが読み込まれていなければ INF 相当 (NaN) を返す。 */
	private double groundY(ServerLevel level, double x, double z) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		if (!level.hasChunk(bx >> 4, bz >> 4)) {
			return Double.NEGATIVE_INFINITY;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(bx, 0, bz));
		return top.getY();
	}

	public ServerLevel levelOf(String dimension) {
		try {
			return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension)));
		} catch (Exception e) {
			return null;
		}
	}

	public String describe(BallRecord r) {
		if (r == null) return "(未登録)";
		if (r.pending) return "未配置 (場所探し中)";
		String where = switch (r.kind) {
			case PLAYER -> r.kind.label + ": " + r.holderName;
			case NONE -> r.kind.label;
			default -> r.kind.label + " @ " + formatPos(r);
		};
		if (r.raiding) {
			where += " [襲撃中: 護衛をせん滅すると取得可能]";
		} else if (r.cleared) {
			where += " [護衛せん滅済み: 触れると取得可能]";
		}
		return where;
	}

	// ------------------------------------------------------------------
	//  補助
	// ------------------------------------------------------------------

	private static String formatPos(BallRecord r) {
		return r.dimension + " (" + Mth.floor(r.x) + ", " + Mth.floor(r.y) + ", " + Mth.floor(r.z) + ")";
	}

	private static String describeSighting(Sighting s) {
		return s.kind.label + " " + s.level.dimension().identifier() + " (" + Mth.floor(s.x) + ", " + Mth.floor(s.y) + ", " + Mth.floor(s.z) + ")";
	}

	private static String newSerial() {
		return UUID.randomUUID().toString();
	}

	private static UUID parseUuid(String s) {
		try {
			return (s == null || s.isEmpty()) ? null : UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private void logEvent(String format, Object... args) {
		if (DbzConfig.logEvents) {
			DragonBallMod.LOGGER.info(format, args);
		}
	}
}
