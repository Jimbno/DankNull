package p455w0rd.danknull.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import p455w0rd.danknull.api.IDankNullHandler;
import p455w0rd.danknull.blocks.tiles.TileDankNullDock;

/**
 * @author p455w0rd
 */
public class ContainerDankNullDock extends ContainerDankNull {

    private final TileDankNullDock tile;

    public ContainerDankNullDock(final EntityPlayer player, final TileDankNullDock tile) {
        super(player);
        this.tile = tile;
        init();
    }

    /**
     * 1.7.10 has no {@code BlockPos} and no capabilities - the tile's own coordinates and its handler getter stand
     * in for {@code getPos()} and {@code hasCapability(...)}.
     */
    @Override
    public boolean canInteractWith(final EntityPlayer player) {
        return tile.getHandler() != null
            && player.getDistanceSq(tile.xCoord + 0.5D, tile.yCoord + 0.5D, tile.zCoord + 0.5D) <= 64.0D
            && super.canInteractWith(player);
    }

    @Override
    protected boolean isDock() {
        return true;
    }

    @Override
    public IDankNullHandler getHandler() {
        return tile.getHandler();
    }

    @Override
    public ItemStack getDankNullStack() {
        return tile.getDankNull();
    }
}
