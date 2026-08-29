package p455w0rd.danknull.recipes;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import p455w0rd.danknull.init.ModGlobals.DankNullTier;
import p455w0rd.danknull.items.ItemDankNull;
import p455w0rd.danknull.util.DankNullStackUtils;

/**
 * Tier-upgrade recipe: surrounds a /dank/null with four panels of the next tier and produces that tier's
 * /dank/null carrying the original's NBT (i.e. its stored contents) verbatim.
 *
 * <p>
 * 1.12's {@code Ingredient} / {@code IShapedRecipe} / registry-entry plumbing does not exist in 1.7.10, so the
 * ingredients are plain {@code ItemStack}s in a fixed 3x3 layout and matching is done with
 * {@link OreDictionary#itemMatches} (item + damage, NBT ignored) - the same semantics 1.12's {@code Ingredient}
 * had.
 * </p>
 *
 * @author p455w0rd
 */
public class RecipeDankNullUpgrade implements IRecipe {

    public static final int WIDTH = 3;
    public static final int HEIGHT = 3;

    /** Row-major, length {@code WIDTH * HEIGHT}; {@code null} means "must be empty". */
    private final ItemStack[] recipeItems;

    public RecipeDankNullUpgrade(final ItemStack[] ingredients) {
        if (ingredients.length != WIDTH * HEIGHT) {
            throw new IllegalArgumentException("RecipeDankNullUpgrade expects a full 3x3 ingredient array");
        }
        recipeItems = ingredients;
    }

    public ItemStack[] getIngredients() {
        return recipeItems;
    }

    /** The /dank/null this recipe consumes, or {@code null}. */
    public ItemStack getInputDankNull() {
        for (final ItemStack ingredient : recipeItems) {
            if (ItemDankNull.isDankNull(ingredient)) {
                return ingredient;
            }
        }
        return null;
    }

    @Override
    public ItemStack getRecipeOutput() {
        final ItemStack input = getInputDankNull();
        if (input == null) {
            return null;
        }
        return ItemDankNull.getTier(input)
            .getUpgradedVersion(input);
    }

    @Override
    public int getRecipeSize() {
        return WIDTH * HEIGHT;
    }

    @Override
    public boolean matches(final InventoryCrafting inv, final World world) {
        // Upstream scans for a shifted match; at a fixed 3x3 that is a single iteration, but it is kept so a
        // larger crafting grid would behave the same way.
        for (int widthIndex = 0; widthIndex <= 3 - WIDTH; ++widthIndex) {
            for (int heightIndex = 0; heightIndex <= 3 - HEIGHT; ++heightIndex) {
                if (checkMatch(inv, widthIndex, heightIndex)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkMatch(final InventoryCrafting inventory, final int widthIndexStart,
        final int heightIndexStart) {
        for (int column = 0; column < 3; ++column) {
            for (int row = 0; row < 3; ++row) {
                final int recipeColumn = column - widthIndexStart;
                final int recipeRow = row - heightIndexStart;
                ItemStack ingredient = null;
                if (recipeColumn >= 0 && recipeRow >= 0 && recipeColumn < WIDTH && recipeRow < HEIGHT) {
                    ingredient = recipeItems[recipeColumn + recipeRow * WIDTH];
                }
                if (!ingredientMatches(ingredient, inventory.getStackInRowAndColumn(column, row))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean ingredientMatches(final ItemStack ingredient, final ItemStack input) {
        if (ingredient == null) {
            return DankNullStackUtils.isEmpty(input);
        }
        if (DankNullStackUtils.isEmpty(input)) {
            return false;
        }
        return OreDictionary.itemMatches(ingredient, input, false);
    }

    @Override
    public ItemStack getCraftingResult(final InventoryCrafting inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            final ItemStack stack = inv.getStackInSlot(i);
            if (ItemDankNull.isDankNull(stack)) {
                final NBTTagCompound oldNBT = stack.getTagCompound();
                final ItemStack newStack = getNewNextTierStack(stack);
                if (DankNullStackUtils.isEmpty(newStack)) {
                    return null;
                }
                newStack.setTagCompound(oldNBT == null ? null : (NBTTagCompound) oldNBT.copy());
                return newStack;
            }
        }
        return null;
    }

    private ItemStack getNewNextTierStack(final ItemStack dankNull) {
        final int tier = ItemDankNull.getTier(dankNull)
            .ordinal();
        // Only tiers 0..4 upgrade; emerald is terminal and creative is never a crafting output.
        if (tier < 0 || tier >= DankNullTier.EMERALD.ordinal()) {
            return null;
        }
        final ItemStack upgraded = DankNullTier.VALUES[tier].getUpgradedVersion(dankNull);
        if (DankNullStackUtils.isEmpty(upgraded)) {
            return null;
        }
        upgraded.setTagCompound(null);
        return upgraded;
    }

    // 1.7.10's IRecipe declares only matches/getCraftingResult/getRecipeSize/getRecipeOutput. There is no
    // getRemainingItems hook (that arrived later); leftover container items are resolved by the crafting container
    // via Item.getContainerItem(ItemStack). Nothing in this recipe leaves a container item, so there is nothing to do.

    public int getWidth() {
        return WIDTH;
    }

    public int getHeight() {
        return HEIGHT;
    }
}
