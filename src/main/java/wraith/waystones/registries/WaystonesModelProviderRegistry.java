package wraith.waystones.registries;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.nbt.CompoundTag;
import wraith.waystones.Utils;
import wraith.waystones.item.LocalVoid;
import wraith.waystones.item.WaystoneScroll;

@Environment(EnvType.CLIENT)
public final class WaystonesModelProviderRegistry {

    public static void register() {
        FabricModelPredicateProviderRegistry.register(Utils.ID("has_learned"),
                (itemStack, clientWorld, livingEntity) -> {
                    if (itemStack.isEmpty()) {
                        return 0f;
                    }
                    if (itemStack.getItem() instanceof WaystoneScroll) {
                        CompoundTag tag = itemStack.getTag();
                        return tag == null || !tag.contains("waystones") || tag.getList("waystones", 8).isEmpty() ? 0 : 1;
                    } else if (itemStack.getItem() instanceof LocalVoid) {
                        CompoundTag tag = itemStack.getTag();
                        return tag == null || !tag.contains("waystone") ? 0 : 1;
                    }
                    return 0f;
                }
        );
    }

}
