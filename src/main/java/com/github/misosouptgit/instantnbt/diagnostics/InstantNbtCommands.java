package com.github.misosouptgit.instantnbt.diagnostics;

import com.github.misosouptgit.instantnbt.InstantNBT;
import com.github.misosouptgit.instantnbt.runtime.InstantNbtRuntime;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.List;

/**
 * /instantnbt diagnostics commands (Project Plan 16.1).
 */
public final class InstantNbtCommands {
	private InstantNbtCommands() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("instantnbt")
			.requires(source -> source.hasPermission(2));

		root.then(Commands.literal("memory").executes(ctx -> dump(ctx.getSource(), InstantNbtRuntime.get().diagnostics().memoryLines())));
		root.then(Commands.literal("pool").executes(ctx -> dump(ctx.getSource(), InstantNbtRuntime.get().diagnostics().poolLines())));
		root.then(Commands.literal("ownership").executes(ctx -> dump(ctx.getSource(), InstantNbtRuntime.get().diagnostics().ownershipLines())));
		root.then(Commands.literal("network").executes(ctx -> dump(ctx.getSource(), InstantNbtRuntime.get().diagnostics().networkLines())));
		root.then(Commands.literal("compat").executes(ctx -> dump(ctx.getSource(), InstantNbtRuntime.get().diagnostics().compatLines())));
		root.then(Commands.literal("profiler").executes(ctx -> dump(ctx.getSource(), InstantNbtRuntime.get().diagnostics().profilerLines())));
		root.then(Commands.literal("export").executes(ctx -> {
			try {
				java.nio.file.Path path = InstantNbtRuntime.get().diagnostics().exportJson();
				reply(ctx.getSource(), "exported " + path);
				return 1;
			} catch (Exception ex) {
				reply(ctx.getSource(), "export failed: " + ex.getMessage());
				return 0;
			}
		}));
		root.then(Commands.literal("killswitch")
			.then(Commands.literal("engage").executes(ctx -> {
				InstantNbtRuntime.get().killSwitch().engage("command", InstantNbtRuntime.get());
				reply(ctx.getSource(), "kill switch engaged");
				return 1;
			}))
			.then(Commands.literal("reset").executes(ctx -> {
				InstantNbtRuntime.get().killSwitch().reset();
				reply(ctx.getSource(), "kill switch reset (restart recommended)");
				return 1;
			})));

		dispatcher.register(root);
	}

	private static int dump(CommandSourceStack source, List<String> lines) {
		for (String line : lines) {
			reply(source, line);
		}
		return 1;
	}

	private static void reply(CommandSourceStack source, String msg) {
		InstantNBT.LOGGER.info("[/instantnbt] {}", msg);
		//? if >=1.20 {
		source.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
		//?} else if >=1.19 {
		/*source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(msg), false);
		*///?} else {
		/*source.sendSuccess(new net.minecraft.network.chat.TextComponent(msg), false);
		*///?}
	}
}
