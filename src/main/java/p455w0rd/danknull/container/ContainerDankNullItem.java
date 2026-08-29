package p455w0rd.danknull.container;

import static p455w0rd.danknull.util.DankNullStackUtils.isEmpty;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import p455w0rd.danknull.api.IDankNullHandler;
import p455w0rd.danknull.inventory.PlayerSlot;
import p455w0rd.danknull.util.DankNullUtils;

/**
 * @author p455w0rd
 */
public class ContainerDankNullItem extends ContainerDankNull {

    private final PlayerSlot playerSlot;

    public ContainerDankNullItem(final EntityPlayer player, final PlayerSlot slot) {
        super(player);
        playerSlot = slot;
        init();
    }

    /**
     * Upstream additionally required {@code current == dankNull}, i.e. that the slot still holds the very same
     * ItemStack <i>object</i> the container was opened with. That is unsafe here: 1.7.10 replaces inventory
     * ItemStack instances wholesale on any slot sync, and {@code EntityPlayer#onUpdate} force-closes the container
     * the moment this returns false, so an identity check makes the GUI shut itself the tick after it opens.
     * Checking that the slot still holds a /dank/null is the property actually being guarded.
     */
    @Override
    public boolean canInteractWith(final EntityPlayer player) {
        final ItemStack current = playerSlot.getStackInSlot(player);
        return !isEmpty(current) && DankNullUtils.isDankNull(current) && super.canInteractWith(player);
    }

    /**
     * Resolved live rather than captured at construction.
     *
     * <p>
     * In 1.12 the handler came from a capability on the stack, and upstream's identity check in
     * {@link #canInteractWith} guaranteed the captured instance stayed the live one - if the stack object was ever
     * replaced, the container closed. That check cannot survive on 1.7.10 (see above), so holding the handler would
     * leave it bound to a stack that is no longer in the inventory: edits would be written to a detached object and
     * silently lost when the GUI closes. Resolving through the slot on every access is what
     * {@link ContainerDankNullDock} already does via its tile.
     * </p>
     */
    @Override
    public IDankNullHandler getHandler() {
        return DankNullUtils.getHandler(playerSlot.getStackInSlot(player));
    }

    @Override
    public ItemStack getDankNullStack() {
        return playerSlot.getStackInSlot(player);
    }
}
