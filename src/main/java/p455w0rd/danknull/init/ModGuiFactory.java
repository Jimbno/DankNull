package p455w0rd.danknull.init;

import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;

import com.google.common.collect.Lists;

import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;

/**
 * @author p455w0rd
 */
// 1.7.10's IModGuiFactory predates hasConfigGui()/createConfigGui(GuiScreen); it instead asks for a GuiScreen class
// with a (GuiScreen parent) constructor, which FML reflectively instantiates from the mod list. Upstream's screen is
// therefore moved into the nested GuiDankNullConfig below.
public class ModGuiFactory implements IModGuiFactory {

    private static final String TITLE = ModGlobals.NAME + " Config";

    @Override
    public void initialize(final Minecraft minecraftInstance) {}

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return GuiDankNullConfig.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(final RuntimeOptionCategoryElement element) {
        return null;
    }

    public static class GuiDankNullConfig extends GuiConfig {

        public GuiDankNullConfig(final GuiScreen parent) {
            super(
                parent,
                getConfigElements(),
                ModGlobals.MODID,
                false,
                false,
                TITLE,
                GuiConfig.getAbridgedConfigPath(
                    ModConfig.getInstance()
                        .getConfigFile()
                        .getAbsolutePath()));
        }

        /**
         * Upstream only listed the client category. The server category is added here as a single sub-category
         * button, so the same screen can edit both without flattening them together.
         */
        @SuppressWarnings("rawtypes")
        private static List<IConfigElement> getConfigElements() {
            final List<IConfigElement> elements = Lists.<IConfigElement>newArrayList();
            elements.addAll(ModConfig.getClientConfigElements());
            elements.add(
                new ConfigElement<Object>(
                    ModConfig.getInstance()
                        .getCategory(ModConfig.SERVER_CAT)));
            return elements;
        }

    }

}
