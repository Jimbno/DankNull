package p455w0rd.danknull.init;

import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.EnumHelper;

public class ModGlobals {

    public static final String MODID = "danknull";
    public static final String NAME = "/dank/null";

    public static boolean GUI_DANKNULL_ISOPEN = false;
    public static float TIME = 0.0F;

    public enum DankNullTier {

        REDSTONE,
        LAPIS,
        IRON,
        GOLD,
        DIAMOND,
        EMERALD,
        CREATIVE,
        NONE;

        //@formatter:off
        private static final int[] OPAQUE_HEX_COLORS = new int[] {
                0xFFEC4848,
                0xFF4885EC,
                0xFFFFFFFF,
                0xFFFFFF00,
                0xFF00FFFF,
                0xFF17FF6D,
                0xFF8F15D4,
                0x0
        };
        private static final int[] HEX_COLORS = new int[] {
                0x99EC4848,
                0x994885EC,
                0x99FFFFFF,
                0x99FFFF00,
                0x9900FFFF,
                0x9917FF6D,
                0x998F15D4,
                0x0
        };
        public static final DankNullTier[] VALUES = values();
        //@formatter:on

        public ResourceLocation getDankNullRegistryName() {
            return new ResourceLocation(ModGlobals.MODID, getUnlocalizedNameForDankNull());
        }

        public ResourceLocation getDankNullPanelRegistryName() {
            return new ResourceLocation(ModGlobals.MODID, getUnlocalizedNameForPanel());
        }

        public String getUnlocalizedNameForDankNull() {
            return "dank_null_" + ordinal();
        }

        public String getUnlocalizedNameForPanel() {
            return "dank_null_panel_" + ordinal();
        }

        public int getMaxStackSize() {
            final int level = ordinal() + 1;
            if (level >= 6) {
                return Integer.MAX_VALUE;
            }
            return level * 128 * level;
        }

        public int getNumRows() {
            int numRows = ordinal();
            if (isCreative()) {
                numRows--;
            }
            return numRows + 1;
        }

        public int getNumRowsMultiplier() {
            return getNumRows() - 1;
        }

        public boolean isCreative() {
            return ordinal() == 6;
        }

        // 140 = player inv
        public int getGuiHeight() {
            return 140 + getNumRowsMultiplier() * 20 + getNumRowsMultiplier() + 1;
        }

        public ResourceLocation getGuiBackground() {
            return new ResourceLocation(
                ModGlobals.MODID,
                "textures/gui/danknullscreen" + (getNumRowsMultiplier() + (isCreative() ? 1 : 0)) + ".png");
        }

        public EnumRarity getRarity() {
            return Rarities.getRarityFromMeta(ordinal());
        }

        public int getHexColor(final boolean opaque) {
            return opaque ? OPAQUE_HEX_COLORS[ordinal()] : HEX_COLORS[ordinal()];
        }

        /**
         * 1.7.10 stores the item id in stack NBT as a numeric short rather than a "modid:name" string, so the 1.12
         * approach of rewriting the "id" tag does not work here. Instead we look the next tier's item up directly and
         * carry the existing tag compound across.
         */
        public ItemStack getUpgradedVersion(final ItemStack dankNull) {
            if (dankNull == null) {
                return null;
            }
            if (ordinal() >= CREATIVE.ordinal()) {
                return dankNull.copy();
            }
            final Item upgraded = ModItems.DANK_NULLS[ordinal() + 1];
            if (upgraded == null) {
                return dankNull.copy();
            }
            final ItemStack result = new ItemStack(upgraded, 1, 0);
            if (dankNull.hasTagCompound()) {
                result.setTagCompound(
                    (NBTTagCompound) dankNull.getTagCompound()
                        .copy());
            }
            return result;
        }

        public static DankNullTier fromOrdinal(final int ordinal) {
            if (ordinal < 0 || ordinal >= VALUES.length) {
                return NONE;
            }
            return VALUES[ordinal];
        }
    }

    public static class Rarities {

        private static final EnumRarity[] RARITY_CACHE = new EnumRarity[] { //@formatter:off
                createRarity("dn:redstone", EnumChatFormatting.RED),
                createRarity("dn:lapis", EnumChatFormatting.BLUE),
                createRarity("dn:iron", EnumChatFormatting.WHITE),
                createRarity("dn:gold", EnumChatFormatting.YELLOW),
                createRarity("dn:diamond", EnumChatFormatting.AQUA),
                createRarity("dn:emerald", EnumChatFormatting.GREEN),
                createRarity("dn:creative", EnumChatFormatting.LIGHT_PURPLE) //@formatter:on
        };

        public static EnumRarity getRarityFromMeta(final int meta) {
            if (meta < 0 || meta >= RARITY_CACHE.length) {
                return EnumRarity.common;
            }
            return RARITY_CACHE[meta];
        }

        private static EnumRarity createRarity(final String name, final EnumChatFormatting color) {
            return EnumHelper.addRarity(name, color, name);
        }
    }

    public static class NBT {

        // Vanilla
        public static final String ID = "id";
        public static final String DAMAGE = "Damage";
        public static final String BLOCKENTITYTAG = "BlockEntityTag";

        // Custom
        public static final String DANKNULL_INVENTORY = "danknull-inventory";
        public static final String DOCKEDSTACK = "DankNullStack";
        public static final String MODE = "Mode";
        public static final String STACK = "Stack";
        public static final String OREDICT = "OreDict";
        public static final String SELECTEDINDEX = "selectedIndex";
        public static final String EXTRACTION_MODES = "ExtractionModes";
        public static final String PLACEMENT_MODES = "PlacementModes";
        public static final String OREDICT_MODES = "OreDictModes";
        public static final String LOCKED = "Locked";
    }
}
