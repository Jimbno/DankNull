package p455w0rd.danknull.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import p455w0rd.danknull.init.ModGlobals;
import p455w0rd.danknull.items.ItemDankNullPanel;
import p455w0rd.danknull.util.DankNullStackUtils;

/**
 * Renders a /dank/null panel as the 3D framed-glass model its JSON describes.
 *
 * <p>
 * Upstream drew panels through its own {@code DankNullPanelRenderer} using the same baked-model machinery as the
 * /dank/null itself. Falling back to a flat {@code IIcon} here would have lost both the geometry and the
 * tier-coloured glint - 1.7.10 draws no glint at all behind a custom {@link IItemRenderer}, so
 * {@code hasEffect} alone achieves nothing. The panel models are structurally identical to the /dank/null's (an
 * opaque {@code #0} frame plus a per-tier {@code #1} glass pane, both already on the block atlas), so this reuses
 * {@link JsonItemModel} and {@link DankNullItemRenderer}'s glint verbatim.
 * </p>
 *
 * <p>
 * Unlike the /dank/null there is nothing to draw inside, so the frame and glass are emitted in a single pass.
 * </p>
 */
public class DankNullPanelRenderer implements IItemRenderer {

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
        final JsonItemModel model = JsonItemModel.get(getModelName((ItemDankNullPanel) stack.getItem()));

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
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TextureMap.locationBlocksTexture);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);

            model.render(-0.5F, -0.5F, -0.5F);

            if (stack.hasEffect(0)) {
                dankNullRenderer.renderGlint(stack, model);
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    /** {@code assets/danknull/models/item/dank_null_panel_&lt;tier&gt;.json}. */
    private static String getModelName(final ItemDankNullPanel item) {
        return ModGlobals.MODID + ":item/"
            + item.getTier()
                .getUnlocalizedNameForPanel();
    }
}
