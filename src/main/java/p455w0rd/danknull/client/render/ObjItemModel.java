package p455w0rd.danknull.client.render;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;
import net.minecraftforge.client.model.obj.WavefrontObject;

import p455w0rd.danknull.DankNull;

/**
 * A Wavefront OBJ model, loaded through Forge's own {@link AdvancedModelLoader}.
 *
 * <p>
 * This needs no third-party library: OBJ support is built into Forge 1.7.10. That
 * matters for packs outside GTNH, where GTNHLib is not just another jar but a coremod pulling in GTNHExtLib and a
 * mixin provider.
 * </p>
 *
 * <p>
 * Two things Forge's parser is strict about, both of which Blockbench exports violate, so models are normalised on
 * the way into {@code assets/danknull/models/}:
 * </p>
 * <ul>
 * <li>Group/object names must match {@code [\w\d.]+} - a name containing a space throws
 * {@code ModelFormatException} at load.</li>
 * <li>Every {@code v}/{@code vn}/{@code vt} component needs an explicit decimal point. An integer-valued texture
 * coordinate is <em>silently dropped</em>, which then shifts every later UV index and garbles the texture with no
 * error at all.</li>
 * </ul>
 *
 * <p>
 * Unlike the JSON path there is no texture atlas: the caller binds the model's own texture, so UVs outside 0..1
 * tile against it rather than bleeding into a neighbouring sprite.
 * </p>
 */
final class ObjItemModel {

    private static final Map<String, ObjItemModel> CACHE = new HashMap<String, ObjItemModel>();

    private final ResourceLocation location;
    private IModelCustom model;
    private boolean failed;

    private ObjItemModel(final ResourceLocation location) {
        this.location = location;
    }

    /**
     * @param path model path under the mod's assets, e.g. {@code "models/dank_null.obj"}
     */
    static ObjItemModel get(final String path) {
        ObjItemModel cached = CACHE.get(path);
        if (cached == null) {
            cached = new ObjItemModel(new ResourceLocation(p455w0rd.danknull.init.ModGlobals.MODID, path));
            CACHE.put(path, cached);
        }
        return cached;
    }

    /** Drops every loaded model so a resource reload re-reads them. */
    static void flush() {
        for (final ObjItemModel entry : CACHE.values()) {
            entry.model = null;
            entry.failed = false;
        }
    }

    private IModelCustom get() {
        if (model == null && !failed) {
            try {
                model = AdvancedModelLoader.loadModel(location);
            } catch (final Throwable t) {
                // Load once and stay failed: retrying every frame would spam the log at 60Hz.
                failed = true;
                DankNull.LOGGER.error("Could not load OBJ model {}", location, t);
            }
        }
        return model;
    }

    /** Renders only the named objects; names are matched case-insensitively and may repeat in the file. */
    void renderOnly(final String... groupNames) {
        final IModelCustom loaded = get();
        if (loaded != null) {
            loaded.renderOnly(groupNames);
        }
    }

    /**
     * The parsed model, or {@code null} if it could not be loaded. Exposed so the dock can walk the geometry and
     * emit it into a chunk tessellator itself; {@link #renderAll()} cannot be used there because it drives the
     * Tessellator start/draw cycle, which is already open during chunk building.
     */
    WavefrontObject getWavefront() {
        final IModelCustom loaded = get();
        return loaded instanceof WavefrontObject ? (WavefrontObject) loaded : null;
    }

    void renderAll() {
        final IModelCustom loaded = get();
        if (loaded != null) {
            loaded.renderAll();
        }
    }
}
