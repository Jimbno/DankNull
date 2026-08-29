package p455w0rd.danknull.client.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;

import com.gtnewhorizon.gtnhlib.blockstate.core.BlockState;
import com.gtnewhorizon.gtnhlib.client.model.BakedModelQuadContext;
import com.gtnewhorizon.gtnhlib.client.model.baked.BakedModel;
import com.gtnewhorizon.gtnhlib.client.model.loading.ModelRegistry;
import com.gtnewhorizon.gtnhlib.client.model.loading.ResourceLoc;
import com.gtnewhorizon.gtnhlib.client.model.unbaked.JSONModel;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.api.util.NormI8;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadView;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadViewMutable;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing;

/**
 * Draws one of the 1.12-era JSON models from {@code assets/danknull/models/item/} as real 3D geometry on 1.7.10.
 *
 * <p>
 * GTNHLib's JSON model pipeline only auto-wires <em>blocks</em> ({@code ModelRegistry}'s reload listener iterates
 * the block registry and {@code ModelISBRH.renderItem} bails out on anything that is not an {@code ItemBlock}), so
 * plain items have to drive the baker by hand: {@link ModelRegistry#getJSONModel} to load and resolve the model,
 * {@link JSONModel#bake()} to turn it into quads, and then a minimal {@link BakedModelQuadContext} to pull those
 * quads back out. {@code ItemContext} must <b>not</b> be used - it casts the stack's item to {@code ItemBlock}.
 * </p>
 *
 * <p>
 * Consequences of that path, all of which the callers depend on:
 * </p>
 * <ul>
 * <li>Every texture the model names must live on the <b>block</b> atlas, because {@code JSONModel.bakeSprite}
 * hardcodes {@code getTextureMapBlocks()}. The /dank/null textures were moved to
 * {@code assets/danknull/textures/blocks/danknull/} for exactly this reason; callers must bind
 * {@code TextureMap.locationBlocksTexture} before drawing.</li>
 * <li>Baking resolves {@link net.minecraft.util.IIcon}s, so the baked model is only valid for one atlas generation.
 * {@link #flush()} is called from {@link ModRenderers} on {@code TextureStitchEvent.Post}.</li>
 * </ul>
 */
final class JsonItemModel {

    private static final Map<String, JsonItemModel> CACHE = new HashMap<String, JsonItemModel>();

    private final ResourceLoc.ModelLoc location;
    private BakedModel baked;

    private JsonItemModel(final ResourceLoc.ModelLoc location) {
        this.location = location;
    }

    /**
     * @param model a 1.12-style model id, e.g. {@code "danknull:item/dank_null_0"}
     */
    static JsonItemModel get(final String model) {
        JsonItemModel cached = CACHE.get(model);
        if (cached == null) {
            cached = new JsonItemModel(ResourceLoc.ModelLoc.fromStr(model));
            CACHE.put(model, cached);
        }
        return cached;
    }

    /** Drops every baked model; the icons they hold do not survive an atlas re-stitch. */
    static void flush() {
        for (final JsonItemModel model : CACHE.values()) {
            model.baked = null;
        }
    }

    private BakedModel getBaked() {
        if (baked == null) {
            final JSONModel json = ModelRegistry.getJSONModel(location);
            if (json == null) {
                return null;
            }
            baked = json.bake();
        }
        return baked;
    }

    /**
     * Pushes this model's quads into the tessellator and draws them. The model's own coordinates are the usual JSON
     * 0..1 cube; the offset is applied on top of that, so passing {@code -0.5F} on every axis produces the
     * origin-centred geometry that vanilla's block-as-item paths expect.
     *
     * <p>
     * No colour is emitted - the quads carry normals instead and are shaded by GL lighting, exactly like
     * {@code RenderBlocks.renderBlockAsItem}. The caller owns blend/alpha/texture state.
     * </p>
     */
    void render(final float offsetX, final float offsetY, final float offsetZ) {
        render(offsetX, offsetY, offsetZ, Pass.ALL);
    }

