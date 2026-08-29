package p455w0rd.danknull.inventory.slot;

import static p455w0rd.danknull.util.DankNullStackUtils.isEmpty;

import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import p455w0rd.danknull.api.IDankNullHandler;

/**
 * A container slot backed by an {@link IDankNullHandler}.
 *
 * <p>
 * 1.12 used Forge's {@code SlotItemHandler}, which does not exist in 1.7.10. Rather than wrapping the handler in
 * an {@code IInventory} adapter, this subclasses {@link Slot} directly and overrides every method that would
 * otherwise touch {@link Slot#inventory} - that field is left {@code null} on purpose so an accidental unrouted
 * access fails loudly instead of silently reading the wrong inventory.
 * </p>
 *
 * <p>
 * The handler is resolved through a supplier on every access rather than captured once. For a /dank/null held in
 * the player's inventory the backing ItemStack object is replaced on any slot sync, and a captured handler would
 * then be writing into a stack that is no longer in the inventory - edits made in the GUI would be silently lost.
 * </p>
 *
 * @author p455w0rd
 */
public class SlotDankNull extends Slot {

    private final Supplier<IDankNullHandler> handlerSource;
    protected final int index;

    public SlotDankNull(final Supplier<IDankNullHandler> handlerSource, final int index, final int x, final int y) {
        super(null, index, x, y);
        this.handlerSource = handlerSource;
        this.index = index;
    }

    /** May be {@code null} if the /dank/null has left the slot the container was opened on. */
    public IDankNullHandler getDankNullHandler() {
        return handlerSource.get();
    }

    @Override
    public ItemStack getStack() {
        final IDankNullHandler handler = getDankNullHandler();
        return handler == null ? null : handler.getFullStackInSlot(index);
    }

    @Override
    public boolean getHasStack() {
        return !isEmpty(getStack());
    }

    @Override
    public void putStack(final ItemStack stack) {
        final IDankNullHandler handler = getDankNullHandler();
        if (handler == null) {
            return;
        }
        handler.setStackInSlot(index, isEmpty(stack) ? null : stack);
        onSlotChanged();
    }

    /**
     * The handler persists itself, and there is no backing {@link IInventory} to mark dirty.
     */
    @Override
    public void onSlotChanged() {}

    @Override
    public void onPickupFromSlot(final EntityPlayer player, final ItemStack stack) {
        onSlotChanged();
    }

    @Override
    public int getSlotStackLimit() {
        final IDankNullHandler handler = getDankNullHandler();
        return handler == null ? 0 : handler.getSlotLimit(index);
    }

    @Override
    public boolean isItemValid(final ItemStack stack) {
        final IDankNullHandler handler = getDankNullHandler();
        return !isEmpty(stack) && handler != null && handler.isItemValid(index, stack);
    }

    @Override
    public ItemStack decrStackSize(final int amount) {
        final IDankNullHandler handler = getDankNullHandler();
        return handler == null ? null : handler.extractItem(index, amount, false);
    }

    @Override
    public boolean canTakeStack(final EntityPlayer player) {
        final IDankNullHandler handler = getDankNullHandler();
        return handler != null && !isEmpty(handler.extractItem(index, 1, true));
    }

    /**
     * This slot is not part of any {@link IInventory}, so it can never match one.
     */
    @Override
    public boolean isSlotInInventory(final IInventory inventory, final int slotIndex) {
        return false;
    }
}
