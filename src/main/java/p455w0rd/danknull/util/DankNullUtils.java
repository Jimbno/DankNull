package p455w0rd.danknull.util;

import static p455w0rd.danknull.util.DankNullStackUtils.isEmpty;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import p455w0rd.danknull.api.IDankNullHandler;
import p455w0rd.danknull.init.ModGlobals.DankNullTier;
import p455w0rd.danknull.inventory.StackDankNullHandler;
import p455w0rd.danknull.items.ItemDankNull;

/**
 * Central access point for a /dank/null's inventory handler.
 *
 * <p>
 * Everywhere the 1.12 source called {@code stack.getCapability(CapabilityDankNull.DANK_NULL_CAPABILITY, null)},
 * this backport calls {@link #getHandler(ItemStack)} instead. The handler is deserialised from the stack's NBT on
 * demand and writes itself back on every mutation, so callers can treat the returned object exactly like the
 * capability instance they replaced - with the one difference that it may be {@code null} for a non-/dank/null
 * stack.
 * </p>
 */
public class DankNullUtils {

    private DankNullUtils() {}

    public static boolean isDankNull(final ItemStack stack) {
        return !isEmpty(stack) && stack.getItem() instanceof ItemDankNull;
    }

    public static DankNullTier getTier(final ItemStack stack) {
        if (!isDankNull(stack)) {
            return DankNullTier.NONE;
        }
        return ((ItemDankNull) stack.getItem()).getTier();
    }

    /**
     * One handler per /dank/null ItemStack, keyed by object identity.
     *
     * <p>
     * This cache is not an optimisation, it is required for correctness. A {@link StackDankNullHandler} holds the
     * whole inventory in memory and writes <i>all</i> of it back to the stack's NBT on every mutation. If callers
     * each got their own instance, two handlers over the same /dank/null would each hold a snapshot taken at
     * different times, and the last one to save would silently overwrite the other's changes - e.g. an item picked
     * up while the GUI is open would be erased the next time the open container wrote itself out.
     * </p>
     *
     * <p>
     * 1.7.10's {@code ItemStack} does not override {@code equals}/{@code hashCode}, so the weak map keys on object
     * identity, which is exactly the intent: one handler per live stack, collected as soon as the stack is.
     * Client and server hold distinct ItemStack objects, so the two sides never share an entry.
     * </p>
     */
    private static final Map<ItemStack, StackDankNullHandler> HANDLERS = Collections
        .synchronizedMap(new WeakHashMap<ItemStack, StackDankNullHandler>());

    /**
     * Returns the handler for the given /dank/null stack, creating it on first use.
     *
     * @return the handler, or {@code null} if the stack is not a /dank/null
     */
    public static IDankNullHandler getHandler(final ItemStack stack) {
        if (!isDankNull(stack)) {
            return null;
        }
        synchronized (HANDLERS) {
            StackDankNullHandler handler = HANDLERS.get(stack);
            if (handler == null) {
                handler = new StackDankNullHandler(getTier(stack), stack);
                handler.load();
                HANDLERS.put(stack, handler);
            }
            return handler;
        }
    }

    /**
     * Defers the handler's NBT write-back until the matching {@link #endBatch}. Every mutation otherwise
     * re-serialises the whole inventory, so a bulk operation touching many slots pays that cost once per slot.
     * Always pair these in a try/finally. No-ops for handlers that are not stack-backed.
     */
    public static void beginBatch(final IDankNullHandler handler) {
        if (handler instanceof StackDankNullHandler) {
            ((StackDankNullHandler) handler).beginBatch();
        }
    }

    public static void endBatch(final IDankNullHandler handler) {
        if (handler instanceof StackDankNullHandler) {
            ((StackDankNullHandler) handler).endBatch();
        }
    }

    /** Returns the /dank/null in the player's hand, or {@code null}. */
    public static ItemStack getDankNullInHand(final EntityPlayer player) {
        if (player == null) {
            return null;
        }
        final ItemStack held = player.getHeldItem();
        return isDankNull(held) ? held : null;
    }

    /** Returns the first /dank/null found anywhere in the player's main inventory, or {@code null}. */
    public static ItemStack getFirstDankNull(final EntityPlayer player) {
        if (player == null || player.inventory == null) {
            return null;
        }
        for (final ItemStack stack : player.inventory.mainInventory) {
            if (isDankNull(stack)) {
                return stack;
            }
        }
        return null;
    }

    /** Persists any pending changes; handlers normally save themselves, this is for explicit flushes. */
    public static void save(final IDankNullHandler handler) {
        if (handler instanceof StackDankNullHandler) {
            ((StackDankNullHandler) handler).save();
        }
    }
}
