package p455w0rd.danknull.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.model.obj.Face;
import net.minecraftforge.client.model.obj.GroupObject;
import net.minecraftforge.client.model.obj.TextureCoordinate;
import net.minecraftforge.client.model.obj.Vertex;
import net.minecraftforge.client.model.obj.WavefrontObject;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.api.ThreadSafeISBRH;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import p455w0rd.danknull.blocks.BlockDankNullDock;

/**
 * Draws the docking station's body into the chunk mesh.
 *
 * <p>
 * The body is a static, non-animated shape, so it belongs in the chunk mesh rather than in a TESR: it is then
 * built once per block update instead of every frame, and gets the same lighting treatment as any other block.
 * {@link TESRDankNullDock} is left with only the docked /dank/null, which genuinely is per-tile and animated.
 * </p>
 *
 * <p>
 * <b>Why the geometry is walked by hand.</b> Forge's {@code WavefrontObject.renderAll()} drives the Tessellator's
 * own {@code startDrawing}/{@code draw} cycle, which cannot be used here - chunk building has one already open -
 * and its {@code tessellate*} variants emit the model's raw 0..1 UVs, which on the stitched terrain atlas would
 * sample the whole sheet rather than this block's sprite. Both are handled by
 * {@link #emit(Tessellator, WavefrontObject, IIcon, int, double, double, double)}, which remaps every UV through
 * the block's {@link IIcon} and bakes the facing rotation into the vertex positions (a {@code glRotate} is not
 * available mid-chunk either).
 * </p>
 *
 * <p>
 * <b>Thread safety.</b> Angelica builds chunk meshes off the main thread and will only run an ISBRH there if it
 * carries {@link ThreadSafeISBRH} (see {@code AngelicaBlockSafetyRegistry}); without it the dock would silently
 * force its chunks back onto the main thread. This class holds no mutable state, hence {@code perThread = false},
 * and per that annotation's contract the Tessellator is fetched inside the render method - via
 * {@link TessellatorManager#get()}, which returns the calling thread's instance - and never cached in a field.
 * </p>
 */
@ThreadSafeISBRH(perThread = false)
public class DankNullDockRenderer implements ISimpleBlockRenderingHandler {

    /**
     * The facing the model is authored in - its opening is on +X, i.e. east - so emitting with this value applies
     * no rotation. Used by the item renderer, which has no block facing to honour.
     */
    static final int AUTHORED_FACING = 3;

    /** Model path under the mod's assets; shared with the item renderer. */
    static final String BODY_MODEL = "models/danknull_dock.obj";

    private final int renderId;

    DankNullDockRenderer(final int renderId) {
        this.renderId = renderId;
    }

    @Override
    public int getRenderId() {
        return renderId;
    }

    @Override
    public boolean shouldRender3DInInventory(final int modelId) {
        return true;
    }

    /**
     * Not used: the dock's ItemBlock is drawn by {@link DankNullDockItemRenderer}, which also has to place the
     * docked /dank/null above the body.
     */
    @Override
    public void renderInventoryBlock(final Block block, final int metadata, final int modelId,
        final RenderBlocks renderer) {}

    @Override
    public boolean renderWorldBlock(final IBlockAccess world, final int x, final int y, final int z, final Block block,
        final int modelId, final RenderBlocks renderer) {
        final WavefrontObject model = ObjItemModel.get(BODY_MODEL)
            .getWavefront();
        final IIcon icon = block.getBlockTextureFromSide(0);
        if (model == null || icon == null) {
            return false;
        }
        // Fetched here rather than cached: off-thread chunk builds each have their own instance.
        final Tessellator tessellator = TessellatorManager.get();
        // Per-block light level; emit() applies the per-face directional shading on top of it.
        tessellator.setBrightness(block.getMixedBrightnessForBlock(world, x, y, z));
        // The model is centred on the block's footprint, so it is emitted about the centre of x/z.
        emit(tessellator, model, icon, world.getBlockMetadata(x, y, z) & 3, x + 0.5D, y, z + 0.5D);
        return true;
    }

