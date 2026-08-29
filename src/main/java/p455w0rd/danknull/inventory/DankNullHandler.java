package p455w0rd.danknull.inventory;

import static p455w0rd.danknull.util.DankNullStackUtils.areItemStacksEqualIgnoreSize;
import static p455w0rd.danknull.util.DankNullStackUtils.canItemStacksStack;
import static p455w0rd.danknull.util.DankNullStackUtils.constrainToRange;
import static p455w0rd.danknull.util.DankNullStackUtils.copyWithSize;
import static p455w0rd.danknull.util.DankNullStackUtils.isEmpty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import p455w0rd.danknull.api.DankNullItemModes.ItemExtractionMode;
import p455w0rd.danknull.api.DankNullItemModes.ItemPlacementMode;
import p455w0rd.danknull.api.IDankNullHandler;
import p455w0rd.danknull.init.ModConfig;
import p455w0rd.danknull.init.ModConfig.Options;
import p455w0rd.danknull.init.ModGlobals;
import p455w0rd.danknull.items.ItemDankNull;

/**
 * @author BrockWS
 */
public class DankNullHandler implements IDankNullHandler {

    public int selected;
    public boolean isLocked;
    private final ModGlobals.DankNullTier tier;
    private final List<ItemStack> stacks;
    private final Map<ItemStack, Boolean> oreStacks;
    private final Map<ItemStack, ItemExtractionMode> extractionStacks;
    private final Map<ItemStack, ItemPlacementMode> placementStacks;

