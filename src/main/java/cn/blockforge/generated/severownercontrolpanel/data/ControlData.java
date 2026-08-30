package cn.blockforge.generated.severownercontrolpanel.data;

import cn.blockforge.generated.severownercontrolpanel.SocpNetwork;
import cn.blockforge.generated.severownercontrolpanel.SocpPayload;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ControlData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "severownercontrolpanel.json";
    private static final Map<UUID, PlayerRecord> PLAYERS = new LinkedHashMap<>();
    private static final Map<String, GroupRecord> GROUPS = new LinkedHashMap<>();
    private static final List<RuleRecord> RULES = new ArrayList<>();
    private static MinecraftServer server;
    private static Path file;
    private static Path legacyFile;
    private static long lastRuleTick;
    private static long nextRespawnOrder;

    private ControlData() {}

    public static void init(MinecraftServer minecraftServer) {
        server = minecraftServer;
        Path configDirectory = server.getServerDirectory().resolve("config");
        file = configDirectory.resolve(FILE_NAME);
        legacyFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve(FILE_NAME);
        load();
    }

    public static void shutdown() {
        save();
        server = null;
        file = null;
        legacyFile = null;
    }

    public static boolean isManager(CommandSenderLike source) {
        return !source.isPlayer() || source.player().hasPermissions(2);
    }

    public static boolean canUsePanel(ServerPlayer player) {
        return player.hasPermissions(2) || getPlayer(player).panel;
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
            if (!record.groups.contains(target)) continue;
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
        save();
        return true;
    }

    public static boolean removePlayerFromGroup(String identifier, String group) {
        PlayerRecord record = findPlayer(identifier);
        if (record == null) return false;
        record.groups.remove(group);
        refreshOnlineTeam(record);
        save();
        return true;
    }

    public static boolean setPanelAccess(String identifier, boolean enabled) {
        PlayerRecord record = findOrCreatePlayer(identifier);
        if (record == null) return false;
        record.panel = enabled;
        save();
        return true;
    }

    public static void registerLogin(ServerPlayer player) {
        PlayerRecord record = getPlayer(player);
        boolean first = !record.seen;
        record.name = player.getName().getString();
        record.seen = true;
        applySelectedRespawn(player);
        refreshOnlineTeam(record);
        save();
        runRules(player, first ? "first_join" : "join");
        player.sendSystemMessage(Component.translatable(first ? "message.severownercontrolpanel.first_join" : "message.severownercontrolpanel.welcome"));
    }

    public static void registerDeath(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            applySelectedRespawn(player);
            runRules(player, "death");
        }
    }

    public static void registerRespawn(ServerPlayer player) {
        applySelectedRespawn(player);
        refreshOnlineTeam(getPlayer(player));
        runRules(player, "respawn");
    }

    public static void tick(MinecraftServer minecraftServer) {
        if (server == null) return;
        long tick = minecraftServer.overworld().getGameTime();
        if (tick - lastRuleTick < 20) return;
        lastRuleTick = tick;
        for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) runRules(player, "interval");
    }

    public static void markBlock(ServerPlayer player, BlockPos pos) {
        PlayerRecord record = getPlayer(player);
        record.pendingX = pos.getX();
        record.pendingY = pos.getY();
        record.pendingZ = pos.getZ();
        record.pendingDimension = player.serverLevel().dimension().location().toString();
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 16, 0.35, 0.5, 0.35, 0.03);
        player.playNotifySound(SoundEvents.BEACON_POWER_SELECT, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.2f);
        String clipboard = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        SocpNetwork.sendToPlayer(player, new SocpPayload("clipboard", clipboard));
        player.sendSystemMessage(Component.translatable("message.severownercontrolpanel.block_marked", pos.getX(), pos.getY(), pos.getZ()));
    }

    public static boolean addRespawn(ServerPlayer player, String name, int x, int y, int z) {
        String cleanName = name.trim();
        if (cleanName.isEmpty()) return false;
        PlayerRecord record = getPlayer(player);
        RespawnPoint previous = record.respawns.get(cleanName);
        long order = previous == null ? nextRespawnOrder++ : previous.order;
        record.respawns.put(cleanName, new RespawnPoint(cleanName, player.serverLevel().dimension().location().toString(), x, y, z, order));
        if (record.selectedRespawn == null || record.selectedRespawn.isBlank()) record.selectedRespawn = cleanName;
        applySelectedRespawn(player);
        save();
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
        save();
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
        save();
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
        save();
        return true;
    }

    public static void clearAll() {
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.setRespawnPosition(null, null, 0.0f, false, false);
            }
        }
        PLAYERS.clear();
        GROUPS.clear();
        RULES.clear();
        nextRespawnOrder = 0;
        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.resolveSibling(FILE_NAME + ".bak"));
            if (legacyFile != null) {
                Files.deleteIfExists(legacyFile);
                Files.deleteIfExists(legacyFile.resolveSibling(FILE_NAME + ".bak"));
            }
        } catch (IOException ignored) { }
        save();
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
        save();
    }

    private static String normalizeColor(String color) {
        String value = color.trim();
        if (value.matches("#[0-9a-fA-F]{6}")) return value.toUpperCase();
        ChatFormatting formatting = ChatFormatting.getByName(value.toLowerCase());
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
        save();
    }

    public static void addRule(String id, String trigger, String target, String action, String value, int limit) {
        RULES.removeIf(rule -> rule.id.equals(id));
        RULES.add(new RuleRecord(id, trigger, target, action, value, limit));
        save();
    }

    public static void removeRule(String id) {
        RULES.removeIf(rule -> rule.id.equals(id));
        save();
    }

    public static String summary() { return "玩家 " + PLAYERS.size() + " | 分组 " + GROUPS.size() + " | 规则 " + RULES.size(); }

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

    public static String listRespawns(ServerPlayer ignored) { return listAllRespawns(); }

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
        JsonArray rules = new JsonArray(); RULES.forEach(rule -> rules.add(rule.toJson())); root.add("rules", rules); root.addProperty("schema", 3);
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            if (Files.exists(file)) Files.copy(file, file.resolveSibling(FILE_NAME + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) { }
    }

    private static void load() {
        PLAYERS.clear(); GROUPS.clear(); RULES.clear(); nextRespawnOrder = 0;
        if (file == null) return;
        Path source = Files.exists(file) ? file : legacyFile;
        if (source == null || !Files.exists(source)) return;
        try {
            JsonObject root;
            try { root = JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8)).getAsJsonObject(); }
            catch (Exception invalid) {
                Path backup = source.resolveSibling(FILE_NAME + ".bak");
                if (!Files.exists(backup)) return;
                root = JsonParser.parseString(Files.readString(backup, StandardCharsets.UTF_8)).getAsJsonObject();
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
            if (source.equals(legacyFile) && !source.equals(file)) save();
        } catch (Exception ignored) { }
    }

    private static void runRules(ServerPlayer player, String trigger) {
        for (RuleRecord rule : RULES) {
            if (!rule.enabled || !rule.trigger.equals(trigger) || !matches(player, rule.target) || (rule.limit > 0 && rule.executions >= rule.limit)) continue;
            executeAction(player, rule.action, rule.value); rule.executions++;
        }
        save();
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

    public interface CommandSenderLike { boolean isPlayer(); ServerPlayer player(); }

    public static final class PlayerRecord {
        UUID uuid; String name; boolean seen; boolean panel; String selectedRespawn = "";
        int pendingX; int pendingY; int pendingZ; String pendingDimension = "minecraft:overworld";
        final List<String> groups = new ArrayList<>(); final Map<String, RespawnPoint> respawns = new LinkedHashMap<>();
        PlayerRecord(String name) { this.name = name; }
        JsonObject toJson(String id) { JsonObject json = new JsonObject(); json.addProperty("uuid", id); json.addProperty("name", name); json.addProperty("seen", seen); json.addProperty("panel", panel); json.addProperty("selectedRespawn", selectedRespawn); JsonArray groupArray = new JsonArray(); groups.forEach(groupArray::add); json.add("groups", groupArray); JsonObject respawnJson = new JsonObject(); respawns.forEach((key, point) -> respawnJson.add(key, point.toJson())); json.add("respawns", respawnJson); return json; }
        static PlayerRecord fromJson(JsonObject json) { PlayerRecord record = new PlayerRecord(json.has("name") ? json.get("name").getAsString() : "未知"); record.seen = getBoolean(json, "seen"); record.panel = getBoolean(json, "panel"); record.selectedRespawn = json.has("selectedRespawn") ? json.get("selectedRespawn").getAsString() : ""; if (json.has("groups")) json.getAsJsonArray("groups").forEach(e -> record.groups.add(e.getAsString())); if (json.has("respawns")) json.getAsJsonObject("respawns").entrySet().forEach(e -> record.respawns.put(e.getKey(), RespawnPoint.fromJson(e.getKey(), e.getValue().getAsJsonObject()))); return record; }
    }
    public static final class GroupRecord { String name; String color; GroupRecord(String name, String color) { this.name = name; this.color = color; } JsonObject toJson() { JsonObject json = new JsonObject(); json.addProperty("name", name); json.addProperty("color", color); return json; } static GroupRecord fromJson(JsonObject json) { return new GroupRecord(json.get("name").getAsString(), normalizeColor(json.has("color") ? json.get("color").getAsString() : "#55FFFF")); } }
    public static final class RespawnPoint { String name; String dimension; int x; int y; int z; long order; RespawnPoint(String name, String dimension, int x, int y, int z, long order) { this.name=name; this.dimension=dimension; this.x=x; this.y=y; this.z=z; this.order=order; } JsonObject toJson() { JsonObject json = new JsonObject(); json.addProperty("dimension", dimension); json.addProperty("x", x); json.addProperty("y", y); json.addProperty("z", z); json.addProperty("order", order); return json; } static RespawnPoint fromJson(String name, JsonObject json) { return new RespawnPoint(name, json.get("dimension").getAsString(), json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt(), json.has("order") ? json.get("order").getAsLong() : -1L); } }
    public static final class RuleRecord { String id; String trigger; String target; String action; String value; int limit; int executions; boolean enabled = true; RuleRecord(String id, String trigger, String target, String action, String value, int limit) { this.id=id; this.trigger=trigger; this.target=target; this.action=action; this.value=value; this.limit=limit; } JsonObject toJson() { JsonObject json = new JsonObject(); json.addProperty("id", id); json.addProperty("trigger", trigger); json.addProperty("target", target); json.addProperty("action", action); json.addProperty("value", value); json.addProperty("limit", limit); json.addProperty("executions", executions); json.addProperty("enabled", enabled); return json; } static RuleRecord fromJson(JsonObject json) { RuleRecord rule = new RuleRecord(json.get("id").getAsString(), json.get("trigger").getAsString(), json.get("target").getAsString(), json.get("action").getAsString(), json.has("value") ? json.get("value").getAsString() : "", json.get("limit").getAsInt()); rule.executions = json.has("executions") ? json.get("executions").getAsInt() : 0; rule.enabled = !json.has("enabled") || json.get("enabled").getAsBoolean(); return rule; } }
    private static boolean getBoolean(JsonObject json, String key) { return json.has(key) && json.get(key).getAsBoolean(); }
}
