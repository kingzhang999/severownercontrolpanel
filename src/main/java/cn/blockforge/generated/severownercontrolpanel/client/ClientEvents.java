package cn.blockforge.generated.severownercontrolpanel.client;

import cn.blockforge.generated.severownercontrolpanel.GeneratedMod;
import cn.blockforge.generated.severownercontrolpanel.SocpPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = GeneratedMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    public static final KeyMapping OPEN_PANEL = new KeyMapping("key.severownercontrolpanel.open_panel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, KeyMapping.CATEGORY_INTERFACE);

    private ClientEvents() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(OPEN_PANEL); }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (OPEN_PANEL.consumeClick() && minecraft.player != null && minecraft.screen == null && minecraft.player.connection != null) {
            minecraft.player.connection.sendCommand("socp panel");
        }
    }

    public static void handlePayload(SocpPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (payload.action()) {
            case "open_panel" -> minecraft.setScreen(new ControlScreen());
            case "clipboard" -> minecraft.keyboardHandler.setClipboard(payload.data());
            case "respawn_options" -> {
                int separator = payload.data().indexOf('\u001f');
                String selected = separator < 0 ? payload.data() : payload.data().substring(0, separator);
                String options = separator < 0 ? "" : payload.data().substring(separator + 1);
                ControlScreen.updateRespawnOptions(selected, options);
            }
            default -> { }
        }
    }
}
