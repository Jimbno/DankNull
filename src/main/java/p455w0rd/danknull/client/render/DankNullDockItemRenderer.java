package p455w0rd.danknull.client.render;

import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.client.model.ModelISBRH;

import p455w0rd.danknull.items.ItemBlockDankNullDock;
import p455w0rd.danknull.util.DankNullStackUtils;

/**
 * The docking station's <em>item</em> form: GTNHLib's JSON model for the dock body, with the docked /dank/null
 * floating above it - the same thing {@link TESRDankNullDock} draws for the placed block, and what upstream's nested
 * {@code TESRDankNullDock.DankNullDockItemRenderer} did in 1.12.
 *
 * <p>
 * <b>Why this delegates instead of replacing.</b> GTNHLib owns the dock's ItemBlock renderer: its
 * {@code ModelRegistry.ReloadListener} walks the whole block registry on every resource-manager reload and calls
 * {@code MinecraftForgeClient.registerItemRenderer(Item.getItemFromBlock(block), ModelISBRH.INSTANCE.get())} for
 * every modeled block ({@code ModelRegistry.java}, {@code loadModelInfo}). Registering something else for the dock is
 * therefore only ever temporary - see {@link ModRenderers} for how the registration is kept alive. Rather than
 * reimplement what {@code ModelISBRH} does, this renderer calls straight into it for the dock body and only adds the
 * docked /dank/null on top, so the body keeps rendering exactly as GTNHLib renders it.
 * </p>
 *
 * <p>
 * <b>Why the display transform is repeated here.</b> {@code ModelISBRH.renderItem} applies the model's BlockBench
 * {@code display} transform (its private {@code applyItemDisplay}) <em>inside</em> its own
 * {@code glPushMatrix}/{@code glPopMatrix} pair, so by the time it returns the matrix is back to the raw Forge
 * helper space and the dock body's on-screen placement is not reproducible without redoing that transform.
 * {@code assets/danknull/models/block/danknull_dock.json} carries no {@code display} block at all, so every
 * {@code getDisplay} lookup returns {@code Position.ModelDisplay.DEFAULT} and {@code applyItemDisplay} takes its
 * "no display data" branch in each case - which is the fixed sequence {@link #applyDockDisplay} reproduces. After it,
 * the coordinate space is the model's own 0..1 block space, so the /dank/null can be placed with the same numbers
 * {@link TESRDankNullDock} uses in world space.
 * </p>
 */
public class DankNullDockItemRenderer implements IItemRenderer {

    /** Centre height of the floating /dank/null in dock-model space; matches {@link TESRDankNullDock}. */
    private static final float DOCKED_Y = 0.4F;

    /** Half a block wide, as in {@link TESRDankNullDock}. */
    private static final float DOCKED_SCALE = 0.5F;

    private final DankNullItemRenderer dankNullRenderer;

    DankNullDockItemRenderer(final DankNullItemRenderer dankNullRenderer) {
        this.dankNullRenderer = dankNullRenderer;
    }

    /** Matches {@code ModelISBRH}, which we stand in front of. */
    @Override
    public boolean handleRenderType(final ItemStack item, final ItemRenderType type) {
        return true;
    }

    /** Matches {@code ModelISBRH}, so Forge applies exactly the helper transforms it is calibrated for. */
    @Override
    public boolean shouldUseRenderHelper(final ItemRenderType type, final ItemStack item,
        final ItemRendererHelper helper) {
        return true;
    }

    @Override
    public void renderItem(final ItemRenderType type, final ItemStack stack, final Object... data) {
        ModelISBRH.INSTANCE.get()
            .renderItem(type, stack, data);

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
