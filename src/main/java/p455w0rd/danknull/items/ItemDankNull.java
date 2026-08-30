package p455w0rd.danknull.items;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemSlab;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import p455w0rd.danknull.DankNull;
import p455w0rd.danknull.api.DankNullItemModes.ItemPlacementMode;
import p455w0rd.danknull.api.IDankNullHandler;
import p455w0rd.danknull.init.ModConfig.Options;
import p455w0rd.danknull.init.ModCreativeTab;
import p455w0rd.danknull.init.ModGlobals.DankNullTier;
import p455w0rd.danknull.init.ModGuiHandler;
import p455w0rd.danknull.util.DankNullStackUtils;
import p455w0rd.danknull.util.DankNullUtils;

/**
 * @author p455w0rd
 */
// Rendering: 3D model (frame + per-tier glass), handled by the render agent via
// MinecraftForgeClient.registerItemRenderer. See client/render/.
// Deliberately no registerIcons/getIcon here - there is no flat dank_null_N.png sprite to bind.
public class ItemDankNull extends Item {

    private final DankNullTier tier;

    public ItemDankNull(final DankNullTier tier) {
        this.tier = tier;
        setUnlocalizedName(tier.getUnlocalizedNameForDankNull());
        setMaxStackSize(1);
        setMaxDamage(0);
        setNoRepair();
        setCreativeTab(ModCreativeTab.TAB);
    }

    public DankNullTier getTier() {
        return tier;
    }

    public static boolean isDankNull(final ItemStack stack) {
        return !DankNullStackUtils.isEmpty(stack) && stack.getItem() instanceof ItemDankNull;
    }

    public static DankNullTier getTier(final ItemStack dankNull) {
        int meta = -1;
        if (isDankNull(dankNull)) {
            meta = ((ItemDankNull) dankNull.getItem()).getTier()
                .ordinal();
        } else if (ItemDankNullPanel.isDankNullPanel(dankNull)) {
            meta = ((ItemDankNullPanel) dankNull.getItem()).getTier()
                .ordinal();
        } else if (ItemBlockDankNullDock.isDankNullDock(dankNull)) {
            final ItemStack dockedDank = ItemBlockDankNullDock.getDockedDankNull(dankNull);
            meta = isDankNull(dockedDank) ? ((ItemDankNull) dockedDank.getItem()).getTier()
                .ordinal() : -1;
        }
        return meta == -1 ? DankNullTier.NONE : DankNullTier.VALUES[meta];
    }

    @Override
    public EnumRarity getRarity(final ItemStack stack) {
        return tier.getRarity();
    }

