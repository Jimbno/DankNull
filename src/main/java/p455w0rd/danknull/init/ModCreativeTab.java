package p455w0rd.danknull.init;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * @author p455w0rd
 */
// 1.7.10 creative tabs are pull-based: the tab is populated by every Item/Block that was given it via
// setCreativeTab. 1.7.10 does have displayAllReleventItems, but that pull-based population already yields the
// same contents and ordering upstream's override produced, so there is nothing to override.
public class ModCreativeTab extends CreativeTabs {

    public static final CreativeTabs TAB = new ModCreativeTab();

    private ModCreativeTab() {
        super(ModGlobals.MODID);
    }

    /** Forces class initialisation so {@link #TAB} exists before the items are constructed. */
    public static void init() {}

    @Override
    public Item getTabIconItem() {
        return ModItems.CREATIVE_DANKNULL;
    }

    @Override
    public ItemStack getIconItemStack() {
        return new ItemStack(ModItems.CREATIVE_DANKNULL);
    }
}
