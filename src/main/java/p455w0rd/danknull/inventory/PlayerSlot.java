package p455w0rd.danknull.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import io.netty.buffer.ByteBuf;

/**
 * Created by brandon3055 on 7/06/2016.
 * Used to store a reference to a specific slot in a players inventory.
 * The slot field corresponds to the index of the item within the sub inventory for the given category.
 *
 * <p>
 * 1.7.10 has no off-hand. {@link EnumInvCategory#OFF_HAND} is retained purely so the on-the-wire category
 * indices stay identical to 1.12's, but nothing in this backport ever produces one and
 * {@link #getStackInSlot(EntityPlayer)} returns {@code null} for it.
 * </p>
 */
public class PlayerSlot {

    private final int slot;
    private final EnumInvCategory category;

    public PlayerSlot(final int slot, final EnumInvCategory category) {
        this.slot = slot;
        this.category = category;
    }

    public static PlayerSlot fromBuff(final ByteBuf buf) {
        final EnumInvCategory category = EnumInvCategory.fromIndex(buf.readByte());
        final int slot = buf.readByte();
        return new PlayerSlot(slot, category);
    }

    /**
     * 1.7.10 has a single hand, so the {@code EnumHand} argument the 1.12 version took is gone.
     */
    public static PlayerSlot getHand(final EntityPlayer player) {
        return new PlayerSlot(player.inventory.currentItem, EnumInvCategory.MAIN);
    }

    public void toBuff(final ByteBuf buf) {
        buf.writeByte(category.getIndex());
        buf.writeByte(slot);
    }

    public int getSlotIndex() {
        return slot;
    }

    public int getCatIndex() {
        return category.getIndex();
    }

    public EnumInvCategory getCategory() {
        return category;
    }

    @Override
    public String toString() {
        return category.getIndex() + ":" + slot;
    }

    public ItemStack getStackInSlot(final EntityPlayer player) {
        if (player == null || player.inventory == null) {
            return null;
        }
        if (category == EnumInvCategory.ARMOR) {
            if (slot < 0 || slot >= player.inventory.armorInventory.length) {
                return null;
            }
            return player.inventory.armorInventory[slot];
        }
        if (category == EnumInvCategory.MAIN) {
            if (slot < 0 || slot >= player.inventory.mainInventory.length) {
                return null;
            }
            return player.inventory.mainInventory[slot];
        }
        return null;
    }

    public enum EnumInvCategory {

        MAIN(0),
        ARMOR(1),
        /** Unused in 1.7.10 - kept so the wire format matches the 1.12 original. */
        OFF_HAND(2);

        private static final EnumInvCategory[] INDEX_MAP = new EnumInvCategory[3];

        static {
            INDEX_MAP[0] = MAIN;
            INDEX_MAP[1] = ARMOR;
            INDEX_MAP[2] = OFF_HAND;
        }

        private final int index;

        EnumInvCategory(final int index) {
            this.index = index;
        }

        public static EnumInvCategory fromIndex(final int index) {
            if (index > 2 || index < 0) {
                return INDEX_MAP[0];
            }
            return INDEX_MAP[index];
        }

        public int getIndex() {
            return index;
        }
    }
}
