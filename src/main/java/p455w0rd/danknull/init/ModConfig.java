package p455w0rd.danknull.init;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.oredict.OreDictionary;

import com.google.common.collect.Lists;

import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import p455w0rd.danknull.DankNull;
import p455w0rd.danknull.network.PacketConfigSync;

/**
 * @author p455w0rd
 */
public class ModConfig {

    /**
     * 1.7.10's Forge {@code Configuration} only defines CATEGORY_GENERAL; CATEGORY_CLIENT was added in a later
     * version, so the same "client" category name is declared locally here.
     */
    public static final String CATEGORY_CLIENT = "client";
    public static final String SERVER_CAT = "Server Rules";
    public static final boolean DEBUG_RESET = false;
    public static final String NAME_CREATIVE_BLACKLIST = "CreativeBlacklist";
    public static final String NAME_CREATIVE_WHITELIST = "CreativeWhitelist";
    public static final String NAME_OREDICT_BLACKLIST = "OreDictBlacklist";
    public static final String NAME_OREDICT_WHITELIST = "OreDictWhitelist";
    public static final String NAME_DISABLE_OREDICT = "DisableOreDictMode";
    public static final String NAME_ALLOW_DOCK_INSERTION = "AllowDockInsertion";
    public static final String NAME_CALL_IT_DEVNULL = "CallItDevNull";
    public static final String NAME_SUPERSHINE = "SuperShine";
    public static final String NAME_ONLY_CYCLE_BLOCKS = "onlyCycleBlocks";

    /** Fallback used only if {@link #load(File)} was never called. */
    private static final File DEFAULT_FILE = new File("config/DankNull.cfg");

    private static Configuration CONFIG = null;

    private static Configuration config() {
        if (CONFIG == null) {
            CONFIG = new Configuration(DEFAULT_FILE);
        }
        return CONFIG;
    }

    public static Configuration getInstance() {
        return config();
    }

    /**
     * 1.12 hardcoded {@code config/DankNull.cfg}. In 1.7.10 the config file is handed to us by
     * {@code FMLPreInitializationEvent#getSuggestedConfigurationFile()}, which honours per-instance config
     * directories, so the proxy passes it in here instead.
     */
    public static void load(final File file) {
        if (DEBUG_RESET && file.exists()) {
            file.delete();
        }
        CONFIG = new Configuration(file);
        sync();
    }

    /** Kept for parity with upstream; prefer {@link #load(File)}. */
    public static void load() {
        load(DEFAULT_FILE);
    }

    public static void sync() {
        Options.callItDevNull = config().getBoolean(
            NAME_CALL_IT_DEVNULL,
            CATEGORY_CLIENT,
            false,
            "Call it a /dev/null in-game (Requested by TheMattaBase)");
        Options.superShine = config().getBoolean(NAME_SUPERSHINE, CATEGORY_CLIENT, false, "Make items ultra shiny!");
        Options.skipNonBlocksOnCycle = config().getBoolean(
            NAME_ONLY_CYCLE_BLOCKS,
            CATEGORY_CLIENT,
            false,
            "When cycling selected item with /dank/null in-hand, should it try to only cycle blocks?");
        Options.creativeBlacklist = config().getString(
            NAME_CREATIVE_BLACKLIST,
            SERVER_CAT,
            "",
            "A semicolon separated list of items that are not allowed to be placed into the creative /dank/null\nFormat: modid:name:meta (meta optional: modid:name is acceptable) - Example: minecraft:diamond;minecraft:coal:1")
            .trim();
        Options.creativeWhitelist = config().getString(
            NAME_CREATIVE_WHITELIST,
            SERVER_CAT,
            "",
            "A semicolon separated list of items that are allowed to be placed into the creative /dank/null\nSame format as Blacklist and whitelist superceeds blacklist.\nIf whitelist is non-empty, then ONLY whitelisted items can be added to the Creative /dank/null")
            .trim();
        Options.oreBlacklist = config().getString(
            NAME_OREDICT_BLACKLIST,
            SERVER_CAT,
            "itemSkull",
            "A semicolon separated list of Ore Dictionary entries (strings) which WILL NOT be allowed to be used with /dank/null's Ore Dictionary functionality.");
        Options.oreWhitelist = config().getString(
            NAME_OREDICT_WHITELIST,
            SERVER_CAT,
            "",
            "A semicolon separated list of Ore Dictionary entries (strings) which WILL BE allowed to be used with /dank/null's Ore Dictionary functionality. Whitelist superceeds blacklist.\nIf whitelist is non-empty, then ONLY Ore Dictionary items matching the entries will\nbe able to take advantage of /dank/null's Ore Dictionary functionality.");
        Options.disableOreDictMode = config().getBoolean(
            NAME_DISABLE_OREDICT,
            SERVER_CAT,
            false,
            "If set to true, then Ore Dictionary Mode will not be available (overrides Ore Dictionary White/Black lists)");
        Options.showHUD = config().getBoolean("showHUD", CATEGORY_CLIENT, true, "Show the /dank/null HUD overlay?");
        Options.allowDockInserting = config().getBoolean(
            NAME_ALLOW_DOCK_INSERTION,
            SERVER_CAT,
            true,
            "If true, you will be able to pipe items into the /dank/null Docking Station");
        Options.invalidateParsedLists();
        if (config().hasChanged()) {
            config().save();
        }
    }

