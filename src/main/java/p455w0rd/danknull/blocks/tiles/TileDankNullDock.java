package p455w0rd.danknull.blocks.tiles;

import static p455w0rd.danknull.util.DankNullStackUtils.areItemStacksEqualIgnoreSize;
import static p455w0rd.danknull.util.DankNullStackUtils.copyWithSize;
import static p455w0rd.danknull.util.DankNullStackUtils.isEmpty;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants;

import p455w0rd.danknull.api.IDankNullHandler;
import p455w0rd.danknull.init.ModConfig;
import p455w0rd.danknull.init.ModGlobals;
import p455w0rd.danknull.init.ModGlobals.NBT;
import p455w0rd.danknull.inventory.StackDankNullHandler;
import p455w0rd.danknull.util.DankNullUtils;

/**
 * Tile entity behind the /dank/null Docking Station.
 *
 * <p>
 * Two things changed in the backport:
 * </p>
 *
 * <ul>
 * <li><b>No capabilities.</b> 1.12 exposed the docked /dank/null through {@code CapabilityDankNull} and Forge's
 * {@code ITEM_HANDLER}. Here the handler is reached with {@link #getHandler()} (for the container/GUI) and the
 * automation-facing half is re-expressed as a vanilla {@link ISidedInventory} so hoppers and pipes can still feed a
 * docked /dank/null.</li>
 * <li><b>No {@code DankNullCap} tag.</b> Upstream re-serialised the handler into a {@code DankNullCap} compound on
 * the docked stack every time the tile was written. This backport instead binds a {@link StackDankNullHandler} to
 * the docked stack, which is exactly what a /dank/null carried in an inventory uses: it writes itself back into the
 * stack's own NBT on every mutation. The tile therefore only has to persist the docked ItemStack itself, the NBT
 * layout matches a non-docked /dank/null, and a dock broken mid-use keeps its contents with no extra work.</li>
 * </ul>
 *
 * @author p455w0rd
 */
public class TileDankNullDock extends TileEntity implements ISidedInventory {

    /** {@code null} when nothing is docked - 1.7.10 has no {@code ItemStack.EMPTY}. */
    private ItemStack dankNull = null;
    private IDankNullHandler dankNullHandler = null;

    /** Suppresses the block update storm while the handler is being deserialised. */
    private boolean loading = false;

    /** Cached {@link #getAccessibleSlotsFromSide(int)} result; invalidated whenever the docked stack changes. */
    private int[] accessibleSlots = new int[0];

    // ------------------------------------------------------------------
    // docked /dank/null
    // ------------------------------------------------------------------

    public ItemStack getDankNull() {
        return dankNull;
    }

    public boolean hasDankNull() {
        return !isEmpty(dankNull) && dankNullHandler != null;
    }

    /**
     * Replaces {@code getCapability(CapabilityDankNull.DANK_NULL_CAPABILITY, null)}.
     *
     * @return the docked /dank/null's handler, or {@code null} when the dock is empty
     */
    public IDankNullHandler getHandler() {
        return dankNullHandler;
    }

    public void setDankNull(final ItemStack stack) {
        if (isEmpty(stack)) {
            removeDankNull();
            return;
        }
        dankNull = stack.copy();
        dankNull.stackSize = 1;
        if (!dankNull.hasTagCompound()) {
            dankNull.setTagCompound(new NBTTagCompound());
        }
        final StackDankNullHandler handler = new StackDankNullHandler(DankNullUtils.getTier(dankNull), dankNull) {

            @Override
            public ItemStack getStackInSlot(final int slot) {
                validateSlot(slot);
                return getExtractableStackInSlot(slot);
            }

            /**
             * Hooked after the write-back rather than on onDataChanged, so a batched bulk insert marks the tile
             * dirty once at the end instead of re-sending the description packet per item.
             */
            @Override
            protected void afterSave() {
                TileDankNullDock.this.markDirty();
            }
        };
        loading = true;
        try {
            handler.load();
        } finally {
            loading = false;
        }
        dankNullHandler = handler;
        rebuildAccessibleSlots();
        markDirty();
    }

