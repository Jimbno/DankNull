package p455w0rd.danknull.client.render;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import p455w0rd.danknull.init.ModGlobals.DankNullTier;
import p455w0rd.danknull.items.ItemDankNull;
import p455w0rd.danknull.items.ItemDankNullPanel;
import p455w0rd.danknull.util.DankNullStackUtils;

/**
 * Renders a /dank/null panel from its OBJ.
 *
 * <p>
 * Panels are the same framed-glass shape as the /dank/null itself, modelled in the same Blockbench project space
 * and sharing both of its textures, so this reuses {@link DankNullItemRenderer}'s shell, tint and glint passes
 * rather than repeating them. Falling back to a flat {@code IIcon} would lose the geometry and the glint alike -
 * 1.7.10 draws no glint behind a custom {@link IItemRenderer}, so {@code hasEffect} on its own achieves nothing.
 * </p>
 *
 * <p>
 * Unlike the /dank/null there is nothing stored inside a panel, so there is no contained stack to draw between
 * the frame and the glass.
 * </p>
 */
public class DankNullPanelRenderer implements IItemRenderer {

    private static final String OBJ_MODEL = "models/dank_null_panel.obj";

    /** The opaque body of the panel. */
    private static final String[] FRAME_PARTS = { "panel" };

    /** The tinted pane. One texture serves every tier, so the tier colour is applied as a tint. */
    private static final String[] GLASS_PARTS = { "glass" };

    private final DankNullItemRenderer dankNullRenderer;

    public DankNullPanelRenderer(final DankNullItemRenderer dankNullRenderer) {
        this.dankNullRenderer = dankNullRenderer;
    }

    @Override
    public boolean handleRenderType(final ItemStack item, final ItemRenderType type) {
        return type == ItemRenderType.ENTITY || type == ItemRenderType.EQUIPPED
            || type == ItemRenderType.EQUIPPED_FIRST_PERSON
            || type == ItemRenderType.INVENTORY;
    }

    @Override
    public boolean shouldUseRenderHelper(final ItemRenderType type, final ItemStack item,
        final ItemRendererHelper helper) {
        return true;
    }

    @Override
    public void renderItem(final ItemRenderType type, final ItemStack stack, final Object... data) {
        if (DankNullStackUtils.isEmpty(stack) || !(stack.getItem() instanceof ItemDankNullPanel)) {
            return;
        }
        final ObjItemModel model = ObjItemModel.get(OBJ_MODEL);
        final DankNullTier tier = ItemDankNull.getTier(stack);

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            // Same compensation as DankNullItemRenderer: Forge pre-translates equipped block geometry by -0.5
            // because vanilla's is 0..1, but ours is already origin-centred.
            if (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
                GL11.glTranslatef(0.5F, 0.5F, 0.5F);
            } else if (type == ItemRenderType.ENTITY) {
                // Same reason as DankNullItemRenderer.ENTITY_LIFT: panels are not ItemBlocks either.
                GL11.glTranslatef(0.0F, 0.25F, 0.0F);
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);

            DankNullItemRenderer.renderObjShell(model, DankNullItemRenderer.OBJ_TEXTURE, FRAME_PARTS);

            final int glass = tier.getHexColor(true);
            GL11.glColor4f((glass >> 16 & 255) / 255.0F, (glass >> 8 & 255) / 255.0F, (glass & 255) / 255.0F, 1.0F);
            GL11.glDepthMask(false);
            // The pane is modelled with no thickness, so its back face is coincident with the front and is dropped
            // on import; disabling culling keeps the remaining single face visible from both sides.
            GL11.glDisable(GL11.GL_CULL_FACE);
            try {
                DankNullItemRenderer.renderObjShell(model, DankNullItemRenderer.OBJ_GLASS_TEXTURE, GLASS_PARTS);
            } finally {
                GL11.glEnable(GL11.GL_CULL_FACE);
                GL11.glDepthMask(true);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            }

            if (stack.hasEffect(0)) {
                // Frame only: gilding the pane as well would double-brighten what is already a tinted overlay.
                dankNullRenderer.renderGlint(stack, model, FRAME_PARTS);
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }
}
