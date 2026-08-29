package p455w0rd.danknull.inventory;

import static p455w0rd.danknull.util.DankNullStackUtils.areItemStacksEqualIgnoreSize;
import static p455w0rd.danknull.util.DankNullStackUtils.copyWithSize;
import static p455w0rd.danknull.util.DankNullStackUtils.isEmpty;

import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import p455w0rd.danknull.api.DankNullItemModes.ItemExtractionMode;
import p455w0rd.danknull.api.DankNullItemModes.ItemPlacementMode;
import p455w0rd.danknull.api.IDankNullHandler;
import p455w0rd.danknull.init.ModGlobals;

/**
 * Reads and writes an {@link IDankNullHandler} to NBT.
 *
 * <p>
 * In 1.12 this lived in {@code CapabilityDankNull} as the {@code Capability.IStorage} implementation. 1.7.10 has no
 * capability system, so the same key names and value types are produced here and stored directly on the
 * /dank/null ItemStack's tag compound.
 * </p>
 *
 * <p>
 * Note this is <b>not</b> cross-version compatible with a 1.12 /dank/null, and cannot be: 1.12 nested the whole
 * payload under a {@code DankNullCap} tag, and more fundamentally 1.7.10 reads a stack's {@code id} as a numeric
 * short where 1.12 wrote a {@code "modid:name"} string, so every stored stack would deserialise to null anyway.
 * </p>
 *
 * <p>
 * Two 1.7.10 API differences are handled throughout: {@code stack.serializeNBT()} becomes
 * {@code writeToNBT(new NBTTagCompound())}, and {@code new ItemStack(nbt)} becomes
 * {@code ItemStack.loadItemStackFromNBT(nbt)}.
 * </p>
 */
public class DankNullSerializer {

    private DankNullSerializer() {}

    public static NBTTagCompound write(final IDankNullHandler instance) {
        final NBTTagCompound tag = new NBTTagCompound();

        final NBTTagList items = new NBTTagList();
        for (int i = 0; i < instance.getSlots(); i++) {
            final ItemStack originalStack = instance.getFullStackInSlot(i);
            if (isEmpty(originalStack)) {
                continue;
            }
            final NBTTagCompound item = new NBTTagCompound();
            // Vanilla writes Count as a byte, so the real (potentially huge) size is stored separately.
            final ItemStack single = copyWithSize(originalStack, 1);
            single.writeToNBT(item);
            item.setInteger("Slot", i);
            item.setInteger("Count", originalStack.stackSize);
            items.appendTag(item);
        }

        final NBTTagList ores = new NBTTagList();
        for (final Map.Entry<ItemStack, Boolean> entry : instance.getOres()
            .entrySet()) {
            final NBTTagCompound oreTag = new NBTTagCompound();
            oreTag.setBoolean(ModGlobals.NBT.OREDICT, entry.getValue());
            oreTag.setTag(
                ModGlobals.NBT.STACK,
                entry.getKey()
                    .writeToNBT(new NBTTagCompound()));
            ores.appendTag(oreTag);
        }

        final NBTTagList extractionModes = new NBTTagList();
        for (final Map.Entry<ItemStack, ItemExtractionMode> entry : instance.getExtractionModes()
            .entrySet()) {
            final NBTTagCompound extractionTag = new NBTTagCompound();
            extractionTag.setInteger(
                ModGlobals.NBT.MODE,
                entry.getValue()
                    .ordinal());
            extractionTag.setTag(
                ModGlobals.NBT.STACK,
                entry.getKey()
                    .writeToNBT(new NBTTagCompound()));
            extractionModes.appendTag(extractionTag);
        }

        final NBTTagList placementModes = new NBTTagList();
        for (final Map.Entry<ItemStack, ItemPlacementMode> entry : instance.getPlacementMode()
            .entrySet()) {
            final NBTTagCompound placementTag = new NBTTagCompound();
            placementTag.setInteger(
                ModGlobals.NBT.MODE,
                entry.getValue()
                    .ordinal());
            placementTag.setTag(
                ModGlobals.NBT.STACK,
                entry.getKey()
                    .writeToNBT(new NBTTagCompound()));
            placementModes.appendTag(placementTag);
        }

        if (items.tagCount() > 0) {
            tag.setTag(ModGlobals.NBT.DANKNULL_INVENTORY, items);
        }
        if (ores.tagCount() > 0) {
            tag.setTag(ModGlobals.NBT.OREDICT_MODES, ores);
        }
        if (extractionModes.tagCount() > 0) {
            tag.setTag(ModGlobals.NBT.EXTRACTION_MODES, extractionModes);
        }
        if (placementModes.tagCount() > 0) {
            tag.setTag(ModGlobals.NBT.PLACEMENT_MODES, placementModes);
        }
        if (instance.getSelected() > -1) {
            tag.setInteger(ModGlobals.NBT.SELECTEDINDEX, instance.getSelected());
        }
        if (instance.isLocked()) {
            tag.setBoolean(ModGlobals.NBT.LOCKED, instance.isLocked());
        }
        return tag;
    }

