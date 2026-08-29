package p455w0rd.danknull.inventory.slot;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;

/**
 * @author p455w0rd
 */
public class SlotHotbar extends Slot {

    private final boolean locked;

    public SlotHotbar(final IInventory inventory, final int index, final int x, final int y, final boolean shouldLock) {
        super(inventory, index, x, y);
        locked = shouldLock;
    }

    @Override
    public boolean canTakeStack(final EntityPlayer player) {
        return !locked;
    }
}
