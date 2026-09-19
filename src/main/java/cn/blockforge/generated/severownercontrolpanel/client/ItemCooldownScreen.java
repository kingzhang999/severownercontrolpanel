package cn.blockforge.generated.severownercontrolpanel.client;

import cn.blockforge.generated.severownercontrolpanel.data.ControlData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 物品冷却设置子界面：校验并下发 /socp item cooldown 命令。 */
public final class ItemCooldownScreen extends Screen {
    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 210;
    private final ControlScreen parent;
    private final String target;
    private final String item;
    private final double currentCooldown;
    private int left;
    private int top;
    private EditBox seconds;
    private Button applyButton;
    private boolean valid;

    public ItemCooldownScreen(ControlScreen parent, String target, String item, double currentCooldown) {
        super(Component.translatable("screen.severownercontrolpanel.cooldown.title"));
        this.parent = parent;
        this.target = target;
        this.item = item;
        this.currentCooldown = currentCooldown;
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        seconds = new EditBox(font, left + 18, top + 82, 200, 20, Component.translatable("screen.severownercontrolpanel.cooldown_input"));
        seconds.setHint(Component.translatable("screen.severownercontrolpanel.cooldown_input"));
        seconds.setMaxLength(20);
        // 支持小数：只允许数字与一个小数点，其余字符直接拒绝输入。
        seconds.setFilter(value -> value.isEmpty() || value.matches("\\d*\\.?\\d*"));
        seconds.setValue(currentCooldown > 0 ? ControlScreen.formatSeconds(currentCooldown) : "");
        seconds.setResponder(value -> validate(value.trim()));
        addRenderableWidget(seconds);
        validate(seconds.getValue().trim());
        applyButton = Button.builder(Component.translatable("screen.severownercontrolpanel.cooldown_apply"), button -> {
            parent.sendItemCooldown(seconds.getValue().trim());
            parent.queryItemStatus();
            onClose();
        }).bounds(left + 18, top + 128, 110, 20).build();
        applyButton.active = valid;
        addRenderableWidget(applyButton);
        addRenderableWidget(Button.builder(Component.translatable("screen.severownercontrolpanel.cooldown_reset"), button -> {
            parent.sendItemCommand("cooldown_reset");
            parent.queryItemStatus();
            onClose();
        }).bounds(left + 138, top + 128, 110, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.severownercontrolpanel.cooldown_back"), button -> onClose())
                .bounds(left + 258, top + 128, 104, 20).build());
        setInitialFocus(seconds);
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE8172029);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 3, 0xFF41C7A3);
        graphics.drawString(font, title, left + 14, top + 10, 0xFFE9F4F1, false);
        graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.cooldown.target",
                Component.literal(target), Component.literal(item)), left + 18, top + 34, 0xFF8FE3C1, false);
        Component current;
        if (currentCooldown < 0) current = Component.translatable("screen.severownercontrolpanel.cooldown.unknown");
        else if (currentCooldown == 0) current = Component.translatable("screen.severownercontrolpanel.cooldown.none");
        else current = Component.translatable("screen.severownercontrolpanel.cooldown.seconds", ControlScreen.formatSeconds(currentCooldown));
        graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.cooldown.current", current), left + 18, top + 50, 0xFFD7E4E1, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawWordWrap(font, Component.translatable("screen.severownercontrolpanel.cooldown_hint"), left + 18, top + 158, PANEL_WIDTH - 36, 0xFFD7E4E1);
        if (!valid && !seconds.getValue().trim().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.cooldown_invalid"), left + 18, top + 108, 0xFFFF5555, false);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
