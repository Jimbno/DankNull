package p455w0rd.danknull.inventory.slot;

import static p455w0rd.danknull.util.DankNullStackUtils.isEmpty;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import p455w0rd.danknull.api.IDankNullHandler;

/**
 * The dock GUI has to be able to pull stacks out regardless of the per-stack extraction mode, which the plain
 * {@link SlotDankNull} deliberately honours.
 *
 * <p>
 * 1.12's version also overrode {@code getItemStackLimit(ItemStack)}; 1.7.10's {@code Slot} has no per-stack
 * limit hook, only {@link SlotDankNull#getSlotStackLimit()}, so that override is gone.
 * </p>
 *
 * @author p455w0rd
 */
public class SlotDankNullDock extends SlotDankNull {

    public SlotDankNullDock(final java.util.function.Supplier<IDankNullHandler> handlerSource, final int index,
        final int x, final int y) {
        super(handlerSource, index, x, y);
    }

    @Override
    public ItemStack decrStackSize(final int amount) {
        final IDankNullHandler handler = getDankNullHandler();
        return handler == null ? null : handler.extractItemIngoreExtractionMode(index, amount, false);
    }

    @Override
    public boolean canTakeStack(final EntityPlayer player) {
        final IDankNullHandler handler = getDankNullHandler();
        return handler != null && !isEmpty(handler.extractItemIngoreExtractionMode(index, 1, true));
    }
}
