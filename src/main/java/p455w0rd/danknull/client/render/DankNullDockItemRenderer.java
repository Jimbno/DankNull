package p455w0rd.danknull.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import p455w0rd.danknull.items.ItemBlockDankNullDock;
import p455w0rd.danknull.util.DankNullStackUtils;

/**
 * The docking station's <em>item</em> form: the dock body OBJ with the docked /dank/null floating above it - the
 * same pair {@link TESRDankNullDock} draws for the placed block, and what upstream's nested
 * {@code TESRDankNullDock.DankNullDockItemRenderer} did in 1.12.
 *
 * <p>
 * <b>Why the display transform is applied by hand.</b> Forge hands an {@code IItemRenderer} a raw helper space that
 * differs per {@link ItemRenderType}; the block-model path (GTNHLib's {@code ModelISBRH}) normally absorbs that in
 * its private {@code applyItemDisplay}. Since the dock no longer goes through a JSON model, that fixed sequence is
 * reproduced in {@link #applyDockDisplay} - the "no display data" branch, as the dock model carries no
 * {@code display} block. After it, the coordinate space is the model's own 0..1 block space, so the /dank/null can
 * be placed with the same numbers {@link TESRDankNullDock} uses in world space.
 * </p>
 */
public class DankNullDockItemRenderer implements IItemRenderer {

    /** Centre height of the floating /dank/null in dock-model space; matches {@link TESRDankNullDock}. */
    private static final float DOCKED_Y = 0.75F;

    /** Matches {@link TESRDankNullDock}'s docked-item scale. */
    private static final float DOCKED_SCALE = 0.75F;

    private final DankNullItemRenderer dankNullRenderer;

    DankNullDockItemRenderer(final DankNullItemRenderer dankNullRenderer) {
        this.dankNullRenderer = dankNullRenderer;
    }

    /** This renderer draws every render type itself. */
    @Override
    public boolean handleRenderType(final ItemStack item, final ItemRenderType type) {
        return true;
    }

    /** Asks Forge for the standard helper transforms, which {@link #applyDockDisplay} is calibrated for. */
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
            applyDockDisplay(type);
            // applyDockDisplay leaves the model's own 0..1 block space, but the OBJ is centred on X/Z, so shift it
            // to the middle of that space.
            GL11.glTranslatef(0.5F, 0.0F, 0.5F);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TESRDankNullDock.BODY_TEXTURE);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            ObjItemModel.get(TESRDankNullDock.BODY_MODEL)
                .renderAll();
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }

        final ItemStack docked = ItemBlockDankNullDock.getDockedDankNull(stack);
        if (DankNullStackUtils.isEmpty(docked)) {
            return;
        }

        GL11.glPushMatrix();
        try {
            applyDockDisplay(type);
            GL11.glTranslatef(0.5F, DOCKED_Y, 0.5F);
            GL11.glScalef(DOCKED_SCALE, DOCKED_SCALE, DOCKED_SCALE);
            dankNullRenderer.renderDankNull(docked);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * Reproduces {@code ModelISBRH.applyItemDisplay} for a model with no {@code display} block, so that whatever is
     * drawn afterwards lands in the same place as the dock body. Pivot is the block centre, as there.
     *
     * <p>
     * {@code FIRST_PERSON_MAP} deliberately gets nothing: {@code applyItemDisplay} has no branch for it either.
     * </p>
     */
    private static void applyDockDisplay(final ItemRenderType type) {
        switch (type) {
            case EQUIPPED:
                GL11.glTranslatef(0.0F, 2.5F / 16.0F, 0.0F);
                GL11.glTranslatef(0.5F, 0.5F, 0.5F);
                GL11.glRotatef(75.0F, 0.0F, 0.0F, 1.0F);
                GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
                GL11.glScalef(0.375F, 0.375F, 0.375F);
                GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
                break;
            case EQUIPPED_FIRST_PERSON:
                GL11.glTranslatef(0.5F, 0.5F, 0.5F);
                GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
                GL11.glScalef(0.4F, 0.4F, 0.4F);
                GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
                break;
            case INVENTORY:
                GL11.glTranslatef(0.5F, 0.5F, 0.5F);
                GL11.glRotatef(30.0F, 0.0F, 0.0F, 1.0F);
                GL11.glRotatef(-135.0F, 0.0F, 1.0F, 0.0F);
                GL11.glScalef(0.625F, 0.625F, 0.625F);
                GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
                break;
            case ENTITY:
                GL11.glTranslatef(0.0F, 3.0F / 16.0F, 0.0F);
                GL11.glTranslatef(0.5F, 0.5F, 0.5F);
                GL11.glScalef(0.25F, 0.25F, 0.25F);
                GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
                break;
            default:
                break;
        }
    }
}
