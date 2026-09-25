package cn.blockforge.generated.severownercontrolpanel.client;

import cn.blockforge.generated.severownercontrolpanel.GeneratedMod;
import cn.blockforge.generated.severownercontrolpanel.SocpPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(modid = GeneratedMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    public static final KeyMapping OPEN_PANEL = new KeyMapping("key.severownercontrolpanel.open_panel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, KeyMapping.CATEGORY_INTERFACE);

    /** 服务端同步过来的本玩家禁用物品，用于在客户端预测阶段提前取消右键使用。 */
    private static final Set<ResourceLocation> BLOCKED_ITEMS = new HashSet<>();

    private ClientEvents() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(OPEN_PANEL); }

    /**
     * 切换世界时清空服务端同步的禁用物品缓存：否则从多人服退出后再进入本地单人存档，
     * 上一次的禁用列表会残留，客户端仍在右键预测阶段取消使用，单人档就被“静默”地改写了行为。
     */
    @SubscribeEvent
    public static void loggingIn(ClientPlayerNetworkEvent.LoggingIn event) { BLOCKED_ITEMS.clear(); }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) { BLOCKED_ITEMS.clear(); }

    /**
     * 食物、药水这类持续使用物品在右键瞬间会先在客户端本地预测执行 {@code ItemStack.use}，
     * 之后即使服务端取消了事件，客户端也会卡在“正在使用”状态且永远收不到结束同步。
     * 这里根据服务端同步的禁用列表在预测之前就取消，从根源上避免一直使用不结束。
     */
    @SubscribeEvent
    public static void rightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof LocalPlayer)) return;
        Item item = event.getItemStack().getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null || !BLOCKED_ITEMS.contains(id)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        // F8 只发起服务端命令，是否允许打开面板完全由服务端实时决定。
        // 本地单人存档下命令会被服务端静默取消，这里先不发，避免键盘操作产生任何可感知行为。
        if (OPEN_PANEL.consumeClick() && minecraft.player != null && minecraft.screen == null
                && minecraft.player.connection != null && !isLocalSingleplayer(minecraft)) {
            minecraft.player.connection.sendCommand("socp panel");
        }
    }

    /** 本地未开放到局域网的集成服务器即单人档；局域网开放会走多人逻辑。 */
    private static boolean isLocalSingleplayer(Minecraft minecraft) {
        return minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null
                && !minecraft.getSingleplayerServer().isPublished();
    }

    public static void handlePayload(SocpPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (payload.action()) {
            case "open_panel" -> minecraft.setScreen(new ControlScreen());
            case "close_panel" -> {
                if (minecraft.screen instanceof ControlScreen) minecraft.setScreen(null);
            }
            case "respawn_options" -> {
                int separator = payload.data().indexOf('\u001f');
                String selected = separator < 0 ? payload.data() : payload.data().substring(0, separator);
                String options = separator < 0 ? "" : payload.data().substring(separator + 1);
                ControlScreen.updateRespawnOptions(selected, options);
            }
            case "gamemode_update" -> {
                String[] parts = payload.data().split("\\u001f", -1);
                if (parts.length >= 3) ControlScreen.updateGameMode(parts[0], parts[1], parts[2], parts.length >= 4 && "1".equals(parts[3]));
            }
            case "groups_list" -> ControlScreen.updateGroupsList(payload.data());
            case "item_status" -> ControlScreen.applyItemStatus(payload.data().split("\u001f", -1));
            case "blocked_list" -> PlayerBlockedListScreen.handleListData(payload.data());
            case "group_players" -> GroupPlayerListScreen.handleGroupData(payload.data());
            case "blocked_items" -> updateBlockedItems(payload.data());
            case "stop_using" -> {
                if (minecraft.player != null) minecraft.player.stopUsingItem();
            }
            default -> { }
        }
    }

    private static void updateBlockedItems(String data) {
        BLOCKED_ITEMS.clear();
        if (data.isEmpty()) return;
        for (String id : data.split("\u001e", -1)) {
            if (id.isEmpty()) continue;
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location != null) BLOCKED_ITEMS.add(location);
        }
    }
}
