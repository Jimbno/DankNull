package p455w0rd.danknull.init;

import static p455w0rd.danknull.inventory.PlayerSlot.EnumInvCategory.MAIN;
import static p455w0rd.danknull.util.DankNullStackUtils.isEmpty;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import p455w0rd.danknull.DankNull;
import p455w0rd.danknull.blocks.tiles.TileDankNullDock;
import p455w0rd.danknull.client.gui.GuiDankNull;
import p455w0rd.danknull.container.ContainerDankNullDock;
import p455w0rd.danknull.container.ContainerDankNullItem;
import p455w0rd.danknull.inventory.PlayerSlot;
import p455w0rd.danknull.items.ItemDankNull;

/**
 * @author p455w0rd
 */
public class ModGuiHandler implements IGuiHandler {

    /** The /dank/null carried in the player's inventory. Must stay 0 - other code hardcodes it. */
    public static final int DANKNULL_ITEM = 0;
    /** The /dank/null docked in a {@link TileDankNullDock}. Must stay 1 - other code hardcodes it. */
    public static final int DANKNULL_DOCK = 1;

    public static void register() {
        DankNull.LOGGER.info("Registering GUI Handler");
        NetworkRegistry.INSTANCE.registerGuiHandler(DankNull.INSTANCE, new ModGuiHandler());
    }

    /**
     * 1.7.10 has no {@code BlockPos}, so the position is passed as three ints.
     */
    public static void launchGui(final int id, final EntityPlayer player, final World world, final int x, final int y,
        final int z) {
        if (!world.isRemote) {
            player.openGui(DankNull.INSTANCE, id, world, x, y, z);
        }
    }

    private static PlayerSlot getDankNullSlot(final EntityPlayer player) {
        final InventoryPlayer playerInv = player.inventory;
        // There is no off-hand in 1.7.10, so the held item is the only "hand" to check.
        final ItemStack held = player.getHeldItem();
        if (!isEmpty(held) && held.getItem() instanceof ItemDankNull) {
            return new PlayerSlot(playerInv.currentItem, MAIN);
        }
        for (int i = 0; i < playerInv.mainInventory.length; i++) {
            final ItemStack stack = playerInv.mainInventory[i];
            if (!isEmpty(stack) && stack.getItem() instanceof ItemDankNull) {
                return new PlayerSlot(i, MAIN);
            }
        }
        return null;
    }

    private static TileDankNullDock getDock(final World world, final int x, final int y, final int z) {
        final TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileDankNullDock) {
            final TileDankNullDock dock = (TileDankNullDock) te;
            if (!isEmpty(dock.getDankNull()) && dock.getHandler() != null) {
                return dock;
            }
        }
        return null;
    }

    @Override
    public Object getServerGuiElement(final int id, final EntityPlayer player, final World world, final int x,
        final int y, final int z) {
        switch (id) {
            case DANKNULL_ITEM: {
                final PlayerSlot dankNull = getDankNullSlot(player);
                return dankNull == null ? null : new ContainerDankNullItem(player, dankNull);
            }
            case DANKNULL_DOCK: {
                final TileDankNullDock dock = getDock(world, x, y, z);
                return dock == null ? null : new ContainerDankNullDock(player, dock);
            }
            default:
                return null;
        }
    }

    @Override
    public Object getClientGuiElement(final int id, final EntityPlayer player, final World world, final int x,
        final int y, final int z) {
        switch (id) {
            case DANKNULL_ITEM: {
                final PlayerSlot dankNull = getDankNullSlot(player);
                return dankNull == null ? null : new GuiDankNull(new ContainerDankNullItem(player, dankNull));
            }
            case DANKNULL_DOCK: {
                final TileDankNullDock dock = getDock(world, x, y, z);
                return dock == null ? null : new GuiDankNull(new ContainerDankNullDock(player, dock));
            }
            default:
                return null;
        }
    }

    /**
     * Retained from upstream so callers can keep using named ids; the ordinals match {@link #DANKNULL_ITEM} and
     * {@link #DANKNULL_DOCK}.
     */
    public enum GUIType {

        DANKNULL,
        DANKNULL_TE;

        public static final GUIType[] VALUES = values();
    }
}
