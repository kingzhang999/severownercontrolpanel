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

    public ControlScreen() {
        super(Component.translatable("screen.severownercontrolpanel.title"));
        activeScreen = this;
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
        addRenderableWidget(tab("screen.severownercontrolpanel.players", 0, 12, 102));
        addRenderableWidget(tab("screen.severownercontrolpanel.groups", 1, 120, 102));
        addRenderableWidget(tab("screen.severownercontrolpanel.rules", 2, 228, 102));
        addRenderableWidget(tab("screen.severownercontrolpanel.respawns", 3, 336, 102));
        addRenderableWidget(tab("screen.severownercontrolpanel.teleport", 4, 444, 96));
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
        EditBox player = field("screen.severownercontrolpanel.player_name", "", 18, 98, 170);
        EditBox group = field("screen.severownercontrolpanel.group_name", "", 198, 98, 170);
        action("screen.severownercontrolpanel.group_add", 378, 98, 78, () -> sendPlayerGroup(player, group, "add"));
        action("screen.severownercontrolpanel.group_remove", 462, 98, 78, () -> sendPlayerGroup(player, group, "remove"));
        action("screen.severownercontrolpanel.panel_enable", 18, 132, 118, () -> sendPlayerPanel(player, true));
        action("screen.severownercontrolpanel.panel_disable", 142, 132, 118, () -> sendPlayerPanel(player, false));
        action("screen.severownercontrolpanel.list_players", 266, 132, 118, () -> sendCommand("socp players"));
        drawHint("screen.severownercontrolpanel.players_hint", 18, 178);
    }

    private void buildGroupsPage() {
        graphicsLabel("screen.severownercontrolpanel.groups_heading", 18, 70);
        EditBox name = field("screen.severownercontrolpanel.group_name", "", 18, 98, 210);
        EditBox color = field("screen.severownercontrolpanel.group_color", "#55FFFF", 240, 98, 130);
        action("screen.severownercontrolpanel.group_save", 382, 98, 76, () -> sendCommand("socp groups set " + word(name) + " " + word(color)));
        action("screen.severownercontrolpanel.group_delete", 466, 98, 74, () -> sendCommand("socp groups delete " + word(name)));
        action("screen.severownercontrolpanel.list_groups", 18, 132, 118, () -> sendCommand("socp groups list"));
        drawHint("screen.severownercontrolpanel.groups_hint", 18, 178);
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
        if (page != 3 || fields.size() < 3) return;
        captureRespawnInputs(fields.get(0), fields.get(1), fields.get(2));
    }

    private void captureRespawnInputs(EditBox player, EditBox name, EditBox pos) {
        if (player != null) respawnPlayerInput = player.getValue();
        if (name != null) respawnNameInput = name.getValue();
        if (pos != null) respawnPositionInput = pos.getValue();
    }

    private void sendPlayerGroup(EditBox player, EditBox group, String operation) {
        sendCommand("socp player " + word(player) + " group " + operation + " " + word(group));
    }

    private void sendPlayerPanel(EditBox player, boolean enabled) {
        sendCommand("socp player " + word(player) + " panel " + enabled);
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
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (activeScreen == this) activeScreen = null;
        Minecraft.getInstance().setScreen(null);
    }
}
