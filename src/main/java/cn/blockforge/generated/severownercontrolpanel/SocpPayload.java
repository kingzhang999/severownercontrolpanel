package cn.blockforge.generated.severownercontrolpanel;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SocpPayload(String action, String data) implements CustomPacketPayload {
    public static final Type<SocpPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SocpPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SocpPayload::action,
            ByteBufCodecs.STRING_UTF8, SocpPayload::data,
            SocpPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
