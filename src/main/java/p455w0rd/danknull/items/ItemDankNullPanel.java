package p455w0rd.danknull.items;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import p455w0rd.danknull.init.ModCreativeTab;
import p455w0rd.danknull.init.ModGlobals;
import p455w0rd.danknull.init.ModGlobals.DankNullTier;
import p455w0rd.danknull.util.DankNullStackUtils;

/**
 * @author p455w0rd
 */
// Unlike the /dank/null itself the panels have real flat sprites
// (assets/danknull/textures/items/dank_null_panel_0..5.png), so they use ordinary 1.7.10 IIcons rather
// than a custom renderer.
public class ItemDankNullPanel extends Item {

    private final DankNullTier tier;

    public ItemDankNullPanel(final DankNullTier tier) {
        this.tier = tier;
        setUnlocalizedName(tier.getUnlocalizedNameForPanel());
        setMaxDamage(0);
        setNoRepair();
        setCreativeTab(ModCreativeTab.TAB);
        setTextureName(ModGlobals.MODID + ":" + tier.getUnlocalizedNameForPanel());
    }

    public static boolean isDankNullPanel(final ItemStack stack) {
        return !DankNullStackUtils.isEmpty(stack) && stack.getItem() instanceof ItemDankNullPanel;
    }

    public DankNullTier getTier() {
        return tier;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(final IIconRegister register) {
        itemIcon = register.registerIcon(getIconString());
    }

    @Override
    public EnumRarity getRarity(final ItemStack stack) {
        return tier.getRarity();
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
}