    @SideOnly(Side.CLIENT)
    public static List<IConfigElement> getClientConfigElements() {
        return new ConfigElement<Object>(getInstance().getCategory(CATEGORY_CLIENT)).getChildElements();
    }

    /**
     * Upstream annotated this {@code @SideOnly(Side.SERVER)}, which strips it from the client jar and therefore
     * skips the sync entirely when a client hosts a LAN world. The logical server is what owns these values, so the
     * annotation is dropped here and the caller guards on the effective side instead.
     */
    public static void sendConfigsToClient(final EntityPlayerMP player) {
        ModNetworking.getInstance()
            .sendTo(
                new PacketConfigSync(
                    Options.creativeBlacklist,
                    Options.creativeWhitelist,
                    Options.oreBlacklist,
                    Options.oreWhitelist,
                    Options.disableOreDictMode),
                player);
    }

    public static boolean isOreDictBlacklistEnabled() {
        return !Options.getOreBlacklist()
            .isEmpty() && !isOreDictWhitelistEnabled();
    }

    public static boolean isOreDictWhitelistEnabled() {
        return !Options.getOreWhitelist()
            .isEmpty();
    }

    public static boolean isOreBlacklisted(final String oreName) {
        for (final String currentOre : Options.getOreBlacklist()) {
            if (currentOre.equals(oreName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOreWhitelisted(final String oreName) {
        for (final String currentOre : Options.getOreWhitelist()) {
            if (currentOre.equals(oreName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isValidOre(final String oreName) {
        if (Options.disableOreDictMode) {
            // This is what actually turns the ore-dict feature off: every ore-name use (matching, conversion,
            // tooltips) funnels through here, and it used to keep answering true with the feature "disabled".
            return false;
        }
        boolean isValid = true;
        if (isOreConfigEnabled()) {
            isValid = isOreDictWhitelistEnabled() && isOreWhitelisted(oreName);
            if (!isValid) {
                isValid = isOreDictBlacklistEnabled() && !isOreBlacklisted(oreName);
            }
        }
        return isValid;
    }

    public static boolean isOreConfigEnabled() {
        // DisableOreDictMode overrides BOTH lists; the old precedence let a non-empty whitelist re-enable it.
        return !Options.disableOreDictMode && (isOreDictBlacklistEnabled() || isOreDictWhitelistEnabled());
    }

    public static boolean isItemOreDictBlacklisted(final ItemStack stack) {
        if (isOreConfigEnabled() && isOreDictBlacklistEnabled()) {
            for (final int id : OreDictionary.getOreIDs(stack)) {
                final String currentOreName = OreDictionary.getOreName(id);
                if (isValidOre(currentOreName)) {
                    for (final String oreName : Options.getOreBlacklist()) {
                        if (isValidOre(oreName) && oreName.equals(currentOreName)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean isItemOreDictWhitelisted(final ItemStack stack) {
        if (isOreConfigEnabled() && isOreDictWhitelistEnabled()) {
            for (final int id : OreDictionary.getOreIDs(stack)) {
                final String currentOreName = OreDictionary.getOreName(id);
                if (isValidOre(currentOreName)) {
                    for (final String oreName : Options.getOreWhitelist()) {
                        if (isValidOre(oreName) && oreName.equals(OreDictionary.getOreName(id))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Whether an item may be put into a <b>Creative</b> /dank/null.
     *
     * <p>
     * A creative /dank/null has an effectively unlimited stack size, so anything stored in one becomes an infinite
     * source of that item. {@code CreativeWhitelist} / {@code CreativeBlacklist} exist to let a server restrict
     * that; per the config text the whitelist supersedes the blacklist, and a non-empty whitelist means <i>only</i>
     * listed items are allowed.
     * </p>
     *
     * <p>
     * Both lists were parsed but never consulted, in this backport and in upstream 1.12 alike - the options
     * appeared in the config GUI and filtered nothing. This is the check that makes them mean something; it is
     * applied in {@code DankNullHandler.isItemValid}, which every insertion path goes through.
     * </p>
     *
     * @return {@code true} if the item is allowed, including when neither list is configured
     */
    public static boolean isAllowedInCreativeDankNull(final ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        try {
            final List<ItemStack> whitelist = Options.getCreativeWhitelistedItems();
            if (!whitelist.isEmpty()) {
                return matchesAny(whitelist, stack);
            }
            return !matchesAny(Options.getCreativeBlacklistedItems(), stack);
        } catch (final Exception e) {
            // A malformed list must not lock the player out of their own /dank/null; warn and allow.
            DankNull.LOGGER.warn("Could not parse the creative /dank/null item lists; allowing all items", e);
            return true;
        }
    }

    /** An entry with a wildcard meta matches the item at any damage value; see the parsing above. */
    private static boolean matchesAny(final List<ItemStack> entries, final ItemStack stack) {
        for (final ItemStack entry : entries) {
            if (entry == null || entry.getItem() != stack.getItem()) {
                continue;
            }
            if (entry.getItemDamage() == OreDictionary.WILDCARD_VALUE
                || entry.getItemDamage() == stack.getItemDamage()) {
                return true;
            }
        }
        return false;
    }

    public static class Options {

        public static boolean callItDevNull = false;
        public static boolean superShine = false;
        public static String creativeBlacklist = "";
        public static String creativeWhitelist = "";
        public static String oreBlacklist = "";
        public static String oreWhitelist = "";
        public static boolean showHUD = true;
        public static boolean disableOreDictMode = false;
        public static boolean allowDockInserting = true;
        public static boolean skipNonBlocksOnCycle = false;

        // 1.7.10 has no NonNullList, and upstream's NonNullListSerializable/WeakHashMapSerializable only existed to
        // make these Java-serialisable for the config sync packet. That packet now writes its fields explicitly, so
        // plain collections are enough.
        private static List<ItemStack> creativeItemBlacklist;
        private static List<ItemStack> creativeItemWhitelist;
        private static ArrayList<String> oreStringBlacklist = Lists.newArrayList();
        private static ArrayList<String> oreStringWhitelist = Lists.newArrayList();

        /**
         * The parsed views above are cached forever once populated. They therefore have to be dropped whenever the
         * raw strings are replaced - on a config reload, and on receiving the server's config sync - or the client
         * keeps filtering against the values it started up with.
         */
        public static void invalidateParsedLists() {
            oreStringBlacklist = Lists.newArrayList();
            oreStringWhitelist = Lists.newArrayList();
            creativeItemBlacklist = null;
            creativeItemWhitelist = null;
        }

        public static List<String> getOreBlacklist() {
            String[] tmpList = null;
            if (oreStringBlacklist.isEmpty() && !oreBlacklist.isEmpty() && getOreWhitelist().isEmpty()) {
                tmpList = oreBlacklist.split(";");
            }
            if (tmpList != null) {
                for (final String string : tmpList) {
                    if (OreDictionary.doesOreNameExist(string)) {
                        oreStringBlacklist.add(string);
                    }
                }
            }
            return oreStringBlacklist;
        }

        public static List<String> getOreWhitelist() {
            String[] tmpList = null;
            if (oreStringWhitelist.isEmpty() && !oreWhitelist.isEmpty()) {
                tmpList = oreWhitelist.split(";");
            }
            if (tmpList != null) {
                for (final String string : tmpList) {
                    if (OreDictionary.doesOreNameExist(string)) {
                        oreStringWhitelist.add(string);
                    }
                }
            }
            return oreStringWhitelist;
        }

        public static List<ItemStack> getCreativeBlacklistedItems() throws Exception {
            if (creativeItemBlacklist == null && getCreativeWhitelistedItems().isEmpty()) {
                creativeItemBlacklist = Lists.newArrayList();
                if (!creativeBlacklist.isEmpty()) {
                    final List<String> itemStringList = Lists.newArrayList(creativeBlacklist.split(";"));
                    for (final String itemString : itemStringList) {
                        final String[] params = itemString.split(":");
                        final int numColons = params.length - 1;
                        if (numColons > 2 || numColons <= 0) {
                            throw new Exception(
                                new Throwable(
                                    "Invalid format for item blacklisting, check " + config().getConfigFile()
                                        + " for an example"));
                        }
                        if (numColons == 1) { // no meta
                            final Item item = GameRegistry.findItem(params[0], params[1]);
                            if (item == null) {
                                DankNull.LOGGER.warn("Item \"" + params[0] + ":" + params[1] + "\" not found");
                            } else {
                                // The meta is optional in the config format, and omitting it is documented as naming
                                // the item rather than one specific damage value - so match any meta.
                                creativeItemBlacklist.add(new ItemStack(item, 1, OreDictionary.WILDCARD_VALUE));
                            }
                        } else if (numColons == 2) {
                            final Item item = GameRegistry.findItem(params[0], params[1]);
                            if (item == null) {
                                DankNull.LOGGER.warn("Item \"" + params[0] + ":" + params[1] + "\" not found");
                            } else {
                                int meta;
                                try {
                                    meta = Integer.parseInt(params[2]);
                                } catch (final NumberFormatException e) {
                                    meta = -1;
                                }
                                if (meta < 0) {
                                    DankNull.LOGGER.warn(
                                        "Invalid metadata for item \"" + params[0]
                                            + ":"
                                            + params[1]
                                            + "\" ("
                                            + params[2]
                                            + ")");
                                } else {
                                    // Upstream added the meta-qualified blacklist entry to creativeItemWhitelist,
                                    // which both lost the blacklist entry and corrupted the whitelist. Fixed here.
                                    creativeItemBlacklist.add(new ItemStack(item, 1, meta));
                                }
                            }
                        }
                    }
                }
            }
            return creativeItemBlacklist == null ? Lists.<ItemStack>newArrayList() : creativeItemBlacklist;
        }

        public static List<ItemStack> getCreativeWhitelistedItems() throws Exception {
            if (creativeItemWhitelist == null) {
                creativeItemWhitelist = Lists.newArrayList();
                if (!creativeWhitelist.isEmpty()) {
                    final List<String> itemStringList = Lists.newArrayList(creativeWhitelist.split(";"));
                    for (final String itemString : itemStringList) {
                        final String[] params = itemString.split(":");
                        final int numColons = params.length - 1;
                        if (numColons > 2 || numColons <= 0) {
                            throw new Exception(
                                new Throwable(
                                    "Invalid format for item whitelisting, check " + config().getConfigFile()
                                        + " for an example"));
                        }
                        if (numColons == 1) { // no meta
                            final Item item = GameRegistry.findItem(params[0], params[1]);
                            if (item == null) {
                                DankNull.LOGGER.warn("Item \"" + params[0] + ":" + params[1] + "\" not found");
                            } else {
                                creativeItemWhitelist.add(new ItemStack(item, 1, OreDictionary.WILDCARD_VALUE));
                            }
                        } else if (numColons == 2) {
                            final Item item = GameRegistry.findItem(params[0], params[1]);
                            if (item == null) {
                                DankNull.LOGGER.warn("Item \"" + params[0] + ":" + params[1] + "\" not found");
                            } else {
                                int meta;
                                try {
                                    meta = Integer.parseInt(params[2]);
                                } catch (final NumberFormatException e) {
                                    meta = -1;
                                }
                                if (meta < 0) {
                                    DankNull.LOGGER.warn(
                                        "Invalid metadata for item \"" + params[0]
                                            + ":"
                                            + params[1]
                                            + "\" ("
                                            + params[2]
                                            + ")");
                                } else {
                                    creativeItemWhitelist.add(new ItemStack(item, 1, meta));
                                }
                            }
                        }
                    }
                }
            }
            return creativeItemWhitelist;
        }
    }

}
