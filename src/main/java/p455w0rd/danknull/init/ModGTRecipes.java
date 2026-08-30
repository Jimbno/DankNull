package p455w0rd.danknull.init;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTRecipeConstants;
import p455w0rd.danknull.DankNull;
import p455w0rd.danknull.init.ModGlobals.DankNullTier;

/**
 * The GregTech progression for the /dank/null, registered in place of the crafting-bench recipes whenever
 * GregTech is loaded (see {@link ModRecipes#register()}).
 *
 * <p>
 * GTNH writes its recipes in Java rather than in scripts - GregTech alone ships a {@code gregtech.loaders}
 * package and some 249 recipe classes, and the pack's {@code scripts/} directory is empty - so registering
 * through GregTech's own API is the pack-native way to do this. CraftTweaker is present for players who want to
 * retune these afterwards.
 * </p>
 *
 * <p>
 * GregTech is a <b>soft</b> dependency. Every GregTech type is referenced from this class alone, and
 * {@link ModRecipes} only names it from inside a {@code Loader.isModLoaded} branch, so the JVM never resolves
 * these symbols on a pack without GregTech.
 * </p>
 *
 * <p>
 * <b>Why the gating is on hulls and tiered components rather than circuits.</b> The obvious choice would be a
 * tier circuit, but this GregTech no longer makes that expressible: the marker materials the old
 * {@code circuitData} / {@code circuitElite} ore-dictionary names were generated from
 * ({@code Materials.Data}, {@code .Elite}, {@code .Master}, {@code .Ultimate}) have been deleted, and the
 * surviving {@code ItemList.Circuit_*} constants carry no tier in their names - {@code Circuit_Data} is not
 * documented anywhere in the jar as "the EV circuit". Hulls and the {@code _HV}/{@code _EV}/... component
 * families state their tier in the constant name, so they gate exactly where intended and break loudly at
 * compile time if a name ever changes.
 * </p>
 */
public class ModGTRecipes {

    private ModGTRecipes() {}

    /** The first /dank/null tier built on the Assembling Line rather than the Assembler. */
    private static final int ASSEMBLING_LINE_FROM = DankNullTier.GOLD.ordinal();

    /** Number of craftable tiers; CREATIVE is deliberately absent, as it is not craftable at any tier. */
    private static final int TIERS = 6;

    /**
     * Recipe voltage per /dank/null tier. The base /dank/null lands at HV and each step up is one voltage tier,
     * which lines the six craftable tiers up with their storage: HV holds 128 items per slot, UV is unlimited.
     */
    private static long recipeVoltage(final int tier) {
        switch (tier) {
            case 0:
                return TierEU.RECIPE_HV;
            case 1:
                return TierEU.RECIPE_EV;
            case 2:
                return TierEU.RECIPE_IV;
            case 3:
                return TierEU.RECIPE_LuV;
            case 4:
                return TierEU.RECIPE_ZPM;
            default:
                return TierEU.RECIPE_UV;
        }
    }

    /** Machine hull of the matching voltage tier - the component that actually pins each recipe to its tier. */
    private static ItemList hull(final int tier) {
        switch (tier) {
            case 0:
                return ItemList.Hull_HV;
            case 1:
                return ItemList.Hull_EV;
            case 2:
                return ItemList.Hull_IV;
            case 3:
                return ItemList.Hull_LuV;
            case 4:
                return ItemList.Hull_ZPM;
            default:
                return ItemList.Hull_UV;
        }
    }

    private static ItemList robotArm(final int tier) {
        switch (tier) {
            case 0:
                return ItemList.Robot_Arm_HV;
            case 1:
                return ItemList.Robot_Arm_EV;
            case 2:
                return ItemList.Robot_Arm_IV;
            case 3:
                return ItemList.Robot_Arm_LuV;
            case 4:
                return ItemList.Robot_Arm_ZPM;
            default:
                return ItemList.Robot_Arm_UV;
        }
    }

    private static ItemList conveyor(final int tier) {
        switch (tier) {
            case 0:
                return ItemList.Conveyor_Module_HV;
            case 1:
                return ItemList.Conveyor_Module_EV;
            case 2:
                return ItemList.Conveyor_Module_IV;
            case 3:
                return ItemList.Conveyor_Module_LuV;
            case 4:
                return ItemList.Conveyor_Module_ZPM;
            default:
                return ItemList.Conveyor_Module_UV;
        }
    }

    private static ItemList sensor(final int tier) {
        switch (tier) {
            case 0:
                return ItemList.Sensor_HV;
            case 1:
                return ItemList.Sensor_EV;
            case 2:
                return ItemList.Sensor_IV;
            case 3:
                return ItemList.Sensor_LuV;
            case 4:
                return ItemList.Sensor_ZPM;
            default:
                return ItemList.Sensor_UV;
        }
    }

