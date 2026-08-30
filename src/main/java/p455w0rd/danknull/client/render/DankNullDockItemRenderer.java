package p455w0rd.danknull.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.model.obj.WavefrontObject;

import org.lwjgl.opengl.GL11;

import p455w0rd.danknull.init.ModBlocks;
import p455w0rd.danknull.items.ItemBlockDankNullDock;
import p455w0rd.danknull.util.DankNullStackUtils;

/**
 * The docking station's <em>item</em> form: the dock body OBJ with the docked /dank/null floating above it - the
 * same pair {@link TESRDankNullDock} draws for the placed block, and what upstream's nested
 * {@code TESRDankNullDock.DankNullDockItemRenderer} did in 1.12.
 *
 * <p>
 * Geometry is emitted centred on the origin, the convention every vanilla "block as item" path is calibrated for
 * and the one {@link DankNullItemRenderer} already uses, so {@code shouldUseRenderHelper} returns {@code true}
 * throughout and only {@code EQUIPPED_BLOCK}'s -0.5 pre-translate has to be compensated.
 * </p>
 */
public class DankNullDockItemRenderer implements IItemRenderer {

    /** Centre height of the floating /dank/null in dock-model space; matches {@link TESRDankNullDock}. */
    private static final float DOCKED_Y = 0.75F;

    /** Matches {@link TESRDankNullDock}'s docked-item scale. */
    private static final float DOCKED_SCALE = 0.75F;

    /** Matches {@link DankNullItemRenderer}: lifts the dropped item clear of the ground. */
    private static final float ENTITY_LIFT = 0.25F;

    /** Full-bright lightmap coordinates, as the inventory has no world lighting to sample. */
    private static final int BRIGHT = 0x00F000F0;

    private final DankNullItemRenderer dankNullRenderer;

    DankNullDockItemRenderer(final DankNullItemRenderer dankNullRenderer) {
        this.dankNullRenderer = dankNullRenderer;
    }

    @Override
    public boolean handleRenderType(final ItemStack item, final ItemRenderType type) {
        return type == ItemRenderType.ENTITY || type == ItemRenderType.EQUIPPED
            || type == ItemRenderType.EQUIPPED_FIRST_PERSON
            || type == ItemRenderType.INVENTORY;
    }

    /** Treat the dock as a 3D block in every context; see the class javadoc. */
    @Override
    public boolean shouldUseRenderHelper(final ItemRenderType type, final ItemStack item,
        final ItemRendererHelper helper) {
        return true;
    }

    @Override
    public void renderItem(final ItemRenderType type, final ItemStack stack, final Object... data) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            // Same convention as DankNullItemRenderer: draw centred on the origin, which is what every vanilla
            // "block as item" path is calibrated for. EQUIPPED_BLOCK is the one helper that pre-translates by
            // -0.5 for 0..1 geometry, so that is undone here.
            if (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
                GL11.glTranslatef(0.5F, 0.5F, 0.5F);
            } else if (type == ItemRenderType.ENTITY) {
                GL11.glTranslatef(0.0F, ENTITY_LIFT, 0.0F);
            }
            // The dock OBJ is centred on x/z but runs y 0..1, so drop it half a block to centre it too.
            GL11.glTranslatef(0.0F, -0.5F, 0.0F);

            renderBody();

            final ItemStack docked = ItemBlockDankNullDock.getDockedDankNull(stack);
            if (!DankNullStackUtils.isEmpty(docked)) {
                GL11.glPushMatrix();
                try {
                    GL11.glTranslatef(0.0F, DOCKED_Y, 0.0F);
                    GL11.glScalef(DOCKED_SCALE, DOCKED_SCALE, DOCKED_SCALE);
                    dankNullRenderer.renderDankNull(docked);
                } finally {
                    GL11.glPopMatrix();
                }
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    /**
     * Draws the dock body from the same OBJ and the same atlas sprite the chunk mesh uses, so the held item and
     * the placed block cannot drift apart. Always drawn in the model authored orientation - an item has no facing.
     */
    private static void renderBody() {
        final WavefrontObject model = ObjItemModel.get(DankNullDockRenderer.BODY_MODEL)
            .getWavefront();
        final IIcon icon = ModBlocks.DANKNULL_DOCK.getBlockTextureFromSide(0);
        if (model == null || icon == null) {
            return;
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        final Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        // No world lighting to sample outside the chunk pass; emit() still applies directional shading, which is
        // what keeps the inventory icon reading as a solid shape rather than a flat silhouette.
        tessellator.setBrightness(BRIGHT);
        DankNullDockRenderer.emit(tessellator, model, icon, DankNullDockRenderer.AUTHORED_FACING, 0.0D, 0.0D, 0.0D);
        tessellator.draw();
    }
}
