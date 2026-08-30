package p455w0rd.danknull.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;

import p455w0rd.danknull.api.IDankNullHandler;
import p455w0rd.danknull.init.ModConfig.Options;
import p455w0rd.danknull.init.ModGlobals;
import p455w0rd.danknull.init.ModGlobals.DankNullTier;
import p455w0rd.danknull.items.ItemDankNull;
import p455w0rd.danknull.util.DankNullStackUtils;
import p455w0rd.danknull.util.DankNullUtils;

/**
 * Renders the /dank/null itself.
 *
 * <p>
 * 1.12 wrapped the item's baked model in p455w0rdslib's {@code ItemLayerWrapper} and drew it from a
 * {@code TileEntityItemStackRenderer}. Neither exists here, so the model is a Blockbench OBJ loaded through
 * Forge's own {@code AdvancedModelLoader} and drawn from a plain 1.7.10 {@link IItemRenderer} - an opaque frame
 * pass, the contained stack, then a per-tier tinted glass pass.
 * </p>
 *
 * <p>
 * Geometry is emitted centred on the origin, which is the
 * convention every vanilla "block as item" path is calibrated for. {@code shouldUseRenderHelper} therefore returns
 * {@code true} throughout so Forge applies its block-shaped helper transforms, and the single case where those
 * assume 0..1 instead ({@code EQUIPPED_BLOCK} pre-translates by -0.5) is compensated in
 * {@link #renderItem(ItemRenderType, ItemStack, Object...)}.
 * </p>
 */
public class DankNullItemRenderer implements IItemRenderer {

    /** How far the contained-item overlay is inset inside the frame. */
    private static final float CONTAINED_SCALE = 0.4F;

    /** Height of the contained stack above the frame centre; see renderContainedStack for where these come from. */
    private static final float CONTAINED_Y = 0.06F;

    private static final float CONTAINED_Y_FIRST_PERSON = 0.30F;

    /**
     * Lift applied to a dropped /dank/null so it does not sink into the ground.
     *
     * <p>
     * {@code ForgeHooksClient.renderEntityItem} picks the dropped-item scale from the block behind the item:
     * {@code renderType = block != null ? block.getRenderType() : 1}, and {@code renderType == 1} selects 0.5
     * rather than the 0.25 a normal dropped block gets. A /dank/null is not an ItemBlock, so it lands on that
     * default and is drawn at double size, still centred on the entity - putting half the model below ground.
     * This raises it so it breaks the surface by the same amount a vanilla dropped block does. The value is in
     * the already-scaled local space, so it is worth 0.125 blocks in the world.
     */
    private static final float ENTITY_LIFT = 0.25F;

    /** OBJ shell, loaded via Forge's own AdvancedModelLoader - no GTNHLib needed for this path. */
    private static final String OBJ_MODEL = "models/dank_null.obj";

    /** Shared with {@link DankNullPanelRenderer}. */
    static final ResourceLocation OBJ_TEXTURE = new ResourceLocation(ModGlobals.MODID, "textures/models/dank_null.png");

    /** Height of the OBJ in Blockbench pixels; it is modelled y 0..OBJ_PIXELS/16 rather than origin-centred. */
    private static final float OBJ_PIXELS = 12.0F;

    /**
     * Scales the OBJ up so it occupies a full block.
     *
     * <p>
     * Every transform around it - Forge's block-shaped render helpers, the equipped-item compensation, the dropped
     * -item lift, and the contained stack's own offsets - is calibrated for a one-block model, which is what the
     * JSON shell was. The OBJ is modelled 12px, so without this it renders at three quarters the size and
     * everything else sits wrong against it. Set this to 1 to draw the model at its authored size instead.
     * </p>
     */
    private static final float OBJ_SCALE = 16.0F / OBJ_PIXELS;

    private static final float OBJ_HALF_HEIGHT = OBJ_PIXELS / 32.0F;

    /** Shared with {@link DankNullPanelRenderer}: panels are the same framed-glass shape on the same textures. */
    static final ResourceLocation OBJ_GLASS_TEXTURE = new ResourceLocation(
        ModGlobals.MODID,
        "textures/models/dank_null_glass.png");

    private static final String[] OBJ_FRAME_PARTS = { "cube", "pillars", "SIDE_CUT", "SIDE_CUT_2", "dankerino" };

