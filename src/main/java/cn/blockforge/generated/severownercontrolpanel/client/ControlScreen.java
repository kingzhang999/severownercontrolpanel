package cn.blockforge.generated.severownercontrolpanel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ControlScreen extends Screen {
    private static final int PANEL_WIDTH = 560;
    private static final int PANEL_HEIGHT = 330;
    private static ControlScreen activeScreen;
    private int left;
    private int top;
    private int page;
    private final List<EditBox> fields = new ArrayList<>();
    private List<String> respawnOptions = new ArrayList<>();
    private String selectedRespawn = "";
    private String respawnPlayerInput = "";
    private String respawnNameInput = "主城";
    private String respawnPositionInput = "0 64 0";
    private String playerInput = "";
    private String gamemodePlayer = "";
    private String gamemodeMode = "unknown";
    private Button gamemodeButton;
    private String itemTargetInput = "";
    private String itemInput = "";
    private String itemQueryA = "";
    private String itemTargetDesc = "";
    private String itemIdResolved = "";
    private String itemResolvedInput = "";
    private String itemGroups = "";
    private String itemPlayers = "";
    private Boolean itemDisabled;
    private double itemCooldown = -1;
    private Button itemStatusButton;

    public ControlScreen() {
        super(Component.translatable("screen.severownercontrolpanel.title"));
        activeScreen = this;
    }

    public static void updateGameMode(String identifier, String name, String mode) {
        if (activeScreen == null) return;
        String input = activeScreen.playerInput.trim();
        if (!input.equalsIgnoreCase(identifier) && !input.equalsIgnoreCase(name)) return;
        activeScreen.gamemodePlayer = input;
        activeScreen.gamemodeMode = mode == null || mode.isBlank() ? "unknown" : mode;
        if (activeScreen.gamemodeButton != null) activeScreen.gamemodeButton.setMessage(activeScreen.gameModeLabel(activeScreen.gamemodeMode));
    }

    private Component gameModeLabel(String mode) {
        return Component.translatable("screen.severownercontrolpanel.gamemode_current").append(Component.literal(mode));
    }

    public static void updateRespawnOptions(String selected, String encodedOptions) {
        if (activeScreen == null) return;
        activeScreen.selectedRespawn = selected == null ? "" : selected;
        activeScreen.respawnOptions = encodedOptions == null || encodedOptions.isBlank()
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(encodedOptions.split("\\u001e", -1)));
        activeScreen.captureRespawnInputs();
        activeScreen.init(activeScreen.minecraft, activeScreen.width, activeScreen.height);
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        clearWidgets();
        fields.clear();
        String[] tabKeys = {
                "screen.severownercontrolpanel.players",
                "screen.severownercontrolpanel.groups",
                "screen.severownercontrolpanel.rules",
                "screen.severownercontrolpanel.respawns",
                "screen.severownercontrolpanel.teleport",
                "screen.severownercontrolpanel.items"
        };
        for (int index = 0; index < tabKeys.length; index++) {
            addRenderableWidget(tab(tabKeys[index], index, 12 + index * 88, 84));
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.severownercontrolpanel.save"), button -> sendCommand("socp save"))
                .bounds(left + 12, top + PANEL_HEIGHT - 30, 86, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.severownercontrolpanel.status"), button -> sendCommand("socp status"))
                .bounds(left + 104, top + PANEL_HEIGHT - 30, 86, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.severownercontrolpanel.close"), button -> onClose())
                .bounds(left + PANEL_WIDTH - 98, top + PANEL_HEIGHT - 30, 86, 20).build());
        buildPage();
    }

    private Button tab(String key, int selected, int x, int width) {
        return Button.builder(Component.translatable(key), button -> selectPage(selected))
                .bounds(left + x, top + 30, width, 20).build();
    }

    private void selectPage(int selected) {
        captureRespawnInputs();
        page = selected;
        init(minecraft, width, height);
    }

    private void buildPage() {
        switch (page) {
            case 1 -> buildGroupsPage();
            case 2 -> buildRulesPage();
            case 3 -> buildRespawnsPage();
            case 4 -> buildTeleportPage();
            case 5 -> buildItemsPage();
            default -> buildPlayersPage();
        }
    }

    private EditBox field(String label, String value, int x, int y, int width) {
        EditBox box = new EditBox(font, left + x, top + y, width, 20, Component.translatable(label));
        box.setValue(value);
        box.setHint(Component.translatable(label));
        box.setMaxLength(256);
        fields.add(box);
        addRenderableWidget(box);
        return box;
    }

    private Button action(String label, int x, int y, int width, Runnable action) {
        Button button = Button.builder(Component.translatable(label), ignored -> action.run())
                .bounds(left + x, top + y, width, 20).build();
        addRenderableWidget(button);
        return button;
    }

    private EditBox ruleField(String label, String parameter, int x, int y, int width) {
        EditBox box = new EditBox(font, left + x, top + y, width, 20, Component.translatable(label));
        box.setHint(Component.literal(parameter));
        box.setMaxLength(256);
        fields.add(box);
        addRenderableWidget(box);
        return box;
    }

    private void buildPlayersPage() {
        graphicsLabel("screen.severownercontrolpanel.players_heading", 18, 70);
        EditBox player = field("screen.severownercontrolpanel.player_name", playerInput, 18, 98, 170);
        EditBox group = field("screen.severownercontrolpanel.group_name", "", 198, 98, 170);
        action("screen.severownercontrolpanel.group_add", 378, 98, 78, () -> sendPlayerGroup(player, group, "add"));
        action("screen.severownercontrolpanel.group_remove", 462, 98, 78, () -> sendPlayerGroup(player, group, "remove"));
        action("screen.severownercontrolpanel.panel_enable", 18, 132, 118, () -> sendPlayerPanel(player, true));
        action("screen.severownercontrolpanel.panel_disable", 142, 132, 118, () -> sendPlayerPanel(player, false));
        action("screen.severownercontrolpanel.player_kick", 266, 132, 86, () -> sendPlayerAction(player, "kick"));
        playerInput = player.getValue();
        gamemodeButton = Button.builder(gameModeLabel(gamemodePlayer.equalsIgnoreCase(playerInput.trim()) ? gamemodeMode : "unknown"), button -> {
                    playerInput = player.getValue();
                    gamemodePlayer = playerInput.trim();
                    sendPlayerAction(player, "gamemode cycle");
                }).bounds(left + 358, top + 132, 98, 20).build();
        addRenderableWidget(gamemodeButton);
        action("screen.severownercontrolpanel.list_players", 462, 132, 78, () -> sendCommand("socp players"));
        drawHint("screen.severownercontrolpanel.players_hint", 18, 178);
    }

    private void buildGroupsPage() {
        graphicsLabel("screen.severownercontrolpanel.groups_heading", 18, 70);
        EditBox name = field("screen.severownercontrolpanel.group_name", "", 18, 98, 170);
        EditBox color = field("screen.severownercontrolpanel.group_color", "red", 198, 98, 150);
        action("screen.severownercontrolpanel.group_save", 356, 98, 76, () -> sendCommand("socp groups set " + word(name) + " " + word(color)));
        action("screen.severownercontrolpanel.group_delete", 440, 98, 100, () -> sendCommand("socp groups delete " + word(name)));
        action("screen.severownercontrolpanel.list_groups", 18, 132, 118, () -> sendCommand("socp groups list"));
        action("screen.severownercontrolpanel.group_kick", 142, 132, 136, () -> sendCommand("socp groups kick " + word(name)));
        drawHint("screen.severownercontrolpanel.groups_hint", 18, 178);
        drawHint("screen.severownercontrolpanel.group_colors_hint", 18, 222);
    }

    private void buildRulesPage() {
        graphicsLabel("screen.severownercontrolpanel.rules_heading", 18, 70);
        EditBox id = ruleField("screen.severownercontrolpanel.rule_id", "id", 18, 98, 118);
        EditBox trigger = ruleField("screen.severownercontrolpanel.rule_trigger", "trigger", 144, 98, 118);
        EditBox target = ruleField("screen.severownercontrolpanel.rule_target", "target", 270, 98, 118);
        EditBox action = ruleField("screen.severownercontrolpanel.rule_action", "action", 396, 98, 118);
        EditBox value = ruleField("screen.severownercontrolpanel.rule_value", "value", 18, 132, 366);
        EditBox template = ruleField("screen.severownercontrolpanel.rule_template", "template", 396, 132, 118);
        action("screen.severownercontrolpanel.rule_add", 18, 166, 92, () -> sendRule(id, trigger, target, action, value));
        action("screen.severownercontrolpanel.rule_remove", 118, 166, 92, () -> sendCommand("socp rule remove " + word(id)));
        action("screen.severownercontrolpanel.rule_template_create", 218, 166, 146, () -> sendCommand("socp rule template " + word(id) + " " + word(template) + " " + word(target)));
        action("screen.severownercontrolpanel.list_rules", 372, 166, 104, () -> sendCommand("socp rule list"));
        drawHint("screen.severownercontrolpanel.rules_hint", 18, 210);
    }

    private void buildRespawnsPage() {
        graphicsLabel("screen.severownercontrolpanel.respawns_heading", 18, 70);
        EditBox player = field("screen.severownercontrolpanel.player_name", respawnPlayerInput, 18, 98, 150);
        EditBox name = field("screen.severownercontrolpanel.respawn_name", respawnNameInput, 178, 98, 150);
        action("screen.severownercontrolpanel.respawn_here", 338, 98, 100, () -> {
            captureRespawnInputs(player, name, null);
            sendCommand("socp player " + word(player) + " respawn here " + name.getValue().trim());
        });
        action("screen.severownercontrolpanel.respawn_list", 446, 98, 94, () -> sendCommand("socp respawns"));
        EditBox pos = field("screen.severownercontrolpanel.respawn_position", respawnPositionInput, 18, 132, 190);
        action("screen.severownercontrolpanel.respawn_at", 218, 132, 106, () -> {
            captureRespawnInputs(player, name, pos);
            sendCommand("socp player " + word(player) + " respawn at " + pos.getValue().trim() + " " + name.getValue().trim());
        });
        action("screen.severownercontrolpanel.respawn_refresh", 334, 132, 100, () -> {
            captureRespawnInputs(player, name, pos);
            if (!word(player).isBlank()) sendCommand("socp player " + word(player) + " respawn options");
        });
        CycleButton<String> selector = buildRespawnSelector(player);
        addRenderableWidget(selector);
        action("screen.severownercontrolpanel.respawn_delete", 334, 166, 98, () -> {
            captureRespawnInputs(player, name, pos);
            if (!selectedRespawn.isBlank() && !selectedRespawn.equals("未设置") && !word(player).isBlank()) {
                sendCommand("socp player " + word(player) + " respawn delete " + selectedRespawn);
            }
        });
        action("screen.severownercontrolpanel.clear_all", 438, 166, 102, () -> sendCommand("socp clear"));
        drawHint("screen.severownercontrolpanel.respawns_hint", 18, 210);
    }

    private void buildTeleportPage() {
        graphicsLabel("screen.severownercontrolpanel.teleport_heading", 18, 70);
        EditBox target = field("screen.severownercontrolpanel.teleport_target", "", 18, 98, 210);
        EditBox position = field("screen.severownercontrolpanel.teleport_position", "", 240, 98, 190);
        action("screen.severownercontrolpanel.teleport_to", 18, 132, 142, () -> sendTeleport(target, position));
        action("screen.severownercontrolpanel.teleport_owner", 166, 132, 182, () -> sendTeleportToOwner(target));
        action("screen.severownercontrolpanel.teleport_all_owner", 354, 132, 186, () -> sendCommand("socp teleport all to_owner"));
        drawHint("screen.severownercontrolpanel.teleport_hint", 18, 178);
    }

    // ==== 物品管理页 ====

    private void buildItemsPage() {
        graphicsLabel("screen.severownercontrolpanel.items_heading", 18, 70);
        EditBox target = field("screen.severownercontrolpanel.item_target", itemTargetInput, 18, 98, 170);
        EditBox item = field("screen.severownercontrolpanel.item_id", itemInput, 198, 98, 150);
        itemStatusButton = Button.builder(itemToggleLabel(), button -> toggleItemStatus(target, item))
                .bounds(left + 358, top + 98, 74, 20).build();
        addRenderableWidget(itemStatusButton);
        action("screen.severownercontrolpanel.item_query", 440, 98, 100, () -> {
            itemTargetInput = target.getValue();
            itemInput = item.getValue();
            queryItemStatus();
        });
        action("screen.severownercontrolpanel.item_cooldown_button", 18, 132, 158, () -> openCooldownScreen(target, item));
        action("screen.severownercontrolpanel.item_list", 184, 132, 100, () -> sendCommand("socp item list"));
        action("screen.severownercontrolpanel.item_clear_all", 290, 132, 130, this::clearAllItemRules);
        action("screen.severownercontrolpanel.item_blocked_list", 426, 132, 114, () -> openBlockedList(target));
        drawHint("screen.severownercontrolpanel.items_hint", 18, 244);
    }

    /**
     * “禁用物品列表”按钮：把输入框 A 交给服务端解析。玩家目标回物品清单直接打开玩家列表界面，
     * 分组目标回组内玩家列表；界面开哪一个完全由服务端返回的载荷类型决定，客户端不做二次猜测。
     */
    private void openBlockedList(EditBox target) {
        String value = target.getValue().trim();
        if (value.isEmpty()) return;
        sendCommand("socp item list_view " + quote(value));
    }

    /** 一键清除全部物品规则：命令成功后把界面显示同步回“启用 / 无冷却”。 */
    private void clearAllItemRules() {
        sendCommand("socp item clear");
        itemDisabled = Boolean.FALSE;
        itemCooldown = 0;
        if (itemStatusButton != null) itemStatusButton.setMessage(itemToggleLabel());
    }

    private Component itemToggleLabel() {
        return Component.translatable(Boolean.TRUE.equals(itemDisabled)
                ? "screen.severownercontrolpanel.item_toggle_disabled"
                : "screen.severownercontrolpanel.item_toggle_enabled");
    }

    private void toggleItemStatus(EditBox target, EditBox item) {
        itemTargetInput = target.getValue();
        itemInput = item.getValue();
        if (itemTargetInput.trim().isEmpty() || itemInput.trim().isEmpty()) return;
        String operation = Boolean.TRUE.equals(itemDisabled) ? "enable" : "disable";
        sendItemCommand(operation);
        itemDisabled = operation.equals("disable") ? Boolean.TRUE : Boolean.FALSE;
        itemStatusButton.setMessage(itemToggleLabel());
        queryItemStatus();
    }

    private void openCooldownScreen(EditBox target, EditBox item) {
        itemTargetInput = target.getValue();
        itemInput = item.getValue();
        if (itemTargetInput.trim().isEmpty() || itemInput.trim().isEmpty()) return;
        if (minecraft != null) minecraft.setScreen(new ItemCooldownScreen(this, itemTargetInput.trim(), itemInput.trim(), itemCooldown));
    }

    void queryItemStatus() {
        itemQueryA = itemTargetInput.trim();
        sendItemCommand("status");
    }

    /** 向服务端发送物品管理命令；中文或含空格的参数自动加引号。 */
    void sendItemCommand(String operation) {
        String target = itemTargetInput.trim();
        String item = itemInput.trim();
        if (target.isEmpty() || item.isEmpty()) return;
        String resolved = ItemResolver.resolveToId(item);
        // 服务端回包里的物品字段就是这里发出的规范 ID，记录后用于回包比对。
        itemResolvedInput = resolved;
        sendCommand("socp item " + operation + " " + quote(target) + " " + quote(resolved));
    }

    /**
     * 冷却秒数需大于 0 且不超过 16 位整型上限，支持 1.5 这类小数。
     * 命令语法是 /socp item cooldown &lt;对象&gt; &lt;物品&gt; &lt;秒数&gt;，秒数必须放在最后，
     * 不能复用 sendItemCommand（那会把 operation 拼在对象/物品之前，导致服务端把物品当成秒数解析）。
     */
    void sendItemCooldown(String seconds) {
        String value = seconds == null ? "" : seconds.trim();
        if (!ItemCooldownScreen.isValidSeconds(value)) return;
        String target = itemTargetInput.trim();
        String item = itemInput.trim();
        if (target.isEmpty() || item.isEmpty()) return;
        String resolved = ItemResolver.resolveToId(item);
        itemResolvedInput = resolved;
        sendCommand("socp item cooldown " + quote(target) + " " + quote(resolved) + " " + value);
    }

    private String quote(String value) {
        return value.matches("\\S+") ? value : "\"" + value + "\"";
    }

    /** 服务端回包：仅在输入未变化时套用，避免过期结果覆盖界面。 */
    public static void applyItemStatus(String[] parts) {
        if (activeScreen == null || parts.length < 8) return;
        if (!parts[0].equals(activeScreen.itemQueryA) || !parts[2].equals(activeScreen.itemResolvedInput)) return;
        activeScreen.itemTargetDesc = parts[1];
        activeScreen.itemIdResolved = parts[3];
        int state;
        double cooldown;
        try { state = Integer.parseInt(parts[4]); } catch (NumberFormatException ignored) { state = 0; }
        try { cooldown = Double.parseDouble(parts[5]); } catch (NumberFormatException ignored) { cooldown = 0; }
        activeScreen.itemDisabled = state == 1 ? Boolean.TRUE : Boolean.FALSE;
        activeScreen.itemCooldown = cooldown;
        activeScreen.itemGroups = parts[6].replace('\u001e', '、');
        activeScreen.itemPlayers = parts[7].replace('\u001e', '、');
        if (activeScreen.itemStatusButton != null) activeScreen.itemStatusButton.setMessage(activeScreen.itemToggleLabel());
    }

    /** 冷却秒数展示文本：整数不带小数点，小数去掉末尾多余的 0。 */
    public static String formatSeconds(double seconds) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds)) return Double.toString(seconds);
        if (seconds == Math.rint(seconds) && Math.abs(seconds) < 1.0e15) return Long.toString((long) seconds);
        return new java.math.BigDecimal(Double.toString(seconds)).stripTrailingZeros().toPlainString();
    }

    private void drawItemInfo(GuiGraphics graphics) {
        int y = top + 162;
        graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.item_info_target",
                Component.literal(itemTargetDesc.isEmpty() ? "—" : itemTargetDesc)), left + 18, y, 0xFFD7E4E1, false);
        y += 13;
        graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.item_info_item",
                Component.literal(itemIdResolved.isEmpty() ? "—" : itemIdResolved)), left + 18, y, 0xFFD7E4E1, false);
        y += 13;
        Component cooldownText = itemCooldown < 0 ? Component.translatable("screen.severownercontrolpanel.cooldown.unknown")
                : itemCooldown == 0 ? Component.translatable("screen.severownercontrolpanel.cooldown.none")
                : Component.translatable("screen.severownercontrolpanel.cooldown.seconds", formatSeconds(itemCooldown));
        graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.item_info_state", itemToggleLabel(), cooldownText), left + 18, y, 0xFFD7E4E1, false);
        y += 13;
        graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.item_info_groups", truncateList(itemGroups)), left + 18, y, 0xFF9DB6B0, false);
        y += 13;
        graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.item_info_players", truncateList(itemPlayers)), left + 18, y, 0xFF9DB6B0, false);
    }

    private String truncateList(String value) {
        String clean = value.trim();
        return clean.length() <= 80 ? clean : clean.substring(0, 80) + "…";
    }

    private void sendTeleport(EditBox target, EditBox position) {
        String targetName = word(target);
        String coordinates = position.getValue().trim();
        if (!targetName.isBlank() && !coordinates.isBlank()) sendCommand("socp teleport " + targetName + " to " + coordinates);
    }

    private void sendTeleportToOwner(EditBox target) {
        String targetName = word(target);
        if (!targetName.isBlank()) sendCommand("socp teleport " + targetName + " to_owner");
    }

    private CycleButton<String> buildRespawnSelector(EditBox player) {
        List<String> values = respawnOptions.isEmpty() ? List.of("未设置") : new ArrayList<>(respawnOptions);
        String initial = values.contains(selectedRespawn) ? selectedRespawn : values.get(0);
        return CycleButton.<String>builder(Component::literal)
                .withValues(values)
                .withInitialValue(initial)
                .create(left + 18, top + 166, 306, 20, Component.translatable("screen.severownercontrolpanel.respawn_current"), (button, value) -> {
                    captureRespawnInputs(player, null, null);
                    if (!word(player).isBlank()) {
                        sendCommand("socp player " + word(player) + " respawn cycle");
                    }
                });
    }

    private void captureRespawnInputs() {
        if (page == 5 && fields.size() >= 2) {
            itemTargetInput = fields.get(0).getValue();
            itemInput = fields.get(1).getValue();
            return;
        }
        if (page != 3 || fields.size() < 3) return;
        captureRespawnInputs(fields.get(0), fields.get(1), fields.get(2));
    }

    private void captureRespawnInputs(EditBox player, EditBox name, EditBox pos) {
        if (player != null) respawnPlayerInput = player.getValue();
        if (name != null) respawnNameInput = name.getValue();
        if (pos != null) respawnPositionInput = pos.getValue();
    }

    private void sendPlayerGroup(EditBox player, EditBox group, String operation) {
        playerInput = player.getValue();
        sendCommand("socp player " + word(player) + " group " + operation + " " + word(group));
    }

    private void sendPlayerPanel(EditBox player, boolean enabled) {
        sendCommand("socp player " + word(player) + " panel " + enabled);
    }

    private void sendPlayerAction(EditBox player, String action) {
        String identifier = word(player);
        if (!identifier.isBlank()) sendCommand("socp player " + identifier + " " + action);
    }

    private void sendRule(EditBox id, EditBox trigger, EditBox target, EditBox action, EditBox value) {
        String ruleAction = word(action);
        String command = "socp rule add " + word(id) + " " + word(trigger) + " " + word(target) + " " + ruleAction;
        if (!ruleAction.equals("clear_effects")) command += " " + value.getValue().trim();
        sendCommand(command);
    }

    private String word(EditBox box) {
        return box.getValue().trim().replaceAll("\\s+", "_");
    }

    private void sendCommand(String command) {
        if (minecraft != null && minecraft.player != null && minecraft.player.connection != null && !command.contains("  ")) {
            minecraft.player.connection.sendCommand(command);
        }
    }

    private void graphicsLabel(String key, int x, int y) {
        addRenderableOnly((graphics, mouseX, mouseY, partialTick) -> graphics.drawString(font, Component.translatable(key), left + x, top + y, 0xFF8FE3C1, false));
    }

    private void drawHint(String key, int x, int y) {
        addRenderableOnly((graphics, mouseX, mouseY, partialTick) -> graphics.drawWordWrap(font, Component.translatable(key), left + x, top + y, PANEL_WIDTH - 36, 0xFFD7E4E1));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE8172029);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 3, 0xFF41C7A3);
        graphics.drawString(font, title, left + 14, top + 10, 0xFFE9F4F1, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (page == 5) drawItemInfo(graphics);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (activeScreen == this) activeScreen = null;
        Minecraft.getInstance().setScreen(null);
    }
}