    public DankNullHandler(final ModGlobals.DankNullTier tier) {
        this.tier = tier;
        final int size = this.tier.getNumRows() * 9;
        stacks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            stacks.add(null);
        }
        oreStacks = new HashMap<>();
        extractionStacks = new HashMap<>();
        placementStacks = new HashMap<>();
        selected = -1;
        isLocked = false;
    }

    public static List<String> getOreNames(final ItemStack stack) {
        if (isEmpty(stack)) {
            return new ArrayList<>();
        }
        final int[] oreIds = OreDictionary.getOreIDs(stack);
        if (oreIds.length > 0) {
            final List<String> nameList = new ArrayList<>();
            for (final int oreId : oreIds) {
                final String name = OreDictionary.getOreName(oreId);
                if (!name.equals("Unknown") && ModConfig.isValidOre(name)) {
                    nameList.add(name);
                }
            }
            return nameList;
        }
        return new ArrayList<>();
    }

    @Override
    public ItemStack getExtractableStackInSlot(final int slot) {
        validateSlot(slot);
        final ItemStack raw = getStackList().get(slot);
        if (isEmpty(raw)) {
            return null;
        }
        final ItemStack slotStack = raw.copy();
        if (getExtractionMode(slotStack) == ItemExtractionMode.KEEP_ALL) {
            return null;
        }
        final int amountToBeKept = getExtractionMode(slotStack).getNumberToKeep();
        if (slotStack.stackSize > amountToBeKept) {
            return copyWithSize(slotStack, slotStack.stackSize - amountToBeKept);
        }
        return null;
    }

    @Override
    public ItemStack getFullStackInSlot(final int slot) {
        validateSlot(slot);
        return getStackList().get(slot);
    }

    @Override
    public ItemStack getRenderableStackForSlot(final int slot) {
        final ItemStack stack = getStackInSlot(slot);
        if (isEmpty(stack)) {
            return null;
        }
        return copyWithSize(stack, 1);
    }

    @Override
    public void setStackInSlot(final int slot, final ItemStack stack) {
        validateSlot(slot);
        getStackList().set(slot, isEmpty(stack) ? null : stack);
        onDataChanged();
    }

    @Override
    public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
        if (isEmpty(stack)) {
            return stack;
        }

        // Convert before validating so an ore-dict variant of a stored (ore-enabled) stack is accepted and merged
        // rather than rejected. If the slot still refuses it, the caller gets its ORIGINAL stack back untouched.
        final ItemStack toInsert = convertOreDict(stack);
        if (!isItemValid(slot, toInsert)) {
            return stack;
        }
        final ItemStack existingStack = getStackList().get(slot);

        if (isEmpty(existingStack)) {
            if (!simulate) {
                getStackList().set(slot, toInsert.copy());
                onDataChanged();
            }
            return null;
        }

        if (!canItemStacksStack(toInsert, existingStack)) {
            return stack;
        }

        // long arithmetic: the emerald/creative tiers cap at Integer.MAX_VALUE, so int addition could overflow
        // negative near the cap - the slot's stackSize would go negative and the serializer would drop it entirely.
        final long combined = (long) existingStack.stackSize + toInsert.stackSize;
        final int newInternalCount = (int) Math.min(combined, tier.getMaxStackSize());
        final int returnCount = (int) (combined - newInternalCount);

        if (!simulate) {
            existingStack.stackSize = newInternalCount;
            onDataChanged();
        }

        if (returnCount == 0) {
            return null;
        }
        return copyWithSize(stack, returnCount);
    }

    @Override
    public boolean isOreDictFiltered(final ItemStack stack) {
        for (final ItemStack storedStack : getStackList()) {
            if (!isEmpty(storedStack) && oreMatches(storedStack, stack)) {
                return true;
            }
        }
        return false;
    }

    private ItemStack convertOreDict(final ItemStack incomingStack) {
        for (final ItemStack storedStack : getStackList()) {
            if (isEmpty(storedStack)) {
                continue;
            }
            if (isOre(storedStack) && !isOre(incomingStack) && oreMatches(storedStack, incomingStack)) {
                return copyWithSize(storedStack, incomingStack.stackSize);
            }
        }
        return incomingStack;
    }

    private boolean oreMatches(final ItemStack storedStack, final ItemStack incomingStack) {
        final List<String> oreNamesForStoredStack = getOreNames(storedStack);
        final List<String> oreNamesForIncomingStack = getOreNames(incomingStack);
        for (final String currentStoredName : oreNamesForStoredStack) {
            if (!ModConfig.isValidOre(currentStoredName)) {
                continue;
            }
            for (final String currentIncomingName : oreNamesForIncomingStack) {
                if (ModConfig.isValidOre(currentIncomingName) && currentIncomingName.equals(currentStoredName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
        if (amount < 1) {
            return null;
        }
        validateSlot(slot);

        final ItemStack existing = getStackList().get(slot);
        if (isEmpty(existing)) {
            return null;
        }

        final int existingCount = existing.stackSize;
        // A locked /dank/null's set of stored item types is frozen, so the last item can never be pulled out.
        final int requiredToKeep = Math.max(getExtractionMode(existing).getNumberToKeep(), isLocked() ? 1 : 0);
        final int extract = Math.min(Math.min(amount, existing.getMaxStackSize()), existingCount - requiredToKeep);
        if (extract < 1) {
            return null;
        }
        if (existingCount <= extract) {
            if (!simulate) {
                getStackList().set(slot, null);
                onDataChanged();
            }
            return existing;
        }
        if (!simulate) {
            getStackList().set(slot, copyWithSize(existing, existingCount - extract));
            onDataChanged();
        }
        return copyWithSize(existing, extract);
    }

    /**
     * The dock GUI needs to pull stacks out regardless of the per-stack extraction mode, which
     * {@link #extractItem(int, int, boolean)} deliberately honours.
     */
    @Override
    public ItemStack extractItemIngoreExtractionMode(final int slot, final int amount, final boolean simulate) {
        if (amount < 1) {
            return null;
        }
        validateSlot(slot);

        final ItemStack existing = getStackList().get(slot);
        if (isEmpty(existing)) {
            return null;
        }

        final int existingCount = existing.stackSize;
        // Even ignoring the extraction mode, a locked /dank/null never gives up the last item of a stored type.
        final int extract = Math
            .min(Math.min(amount, existing.getMaxStackSize()), existingCount - (isLocked() ? 1 : 0));
        if (extract < 1) {
            return null;
        }
        if (existingCount <= extract) {
            if (!simulate) {
                getStackList().set(slot, null);
                onDataChanged();
            }
            return existing;
        }
        if (!simulate) {
            getStackList().set(slot, copyWithSize(existing, existingCount - extract));
            onDataChanged();
        }
        return copyWithSize(existing, extract);
    }

    @Override
    public boolean containsItemStack(final ItemStack stack) {
        return findItemStack(stack) > -1;
    }

    @Override
    public int findItemStack(final ItemStack stack) {
        for (int i = 0; i < getStackList().size(); i++) {
            if (areItemStacksEqualIgnoreSize(getStackList().get(i), stack)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public ImmutableList<Integer> findItemStacks(final ItemStack stack) {
        final ImmutableList.Builder<Integer> results = ImmutableList.builder();
        for (int i = 0; i < getStackList().size(); i++) {
            if (areItemStacksEqualIgnoreSize(getStackList().get(i), stack)) {
                results.add(i);
            }
        }
        return results.build();
    }

    @Override
    public ImmutableList<Integer> findOreMatchingStacks(final ItemStack stack) {
        final ImmutableList.Builder<Integer> results = ImmutableList.builder();
        if (isEmpty(stack)) {
            return results.build();
        }
        for (int i = 0; i < getStackList().size(); i++) {
            final ItemStack stored = getStackList().get(i);
            if (!isEmpty(stored) && isOre(stored) && oreMatches(stored, stack)) {
                results.add(i);
            }
        }
        return results.build();
    }

    @Override
    public int stackCount() {
        int count = 0;
        for (final ItemStack stack : getStackList()) {
            if (!isEmpty(stack)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public ModGlobals.DankNullTier getTier() {
        return tier;
    }

    @Override
    public int getSelected() {
        return selected;
    }

    @Override
    public void setSelected(final int slot) {
        selected = constrainToRange(slot, -1, stacks.size() - 1);
        onDataChanged();
    }

    @Override
    public void cycleSelected(final boolean forward) {
        final List<Integer> blockSlots = getBlockStacksSlots();
        if (Options.skipNonBlocksOnCycle) {
            final int numBlockSlots = blockSlots.size();
            if (numBlockSlots > 0) {
                if (numBlockSlots == 1) {
                    if (getSelected() != blockSlots.get(0)) {
                        setSelected(blockSlots.get(0));
                    }
                    return;
                }
                final int current = getSelected();
                if (!blockSlots.contains(current)) {
                    setSelected(blockSlots.get(0));
                    return;
                }
                final int min = 0;
                final int max = blockSlots.size() - 1;
                final int currentIndex = getBlockSlotIndex(current);
                if (forward) {
                    if (currentIndex != max) {
                        setSelected(blockSlots.get(currentIndex + 1));
                        return;
                    }
                    setSelected(blockSlots.get(0));
                } else {
                    if (currentIndex != min) {
                        setSelected(blockSlots.get(currentIndex - 1));
                        return;
                    }
                    setSelected(blockSlots.get(max));
                }
                return;
            }
        }
        // Cycle over the slots that actually hold something - occupied slots are not contiguous once a middle
        // slot has been emptied, so stepping the raw index would land the selection on empty slots.
        final List<Integer> filled = getNonEmptySlots();
        if (filled.isEmpty()) {
            return;
        }
        final int current = getSelected();
        final int currentIndex = filled.indexOf(current);
        final int target;
        if (currentIndex < 0) {
            target = filled.get(0);
        } else if (forward) {
            target = filled.get((currentIndex + 1) % filled.size());
        } else {
            target = filled.get((currentIndex - 1 + filled.size()) % filled.size());
        }
        if (current != target) {
            setSelected(target);
        }
    }

    private List<Integer> getNonEmptySlots() {
        final List<Integer> slots = Lists.newArrayList();
        for (int i = 0; i < getSlots(); i++) {
            if (!isEmpty(getFullStackInSlot(i))) {
                slots.add(i);
            }
        }
        return slots;
    }

    private int getBlockSlotIndex(final int slot) {
        final List<Integer> blockSlots = getBlockStacksSlots();
        for (int i = 0; i < blockSlots.size(); i++) {
            if (blockSlots.get(i) == slot) {
                return i;
            }
        }
        return -1;
    }

    private List<Integer> getBlockStacksSlots() {
        final List<Integer> blockStackSlots = Lists.newArrayList();
        for (int i = 0; i < getSlots(); i++) {
            final ItemStack fullStack = getFullStackInSlot(i);
            if (!isEmpty(fullStack) && fullStack.getItem() instanceof ItemBlock) {
                blockStackSlots.add(i);
            }
        }
        Collections.sort(blockStackSlots);
        return blockStackSlots;
    }

    @Override
    public boolean isLocked() {
        return isLocked;
    }

    @Override
    public void setLocked(final boolean lock) {
        isLocked = lock;
        onDataChanged();
    }

    @Override
    public boolean isLockingSupported() {
        return tier.isCreative();
    }

    @Override
    public void setOre(ItemStack stack, final boolean ore) {
        if (isEmpty(stack)) {
            return;
        }
        for (final ItemStack currentStack : oreStacks.keySet()) {
            if (areItemStacksEqualIgnoreSize(currentStack, stack)) {
                oreStacks.put(currentStack, ore);
                onDataChanged();
                return;
            }
        }
        stack = copyWithSize(stack, 1);
        oreStacks.put(stack, ore);
        onDataChanged();
    }

    @Override
    public boolean isOre(final ItemStack stack) {
        if (isEmpty(stack)) {
            return false;
        }
        for (final ItemStack currentStack : oreStacks.keySet()) {
            if (areItemStacksEqualIgnoreSize(currentStack, stack)) {
                return oreStacks.get(currentStack);
            }
        }
        return false;
    }

    @Override
    public boolean isOreSupported(final ItemStack stack) {
        return !isEmpty(stack) && OreDictionary.getOreIDs(stack).length > 0;
    }

    @Override
    public Map<ItemStack, Boolean> getOres() {
        return oreStacks;
    }

    @Override
    public void setExtractionMode(ItemStack stack, final ItemExtractionMode mode) {
        if (isEmpty(stack)) {
            return;
        }
        for (final ItemStack currentStack : extractionStacks.keySet()) {
            if (areItemStacksEqualIgnoreSize(currentStack, stack)) {
                extractionStacks.put(currentStack, mode);
                onDataChanged();
                return;
            }
        }
        stack = copyWithSize(stack, 1);
        extractionStacks.put(stack, mode);
        onDataChanged();
    }

    @Override
    public void cycleExtractionMode(final ItemStack stack, final boolean forward) {
        ItemExtractionMode current = getExtractionMode(stack);
        final ItemExtractionMode[] values = ItemExtractionMode.VALUES;
        if (forward) {
            current = current.ordinal() == values.length - 1 ? values[0] : values[current.ordinal() + 1];
        } else {
            current = current.ordinal() == 0 ? values[values.length - 1] : values[current.ordinal() - 1];
        }
        setExtractionMode(stack, current);
    }

    @Override
    public ItemExtractionMode getExtractionMode(final ItemStack stack) {
        for (final ItemStack currentStack : extractionStacks.keySet()) {
            if (areItemStacksEqualIgnoreSize(currentStack, stack)) {
                return extractionStacks.get(currentStack);
            }
        }
        return ItemExtractionMode.KEEP_1;
    }

    @Override
    public Map<ItemStack, ItemExtractionMode> getExtractionModes() {
        return extractionStacks;
    }

    @Override
    public void setPlacementMode(ItemStack stack, final ItemPlacementMode mode) {
        if (isEmpty(stack)) {
            return;
        }
        for (final ItemStack currentStack : placementStacks.keySet()) {
            if (areItemStacksEqualIgnoreSize(currentStack, stack)) {
                placementStacks.put(currentStack, mode);
                onDataChanged();
                return;
            }
        }
        stack = copyWithSize(stack, 1);
        placementStacks.put(stack, mode);
        onDataChanged();
    }

    @Override
    public void cyclePlacementMode(final ItemStack stack, final boolean forward) {
        ItemPlacementMode current = getPlacementMode(stack);
        final ItemPlacementMode[] values = ItemPlacementMode.VALUES;
        if (forward) {
            current = current.ordinal() == values.length - 1 ? values[0] : values[current.ordinal() + 1];
        } else {
            current = current.ordinal() == 0 ? values[values.length - 1] : values[current.ordinal() - 1];
        }
        setPlacementMode(stack, current);
    }

    @Override
    public ItemPlacementMode getPlacementMode(final ItemStack stack) {
        for (final ItemStack currentStack : placementStacks.keySet()) {
            if (areItemStacksEqualIgnoreSize(currentStack, stack)) {
                return placementStacks.get(currentStack);
            }
        }
        return ItemPlacementMode.KEEP_1;
    }

    @Override
    public Map<ItemStack, ItemPlacementMode> getPlacementMode() {
        return placementStacks;
    }

    @Override
    public int getSlots() {
        return getStackList().size();
    }

    @Override
    public List<ItemStack> getStackList() {
        return stacks;
    }

    @Override
    public int getSlotLimit(final int slot) {
        return tier.getMaxStackSize();
    }

    @Override
    public boolean isItemValid(final int slot, final ItemStack stack) {
        if (isEmpty(stack) || stack.getItem() instanceof ItemDankNull) {
            return false;
        }
        // A creative /dank/null makes anything inside it an infinite source, so servers can restrict what may go
        // in via the CreativeWhitelist / CreativeBlacklist options. Only that tier is filtered.
        if (tier.isCreative() && !ModConfig.isAllowedInCreativeDankNull(stack)) {
            return false;
        }
        final ItemStack existing = getFullStackInSlot(slot);
        if (isEmpty(existing)) {
            // A locked /dank/null's set of stored item types is frozen: nothing new may claim an empty slot.
            return !isLocked();
        }
        return areItemStacksEqualIgnoreSize(existing, stack);
    }

    protected void onDataChanged() {}
}
