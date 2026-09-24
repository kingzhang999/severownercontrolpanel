package cn.blockforge.generated.severownercontrolpanel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 子界面 A：玩家禁用物品列表。
 * 界面主体是一个原版风格的滚动选择列表（{@link ObjectSelectionList}，与游戏“语言”设置界面同款：
 * 带列表背景、右侧滚动条、逐行高亮与选中描边），逐行展示该玩家当前被禁用或设了冷却时间的物品。
 * 点击任意一行进入编辑菜单，可设置可使用状态或冷却时间。
 * 底部批量输入框支持区间（1-3）与序号（2,5）混用，留空则切换全部物品的可使用状态。
 * 数据来自服务端 "blocked_list" 载荷，顺序与服务端 ITEM_RULES 一致，序号即列表行号（从 1 开始）。
 */
public final class PlayerBlockedListScreen extends Screen {
    private static final int PANEL_WIDTH = 480;
    private static final int PANEL_HEIGHT = 300;
    private static final int LIST_X = 12;
    private static final int LIST_Y = 42;
    private static final int LIST_WIDTH = PANEL_WIDTH - LIST_X * 2;
    private static final int LIST_HEIGHT = 186;
    private static final int ROW_HEIGHT = 22;

    /** 列表中打开的唯一实例；服务端回包优先就地刷新它，保证编辑界面返回后数据是最新的。 */
    private static PlayerBlockedListScreen open;

    private final Screen parent;
    private final String playerKey;
    private final String playerDisplay;
    private final List<String[]> rows = new ArrayList<>();
    private BlockedItemList list;
    private EditBox batchInput;
    private int left;
    private int top;

    private PlayerBlockedListScreen(Screen parent, String playerKey, String playerDisplay, List<String[]> rows) {
        super(Component.translatable("screen.severownercontrolpanel.blocked.title"));
        this.parent = parent;
        this.playerKey = playerKey;
        this.playerDisplay = playerDisplay;
        this.rows.addAll(rows);
    }

