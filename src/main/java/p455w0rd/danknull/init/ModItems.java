package p455w0rd.danknull.init;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;

import cpw.mods.fml.common.registry.GameRegistry;
import p455w0rd.danknull.init.ModGlobals.DankNullTier;
import p455w0rd.danknull.items.ItemBlockDankNullDock;
import p455w0rd.danknull.items.ItemDankNull;
import p455w0rd.danknull.items.ItemDankNullPanel;

/**
 * @author p455w0rd
 */
public class ModItems {

    /** Registry name of the docking station's block/ItemBlock; owned by ModBlocks, mirrored here for lookups. */
    public static final String DOCK_REGISTRY_NAME = "danknull_dock";

    public static final ItemDankNull REDSTONE_DANKNULL = new ItemDankNull(DankNullTier.REDSTONE);
    public static final ItemDankNull LAPIS_DANKNULL = new ItemDankNull(DankNullTier.LAPIS);
    public static final ItemDankNull IRON_DANKNULL = new ItemDankNull(DankNullTier.IRON);
    public static final ItemDankNull GOLD_DANKNULL = new ItemDankNull(DankNullTier.GOLD);
    public static final ItemDankNull DIAMOND_DANKNULL = new ItemDankNull(DankNullTier.DIAMOND);
    public static final ItemDankNull EMERALD_DANKNULL = new ItemDankNull(DankNullTier.EMERALD);
    public static final ItemDankNull CREATIVE_DANKNULL = new ItemDankNull(DankNullTier.CREATIVE);

    public static final ItemDankNullPanel REDSTONE_PANEL = new ItemDankNullPanel(DankNullTier.REDSTONE);
    public static final ItemDankNullPanel LAPIS_PANEL = new ItemDankNullPanel(DankNullTier.LAPIS);
    public static final ItemDankNullPanel IRON_PANEL = new ItemDankNullPanel(DankNullTier.IRON);
    public static final ItemDankNullPanel GOLD_PANEL = new ItemDankNullPanel(DankNullTier.GOLD);
    public static final ItemDankNullPanel DIAMOND_PANEL = new ItemDankNullPanel(DankNullTier.DIAMOND);
    public static final ItemDankNullPanel EMERALD_PANEL = new ItemDankNullPanel(DankNullTier.EMERALD);

    /**
     * Indexed by {@link ModGlobals.DankNullTier} ordinal, REDSTONE..CREATIVE.
     * {@code DankNullTier.getUpgradedVersion()} reads {@code DANK_NULLS[ordinal + 1]}, so this must be populated
     * before any tier upgrade happens - it is filled by the static initialiser below, i.e. as soon as this class is
     * touched at all.
     */
    public static final Item[] DANK_NULLS = new Item[7];

    public static final Item[] PANELS = new Item[6];

    static {
        DANK_NULLS[DankNullTier.REDSTONE.ordinal()] = REDSTONE_DANKNULL;
        DANK_NULLS[DankNullTier.LAPIS.ordinal()] = LAPIS_DANKNULL;
        DANK_NULLS[DankNullTier.IRON.ordinal()] = IRON_DANKNULL;
        DANK_NULLS[DankNullTier.GOLD.ordinal()] = GOLD_DANKNULL;
        DANK_NULLS[DankNullTier.DIAMOND.ordinal()] = DIAMOND_DANKNULL;
        DANK_NULLS[DankNullTier.EMERALD.ordinal()] = EMERALD_DANKNULL;
        DANK_NULLS[DankNullTier.CREATIVE.ordinal()] = CREATIVE_DANKNULL;

        PANELS[DankNullTier.REDSTONE.ordinal()] = REDSTONE_PANEL;
        PANELS[DankNullTier.LAPIS.ordinal()] = LAPIS_PANEL;
        PANELS[DankNullTier.IRON.ordinal()] = IRON_PANEL;
        PANELS[DankNullTier.GOLD.ordinal()] = GOLD_PANEL;
        PANELS[DankNullTier.DIAMOND.ordinal()] = DIAMOND_PANEL;
        PANELS[DankNullTier.EMERALD.ordinal()] = EMERALD_PANEL;
    }

    /**
     * The docking station's ItemBlock. In 1.7.10 the ItemBlock instance is created by
     * {@code GameRegistry.registerBlock(block, ItemBlockDankNullDock.class, name)}, so it is not constructed here;
     * {@link #resolveDockItem()} picks it back out of the registry once the block has been registered.
     */
    public static ItemBlockDankNullDock DANK_NULL_DOCK_ITEM = null;

    /** Call after the blocks have been registered (late preInit) to populate {@link #DANK_NULL_DOCK_ITEM}. */
    public static ItemBlockDankNullDock resolveDockItem() {
        if (DANK_NULL_DOCK_ITEM == null) {
            final Item item = GameRegistry.findItem(ModGlobals.MODID, DOCK_REGISTRY_NAME);
            if (item instanceof ItemBlockDankNullDock) {
                DANK_NULL_DOCK_ITEM = (ItemBlockDankNullDock) item;
            }
        }
        return DANK_NULL_DOCK_ITEM;
    }

    /** All items owned by the mod, including the dock's ItemBlock once it has been resolved. */
    public static Item[] getItems() {
        final List<Item> items = new ArrayList<Item>();
        for (final Item item : DANK_NULLS) {
            items.add(item);
        }
        for (final Item item : PANELS) {
            items.add(item);
        }
        final Item dock = resolveDockItem();
        if (dock != null) {
            items.add(dock);
        }
        return items.toArray(new Item[items.size()]);
    }

    /** Registers the plain items. The dock's ItemBlock is registered alongside its block by ModBlocks. */
    public static void register() {
        ModCreativeTab.init();
        for (int i = 0; i < DANK_NULLS.length; i++) {
            GameRegistry.registerItem(DANK_NULLS[i], DankNullTier.VALUES[i].getUnlocalizedNameForDankNull());
        }
        for (int i = 0; i < PANELS.length; i++) {
            GameRegistry.registerItem(PANELS[i], DankNullTier.VALUES[i].getUnlocalizedNameForPanel());
        }
    }
}
