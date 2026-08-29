package p455w0rd.danknull.container;

import static p455w0rd.danknull.util.DankNullStackUtils.getCount;
import static p455w0rd.danknull.util.DankNullStackUtils.isEmpty;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S2FPacketSetSlot;

import p455w0rd.danknull.api.IDankNullHandler;
import p455w0rd.danknull.init.ModNetworking;
import p455w0rd.danknull.inventory.slot.SlotDankNull;
import p455w0rd.danknull.inventory.slot.SlotDankNullDock;
import p455w0rd.danknull.inventory.slot.SlotHotbar;
import p455w0rd.danknull.network.PacketUpdateSlot;
import p455w0rd.danknull.util.DankNullUtils;

/**
 * @author BrockWS
 */
public abstract class ContainerDankNull extends Container {

    /** The 9 hotbar slots plus the 27 main inventory slots that precede the /dank/null's own slots. */
    public static final int PLAYER_SLOT_COUNT = 36;

    protected final EntityPlayer player;

    /** Player-inventory index (0-35) holding the open /dank/null, or -1; that slot is locked while the GUI is open. */
    protected int lockedSlot = -1;

    public ContainerDankNull(final EntityPlayer player) {
        this.player = player;
    }

    protected void init() {
        final InventoryPlayer playerInv = player.inventory;
        final IDankNullHandler handler = getHandler();
        final int numRows = handler.getTier()
            .getNumRows();
        for (int i = 0; i < playerInv.getSizeInventory(); i++) {
            final ItemStack currStack = playerInv.getStackInSlot(i);
            if (!isEmpty(currStack) && currStack == getDankNullStack()) {
                lockedSlot = i;
            }
        }
        for (int i = 0; i < 9; i++) {
            addSlotToContainer(
                new SlotHotbar(playerInv, i, i * 20 + 9 + i, 90 + numRows - 1 + numRows * 20 + 6, lockedSlot == i));
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                final int invIndex = j + i * 9 + 9;
                // SlotHotbar is just "player slot with an optional lock": a /dank/null opened from the main
                // inventory (via the keybind) must be as immovable as one opened from the hotbar.
                addSlotToContainer(
                    new SlotHotbar(
                        playerInv,
                        invIndex,
                        j * 20 + 9 + j,
                        149 + numRows - 1 + i - (6 - numRows) * 20 + i * 20,
                        lockedSlot == invIndex));
            }
        }
        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < 9; j++) {
                addSlotToContainer(createDankNullSlot(i, j));
            }
        }
    }

    public abstract IDankNullHandler getHandler();

    public abstract ItemStack getDankNullStack();

    protected boolean isDock() {
        return false;
    }

    private SlotDankNull createDankNullSlot(final int i, final int j) {
        return isDock() ? new SlotDankNullDock(this::getHandler, j + i * 9, j * 20 + 9 + j, 19 + i + i * 20)
            : new SlotDankNull(this::getHandler, j + i * 9, j * 20 + 9 + j, 19 + i + i * 20);
    }

    @Override
    public boolean canInteractWith(final EntityPlayer player) {
        return getHandler() != null;
    }

    @Override
    public Slot getSlot(final int slotId) {
        if (slotId < inventorySlots.size() && slotId >= 0) {
            return inventorySlots.get(slotId);
        }
        return null;
    }

    /**
     * 1.7.10 has no {@code ClickType} enum - {@code mode} is the raw int vanilla uses: 0 = pickup, 1 = quick move,
     * 2 = hotbar swap, 3 = clone, 4 = throw, 5 = drag, 6 = pickup all.
     */
    @Override
    public ItemStack slotClick(final int slotId, final int clickedButton, final int mode, final EntityPlayer player) {
        // Vanilla's number-key swap (mode 2) writes into the TARGET hotbar slot directly and only consults
        // canTakeStack on the clicked slot, so it would move the open /dank/null out of its locked slot.
        if (mode == 2 && clickedButton == lockedSlot) {
            return null;
        }
        final Slot slot = getSlot(slotId);
        if (slot == null || slotId < PLAYER_SLOT_COUNT && mode != 1 || mode == 3) {
            return super.slotClick(slotId, clickedButton, mode, player);
        }
        if (mode == 1) {
            return transferStackInSlot(player, slotId);
        }
        final InventoryPlayer inventoryPlayer = player.inventory;
        final ItemStack heldStack = inventoryPlayer.getItemStack();
        if (slot instanceof SlotDankNull && mode == 0) {
            final ItemStack slotStack = slot.getStack();
            if (DankNullUtils.isDankNull(slotStack)) {
                return null;
            }
            if (!isEmpty(heldStack)) { // Want to insert held stack into DankNull
                final ItemStack toAdd = heldStack.copy();
                if (clickedButton == 1) {
                    toAdd.stackSize = 1;
                }
                final ItemStack leftover = addStack(toAdd);
                if (clickedButton == 0) {
                    inventoryPlayer.setItemStack(isEmpty(leftover) ? null : leftover);
                } else if (clickedButton == 1 && isEmpty(leftover)) { // Right clicked and one item fit
                    heldStack.stackSize--;
                    inventoryPlayer.setItemStack(heldStack.stackSize <= 0 ? null : heldStack);
                }
                syncHeldItem(player);
            } else if (!isEmpty(slotStack)) { // Want to take stack out of DankNull
                int amount = Math.min(slotStack.stackSize, slotStack.getMaxStackSize());
                if (clickedButton == 1) {
                    // Round up like vanilla, so a single item can still be picked up with a right click.
                    amount = (amount + 1) / 2;
                }
                final ItemStack newStack = slot.decrStackSize(amount);
                inventoryPlayer.setItemStack(isEmpty(newStack) ? null : newStack);
                syncHeldItem(player);
            }
        }
        return null;
    }

    /**
     * 1.12's {@code EntityPlayerMP#updateHeldItem()} does not exist in 1.7.10; this is the packet it sent.
     */
    private static void syncHeldItem(final EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            final EntityPlayerMP playerMP = (EntityPlayerMP) player;
            playerMP.playerNetServerHandler.sendPacket(new S2FPacketSetSlot(-1, -1, playerMP.inventory.getItemStack()));
        }
    }

    @Override
    public ItemStack transferStackInSlot(final EntityPlayer player, final int index) {
        final Slot clickSlot = getSlot(index);
        if (clickSlot == null || !clickSlot.getHasStack()) {
            return null;
        }
        if (!(clickSlot instanceof SlotDankNull)) { // Shift click from Player Inventory
            final ItemStack leftover = addStack(clickSlot.getStack());
            clickSlot.putStack(isEmpty(leftover) ? null : leftover);
            player.inventory.markDirty();
            return null;
        }
        final IDankNullHandler handler = getHandler();
        if (handler == null) {
            // The /dank/null can leave its slot in the same tick as this click - canInteractWith only closes the
            // container on the NEXT tick, and an exception here would take down the server's packet handling.
            return null;
        }
        final int slotIndex = clickSlot.getSlotIndex();
        final ItemStack fullStack = handler.getFullStackInSlot(slotIndex);
        if (isEmpty(fullStack)) {
            return null;
        }
        final ItemStack slotStack = handler.extractItem(slotIndex, fullStack.getMaxStackSize(), true);
        if (!isEmpty(slotStack) && !handler.getTier()
            .isCreative()) {
            final int notAdded = insertIntoPlayerInventory(player, slotStack);
            if (notAdded < slotStack.stackSize) {
                handler.extractItemIngoreExtractionMode(slotIndex, slotStack.stackSize - notAdded, false);
                clickSlot.onSlotChanged();
            }
        }
        return null;
    }

    /**
     * Replacement for {@code ItemHandlerHelper.insertItemStacked(new PlayerMainInvWrapper(inv), stack, false)} -
     * neither the helper nor the wrapper exists in 1.7.10.
     *
     * @return the number of items that could <em>not</em> be inserted
     */
    private static int insertIntoPlayerInventory(final EntityPlayer player, final ItemStack stack) {
        final ItemStack toInsert = stack.copy();
        player.inventory.addItemStackToInventory(toInsert);
        return getCount(toInsert);
    }

    /**
     * Pushes a stack into whichever slots will take it, preferring slots already holding the item.
     *
     * <p>
     * The whole scan runs inside one batch: each mutation would otherwise re-serialise the entire inventory to the
     * /dank/null's NBT, so a shift-click into a large tier cost one full serialisation per slot visited. The
     * selection is likewise updated once at the end rather than per slot.
     * </p>
     */
    private ItemStack addStack(final ItemStack stack) {
        ItemStack leftover = stack.copy();
        final IDankNullHandler handler = getHandler();
        if (handler == null) {
            return leftover;
        }
        DankNullUtils.beginBatch(handler);
        try {
            for (int i = 0; i < handler.getSlots(); i++) {
                // Occupied slots first (insertItem validates and ore-converts internally): an empty slot earlier
                // in the list must not split the item across two slots when a stack of it already exists later.
                if (!isEmpty(handler.getFullStackInSlot(i))) {
                    leftover = handler.insertItem(i, leftover, false);
                }
                if (isEmpty(leftover)) {
                    handler.updateSelectedSlot();
                    return null;
                }
            }
            for (int i = 0; i < handler.getSlots(); i++) {
                if (isEmpty(handler.getFullStackInSlot(i)) && handler.isItemValid(i, leftover)) {
                    handler.setStackInSlot(i, leftover);
                    handler.updateSelectedSlot();
                    return null;
                }
            }
        } finally {
            DankNullUtils.endBatch(handler);
        }
        return leftover;
    }

    /**
     * Vanilla's double-click "collect all" (mode 6) consults this per slot. It must never gather from the
     * /dank/null's slots: vanilla adds the requested amount to the cursor regardless of how many items
     * {@code decrStackSize} actually removed, and a /dank/null extraction is capped by the stack's extraction
     * mode - the difference would be duplicated out of nothing.
     */
    @Override
    public boolean func_94530_a(final ItemStack stack, final Slot slot) {
        return !(slot instanceof SlotDankNull) && super.func_94530_a(stack, slot);
    }

    /**
     * Needed due to item stacks having a network limitation of size count byte.
     *
     * <p>
     * 1.7.10 has no {@code ItemStack.areItemStacksEqualUsingNBTShareTag}, so only the plain equality check
     * survives; {@code PacketUpdateSlot} is what actually carries the oversized count to the client.
     * </p>
     */
    @Override
    public void detectAndSendChanges() {
        for (int i = 0; i < inventorySlots.size(); ++i) {
            final ItemStack slotStack = inventorySlots.get(i)
                .getStack();
            ItemStack clientStack = inventoryItemStacks.get(i);
            if (!ItemStack.areItemStacksEqual(clientStack, slotStack)) {
                clientStack = isEmpty(slotStack) ? null : slotStack.copy();
                inventoryItemStacks.set(i, clientStack);
                for (final ICrafting listener : crafters) {
                    if (listener instanceof EntityPlayerMP) {
                        // TODO(net): PacketUpdateSlot(int slot, ItemStack stack) - owned by the networking agent.
                        ModNetworking.getInstance()
                            .sendTo(new PacketUpdateSlot(i, clientStack), (EntityPlayerMP) listener);
                    }
                }
            }
        }
    }
}
