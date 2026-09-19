package cn.blockforge.generated.severownercontrolpanel.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 客户端物品解析：利用客户端本地化的物品显示名，把中文 / 英文名换算成注册表 ID。
 * 服务端没有 assets 语言文件，因此翻译必须在发送命令前完成。
 */
public final class ItemResolver {
    private ItemResolver() {}

    /** 返回最匹配的规范 ID；无法解析时原样返回输入，交给服务端兜底匹配。 */
    public static String resolveToId(String input) {
        if (input == null || input.isBlank()) return "";
        String value = input.trim();
        Registry<Item> registry = BuiltInRegistries.ITEM;
        ResourceLocation direct = ResourceLocation.tryParse(value);
        if (direct != null && registry.containsKey(direct)) return direct.toString();
        if (value.contains(":")) return value;
        ResourceLocation defaulted = ResourceLocation.tryParse(value.toLowerCase(Locale.ROOT));
        if (defaulted != null && registry.containsKey(defaulted)) return defaulted.toString();
        String needle = value.toLowerCase(Locale.ROOT);
        List<ResourceLocation> namePrefixes = new ArrayList<>();
        List<ResourceLocation> nameContains = new ArrayList<>();
        List<ResourceLocation> pathMatches = new ArrayList<>();
        for (ResourceLocation id : registry.keySet()) {
            Item item = registry.get(id);
            if (item == null) continue;
            String display = displayOf(item);
            if (display.equals(needle)) return id.toString();
            if (display.startsWith(needle)) namePrefixes.add(id);
            else if (display.contains(needle)) nameContains.add(id);
            else if (id.getPath().contains(needle)) pathMatches.add(id);
        }
        if (!namePrefixes.isEmpty()) return namePrefixes.get(0).toString();
        if (!nameContains.isEmpty()) return nameContains.get(0).toString();
        if (!pathMatches.isEmpty()) return pathMatches.get(0).toString();
        return value;
    }

    /** 取物品当前语言的显示名（小写），翻译缺失时回退到英文 key 尾段。 */
    public static String displayOf(Item item) {
        String key = item.getDescriptionId();
        String translated = I18n.get(key);
        if (translated.equals(key)) return key.substring(key.lastIndexOf('.') + 1).replace('_', ' ').toLowerCase(Locale.ROOT);
        return translated.toLowerCase(Locale.ROOT);
    }
}
