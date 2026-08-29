package p455w0rd.danknull.inventory;

import java.lang.ref.WeakReference;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import p455w0rd.danknull.init.ModGlobals;

/**
 * A {@link DankNullHandler} bound to the /dank/null ItemStack it was loaded from, writing itself back into that
 * stack's NBT whenever it changes.
 *
 * <p>
 * This takes the place of the 1.12 capability provider: instead of the handler being attached to the stack as a
 * capability and persisted by Forge, it is serialised into the stack's own tag compound under
 * {@link ModGlobals.NBT#DANKNULL_INVENTORY}'s parent tag.
 * </p>
 */
public class StackDankNullHandler extends DankNullHandler {

    /**
     * Weakly held on purpose. The handler cache in {@code DankNullUtils} is a {@code WeakHashMap} keyed on this very
     * stack, so a strong reference here would reach from the map's value back to its own key - and a weak entry
     * whose value reaches its key is never collected. Keeping this weak is what lets that cache actually evict.
     */
    private final WeakReference<ItemStack> container;

    /** The tags this handler owns; everything else on the stack (display name, enchants) is left untouched. */
    private static final String[] OWNED_KEYS = { ModGlobals.NBT.DANKNULL_INVENTORY, ModGlobals.NBT.OREDICT_MODES,
        ModGlobals.NBT.EXTRACTION_MODES, ModGlobals.NBT.PLACEMENT_MODES, ModGlobals.NBT.SELECTEDINDEX,
        ModGlobals.NBT.LOCKED };

    /** Suppresses write-back while {@link DankNullSerializer#read} populates this handler. */
    private boolean loading;

    /** Nesting depth of {@link #beginBatch()}; write-back is deferred while this is positive. */
    private int suspendDepth;

    private boolean dirtyWhileSuspended;

    public StackDankNullHandler(final ModGlobals.DankNullTier tier, final ItemStack container) {
        super(tier);
        this.container = new WeakReference<>(container);
    }

    /** Populates this handler from the container stack without triggering a write-back per mutation. */
    public void load() {
        loading = true;
        try {
            final ItemStack stack = getContainer();
            if (stack != null && stack.hasTagCompound()) {
                DankNullSerializer.read(this, stack.getTagCompound());
            }
        } finally {
            loading = false;
        }
    }

    public ItemStack getContainer() {
        return container.get();
    }

    /**
     * Defers write-back until the matching {@link #endBatch()}. Serialising this handler rewrites the <i>entire</i>
     * inventory, so a bulk operation touching many slots would otherwise pay that cost once per slot. Always pair
     * these in a try/finally.
     */
    public void beginBatch() {
        suspendDepth++;
    }

    public void endBatch() {
        if (suspendDepth > 0 && --suspendDepth == 0 && dirtyWhileSuspended) {
            dirtyWhileSuspended = false;
            flush();
        }
    }

    @Override
    protected final void onDataChanged() {
        if (loading || getContainer() == null) {
            return;
        }
        if (suspendDepth > 0) {
            dirtyWhileSuspended = true;
            return;
        }
        flush();
    }

    private void flush() {
        save();
        afterSave();
    }

    /**
     * Hook for subclasses needing to react to a completed write-back (the docking station marks its tile dirty).
     * Overriding {@link #onDataChanged()} directly would defeat the batching above.
     */
    protected void afterSave() {}

    /** Serialises the current contents back onto the container stack. */
    public void save() {
        final ItemStack stack = getContainer();
        if (stack == null) {
            return;
        }
        final NBTTagCompound serialized = DankNullSerializer.write(this);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        for (final String key : OWNED_KEYS) {
            tag.removeTag(key);
        }
        // No defensive copy: `serialized` was just built here and nothing else holds a reference to it.
        for (final String key : serialized.func_150296_c()) {
            tag.setTag(key, serialized.getTag(key));
        }
    }
}
