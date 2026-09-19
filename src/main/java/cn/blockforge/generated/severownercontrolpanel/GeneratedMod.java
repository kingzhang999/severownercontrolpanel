package cn.blockforge.generated.severownercontrolpanel;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(GeneratedMod.MOD_ID)
public final class GeneratedMod {
    public static final String MOD_ID = "severownercontrolpanel";

    public GeneratedMod(IEventBus modBus) {
        ControlCommands.registerArgumentTypes(modBus);
        modBus.addListener(SocpNetwork::registerPayloads);
    }
}
