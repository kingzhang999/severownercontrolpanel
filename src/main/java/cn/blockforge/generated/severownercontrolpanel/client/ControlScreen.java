package cn.blockforge.generated.severownercontrolpanel.client;

import cn.blockforge.generated.severownercontrolpanel.data.ControlData;
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
    private boolean gamemodeLocked;
    private Button gamemodeButton;
    private Button gamemodeLockButton;
    private String groupsListText = "";
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
    }

    /**
     * 当前屏幕栈上最靠近根的管理面板实例。
     *
     * <p>以前这里缓存一个静态引用，只在 {@link #onClose()} 里清理；跳转子界面走的是
     * {@code Minecraft.setScreen()}，只会触发 {@code removed()} 而不会触发 {@code onClose()}，
     * 于是面板被自己的子界面遮住时仍会保留引用，被死亡界面等顶掉后引用还会残留，属于隐性状态耦合。
     * 改为沿着“子界面 -> 父界面”的链条从当前屏幕向上找：面板被自己的子界面遮挡时依然能找到
     * （回包仍能更新到背后的面板），真正离开屏幕栈后引用自然失效，不需要再有额外清理。
     */
    private static ControlScreen activeScreen() {
        Screen current = Minecraft.getInstance().screen;
        while (current != null) {
            if (current instanceof ControlScreen panel) return panel;
            current = PlayerBlockedListScreen.parentOf(current);
        }
        return null;
    }

    public static void updateGameMode(String identifier, String name, String mode, boolean locked) {
        ControlScreen panel = activeScreen();
        if (panel == null) return;
        String input = panel.playerInput.trim();
        if (!input.equalsIgnoreCase(identifier) && !input.equalsIgnoreCase(name)) return;
        panel.gamemodePlayer = input;
        panel.gamemodeMode = mode == null || mode.isBlank() ? "unknown" : mode;
        panel.gamemodeLocked = locked;
        panel.refreshGameModeWidgets();
    }

    /**
     * 把已知的游戏模式与锁定状态同步到模式切换按钮和锁定按钮上。
     *
     * <p>只有在“最近一次回包的对象与输入框当前内容一致”时才算已知状态：输入框换成别的玩家后，
     * 两个按钮立刻回到未查询的显示，避免把上一个玩家的模式或锁定状态当成当前输入对象的。
     * 模式切换按钮在已知且被锁定时禁用（参照原版难度锁定，只能先解锁）；未知状态仍可点击，
     * 点击即发 cycle 命令，服务端的回包会把状态补上。
     */
    private void refreshGameModeWidgets() {
        boolean known = !gamemodePlayer.isBlank() && gamemodePlayer.equalsIgnoreCase(playerInput.trim());
        if (gamemodeButton != null) {
            gamemodeButton.setMessage(gameModeLabel(known ? gamemodeMode : "unknown"));
            gamemodeButton.active = !(known && gamemodeLocked);
        }
        if (gamemodeLockButton != null) {
            gamemodeLockButton.setMessage(gameModeLockLabel(known && gamemodeLocked));
        }
    }

    private Component gameModeLabel(String mode) {
        return Component.translatable("screen.severownercontrolpanel.gamemode_current").append(Component.literal(mode));
    }

    /** 锁定按钮文字：与模式切换按钮一致，直接显示它当前所处的状态。 */
    private Component gameModeLockLabel(boolean locked) {
        return Component.translatable("screen.severownercontrolpanel.gamemode_lock",
                Component.translatable(locked
                        ? "screen.severownercontrolpanel.gamemode_locked"
                        : "screen.severownercontrolpanel.gamemode_unlocked"));
    }

    /** 分组列表回包：在分组页以文字动态展示服务端返回的结果。 */
    public static void updateGroupsList(String text) {
        ControlScreen panel = activeScreen();
        if (panel == null) return;
        panel.groupsListText = text == null ? "" : text;
    }

    public static void updateRespawnOptions(String selected, String encodedOptions) {
        ControlScreen panel = activeScreen();
        if (panel == null) return;
        panel.selectedRespawn = selected == null ? "" : selected;
        panel.respawnOptions = encodedOptions == null || encodedOptions.isBlank()
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(encodedOptions.split("\\u001e", -1)));
        panel.captureRespawnInputs();
        panel.init(panel.minecraft, panel.width, panel.height);
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
        action("screen.severownercontrolpanel.panel_enable", 18, 132, 110, () -> sendPlayerPanel(player, true));
        action("screen.severownercontrolpanel.panel_disable", 134, 132, 110, () -> sendPlayerPanel(player, false));
        action("screen.severownercontrolpanel.player_kick", 250, 132, 86, () -> sendPlayerAction(player, "kick"));
        playerInput = player.getValue();
        // 输入框内容一变就按新对象刷新模式/锁定按钮，避免按钮继续显示上一个玩家的状态。
        player.setResponder(value -> {
            playerInput = value;
            refreshGameModeWidgets();
        });
        // 参照原版难度设置：状态切换按钮旁边紧跟锁定按钮，锁定后切换按钮被禁用。
        gamemodeButton = Button.builder(gameModeLabel("unknown"), button -> {
                    playerInput = player.getValue();
                    sendPlayerAction(player, "gamemode cycle");
                }).bounds(left + 342, top + 132, 100, 20).build();
        addRenderableWidget(gamemodeButton);
        gamemodeLockButton = Button.builder(gameModeLockLabel(false), button -> {
                    playerInput = player.getValue();
                    sendPlayerAction(player, "gamemode lock");
                }).bounds(left + 448, top + 132, 100, 20).build();
        addRenderableWidget(gamemodeLockButton);
        action("screen.severownercontrolpanel.list_players", 18, 166, 118, () -> sendCommand("socp players"));
        drawHint("screen.severownercontrolpanel.players_hint", 18, 202);
        refreshGameModeWidgets();
    }

    private void buildGroupsPage() {
        graphicsLabel("screen.severownercontrolpanel.groups_heading", 18, 70);
        EditBox name = field("screen.severownercontrolpanel.group_name", "", 18, 98, 170);
        EditBox color = field("screen.severownercontrolpanel.group_color", "red", 198, 98, 150);
        action("screen.severownercontrolpanel.group_save", 356, 98, 76, () -> sendCommand("socp groups set " + word(name) + " " + word(color)));
        action("screen.severownercontrolpanel.group_delete", 440, 98, 100, () -> sendCommand("socp groups delete " + word(name)));
        action("screen.severownercontrolpanel.list_groups", 18, 132, 118, () -> sendCommand("socp groups list"));
        action("screen.severownercontrolpanel.group_kick", 142, 132, 136, () -> sendCommand("socp groups kick " + word(name)));
        // 分组列表回包（groups_list）只更新字段，这里每帧读取最新内容，并裁剪在固定区域内换行展示。
        addRenderableOnly((graphics, mouseX, mouseY, partialTick) -> {
            graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.groups_list_heading"),
                    left + 18, top + 158, 0xFF8FE3C1, false);
            graphics.enableScissor(left + 14, top + 172, left + PANEL_WIDTH - 14, top + 238);
            String body = groupsListText.isBlank()
                    ? Component.translatable("screen.severownercontrolpanel.groups_list_empty").getString()
                    : groupsListText;
            int lineY = top + 174;
            for (String line : body.split("\\R", -1)) {
                if (lineY > top + 230) break;
                graphics.drawString(font, font.plainSubstrByWidth(line, PANEL_WIDTH - 40),
                        left + 18, lineY, 0xFFD7E4E1, false);
                lineY += 11;
            }
            graphics.disableScissor();
        });
        drawHint("screen.severownercontrolpanel.groups_hint", 18, 244);
        drawHint("screen.severownercontrolpanel.group_colors_hint", 18, 272);
    }

    private void buildRulesPage() {
        graphicsLabel("screen.severownercontrolpanel.rules_heading", 18, 70);
        EditBox id = ruleField("screen.severownercontrolpanel.rule_id", "id", 18, 98, 118);
        EditBox trigger = ruleField("screen.severownercontrolpanel.rule_trigger", "trigger", 144, 98, 118);
        EditBox target = ruleField("screen.severownercontrolpanel.rule_target", "target", 270, 98, 118);
        EditBox action = ruleField("screen.severownercontrolpanel.rule_action", "action", 396, 98, 118);
        EditBox value = ruleField("screen.severownercontrolpanel.rule_value", "value", 18, 132, 496);
        action("screen.severownercontrolpanel.rule_add", 18, 166, 118, () -> sendRule(id, trigger, target, action, value));
        action("screen.severownercontrolpanel.rule_remove", 142, 166, 118, () -> sendCommand("socp rule remove " + word(id)));
        action("screen.severownercontrolpanel.list_rules", 266, 166, 118, () -> sendCommand("socp rule list"));
        drawHint("screen.severownercontrolpanel.rules_hint", 18, 202);
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
        action("screen.severownercontrolpanel.item_edit_button", 18, 132, 158, () -> openItemEditor(target, item));
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

    /**
     * 打开统一的物品规则编辑界面：物品管理页的“编辑该物品规则”和禁用物品列表里点击某一行
     * 走的是同一个 {@link ItemRuleScreen}，可设置可使用状态与冷却时间，不再各开一套界面。
     * 界面的初始状态只在与最近一次查询结果一致时才套用，避免把上一个物品的状态显示成当前物品的。
     */
    private void openItemEditor(EditBox target, EditBox item) {
        itemTargetInput = target.getValue();
        itemInput = item.getValue();
        String rawTarget = itemTargetInput.trim();
        String rawItem = itemInput.trim();
        if (rawTarget.isEmpty() || rawItem.isEmpty() || minecraft == null) return;
        String resolved = ItemResolver.resolveToId(rawItem);
        itemResolvedInput = resolved;
        boolean sameQuery = itemQueryA.equals(rawTarget) && itemResolvedInput.equalsIgnoreCase(resolved);
        String targetDisplay = sameQuery && !itemTargetDesc.isBlank() ? itemTargetDesc : rawTarget;
        minecraft.setScreen(new ItemRuleScreen(this, rawTarget, targetDisplay, resolved, ItemResolver.displayOfId(resolved),
                "", sameQuery && Boolean.TRUE.equals(itemDisabled), sameQuery && itemCooldown > 0 ? itemCooldown : 0,
                this::queryItemStatus));
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

    private String quote(String value) {
        return value.matches("\\S+") ? value : "\"" + value + "\"";
    }

    /** 服务端回包：仅在输入未变化时套用，避免过期结果覆盖界面。 */
    public static void applyItemStatus(String[] parts) {
        ControlScreen panel = activeScreen();
        if (panel == null || parts.length < 8) return;
        if (!parts[0].equals(panel.itemQueryA) || !parts[2].equals(panel.itemResolvedInput)) return;
        panel.itemTargetDesc = parts[1];
        panel.itemIdResolved = parts[3];
        int state;
        double cooldown;
        try { state = Integer.parseInt(parts[4]); } catch (NumberFormatException ignored) { state = 0; }
        try { cooldown = Double.parseDouble(parts[5]); } catch (NumberFormatException ignored) { cooldown = 0; }
        panel.itemDisabled = state == 1 ? Boolean.TRUE : Boolean.FALSE;
        panel.itemCooldown = cooldown;
        panel.itemGroups = parts[6].replace('\u001e', '、');
        panel.itemPlayers = parts[7].replace('\u001e', '、');
        if (panel.itemStatusButton != null) panel.itemStatusButton.setMessage(panel.itemToggleLabel());
    }

    /** 冷却秒数展示文本：与 {@link ControlData#formatSeconds(double)} 共用同一实现，避免两处格式化结果不一致。 */
    public static String formatSeconds(double seconds) {
        return ControlData.formatSeconds(seconds);
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
        Minecraft.getInstance().setScreen(null);
    }
}
