package p455w0rd.danknull.init;

import net.minecraft.block.Block;

import cpw.mods.fml.common.registry.GameRegistry;
import p455w0rd.danknull.blocks.BlockDankNullDock;
import p455w0rd.danknull.blocks.tiles.TileDankNullDock;
import p455w0rd.danknull.items.ItemBlockDankNullDock;

/**
 * @author p455w0rd
 */
public class ModBlocks {

    public static final BlockDankNullDock DANKNULL_DOCK = new BlockDankNullDock();

    private static final Block[] BLOCK_ARRAY = new Block[] { DANKNULL_DOCK };

    private ModBlocks() {}

    public static Block[] getBlocks() {
        return BLOCK_ARRAY;
    }

    /**
     * 1.12 registered blocks from a {@code RegistryEvent.Register<Block>}; 1.7.10 does it imperatively from
     * preInit. The 1.7.10 registry also constructs the ItemBlock itself, which is why
     * {@link ItemBlockDankNullDock} takes a {@code Block} and is not built by {@link ModItems}.
     */
    public static void register() {
        GameRegistry.registerBlock(DANKNULL_DOCK, ItemBlockDankNullDock.class, BlockDankNullDock.NAME);
        GameRegistry.registerTileEntity(TileDankNullDock.class, BlockDankNullDock.NAME);
    }
}
