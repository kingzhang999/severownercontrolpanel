package cn.blockforge.generated.severownercontrolpanel;

import cn.blockforge.generated.severownercontrolpanel.data.ControlData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ControlCommands {
    private static final String[] TRIGGERS = {"join", "first_join", "interval", "death", "respawn"};
    private static final String[] ACTIONS = {"message", "give", "effect", "teleport", "clear_effects"};
    private static final String[] TEMPLATES = {"join", "first_join", "interval", "death", "respawn"};

    private ControlCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("socp")
                .requires(ControlCommands::canManage)
                .then(Commands.literal("panel").executes(context -> openPanel(context.getSource())))
                .then(Commands.literal("save").executes(context -> save(context.getSource())))
                .then(Commands.literal("clear").executes(context -> clear(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("players").executes(context -> players(context.getSource())))
                .then(Commands.literal("respawns").executes(context -> respawns(context.getSource())))
                .then(teleportCommands())
                .then(groupCommands())
                .then(playerCommands())
                .then(ruleCommands());
        dispatcher.register(root);
    }

    private static boolean canManage(CommandSourceStack source) {
        return !source.isPlayer() || source.getPlayer() != null && ControlData.canUsePanel(source.getPlayer());
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(SuggestionsBuilder builder, String... values) {
        return SharedSuggestionProvider.suggest(values, builder);
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestGroups(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ControlData.groupNames(), builder);
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestRules(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ControlData.ruleIds(), builder);
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlayers(SuggestionsBuilder builder) {
        return suggest(builder, ControlData.playerNames().toArray(String[]::new));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> teleportCommands() {
        var target = Commands.argument("target", StringArgumentType.word())
                .suggests((context, builder) -> suggestTeleportTargets(builder))
                .then(Commands.literal("to")
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(context -> teleportToPosition(context))))
                .then(Commands.literal("to_owner")
                        .executes(context -> teleportToOwner(context)));
        return Commands.literal("teleport").then(target);
    }

    private static int teleportToPosition(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer executor = source.getPlayer();
        if (executor == null) return feedback(source, "请在游戏内执行传送操作，坐标所在维度取自执行者");
        Vec3 position = Vec3Argument.getVec3(context, "pos");
        List<ServerPlayer> targets = ControlData.findTeleportTargets(StringArgumentType.getString(context, "target"));
        if (targets.isEmpty()) return feedback(source, "未找到在线目标玩家或分组");
        int count = ControlData.teleportPlayers(targets, source.getLevel(), position.x, position.y, position.z);
        return feedback(source, "已将 " + count + " 名玩家传送到指定坐标");
    }

    private static int teleportToOwner(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer owner = findOwner(source);
        if (owner == null) return feedback(source, "未找到在线服主；专用服务器请由服主本人执行此操作");
        List<ServerPlayer> targets = ControlData.findTeleportTargets(StringArgumentType.getString(context, "target"));
        if (targets.isEmpty()) return feedback(source, "未找到在线目标玩家或分组");
        int count = ControlData.teleportPlayers(targets, owner.serverLevel(), owner.getX(), owner.getY(), owner.getZ());
        return feedback(source, "已将 " + count + " 名玩家传送到服主所在位置");
    }

    private static ServerPlayer findOwner(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (source.getPlayer() != null && server.isSingleplayer() && server.isSingleplayerOwner(source.getPlayer().getGameProfile())) {
            return source.getPlayer();
        }
        if (server.isSingleplayer() && server.getSingleplayerProfile() != null) {
            return server.getPlayerList().getPlayer(server.getSingleplayerProfile().getId());
        }
        return source.getPlayer();
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestTeleportTargets(SuggestionsBuilder builder) {
        List<String> values = new ArrayList<>();
        values.add("all");
        values.addAll(ControlData.groupNames());
        values.addAll(ControlData.playerNames());
        return suggest(builder, values.toArray(String[]::new));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> groupCommands() {
        var set = Commands.literal("set")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> suggestGroups(builder))
                        .then(Commands.argument("color", StringArgumentType.word())
                                .suggests((context, builder) -> suggest(builder, "#41C7A3", "#55FFFF", "#FFAA00", "#FF5555", "aqua", "gold", "red"))
                                .executes(context -> {
                                    ControlData.setGroup(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "color"));
                                    return feedback(context.getSource(), "分组颜色已保存，并已同步在线玩家");
                                })));
        var delete = Commands.literal("delete")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> suggestGroups(builder))
                        .executes(context -> {
                            ControlData.deleteGroup(StringArgumentType.getString(context, "name"));
                            return feedback(context.getSource(), "分组已删除");
                        }));
        return Commands.literal("groups")
                .then(Commands.literal("list").executes(context -> groups(context.getSource())))
                .then(set)
                .then(delete);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> playerCommands() {
        var groupAdd = Commands.literal("add")
                .then(Commands.argument("group", StringArgumentType.word())
                        .suggests((context, builder) -> suggestGroups(builder))
                        .executes(context -> {
                            String player = StringArgumentType.getString(context, "player");
                            boolean changed = ControlData.addPlayerToGroup(player, StringArgumentType.getString(context, "group"));
                            return feedback(context.getSource(), changed ? "玩家已加入分组" : "未找到该玩家记录");
                        }));
        var groupRemove = Commands.literal("remove")
                .then(Commands.argument("group", StringArgumentType.word())
                        .suggests((context, builder) -> suggestGroups(builder))
                        .executes(context -> {
                            String player = StringArgumentType.getString(context, "player");
                            boolean changed = ControlData.removePlayerFromGroup(player, StringArgumentType.getString(context, "group"));
                            return feedback(context.getSource(), changed ? "玩家已移出分组" : "未找到该玩家记录");
                        }));
        var groups = Commands.literal("group").then(groupAdd).then(groupRemove);
        var panel = Commands.literal("panel")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .suggests((context, builder) -> suggest(builder, "true", "false"))
                        .executes(context -> {
                            String player = StringArgumentType.getString(context, "player");
                            boolean changed = ControlData.setPanelAccess(player, BoolArgumentType.getBool(context, "enabled"));
                            return feedback(context.getSource(), changed ? "面板权限已更新" : "未找到该玩家记录");
                        }));
        var here = Commands.literal("here")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggestRespawnNames(context, builder))
                        .executes(context -> {
                            ServerPlayer player = ControlData.findOnlinePlayer(StringArgumentType.getString(context, "player"));
                            if (player == null) return feedback(context.getSource(), "目标玩家必须在线，才能保存当前位置");
                            var pos = player.blockPosition();
                            return ControlData.addRespawn(player, StringArgumentType.getString(context, "name"), pos.getX(), pos.getY(), pos.getZ()) ? 1 : 0;
                        }));
        var at = Commands.literal("at")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((context, builder) -> suggestRespawnNames(context, builder))
                                .executes(context -> {
                                    ServerPlayer player = ControlData.findOnlinePlayer(StringArgumentType.getString(context, "player"));
                                    if (player == null) return feedback(context.getSource(), "目标玩家必须在线，才能设置维度");
                                    var pos = BlockPosArgument.getBlockPos(context, "pos");
                                    return ControlData.addRespawn(player, StringArgumentType.getString(context, "name"), pos.getX(), pos.getY(), pos.getZ()) ? 1 : 0;
                                })));
        var select = Commands.literal("select")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggestRespawnNamesForTarget(context, builder))
                        .executes(context -> {
                            String player = StringArgumentType.getString(context, "player");
                            String name = StringArgumentType.getString(context, "name");
                            return feedback(context.getSource(), ControlData.selectRespawn(player, name) ? "当前重生点已更新" : "未找到目标玩家或重生点");
                        }));
        var cycle = Commands.literal("cycle").executes(context -> {
            String identifier = StringArgumentType.getString(context, "player");
            String next = ControlData.cycleRespawn(identifier);
            if (next.isBlank()) return feedback(context.getSource(), "未找到目标玩家或重生点");
            ServerPlayer requester = context.getSource().getPlayer();
            if (requester != null) ControlData.sendRespawnOptions(requester, identifier);
            return feedback(context.getSource(), "当前重生点已切换为 " + next);
        });
        var delete = Commands.literal("delete")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggestRespawnNamesForTarget(context, builder))
                        .executes(context -> {
                            String player = StringArgumentType.getString(context, "player");
                            String name = StringArgumentType.getString(context, "name");
                            return feedback(context.getSource(), ControlData.deleteRespawn(player, name) ? "重生点已删除" : "未找到目标玩家或重生点");
                        }));
        var respawn = Commands.literal("respawn")
                .then(Commands.literal("list").executes(context -> respawns(context.getSource())))
                .then(Commands.literal("options").executes(context -> {
                    ServerPlayer requester = context.getSource().getPlayer();
                    if (requester == null) return feedback(context.getSource(), "请在游戏内使用此操作");
                    ControlData.sendRespawnOptions(requester, StringArgumentType.getString(context, "player"));
                    return 1;
                }))
                .then(select)
                .then(cycle)
                .then(delete)
                .then(here)
                .then(at);
        return Commands.literal("player")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> suggestPlayers(builder))
                        .then(groups)
                        .then(panel)
                        .then(respawn));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> ruleCommands() {
        var remove = Commands.literal("remove")
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((context, builder) -> suggestRules(builder))
                        .executes(context -> {
                            ControlData.removeRule(StringArgumentType.getString(context, "id"));
                            return feedback(context.getSource(), "规则已删除");
                        }));
        var value = Commands.argument("value", StringArgumentType.greedyString())
                .suggests((context, builder) -> suggestRuleValue(context, builder))
                .executes(context -> addRule(context, StringArgumentType.getString(context, "value")));
        var action = Commands.argument("action", StringArgumentType.word())
                .suggests((context, builder) -> suggest(builder, ACTIONS))
                .executes(context -> addRule(context, ""))
                .then(value);
        var target = Commands.argument("target", StringArgumentType.word())
                .suggests((context, builder) -> suggestTargets(builder))
                .then(action);
        var trigger = Commands.argument("trigger", StringArgumentType.word())
                .suggests((context, builder) -> suggest(builder, TRIGGERS))
                .then(target);
        var add = Commands.literal("add")
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((context, builder) -> suggestRules(builder))
                        .then(trigger));
        var template = Commands.literal("template")
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((context, builder) -> suggestRules(builder))
                        .then(Commands.argument("template", StringArgumentType.word())
                                .suggests((context, builder) -> suggest(builder, TEMPLATES))
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests((context, builder) -> suggestTargets(builder))
                                        .executes(context -> {
                                            String id = StringArgumentType.getString(context, "id");
                                            String templateName = StringArgumentType.getString(context, "template");
                                            String targetName = StringArgumentType.getString(context, "target");
                                            ControlData.addRule(id, templateName, targetName, "message", "欢迎来到服务器", 0);
                                            return feedback(context.getSource(), "规则模板已创建");
                                        }))));
        return Commands.literal("rule")
                .then(Commands.literal("list").executes(context -> rules(context.getSource())))
                .then(remove)
                .then(add)
                .then(template);
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestTargets(SuggestionsBuilder builder) {
        List<String> values = new ArrayList<>();
        values.add("all"); values.addAll(ControlData.groupNames()); values.addAll(ControlData.playerNames());
        return suggest(builder, values.toArray(String[]::new));
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestRuleValue(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String action = StringArgumentType.getString(context, "action");
        return switch (action) {
            case "give" -> suggest(builder, "minecraft:bread 16", "minecraft:torch 32");
            case "effect" -> suggest(builder, "speed 30 1", "night_vision 120 0");
            case "teleport" -> suggest(builder, "0,64,0");
            case "message" -> suggest(builder, "欢迎来到服务器");
            default -> suggest(builder, "");
        };
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestRespawnNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String identifier = StringArgumentType.getString(context, "player");
        return suggest(builder, ControlData.respawnNames(identifier).toArray(String[]::new));
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestRespawnNamesForTarget(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return suggestRespawnNames(context, builder);
    }

    private static int addRule(CommandContext<CommandSourceStack> context, String value) {
        ControlData.addRule(StringArgumentType.getString(context, "id"), StringArgumentType.getString(context, "trigger"), StringArgumentType.getString(context, "target"), StringArgumentType.getString(context, "action"), value, 0);
        return feedback(context.getSource(), "规则已添加");
    }

    private static int respawns(CommandSourceStack source) {
        String list = ControlData.listAllRespawns();
        return feedback(source, list.isBlank() ? "暂无重生点记录" : list);
    }

    private static int openPanel(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return feedback(source, "请在游戏内使用 /socp panel");
        if (SocpNetwork.sendToPlayer(player, new SocpPayload("open_panel", ""))) return 1;
        sendVanillaPanel(player);
        return 1;
    }

    private static void sendVanillaPanel(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("[服务器管理面板] 未安装客户端模组，已切换为原版指令面板。"));
        player.sendSystemMessage(panelButton("查看状态", "/socp status")
                .append(Component.literal("  "))
                .append(panelButton("玩家列表", "/socp players"))
                .append(Component.literal("  "))
                .append(panelButton("分组列表", "/socp groups list")));
        player.sendSystemMessage(panelButton("规则列表", "/socp rule list")
                .append(Component.literal("  "))
                .append(panelButton("重生点记录", "/socp respawns"))
                .append(Component.literal("  "))
                .append(panelButton("保存配置", "/socp save")));
        player.sendSystemMessage(Component.literal("传送操作请直接使用：/socp teleport <玩家名|分组名|all> to <x> <y> <z>"));
    }

    private static MutableComponent panelButton(String label, String command) {
        return Component.literal("[" + label + "]")
                .setStyle(Style.EMPTY.withColor(0x41C7A3).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    private static int save(CommandSourceStack source) { ControlData.save(); return feedback(source, "配置已保存，并已更新备份"); }
    private static int clear(CommandSourceStack source) {
        if (!ControlData.isManager(new ControlData.CommandSenderLike() {
            public boolean isPlayer() { return source.isPlayer(); }
            public ServerPlayer player() { return source.getPlayer(); }
        })) return feedback(source, "只有服主或控制台可以清除全部配置");
        ControlData.clearAll();
        return feedback(source, "所有配置已清除");
    }
    private static int status(CommandSourceStack source) { return feedback(source, ControlData.summary()); }
    private static int players(CommandSourceStack source) { String list = ControlData.listPlayers(); return feedback(source, list.isBlank() ? "暂无玩家记录" : list); }
    private static int groups(CommandSourceStack source) { String list = ControlData.listGroups(); return feedback(source, list.isBlank() ? "暂无分组" : list); }
    private static int rules(CommandSourceStack source) { String list = ControlData.listRules(); return feedback(source, list.isBlank() ? "暂无规则" : list); }
    private static int feedback(CommandSourceStack source, String message) { source.sendSuccess(() -> Component.literal(message), false); return 1; }
}
