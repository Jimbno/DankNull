package p455w0rd.danknull.client.render;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
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
 * <li><b>The docking station's block.</b> It draws nothing in the chunk pass
 * ({@code BlockDankNullDock.getRenderType()} is -1); {@link TESRDankNullDock}, bound below, draws both its body and
 * the docked /dank/null from OBJ.</li>
 * <li><b>The HUD.</b> {@code ModEvents} owns the {@code RenderGameOverlayEvent.Post} handler and calls
 * {@link HUDRenderer#renderHUD} from there.</li>
 * </ul>
 */
public class ModRenderers {

    private static boolean registered = false;

    /** The dock's ItemBlock, or {@code null} if the block registry has not produced one. */
    private static Item dockItem;

    private static DankNullDockItemRenderer dockRenderer;

    /** Probe stack for the registration check below; {@code MinecraftForgeClient} has no way to read the map back. */
    private static ItemStack dockProbe;

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

        dockItem = Item.getItemFromBlock(ModBlocks.DANKNULL_DOCK);
        if (dockItem != null) {
            dockRenderer = new DankNullDockItemRenderer(dankNullRenderer);
            dockProbe = new ItemStack(dockItem);
            MinecraftForgeClient.registerItemRenderer(dockItem, dockRenderer);
        }

        ClientRegistry.bindTileEntitySpecialRenderer(TileDankNullDock.class, new TESRDankNullDock());

        final ModRenderers handler = new ModRenderers();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
    }

    /**
     * Baked JSON models hold {@code IIcon}s resolved against one atlas generation, so they have to go whenever the
     * atlas is rebuilt (resource reload, resource pack change, mipmap setting change).
     */
    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onTextureStitchPost(final TextureStitchEvent.Post event) {
        JsonItemModel.flush();
        ObjItemModel.flush();
    }

    /**
     * Keeps {@link DankNullDockItemRenderer} registered for the dock's ItemBlock.
     *
     * <p>
     * GTNHLib's {@code ModelRegistry.ReloadListener.loadModelInfo} re-runs
     * {@code MinecraftForgeClient.registerItemRenderer(item, ModelISBRH.INSTANCE.get())} for <em>every</em> modeled
     * block on every resource-manager reload, silently replacing whatever else is registered for that item. There is
     * no event fired after it, and it cannot be outrun by ordering:
     * </p>
     * <ul>
     * <li>{@code TextureStitchEvent.Post} is too early. The stitch happens inside {@code TextureManager}'s own
     * reload, and {@code TextureManager} is registered as a reload listener in {@code Minecraft.startGame} - before
     * mod init - whereas GTNHLib registers its listener in <em>postInit</em>
     * ({@code GTNHLib ClientProxy.postInit}). {@code SimpleReloadableResourceManager.notifyReloadListeners} runs
     * listeners in registration order, so GTNHLib's always runs after the stitch.</li>
     * <li>Registering our own reload listener from {@code ClientProxy.init} is too early for the same reason: init
     * precedes GTNHLib's postInit, so our listener would be ordered ahead of theirs and clobbered every reload.</li>
     * </ul>
     *
     * <p>
     * Rather than depend on load-order (which would break the moment GTNHLib moved that registration, or another mod
     * did the same thing), the registration is simply re-asserted when it is found to be gone. The check is one
     * {@code IdentityHashMap} lookup on the client tick; {@code MinecraftForgeClient.registerItemRenderer} is a plain
     * {@code put} into that same map, so re-registering costs nothing either.
     * </p>
     */
    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || dockRenderer == null) {
            return;
        }
        if (MinecraftForgeClient.getItemRenderer(dockProbe, ItemRenderType.INVENTORY) != dockRenderer) {
            MinecraftForgeClient.registerItemRenderer(dockItem, dockRenderer);
        }
    }
}
