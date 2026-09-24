package cn.blockforge.generated.severownercontrolpanel.data;

import cn.blockforge.generated.severownercontrolpanel.SocpNetwork;
import cn.blockforge.generated.severownercontrolpanel.SocpPayload;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ControlData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "severownercontrolpanel.json";
    private static final Map<UUID, PlayerRecord> PLAYERS = new LinkedHashMap<>();
    private static final Map<String, GroupRecord> GROUPS = new LinkedHashMap<>();
    private static final List<RuleRecord> RULES = new ArrayList<>();
    private static final List<ItemRuleRecord> ITEM_RULES = new ArrayList<>();
    /**
     * 本模组自己的物品冷却：UUID -> 物品 -> 冷却结束时的服务端游戏刻。
     *
     * <p>判定与计时不再依赖原版 {@link net.minecraft.world.item.ItemCooldowns}：原版在
     * {@code ServerPlayerGameMode.useItem} 里一旦发现物品处于原版冷却，就会在触发
     * {@code PlayerInteractEvent.RightClickItem} 之前直接返回，导致对着空气使用被冷却物品时
     * 事件与聊天提示全都没有（只有对着方块时 {@code RightClickBlock} 先于冷却检查触发才会提示）。
     * 自建计时后事件始终会触发，判定、提示与解除用同一份数据；面板把冷却改成 0 也会立刻解除。
     */
    private static final Map<UUID, Map<Item, Long>> COOLDOWN_ENDS = new HashMap<>();
    /**
     * 需要在下个 tick 补发给客户端的冷却显示。物品自身 {@code use()} 可能先写入一个更短的原版冷却，
     * 所以延后一个服务端 tick 覆盖它，保证末影珍珠这类瞬发物品的显示与实际冷却一致。
     */
    private static final Map<UUID, Map<Item, Integer>> PENDING_OVERLAYS = new HashMap<>();
    private static final Map<UUID, Long> ITEM_NOTICES = new HashMap<>();
    private static MinecraftServer server;
    private static Path file;
    private static Path legacyFile;
    private static long lastRuleTick;
    private static long nextRespawnOrder;

    private ControlData() {}

    public static void init(MinecraftServer minecraftServer) {
        server = minecraftServer;
        // 规则计时只对当前这次服务器生命周期有意义；重开存档时必须归零，否则会拿上次的游戏刻去比较。
        lastRuleTick = 0;
        LOGGER.info("SeverOwnerControlPanel initialized; multiplayer mode={}", isMultiplayer());
        Path configDirectory = server.getServerDirectory().resolve("config");
        file = configDirectory.resolve(FILE_NAME);
        legacyFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve(FILE_NAME);
        load();
    }

    public static void shutdown() {
        // 配置只在用户明确点击保存后写盘，服务器关闭不再隐式保存。
        LOGGER.info("SeverOwnerControlPanel stopped; pending in-memory changes were discarded");
        server = null;
        file = null;
        legacyFile = null;
    }

    /** 面板权限由服务端实时判断；明确禁用后，即使是管理员也不能绕过。 */
    public static boolean canUsePanel(ServerPlayer player) {
        if (!isMultiplayer() || player == null) return false;
        if (isOwner(player)) return true;
        PlayerRecord record = getPlayer(player);
        return record.panel == null ? player.hasPermissions(2) : record.panel;
    }

    /** 本模组只在多人游戏中工作：局域网开放后的集成服务器也属于多人。 */
    public static boolean isMultiplayer() {
        return server != null && (server.isPublished() || !server.isSingleplayer());
    }

    /** 只有真正的单人服主可以被识别为服主；专用服务器没有内置服主。 */
    public static boolean isOwner(ServerPlayer player) {
        if (server == null || player == null || !server.isSingleplayer() || server.getSingleplayerProfile() == null) return false;
        return server.isSingleplayerOwner(player.getGameProfile());
    }

    public static boolean isOwnerOrManager(ServerPlayer player) {
        return player != null && (isOwner(player) || player.hasPermissions(2));
    }

    private static boolean isOwnerIdentifier(String identifier, ServerPlayer online, PlayerRecord record) {
        if (online != null) return isOwner(online);
        if (server == null || !server.isSingleplayer() || server.getSingleplayerProfile() == null) return false;
        UUID ownerId = server.getSingleplayerProfile().getId();
        return ownerId.equals(record.uuid) || ownerId.toString().equalsIgnoreCase(identifier.trim());
    }

    public static PlayerRecord getPlayer(ServerPlayer player) {
        PlayerRecord record = PLAYERS.computeIfAbsent(player.getUUID(), id -> new PlayerRecord(player.getName().getString()));
        record.uuid = player.getUUID();
        record.name = player.getName().getString();
        return record;
    }

    public static PlayerRecord findPlayer(String identifier) {
        try {
            PlayerRecord record = PLAYERS.get(UUID.fromString(identifier));
            if (record != null) return record;
        } catch (IllegalArgumentException ignored) {
        }
        for (PlayerRecord record : PLAYERS.values()) {
            if (record.name.equalsIgnoreCase(identifier)) return record;
        }
        return null;
    }

    public static ServerPlayer findOnlinePlayer(String identifier) {
        if (server == null) return null;
        try {
            UUID uuid = UUID.fromString(identifier);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.getUUID().equals(uuid)) return player;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return server.getPlayerList().getPlayerByName(identifier);
    }

    public static List<ServerPlayer> findTeleportTargets(String identifier) {
        if (server == null || identifier == null || identifier.isBlank()) return List.of();
        String target = identifier.trim();
        List<ServerPlayer> result = new ArrayList<>();
        if (target.equals("all") || target.equals("@a")) {
            result.addAll(server.getPlayerList().getPlayers());
            return result;
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online.getName().getString().equalsIgnoreCase(target) || online.getUUID().toString().equalsIgnoreCase(target)) {
                result.add(online);
                return result;
            }
        }
        for (PlayerRecord record : PLAYERS.values()) {
            // 与 isMemberOf / effectiveItemRules 保持一致：分组名匹配忽略大小写。
            if (!isMemberOf(record, target)) continue;
            ServerPlayer online = findOnlinePlayer(record.uuid.toString());
            if (online != null && !result.contains(online)) result.add(online);
        }
        return result;
    }

    public static int teleportPlayers(List<ServerPlayer> targets, ServerLevel destination, double x, double y, double z) {
        if (destination == null) return 0;
        for (ServerPlayer target : targets) {
            target.teleportTo(destination, x, y, z, target.getYRot(), target.getXRot());
        }
        logOperation("teleport", targets.size() + " 名玩家 -> " + destination.dimension().location() + " " + x + " " + y + " " + z);
        return targets.size();
    }

    private static PlayerRecord findOrCreatePlayer(String identifier) {
        PlayerRecord existing = findPlayer(identifier);
        if (existing != null) return existing;
        ServerPlayer online = findOnlinePlayer(identifier);
        return online == null ? null : getPlayer(online);
    }

    public static boolean addPlayerToGroup(String identifier, String group) {
        PlayerRecord record = findOrCreatePlayer(identifier);
        if (record == null) return false;
        GROUPS.computeIfAbsent(group, ignored -> new GroupRecord(group, "#55FFFF"));
        if (!record.groups.contains(group)) record.groups.add(group);
        refreshOnlineTeam(record);
        ServerPlayer member = findOnlinePlayer(record.uuid.toString());
        if (member != null) syncBlockedItems(member);
        logOperation("player_group_add", record.name + " -> " + group);
        return true;
    }

    public static boolean removePlayerFromGroup(String identifier, String group) {
        PlayerRecord record = findPlayer(identifier);
        if (record == null) return false;
        record.groups.remove(group);
        refreshOnlineTeam(record);
        ServerPlayer member = findOnlinePlayer(record.uuid.toString());
        if (member != null) syncBlockedItems(member);
        logOperation("player_group_remove", record.name + " <- " + group);
        return true;
    }

    public static boolean setPanelAccess(String identifier, boolean enabled) {
        PlayerRecord record = findOrCreatePlayer(identifier);
        if (record == null) return false;
        ServerPlayer online = findOnlinePlayer(identifier);
        if (!enabled && isOwnerIdentifier(identifier, online, record)) {
            LOGGER.warn("拒绝禁用服主 {} 的面板权限", record.name);
            return false;
        }
        record.panel = enabled;
        if (!enabled && online != null) {
            // 撤销权限时立即关闭已安装客户端面板，防止继续使用旧界面。
            SocpNetwork.sendToPlayer(online, new SocpPayload("close_panel", ""));
        }
        logOperation("panel_access", record.name + "=" + enabled);
        return true;
    }

    /** 踢出一个在线玩家；目标必须先通过 UUID 或名字解析。 */
    public static boolean kickPlayer(String identifier) {
        ServerPlayer target = findOnlinePlayer(identifier);
        if (target == null) return false;
        LOGGER.info("踢出玩家 {} ({})", target.getName().getString(), target.getUUID());
        target.connection.disconnect(Component.literal("你已被服务器管理面板踢出"));
        return true;
    }

    /** 按分组踢出当前在线的全部成员。 */
    public static int kickGroup(String group) {
        if (server == null || !GROUPS.containsKey(group)) return 0;
        int count = 0;
        for (ServerPlayer player : new ArrayList<>(server == null ? List.of() : server.getPlayerList().getPlayers())) {
            if (getPlayer(player).groups.contains(group)) {
                LOGGER.info("因分组 {} 踢出玩家 {} ({})", group, player.getName().getString(), player.getUUID());
                player.connection.disconnect(Component.literal("你所在的分组已被服务器管理面板踢出"));
                count++;
            }
        }
        return count;
    }

    /** 按生存、创造、冒险、旁观顺序切换玩家模式。 */
    public static String cycleGameMode(String identifier) {
        ServerPlayer target = findOnlinePlayer(identifier);
        if (target == null) return "";
        net.minecraft.world.level.GameType[] modes = {
                net.minecraft.world.level.GameType.SURVIVAL,
                net.minecraft.world.level.GameType.CREATIVE,
                net.minecraft.world.level.GameType.ADVENTURE,
                net.minecraft.world.level.GameType.SPECTATOR
        };
        net.minecraft.world.level.GameType current = target.gameMode.getGameModeForPlayer();
        int next = (java.util.Arrays.asList(modes).indexOf(current) + 1) % modes.length;
        target.setGameMode(modes[next]);
        LOGGER.info("切换玩家 {} ({}) 的游戏模式为 {}", target.getName().getString(), target.getUUID(), modes[next].getName());
        return modes[next].getName();
    }

    private static void logOperation(String operation, String detail) {
        LOGGER.info("管理操作 {}: {}", operation, detail);
    }

    // ==== 物品管理：目标与物品解析 ====

    /** 把面板输入框 A 的组名 / 玩家名 / UUID 解析为 [类型, 键, 描述]；无法识别返回 null。 */
    public static String[] resolveTarget(String identifier) {
        if (server == null || identifier == null || identifier.isBlank()) return null;
        String value = identifier.trim();
        try {
            UUID uuid = UUID.fromString(value);
            PlayerRecord record = PLAYERS.get(uuid);
            if (record != null) return new String[]{"player", uuid.toString(), "玩家 " + record.name};
            ServerPlayer online = server.getPlayerList().getPlayer(uuid);
            if (online != null) return new String[]{"player", uuid.toString(), "玩家 " + online.getName().getString()};
            // 未入库的 UUID 允许作为预配置对象。
            return new String[]{"player", uuid.toString(), "玩家 " + uuid};
        } catch (IllegalArgumentException ignored) {
        }
        PlayerRecord record = findPlayer(value);
        if (record != null) return new String[]{"player", record.uuid.toString(), "玩家 " + record.name};
        ServerPlayer online = server.getPlayerList().getPlayerByName(value);
        if (online != null) return new String[]{"player", online.getUUID().toString(), "玩家 " + online.getName().getString()};
        for (String group : GROUPS.keySet()) {
            if (group.equalsIgnoreCase(value)) return new String[]{"group", group, "分组 " + group};
        }
        return null;
    }

    /** 把物品 ID 或 ID 片段解析为注册表中的规范 ID；无法识别返回 null。 */
    public static String resolveItemString(String input) {
        if (input == null || input.isBlank()) return null;
        String value = input.trim();
        ResourceLocation direct = ResourceLocation.tryParse(value);
        if (direct != null && BuiltInRegistries.ITEM.containsKey(direct)) return direct.toString();
        ResourceLocation lower = ResourceLocation.tryParse(value.toLowerCase(Locale.ROOT));
        if (lower != null && BuiltInRegistries.ITEM.containsKey(lower)) return lower.toString();
        String needle = value.toLowerCase(Locale.ROOT);
        List<ResourceLocation> prefixes = new ArrayList<>();
        List<ResourceLocation> partials = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            String path = id.getPath();
            String full = id.toString();
            if (path.equals(needle) || full.equals(needle)) return full;
            if (path.startsWith(needle) || full.startsWith(needle)) prefixes.add(id);
            else if (path.contains(needle)) partials.add(id);
        }
        if (!prefixes.isEmpty()) return prefixes.get(0).toString();
        if (!partials.isEmpty()) return partials.get(0).toString();
        return null;
    }

    private static String itemId(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "minecraft:air" : id.toString();
    }

    private static ItemRuleRecord findRule(String type, String key, String item) {
        for (ItemRuleRecord rule : ITEM_RULES) {
            if (rule.type.equals(type) && rule.key.equalsIgnoreCase(key) && rule.item.equals(item)) return rule;
        }
        return null;
    }

    private static ItemRuleRecord getOrCreateRule(String targetInput, String itemInput) {
        String[] target = resolveTarget(targetInput);
        String item = resolveItemString(itemInput);
        if (target == null || item == null) return null;
        ItemRuleRecord rule = findRule(target[0], target[1], item);
        if (rule == null) {
            rule = new ItemRuleRecord(target[0], target[1], item);
            ITEM_RULES.add(rule);
        }
        return rule;
    }

    // ==== 物品管理：规则写入 ====

    public static boolean setItemDisabled(String targetInput, String itemInput, boolean disabled) {
        ItemRuleRecord rule = getOrCreateRule(targetInput, itemInput);
        if (rule == null) return false;
        rule.disabled = disabled;
        broadcastBlockedItems();
        logOperation(disabled ? "item_disable" : "item_enable", rule.key + " -> " + rule.item);
        return true;
    }

    /** 冷却上限：16 位整型最大值（32767 秒）。 */
    public static final int MAX_COOLDOWN_SECONDS = Short.MAX_VALUE;

    /**
     * 冷却校验：把输入向零截断为 int 后必须大于 0 且不超过 {@link #MAX_COOLDOWN_SECONDS}。
     * 既支持 1.5 这类非整数输入，又能拦下溢出到 int 之外或过大的数值。
     */
    public static boolean isValidCooldown(double seconds) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds)) return false;
        int truncated = (int) seconds;
        return truncated > 0 && truncated <= MAX_COOLDOWN_SECONDS;
    }

    /** 按用户输入的原始合法数值写入冷却，不做整数化，因此 1.5 秒会被完整保留。 */
    public static boolean setItemCooldown(String targetInput, String itemInput, double seconds) {
        if (!isValidCooldown(seconds)) return false;
        ItemRuleRecord rule = getOrCreateRule(targetInput, itemInput);
        if (rule == null) return false;
        rule.cooldown = seconds;
        clearCooldownForItem(rule.item);
        logOperation("item_cooldown", rule.key + " -> " + rule.item + " = " + formatSeconds(seconds) + "s");
        return true;
    }

    /** 冷却秒数展示文本：整数不带小数点，小数去掉末尾多余的 0。 */
    public static String formatSeconds(double seconds) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds)) return Double.toString(seconds);
        if (seconds == Math.rint(seconds) && Math.abs(seconds) < 1.0e15) return Long.toString((long) seconds);
        return java.math.BigDecimal.valueOf(seconds).stripTrailingZeros().toPlainString();
    }

    /** 恢复默认时长：冷却归零；规则若已无实际内容则整条移除。 */
    public static boolean resetItemCooldown(String targetInput, String itemInput) {
        ItemRuleRecord rule = getOrCreateRule(targetInput, itemInput);
        if (rule == null) return false;
        rule.cooldown = 0;
        if (!rule.disabled) ITEM_RULES.remove(rule);
        clearCooldownForItem(rule.item);
        logOperation("item_cooldown_reset", rule.key + " -> " + rule.item);
        return true;
    }

    /**
     * 一键清除全部物品规则（禁用与冷却一并抹除），并立刻清掉所有在线玩家的残留计时与客户端灰色显示。
     * 与其它配置一样只改内存，用户点击保存后才写入文件。返回被清除的规则条数。
     */
    public static int clearItemRules() {
        int count = ITEM_RULES.size();
        ITEM_RULES.clear();
        clearAllCooldowns();
        ITEM_NOTICES.clear();
        broadcastBlockedItems();
        logOperation("item_rules_clear", "已清除 " + count + " 条物品规则（等待手动保存）");
        return count;
    }

    /** 把当前解析结果与状态回传给打开面板的玩家，用于按钮文字与提示信息。 */
    public static void sendItemStatus(ServerPlayer requester, String targetInput, String itemInput) {
        if (requester == null) return;
        String[] target = resolveTarget(targetInput);
        String item = resolveItemString(itemInput);
        int state = 0;
        double cooldown = 0;
        if (target != null && item != null) {
            ItemRuleRecord rule = findRule(target[0], target[1], item);
            if (rule != null) {
                state = rule.disabled ? 1 : 0;
                cooldown = rule.cooldown;
            }
        }
        String data = String.join("\u001f",
                sanitize(targetInput), target == null ? "" : target[2],
                sanitize(itemInput), item == null ? "" : item,
                Integer.toString(state), formatSeconds(cooldown),
                String.join("\u001e", groupNames()), String.join("\u001e", playerNames()));
        SocpNetwork.sendToPlayer(requester, new SocpPayload("item_status", data));
    }

    /** 查询结果的一行式文字，用于在玩家聊天框里回显，避免只有界面变化没有聊天反馈。 */
    public static String describeItemStatus(String targetInput, String itemInput) {
        String[] target = resolveTarget(targetInput);
        if (target == null) return "未能识别对象：" + targetInput;
        String item = resolveItemString(itemInput);
        if (item == null) return "未能识别物品：" + itemInput;
        ItemRuleRecord rule = findRule(target[0], target[1], item);
        boolean disabled = rule != null && rule.disabled;
        double cooldown = rule == null ? 0 : rule.cooldown;
        return "对象：" + target[2] + " | 物品：" + item + " | 状态：" + (disabled ? "禁用" : "启用")
                + " | 冷却：" + formatSeconds(cooldown) + " 秒";
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace('\u001f', ' ').replace('\u001e', ' ');
    }

    public static String listItemRules() {
        StringBuilder builder = new StringBuilder();
        for (ItemRuleRecord rule : ITEM_RULES) {
            builder.append("player".equals(rule.type) ? describePlayerKey(rule.key) : "分组 " + rule.key)
                    .append(" | ").append(rule.item)
                    .append(" | ").append(rule.disabled ? "禁用" : "启用")
                    .append(" | 冷却 ").append(formatSeconds(rule.cooldown)).append(" 秒\n");
        }
        return builder.toString();
    }

    /** 物品规则里的玩家一栏统一显示为“玩家 名字 (UUID)”，查不到名字时退回纯 UUID。 */
    private static String describePlayerKey(String key) {
        String name = null;
        PlayerRecord record = findPlayer(key);
        if (record != null) name = record.name;
        if ((name == null || name.isBlank()) && server != null) {
            try {
                ServerPlayer online = server.getPlayerList().getPlayer(UUID.fromString(key));
                if (online != null) name = online.getName().getString();
            } catch (IllegalArgumentException ignored) {
            }
        }
        return name == null || name.isBlank() ? "玩家 " + key : "玩家 " + name + " (" + key + ")";
    }

    // ==== 禁用物品列表（面板子界面）：玩家物品规则清单与批量切换 ====

    /**
     * 该玩家当前“实际生效”的禁用 / 冷却规则：既包含直接指向该玩家的规则，
     * 也包含指向其所在分组的规则——分组规则同样会让玩家用不了物品，必须一并展示，
     * 否则只给某个分组配过规则时，玩家子界面会什么都看不到。
     * 顺序与 ITEM_RULES 一致，即子界面 A 的展示顺序（序号从 1 开始），批量切换按同一份顺序解析序号。
     */
    public static List<ItemRuleRecord> effectiveItemRules(String playerKey) {
        PlayerRecord record = findPlayer(playerKey);
        if (record == null) {
            ServerPlayer online = findOnlinePlayer(playerKey);
            if (online != null) record = getPlayer(online);
        }
        List<ItemRuleRecord> rules = new ArrayList<>();
        for (ItemRuleRecord rule : ITEM_RULES) {
            if (!rule.disabled && rule.cooldown <= 0) continue;
            // 只要规则指向该玩家（无论 type 字段是否规范）就算自己的规则，兼容历史/异常数据。
            boolean own = rule.key.equalsIgnoreCase(playerKey) || record != null && rule.key.equalsIgnoreCase(record.uuid.toString());
            if (own) {
                rules.add(rule);
            } else if (!rule.type.equals("player") && record != null && isMemberOf(record, rule.key)) {
                rules.add(rule);
            }
        }
        return rules;
    }

    /** 玩家是否属于该分组（忽略大小写）。 */
    private static boolean isMemberOf(PlayerRecord record, String group) {
        for (String member : record.groups) {
            if (member.equalsIgnoreCase(group)) return true;
        }
        return false;
    }

    /** 规则来源的展示文字：玩家规则显示玩家名，分组规则显示分组名。 */
    private static String ruleSourceDisplay(ItemRuleRecord rule) {
        if (rule.type.equals("player") || findPlayer(rule.key) != null) return describePlayerKey(rule.key);
        return "分组 " + rule.key;
    }

    /**
     * “禁用物品列表”按钮的入口：目标为玩家时直接下发其物品清单（子界面 A），
     * 目标为分组时下发组内玩家列表（子界面 B），两者都通过自定义载荷回给请求者。
     * 目标无法识别时返回 false。
     */
    public static boolean sendBlockedList(ServerPlayer requester, String targetInput) {
        if (requester == null) return false;
        String[] target = resolveTarget(targetInput);
        if (target == null) return false;
        if (target[0].equals("player")) {
            sendPlayerItemList(requester, target[1], target[2]);
            return true;
        }
        StringBuilder builder = new StringBuilder(sanitize(target[1]));
        Set<UUID> added = new LinkedHashSet<>();
        for (PlayerRecord record : PLAYERS.values()) {
            if (!isMemberOf(record, target[1]) || !added.add(record.uuid)) continue;
            builder.append('\u001e').append(sanitize(record.name)).append('\u001f').append(record.uuid);
        }
        // 兜底：在线但尚未写入 PLAYERS 的玩家也列出来，避免配置缺失时组内列表为空。
        if (server != null) {
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                PlayerRecord record = getPlayer(online);
                if (!isMemberOf(record, target[1]) || !added.add(record.uuid)) continue;
                builder.append('\u001e').append(sanitize(record.name)).append('\u001f').append(record.uuid);
            }
        }
        SocpNetwork.sendToPlayer(requester, new SocpPayload("group_players", builder.toString()));
        return true;
    }

    /**
     * 把玩家名下“被禁用或设了冷却”的物品逐行发给请求者，字段以 \u001f 分隔、行以 \u001e 分隔。
     * 每行字段依次为：物品 ID、是否禁用、冷却秒数、规则类型（player/group）、规则键、来源展示名。
     * 客户端据此既能列出分组继承来的限制，也能在编辑时定位到正确的规则对象。
     */
    public static void sendPlayerItemList(ServerPlayer requester, String playerKey, String display) {
        if (requester == null) return;
        StringBuilder builder = new StringBuilder(playerKey).append('\u001e').append(sanitize(display));
        for (ItemRuleRecord rule : effectiveItemRules(playerKey)) {
            builder.append('\u001e').append(rule.item).append('\u001f')
                    .append(rule.disabled ? 1 : 0).append('\u001f').append(formatSeconds(rule.cooldown))
                    .append('\u001f').append(rule.type).append('\u001f').append(sanitize(rule.key))
                    .append('\u001f').append(sanitize(ruleSourceDisplay(rule)));
        }
        SocpNetwork.sendToPlayer(requester, new SocpPayload("blocked_list", builder.toString()));
    }

    /**
     * 批量切换可使用状态：spec 为逗号分隔的序号描述，序号对应子界面 A 的展示顺序，
     * 每段既可以是单个数字（如 "5"）也可以是数字区间（如 "1-3"），可混用（如 "1-3,5"）；
     * spec 为空时切换列表中的全部物品。越界序号自动忽略，重复序号只切换一次。
     * 返回实际切换的物品数；目标不是玩家（分组或无法识别）时返回 -1。
     */
    public static int toggleItemsByRange(ServerPlayer requester, String targetInput, String spec) {
        String[] target = resolveTarget(targetInput);
        if (target == null || !target[0].equals("player")) return -1;
        List<ItemRuleRecord> rules = effectiveItemRules(target[1]);
        Set<Integer> indexes = new LinkedHashSet<>();
        if (spec == null || spec.isBlank()) {
            for (int index = 0; index < rules.size(); index++) indexes.add(index);
        } else {
            for (String token : spec.split(",")) {
                String part = token.trim();
                if (part.isEmpty()) continue;
                int dash = part.indexOf('-');
                try {
                    if (dash < 0) {
                        addRangeIndex(indexes, Integer.parseInt(part), Integer.parseInt(part), rules.size());
                    } else {
                        addRangeIndex(indexes, Integer.parseInt(part.substring(0, dash).trim()),
                                Integer.parseInt(part.substring(dash + 1).trim()), rules.size());
                    }
                } catch (NumberFormatException ignored) {
                    // 非数字片段不参与切换，聊天反馈里的实际条数会体现结果。
                }
            }
        }
        for (int index : indexes) {
            ItemRuleRecord rule = rules.get(index);
            rule.disabled = !rule.disabled;
        }
        broadcastBlockedItems();
        logOperation("item_batch_toggle", target[2] + " 批量切换 " + indexes.size() + " 件物品（" + (spec == null || spec.isBlank() ? "全部" : spec) + "）");
        sendPlayerItemList(requester, target[1], target[2]);
        return indexes.size();
    }

    /** 把 1 起始的闭区间 [from, to] 裁剪到列表长度后写入索引集合；倒序区间按两端相同处理。 */
    private static void addRangeIndex(Set<Integer> indexes, int from, int to, int size) {
        if (to < from) {
            int swap = from;
            from = to;
            to = swap;
        }
        for (int value = Math.max(1, from); value <= Math.min(size, to); value++) indexes.add(value - 1);
    }

    // ==== 物品管理：执行逻辑 ====

    private static boolean matchesItemTarget(ServerPlayer player, PlayerRecord record, ItemRuleRecord rule) {
        if (rule.type.equals("player")) return rule.key.equalsIgnoreCase(player.getUUID().toString());
        if (record == null) return false;
        for (String group : record.groups) {
            if (group.equalsIgnoreCase(rule.key)) return true;
        }
        return false;
    }

    /** 玩家（含其所在分组）是否被禁止使用该物品。 */
    public static boolean isItemBlocked(ServerPlayer player, Item item) {
        if (ITEM_RULES.isEmpty()) return false;
        String id = itemId(item);
        PlayerRecord record = PLAYERS.get(player.getUUID());
        for (ItemRuleRecord rule : ITEM_RULES) {
            if (rule.disabled && rule.item.equals(id) && matchesItemTarget(player, record, rule)) return true;
        }
        return false;
    }

    /**
     * 通知已安装本模组的客户端立刻结束右键使用动画。原版客户端没有该通道时静默跳过，
     * 服务端依旧会拦下物品本身的效果，只是动画由原版自行处理。
     */
    public static void notifyStopUsing(ServerPlayer player) {
        if (player == null) return;
        SocpNetwork.sendToPlayer(player, new SocpPayload("stop_using", ""));
    }

    /**
     * 把该玩家当前被禁用的物品同步给客户端。客户端会在右键预测阶段就取消 {@code ItemStack.use}，
     * 避免食物、药水这类持续使用物品进入原版的“正在使用”状态却永远收不到结束同步。
     */
    public static void syncBlockedItems(ServerPlayer player) {
        if (player == null) return;
        PlayerRecord record = PLAYERS.get(player.getUUID());
        List<String> ids = new ArrayList<>();
        for (ItemRuleRecord rule : ITEM_RULES) {
            if (rule.disabled && matchesItemTarget(player, record, rule) && !ids.contains(rule.item)) ids.add(rule.item);
        }
        SocpNetwork.sendToPlayer(player, new SocpPayload("blocked_items", String.join("\u001e", ids)));
    }

    /** 物品规则或分组变化后，刷新所有在线客户端的禁用列表。 */
    private static void broadcastBlockedItems() {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) syncBlockedItems(player);
    }

    /**
     * 每 tick 兜底：已经开始的被禁用物品使用（例如面板在使用途中才把该物品改为禁用）立即打断。
     * 食物等按住右键持续使用的物品只有这样才会结束，而不是一直“使用中”。
     */
    private static void stopBlockedUses(MinecraftServer minecraftServer) {
        for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) {
            if (!player.isUsingItem()) continue;
            ItemStack using = player.getUseItem();
            if (using.isEmpty() || !isItemBlocked(player, using.getItem())) continue;
            player.stopUsingItem();
            notifyStopUsing(player);
        }
    }

    /** 取所有命中规则中的最长冷却（秒），保留小数精度。 */
    public static double effectiveCooldown(ServerPlayer player, Item item) {
        if (ITEM_RULES.isEmpty()) return 0;
        String id = itemId(item);
        PlayerRecord record = PLAYERS.get(player.getUUID());
        double best = 0;
        for (ItemRuleRecord rule : ITEM_RULES) {
            if (rule.cooldown > 0 && rule.item.equals(id) && matchesItemTarget(player, record, rule)) best = Math.max(best, rule.cooldown);
        }
        return best;
    }

    /** 判定玩家此刻能否使用手持物品；不允许时负责提示（提示做节流）。 */
    public static boolean allowUseItem(ServerPlayer player, ItemStack stack) {
        return allowUseItem(player, stack, true);
    }

    /**
     * mark 为 false 时只判定不记录冷却，避免 RightClickBlock 与 RightClickItem 连发造成重复计时。
     * 需要按住右键持续使用的物品（食物、药水、弓等）不在这里开始计时：右键只是开始使用，
     * 只有真正用完后才由 {@link #markItemUsed(ServerPlayer, ItemStack)} 开始计时，
     * 否则玩家食物还没吃完就会被冷却挡住。
     *
     * <p>冷却完全由本模组自己的计时表判定，原版冷却不再参与拦截，因此对着空气或对着方块使用
     * 被冷却物品都会触发事件并给出“剩余秒数”的聊天提示；把冷却配置改成 0 会立即解除。
     */
    public static boolean allowUseItem(ServerPlayer player, ItemStack stack, boolean mark) {
        if (stack.isEmpty()) return true;
        Item item = stack.getItem();
        if (isItemBlocked(player, item)) {
            if (shouldNotify(player)) player.sendSystemMessage(Component.translatable("message.severownercontrolpanel.item_blocked"));
            // 客户端可能已经预测使用并进入“使用中”，这里立刻通知它结束，避免被禁用物品一直用下去。
            notifyStopUsing(player);
            return false;
        }
        double cooldown = effectiveCooldown(player, item);
        if (cooldown <= 0) {
            // 冷却已被重置为 0：立刻清掉残留计时，保证当前这次就能用。
            clearItemCooldown(player, item);
            return true;
        }
        long remainingTicks = remainingCooldownTicks(player, item);
        if (remainingTicks > 0) {
            long remaining = Math.max(1L, (remainingTicks + 19L) / 20L);
            if (shouldNotify(player)) player.sendSystemMessage(Component.translatable("message.severownercontrolpanel.item_cooldown", remaining));
            notifyStopUsing(player);
            return false;
        }
        // 持续使用的物品留到使用完成时计时；瞬发物品（末影珍珠、投掷物等）在这里排队计时。
        if (mark && stack.getUseDuration(player) <= 0) startItemCooldown(player, item, cooldown);
        return true;
    }

    /** 当前服务端游戏刻；冷却剩余量按它计算，和聊天提示、解除时刻是同一份数据。 */
    private static long nowTicks() {
        return server == null ? 0L : server.overworld().getGameTime();
    }

    /** 该物品还剩多少 tick 冷却；已到点会顺手清掉计时。 */
    private static long remainingCooldownTicks(ServerPlayer player, Item item) {
        Map<Item, Long> ends = COOLDOWN_ENDS.get(player.getUUID());
        if (ends == null) return 0L;
        Long end = ends.get(item);
        if (end == null) return 0L;
        long remaining = end - nowTicks();
        if (remaining <= 0L) {
            ends.remove(item);
            return 0L;
        }
        return remaining;
    }

    /** 秒数换算成冷却 tick，至少 1 tick。 */
    private static long cooldownTicks(double seconds) {
        return Math.max(1L, Math.min(Integer.MAX_VALUE, (long) Math.ceil(seconds * 20.0)));
    }

    /** 开始冷却：立即写服务端计时，并排队在下个 tick 把显示同步给客户端。 */
    private static void startItemCooldown(ServerPlayer player, Item item, double seconds) {
        long ticks = cooldownTicks(seconds);
        COOLDOWN_ENDS.computeIfAbsent(player.getUUID(), key -> new HashMap<>()).put(item, nowTicks() + ticks);
        PENDING_OVERLAYS.computeIfAbsent(player.getUUID(), key -> new HashMap<>()).put(item, (int) ticks);
    }

    /** 清除单个物品的冷却计时，并让客户端冷却显示立即消失。 */
    private static void clearItemCooldown(ServerPlayer player, Item item) {
        if (player == null) return;
        Map<Item, Long> ends = COOLDOWN_ENDS.get(player.getUUID());
        if (ends != null && ends.remove(item) != null) {
            player.connection.send(new ClientboundCooldownPacket(item, 0));
        }
        Map<Item, Integer> pending = PENDING_OVERLAYS.get(player.getUUID());
        if (pending != null) pending.remove(item);
    }

    /**
     * 面板修改或重置某物品冷却后调用：清掉所有在线玩家针对该物品的残留计时与客户端显示，
     * 避免“配置已改成 0，物品却还因旧计时用不了”。
     */
    private static void clearCooldownForItem(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) return;
        COOLDOWN_ENDS.values().forEach(map -> map.remove(item));
        COOLDOWN_ENDS.values().removeIf(Map::isEmpty);
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Map<Item, Integer> pending = PENDING_OVERLAYS.get(player.getUUID());
            if (pending != null) pending.remove(item);
            player.connection.send(new ClientboundCooldownPacket(item, 0));
        }
    }

    /** 清空所有玩家的本模组冷却，并让客户端显示立即消失。 */
    private static void clearAllCooldowns() {
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Map<Item, Long> ends = COOLDOWN_ENDS.get(player.getUUID());
                if (ends == null) continue;
                for (Item item : new ArrayList<>(ends.keySet())) player.connection.send(new ClientboundCooldownPacket(item, 0));
            }
        }
        COOLDOWN_ENDS.clear();
        PENDING_OVERLAYS.clear();
    }

    /** 把上一个 tick 排队的冷却显示发给客户端；此时物品自身的 use() 已经执行完毕。 */
    private static void flushPendingCooldowns() {
        if (PENDING_OVERLAYS.isEmpty() || server == null) return;
        for (Map.Entry<UUID, Map<Item, Integer>> entry : PENDING_OVERLAYS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            entry.getValue().forEach((item, ticks) -> {
                // 先清掉物品 use() 自己写入的较短原版冷却，再覆盖成本模组配置的时长，
                // 否则那段时间原版会在触发 RightClickItem 之前提前返回，玩家看不到提示。
                player.getCooldowns().removeCooldown(item);
                player.connection.send(new ClientboundCooldownPacket(item, ticks));
            });
        }
        PENDING_OVERLAYS.clear();
    }

    /** 冷却到点后清掉计时，并补发一次解除包，保证客户端显示与服务端判定同步。 */
    private static void pruneExpiredCooldowns() {
        if (COOLDOWN_ENDS.isEmpty() || server == null) return;
        long now = nowTicks();
        for (Map.Entry<UUID, Map<Item, Long>> entry : COOLDOWN_ENDS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            entry.getValue().entrySet().removeIf(cooldown -> {
                if (cooldown.getValue() > now) return false;
                if (player != null) player.connection.send(new ClientboundCooldownPacket(cooldown.getKey(), 0));
                return true;
            });
        }
        COOLDOWN_ENDS.values().removeIf(Map::isEmpty);
    }

    /**
     * 物品真正使用完成时开始计时。食物、药水这类需要耗费时间使用的物品，
     * 使用前被打断（如吃到一半松手）不算一次使用，因此不会进入冷却。
     * 传入 {@code stack} 应为使用前的物品，调用方需在使用完成事件里取原始物品栈。
     */
    public static void markItemUsed(ServerPlayer player, ItemStack stack) {
        if (player == null || stack.isEmpty()) return;
        Item item = stack.getItem();
        if (isItemBlocked(player, item)) return;
        double cooldown = effectiveCooldown(player, item);
        if (cooldown <= 0) return;
        if (remainingCooldownTicks(player, item) > 0) return;
        startItemCooldown(player, item, cooldown);
    }

    private static boolean shouldNotify(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = ITEM_NOTICES.get(player.getUUID());
        if (last != null && now - last < 1000) return false;
        ITEM_NOTICES.put(player.getUUID(), now);
        return true;
    }

    public static void registerLogin(ServerPlayer player) {
        if (!isMultiplayer()) return;
        PlayerRecord record = getPlayer(player);
        boolean first = !record.seen;
        record.name = player.getName().getString();
        record.seen = true;
        applySelectedRespawn(player);
        refreshOnlineTeam(record);
        syncBlockedItems(player);
        LOGGER.info("玩家登录 {} ({})", player.getName().getString(), player.getUUID());
        runRules(player, first ? "first_join" : "join");
        player.sendSystemMessage(Component.translatable(first ? "message.severownercontrolpanel.first_join" : "message.severownercontrolpanel.welcome"));
    }

    public static void registerDeath(LivingEntity entity) {
        if (!isMultiplayer()) return;
        if (entity instanceof ServerPlayer player) {
            applySelectedRespawn(player);
            runRules(player, "death");
        }
    }

    public static void registerRespawn(ServerPlayer player) {
        if (!isMultiplayer()) return;
        applySelectedRespawn(player);
        refreshOnlineTeam(getPlayer(player));
        runRules(player, "respawn");
    }

    public static void tick(MinecraftServer minecraftServer) {
        if (!isMultiplayer()) return;
        flushPendingCooldowns();
        pruneExpiredCooldowns();
        stopBlockedUses(minecraftServer);
        long tick = minecraftServer.overworld().getGameTime();
        if (tick - lastRuleTick < 20) return;
        lastRuleTick = tick;
        for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) runRules(player, "interval");
    }

    /** 保存玩家当前位置时使用目标玩家当前所在维度。 */
    public static boolean addRespawnHere(ServerPlayer player, String name) {
        BlockPos pos = player.blockPosition();
        return addRespawn(player, player.serverLevel(), name, pos.getX(), pos.getY(), pos.getZ());
    }

    /** 保存指定坐标时使用执行管理操作者所在维度。 */
    public static boolean addRespawnAt(ServerPlayer player, ServerLevel dimension, String name, int x, int y, int z) {
        return addRespawn(player, dimension, name, x, y, z);
    }

    private static boolean addRespawn(ServerPlayer player, ServerLevel dimension, String name, int x, int y, int z) {
        String cleanName = name.trim();
        if (cleanName.isEmpty() || dimension == null) return false;
        PlayerRecord record = getPlayer(player);
        RespawnPoint previous = record.respawns.get(cleanName);
        long order = previous == null ? nextRespawnOrder++ : previous.order;
        record.respawns.put(cleanName, new RespawnPoint(cleanName, dimension.dimension().location().toString(), x, y, z, order));
        if (record.selectedRespawn == null || record.selectedRespawn.isBlank()) record.selectedRespawn = cleanName;
        applySelectedRespawn(player);
        logOperation("respawn_save", player.getName().getString() + " -> " + cleanName + " @ " + dimension.dimension().location());
        player.sendSystemMessage(Component.translatable("message.severownercontrolpanel.respawn_saved", cleanName));
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.4f);
        return true;
    }

    public static boolean selectRespawn(String identifier, String name) {
        PlayerRecord record = findPlayer(identifier);
        if (record == null || !record.respawns.containsKey(name)) return false;
        record.selectedRespawn = name;
        ServerPlayer online = findOnlinePlayer(identifier);
        if (online != null) applySelectedRespawn(online);
        logOperation("respawn_select", identifier + " -> " + name);
        return true;
    }

    public static String cycleRespawn(String identifier) {
        PlayerRecord record = findPlayer(identifier);
        if (record == null || record.respawns.isEmpty()) return "";
        List<String> names = respawnNames(identifier);
        int current = names.indexOf(record.selectedRespawn);
        String next = names.get((current + 1 + names.size()) % names.size());
        record.selectedRespawn = next;
        ServerPlayer online = findOnlinePlayer(identifier);
        if (online != null) applySelectedRespawn(online);
        logOperation("respawn_cycle", identifier + " -> " + next);
        return next;
    }

    public static boolean deleteRespawn(String identifier, String name) {
        PlayerRecord record = findPlayer(identifier);
        if (record == null || record.respawns.remove(name) == null) return false;
        if (name.equals(record.selectedRespawn)) {
            record.selectedRespawn = respawnNames(identifier).stream().findFirst().orElse("");
            ServerPlayer online = findOnlinePlayer(identifier);
            if (online != null) {
                if (record.selectedRespawn.isBlank()) online.setRespawnPosition(null, null, 0.0f, false, false);
                else applySelectedRespawn(online);
            }
        }
        logOperation("respawn_delete", identifier + " -> " + name);
        return true;
    }

    /** 清空只修改内存；用户点击保存后才会把清空结果写入文件。 */
    public static void clearAll() {
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.setRespawnPosition(null, null, 0.0f, false, false);
            }
        }
        PLAYERS.clear();
        GROUPS.clear();
        RULES.clear();
        ITEM_RULES.clear();
        clearAllCooldowns();
        ITEM_NOTICES.clear();
        broadcastBlockedItems();
        nextRespawnOrder = 0;
        logOperation("clear_all", "全部配置已清空（等待手动保存）");
    }

    public static String selectedRespawn(String identifier) {
        PlayerRecord record = findPlayer(identifier);
        return record == null || record.selectedRespawn == null ? "" : record.selectedRespawn;
    }

    public static List<String> respawnNames(String identifier) {
        PlayerRecord record = findPlayer(identifier);
        if (record == null) return List.of();
        return record.respawns.values().stream().sorted((a, b) -> Long.compare(a.order, b.order)).map(point -> point.name).toList();
    }

    public static void sendRespawnOptions(ServerPlayer requester, String identifier) {
        String selected = selectedRespawn(identifier);
        String data = selected + "\u001f" + String.join("\u001e", respawnNames(identifier));
        SocpNetwork.sendToPlayer(requester, new SocpPayload("respawn_options", data));
    }

    private static void applySelectedRespawn(ServerPlayer player) {
        PlayerRecord record = getPlayer(player);
        RespawnPoint point = record.respawns.get(record.selectedRespawn);
        if (point == null || server == null) return;
        ResourceLocation location = ResourceLocation.tryParse(point.dimension);
        if (location == null) return;
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, location);
        if (server.getLevel(dimension) == null) return;
        player.setRespawnPosition(dimension, new BlockPos(point.x, point.y, point.z), 0.0f, true, true);
    }

    public static void setGroup(String group, String color) {
        String normalized = normalizeColor(color);
        GROUPS.computeIfAbsent(group, ignored -> new GroupRecord(group, normalized));
        GROUPS.get(group).color = normalized;
        if (server != null) {
            for (PlayerRecord record : PLAYERS.values()) refreshOnlineTeam(record);
        }
        logOperation("group_set", group + "=" + normalized);
    }

    /** 支持 Minecraft 颜色名、常用英文别名和 #RRGGBB。 */
    private static String normalizeColor(String color) {
        String value = color == null ? "" : color.trim().toLowerCase();
        if (value.matches("#[0-9a-f]{6}")) return value.toUpperCase();
        Map<String, String> aliases = Map.ofEntries(
                Map.entry("red", "#FF5555"), Map.entry("blue", "#5555FF"), Map.entry("purple", "#AA00AA"),
                Map.entry("green", "#55FF55"), Map.entry("lime", "#55FF55"), Map.entry("yellow", "#FFFF55"),
                Map.entry("gold", "#FFAA00"), Map.entry("orange", "#FFAA00"), Map.entry("aqua", "#55FFFF"),
                Map.entry("cyan", "#55FFFF"), Map.entry("light_blue", "#5555FF"), Map.entry("pink", "#FF55FF"),
                Map.entry("magenta", "#FF55FF"), Map.entry("white", "#FFFFFF"), Map.entry("gray", "#AAAAAA"),
                Map.entry("grey", "#AAAAAA"), Map.entry("dark_gray", "#555555"), Map.entry("black", "#000000"),
                Map.entry("dark_red", "#AA0000"), Map.entry("dark_green", "#00AA00"), Map.entry("dark_blue", "#0000AA"),
                Map.entry("dark_purple", "#5500AA"), Map.entry("teal", "#008080"), Map.entry("brown", "#AA5500"));
        String alias = aliases.get(value);
        if (alias != null) return alias;
        ChatFormatting formatting = ChatFormatting.getByName(value);
        if (formatting != null && formatting.isColor() && formatting.getColor() != null) {
            return String.format("#%06X", formatting.getColor());
        }
        return "#55FFFF";
    }

    private static void refreshOnlineTeam(PlayerRecord record) {
        if (server == null) return;
        ServerPlayer player = null;
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online.getUUID().equals(record.uuid)) {
                player = online;
                break;
            }
        }
        if (player == null) return;
        for (PlayerTeam team : new ArrayList<>(server.getScoreboard().getPlayerTeams())) {
            if (team.getName().startsWith("socp_") && team.getPlayers().contains(player.getScoreboardName())) {
                server.getScoreboard().removePlayerFromTeam(player.getScoreboardName(), team);
            }
        }
        if (record.groups.isEmpty()) return;
        GroupRecord group = GROUPS.get(record.groups.get(0));
        if (group == null) return;
        String teamName = "socp_" + Integer.toHexString(group.name.hashCode());
        PlayerTeam team = server.getScoreboard().getPlayerTeam(teamName);
        if (team == null) team = server.getScoreboard().addPlayerTeam(teamName);
        Integer rgb = parseRgb(group.color);
        if (rgb != null) {
            team.setPlayerPrefix(Component.literal("[" + group.name + "] ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))));
            ChatFormatting legacy = closestLegacyColor(rgb);
            if (legacy != null) team.setColor(legacy);
        }
        server.getScoreboard().addPlayerToTeam(player.getScoreboardName(), team);
    }

    private static Integer parseRgb(String color) {
        try { return Integer.parseInt(color.substring(1), 16); }
        catch (RuntimeException ignored) { return null; }
    }

    private static ChatFormatting closestLegacyColor(int rgb) {
        ChatFormatting best = null;
        int distance = Integer.MAX_VALUE;
        for (ChatFormatting formatting : ChatFormatting.values()) {
            if (!formatting.isColor() || formatting.getColor() == null) continue;
            int candidate = formatting.getColor();
            int dr = ((rgb >> 16) & 255) - ((candidate >> 16) & 255);
            int dg = ((rgb >> 8) & 255) - ((candidate >> 8) & 255);
            int db = (rgb & 255) - (candidate & 255);
            int next = dr * dr + dg * dg + db * db;
            if (next < distance) { distance = next; best = formatting; }
        }
        return best;
    }

    public static void deleteGroup(String group) {
        GROUPS.remove(group);
        PLAYERS.values().forEach(record -> record.groups.remove(group));
        if (server != null) {
            for (PlayerRecord record : PLAYERS.values()) refreshOnlineTeam(record);
        }
        logOperation("group_delete", group);
    }

    public static void addRule(String id, String trigger, String target, String action, String value, int limit) {
        RULES.removeIf(rule -> rule.id.equals(id));
        RULES.add(new RuleRecord(id, trigger, target, action, value, limit));
        logOperation("rule_add", id + " -> " + trigger + "/" + action);
    }

    public static void removeRule(String id) {
        RULES.removeIf(rule -> rule.id.equals(id));
        logOperation("rule_remove", id);
    }

    public static String summary() { return "玩家 " + PLAYERS.size() + " | 分组 " + GROUPS.size() + " | 规则 " + RULES.size() + " | 物品规则 " + ITEM_RULES.size(); }

    public static String listPlayers() {
        StringBuilder builder = new StringBuilder();
        PLAYERS.forEach((id, record) -> builder.append(record.name).append(" (").append(id).append(") [").append(String.join(",", record.groups)).append("]\n"));
        return builder.toString();
    }

    public static List<String> playerNames() { return PLAYERS.values().stream().map(record -> record.name).sorted(String.CASE_INSENSITIVE_ORDER).toList(); }
    public static String listGroups() { StringBuilder builder = new StringBuilder(); GROUPS.forEach((id, group) -> builder.append(group.name).append(" ").append(group.color).append("\n")); return builder.toString(); }
    public static List<String> groupNames() { return new ArrayList<>(GROUPS.keySet()); }
    public static String listRules() { StringBuilder builder = new StringBuilder(); RULES.forEach(rule -> builder.append(rule.id).append(" -> ").append(rule.trigger).append(" / ").append(rule.action).append("\n")); return builder.toString(); }
    public static List<String> ruleIds() { return RULES.stream().map(rule -> rule.id).toList(); }

    public static String listAllRespawns() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<UUID, PlayerRecord> entry : PLAYERS.entrySet()) {
            PlayerRecord record = entry.getValue();
            if (record.respawns.isEmpty()) continue;
            builder.append(record.name).append(" (").append(entry.getKey()).append(")");
            if (record.selectedRespawn != null && !record.selectedRespawn.isBlank()) builder.append(" 当前=").append(record.selectedRespawn);
            builder.append("\n");
            record.respawns.forEach((name, point) -> builder.append("  ").append(name).append(" ").append(point.dimension).append(" ")
                    .append(point.x).append(" ").append(point.y).append(" ").append(point.z).append("\n"));
        }
        return builder.toString();
    }

    public static void save() {
        if (file == null) return;
        JsonObject root = new JsonObject();
        JsonArray players = new JsonArray(); PLAYERS.forEach((id, record) -> players.add(record.toJson(id.toString()))); root.add("players", players);
        JsonArray groups = new JsonArray(); GROUPS.forEach((id, group) -> groups.add(group.toJson())); root.add("groups", groups);
        JsonArray rules = new JsonArray(); RULES.forEach(rule -> rules.add(rule.toJson())); root.add("rules", rules);
        JsonArray items = new JsonArray(); ITEM_RULES.forEach(rule -> items.add(rule.toJson())); root.add("items", items);
        root.addProperty("schema", 4);
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            if (Files.exists(file)) Files.copy(file, file.resolveSibling(FILE_NAME + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("配置已保存到 {}", file);
        } catch (IOException exception) {
            LOGGER.error("保存配置失败: {}", file, exception);
        }
    }

    private static void load() {
        PLAYERS.clear(); GROUPS.clear(); RULES.clear(); ITEM_RULES.clear(); COOLDOWN_ENDS.clear(); PENDING_OVERLAYS.clear(); ITEM_NOTICES.clear(); nextRespawnOrder = 0; lastRuleTick = 0;
        if (file == null) return;
        Path source = Files.exists(file) ? file : legacyFile;
        if (source == null || !Files.exists(source)) return;
        try {
            JsonObject root;
            try { root = JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8)).getAsJsonObject(); }
            catch (RuntimeException invalid) {
                // 损坏的配置不能再静默丢弃：先说明原因，再尝试从上次保存的备份恢复。
                LOGGER.error("配置文件 {} 内容损坏，尝试从备份恢复", source, invalid);
                Path backup = source.resolveSibling(FILE_NAME + ".bak");
                if (!Files.exists(backup)) {
                    LOGGER.error("备份文件 {} 不存在，本次以空配置启动；请修复或删除原配置后重新保存", backup);
                    return;
                }
                root = JsonParser.parseString(Files.readString(backup, StandardCharsets.UTF_8)).getAsJsonObject();
                LOGGER.warn("已从备份 {} 恢复配置", backup);
            }
            JsonArray players = root.has("players") ? root.getAsJsonArray("players") : new JsonArray();
            for (JsonElement element : players) {
                JsonObject json = element.getAsJsonObject();
                UUID id = UUID.fromString(json.get("uuid").getAsString());
                PlayerRecord record = PlayerRecord.fromJson(json); record.uuid = id; PLAYERS.put(id, record);
                for (RespawnPoint point : record.respawns.values()) {
                    if (point.order < 0) point.order = nextRespawnOrder++;
                    else nextRespawnOrder = Math.max(nextRespawnOrder, point.order + 1);
                }
            }
            JsonArray groups = root.has("groups") ? root.getAsJsonArray("groups") : new JsonArray();
            for (JsonElement element : groups) { GroupRecord group = GroupRecord.fromJson(element.getAsJsonObject()); GROUPS.put(group.name, group); }
            JsonArray rules = root.has("rules") ? root.getAsJsonArray("rules") : new JsonArray();
            for (JsonElement element : rules) RULES.add(RuleRecord.fromJson(element.getAsJsonObject()));
            JsonArray items = root.has("items") ? root.getAsJsonArray("items") : new JsonArray();
            for (JsonElement element : items) ITEM_RULES.add(ItemRuleRecord.fromJson(element.getAsJsonObject()));
            if (source.equals(legacyFile) && !source.equals(file)) {
                LOGGER.info("读取旧版配置文件 {}；等待用户点击保存后迁移到新位置", source);
            }
        } catch (Exception failure) {
            LOGGER.error("读取配置文件 {} 失败，本次以空配置启动（不会覆盖磁盘内容）", source, failure);
        }
    }

    private static void runRules(ServerPlayer player, String trigger) {
        for (RuleRecord rule : RULES) {
            if (!rule.enabled || !rule.trigger.equals(trigger) || !matches(player, rule.target) || (rule.limit > 0 && rule.executions >= rule.limit)) continue;
            executeAction(player, rule.action, rule.value); rule.executions++;
            LOGGER.info("规则执行 {}: player={}, action={}", rule.id, player.getName().getString(), rule.action);
        }
    }

    private static boolean matches(ServerPlayer player, String target) {
        if (target.equals("all")) return true;
        if (target.equalsIgnoreCase(player.getName().getString())) return true;
        return getPlayer(player).groups.contains(target);
    }

    private static void executeAction(ServerPlayer player, String action, String value) {
        switch (action) {
            case "message" -> player.sendSystemMessage(Component.literal(value));
            case "teleport" -> { String[] parts = value.split(","); if (parts.length >= 3) try { player.teleportTo(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2])); } catch (NumberFormatException ignored) { } }
            case "give" -> player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), "give " + player.getName().getString() + " " + value);
            case "effect" -> player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), "effect give " + player.getName().getString() + " " + value);
            case "clear_effects" -> player.removeAllEffects();
            default -> { }
        }
        player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.6f);
    }

    public static final class PlayerRecord {
        UUID uuid; String name; boolean seen; Boolean panel; String selectedRespawn = "";
        final List<String> groups = new ArrayList<>(); final Map<String, RespawnPoint> respawns = new LinkedHashMap<>();
        PlayerRecord(String name) { this.name = name; }
        JsonObject toJson(String id) { JsonObject json = new JsonObject(); json.addProperty("uuid", id); json.addProperty("name", name); json.addProperty("seen", seen); if (panel == null) json.add("panel", com.google.gson.JsonNull.INSTANCE); else json.addProperty("panel", panel); json.addProperty("selectedRespawn", selectedRespawn); JsonArray groupArray = new JsonArray(); groups.forEach(groupArray::add); json.add("groups", groupArray); JsonObject respawnJson = new JsonObject(); respawns.forEach((key, point) -> respawnJson.add(key, point.toJson())); json.add("respawns", respawnJson); return json; }
        static PlayerRecord fromJson(JsonObject json) { PlayerRecord record = new PlayerRecord(json.has("name") ? json.get("name").getAsString() : "未知"); record.seen = getBoolean(json, "seen"); record.panel = json.has("panel") && !json.get("panel").isJsonNull() ? json.get("panel").getAsBoolean() : null; record.selectedRespawn = json.has("selectedRespawn") ? json.get("selectedRespawn").getAsString() : ""; if (json.has("groups")) json.getAsJsonArray("groups").forEach(e -> record.groups.add(e.getAsString())); if (json.has("respawns")) json.getAsJsonObject("respawns").entrySet().forEach(e -> record.respawns.put(e.getKey(), RespawnPoint.fromJson(e.getKey(), e.getValue().getAsJsonObject()))); return record; }
    }
    public static final class GroupRecord { String name; String color; GroupRecord(String name, String color) { this.name = name; this.color = color; } JsonObject toJson() { JsonObject json = new JsonObject(); json.addProperty("name", name); json.addProperty("color", color); return json; } static GroupRecord fromJson(JsonObject json) { return new GroupRecord(json.get("name").getAsString(), normalizeColor(json.has("color") ? json.get("color").getAsString() : "#55FFFF")); } }
    public static final class RespawnPoint { String name; String dimension; int x; int y; int z; long order; RespawnPoint(String name, String dimension, int x, int y, int z, long order) { this.name=name; this.dimension=dimension; this.x=x; this.y=y; this.z=z; this.order=order; } JsonObject toJson() { JsonObject json = new JsonObject(); json.addProperty("dimension", dimension); json.addProperty("x", x); json.addProperty("y", y); json.addProperty("z", z); json.addProperty("order", order); return json; } static RespawnPoint fromJson(String name, JsonObject json) { return new RespawnPoint(name, json.get("dimension").getAsString(), json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt(), json.has("order") ? json.get("order").getAsLong() : -1L); } }
    public static final class ItemRuleRecord {
        String type; String key; String item; boolean disabled; double cooldown;
        ItemRuleRecord(String type, String key, String item) { this.type = type; this.key = key; this.item = item; }
        JsonObject toJson() { JsonObject json = new JsonObject(); json.addProperty("type", type); json.addProperty("key", key); json.addProperty("item", item); json.addProperty("disabled", disabled); json.addProperty("cooldown", cooldown); return json; }
        static ItemRuleRecord fromJson(JsonObject json) {
            ItemRuleRecord rule = new ItemRuleRecord(json.get("type").getAsString(), json.get("key").getAsString(), json.get("item").getAsString());
            rule.disabled = getBoolean(json, "disabled");
            rule.cooldown = json.has("cooldown") ? json.get("cooldown").getAsDouble() : 0.0;
            return rule;
        }
    }
    public static final class RuleRecord { String id; String trigger; String target; String action; String value; int limit; int executions; boolean enabled = true; RuleRecord(String id, String trigger, String target, String action, String value, int limit) { this.id=id; this.trigger=trigger; this.target=target; this.action=action; this.value=value; this.limit=limit; } JsonObject toJson() { JsonObject json = new JsonObject(); json.addProperty("id", id); json.addProperty("trigger", trigger); json.addProperty("target", target); json.addProperty("action", action); json.addProperty("value", value); json.addProperty("limit", limit); json.addProperty("executions", executions); json.addProperty("enabled", enabled); return json; } static RuleRecord fromJson(JsonObject json) { RuleRecord rule = new RuleRecord(json.get("id").getAsString(), json.get("trigger").getAsString(), json.get("target").getAsString(), json.get("action").getAsString(), json.has("value") ? json.get("value").getAsString() : "", json.get("limit").getAsInt()); rule.executions = json.has("executions") ? json.get("executions").getAsInt() : 0; rule.enabled = !json.has("enabled") || json.get("enabled").getAsBoolean(); return rule; } }
    private static boolean getBoolean(JsonObject json, String key) { return json.has(key) && json.get(key).getAsBoolean(); }
}
