package wraith.waystones.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import wraith.waystones.Waystones;
import wraith.waystones.WaystonesClient;
import wraith.waystones.block.WaystoneBlock;
import wraith.waystones.block.WaystoneBlockEntity;

import java.util.List;

public class LocalVoid extends Item {

    public LocalVoid(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("waystone")) {
            return TypedActionResult.fail(stack);
        }
        String hash = tag.getString("waystone");
        if (Waystones.WAYSTONE_STORAGE != null) {
            WaystoneBlockEntity waystone = Waystones.WAYSTONE_STORAGE.getWaystone(hash);
            if (waystone == null) {
                stack.setTag(null);
            } else {
                if (!user.isCreative()) {
                    stack.decrement(1);
                }
                waystone.teleportPlayer(user, false);
            }
        }
        if (stack.isEmpty()) {
            user.setStackInHand(hand, ItemStack.EMPTY);
        }
        stack = user.getStackInHand(hand);
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        WaystoneBlockEntity entity = WaystoneBlock.getEntity(world, context.getBlockPos());
        if (entity != null) {
            ItemStack stack = context.getStack();
            CompoundTag tag = new CompoundTag();
            tag.putString("waystone", entity.getHash());
            stack.setTag(tag);
        }
        return super.useOnBlock(context);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        String name = null;
        CompoundTag tag = stack.getTag();
        boolean invalid = false;
        if (tag == null || !tag.contains("waystone")) {
            invalid = true;
        } else {
            name = WaystonesClient.WAYSTONE_STORAGE.getName(tag.getString("waystone"));
            if (name == null) {
                invalid = true;
            }
        }
        if (invalid) {
            tooltip.add(new TranslatableText("waystones.local_void.empty_tooltip").formatted(Formatting.DARK_AQUA));
            return;
        }
        tooltip.add(new TranslatableText("waystones.local_void.tooltip").append(" " + name).formatted(Formatting.GOLD));
    }

}
