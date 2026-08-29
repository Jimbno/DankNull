package p455w0rd.danknull.util;

import net.minecraft.item.ItemStack;

/**
 * Stack helpers for the 1.7.10 backport.
 *
 * <p>
 * 1.12 represents "no stack" with the {@code ItemStack.EMPTY} sentinel and exposes {@code isEmpty()} /
 * {@code getCount()} / {@code setCount()}. 1.7.10 uses {@code null} and the public {@code stackSize} field, so every
 * emptiness check in the original source is routed through here rather than being open-coded.
 * </p>
 *
 * <p>
 * This also replaces the handful of {@code p455w0rdslib.util.ItemUtils} and Forge
 * {@code ItemHandlerHelper} methods the 1.12 version relied on, neither of which exists in 1.7.10.
 * </p>
 */
public class DankNullStackUtils {

    private DankNullStackUtils() {}

    /** A stack is "empty" in 1.7.10 terms if it is null, has no item, or has a non-positive size. */
    public static boolean isEmpty(final ItemStack stack) {
        return stack == null || stack.getItem() == null || stack.stackSize <= 0;
    }

    public static int getCount(final ItemStack stack) {
        return isEmpty(stack) ? 0 : stack.stackSize;
    }

    /** Replacement for {@code ItemHandlerHelper.copyStackWithSize}. Returns null when the size is non-positive. */
    public static ItemStack copyWithSize(final ItemStack stack, final int size) {
        if (stack == null || size <= 0) {
            return null;
        }
        final ItemStack copy = stack.copy();
        copy.stackSize = size;
        return copy;
    }

    /**
     * Replacement for {@code ItemUtils.areItemStacksEqualIgnoreSize}. Compares item, damage and NBT but not size.
     * Null-safe; two empty stacks are considered equal.
     */
    public static boolean areItemStacksEqualIgnoreSize(final ItemStack a, final ItemStack b) {
        if (isEmpty(a) || isEmpty(b)) {
            return isEmpty(a) && isEmpty(b);
        }
        if (a.getItem() != b.getItem()) {
            return false;
        }
        if (a.getItemDamage() != b.getItemDamage()) {
            return false;
        }
        return ItemStack.areItemStackTagsEqual(a, b);
    }

    /**
     * Replacement for {@code ItemHandlerHelper.canItemStacksStack}.
     *
     * <p>
     * Deliberately does <b>not</b> consult {@link ItemStack#isStackable()}. Forge's helper does not either, and a
     * /dank/null's whole point is that it ignores vanilla stack limits: gating on stackability would cap every
     * max-stack-1 item (buckets, tools, armour) and every damaged tool at one per slot instead of the tier limit.
     * </p>
     */
    public static boolean canItemStacksStack(final ItemStack a, final ItemStack b) {
        if (isEmpty(a) || isEmpty(b)) {
            return false;
        }
        return areItemStacksEqualIgnoreSize(a, b);
    }

    /** Guava 17 (shipped with 1.7.10) has no {@code Ints.constrainToRange}. */
    public static int constrainToRange(final int value, final int min, final int max) {
        return Math.min(Math.max(value, min), max);
    }
}
