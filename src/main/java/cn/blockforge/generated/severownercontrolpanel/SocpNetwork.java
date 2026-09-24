package cn.blockforge.generated.severownercontrolpanel;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SocpNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();

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
            } catch (ClassNotFoundException absent) {
                // 专用服务器不加载客户端处理类，这是预期情况，不是错误。
            } catch (ReflectiveOperationException failure) {
                // 反射到方法本身失败（方法签名漂移、处理逻辑抛错等）必须留下日志，不能静默吞掉。
                LOGGER.error("处理客户端载荷 {} 失败", socpPayload.action(), failure);
            }
        });
    }
}
