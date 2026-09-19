package cn.blockforge.generated.severownercontrolpanel;

import cn.blockforge.generated.severownercontrolpanel.data.ControlData;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ControlCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String[] TRIGGERS = {"join", "first_join", "interval", "death", "respawn"};
    private static final String[] ACTIONS = {"message", "give", "effect", "teleport", "clear_effects"};
    private static final String[] TEMPLATES = {"join", "first_join", "interval", "death", "respawn"};
    private static final String[] COLORS = {
            "red", "blue", "purple", "green", "lime", "yellow", "gold", "orange", "aqua", "cyan",
            "light_blue", "pink", "magenta", "white", "gray", "grey", "dark_gray", "black", "dark_red",
            "dark_green", "dark_blue", "dark_purple", "teal", "brown", "#41C7A3", "#55FFFF"
    };

    private ControlCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("socp")
                .requires(ControlCommands::canManage)
                .then(Commands.literal("panel").executes(context -> openPanel(context.getSource())))
                // 配置只有显式执行此命令或点击面板保存按钮时才会写入磁盘。
                .then(Commands.literal("save").executes(context -> save(context.getSource())))
                .then(Commands.literal("clear").executes(context -> clear(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("players").executes(context -> players(context.getSource())))
                .then(Commands.literal("respawns").executes(context -> respawns(context.getSource())))
                .then(teleportCommands())
                .then(groupCommands())
                .then(playerCommands())
                .then(itemCommands())
                .then(ruleCommands());
        dispatcher.register(root);
    }

    /** 命令树的 requires 会在解析时检查，事件层还会再次检查，防止旧聊天按钮绕过权限。 */
    private static boolean canManage(CommandSourceStack source) {
        return ControlData.isMultiplayer() && (!source.isPlayer()
                || source.getPlayer() != null && ControlData.canUsePanel(source.getPlayer()));
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(SuggestionsBuilder builder, String... values) {
        return SharedSuggestionProvider.suggest(values, builder);
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestGroups(SuggestionsBuilder builder) {
        return suggest(builder, ControlData.groupNames().toArray(String[]::new));
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestRules(SuggestionsBuilder builder) {
        return suggest(builder, ControlData.ruleIds().toArray(String[]::new));
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlayers(SuggestionsBuilder builder) {
        return suggest(builder, ControlData.playerNames().toArray(String[]::new));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> teleportCommands() {
        var target = Commands.argument("target", StringArgumentType.word())
                .suggests((context, builder) -> suggestTeleportTargets(builder))
                .then(Commands.literal("to")
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ControlCommands::teleportToPosition)))
                .then(Commands.literal("to_owner").executes(ControlCommands::teleportToOwner));
        return Commands.literal("teleport").then(target);
    }

    private static int teleportToPosition(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer executor = source.getPlayer();
        if (executor == null) return feedback(source, "请在游戏内执行传送操作，坐标所在维度取自执行者");
        Vec3 position = Vec3Argument.getVec3(context, "pos");
        List<ServerPlayer> targets = ControlData.findTeleportTargets(StringArgumentType.getString(context, "target"));
        if (targets.isEmpty()) return feedback(source, "未找到在线目标玩家或分组");
        int count = ControlData.teleportPlayers(targets, executor.serverLevel(), position.x, position.y, position.z);
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
        if (server.isSingleplayer() && server.getSingleplayerProfile() != null) {
            return server.getPlayerList().getPlayer(server.getSingleplayerProfile().getId());
        }
        return null;
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
                .then(Commands.argument("name", TokenArgumentType.token())
                        .suggests((context, builder) -> suggestGroups(builder))
                        .then(Commands.argument("color", StringArgumentType.word())
                                .suggests((context, builder) -> suggest(builder, COLORS))
                                .executes(context -> {
                                    ControlData.setGroup(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "color"));
                                    return feedback(context.getSource(), "分组颜色已保存，并已同步在线玩家");
                                })));
        var delete = Commands.literal("delete")
                .then(Commands.argument("name", TokenArgumentType.token())
                        .suggests((context, builder) -> suggestGroups(builder))
                        .executes(context -> {
                            String group = StringArgumentType.getString(context, "name");
                            ControlData.deleteGroup(group);
                            return feedback(context.getSource(), "分组已删除");
                        }));
        var kick = Commands.literal("kick")
                .then(Commands.argument("name", TokenArgumentType.token())
                        .suggests((context, builder) -> suggestGroups(builder))
                        .executes(context -> {
                            String group = StringArgumentType.getString(context, "name");
                            int count = ControlData.kickGroup(group);
                            return feedback(context.getSource(), "已踢出分组 " + group + " 中的 " + count + " 名在线玩家");
                        }));
        return Commands.literal("groups")
                .then(Commands.literal("list").executes(context -> groups(context.getSource())))
                .then(set).then(delete).then(kick);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> playerCommands() {
        var groupAdd = Commands.literal("add")
                .then(Commands.argument("group", TokenArgumentType.token())
                        .suggests((context, builder) -> suggestGroups(builder))
                        .executes(context -> {
                            String player = StringArgumentType.getString(context, "player");
                            boolean changed = ControlData.addPlayerToGroup(player, StringArgumentType.getString(context, "group"));
                            return feedback(context.getSource(), changed ? "玩家已加入分组" : "未找到该玩家记录");
                        }));
        var groupRemove = Commands.literal("remove")
                .then(Commands.argument("group", TokenArgumentType.token())
                        .suggests((context, builder) -> suggestGroups(builder))
                        .executes(context -> {
                            String player = StringArgumentType.getString(context, "player");
                            boolean changed = ControlData.removePlayerFromGroup(player, StringArgumentType.getString(context, "group"));
                            return feedback(context.getSource(), changed ? "玩家已移出分组" : "未找到该玩家记录");
                        }));
        var groups = Commands.literal("group").then(groupAdd).then(groupRemove);
        var kick = Commands.literal("kick").executes(context -> {
            String player = StringArgumentType.getString(context, "player");
            return feedback(context.getSource(), ControlData.kickPlayer(player) ? "玩家已踢出" : "目标玩家不在线");
        });
        var gamemode = Commands.literal("gamemode").then(Commands.literal("cycle").executes(context -> {
            String identifier = StringArgumentType.getString(context, "player");
            ServerPlayer target = ControlData.findOnlinePlayer(identifier);
            String next = ControlData.cycleGameMode(identifier);
            if (next.isBlank() || target == null) return feedback(context.getSource(), "目标玩家不在线");
            ServerPlayer requester = context.getSource().getPlayer();
            if (requester != null) {
                String data = target.getUUID() + "\u001f" + target.getName().getString() + "\u001f" + next;
                SocpNetwork.sendToPlayer(requester, new SocpPayload("gamemode_update", data));
            }
            return feedback(context.getSource(), "玩家游戏模式已切换为 " + next);
        }));
        var panel = Commands.literal("panel")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .suggests((context, builder) -> suggest(builder, "true", "false"))
                        .executes(context -> {
                            String player = StringArgumentType.getString(context, "player");
                            boolean enabled = BoolArgumentType.getBool(context, "enabled");
                            // 目标为服主时由 ControlData 强制保护；拥有面板权限的管理者仍可管理其他玩家。
                            boolean changed = ControlData.setPanelAccess(player, enabled);
                            return feedback(context.getSource(), changed ? "面板权限已更新" : "未找到该玩家记录或该玩家是服主");
                        }));
        var here = Commands.literal("here")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggestRespawnNames(context, builder))
                        .executes(context -> {
                            ServerPlayer player = ControlData.findOnlinePlayer(StringArgumentType.getString(context, "player"));
                            if (player == null) return feedback(context.getSource(), "目标玩家必须在线，才能保存当前位置");
                            return ControlData.addRespawnHere(player, StringArgumentType.getString(context, "name")) ? 1 : 0;
                        }));
        var at = Commands.literal("at")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((context, builder) -> suggestRespawnNames(context, builder))
                                .executes(context -> {
                                    ServerPlayer player = ControlData.findOnlinePlayer(StringArgumentType.getString(context, "player"));
                                    if (player == null) return feedback(context.getSource(), "目标玩家必须在线，才能设置重生点");
                                    var pos = BlockPosArgument.getBlockPos(context, "pos");
                                    return ControlData.addRespawnAt(player, context.getSource().getLevel(), StringArgumentType.getString(context, "name"), pos.getX(), pos.getY(), pos.getZ()) ? 1 : 0;
                                })));
        var select = Commands.literal("select")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggestRespawnNames(context, builder))
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
                        .suggests((context, builder) -> suggestRespawnNames(context, builder))
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
                .then(select).then(cycle).then(delete).then(here).then(at);
        return Commands.literal("player")
                .then(Commands.argument("player", TokenArgumentType.token())
                        .suggests((context, builder) -> suggestPlayers(builder))
                        .then(groups).then(panel).then(kick).then(gamemode).then(respawn));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> itemTargetArg() {
        return Commands.argument("target", TokenArgumentType.token())
                .suggests((context, builder) -> suggestTargets(builder));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> itemInputArg() {
        return Commands.argument("item", TokenArgumentType.token())
                .suggests((context, builder) -> suggestItemIds(builder));
    }

    /**
     * 读取一段"允许包含冒号等特殊字符"的参数。原版 StringArgumentType.string()/word() 在
     * 无引号时只接受 [A-Za-z0-9_.+-]，minecraft:apple 这类带命名空间的 ID 会被截断并报
     * "参数后应有空格分隔，但发现了紧邻的数据"；本类型改为读到下一个空白为止，
     * 带引号时仍按引号字符串解析，因此含空格的中文/英文名也可以引号形式输入。
     */
    public static final class TokenArgumentType implements ArgumentType<String> {
        private static final TokenArgumentType INSTANCE = new TokenArgumentType();
        private static final SimpleCommandExceptionType MISSING_INPUT =
                new SimpleCommandExceptionType(Component.literal("此处需要输入一个参数"));

        private TokenArgumentType() {}

        public static TokenArgumentType token() { return INSTANCE; }

        @Override
        public String parse(StringReader reader) throws CommandSyntaxException {
            if (reader.canRead() && (reader.peek() == '"' || reader.peek() == '\'')) {
                return reader.readQuotedString();
            }
            int start = reader.getCursor();
            while (reader.canRead() && !Character.isWhitespace(reader.peek())) reader.skip();
            String token = reader.getString().substring(start, reader.getCursor());
            if (token.isEmpty()) throw MISSING_INPUT.create();
            return token;
        }
    }

    /**
     * 自定义参数类型必须注册进 minecraft:command_argument_type 注册表，命令树才能同步给客户端；
     * 否则在"对局域网开放"或玩家进服下发命令树时，ArgumentTypeInfos.byClass 找不到本类型的
     * 序列化信息，会直接抛 "Unrecognized argument type ..." 导致崩溃。
     * TokenArgumentType 是无状态单例，因此用 SingletonArgumentInfo.contextFree（与 NeoForge
     * 注册 ModIdArgument 的方式一致）；未安装本模组的原版客户端收到命令树时，NeoForge 的
     * VanillaConnectionNetworkFilter 会自动剥离非原版命名空间的参数节点，不会因此断线。
     */
    private static final DeferredRegister<ArgumentTypeInfo<?, ?>> COMMAND_ARGUMENT_TYPES =
            DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, GeneratedMod.MOD_ID);

    /** 必须在模组构造阶段挂到 Mod 事件总线（由 GeneratedMod 构造函数调用）。 */
    public static void registerArgumentTypes(IEventBus modBus) {
        COMMAND_ARGUMENT_TYPES.register("token",
                () -> ArgumentTypeInfos.registerByClass(TokenArgumentType.class,
                        SingletonArgumentInfo.contextFree(TokenArgumentType::token)));
        COMMAND_ARGUMENT_TYPES.register(modBus);
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestItemIds(SuggestionsBuilder builder) {
        String needle = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
        List<String> values = new ArrayList<>();
        for (net.minecraft.resources.ResourceLocation id : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) {
            if (needle.isEmpty() || id.getPath().startsWith(needle) || id.toString().startsWith(needle)) values.add(id.toString());
            if (values.size() >= 60) break;
        }
        // 补全值可能带引号，TokenArgumentType 对引号与裸 ID 都能解析。
        return suggest(builder, values.toArray(String[]::new));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> itemCommands() {
        var disable = Commands.literal("disable").then(itemTargetArg()
                .then(itemInputArg().executes(context -> {
                    String target = StringArgumentType.getString(context, "target");
                    String item = StringArgumentType.getString(context, "item");
                    boolean changed = ControlData.setItemDisabled(target, item, true);
                    return feedback(context.getSource(), changed ? "已禁用该对象使用该物品，其右键使用将被拦截" : "未能识别对象或物品");
                })));
        var enable = Commands.literal("enable").then(itemTargetArg()
                .then(itemInputArg().executes(context -> {
                    String target = StringArgumentType.getString(context, "target");
                    String item = StringArgumentType.getString(context, "item");
                    boolean changed = ControlData.setItemDisabled(target, item, false);
                    return feedback(context.getSource(), changed ? "已恢复该对象使用该物品的权限" : "未能识别对象或物品");
                })));
        var cooldown = Commands.literal("cooldown").then(itemTargetArg()
                .then(itemInputArg()
                        .then(Commands.argument("seconds", TokenArgumentType.token())
                                .executes(context -> {
                                    String target = StringArgumentType.getString(context, "target");
                                    String item = StringArgumentType.getString(context, "item");
                                    String raw = StringArgumentType.getString(context, "seconds").trim();
                                    double seconds;
                                    try {
                                        seconds = Double.parseDouble(raw);
                                    } catch (NumberFormatException invalid) {
                                        return feedback(context.getSource(), "冷却时间必须是数字，例如 5 或 1.5");
                                    }
                                    if (!ControlData.isValidCooldown(seconds)) {
                                        return feedback(context.getSource(), "冷却时间需大于 0 且不超过 " + ControlData.MAX_COOLDOWN_SECONDS + " 秒");
                                    }
                                    boolean changed = ControlData.setItemCooldown(target, item, seconds);
                                    return feedback(context.getSource(), changed
                                            ? "物品冷却已设置为 " + ControlData.formatSeconds(seconds) + " 秒"
                                            : "未能识别对象或物品");
                                }))));
        var cooldownReset = Commands.literal("cooldown_reset").then(itemTargetArg()
                .then(itemInputArg().executes(context -> {
                    String target = StringArgumentType.getString(context, "target");
                    String item = StringArgumentType.getString(context, "item");
                    boolean changed = ControlData.resetItemCooldown(target, item);
                    return feedback(context.getSource(), changed ? "该物品冷却已恢复默认值（0 秒）" : "未能识别对象或物品");
                })));
        var status = Commands.literal("status").then(itemTargetArg()
                .then(itemInputArg().executes(context -> {
                    String target = StringArgumentType.getString(context, "target");
                    String item = StringArgumentType.getString(context, "item");
                    ServerPlayer requester = context.getSource().getPlayer();
                    if (requester != null) {
                        ControlData.sendItemStatus(requester, target, item);
                        // 界面回包负责按钮文字，聊天反馈保证玩家一定能看到查询结果。
                        return feedback(context.getSource(), ControlData.describeItemStatus(target, item));
                    }
                    String[] resolved = ControlData.resolveTarget(target);
                    String resolvedItem = ControlData.resolveItemString(item);
                    return feedback(context.getSource(), "对象解析: " + (resolved == null ? "失败" : resolved[2]) + " | 物品解析: " + (resolvedItem == null ? "失败" : resolvedItem));
                })));
        var list = Commands.literal("list").executes(context -> {
            String result = ControlData.listItemRules();
            return feedback(context.getSource(), result.isBlank() ? "暂无物品规则" : result);
        });
        // 一键清除全部物品规则：与面板按钮共用同一条路径，避免两边行为不一致。
        var clear = Commands.literal("clear").executes(context -> {
            int count = ControlData.clearItemRules();
            return feedback(context.getSource(), count == 0
                    ? "当前没有任何物品规则"
                    : "已清除全部 " + count + " 条物品规则，点击保存后才会写入文件");
        });
        // “禁用物品列表”按钮入口：玩家目标回物品清单，分组目标回组内玩家列表。
        var listView = Commands.literal("list_view").then(itemTargetArg().executes(context -> {
            ServerPlayer requester = context.getSource().getPlayer();
            String target = StringArgumentType.getString(context, "target");
            if (requester == null) return feedback(context.getSource(), "请在游戏内使用此操作");
            if (ControlData.sendBlockedList(requester, target)) {
                // 明确回执：列表为空时玩家能区分“命令没生效”和“确实没有规则”。
                return feedback(context.getSource(), "已下发禁用物品列表：" + target);
            }
            return feedback(context.getSource(), "未能识别对象：" + target);
        }));
        // 批量切换可使用状态：不带 spec 时切换玩家列表中的全部物品。
        var batch = Commands.literal("batch").then(itemTargetArg()
                .executes(context -> batchToggle(context, ""))
                .then(Commands.argument("spec", TokenArgumentType.token())
                        .executes(context -> batchToggle(context, StringArgumentType.getString(context, "spec")))));
        return Commands.literal("item").then(disable).then(enable).then(cooldown).then(cooldownReset)
                .then(status).then(clear).then(list).then(listView).then(batch);
    }

    private static int batchToggle(CommandContext<CommandSourceStack> context, String spec) {
        CommandSourceStack source = context.getSource();
        ServerPlayer requester = source.getPlayer();
        if (requester == null) return feedback(source, "请在游戏内使用此操作");
        String target = StringArgumentType.getString(context, "target");
        int count = ControlData.toggleItemsByRange(requester, target, spec);
        if (count < 0) return feedback(source, "批量切换只作用于玩家物品列表，请先输入玩家名或 UUID（分组请先点开成员）");
        return feedback(source, count == 0 ? "没有匹配到可切换的物品" : "已切换 " + count + " 件物品的可使用状态");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> ruleCommands() {        var remove = Commands.literal("remove")
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
                .suggests((context, builder) -> suggestTargets(builder)).then(action);
        var trigger = Commands.argument("trigger", StringArgumentType.word())
                .suggests((context, builder) -> suggest(builder, TRIGGERS)).then(target);
        var add = Commands.literal("add")
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((context, builder) -> suggestRules(builder)).then(trigger));
        var template = Commands.literal("template")
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((context, builder) -> suggestRules(builder))
                        .then(Commands.argument("template", StringArgumentType.word())
                                .suggests((context, builder) -> suggest(builder, TEMPLATES))
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests((context, builder) -> suggestTargets(builder))
                                        .executes(context -> {
                                            ControlData.addRule(StringArgumentType.getString(context, "id"), StringArgumentType.getString(context, "template"), StringArgumentType.getString(context, "target"), "message", "欢迎来到服务器", 0);
                                            return feedback(context.getSource(), "规则模板已创建");
                                        }))));
        return Commands.literal("rule")
                .then(Commands.literal("list").executes(context -> rules(context.getSource())))
                .then(remove).then(add).then(template);
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
        return suggest(builder, ControlData.respawnNames(StringArgumentType.getString(context, "player")).toArray(String[]::new));
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
                .append(Component.literal("  ")).append(panelButton("玩家列表", "/socp players"))
                .append(Component.literal("  ")).append(panelButton("分组列表", "/socp groups list")));
        player.sendSystemMessage(panelButton("规则列表", "/socp rule list")
                .append(Component.literal("  ")).append(panelButton("重生点记录", "/socp respawns"))
                .append(Component.literal("  ")).append(panelButton("保存配置", "/socp save")));
        player.sendSystemMessage(Component.literal("传送操作请直接使用：/socp teleport <玩家名|分组名|all> to <x> <y> <z>"));
    }

    private static MutableComponent panelButton(String label, String command) {
        return Component.literal("[" + label + "]")
                .setStyle(Style.EMPTY.withColor(0x41C7A3).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    private static int save(CommandSourceStack source) {
        ControlData.save();
        return feedback(source, "配置已保存，并已更新备份");
    }

    private static int clear(CommandSourceStack source) {
        if (source.isPlayer() && !ControlData.isOwnerOrManager(source.getPlayer())) {
            return feedback(source, "只有服主或管理员可以清除全部配置");
        }
        ControlData.clearAll();
        return feedback(source, "所有配置已清除，点击保存后才会写入文件");
    }

    private static int status(CommandSourceStack source) { return feedback(source, ControlData.summary()); }
    private static int players(CommandSourceStack source) { String list = ControlData.listPlayers(); return feedback(source, list.isBlank() ? "暂无玩家记录" : list); }
    private static int groups(CommandSourceStack source) { String list = ControlData.listGroups(); return feedback(source, list.isBlank() ? "暂无分组" : list); }
    private static int rules(CommandSourceStack source) { String list = ControlData.listRules(); return feedback(source, list.isBlank() ? "暂无规则" : list); }

    private static int feedback(CommandSourceStack source, String message) {
        LOGGER.info("命令执行反馈: {}", message);
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