    /** Drawn last with depth writes off and tinted per tier - one greyscale sheet serves all seven tiers. */
    private static final String[] OBJ_GLASS_PARTS = { "glass" };

    /** Extrusion depth of a flat icon, matching vanilla's held-item thickness. */
    private static final float ICON_THICKNESS = 0.0625F;

    /**
     * Degrees per second the contained item spins. 1.12 read {@code ModGlobals.TIME}, which is advanced by a player
     * tick handler; this renderer derives the angle from the wall clock instead so it keeps spinning (at upstream's
     * 0.75 degrees per tick) regardless of who ticks that field.
     */
    private static final float SPIN_DEGREES_PER_SECOND = 15.0F;

    private static final ResourceLocation GLINT_TEXTURE = new ResourceLocation(
        "textures/misc/enchanted_item_glint.png");

    /**
     * How far the glint sheet is stretched across the model, in atlas-UV units.
     *
     * <p>
     * Vanilla's glint passes re-draw a <em>fresh</em> quad with UVs 0..1 and shrink them with a 0.125 texture
     * matrix. There is no fresh quad here - the glint is the model's own geometry re-drawn - so the UVs coming in
     * are block-atlas coordinates, and upstream's {@code GlintEffectRenderer} scale of 8 is the equivalent: it
     * blows the sliver of atlas the model occupies up to a usable fraction of the glint sheet.
     * </p>
     */
    private static final float GLINT_TEXTURE_SCALE = 0.125F;

    /** Vanilla's {@code f7}: the glint colour is dimmed to 76% before being drawn additively. */
    private static final float GLINT_DIM = 0.76F;

    /**
     * Upstream {@code GlintEffectRenderer.apply}'s per-tier glint colours, indexed by {@link DankNullTier#ordinal()},
     * kept as the literal constants it used. These are the muted colours; {@code Options.superShine} swaps them for
     * the tier's real (much brighter) hex colour - see {@link #renderGlint}.
     */
    //@formatter:off
    private static final int[] GLINT_COLORS = new int[] {
            -10092544,  // REDSTONE
            -16777114,  // LAPIS
            -10066330,  // IRON
            -10066432,  // GOLD
            -12097946,  // DIAMOND
            -16751104,  // EMERALD
            0xFF8F15D4, // CREATIVE
            0xFF0000FF  // NONE
    };
    //@formatter:on

    /** Upstream's fallback colour for anything that is not a recognised tier. */
    private static final int GLINT_COLOR_DEFAULT = -8372020;

    /**
     * Guards against a /dank/null that contains a /dank/null recursing through the generic item renderer. Render
     * happens on the client thread only, so a plain field is sufficient.
     */
    private static boolean renderingContained = false;

    /** Only ever touched from the render thread; vanilla's {@code ItemRenderer} keeps one the same way. */
    private final RenderBlocks renderBlocks = new RenderBlocks();

    @Override
    public boolean handleRenderType(final ItemStack item, final ItemRenderType type) {
        return type == ItemRenderType.ENTITY || type == ItemRenderType.EQUIPPED
            || type == ItemRenderType.EQUIPPED_FIRST_PERSON
            || type == ItemRenderType.INVENTORY;
    }

    @Override
    public boolean shouldUseRenderHelper(final ItemRenderType type, final ItemStack item,
        final ItemRendererHelper helper) {
        // Treat the /dank/null as a 3D block in every context; see the class javadoc.
        return true;
    }

