package p455w0rd.danknull.client.render;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import p455w0rd.danknull.blocks.tiles.TileDankNullDock;
import p455w0rd.danknull.util.DankNullStackUtils;

/**
 * Draws the docked /dank/null floating above the docking station.
 *
 * <p>
 * The dock body is not drawn here: it is a static shape and goes into the chunk mesh via
 * {@link DankNullDockRenderer}, so it is built once per block update rather than every frame. Only the docked
 * item - which is per-tile and tier-dependent - needs a TESR.
 * </p>
 *
 * <p>
 * Possible later optimisation: Angelica's {@code com.gtnewhorizons.angelica.api.tesr.TesrMeshProvider}
 * (API token {@code angelica|tesr}) can batch and cache this mesh. It is not used because it is a pure performance
 * win rather than a compatibility requirement, and because its mesh keys are compared by reference identity - a
 * freshly allocated key would miss the cache every frame and leak an entry until the 60s LRU sweep.
 * </p>
 */
public class TESRDankNullDock extends TileEntitySpecialRenderer {

    /**
     * Where the docked /dank/null sits, taken from the {@code dankerino} reference group in the Blockbench dock
     * project: that group is the item model translated up by 0.375, spanning y 0.375..1.125 at full size.
     * {@link DankNullItemRenderer#renderDankNull} draws a one-block-tall model centred on the origin, so
     * reproducing that envelope means scaling to its 0.75 height and centring at 0.75.
     */
    private static final double DOCKED_ITEM_SCALE = 0.75D;
    private static final double DOCKED_ITEM_CENTRE_Y = 0.75D;

    private final DankNullItemRenderer itemRenderer = new DankNullItemRenderer();

    @Override
    public void renderTileEntityAt(final TileEntity tile, final double x, final double y, final double z,
        final float partialTicks) {
        if (!(tile instanceof TileDankNullDock)) {
            return;
        }
        final TileDankNullDock dock = (TileDankNullDock) tile;

        final float previousBrightnessX = OpenGlHelper.lastBrightnessX;
        final float previousBrightnessY = OpenGlHelper.lastBrightnessY;

        final ItemStack stack = dock.getDankNull();
        if (DankNullStackUtils.isEmpty(stack)) {
            return;
        }

        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x + 0.5D, y + DOCKED_ITEM_CENTRE_Y, z + 0.5D);
            GL11.glScaled(DOCKED_ITEM_SCALE, DOCKED_ITEM_SCALE, DOCKED_ITEM_SCALE);

            // Light the floating item from the block space above the dock rather than from whatever the previous
            // TESR happened to leave bound; 1.12 got this for free from the item renderer's own lighting pass.
            if (tile.getWorldObj() != null) {
                final int brightness = tile.getWorldObj()
                    .getLightBrightnessForSkyBlocks(tile.xCoord, tile.yCoord + 1, tile.zCoord, 0);
                OpenGlHelper
                    .setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightness % 65536, brightness / 65536);
            }

            // Straight delegation to the item renderer, as upstream did. The geometry is already centred on the
            // origin and the transform above is complete, so this goes in below the per-render-type compensations
            // in DankNullItemRenderer.renderItem.
            itemRenderer.renderDankNull(stack);
        } finally {
            OpenGlHelper
                .setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, previousBrightnessX, previousBrightnessY);
            GL11.glPopMatrix();
        }
    }
}