    @Override
    public String getItemStackDisplayName(final ItemStack stack) {
        String name = super.getItemStackDisplayName(stack);
        if (Options.callItDevNull) {
            name = name.replace("/dank/", "/dev/");
        }
        return name;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addInformation(final ItemStack stack, final EntityPlayer player, final List tooltip,
        final boolean advanced) {
        final DankNullTier stackTier = getTier(stack);
        tooltip.add(StatCollector.translateToLocal("dn.number_of_slots.desc") + ": " + stackTier.getNumRows() * 9);
        final String maxMsg = stackTier == DankNullTier.CREATIVE
            ? "" + EnumChatFormatting.DARK_PURPLE
                + StatCollector.translateToLocal("dn.infinite.desc")
                + EnumChatFormatting.GRAY
            : "" + stackTier.getMaxStackSize();
        tooltip.add(maxMsg + " " + StatCollector.translateToLocal("dn.items_per_slot.desc"));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(final ItemStack stack, final int pass) {
        return true;
    }

    @Override
    public boolean isDamaged(final ItemStack stack) {
        return false;
    }

    @Override
    public boolean isRepairable() {
        return false;
    }

    @Override
    public boolean showDurabilityBar(final ItemStack stack) {
        return false;
    }

    @Override
    public boolean isDamageable() {
        return false;
    }

    private Block getBlockUnderPlayer(final EntityPlayer player) {
        final int blockX = MathHelper.floor_double(player.posX);
        final int blockY = MathHelper.floor_double(player.boundingBox.minY - 0.5D);
        final int blockZ = MathHelper.floor_double(player.posZ);
        return player.worldObj.getBlock(blockX, blockY, blockZ);
    }

    private void openGui(final EntityPlayer player, final World world) {
        player.openGui(
            DankNull.INSTANCE,
            ModGuiHandler.DANKNULL_ITEM,
            world,
            (int) player.posX,
            (int) player.posY,
            (int) player.posZ);
    }

    @Override
    public ItemStack onItemRightClick(final ItemStack stack, final World world, final EntityPlayer player) {
        if (player.isSneaking() && getBlockUnderPlayer(player) != Blocks.air && !world.isRemote) {
            openGui(player, world);
        }
        return stack;
    }

    /**
     * Places the currently selected stack out of the /dank/null.
     *
     * <p>
     * Mirrors upstream: sneak to open the GUI, placement-mode gating, the filled-bucket special case, the
     * slab/double-slab merge special case, then ordinary block placement with the placement sound.
     * </p>
     */
    @Override
    public boolean onItemUse(final ItemStack stack, final EntityPlayer player, final World world, final int x,
        final int y, final int z, final int side, final float hitX, final float hitY, final float hitZ) {
        final IDankNullHandler handler = DankNullUtils.getHandler(stack);
        if (handler == null) {
            return false;
        }
        final int selected = handler.getSelected();
        final ItemStack selectedStack = selected > -1 ? handler.getFullStackInSlot(selected) : null;
        final Block selectedBlock = DankNullStackUtils.isEmpty(selectedStack) ? null
            : Block.getBlockFromItem(selectedStack.getItem());
        final boolean isSelectedStackABlock = selectedBlock != null && selectedBlock != Blocks.air;
        final Block blockUnderPlayer = getBlockUnderPlayer(player);

        // Upstream also required a block to be selected here. 1.7.10 does still fall through to onItemRightClick
        // when this returns false, but only via the client's sendUseItem path, whose C08 carries no block position -
        // so getBlockUnderPlayer would be evaluated for a different interaction than the one the player made, and
        // sneak+right-click on a block with an empty /dank/null did nothing at all. The selected-block check is
        // therefore used only to choose between "place the block" and "open the GUI", not whether the GUI can open.
        if (player.isSneaking() && blockUnderPlayer != Blocks.air
            && (!isSelectedStackABlock || blockUnderPlayer != selectedBlock)) {
            if (!world.isRemote) {
                openGui(player, world);
            }
            return true;
        }
        if (DankNullStackUtils.isEmpty(selectedStack)) {
            return false;
        }
        final ItemPlacementMode placementMode = handler.getPlacementMode(selectedStack);
        if (placementMode.getNumberToKeep() >= DankNullStackUtils.getCount(selectedStack)
            && !player.capabilities.isCreativeMode) {
            return false;
        }
        // Consumption in every placement path below deliberately IGNORES the per-stack extraction mode: placement
        // is governed by the placement mode (checked above), and letting the extraction mode's keep-count block
        // the consume would place the fluid/block while leaving the item in the /dank/null - a dupe. Each branch
        // also simulates the consume BEFORE acting, so nothing is ever placed that cannot then be paid for.
        //
        // Upstream's bucket branch. 1.7.10 has no UniversalBucket/fluid capabilities, so "is a bucket" is decided
        // through FluidContainerRegistry / IFluidContainerItem instead - see isFilledBucket.
        if (isFilledBucket(selectedStack)) {
            if (DankNullStackUtils.isEmpty(handler.extractItemIngoreExtractionMode(selected, 1, true))) {
                return false;
            }
            if (!tryUseBucket(world, player, selectedStack, x, y, z, side)) {
                return false;
            }
            handler.extractItemIngoreExtractionMode(selected, 1, player.capabilities.isCreativeMode);
            return true;
        }

        if (!(selectedStack.getItem() instanceof ItemBlock) || !isSelectedStackABlock) {
            return false;
        }

        // Upstream's double-slab merge. 1.7.10's vanilla ItemSlab.onItemUse already implements the whole
        // merge-into-a-double-slab dance (including the offset case in func_150946_a) against block metadata, so it is
        // delegated to rather than reimplemented; it consumes from the throwaway single-item stack we hand it, which
        // is how we detect that something was actually placed.
        if (selectedStack.getItem() instanceof ItemSlab) {
            if (DankNullStackUtils.isEmpty(handler.extractItemIngoreExtractionMode(selected, 1, true))) {
                return false;
            }
            final ItemStack slabStack = DankNullStackUtils.copyWithSize(selectedStack, 1);
            if (!selectedStack.getItem()
                .onItemUse(slabStack, player, world, x, y, z, side, hitX, hitY, hitZ)) {
                return false;
            }
            if (DankNullStackUtils.isEmpty(slabStack)) {
                handler.extractItemIngoreExtractionMode(selected, 1, player.capabilities.isCreativeMode);
            }
            return true;
        }

        final ForgeDirection facing = ForgeDirection.getOrientation(side);
        int placeX = x;
        int placeY = y;
        int placeZ = z;
        if (!world.getBlock(x, y, z)
            .isReplaceable(world, x, y, z)) {
            placeX += facing.offsetX;
            placeY += facing.offsetY;
            placeZ += facing.offsetZ;
        }

        if (!player.canPlayerEdit(placeX, placeY, placeZ, side, selectedStack)
            || !world.canPlaceEntityOnSide(selectedBlock, placeX, placeY, placeZ, false, side, player, selectedStack)
            || DankNullStackUtils.isEmpty(handler.extractItemIngoreExtractionMode(selected, 1, true))) {
            return false;
        }

        final ItemBlock itemBlock = (ItemBlock) selectedStack.getItem();
        final int meta = itemBlock.getMetadata(selectedStack.getItemDamage());
        final ItemStack placeStack = DankNullStackUtils.copyWithSize(selectedStack, 1);
        if (itemBlock.placeBlockAt(placeStack, player, world, placeX, placeY, placeZ, side, hitX, hitY, hitZ, meta)) {
            final Block placed = world.getBlock(placeX, placeY, placeZ);
            world.playSoundEffect(
                placeX + 0.5D,
                placeY + 0.5D,
                placeZ + 0.5D,
                placed.stepSound.func_150496_b(),
                (placed.stepSound.getVolume() + 1.0F) / 2.0F,
                placed.stepSound.getPitch() * 0.8F);
            handler.extractItemIngoreExtractionMode(selected, 1, player.capabilities.isCreativeMode);
        }
        return true;
    }

    /**
     * 1.7.10 replacement for upstream's {@code isBucket}, which tested for the vanilla water/lava buckets or a Forge
     * {@code UniversalBucket}. Here that is a filled vanilla {@link ItemBucket}, any filled container Forge has
     * registered against an empty bucket, or an {@link IFluidContainerItem} holding at least a bucket of a fluid that
     * has a block. Empty containers - and therefore picking fluids up - are out of scope, as they are upstream.
     */
    private boolean isFilledBucket(final ItemStack stack) {
        if (DankNullStackUtils.isEmpty(stack)) {
            return false;
        }
        if (stack.getItem() instanceof ItemBucket) {
            // Only the filled vanilla buckets are registered; the empty one and milk are not.
            return FluidContainerRegistry.isFilledContainer(stack);
        }
        if (stack.getItem() instanceof IFluidContainerItem) {
            final FluidStack contained = getContainedFluid(stack);
            return contained != null && contained.amount >= FluidContainerRegistry.BUCKET_VOLUME
                && contained.getFluid() != null
                && contained.getFluid()
                    .canBePlacedInWorld();
        }
        return FluidContainerRegistry.isFilledContainer(stack) && FluidContainerRegistry.isBucket(stack);
    }

    private FluidStack getContainedFluid(final ItemStack stack) {
        if (DankNullStackUtils.isEmpty(stack)) {
            return null;
        }
        if (stack.getItem() instanceof IFluidContainerItem) {
            return ((IFluidContainerItem) stack.getItem()).getFluid(stack);
        }
        return FluidContainerRegistry.getFluidForFilledItem(stack);
    }

    /**
     * Empties the given filled bucket against the clicked face, and hands the emptied container back to the player.
     * Modelled on {@link ItemBucket#onItemRightClick}: the fluid goes into the block adjacent to the clicked face.
     *
     * @return whether the fluid was actually placed
     */
    private boolean tryUseBucket(final World world, final EntityPlayer player, final ItemStack bucket, final int x,
        final int y, final int z, final int side) {
        if (!isFilledBucket(bucket)) {
            return false;
        }
        final ForgeDirection facing = ForgeDirection.getOrientation(side);
        final int targetX = x + facing.offsetX;
        final int targetY = y + facing.offsetY;
        final int targetZ = z + facing.offsetZ;
        if (!world.canMineBlock(player, x, y, z) || !player.canPlayerEdit(targetX, targetY, targetZ, side, bucket)) {
            return false;
        }

        final boolean placed;
        if (bucket.getItem() instanceof ItemBucket) {
            // Vanilla knows which block each of its buckets holds, and handles the Nether water fizz.
            placed = ((ItemBucket) bucket.getItem()).tryPlaceContainedLiquid(world, targetX, targetY, targetZ);
        } else {
            final FluidStack contained = getContainedFluid(bucket);
            if (contained == null || contained.getFluid() == null
                || !contained.getFluid()
                    .canBePlacedInWorld()) {
                return false;
            }
            placed = placeFluidBlock(
                world,
                contained.getFluid()
                    .getBlock(),
                targetX,
                targetY,
                targetZ);
        }
        if (!placed) {
            return false;
        }
        // In creative the /dank/null keeps its bucket (the extract is simulated), so handing an empty one back would
        // create it out of nothing - vanilla ItemBucket skips the swap in creative for the same reason.
        if (!world.isRemote && !player.capabilities.isCreativeMode) {
            giveEmptiedContainer(player, bucket);
        }
        return true;
    }

    /**
     * Generic form of {@link ItemBucket#tryPlaceContainedLiquid} for fluids vanilla knows nothing about.
     */
    private boolean placeFluidBlock(final World world, final Block fluidBlock, final int x, final int y, final int z) {
        if (fluidBlock == null || fluidBlock == Blocks.air) {
            return false;
        }
        final Material material = world.getBlock(x, y, z)
            .getMaterial();
        final boolean replaceable = !material.isSolid();
        if (!world.isAirBlock(x, y, z) && !replaceable) {
            return false;
        }
        if (!world.isRemote && replaceable && !material.isLiquid()) {
            world.func_147480_a(x, y, z, true);
        }
        return world.setBlock(x, y, z, fluidBlock, 0, 3);
    }

    /**
     * Upstream hands the player a plain empty bucket; here the emptied container is asked for instead, so a modded
     * container comes back as itself rather than as a vanilla bucket.
     */
    private void giveEmptiedContainer(final EntityPlayer player, final ItemStack bucket) {
        final ItemStack empty;
        if (bucket.getItem() instanceof IFluidContainerItem) {
            empty = DankNullStackUtils.copyWithSize(bucket, 1);
            ((IFluidContainerItem) empty.getItem()).drain(empty, FluidContainerRegistry.BUCKET_VOLUME, true);
        } else {
            empty = FluidContainerRegistry.drainFluidContainer(bucket);
        }
        if (DankNullStackUtils.isEmpty(empty)) {
            return;
        }
        if (!player.inventory.addItemStackToInventory(empty)) {
            player.dropPlayerItemWithRandomChoice(empty, false);
        }
    }
}