    @Override
    public void renderItem(final ItemRenderType type, final ItemStack stack, final Object... data) {
        GL11.glPushMatrix();
        try {
            // ForgeHooksClient.renderEquippedItem translates by (-0.5, -0.5, -0.5) before handing over, because
            // vanilla's equipped-block geometry is 0..1. Ours is already centred, so undo it.
            if (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
                GL11.glTranslatef(0.5F, 0.5F, 0.5F);
            } else if (type == ItemRenderType.ENTITY) {
                GL11.glTranslatef(0.0F, ENTITY_LIFT, 0.0F);
            }
            renderDankNull(stack, type == ItemRenderType.EQUIPPED_FIRST_PERSON);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * Draws part of the OBJ shell, scaled to a full block and centred on the origin.
     *
     * <p>
     * Binds the model's texture every time rather than once per draw: the contained stack is rendered between the
     * frame and glass passes and binds the block atlas for itself, so a single bind up front would leave the glass
     * sampling whatever sprite happened to be there.
     * </p>
     */
    static void renderObjShell(final ObjItemModel model, final ResourceLocation texture, final String... parts) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);
        GL11.glPushMatrix();
        try {
            GL11.glScalef(OBJ_SCALE, OBJ_SCALE, OBJ_SCALE);
            GL11.glTranslatef(0.0F, -OBJ_HALF_HEIGHT, 0.0F);
            model.renderOnly(parts);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * Draws the /dank/null centred on the origin, one block wide: the selected stack floating inside, then the frame
     * and its glass over it, then the glint over that.
     *
     * <p>
     * Split out of {@link #renderItem} so {@link DankNullDockItemRenderer} can place a docked /dank/null itself
     * without inheriting the per-render-type compensations, which it applies in its own way.
     * </p>
     */
    void renderDankNull(final ItemStack stack) {
        renderDankNull(stack, false);
    }

    void renderDankNull(final ItemStack stack, final boolean firstPerson) {
        if (DankNullStackUtils.isEmpty(stack) || !(stack.getItem() instanceof ItemDankNull)) {
            return;
        }
        final ObjItemModel model = ObjItemModel.get(OBJ_MODEL);
        final DankNullTier tier = ItemDankNull.getTier(stack);

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(OBJ_TEXTURE);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);

            // Three passes, so the contained stack sits inside the box rather than in front of it.
            // 1. the opaque frame, writing depth, so its bars correctly hide whatever is behind them;
            // 2. the contained stack, depth-tested against that frame;
            // 3. the glass, depth-tested but with depth writes off, so it tints the stack instead of hiding it.
            // The shell passes are wrapped so their scaling does not reach the contained stack, which positions
            // itself in unscaled, origin-centred space.
            renderObjShell(model, OBJ_TEXTURE, OBJ_FRAME_PARTS);

            renderContainedStack(stack, firstPerson);

            // One texture serves every tier here, unlike the JSON models' per-tier glass sprite, so the tier
            // colour is applied as a tint on the glass parts instead.
            if (OBJ_GLASS_PARTS.length > 0) {
                final int glass = tier.getHexColor(true);
                GL11.glColor4f((glass >> 16 & 255) / 255.0F, (glass >> 8 & 255) / 255.0F, (glass & 255) / 255.0F, 1.0F);
                GL11.glDepthMask(false);
                // The panes are modelled with no thickness, so their back faces are coincident with the front and
                // are dropped on import; disabling culling keeps the remaining single face visible from both sides.
                GL11.glDisable(GL11.GL_CULL_FACE);
                try {
                    renderObjShell(model, OBJ_GLASS_TEXTURE, OBJ_GLASS_PARTS);
                } finally {
                    GL11.glEnable(GL11.GL_CULL_FACE);
                    GL11.glDepthMask(true);
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                }
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    /**
     * Draws the currently selected stack floating inside the frame, as 1.12 did.
     *
     * <p>
     * 1.12 handed the contained stack to the generic item renderer with {@code TransformType.NONE}. There is no
     * equivalent on 1.7.10 - {@code ItemRenderer.renderItem} always applies the held-item transform - so the two
     * vanilla cases are reproduced directly instead: 3D blocks through {@code renderBlockAsItem} (already centred on
     * the origin) and everything else as the extruded icon quad, re-centred by hand. An item with its own
     * {@code IItemRenderer} therefore shows as its flat icon here rather than its custom geometry.
     * </p>
     *
     * <p>
     * This used to be skipped for {@code INVENTORY} on the grounds that Forge's {@code INVENTORY_BLOCK} helper
     * ({@code ForgeHooksClient.renderInventoryItem}) renders inside a mirrored {@code glScalef(1, 1, -1)}. That
     * mirror is real but it is not a net mirror: the GUI projection is
     * {@code glOrtho(0, width, height, 0, 1000, 3000)} ({@code EntityRenderer.setupOverlayRendering}), whose
     * {@code bottom > top} flips Y, and the two negatives cancel. The GUI hands us the same handedness the world
     * does - which is exactly why vanilla's own {@code renderBlockAsItem} geometry is not inside-out in a slot - so
     * nested item rendering needs no compensation and the contained stack is drawn the same way in every render
     * type.
     * </p>
     */
    private void renderContainedStack(final ItemStack dankNull, final boolean firstPerson) {
        if (renderingContained) {
            return;
        }
        final IDankNullHandler handler = DankNullUtils.getHandler(dankNull);
        if (handler == null || handler.getSelected() < 0) {
            return;
        }
        final ItemStack contained = handler.getRenderableStackForSlot(handler.getSelected());
        if (DankNullStackUtils.isEmpty(contained)) {
            return;
        }
        final TextureManager textureManager = Minecraft.getMinecraft()
            .getTextureManager();

        renderingContained = true;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        try {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            // Depth testing stays ON: the frame pass has already written depth, so the box's opaque bars occlude
            // this correctly and it reads as being inside the cage.
            // Upstream scaled by 0.4 and then translated by (0.75, 1.5, 0.75) in first person or (0.75, 0.9, 0.75)
            // otherwise. Its model space had the origin at a corner, so those put the block's centre at y 0.8 and
            // 0.56 of a block, i.e. +0.30 and +0.06 once expressed in our origin-centred space. The non-first-person
            // offset is that derived value; the first-person one was then raised by hand to 0.50, which sits better
            // against 1.7.10's equipped-item transform than a direct translation of upstream's number did.
            GL11.glTranslatef(0.0F, firstPerson ? CONTAINED_Y_FIRST_PERSON : CONTAINED_Y, 0.0F);
            GL11.glScalef(CONTAINED_SCALE, CONTAINED_SCALE, CONTAINED_SCALE);
            // Upstream tumbles the block about (1, 1, 1) rather than spinning it about Y.
            GL11.glRotatef(getSpinAngle(), 1.0F, 1.0F, 1.0F);

            final Block block = Block.getBlockFromItem(contained.getItem());
            if (block != null && block != Blocks.air
                && contained.getItemSpriteNumber() == 0
                && RenderBlocks.renderItemIn3d(block.getRenderType())) {
                textureManager.bindTexture(TextureMap.locationBlocksTexture);
                renderBlocks.renderBlockAsItem(block, contained.getItemDamage(), 1.0F);
            } else {
                final IIcon icon = contained.getIconIndex();
                if (icon != null) {
                    textureManager.bindTexture(
                        contained.getItemSpriteNumber() == 0 ? TextureMap.locationBlocksTexture
                            : TextureMap.locationItemsTexture);
                    GL11.glTranslatef(-0.5F, -0.5F, ICON_THICKNESS * 0.5F);
                    ItemRenderer.renderItemIn2D(
                        TessellatorManager.get(),
                        icon.getMaxU(),
                        icon.getMinV(),
                        icon.getMinU(),
                        icon.getMaxV(),
                        icon.getIconWidth(),
                        icon.getIconHeight(),
                        ICON_THICKNESS);
                }
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
            renderingContained = false;
        }
    }

    /**
     * The enchantment glint, hand-rolled.
     *
     * <p>
     * 1.7.10 draws no glint of its own behind a custom {@code IItemRenderer}. Every GUI item goes through
     * {@code RenderItem.renderItemAndEffectIntoGUI}, whose own glint block is dead code - it is guarded by
     * {@code if (false &amp;&amp; p_82406_3_.hasEffect())}, Forge's "Bugfix, Move this to a per-render pass, modders
     * must handle themselves". Its other glint call sites ({@code renderItemIntoGUI}'s {@code renderEffect}, and the
     * flat-icon branches of {@code RenderItem.renderItem} and {@code ItemRenderer.renderItem}) are all downstream of
     * the point where {@code ForgeHooksClient.renderInventoryItem}/{@code renderEquippedItem} hands rendering to us,
     * so none of them run either. Hence this.
     * </p>
     *
     * <p>
     * The GL state and the UV animation are vanilla's, taken from {@code ItemRenderer.renderItem}'s glint block (the
     * 3D one; {@code RenderItem.renderEffect} is the flat GUI variant of the same thing): additive
     * {@code SRC_COLOR, ONE} blending, {@code GL_EQUAL} depth so the glint only lands on pixels the model just wrote,
     * and two texture-matrix passes scrolling in opposite directions at one texture unit per 3000 ms and per 4873 ms,
     * rotated -50 and +10 degrees. The one difference is that the glint is painted onto the model's own geometry
     * rather than a fresh 0..1 quad (see {@link #GLINT_TEXTURE_SCALE}), which is what upstream's
     * {@code GlintEffectRenderer} did too.
     * </p>
     *
     * <p>
     * {@code Options.superShine} is upstream's <em>intensity</em> switch, not an on/off switch: 1.12's
     * {@code DankNullRenderer} (and {@code DankNullPanelRenderer}) drew a glint whenever the stack had an effect, and
     * used {@code superShine} only to pick between {@code GlintEffectRenderer.apply} (the muted per-tier constants in
     * {@link #GLINT_COLORS}) and {@code apply2} (the tier's real hex colour). That reading is kept: superShine on
     * means the tier colour at full strength, superShine off means upstream's dimmer constants, dimmed again by
     * vanilla's 0.76 factor.
     * </p>
     *
     * <p>
     * Depth function, depth mask, matrix mode and the current colour are not covered by the {@code GL_ENABLE_BIT} /
     * {@code GL_COLOR_BUFFER_BIT} attribute push in {@link #renderDankNull}, so all four are restored by hand.
     * </p>
     */
    void renderGlint(final ItemStack stack, final ObjItemModel model, final String... parts) {
        final DankNullTier tier = ItemDankNull.getTier(stack);
        final int color;
        final float intensity;
        if (Options.superShine) {
            color = tier.getHexColor(false);
            intensity = 1.0F;
        } else {
            color = tier.ordinal() < GLINT_COLORS.length ? GLINT_COLORS[tier.ordinal()] : GLINT_COLOR_DEFAULT;
            intensity = GLINT_DIM;
        }
        final float red = (color >> 16 & 255) / 255.0F * intensity;
        final float green = (color >> 8 & 255) / 255.0F * intensity;
        final float blue = (color & 255) / 255.0F * intensity;
        final TextureManager textureManager = Minecraft.getMinecraft()
            .getTextureManager();

        textureManager.bindTexture(GLINT_TEXTURE);
        GL11.glDepthFunc(GL11.GL_EQUAL);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glColor4f(red, green, blue, 1.0F);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        try {
            glintPass(model, scroll(3000L), -50.0F, parts);
            glintPass(model, -scroll(4873L), 10.0F, parts);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GL11.glDepthMask(true);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            textureManager.bindTexture(TextureMap.locationBlocksTexture);
        }
    }

    /**
     * One glint pass. The texture matrix is already the current matrix; {@code offset} is the net scroll in texture
     * units, and is divided back out by the scale because {@code glScalef} then {@code glTranslatef} multiplies the
     * translation by the scale. Vanilla's 0.125 scale plus an 8x offset works out to the same one-unit-per-period
     * scroll.
     */
    private void glintPass(final ObjItemModel model, final float offset, final float rotation, final String... parts) {
        GL11.glPushMatrix();
        try {
            GL11.glScalef(GLINT_TEXTURE_SCALE, GLINT_TEXTURE_SCALE, GLINT_TEXTURE_SCALE);
            GL11.glTranslatef(offset / GLINT_TEXTURE_SCALE, 0.0F, 0.0F);
            GL11.glRotatef(rotation, 0.0F, 0.0F, 1.0F);
            // The glint texture is already bound and the texture matrix is live, so this only has to re-emit the
            // same geometry; the model's own UVs then sample the glint sheet. The modelview transform has to match
            // renderObjShell's exactly or the overlay would not sit on the surface it is meant to gild.
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            try {
                GL11.glScalef(OBJ_SCALE, OBJ_SCALE, OBJ_SCALE);
                GL11.glTranslatef(0.0F, -OBJ_HALF_HEIGHT, 0.0F);
                model.renderOnly(parts);
            } finally {
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_TEXTURE);
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** Vanilla's glint scroll: one full texture unit per {@code period} milliseconds. */
    private static float scroll(final long period) {
        return Minecraft.getSystemTime() % period / (float) period;
    }

    private static float getSpinAngle() {
        final long period = (long) (360.0F / SPIN_DEGREES_PER_SECOND * 1000.0F);
        return System.currentTimeMillis() % period * 360.0F / period;
    }

    /** {@code assets/danknull/models/item/dank_null_&lt;tier&gt;.json}. */
    private static String getModelName(final ItemDankNull item) {
        return ModGlobals.MODID + ":item/"
            + item.getTier()
                .getUnlocalizedNameForDankNull();
    }
}
