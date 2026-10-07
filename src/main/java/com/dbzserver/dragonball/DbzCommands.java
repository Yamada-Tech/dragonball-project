package com.dbzserver.dragonball;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** 運営用コマンド /db 。OP (権限レベル2以上) のみ使用できます。 */
public final class DbzCommands {
	private static final String NUM = "number";

	private DbzCommands() {
	}

	private static boolean isAdmin(CommandSourceStack source) {
		return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(2)));
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("db")
				.requires(DbzCommands::isAdmin)
				.executes(DbzCommands::help)
				.then(literal("help").executes(DbzCommands::help))
				.then(literal("status").executes(DbzCommands::status))
				.then(literal("locate")
						.then(argument(NUM, IntegerArgumentType.integer(1, 7)).executes(DbzCommands::locate)))
				.then(literal("tp")
						.then(argument(NUM, IntegerArgumentType.integer(1, 7)).executes(DbzCommands::teleport)))
				.then(literal("give")
						.then(argument("player", EntityArgument.player())
								.then(argument(NUM, IntegerArgumentType.integer(1, 7)).executes(DbzCommands::give))))
				.then(literal("radar")
						.executes(ctx -> giveRadar(ctx, ctx.getSource().getPlayerOrException()))
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> giveRadar(ctx, EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("respawn")
						.then(literal("all").executes(DbzCommands::respawnAll))
						.then(argument(NUM, IntegerArgumentType.integer(1, 7)).executes(DbzCommands::respawnOne)))
				.then(literal("return")
						.then(argument(NUM, IntegerArgumentType.integer(1, 7)).executes(DbzCommands::returnHome)))
				.then(literal("sethere")
						.then(argument(NUM, IntegerArgumentType.integer(1, 7)).executes(DbzCommands::setHere)))
				.then(literal("audit").executes(DbzCommands::audit))
				.then(literal("reload").executes(DbzCommands::reload))
				.then(literal("config").executes(DbzCommands::showConfig)));
	}

	// ------------------------------------------------------------------

	private static BallManager manager(CommandContext<CommandSourceStack> ctx) {
		BallManager m = DragonBallMod.manager();
		if (m == null) {
			ctx.getSource().sendFailure(Component.literal("ボール管理がまだ起動していません。"));
		}
		return m;
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
	}

	private static void fail(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendFailure(Component.literal(text));
	}

	private static String label(int n) {
		return "[" + n + "] " + ModItems.BALL_NAMES[n - 1];
	}

	// ------------------------------------------------------------------

	private static int help(CommandContext<CommandSourceStack> ctx) {
		say(ctx, "=== /db コマンド一覧 ===");
		say(ctx, "/db status                      7個すべての状態を表示");
		say(ctx, "/db locate <1-7>                指定ボールの位置を表示");
		say(ctx, "/db tp <1-7>                    指定ボールの位置へテレポート");
		say(ctx, "/db give <プレイヤー> <1-7>     ボールを既存のものごと移して付与 (複製にならない)");
		say(ctx, "/db radar [プレイヤー]          ドラゴンレーダーを付与");
		say(ctx, "/db respawn <1-7|all>           新しいランダム位置へ再配置 (旧実体は削除)");
		say(ctx, "/db return <1-7>                最初のチェストへ戻す");
		say(ctx, "/db sethere <1-7>               今いる場所へ銅チェストごと移動");
		say(ctx, "/db audit                       整合性チェックを今すぐ実行");
		say(ctx, "/db reload                      設定ファイルを再読み込み");
		say(ctx, "/db config                      現在の主な設定値を表示");
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		BallManager m = manager(ctx);
		if (m == null) return 0;
		say(ctx, "=== ドラゴンボールの状態 ===");
		for (int n = 1; n <= 7; n++) {
			say(ctx, label(n) + " : " + m.describe(m.record(n)));
		}
		return 1;
	}

	private static int locate(CommandContext<CommandSourceStack> ctx) {
		BallManager m = manager(ctx);
		if (m == null) return 0;
		int n = IntegerArgumentType.getInteger(ctx, NUM);
		say(ctx, label(n) + " : " + m.describe(m.record(n)));
		return 1;
	}

	private static int teleport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		BallManager m = manager(ctx);
		if (m == null) return 0;
		int n = IntegerArgumentType.getInteger(ctx, NUM);
		BallRecord r = m.record(n);
		if (r == null || r.pending || r.kind == Kind.NONE) {
			fail(ctx, label(n) + " はまだ配置されていません。");
			return 0;
		}
		ServerLevel level = m.levelOf(r.dimension);
		if (level == null) {
			fail(ctx, "ディメンション " + r.dimension + " が見つかりません。");
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Vec3 pos = new Vec3(r.x, r.y + 1.0, r.z);
		player.teleport(new TeleportTransition(level, pos, Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
		say(ctx, label(n) + " の位置へテレポートしました。");
		return 1;
	}

	private static int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		BallManager m = manager(ctx);
		if (m == null) return 0;
		int n = IntegerArgumentType.getInteger(ctx, NUM);
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		m.giveBall(n, target);
		say(ctx, label(n) + " を " + target.getName().getString() + " に付与しました。(元の実体は次の整合性チェックで削除されます)");
		return 1;
	}

	private static int giveRadar(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
		ItemStack stack = ModItems.RADAR.getDefaultInstance();
		if (!target.getInventory().add(stack)) {
			target.drop(stack, false);
		}
		say(ctx, "ドラゴンレーダーを " + target.getName().getString() + " に付与しました。");
		return 1;
	}

	private static int respawnOne(CommandContext<CommandSourceStack> ctx) {
		BallManager m = manager(ctx);
		if (m == null) return 0;
		int n = IntegerArgumentType.getInteger(ctx, NUM);
		boolean ok = m.respawn(n);
		if (ok) {
			say(ctx, label(n) + " を再配置しました: " + m.describe(m.record(n)));
		} else {
			fail(ctx, label(n) + " を置ける場所が見つかりませんでした。設定の範囲や試行回数を見直してください。");
		}
		return ok ? 1 : 0;
	}

	private static int respawnAll(CommandContext<CommandSourceStack> ctx) {
		BallManager m = manager(ctx);
		if (m == null) return 0;
		int okCount = 0;
		for (int n = 1; n <= 7; n++) {
			if (m.respawn(n)) okCount++;
		}
		say(ctx, "7個中 " + okCount + " 個を再配置しました。");
		return okCount;
	}

	private static int returnHome(CommandContext<CommandSourceStack> ctx) {
		BallManager m = manager(ctx);
		if (m == null) return 0;
		int n = IntegerArgumentType.getInteger(ctx, NUM);
		boolean ok = m.returnHome(n);
		if (ok) {
			say(ctx, label(n) + " を元のチェストへ戻しました: " + m.describe(m.record(n)));
		} else {
			fail(ctx, label(n) + " を戻せませんでした。");
		}
		return ok ? 1 : 0;
	}

	private static int setHere(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		BallManager m = manager(ctx);
		if (m == null) return 0;
		int n = IntegerArgumentType.getInteger(ctx, NUM);
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		BlockPos pos = player.blockPosition();
		boolean ok = m.moveHere(n, (ServerLevel) player.level(), pos);
		if (ok) {
			say(ctx, label(n) + " を現在地に銅チェストごと移動しました。");
		} else {
			fail(ctx, "ここには置けません (足元に別のブロックがあります)。");
		}
		return ok ? 1 : 0;
	}

	private static int audit(CommandContext<CommandSourceStack> ctx) {
		BallManager m = manager(ctx);
		if (m == null) return 0;
		BallManager.AuditResult r = m.runAudit();
		say(ctx, "整合性チェック完了: 古い複製の削除 " + r.staleRemoved + " 件 / 重複の削除 " + r.duplicatesRemoved
				+ " 件 / 消失による再配置 " + r.regenerated + " 件 / 未配置の配置 " + r.placed + " 件");
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		DbzConfig.load();
		ctx.getSource().sendSuccess(() -> Component.literal("設定を再読み込みしました: " + DbzConfig.path())
				.withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int showConfig(CommandContext<CommandSourceStack> ctx) {
		say(ctx, "=== 現在の設定 (" + DbzConfig.path().getFileName() + ") ===");
		say(ctx, "配置の中心: X=" + DbzConfig.centerX + " Z=" + DbzConfig.centerZ);
		say(ctx, "配置の範囲: " + DbzConfig.minRadius + " 〜 " + DbzConfig.maxRadius + " ブロック");
		say(ctx, "ボール同士の最低距離: " + DbzConfig.minBallDistance);
		say(ctx, "海を避ける: " + DbzConfig.avoidOcean + " / 川を避ける: " + DbzConfig.avoidRiver + " / ワックス掛け: " + DbzConfig.waxedChest);
		say(ctx, "レーダー更新: " + DbzConfig.radarUpdateTicks + " tick / 最大距離: " + (DbzConfig.radarMaxRange == 0 ? "無制限" : DbzConfig.radarMaxRange));
		say(ctx, "整合性チェック間隔: " + DbzConfig.auditIntervalTicks + " tick / 消失判定の猶予: " + DbzConfig.lostGraceAudits + " 回");
		return 1;
	}
}
