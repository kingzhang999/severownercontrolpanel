package cn.blockforge.generated.severownercontrolpanel;

import cn.blockforge.generated.severownercontrolpanel.data.ControlData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
        if (player != null && !ControlData.canUsePanel(player)) {
            event.setCanceled(true);
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.severownercontrolpanel.no_permission"));
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
}
