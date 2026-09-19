package cn.blockforge.generated.severownercontrolpanel.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 玩家禁用物品列表的编辑菜单：对单条物品规则设置可使用状态或冷却时间。
 * 命令沿用服务端的 item disable / enable / cooldown / cooldown_reset：
 * 玩家自身的规则按玩家键下发，分组继承来的规则按分组名下发，避免误改/误建规则对象。
 * 每次操作后立刻请求 list_view，让身后的列表与服务端保持一致。
 */
public final class ItemRuleEditScreen extends Screen {
    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 226;

    /** 父链由 PlayerBlockedListScreen.parentOf 读取，保持包内可见。 */
    final PlayerBlockedListScreen parent;
    private final String playerDisplay;
    /** 这条规则真正的对象：玩家自身的规则是 UUID，分组规则是分组名，命令按它下发。 */
    private final String ruleTarget;
    private final String ruleSource;
    private final String itemId;
    private boolean disabled;
    private double cooldown;
    private int left;
    private int top;
    private Button toggleButton;
    private EditBox seconds;
    private Button applyButton;

    public ItemRuleEditScreen(PlayerBlockedListScreen parent, String playerKey, String playerDisplay,
                              String ruleTarget, String ruleSource, String itemId, boolean disabled, double cooldown) {
        super(Component.translatable("screen.severownercontrolpanel.item_edit.title"));
        this.parent = parent;
        this.playerDisplay = playerDisplay;
        this.ruleTarget = ruleTarget == null || ruleTarget.isBlank() ? playerKey : ruleTarget;
        this.ruleSource = ruleSource == null || ruleSource.isBlank() ? playerDisplay : ruleSource;
        this.itemId = itemId;
        this.disabled = disabled;
        this.cooldown = cooldown;
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        toggleButton = Button.builder(toggleLabel(), button -> {
            runCommand("socp item " + (disabled ? "enable" : "disable") + " " + quote(ruleTarget) + " " + quote(itemId));
            disabled = !disabled;
            button.setMessage(toggleLabel());
            parent.refreshFromServer();
        }).bounds(left + 18, top + 120, 112, 20).build();
        addRenderableWidget(toggleButton);
        seconds = new EditBox(font, left + 140, top + 86, 140, 20, Component.translatable("screen.severownercontrolpanel.cooldown_input"));
        seconds.setHint(Component.translatable("screen.severownercontrolpanel.cooldown_input"));
        seconds.setMaxLength(20);
        seconds.setFilter(value -> value.isEmpty() || value.matches("\\d*\\.?\\d*"));
        seconds.setValue(cooldown > 0 ? ControlScreen.formatSeconds(cooldown) : "");
        seconds.setResponder(value -> {
            if (applyButton != null) applyButton.active = ItemCooldownScreen.isValidSeconds(value.trim());
        });
        addRenderableWidget(seconds);
        applyButton = Button.builder(Component.translatable("screen.severownercontrolpanel.item_edit.cooldown_apply"), button -> {
            String value = seconds.getValue().trim();
            if (!ItemCooldownScreen.isValidSeconds(value)) return;
            runCommand("socp item cooldown " + quote(ruleTarget) + " " + quote(itemId) + " " + value);
            cooldown = Double.parseDouble(value);
            seconds.setValue("");
            parent.refreshFromServer();
        }).bounds(left + 288, top + 86, 92, 20).build();
        applyButton.active = false;
        addRenderableWidget(applyButton);
        addRenderableWidget(Button.builder(Component.translatable("screen.severownercontrolpanel.item_edit.cooldown_reset"), button -> {
            runCommand("socp item cooldown_reset " + quote(ruleTarget) + " " + quote(itemId));
            cooldown = 0;
            seconds.setValue("");
            parent.refreshFromServer();
        }).bounds(left + 140, top + 120, 112, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.severownercontrolpanel.item_edit.back"), button -> onClose())
                .bounds(left + 262, top + 120, 118, 20).build());
    }

    private Component toggleLabel() {
        return Component.translatable(disabled
                ? "screen.severownercontrolpanel.item_edit.enable"
                : "screen.severownercontrolpanel.item_edit.disable");
    }

    private void runCommand(String command) {
        if (minecraft != null && minecraft.player != null && minecraft.player.connection != null) {
            minecraft.player.connection.sendCommand(command);
        }
    }

    /** 含空格的对象名加引号，避免命令参数被截断。 */
    private static String quote(String value) {
        String text = value == null ? "" : value.trim().replace("\"", "");
        return text.matches("\\S+") ? text : "\"" + text + "\"";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE8172029);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 3, 0xFF41C7A3);
        graphics.drawString(font, title, left + 14, top + 10, 0xFFE9F4F1, false);
        graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.item_edit.player", playerDisplay), left + 14, top + 26, 0xFF8FE3C1, false);
        graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.item_edit.item", itemId), left + 14, top + 42, 0xFFD7E4E1, false);
        graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.item_edit.source", ruleSource), left + 14, top + 74, 0xFF9DB6B0, false);
        Component state = Component.translatable("screen.severownercontrolpanel.item_info_state",
                Component.translatable(disabled
                        ? "screen.severownercontrolpanel.item_toggle_disabled"
                        : "screen.severownercontrolpanel.item_toggle_enabled"),
                cooldown > 0 ? Component.translatable("screen.severownercontrolpanel.cooldown.seconds", ControlScreen.formatSeconds(cooldown))
                        : Component.translatable("screen.severownercontrolpanel.cooldown.none"));
        graphics.drawString(font, state, left + 14, top + 58, disabled ? 0xFFFF6B6B : 0xFF8FE3C1, false);
        graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.item_edit.cooldown_label"), left + 14, top + 90, 0xFFD7E4E1, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawWordWrap(font, Component.translatable("screen.severownercontrolpanel.item_edit.hint"), left + 18, top + 152, PANEL_WIDTH - 36, 0xFFD7E4E1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
