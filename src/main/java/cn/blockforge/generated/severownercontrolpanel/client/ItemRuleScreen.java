package cn.blockforge.generated.severownercontrolpanel.client;

import cn.blockforge.generated.severownercontrolpanel.data.ControlData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 单条物品规则的统一编辑界面：物品管理页的“设置该物品冷却时间”和禁用物品列表里点击某一行，
 * 打开的都是这一个界面，可设置可使用状态与冷却时间。
 *
 * <p>命令沿用服务端的 item disable / enable / cooldown / cooldown_reset：
 * 玩家自身的规则按玩家键下发，分组继承来的规则按分组名下发，避免误改/误建规则对象。
 * 操作完成后通过 {@code onChanged} 通知调用方刷新它背后的列表或状态按钮。
 */
public final class ItemRuleScreen extends Screen {
    private static final int PANEL_WIDTH = 440;
    private static final int PANEL_HEIGHT = 236;

    /** 父链由 PlayerBlockedListScreen.parentOf 与 ControlScreen.activeScreen 读取，保持包内可见。 */
    final Screen parent;
    /** 这条规则真正的对象：玩家自身的规则是 UUID，分组规则是分组名，命令按它下发。 */
    private final String ruleTarget;
    private final String targetDisplay;
    private final String itemId;
    private final String itemDisplay;
    private final String sourceDisplay;
    private final Runnable onChanged;
    private boolean disabled;
    private double cooldown;
    private int left;
    private int top;
    private Button toggleButton;
    private EditBox seconds;
    private Button applyButton;
    private boolean valid;

    public ItemRuleScreen(Screen parent, String ruleTarget, String targetDisplay, String itemId, String itemDisplay,
                          String sourceDisplay, boolean disabled, double cooldown, Runnable onChanged) {
        super(Component.translatable("screen.severownercontrolpanel.item_edit.title"));
        this.parent = parent;
        this.ruleTarget = ruleTarget == null ? "" : ruleTarget.trim();
        this.targetDisplay = targetDisplay == null || targetDisplay.isBlank() ? this.ruleTarget : targetDisplay;
        this.itemId = itemId == null ? "" : itemId.trim();
        this.itemDisplay = itemDisplay == null || itemDisplay.isBlank() ? this.itemId : itemDisplay;
        this.sourceDisplay = sourceDisplay == null ? "" : sourceDisplay;
        this.disabled = disabled;
        this.cooldown = cooldown;
        this.onChanged = onChanged;
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        toggleButton = Button.builder(toggleLabel(), button -> {
            runCommand("socp item " + (disabled ? "enable" : "disable") + " " + quote(ruleTarget) + " " + quote(itemId));
            disabled = !disabled;
            button.setMessage(toggleLabel());
            notifyChanged();
        }).bounds(left + 18, top + 146, 130, 20).build();
        addRenderableWidget(toggleButton);
        seconds = new EditBox(font, left + 18, top + 110, 200, 20, Component.translatable("screen.severownercontrolpanel.cooldown_input"));
        seconds.setHint(Component.translatable("screen.severownercontrolpanel.cooldown_input"));
        seconds.setMaxLength(20);
        // 支持小数：只允许数字与一个小数点，其余字符直接拒绝输入。
        seconds.setFilter(value -> value.isEmpty() || value.matches("\\d*\\.?\\d*"));
        seconds.setValue(cooldown > 0 ? ControlData.formatSeconds(cooldown) : "");
        seconds.setResponder(value -> validate(value.trim()));
        addRenderableWidget(seconds);
        validate(seconds.getValue().trim());
        applyButton = Button.builder(Component.translatable("screen.severownercontrolpanel.item_edit.cooldown_apply"), button -> {
            String value = seconds.getValue().trim();
            if (!isValidSeconds(value)) return;
            runCommand("socp item cooldown " + quote(ruleTarget) + " " + quote(itemId) + " " + value);
            cooldown = Double.parseDouble(value);
            seconds.setValue("");
            notifyChanged();
        }).bounds(left + 226, top + 110, 92, 20).build();
        applyButton.active = valid;
        addRenderableWidget(applyButton);
        addRenderableWidget(Button.builder(Component.translatable("screen.severownercontrolpanel.item_edit.cooldown_reset"), button -> {
            runCommand("socp item cooldown_reset " + quote(ruleTarget) + " " + quote(itemId));
            cooldown = 0;
            seconds.setValue("");
            notifyChanged();
        }).bounds(left + 326, top + 110, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.severownercontrolpanel.item_edit.back"), button -> onClose())
                .bounds(left + 326, top + 146, 96, 20).build());
        setInitialFocus(seconds);
    }

    private Component toggleLabel() {
        return Component.translatable(disabled
                ? "screen.severownercontrolpanel.item_edit.enable"
                : "screen.severownercontrolpanel.item_edit.disable");
    }

    /**
     * 冷却值支持小数（如 1.5）。校验规则：解析为数字后向零截断成 int，必须大于 0
     * 且不超过 16 位整型上限；真正执行冷却时使用用户输入的原始数值，不做整数化。
     */
    public static boolean isValidSeconds(String value) {
        if (value == null) return false;
        String text = value.trim();
        if (text.isEmpty()) return false;
        try {
            return ControlData.isValidCooldown(Double.parseDouble(text));
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private void validate(String value) {
        valid = isValidSeconds(value);
        if (applyButton != null) applyButton.active = valid;
    }

    private void notifyChanged() {
        if (onChanged != null) onChanged.run();
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
        graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.item_edit.player", targetDisplay), left + 14, top + 28, 0xFF8FE3C1, false);
        graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.item_edit.item", itemDisplay), left + 14, top + 44, 0xFFD7E4E1, false);
        Component state = Component.translatable("screen.severownercontrolpanel.item_info_state",
                Component.translatable(disabled
                        ? "screen.severownercontrolpanel.item_toggle_disabled"
                        : "screen.severownercontrolpanel.item_toggle_enabled"),
                cooldown > 0 ? Component.translatable("screen.severownercontrolpanel.cooldown.seconds", ControlData.formatSeconds(cooldown))
                        : Component.translatable("screen.severownercontrolpanel.cooldown.none"));
        graphics.drawString(font, state, left + 14, top + 60, disabled ? 0xFFFF6B6B : 0xFF8FE3C1, false);
        if (!sourceDisplay.isBlank()) {
            graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.item_edit.source", sourceDisplay), left + 14, top + 76, 0xFF9DB6B0, false);
        }
        graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.item_edit.cooldown_label"), left + 14, top + 94, 0xFFD7E4E1, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!valid && !seconds.getValue().trim().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.cooldown_invalid"), left + 18, top + 132, 0xFFFF5555, false);
        }
        graphics.drawWordWrap(font, Component.translatable("screen.severownercontrolpanel.item_edit.hint"), left + 18, top + 176, PANEL_WIDTH - 36, 0xFFD7E4E1);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
