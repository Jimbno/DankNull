package p455w0rd.danknull.items;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import p455w0rd.danknull.blocks.tiles.TileDankNullDock;
import p455w0rd.danknull.init.ModConfig.Options;
import p455w0rd.danknull.init.ModGlobals.NBT;
import p455w0rd.danknull.util.DankNullStackUtils;

/**
 * @author p455w0rd
 */
// The 1.7.10 block registry creates the ItemBlock itself, so this takes the Block in its constructor and is
// registered by ModBlocks via GameRegistry.registerBlock(block, ItemBlockDankNullDock.class, name) rather than
// being constructed and registered by ModItems.
public class ItemBlockDankNullDock extends ItemBlock {

    public ItemBlockDankNullDock(final Block block) {
        super(block);
    }

    /**
     * Restores a docked /dank/null when the station is placed back down.
     *
     * <p>
     * 1.12's {@code ItemBlock.placeBlockAt} calls {@code setTileEntityNBT}, which copies the stack's
     * {@code BlockEntityTag} into the freshly created tile for free. 1.7.10 has no such hook - the string
     * {@code BlockEntityTag} does not appear anywhere in its sources - so without this the tag written on break
     * would be read only for the tooltip and renderer, and the docked /dank/null would be destroyed on placement
     * even though the item in hand still visibly contained it.
     * </p>
     */
    @Override
    public boolean placeBlockAt(final ItemStack stack, final EntityPlayer player, final World world, final int x,
        final int y, final int z, final int side, final float hitX, final float hitY, final float hitZ,
        final int metadata) {
        if (!super.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, metadata)) {
            return false;
        }
        if (world.isRemote || !stack.hasTagCompound()) {
            return true;
        }
        final NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(NBT.BLOCKENTITYTAG, Constants.NBT.TAG_COMPOUND)) {
            return true;
        }
        final TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileDankNullDock)) {
            return true;
        }
        final NBTTagCompound stored = (NBTTagCompound) tag.getCompoundTag(NBT.BLOCKENTITYTAG)
            .copy();
        // The tile's own coordinates win; only the docked contents travel with the item.
        stored.setInteger("x", x);
        stored.setInteger("y", y);
        stored.setInteger("z", z);
        te.readFromNBT(stored);
        te.markDirty();
        return true;
    }

    /** @return the /dank/null stored in the docking station's BlockEntityTag, or {@code null} if there is none */
    public static ItemStack getDockedDankNull(final ItemStack dankDock) {
        if (DankNullStackUtils.isEmpty(dankDock) || !dankDock.hasTagCompound()) {
            return null;
        }
        if (!dankDock.getTagCompound()
            .hasKey(NBT.BLOCKENTITYTAG, Constants.NBT.TAG_COMPOUND)) {
            return null;
        }
        final NBTTagCompound nbt = dankDock.getTagCompound()
            .getCompoundTag(NBT.BLOCKENTITYTAG);
        if (nbt.hasNoTags() || !nbt.hasKey(NBT.DOCKEDSTACK, Constants.NBT.TAG_COMPOUND)) {
            return null;
        }
        return ItemStack.loadItemStackFromNBT(nbt.getCompoundTag(NBT.DOCKEDSTACK));
    }

    public static boolean isDankNullDock(final ItemStack stack) {
        return !DankNullStackUtils.isEmpty(stack) && stack.getItem() instanceof ItemBlockDankNullDock;
    }

    @Override
    public String getItemStackDisplayName(final ItemStack stack) {
        String name = super.getItemStackDisplayName(stack);
        if (Options.callItDevNull) {
            name = name.replace("/dank/", "/dev/");
        }
        return name;
    }
}
