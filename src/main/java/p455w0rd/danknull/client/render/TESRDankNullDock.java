package p455w0rd.danknull.client.render;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import p455w0rd.danknull.blocks.tiles.TileDankNullDock;
import p455w0rd.danknull.init.ModGlobals;
import p455w0rd.danknull.util.DankNullStackUtils;

/**
 * Draws the docking station: its body from {@code models/danknull_dock.obj}, and the docked /dank/null above it.
 *
 * <p>
 * The body is drawn here rather than in the chunk mesh because Forge's OBJ loader renders through
 * {@code Tessellator.startDrawing()/draw()} - immediate mode - which cannot run inside {@code renderWorldBlock}
 * while a chunk-wide tessellation is open. {@code BlockDankNullDock.getRenderType()} returns -1 accordingly, so
 * nothing is drawn for the block in the chunk pass. Drawing the model here also lets it bind its own texture, so
 * its UVs are not confined to a stitched terrain atlas sprite.
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

    /** Dock body model and its texture, produced by {@code tools/import-dock.js}. */
    static final String BODY_MODEL = "models/danknull_dock.obj";
    static final ResourceLocation BODY_TEXTURE = new ResourceLocation(
        ModGlobals.MODID,
        "textures/models/danknull_dock.png");

    private final DankNullItemRenderer itemRenderer = new DankNullItemRenderer();

    /**
     * Draws the dock body. The OBJ is modelled in block space with the origin at the centre of the block's
     * footprint and y running up from its base, so it needs no transform beyond being placed at the block corner.
     */
    private void renderBody(final TileDankNullDock dock, final double x, final double y, final double z) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glTranslated(x + 0.5D, y, z + 0.5D);
            // Metadata holds which way the front faces; the model is authored facing south (its opening on +X).
            GL11.glRotatef(facingAngle(dock), 0.0F, 1.0F, 0.0F);
            bindTexture(BODY_TEXTURE);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            ObjItemModel.get(BODY_MODEL)
                .renderAll();
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    /** Degrees to rotate the body so its front matches the facing stored in block metadata. */
    private static float facingAngle(final TileDankNullDock dock) {
        if (dock.getWorldObj() == null) {
            return 0.0F;
        }
        switch (dock.getWorldObj()
            .getBlockMetadata(dock.xCoord, dock.yCoord, dock.zCoord) & 3) {
            case 1:
                return 90.0F;
            case 2:
                return 180.0F;
            case 3:
                return 270.0F;
            default:
                return 0.0F;
        }
    }

    @Override
    public void renderTileEntityAt(final TileEntity tile, final double x, final double y, final double z,
        final float partialTicks) {
        if (!(tile instanceof TileDankNullDock)) {
            return;
        }
        final TileDankNullDock dock = (TileDankNullDock) tile;

        final float previousBrightnessX = OpenGlHelper.lastBrightnessX;
        final float previousBrightnessY = OpenGlHelper.lastBrightnessY;

        // The body draws whether or not anything is docked - it is the block itself, which renders nothing in the
        // chunk pass.
        if (dock.getWorldObj() != null) {
            final int bodyBrightness = dock.getWorldObj()
                .getLightBrightnessForSkyBlocks(dock.xCoord, dock.yCoord, dock.zCoord, 0);
            OpenGlHelper
                .setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, bodyBrightness % 65536, bodyBrightness / 65536);
        }
        renderBody(dock, x, y, z);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, previousBrightnessX, previousBrightnessY);

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
