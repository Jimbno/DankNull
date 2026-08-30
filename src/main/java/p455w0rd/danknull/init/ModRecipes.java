package p455w0rd.danknull.init;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.RecipeSorter;
import net.minecraftforge.oredict.ShapedOreRecipe;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import p455w0rd.danknull.DankNull;
import p455w0rd.danknull.recipes.RecipeDankNullUpgrade;

/**
 * 1.7.10 has no JSON recipe system, so the thirteen {@code assets/danknull/recipes/*.json} files upstream shipped
 * are re-expressed here in Java. Every one of them declared {@code forge:ore_shaped}, hence {@link ShapedOreRecipe}
 * throughout even though only the lapis panel actually uses an ore-dictionary entry.
 *
 * @author p455w0rd
 */
public class ModRecipes {

    /** Corner ingredient of each panel recipe, by tier ordinal. Entry 1 is the only ore-dicted one. */
    private static Object getPanelCornerIngredient(final int tier) {
        switch (tier) {
            case 0:
                return new ItemStack(Items.redstone);
            case 1:
                return "gemLapis";
            case 2:
                return new ItemStack(Items.iron_ingot);
            case 3:
                return new ItemStack(Items.gold_ingot);
            case 4:
                return new ItemStack(Items.diamond);
            case 5:
                return new ItemStack(Items.emerald);
            default:
                return null;
        }
    }

    /** stained_glass_pane metadata at the centre of each panel recipe: red, blue, white, yellow, cyan, lime. */
    private static final int[] PANEL_GLASS_META = { 14, 11, 0, 4, 9, 5 };

    public static final IRecipe[] UPGRADE_RECIPES = new IRecipe[5];

    /** GregTech's mod id on 1.7.10. Its presence decides which progression the recipes follow. */
    private static final String GREGTECH = "gregtech";

    public static void register() {
        // 1.7.10 Forge sorts custom IRecipe implementations by registered category; without this it logs
        // "Unknown recipe class!" and falls back to an arbitrary ordering. It must be tried before the vanilla
        // shaped recipes so an upgrade is not swallowed by a plain shaped match. (Do not also declare
        // "after:minecraft:shapeless" - vanilla already sets shaped-before-shapeless, so that closes a cycle.)
        RecipeSorter.register(
            ModGlobals.MODID + ":danknull_upgrade",
            RecipeDankNullUpgrade.class,
            RecipeSorter.Category.SHAPED,
            "before:minecraft:shaped");

        // The tier upgrade stays on the crafting bench on BOTH paths, and has to: it is the only recipe that
        // carries a /dank/null's stored contents across to the new tier, which RecipeDankNullUpgrade does by
        // copying the stack's NBT. A GregTech recipe cannot - its output is a fixed ItemStack - so upgrading in
        // an Assembler would hand back an empty /dank/null and silently void everything inside. It stays gated
        // regardless, because it consumes the next tier's panels and those are GregTech-gated below.
        registerUpgradeRecipes();

        if (Loader.isModLoaded(GREGTECH)) {
            // On a GregTech pack the /dank/null is gated on GT progression instead, from HV upwards. The bench
            // recipes are deliberately NOT registered as well: they are reachable from coal blocks and gems, so
            // leaving them in would let a player walk straight past the tier gating.
            DankNull.LOGGER.info("GregTech detected - registering tiered GregTech recipes");
            ModGTRecipes.register();
            return;
        }
        DankNull.LOGGER.info("No GregTech - registering standalone crafting recipes");
        registerCraftingBenchRecipes();
    }

    /** The standalone progression, used on any pack without GregTech: panels from gems and coal blocks. */
    private static void registerCraftingBenchRecipes() {
        registerPanelRecipes();
        registerDankNullRecipes();
        registerDockRecipe();
    }

    /** The tier's stained glass pane, shared with {@link ModGTRecipes} so both paths keep the same colours. */
    static ItemStack getPanelGlassPane(final int tier) {
        return new ItemStack(Blocks.stained_glass_pane, 1, PANEL_GLASS_META[tier]);
    }

    private static void registerPanelRecipes() {
        for (int tier = 0; tier < ModItems.PANELS.length; tier++) {
            final Object corner = getPanelCornerIngredient(tier);
            GameRegistry.addRecipe(
                new ShapedOreRecipe(
                    new ItemStack(ModItems.PANELS[tier], 1, 0),
                    "aca",
                    "cbc",
                    "aca",
                    'a',
                    corner,
                    'b',
                    new ItemStack(Blocks.stained_glass_pane, 1, PANEL_GLASS_META[tier]),
                    'c',
                    new ItemStack(Blocks.coal_block)));
        }
    }