    /** 处理 "blocked_list" 载荷：已打开则刷新行数据，未打开则基于当前屏幕新开一层。 */
    public static void handleListData(String data) {
        String[] lines = data.split("\u001e", -1);
        if (lines.length < 2) return;
        List<String[]> rows = new ArrayList<>();
        for (int index = 2; index < lines.length; index++) {
            String[] fields = lines[index].split("\u001f", -1);
            // 每行：物品 ID / 禁用 / 冷却 / 规则类型 / 规则键 / 来源。旧格式只有前 3 段时按玩家规则兜底。
            if (fields.length >= 6) {
                rows.add(fields);
            } else if (fields.length >= 3) {
                rows.add(new String[]{fields[0], fields[1], fields[2], "player", "", ""});
            }
        }
        if (open != null && (!open.isLiveOnScreenStack() || !open.playerKey.equalsIgnoreCase(lines[0]))) {
            // 静态实例被死亡界面等不触发 onClose 的路径顶掉过，或换了查看对象，则视为已关闭，走下方新开流程。
            open = null;
        }
        if (open != null) {
            open.setRows(rows);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // 父界面可能是面板；即使玩家已关闭面板也照常打开列表，返回时自然回到游戏界面。
        open = new PlayerBlockedListScreen(minecraft.screen, lines[0], lines[1], rows);
        minecraft.setScreen(open);
    }

    /** 用服务端回包整体替换列表数据；界面还活着时同步刷新列表控件。 */
    private void setRows(List<String[]> newRows) {
        rows.clear();
        rows.addAll(newRows);
        if (list != null) list.rebuildEntries();
    }

    /** 沿父界面链检查本实例是否仍挂在当前界面栈上（编辑界面在顶层时也算存活）。 */
    private boolean isLiveOnScreenStack() {
        Screen current = Minecraft.getInstance().screen;
        while (current != null) {
            if (current == this) return true;
            current = parentOf(current);
        }
        return false;
    }

    /** 面板子界面共同的父链访问；链上其余屏幕的父级一律视为面板根（ControlScreen）。 */
    static Screen parentOf(Screen screen) {
        if (screen instanceof PlayerBlockedListScreen list) return list.parent;
        if (screen instanceof ItemCooldownScreen cooldown) return cooldown.parent;
        if (screen instanceof ItemRuleEditScreen edit) return edit.parent;
        if (screen instanceof GroupPlayerListScreen group) return group.parent;
        return null;
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        list = new BlockedItemList(minecraft, LIST_WIDTH, LIST_HEIGHT, top + LIST_Y, ROW_HEIGHT);
        list.setX(left + LIST_X);
        list.rebuildEntries();
        addRenderableWidget(list);
        batchInput = new EditBox(font, left + 18, top + 238, 246, 20, Component.translatable("screen.severownercontrolpanel.blocked.batch"));
        batchInput.setHint(Component.translatable("screen.severownercontrolpanel.blocked.batch"));
        batchInput.setMaxLength(128);
        // 只放行数字、逗号与短横，空格等非法字符直接输不进去，避免拼命令时参数被截断。
        batchInput.setFilter(value -> value.matches("[0-9,\\-]*"));
        addRenderableWidget(batchInput);
        addRenderableWidget(Button.builder(Component.translatable("screen.severownercontrolpanel.blocked.batch_apply"), button -> applyBatch())
                .bounds(left + 270, top + 238, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.severownercontrolpanel.blocked.back"), button -> onClose())
                .bounds(left + 372, top + 238, 96, 20).build());
    }

    /** 发送批量切换命令；输入为空即切换全部。batch 执行后服务端会重发清单，本界面随回包刷新。 */
    private void applyBatch() {
        String spec = batchInput.getValue().trim();
        StringBuilder command = new StringBuilder("socp item batch ").append(quote(playerKey));
        if (!spec.isEmpty()) command.append(' ').append(spec);
        runCommand(command.toString());
    }

    /** 点击/回车某一行时打开该物品的编辑菜单。 */
    private void openEditor(String[] row) {
        // 分组继承来的规则要用分组名去编辑，玩家自身的规则才用玩家键，否则会误建一条重复的玩家规则。
        String ruleTarget = row.length >= 5 && !row[4].isBlank() ? row[4] : playerKey;
        String ruleSource = row.length >= 6 && !row[5].isBlank() ? row[5] : playerDisplay;
        minecraft.setScreen(new ItemRuleEditScreen(this, playerKey, playerDisplay, ruleTarget, ruleSource, row[0], "1".equals(row[1]), parseSeconds(row[2])));
    }

    private static double parseSeconds(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }

    /** 物品在当前语言下的显示名；注册表查不到时退回原始 ID。 */
    private String displayOf(String itemId) {
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        Item item = location == null ? null : BuiltInRegistries.ITEM.get(location);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return itemId;
        String display = ItemResolver.displayOf(item);
        return display == null || display.isEmpty() ? itemId : display;
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
        graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.blocked.player", playerDisplay), left + 14, top + 26, 0xFF8FE3C1, false);
        // 列表控件负责画布景、行内容、高亮描边与滚动条；本方法只补面板边框和空列表提示。
        super.render(graphics, mouseX, mouseY, partialTick);
        if (rows.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.blocked.empty"), left + 24, top + LIST_Y + 12, 0xFFD7E4E1, false);
        }
        graphics.drawWordWrap(font, Component.translatable("screen.severownercontrolpanel.blocked.hint"), left + 18, top + 264, PANEL_WIDTH - 36, 0xFFD7E4E1);
    }

    private void runCommand(String command) {
        if (minecraft != null && minecraft.player != null && minecraft.player.connection != null) {
            minecraft.player.connection.sendCommand(command);
        }
    }

    void refreshFromServer() {
        runCommand("socp item list_view " + quote(playerKey));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (open == this) open = null;
        minecraft.setScreen(parent);
    }

    /**
     * 原版风格的物品选择列表（{@link ObjectSelectionList}）。
     * 行高 22：第一行是序号 + 物品名与右对齐的“状态 · 冷却”，第二行是物品 ID 与规则来源。
     */
    private final class BlockedItemList extends ObjectSelectionList<BlockedItemList.Entry> {
        BlockedItemList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        /** 按当前 rows 重建全部行。 */
        void rebuildEntries() {
            List<Entry> entries = new ArrayList<>();
            for (String[] row : rows) entries.add(new Entry(row));
            replaceEntries(entries);
        }

        @Override
        public int getRowWidth() {
            // 右侧留出滚动条与选中描边的空间。
            return this.getWidth() - 20;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getX() + this.getWidth() - 6;
        }

        /** 列表中的一行物品。 */
        final class Entry extends ObjectSelectionList.Entry<Entry> {
            private final String[] row;

            Entry(String[] row) {
                this.row = row;
            }

            private boolean disabled() {
                return "1".equals(row[1]);
            }

            private String source() {
                return row.length >= 6 && !row[5].isBlank() ? row[5] : "";
            }

            private Component status() {
                Component state = Component.translatable(disabled()
                        ? "screen.severownercontrolpanel.item_toggle_disabled"
                        : "screen.severownercontrolpanel.item_toggle_enabled");
                double cooldown = parseSeconds(row[2]);
                Component cd = cooldown > 0
                        ? Component.translatable("screen.severownercontrolpanel.cooldown.seconds", ControlScreen.formatSeconds(cooldown))
                        : Component.translatable("screen.severownercontrolpanel.cooldown.none");
                return Component.translatable("screen.severownercontrolpanel.blocked.row_status", state, cd);
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovering, float partialTick) {
                int color = disabled() ? 0xFFFF6B6B : 0xFF8FE3C1;
                String name = (index + 1) + ". " + displayOf(row[0]);
                graphics.drawString(font, font.plainSubstrByWidth(name, width - 8), left + 4, top + 2, color, false);
                Component status = status();
                graphics.drawString(font, status, left + width - font.width(status) - 4, top + 2, color, false);
                String source = source();
                String detail = source.isEmpty() ? row[0] : row[0] + "  ·  " + source;
                graphics.drawString(font, font.plainSubstrByWidth(detail, width - 8), left + 4, top + 12, 0xFF9DB6B0, false);
            }

            @Override
            public void renderBack(GuiGraphics graphics, int index, int top, int left, int width, int height,
                                   int mouseX, int mouseY, boolean hovering, float partialTick) {
                if (hovering) {
                    graphics.fill(left, top - 1, left + width, top + height + 1, 0x33FFFFFF);
                }
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button != 0) return false;
                BlockedItemList.this.setSelected(this);
                openEditor(row);
                return true;
            }

            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
                    openEditor(row);
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() {
                return Component.literal(displayOf(row[0]) + " ").append(status());
            }
        }
    }
}
