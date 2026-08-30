package p455w0rd.danknull.client.render;

import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import p455w0rd.danknull.blocks.BlockDankNullDock;
import p455w0rd.danknull.blocks.tiles.TileDankNullDock;
import p455w0rd.danknull.init.ModBlocks;
import p455w0rd.danknull.init.ModItems;

/**
 * Client-side renderer wiring, called from {@code ClientProxy.init}.
 *
 * <p>
 * What is <em>not</em> here, and why:
 * </p>
 * <ul>
 * <li><b>The docking station's block.</b> Its body is drawn into the chunk mesh by {@link DankNullDockRenderer},
 * registered below; {@link TESRDankNullDock} adds only the docked /dank/null.</li>
 * <li><b>The HUD.</b> {@code ModEvents} owns the {@code RenderGameOverlayEvent.Post} handler and calls
 * {@link HUDRenderer#renderHUD} from there.</li>
 * </ul>
 */
public class ModRenderers {

    private static boolean registered = false;

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        final DankNullItemRenderer dankNullRenderer = new DankNullItemRenderer();
        for (final Item dankNull : ModItems.DANK_NULLS) {
            if (dankNull != null) {
                MinecraftForgeClient.registerItemRenderer(dankNull, dankNullRenderer);
            }
        }

        // Panels get the same treatment: their models are the same framed-glass shape, and a flat IIcon would
        // lose both the geometry and the glint (1.7.10 draws none behind a custom IItemRenderer).
        final DankNullPanelRenderer panelRenderer = new DankNullPanelRenderer(dankNullRenderer);
        for (final Item panel : ModItems.PANELS) {
            if (panel != null) {
                MinecraftForgeClient.registerItemRenderer(panel, panelRenderer);
            }
        }

        // The dock body renders into the chunk mesh; see DankNullDockRenderer for why it is not a TESR.
        final int dockRenderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new DankNullDockRenderer(dockRenderId));
        BlockDankNullDock.renderId = dockRenderId;

        final Item dockItem = Item.getItemFromBlock(ModBlocks.DANKNULL_DOCK);
        if (dockItem != null) {
            MinecraftForgeClient.registerItemRenderer(dockItem, new DankNullDockItemRenderer(dankNullRenderer));
        }

        ClientRegistry.bindTileEntitySpecialRenderer(TileDankNullDock.class, new TESRDankNullDock());

        final ModRenderers handler = new ModRenderers();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
    }

    /** Models cache resources resolved against one atlas generation, so they go whenever the atlas is rebuilt. */
    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onTextureStitchPost(final TextureStitchEvent.Post event) {
        ObjItemModel.flush();
    }

}
