package p455w0rd.danknull.client.render;

import net.minecraft.client.renderer.Tessellator;

/**
 * The handful of drawing helpers the port needs from p455w0rdslib's {@code GuiUtils}/{@code RenderUtils}, which are
 * 1.12-only and not available here.
 */
final class RenderHelpers {

    /** Vanilla GUI sheets are 256x256, so one texel is 1/256 of the sheet. */
    private static final float TEXEL = 1.0F / 256.0F;

    private RenderHelpers() {}

    /**
     * p455w0rdslib's {@code GuiUtils.drawTexturedModalRect}: identical to {@link net.minecraft.client.gui.Gui}'s
     * except the z level is passed in rather than read off a {@code Gui} instance.
     */
    static void drawTexturedModalRect(final int x, final int y, final int u, final int v, final int width,
        final int height, final float zLevel) {
        final Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, zLevel, u * TEXEL, (v + height) * TEXEL);
        tessellator.addVertexWithUV(x + width, y + height, zLevel, (u + width) * TEXEL, (v + height) * TEXEL);
        tessellator.addVertexWithUV(x + width, y, zLevel, (u + width) * TEXEL, v * TEXEL);
        tessellator.addVertexWithUV(x, y, zLevel, u * TEXEL, v * TEXEL);
        tessellator.draw();
    }
}