    /**
     * Emits the model into {@code tessellator} about the given origin, rotated for {@code facing} and with UVs
     * remapped onto {@code icon}. The tessellator must already be drawing quads.
     *
     * @param facing the low two metadata bits: 0 = south, 1 = west, 2 = north, 3 = east
     */
    static void emit(final Tessellator tessellator, final WavefrontObject model, final IIcon icon, final int facing,
        final double originX, final double originY, final double originZ) {
        for (final GroupObject group : model.groupObjects) {
            for (final Face face : group.faces) {
                final Vertex[] vertices = face.vertices;
                if (vertices == null || vertices.length < 3) {
                    continue;
                }
                if (face.faceNormal == null) {
                    face.faceNormal = face.calculateFaceNormal();
                }
                final Vertex normal = face.faceNormal;
                final float nx = rotateX(normal.x, normal.z, facing);
                final float nz = rotateZ(normal.x, normal.z, facing);
                tessellator.setNormal(nx, normal.y, nz);
                // Without this every face takes the same colour and the model reads as flat and unlit. Chunk
                // geometry is not lit by GL lighting - RenderBlocks bakes the directional falloff into vertex
                // colour instead, and an ISBRH has to do the same or it stands out against every other block.
                final float shade = faceShade(nx, normal.y, nz);
                tessellator.setColorOpaque_F(shade, shade, shade);

                final TextureCoordinate[] uvs = face.textureCoordinates;
                for (int i = 0; i < 4; i++) {
                    // Chunk building is mid-quad, so a triangle is emitted as a degenerate quad rather than
                    // switching drawing mode, which would corrupt the batch.
                    final int index = Math.min(i, vertices.length - 1);
                    final Vertex vertex = vertices[index];
                    final double vx = originX + rotateX(vertex.x, vertex.z, facing);
                    final double vz = originZ + rotateZ(vertex.x, vertex.z, facing);
                    if (uvs != null && index < uvs.length && uvs[index] != null) {
                        // Forge already stores v flipped (it parses `1 - v`), which is the orientation
                        // getInterpolatedV expects, so no further flip here.
                        tessellator.addVertexWithUV(
                            vx,
                            originY + vertex.y,
                            vz,
                            icon.getInterpolatedU(uvs[index].u * 16.0D),
                            icon.getInterpolatedV(uvs[index].v * 16.0D));
                    } else {
                        tessellator.addVertexWithUV(vx, originY + vertex.y, vz, icon.getMinU(), icon.getMinV());
                    }
                }
            }
        }
    }

    /**
     * Vanilla's per-face brightness multipliers, from {@code RenderBlocks}: full for an up-facing surface, 0.5
     * down, 0.8 along z and 0.6 along x. A face that is not axis-aligned takes the value of whichever axis it
     * leans towards, which is the closest this model gets to vanilla's behaviour without per-vertex AO.
     */
    private static float faceShade(final float nx, final float ny, final float nz) {
        final float ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
        if (ay >= ax && ay >= az) {
            return ny > 0.0F ? 1.0F : 0.5F;
        }
        return az >= ax ? 0.8F : 0.6F;
    }

    /**
     * Rotation about the vertical axis, taking the model's authored orientation to the facing in metadata (which
     * {@link BlockDankNullDock#getFacing(int)} decodes as 0 = south, 1 = west, 2 = north, 3 = east).
     *
     * <p>
     * The model is authored with its opening on <b>+X, i.e. east</b> - its three posts sit on the north, south and
     * west sides - so east is the identity case and the others rotate away from it. Authoring the opening on a
     * different side means changing these two methods, not the metadata.
     * </p>
     */
    private static float rotateX(final float x, final float z, final int facing) {
        switch (facing) {
            case 0:
                return -z;
            case 1:
                return -x;
            case 2:
                return z;
            default:
                return x;
        }
    }

    private static float rotateZ(final float x, final float z, final int facing) {
        switch (facing) {
            case 0:
                return x;
            case 1:
                return -z;
            case 2:
                return -x;
            default:
                return z;
        }
    }
}