    public static void read(final IDankNullHandler instance, final NBTTagCompound tag) {
        if (tag == null || tag.hasNoTags()) {
            return;
        }

        if (tag.hasKey(ModGlobals.NBT.DANKNULL_INVENTORY)) {
            final NBTTagList items = tag.getTagList(ModGlobals.NBT.DANKNULL_INVENTORY, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < items.tagCount(); i++) {
                final NBTTagCompound item = items.getCompoundTagAt(i);
                final int slot = item.getInteger("Slot");
                final int count = item.getInteger("Count");
                final ItemStack stack = ItemStack.loadItemStackFromNBT(item);
                if (stack == null || slot < 0 || slot >= instance.getSlots()) {
                    continue;
                }
                stack.stackSize = count;
                instance.getStackList()
                    .set(slot, stack);
            }
        }

        if (tag.hasKey(ModGlobals.NBT.OREDICT_MODES)) {
            final NBTTagList items = tag.getTagList(ModGlobals.NBT.OREDICT_MODES, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < items.tagCount(); i++) {
                final NBTTagCompound item = items.getCompoundTagAt(i);
                final boolean oreDict = item.getBoolean(ModGlobals.NBT.OREDICT);
                final ItemStack stack = ItemStack.loadItemStackFromNBT(item.getCompoundTag(ModGlobals.NBT.STACK));
                if (isEmpty(stack)) {
                    continue;
                }
                final Map<ItemStack, Boolean> oreStacks = instance.getOres();
                boolean foundStack = false;
                for (final ItemStack currentStack : oreStacks.keySet()) {
                    if (areItemStacksEqualIgnoreSize(currentStack, stack)) {
                        oreStacks.put(currentStack, oreDict);
                        foundStack = true;
                        break;
                    }
                }
                if (!foundStack) {
                    oreStacks.put(copyWithSize(stack, 1), oreDict);
                }
            }
        }

        if (tag.hasKey(ModGlobals.NBT.EXTRACTION_MODES)) {
            final NBTTagList items = tag.getTagList(ModGlobals.NBT.EXTRACTION_MODES, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < items.tagCount(); i++) {
                final NBTTagCompound item = items.getCompoundTagAt(i);
                final int ordinal = item.getInteger(ModGlobals.NBT.MODE);
                if (ordinal < 0 || ordinal >= ItemExtractionMode.VALUES.length) {
                    continue;
                }
                final ItemExtractionMode mode = ItemExtractionMode.VALUES[ordinal];
                final ItemStack stack = ItemStack.loadItemStackFromNBT(item.getCompoundTag(ModGlobals.NBT.STACK));
                if (isEmpty(stack)) {
                    continue;
                }
                final Map<ItemStack, ItemExtractionMode> extractionStacks = instance.getExtractionModes();
                boolean foundStack = false;
                for (final ItemStack currentStack : extractionStacks.keySet()) {
                    if (areItemStacksEqualIgnoreSize(currentStack, stack)) {
                        extractionStacks.put(currentStack, mode);
                        foundStack = true;
                        break;
                    }
                }
                // NB: the 1.12 original used `return` here rather than `break`, which silently dropped every
                // remaining mode entry once one matched. Fixed to `break` + continue so all entries are read.
                if (!foundStack) {
                    extractionStacks.put(copyWithSize(stack, 1), mode);
                }
            }
        }

        if (tag.hasKey(ModGlobals.NBT.PLACEMENT_MODES)) {
            final NBTTagList items = tag.getTagList(ModGlobals.NBT.PLACEMENT_MODES, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < items.tagCount(); i++) {
                final NBTTagCompound item = items.getCompoundTagAt(i);
                final int ordinal = item.getInteger(ModGlobals.NBT.MODE);
                if (ordinal < 0 || ordinal >= ItemPlacementMode.VALUES.length) {
                    continue;
                }
                final ItemPlacementMode mode = ItemPlacementMode.VALUES[ordinal];
                final ItemStack stack = ItemStack.loadItemStackFromNBT(item.getCompoundTag(ModGlobals.NBT.STACK));
                if (isEmpty(stack)) {
                    continue;
                }
                final Map<ItemStack, ItemPlacementMode> placementStacks = instance.getPlacementMode();
                boolean foundStack = false;
                for (final ItemStack currentStack : placementStacks.keySet()) {
                    if (areItemStacksEqualIgnoreSize(currentStack, stack)) {
                        placementStacks.put(currentStack, mode);
                        foundStack = true;
                        break;
                    }
                }
                if (!foundStack) {
                    placementStacks.put(copyWithSize(stack, 1), mode);
                }
            }
        }

        if (tag.hasKey(ModGlobals.NBT.SELECTEDINDEX)) {
            instance.setSelected(tag.getInteger(ModGlobals.NBT.SELECTEDINDEX));
        }
        if (tag.hasKey(ModGlobals.NBT.LOCKED)) {
            instance.setLocked(tag.getBoolean(ModGlobals.NBT.LOCKED));
        }
    }
}
