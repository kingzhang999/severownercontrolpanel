package cn.blockforge.generated.severownercontrolpanel;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.InvocationTargetException;

public final class SocpNetwork {
    private SocpNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.optional().playToClient(SocpPayload.TYPE, SocpPayload.STREAM_CODEC, SocpNetwork::handleClientPayload);
    }

    public static boolean sendToPlayer(ServerPlayer player, SocpPayload payload) {
        if (player.connection != null && NetworkRegistry.hasChannel(player.connection, payload.type().id())) {
            PacketDistributor.sendToPlayer(player, payload);
            return true;
        }
        return false;
    }

    private static void handleClientPayload(CustomPacketPayload payload, IPayloadContext context) {
        if (!(payload instanceof SocpPayload socpPayload)) return;
        context.enqueueWork(() -> {
            try {
                Class<?> clientEvents = Class.forName("cn.blockforge.generated.severownercontrolpanel.client.ClientEvents");
                clientEvents.getMethod("handlePayload", SocpPayload.class).invoke(null, socpPayload);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
                // The client handler is intentionally absent from dedicated-server class loading.
            }
        });
    }
}
