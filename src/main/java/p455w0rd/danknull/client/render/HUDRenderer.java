package p455w0rd.danknull.client.render;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import p455w0rd.danknull.api.DankNullItemModes.ItemPlacementMode;
import p455w0rd.danknull.api.IDankNullHandler;
import p455w0rd.danknull.init.ModConfig;
import p455w0rd.danknull.init.ModConfig.Options;
import p455w0rd.danknull.init.ModGlobals;
import p455w0rd.danknull.init.ModGlobals.DankNullTier;
import p455w0rd.danknull.inventory.DankNullHandler;
import p455w0rd.danknull.items.ItemDankNull;
import p455w0rd.danknull.util.DankNullStackUtils;
import p455w0rd.danknull.util.DankNullUtils;

/**
 * The in-world overlay describing the held /dank/null: its name, the selected stack, and that stack's modes.
 *
 * <p>
 * Called from {@code ModEvents}' {@code RenderGameOverlayEvent.Post} handler.
 * </p>
 *
 * <p>
 * Changes from 1.12: p455w0rdslib's {@code GuiUtils}/{@code RenderUtils} are gone, so the textured-rect draw
 * lives in {@link RenderHelpers} and the item is drawn through {@code RenderItem.getInstance()}; there is no
 * off-hand, so the off-hand fallback is dropped; and the GL state this overlay touches is now saved and restored
 * (1.12 left blend and alpha enabled and called {@code enableGUIStandardItemLighting} with no matching
 * {@code disableStandardItemLighting}, which under Angelica's state tracker would corrupt later draws).
 * </p>
 *
 * @author p455w0rd
 */
public class HUDRenderer {

    private static final ResourceLocation HUD_TEXTURE = new ResourceLocation(
        ModGlobals.MODID,
        "textures/gui/danknullscreen0.png");

    @SideOnly(Side.CLIENT)
    public static void renderHUD(final Minecraft mc, final ScaledResolution scaledRes) {
        if (!Options.showHUD || ModGlobals.GUI_DANKNULL_ISOPEN) {
            return;
        }
        if (mc == null || mc.thePlayer == null || scaledRes == null) {
            return;
        }
        if (mc.playerController != null && !mc.playerController.shouldDrawHUD()
            && !mc.thePlayer.capabilities.isCreativeMode) {
            return;
        }
        final ItemStack currentItem = mc.thePlayer.inventory.getCurrentItem();
        if (!ItemDankNull.isDankNull(currentItem)) {
            return;
        }
        final IDankNullHandler handler = DankNullUtils.getHandler(currentItem);
        if (handler == null || handler.getSelected() < 0) {
            return;
        }
        final ItemStack selectedStack = handler.getFullStackInSlot(handler.getSelected());
        final TextureManager tm = mc.renderEngine;
        if (tm == null || DankNullStackUtils.isEmpty(selectedStack)) {
            return;
        }

        final int width = scaledRes.getScaledWidth();
        final int height = scaledRes.getScaledHeight();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            tm.bindTexture(HUD_TEXTURE);
            RenderHelpers.drawTexturedModalRect(width - 106, height - 45, 0, 210, 106, 45, 0);

            GL11.glPushMatrix();
            try {
                GL11.glScalef(0.5F, 0.5F, 0.5F);
                drawText(mc, handler, currentItem, selectedStack, width, height);
            } finally {
                GL11.glPopMatrix();
            }

            RenderHelper.enableGUIStandardItemLighting();
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            RenderItem.getInstance()
                .renderItemAndEffectIntoGUI(mc.fontRenderer, tm, currentItem, width - 106 + 5, height - 20);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            RenderHelper.disableStandardItemLighting();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    /** Drawn inside a 0.5 scale, hence the doubled coordinates - kept exactly as 1.12 laid them out. */
    private static void drawText(final Minecraft mc, final IDankNullHandler handler, final ItemStack dankNull,
        final ItemStack selectedStack, final int width, final int height) {
        final int left = width * 2 - 212;
        final int bottom = height * 2;

        mc.fontRenderer.drawStringWithShadow(
            dankNull.getDisplayName(),
            left + 55,
            bottom - 83,
            handler.getTier()
                .getHexColor(true));

        String selectedStackName = selectedStack.getDisplayName();
        final int itemNameWidth = mc.fontRenderer.getStringWidth(selectedStackName);
        if (itemNameWidth >= 88 && selectedStackName.length() >= 14) {
            selectedStackName = selectedStackName.substring(0, 14)
                .trim() + "...";
        }
        mc.fontRenderer.drawStringWithShadow(
            StatCollector.translateToLocal("dn.selected_item.desc") + ": " + selectedStackName,
            left + 45,
            bottom - 72,
            0xFFFFFF);

        final String count = ItemDankNull.getTier(dankNull) == DankNullTier.CREATIVE
            ? StatCollector.translateToLocal("dn.infinite.desc")
            : String.valueOf(DankNullStackUtils.getCount(selectedStack));
        mc.fontRenderer.drawStringWithShadow(
            StatCollector.translateToLocal("dn.count.desc") + ": " + count,
            left + 45,
            bottom - 61,
            0xFFFFFF);

        final ItemPlacementMode placementMode = handler.getPlacementMode(selectedStack);
        final String extractLabel = StatCollector.translateToLocal("dn.extract.desc");
        final String placeLabel = StatCollector.translateToLocal("dn.place.desc");
        final String placementTooltip = placementMode.getTooltip()
            .replace(extractLabel.toLowerCase(Locale.ENGLISH), placeLabel.toLowerCase(Locale.ENGLISH))
            .replace(extractLabel, placeLabel);
        mc.fontRenderer.drawStringWithShadow(placeLabel + ": " + placementTooltip, left + 45, bottom - 50, 0xFFFFFF);
        mc.fontRenderer.drawStringWithShadow(
            extractLabel + ": "
                + handler.getExtractionMode(selectedStack)
                    .getTooltip(),
            left + 45,
            bottom - 40,
            0xFFFFFF);

        // The open keybind used to be reported on its own line here. It told a player who had already opened the
        // /dank/null how to open it, and spent a line of a six-line card nagging about an unbound key. The ore
        // dictionary line moves up into the space rather than leaving a hole.
        String oreDictMode = StatCollector.translateToLocal("dn.ore_dictionary.desc") + ": "
            + (handler.isOre(selectedStack) ? StatCollector.translateToLocal("dn.enabled.desc")
                : StatCollector.translateToLocal("dn.disabled.desc"));
        if (DankNullHandler.getOreNames(selectedStack)
            .isEmpty()) {
            oreDictMode = StatCollector.translateToLocal("dn.not_oredicted.desc");
        }
        mc.fontRenderer.drawStringWithShadow(oreDictMode, left + 45, bottom - 29, 0xFFFFFF);
    }

    public static void toggleHUD() {
        Options.showHUD = !Options.showHUD;
        ModConfig.getInstance()
            .get(ModConfig.CATEGORY_CLIENT, "showHUD", true)
            .set(Options.showHUD);
        ModConfig.getInstance()
            .save();
    }
}