    /**
     * Which faces of the /dank/null shell to emit.
     *
     * <p>
     * The shell is one baked model but two materially different surfaces: the opaque frame ({@code #0} in the JSON)
     * and the translucent glass ({@code #1}). They have to be drawn in separate passes so the contained stack can
     * sit <i>inside</i> the box - frame first so it occludes the stack, then the stack, then the glass with depth
     * writes disabled so it tints the stack rather than hiding it.
     * </p>
     */
    enum Pass {
        ALL,
        FRAME,
        GLASS
    }

    /** Identifies the glass faces by sprite; the frame and glass are the model's only two textures. */
    private static boolean isGlass(final ModelQuadView quad) {
        final Object sprite = quad.celeritas$getSprite();
        return sprite instanceof IIcon && ((IIcon) sprite).getIconName()
            .contains("glass_");
    }

    void render(final float offsetX, final float offsetY, final float offsetZ, final Pass pass) {
        render(offsetX, offsetY, offsetZ, pass, false);
    }

    /**
     * @param positionUVs derive texture coordinates from each vertex's model-space position instead of using the
     *                    baked block-atlas coordinates. Needed by the glint pass: the bound texture there is the
     *                    glint sheet, not the atlas, so the baked UVs point at an unrelated sliver of atlas space
     *                    and the pattern restarts at every texture boundary. Positions are shared between adjacent
     *                    faces, so deriving from them makes the sheet flow across the model without seams.
     */
    void render(final float offsetX, final float offsetY, final float offsetZ, final Pass pass,
        final boolean positionUVs) {
        final BakedModel model = getBaked();
        if (model == null) {
            return;
        }
        // TessellatorManager rather than Tessellator.instance, per the Angelica integration rules; on the main
        // thread (which is the only place item rendering happens) it hands back the vanilla instance anyway.
        final Tessellator tessellator = TessellatorManager.get();
        final QuadContext context = new QuadContext();

        tessellator.startDrawingQuads();
        for (final ModelQuadFacing facing : ModelQuadFacing.VALUES) {
            context.quadFacing = facing;
            final List<ModelQuadView> quads = model.getQuads(context);
            if (quads.isEmpty()) {
                continue;
            }
            for (int q = 0; q < quads.size(); q++) {
                final ModelQuadView quad = quads.get(q);
                if (pass != Pass.ALL && isGlass(quad) != (pass == Pass.GLASS)) {
                    continue;
                }
                final int normal = quad.getComputedFaceNormal();
                tessellator.setNormal(NormI8.unpackX(normal), NormI8.unpackY(normal), NormI8.unpackZ(normal));
                for (int i = 0; i < 4; i++) {
                    final float x = quad.getX(i);
                    final float y = quad.getY(i);
                    final float z = quad.getZ(i);
                    tessellator.addVertexWithUV(
                        x + offsetX,
                        y + offsetY,
                        z + offsetZ,
                        positionUVs ? x + z : quad.getTexU(i),
                        positionUVs ? y + z : quad.getTexV(i));
                }
            }
        }
        tessellator.draw();
    }

    /**
     * The smallest context {@code PileOfQuads} will accept: it only ever reads {@link #getQuadFacing()}.
     * {@link #getBlockState()} is deliberately {@code null} - there is no block behind a /dank/null.
     */
    private static final class QuadContext implements BakedModelQuadContext {

        private final Random random = new Random();
        private ModelQuadFacing quadFacing = ModelQuadFacing.UNASSIGNED;

        @Override
        public BlockState getBlockState() {
            return null;
        }

        @Override
        public ModelQuadFacing getQuadFacing() {
            return quadFacing;
        }

        @Override
        public Random getRandom() {
            return random;
        }

        @Override
        public Supplier<ModelQuadViewMutable> getQuadPool() {
            return null;
        }
    }
}
