package p455w0rd.danknull.api;

import net.minecraft.util.StatCollector;

/**
 * @author p455w0rd
 */
public class DankNullItemModes {

    /**
     * 1.7.10 has no client/server-split I18n helper equivalent to 1.12's
     * {@code net.minecraft.util.text.translation.I18n}, and enum constants are initialised long before the language
     * files are loaded. The messages are therefore resolved lazily instead of being baked into the constant.
     */
    public enum ItemExtractionMode {

        KEEP_ALL(Integer.MAX_VALUE, "dn.not_extract.desc", null),
        KEEP_1(1, "dn.extract_all_but.desc", "1"),
        KEEP_16(16, "dn.extract_all_but.desc", "16"),
        KEEP_64(64, "dn.extract_all_but.desc", "64"),
        KEEP_NONE(0, "dn.extract_all.desc", null);

        public static final ItemExtractionMode[] VALUES = values();

        private final int number;
        private final String key;
        private final String suffix;

        ItemExtractionMode(final int numberToKeep, final String key, final String suffix) {
            this.number = numberToKeep;
            this.key = key;
            this.suffix = suffix;
        }

        public int getNumberToKeep() {
            return number;
        }

        private String msg() {
            final String base = StatCollector.translateToLocal(key);
            return suffix == null ? base : base + " " + suffix;
        }

        public String getMessage() {
            return StatCollector.translateToLocal("dn.will.desc") + " "
                + msg()
                + " "
                + StatCollector.translateToLocal("dn.from_slot.desc");
        }

        public String getTooltip() {
            final String msg = msg();
            if (this == KEEP_ALL) {
                return StatCollector.translateToLocal("dn.do.desc") + " " + msg;
            }
            return msg.isEmpty() ? msg
                : msg.substring(0, 1)
                    .toUpperCase() + msg.substring(1);
        }
    }

    public enum ItemPlacementMode {

        KEEP_ALL(Integer.MAX_VALUE, "dn.not_place.desc", null),
        KEEP_1(1, "dn.place_all_but.desc", "1"),
        KEEP_16(16, "dn.place_all_but.desc", "16"),
        KEEP_64(64, "dn.place_all_but.desc", "64"),
        KEEP_NONE(0, "dn.place_all.desc", null);

        public static final ItemPlacementMode[] VALUES = values();

        private final int number;
        private final String key;
        private final String suffix;

        ItemPlacementMode(final int numberToKeep, final String key, final String suffix) {
            this.number = numberToKeep;
            this.key = key;
            this.suffix = suffix;
        }

        public int getNumberToKeep() {
            return number;
        }

        private String msg() {
            final String base = StatCollector.translateToLocal(key);
            return suffix == null ? base : base + " " + suffix;
        }

        public String getMessage() {
            return StatCollector.translateToLocal("dn.will.desc") + " "
                + msg()
                + " "
                + StatCollector.translateToLocal("dn.from_slot.desc");
        }

        public String getTooltip() {
            final String msg = msg();
            if (this == KEEP_ALL) {
                return StatCollector.translateToLocal("dn.do.desc") + " " + msg;
            }
            return msg.isEmpty() ? msg
                : msg.substring(0, 1)
                    .toUpperCase() + msg.substring(1);
        }
    }
}
