package cn.blockforge.generated.severownercontrolpanel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 子界面 B：分组玩家列表。
 * 在物品管理页的“禁用物品列表”按钮输入组名时打开，界面主体同样是原版风格的滚动选择列表
 * （{@link ObjectSelectionList}，与游戏“语言”设置界面同款），逐行展示组内玩家。
 * 点击（或回车）玩家名向服务端请求该玩家的清单，回包会打开玩家禁用物品列表界面。
 */
public final class GroupPlayerListScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 264;
    private static final int LIST_X = 12;
    private static final int LIST_Y = 42;
    private static final int LIST_WIDTH = PANEL_WIDTH - LIST_X * 2;
    private static final int LIST_HEIGHT = 150;
    private static final int ROW_HEIGHT = 22;

    /** 与 PlayerBlockedListScreen 同样的单实例策略，服务端重复回包只刷新不叠窗。 */
    private static GroupPlayerListScreen open;

    /** 父链由 PlayerBlockedListScreen.parentOf 读取，保持包内可见。 */
    final Screen parent;
    private final String groupName;
    private final List<String[]> rows = new ArrayList<>();
    private PlayerList list;
    private int left;
    private int top;

    private GroupPlayerListScreen(Screen parent, String groupName, List<String[]> rows) {
        super(Component.translatable("screen.severownercontrolpanel.group_list.title"));
        this.parent = parent;
        this.groupName = groupName;
        this.rows.addAll(rows);
    }

    /** 处理 "group_players" 载荷：字段为 name\u001fuuid，每行一个组内玩家。 */
    public static void handleGroupData(String data) {
        String[] lines = data.split("\u001e", -1);
        List<String[]> rows = new ArrayList<>();
        for (int index = 1; index < lines.length; index++) {
            String[] fields = lines[index].split("\u001f", -1);
            if (fields.length >= 2) rows.add(fields);
        }
        if (open != null && !open.isLiveOnScreenStack()) {
            // 静态实例被不触发 onClose 的路径顶掉过，视为已关闭，走下方新开流程。
            open = null;
        }
        if (open != null) {
            open.setRows(rows);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // 父界面可能是面板；即使玩家已关闭面板也照常打开列表，返回时自然回到游戏界面。
        open = new GroupPlayerListScreen(minecraft.screen, lines[0], rows);
        minecraft.setScreen(open);
    }

    /** 用服务端回包整体替换列表数据；界面还活着时同步刷新列表控件。 */
    private void setRows(List<String[]> newRows) {
        rows.clear();
        rows.addAll(newRows);
        if (list != null) list.rebuildEntries();
    }

    /** 沿父界面链检查本实例是否仍挂在当前界面栈上。 */
    private boolean isLiveOnScreenStack() {
        Screen current = Minecraft.getInstance().screen;
        while (current != null) {
            if (current == this) return true;
            current = PlayerBlockedListScreen.parentOf(current);
        }
        return false;
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        list = new PlayerList(minecraft, LIST_WIDTH, LIST_HEIGHT, top + LIST_Y, ROW_HEIGHT);
        list.setX(left + LIST_X);
        list.rebuildEntries();
        addRenderableWidget(list);
        addRenderableWidget(Button.builder(Component.translatable("screen.severownercontrolpanel.group_list.back"), button -> onClose())
                .bounds(left + PANEL_WIDTH - 102, top + PANEL_HEIGHT - 30, 86, 20).build());
    }

    /** 点击某个玩家：向服务端请求该玩家的物品清单，"blocked_list" 回包会打开玩家列表界面。 */
    private void requestPlayer(String uuid) {
        if (minecraft != null && minecraft.player != null && minecraft.player.connection != null) {
            minecraft.player.connection.sendCommand("socp item list_view " + quote(uuid));
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
        graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.group_list.group", groupName), left + 14, top + 26, 0xFF8FE3C1, false);
        // 列表控件负责画布景、行内容、高亮描边与滚动条；本方法只补面板边框和空列表提示。
        super.render(graphics, mouseX, mouseY, partialTick);
        if (rows.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.severownercontrolpanel.group_list.empty"), left + 24, top + LIST_Y + 12, 0xFFD7E4E1, false);
        }
        graphics.drawWordWrap(font, Component.translatable("screen.severownercontrolpanel.group_list.hint"), left + 18, top + PANEL_HEIGHT - 62, PANEL_WIDTH - 36, 0xFFD7E4E1);
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
     * 原版风格的玩家选择列表（{@link ObjectSelectionList}）。
     * 行高 22：第一行是序号 + 玩家名，第二行是 UUID。
     */
    private final class PlayerList extends ObjectSelectionList<PlayerList.Entry> {
        PlayerList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
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

        /** 列表中的一行玩家。 */
        final class Entry extends ObjectSelectionList.Entry<Entry> {
            private final String[] row;

            Entry(String[] row) {
                this.row = row;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovering, float partialTick) {
                String name = (index + 1) + ". " + row[0];
                graphics.drawString(font, font.plainSubstrByWidth(name, width - 8), left + 4, top + 2, 0xFFE9F4F1, false);
                String uuid = row.length >= 2 ? row[1] : "";
                graphics.drawString(font, font.plainSubstrByWidth(uuid, width - 8), left + 4, top + 12, 0xFF9DB6B0, false);
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
                PlayerList.this.setSelected(this);
                requestPlayer(row.length >= 2 ? row[1] : row[0]);
                return true;
            }

            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
                    requestPlayer(row.length >= 2 ? row[1] : row[0]);
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() {
                return Component.literal(row[0]);
            }
        }
    }
}