    public void removeDankNull() {
        if (isEmpty(dankNull) && dankNullHandler == null) {
            return;
        }
        dankNull = null;
        dankNullHandler = null;
        rebuildAccessibleSlots();
        markDirty();
    }

    private void rebuildAccessibleSlots() {
        if (dankNullHandler == null) {
            accessibleSlots = new int[0];
            return;
        }
        // The virtual input slot goes first so automation reaches it before it starts probing the real slots.
        final int size = dankNullHandler.getSlots() + 1;
        accessibleSlots = new int[size];
        accessibleSlots[0] = dankNullHandler.getSlots();
        for (int i = 0; i < size - 1; i++) {
            accessibleSlots[i + 1] = i;
        }
    }

    // ------------------------------------------------------------------
    // sync
    // ------------------------------------------------------------------

    @Override
    public void markDirty() {
        super.markDirty();
        if (loading || worldObj == null) {
            return;
        }
        // Server side this queues the description packet below for everyone tracking the chunk, which is what
        // upstream's PacketSetDankNullInDock / PacketEmptyDock did by hand; client side it just re-renders.
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public Packet getDescriptionPacket() {
        final NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(final NetworkManager net, final S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
        if (worldObj != null) {
            worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
        }
    }

    @Override
    public void validate() {
        super.validate();
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    public void readFromNBT(final NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey(NBT.DOCKEDSTACK, Constants.NBT.TAG_COMPOUND)) {
            setDankNull(ItemStack.loadItemStackFromNBT(nbt.getCompoundTag(NBT.DOCKEDSTACK)));
        } else {
            // Needed on the client: an emptied dock arrives as a description packet with no docked stack.
            removeDankNull();
        }
    }

    @Override
    public void writeToNBT(final NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (isEmpty(dankNull)) {
            return;
        }
        // Flush anything the handler has not written back yet, then persist the stack (which now carries the
        // whole inventory in its own tag compound).
        DankNullUtils.save(dankNullHandler);
        compound.setTag(NBT.DOCKEDSTACK, dankNull.writeToNBT(new NBTTagCompound()));
    }

    // ------------------------------------------------------------------
    // ISidedInventory
    //
    // Stands in for the IItemHandler upstream exposed as a capability. A /dank/null slot holds far more than
    // getInventoryStackLimit() items, and the vanilla hopper refuses to top up a slot that is already at that
    // limit, so insertion is not done through the real slots at all: slot index getSlots() is a virtual, always
    // empty input slot that funnels everything through IDankNullHandler.insertItem (which is what applies the ore
    // dictionary conversion and the per-tier stack cap). The real slots stay read-only to automation and are only
    // there so items can be pulled back out.
    // ------------------------------------------------------------------

    private int getInputSlot() {
        return dankNullHandler == null ? -1 : dankNullHandler.getSlots();
    }

    private boolean isRealSlot(final int slot) {
        return dankNullHandler != null && slot >= 0 && slot < dankNullHandler.getSlots();
    }

    /**
     * Feeds a stack into the docked /dank/null, preferring a slot that already holds the same item so the
     * inventory does not end up with the same thing in two slots.
     *
     * @return whatever would not fit, or {@code null} if everything was accepted
     */
    private ItemStack insertIntoDankNull(final ItemStack stack, final boolean simulate) {
        if (dankNullHandler == null || isEmpty(stack)) {
            return stack;
        }
        ItemStack remainder = stack;
        // Batched: without this each insert re-serialises the docked /dank/null and marks the tile dirty, so a
        // hopper feeding the dock would re-send the description packet once per slot visited.
        DankNullUtils.beginBatch(dankNullHandler);
        try {
            final int existing = dankNullHandler.findItemStack(stack);
            if (existing > -1) {
                remainder = dankNullHandler.insertItem(existing, remainder, simulate);
            }
            for (int slot = 0; slot < dankNullHandler.getSlots() && !isEmpty(remainder); slot++) {
                if (slot == existing) {
                    continue;
                }
                remainder = dankNullHandler.insertItem(slot, remainder, simulate);
            }
        } finally {
            DankNullUtils.endBatch(dankNullHandler);
        }
        return remainder;
    }

    @Override
    public int getSizeInventory() {
        return dankNullHandler == null ? 0 : dankNullHandler.getSlots() + 1;
    }

    @Override
    public ItemStack getStackInSlot(final int slot) {
        if (!isRealSlot(slot)) {
            return null;
        }
        // Honours the per-stack extraction mode, exactly like the anonymous handler upstream installed on the dock.
        return dankNullHandler.getStackInSlot(slot);
    }

    @Override
    public ItemStack decrStackSize(final int slot, final int amount) {
        if (!isRealSlot(slot) || amount < 1) {
            return null;
        }
        return dankNullHandler.extractItem(slot, amount, false);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(final int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(final int slot, final ItemStack stack) {
        if (dankNullHandler == null) {
            return;
        }
        if (slot == getInputSlot()) {
            if (isEmpty(stack) || !ModConfig.Options.allowDockInserting) {
                return;
            }
            final ItemStack remainder = insertIntoDankNull(stack, false);
            // IInventory has no way to hand a remainder back to the caller, which believes the whole stack was
            // accepted. canInsertItem() only says yes when everything fits, so this is a belt-and-braces path -
            // dropping it is still better than voiding it.
            if (!isEmpty(remainder) && worldObj != null && !worldObj.isRemote) {
                worldObj.spawnEntityInWorld(
                    new EntityItem(worldObj, xCoord + 0.5D, yCoord + 1.0D, zCoord + 0.5D, remainder));
            }
            return;
        }
        if (isRealSlot(slot)) {
            // Automation mods write back what getStackInSlot() handed them minus what they took - but that was an
            // extraction-mode-limited COPY of the real (possibly far larger) stack. Writing it through verbatim
            // would void the difference, so the write is translated into the extraction or re-insertion it
            // actually means. (The vanilla hopper's failed-transfer rollback also lands here, writing the
            // pre-extraction copy back - the diff below restores it correctly.)
            final ItemStack visible = dankNullHandler.getStackInSlot(slot);
            if (isEmpty(visible)) {
                return;
            }
            final boolean clearing = isEmpty(stack);
            if (!clearing && !areItemStacksEqualIgnoreSize(visible, stack)) {
                return;
            }
            final int diff = visible.stackSize - (clearing ? 0 : stack.stackSize);
            DankNullUtils.beginBatch(dankNullHandler);
            try {
                if (diff < 0) {
                    dankNullHandler.insertItem(slot, copyWithSize(stack, -diff), false);
                } else {
                    int remaining = diff;
                    while (remaining > 0) {
                        // extractItemIngoreExtractionMode caps each pull at the item's max stack size.
                        final ItemStack removed = dankNullHandler
                            .extractItemIngoreExtractionMode(slot, remaining, false);
                        if (isEmpty(removed)) {
                            break;
                        }
                        remaining -= removed.stackSize;
                    }
                }
            } finally {
                DankNullUtils.endBatch(dankNullHandler);
            }
        }
    }

    @Override
    public String getInventoryName() {
        return "container." + ModGlobals.MODID + ".dock";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(final EntityPlayer player) {
        if (worldObj == null || worldObj.getTileEntity(xCoord, yCoord, zCoord) != this) {
            return false;
        }
        return player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 64.0D;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(final int slot, final ItemStack stack) {
        if (dankNullHandler == null || isEmpty(stack) || slot != getInputSlot()) {
            return false;
        }
        if (!ModConfig.Options.allowDockInserting) {
            return false;
        }
        // Ignoring stack size, per the IInventory contract: can any slot take this item at all?
        for (int i = 0; i < dankNullHandler.getSlots(); i++) {
            if (dankNullHandler.isItemValid(i, stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(final int side) {
        return accessibleSlots;
    }

    @Override
    public boolean canInsertItem(final int slot, final ItemStack stack, final int side) {
        if (!isItemValidForSlot(slot, stack)) {
            return false;
        }
        // Only accept when the whole stack fits, since setInventorySlotContents() cannot return a remainder.
        return isEmpty(insertIntoDankNull(stack, true));
    }

    @Override
    public boolean canExtractItem(final int slot, final ItemStack stack, final int side) {
        return isRealSlot(slot);
    }
}