    private static void registerDankNullRecipes() {
        // No recipe for the creative tier - it is creative-only.
        for (int tier = 0; tier < ModItems.PANELS.length; tier++) {
            GameRegistry.addRecipe(
                new ShapedOreRecipe(
                    new ItemStack(ModItems.DANK_NULLS[tier], 1, 0),
                    " a ",
                    "aaa",
                    " a ",
                    'a',
                    new ItemStack(ModItems.PANELS[tier])));
        }
    }

    private static void registerDockRecipe() {
        final Item dock = ModItems.resolveDockItem();
        if (dock == null) {
            DankNull.LOGGER.warn("/dank/null Docking Station item was not registered - skipping its crafting recipe");
            return;
        }
        GameRegistry.addRecipe(
            new ShapedOreRecipe(
                new ItemStack(dock, 1, 0),
                "aba",
                "bcb",
                "aba",
                'a',
                new ItemStack(Items.emerald),
                'b',
                new ItemStack(Items.redstone),
                'c',
                new ItemStack(Blocks.obsidian)));
    }

    private static void registerUpgradeRecipes() {
        UPGRADE_RECIPES[0] = addDankNullUpgradeRecipe(
            " a ",
            "aba",
            " a ",
            'a',
            new ItemStack(ModItems.LAPIS_PANEL),
            'b',
            new ItemStack(ModItems.REDSTONE_DANKNULL));
        UPGRADE_RECIPES[1] = addDankNullUpgradeRecipe(
            " a ",
            "aba",
            " a ",
            'a',
            new ItemStack(ModItems.IRON_PANEL),
            'b',
            new ItemStack(ModItems.LAPIS_DANKNULL));
        UPGRADE_RECIPES[2] = addDankNullUpgradeRecipe(
            " a ",
            "aba",
            " a ",
            'a',
            new ItemStack(ModItems.GOLD_PANEL),
            'b',
            new ItemStack(ModItems.IRON_DANKNULL));
        UPGRADE_RECIPES[3] = addDankNullUpgradeRecipe(
            " a ",
            "aba",
            " a ",
            'a',
            new ItemStack(ModItems.DIAMOND_PANEL),
            'b',
            new ItemStack(ModItems.GOLD_DANKNULL));
        UPGRADE_RECIPES[4] = addDankNullUpgradeRecipe(
            " a ",
            "aba",
            " a ",
            'a',
            new ItemStack(ModItems.EMERALD_PANEL),
            'b',
            new ItemStack(ModItems.DIAMOND_DANKNULL));
        for (final IRecipe recipe : UPGRADE_RECIPES) {
            GameRegistry.addRecipe(recipe);
        }
    }

    /**
     * Stands in for 1.12's {@code CraftingHelper.parseShaped}: three pattern rows followed by
     * {@code char, ItemStack} pairs, producing a fixed 3x3 {@link RecipeDankNullUpgrade}.
     */
    public static IRecipe addDankNullUpgradeRecipe(final Object... params) {
        final Map<Character, ItemStack> key = new HashMap<Character, ItemStack>();
        final String[] pattern = new String[RecipeDankNullUpgrade.HEIGHT];
        int index = 0;
        for (int row = 0; row < RecipeDankNullUpgrade.HEIGHT; row++) {
            pattern[row] = (String) params[index++];
        }
        while (index < params.length) {
            final Character symbol = (Character) params[index++];
            key.put(symbol, (ItemStack) params[index++]);
        }
        final ItemStack[] ingredients = new ItemStack[RecipeDankNullUpgrade.WIDTH * RecipeDankNullUpgrade.HEIGHT];
        for (int row = 0; row < RecipeDankNullUpgrade.HEIGHT; row++) {
            for (int column = 0; column < RecipeDankNullUpgrade.WIDTH; column++) {
                final char symbol = pattern[row].charAt(column);
                ingredients[column + row * RecipeDankNullUpgrade.WIDTH] = symbol == ' ' ? null
                    : key.get(Character.valueOf(symbol));
            }
        }
        return new RecipeDankNullUpgrade(ingredients);
    }

}
