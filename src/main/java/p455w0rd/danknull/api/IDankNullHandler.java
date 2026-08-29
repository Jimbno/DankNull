package p455w0rd.danknull.api;

import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.google.common.collect.ImmutableList;

import p455w0rd.danknull.api.DankNullItemModes.ItemExtractionMode;
import p455w0rd.danknull.api.DankNullItemModes.ItemPlacementMode;
import p455w0rd.danknull.init.ModGlobals;
import p455w0rd.danknull.util.DankNullStackUtils;

/**
 * The inventory contract of a /dank/null.
 *
 * <p>
 * In 1.12 this was a Forge capability extending {@code IItemHandlerModifiable}. 1.7.10 has neither capabilities nor
 * {@code IItemHandler}, so the item-handler surface ({@link #getSlots()}, {@link #insertItem}, {@link #extractItem},
 * {@link #setStackInSlot}, {@link #getSlotLimit}) is declared directly on this interface instead. The handler is
 * attached to the item via NBT rather than by capability - see
 * {@code p455w0rd.danknull.inventory.DankNullInventoryProvider}.
 * </p>
 *
 * <p>
 * Empty stacks are {@code null} throughout, per 1.7.10 convention.
 * </p>
 *
 * @author BrockWS
 */
public interface IDankNullHandler {

    // ------------------------------------------------------------------
    // item-handler surface (was IItemHandlerModifiable in 1.12)
    // ------------------------------------------------------------------

    int getSlots();

    ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

    ItemStack extractItem(int slot, int amount, boolean simulate);

    void setStackInSlot(int slot, ItemStack stack);

    int getSlotLimit(int slot);

    boolean isItemValid(int slot, ItemStack stack);

    /**
     * Gets the raw list of ItemStacks contained within this handler
     *
     * @return List of ItemStacks, {@code null} entries meaning empty
     */
    List<ItemStack> getStackList();

    /**
     * extracts while ignoring extraction mode..needed for GUI on dock
     */
    default ItemStack extractItemIngoreExtractionMode(final int slot, final int amount, final boolean simulate) {
        return extractItem(slot, amount, simulate);
    }

    /**
     * Override to implement slot validation
     *
     * @param slot Slot index
     * @return The ItemStack that resides in this slot
     */
    default ItemStack getStackInSlot(final int slot) {
        validateSlot(slot);
        return getStackList().get(slot);
    }

    /**
     * Returns the stack in the given slot with<br>
     * extraction rules applied
     *
     * @param slot Slot index
     * @return extractable stack
     */
    ItemStack getExtractableStackInSlot(int slot);

    /**
     * Returns the stack in slot "<i>slot</i>" with<br>
     * actual stack size
     */
    ItemStack getFullStackInSlot(int slot);

    /**
     * Creates a stack with a size of 1 for rendering purposes
     *
     * @param slot Slot index
     * @return visual stack
     */
    ItemStack getRenderableStackForSlot(int slot);

    /**
     * Checks whether the stack matches any stacks in the<br>
     * itemHandler with OreDictionary mode enabled
     *
     * @param stack The stack being checked
     * @return is it is filtered by this itemHandler
     */
    boolean isOreDictFiltered(ItemStack stack);

    /**
     * Checks if the given ItemStack is contained in the inventory
     */
    boolean containsItemStack(ItemStack stack);

    /**
     * Searches the inventory for the given stack, ignoring stack size
     *
     * @return Slot the Stack is in, or -1
     */
    int findItemStack(ItemStack stack);

    /**
     * Searches the inventory and returns all positions of stacks that are of the same item type.
     */
    ImmutableList<Integer> findItemStacks(ItemStack stack);

    /**
     * Searches the inventory for slots whose stored stack has ore-dictionary mode enabled and ore-matches the
     * given stack. {@link #insertItem} converts an insert into such a slot to the stored form.
     */
    ImmutableList<Integer> findOreMatchingStacks(ItemStack stack);

    /**
     * Calculates the amount of non-empty stacks in the inventory
     */
    int stackCount();

    ModGlobals.DankNullTier getTier();

    int getSelected();

    void setSelected(int slot);

    /**
     * Cycles to the next (or previous) index
     *
     * @param forward Cycle forwards if true
     */
    void cycleSelected(boolean forward);

    /** Used for the Creative /dank/null. */
    boolean isLocked();

    void setLocked(boolean lock);

    boolean isLockingSupported();

    void setOre(ItemStack stack, boolean ore);

    boolean isOre(ItemStack stack);

    boolean isOreSupported(ItemStack stack);

    /** Shouldn't be modified. */
    Map<ItemStack, Boolean> getOres();

    void setExtractionMode(ItemStack stack, ItemExtractionMode mode);

    void cycleExtractionMode(ItemStack stack, boolean forward);

    ItemExtractionMode getExtractionMode(ItemStack stack);

    /** Shouldn't be modified. */
    Map<ItemStack, ItemExtractionMode> getExtractionModes();

    void setPlacementMode(ItemStack stack, ItemPlacementMode mode);

    void cyclePlacementMode(ItemStack stack, boolean forward);

    ItemPlacementMode getPlacementMode(ItemStack stack);

    /** Shouldn't be modified. */
    Map<ItemStack, ItemPlacementMode> getPlacementMode();

    /**
     * Re-points the selection at a non-empty slot after the contents changed. Keeping the selection valid is a
     * handler invariant, so it lives here rather than being something each caller has to remember to invoke.
     */
    default void updateSelectedSlot() {
        final int currentlySelected = getSelected();
        if (currentlySelected >= 0 && !DankNullStackUtils.isEmpty(getFullStackInSlot(currentlySelected))) {
            return;
        }
        int newSelected = -1;
        if (currentlySelected > 0) {
            for (int i = currentlySelected; i > -1; i--) {
                if (DankNullStackUtils.isEmpty(getFullStackInSlot(i))) {
                    continue;
                }
                newSelected = i;
                break;
            }
        } else if (!DankNullStackUtils.isEmpty(getFullStackInSlot(0))) {
            newSelected = 0;
        }
        setSelected(newSelected);
    }

    default void validateSlot(final int slot) {
        if (slot < 0 || slot >= getSlots()) {
            throw new RuntimeException("Slot " + slot + " not in valid range - [0," + getSlots() + ")");
        }
    }

    /** Convenience mirror of the 1.12 {@code ItemStack.isEmpty()} used pervasively by callers. */
    default boolean isSlotEmpty(final int slot) {
        return DankNullStackUtils.isEmpty(getStackInSlot(slot));
    }
}