    /** The tier's structural material, following GregTech's own voltage-tier materials. */
    private static Materials plateMaterial(final int tier) {
        switch (tier) {
            case 0:
                return Materials.StainlessSteel;
            case 1:
                return Materials.Titanium;
            case 2:
                return Materials.TungstenSteel;
            case 3:
                return Materials.Chrome;
            case 4:
                return Materials.Iridium;
            default:
                return Materials.Osmium;
        }
    }

    public static void register() {
        for (int tier = 0; tier < TIERS; tier++) {
            registerPanel(tier);
            registerDankNull(tier);
        }
        registerDock();
        DankNull.LOGGER.info("Registered GregTech recipes for {} /dank/null tiers (HV through UV)", TIERS);
    }

    /**
     * A panel: the tier's plates and sensor around a pane of its glass. Always on the Assembler - a panel is a
     * component, and gating the intermediate behind the Assembling Line as well would only add busywork.
     */
    private static void registerPanel(final int tier) {
        final ItemStack sensorStack = stack(sensor(tier), 1);
        final ItemStack plates = GTOreDictUnificator.get(OrePrefixes.plate, plateMaterial(tier), 4L);
        if (sensorStack == null || plates == null) {
            skip("panel", tier);
            return;
        }
        GTValues.RA.stdBuilder()
            .itemInputs(plates, sensorStack, ModRecipes.getPanelGlassPane(tier))
            .itemOutputs(new ItemStack(ModItems.PANELS[tier], 1, 0))
            .duration((10 + 5 * tier) * GTRecipeBuilder.SECONDS)
            .eut(recipeVoltage(tier))
            .addTo(RecipeMaps.assemblerRecipes);
    }

    /**
     * A /dank/null: four of its panels around the tier's hull, with the arm and conveyor that give it its
     * pick-up behaviour. Assembler up to IV, Assembling Line from LuV.
     */
    private static void registerDankNull(final int tier) {
        final ItemStack hullStack = stack(hull(tier), 1);
        final ItemStack armStack = stack(robotArm(tier), 2);
        final ItemStack conveyorStack = stack(conveyor(tier), 2);
        if (hullStack == null || armStack == null || conveyorStack == null) {
            skip("/dank/null", tier);
            return;
        }
        final ItemStack output = new ItemStack(ModItems.DANK_NULLS[tier], 1, 0);
        final ItemStack panels = new ItemStack(ModItems.PANELS[tier], 4, 0);
        final GTRecipeBuilder builder = GTValues.RA.stdBuilder()
            .itemInputs(panels, hullStack, armStack, conveyorStack)
            .itemOutputs(output)
            .duration((30 + 15 * tier) * GTRecipeBuilder.SECONDS)
            .eut(recipeVoltage(tier));

        if (tier < ASSEMBLING_LINE_FROM) {
            builder.addTo(RecipeMaps.assemblerRecipes);
            return;
        }
        // The Assembling Line wants something to scan first. The tier below is the natural prerequisite: it
        // makes the ladder explicit rather than letting a player jump straight to the top tier.
        builder.metadata(GTRecipeConstants.RESEARCH_ITEM, new ItemStack(ModItems.DANK_NULLS[tier - 1], 1, 0))
            .metadata(GTRecipeConstants.RESEARCH_STATION_DATA, 1 << tier - ASSEMBLING_LINE_FROM)
            .addTo(GTRecipeConstants.AssemblyLine);
    }

    /** The docking station sits at the base tier - it is a holder, not a storage upgrade. */
    private static void registerDock() {
        final Item dock = ModItems.resolveDockItem();
        final ItemStack hullStack = stack(hull(0), 1);
        final ItemStack armStack = stack(robotArm(0), 1);
        final ItemStack plates = GTOreDictUnificator.get(OrePrefixes.plate, plateMaterial(0), 4L);
        if (dock == null || hullStack == null || armStack == null || plates == null) {
            DankNull.LOGGER.warn("Skipping the GregTech docking station recipe - a component was unavailable");
            return;
        }
        GTValues.RA.stdBuilder()
            .itemInputs(hullStack, armStack, plates)
            .itemOutputs(new ItemStack(dock, 1, 0))
            .duration(20 * GTRecipeBuilder.SECONDS)
            .eut(recipeVoltage(0))
            .addTo(RecipeMaps.assemblerRecipes);
    }

    /**
     * {@link ItemList#get(long, Object...)} returns {@code null} for a constant GregTech never registered, and a
     * recipe built on one would be silently dropped or throw deep inside the builder. Checking here means an
     * absent component is reported against the tier that wanted it.
     */
    private static ItemStack stack(final ItemList item, final int amount) {
        return item.hasBeenSet() ? item.get(amount) : null;
    }

    private static void skip(final String what, final int tier) {
        DankNull.LOGGER.warn("Skipping the GregTech {} recipe for tier {} - a component was unavailable", what, tier);
    }
}
