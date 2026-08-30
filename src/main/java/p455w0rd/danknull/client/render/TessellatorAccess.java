package p455w0rd.danknull.client.render;

import net.minecraft.client.renderer.Tessellator;

/**
 * Hands back the Tessellator the calling thread should draw into.
 *
 * <p>
 * The mod has no hard dependency on GTNHLib, but it must still be correct when GTNHLib is present. Angelica
 * builds chunk meshes off the main thread, and it is allowed to do that for a block whose
 * {@code ISimpleBlockRenderingHandler} carries {@code @ThreadSafeISBRH} - as {@link DankNullDockRenderer} does.
 * Off that thread, vanilla's single static {@link Tessellator#instance} is the wrong object: several threads
 * would interleave vertices into one buffer. GTNHLib's {@code TessellatorManager.get()} returns a per-thread
 * instance and is what has to be used there.
 * </p>
 *
 * <p>
 * Angelica depends on GTNHLib, so the two are always present together: if GTNHLib is missing then Angelica is
 * too, nothing builds chunks off-thread, and the vanilla singleton is both correct and faster to reach.
 * </p>
 *
 * <p>
 * <b>Why the indirection.</b> {@code TessellatorManager} is compiled against but may be absent at runtime, so
 * no field, signature or executed instruction here may mention it. The reference is confined to
 * {@link Threaded}, a class that is only ever loaded from inside the branch that has already proven GTNHLib is
 * loadable - so on a standalone install its bytecode is never resolved and no {@code NoClassDefFoundError} can
 * be thrown.
 * </p>
 */
final class TessellatorAccess {

    /** True once GTNHLib's TessellatorManager has been shown to be present. */
    private static final boolean THREAD_LOCAL_AVAILABLE = detect();

    private TessellatorAccess() {}

    private static boolean detect() {
        try {
            Class.forName(
                "com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager",
                false,
                TessellatorAccess.class.getClassLoader());
            return true;
        } catch (final ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /**
     * @return the Tessellator for the calling thread; never cached by callers, since an off-thread chunk build
     *         gets a different instance from the main thread.
     */
    static Tessellator get() {
        return THREAD_LOCAL_AVAILABLE ? Threaded.get() : Tessellator.instance;
    }

    /** Isolates the GTNHLib reference so it is only linked when GTNHLib is actually installed. */
    private static final class Threaded {

        private Threaded() {}

        static Tessellator get() {
            return com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager.get();
        }
    }
}
