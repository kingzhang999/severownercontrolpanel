package cn.blockforge.generated.severownercontrolpanel;

import cn.blockforge.generated.severownercontrolpanel.data.ControlData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = GeneratedMod.MOD_ID)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) { ControlCommands.register(event); }

    @SubscribeEvent
    public static void command(CommandEvent event) {
        String input = event.getParseResults().getReader().getString().trim();
        if (input.startsWith("/")) input = input.substring(1).trim();
        if (!input.equals("socp") && !input.startsWith("socp ")) return;

        var source = event.getParseResults().getContext().getSource();
        ServerPlayer player = source.getPlayer();
        // 事件层覆盖未安装客户端、旧聊天按钮和权限变更后的所有命令请求。
        if (!ControlData.isMultiplayer() || player != null && !ControlData.canUsePanel(player)) {
            event.setCanceled(true);
            if (player != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.severownercontrolpanel.no_permission"));
            }
        }
    }

    @SubscribeEvent
    public static void serverStarting(ServerStartingEvent event) { ControlData.init(event.getServer()); }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) { ControlData.shutdown(); }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ControlData.registerLogin(player);
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) { ControlData.registerDeath(event.getEntity()); }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ControlData.registerRespawn(player);
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) { ControlData.tick(event.getServer()); }

    /** 被禁用或处于冷却中的物品不允许右键使用（包括对着方块使用）。 */
    @SubscribeEvent
    public static void rightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && !ControlData.allowUseItem(player, event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && !ControlData.allowUseItem(player, event.getItemStack(), false)) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        }
    }

    /**
     * 兜底：任何绕过右键事件直接让物品开始使用的入口，只要物品被禁用就取消“开始使用”。
     * 食物、药水这类持续使用物品若已经开始，还会由服务端每 tick 的兜底逻辑打断。
     */
    @SubscribeEvent
    public static void startUsingItem(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player && ControlData.isItemBlocked(player, event.getItem().getItem())) {
            event.setCanceled(true);
            ControlData.notifyStopUsing(player);
        }
    }

    /**
     * 需要按住右键持续使用的物品（食物、药水等）在真正使用完成后才开始计算冷却。
     * {@code event.getItem()} 是使用前的物品拷贝，因此能准确对应用户配置的物品。
     */
    @SubscribeEvent
    public static void finishUsingItem(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ControlData.markItemUsed(player, event.getItem());
        }
    }
}
