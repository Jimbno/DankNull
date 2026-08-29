package p455w0rd.danknull.client.gui;

import static p455w0rd.danknull.util.DankNullStackUtils.copyWithSize;
import static p455w0rd.danknull.util.DankNullStackUtils.getCount;
import static p455w0rd.danknull.util.DankNullStackUtils.isEmpty;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.google.common.collect.Lists;

import cpw.mods.fml.relauncher.ReflectionHelper;
import p455w0rd.danknull.api.DankNullItemModes.ItemExtractionMode;
import p455w0rd.danknull.api.DankNullItemModes.ItemPlacementMode;
import p455w0rd.danknull.api.IDankNullHandler;
import p455w0rd.danknull.container.ContainerDankNull;
import p455w0rd.danknull.init.ModConfig;
import p455w0rd.danknull.init.ModConfig.Options;
import p455w0rd.danknull.init.ModGlobals;
import p455w0rd.danknull.init.ModGlobals.DankNullTier;
import p455w0rd.danknull.init.ModNetworking;
import p455w0rd.danknull.inventory.DankNullHandler;
import p455w0rd.danknull.inventory.slot.SlotDankNull;
import p455w0rd.danknull.network.PacketChangeMode;
import p455w0rd.danknull.util.DankNullStackUtils;
import yalter.mousetweaks.api.MouseTweaksIgnore;

/**
 * The /dank/null screen.
 *
 * <p>
 * Upstream extended {@code p455w0rdslib.client.gui.GuiModular} and leaned on {@code GuiUtils},
 * {@code RenderUtils}, {@code ReadableNumberConverter} and the library's Thaumcraft hook. None of that library
 * exists for 1.7.10, so this extends {@link GuiContainer} directly and reimplements the handful of helpers that
 * were actually used - slot drawing, a border-coloured tooltip and a large-number abbreviator. The Thaumcraft and
 * Chisel integrations are dropped along with the rest of the mod integrations.
 * </p>
 *
 * <p>
 * {@link GuiContainer#drawScreen} is replaced rather than extended because vanilla's slot renderer
 * ({@code func_146977_a}) is private and would draw raw six-digit stack sizes over the /dank/null's slots.
 * Replacing it also drops the drag/split visuals vanilla draws out of its private state, so those are rebuilt
 * here - see the "vanilla drag/split state" section below.
 * </p>
 *
 * @author p455w0rd
 */
@MouseTweaksIgnore
public class GuiDankNull extends GuiContainer {

    /** Upstream's {@code GuiModular} width; the background texture is drawn at this width. */
    private static final int BACKGROUND_WIDTH = 210;
    /** Upstream's shadowed {@code xSize}, used to place the header text and the "selected" marker. */
    private static final int CONTENT_WIDTH = 201;
    private static final char[] NUMBER_SUFFIXES = { 'K', 'M', 'G', 'T', 'P', 'E' };
    /** Vanilla's slot tint, used both for the hovered slot and for the click-drag preview. */
    private static final int SLOT_TINT = -2130706433;

    // ------------------------------------------------------------------
    // vanilla drag/split state
    //
    // Replacing drawScreen means reproducing what vanilla drew out of GuiContainer's private state. Two of the
    // fields the click-drag preview needs are in fact accessible - field_147007_t (the drag flag) and
    // field_147008_s (the dragged-over slots) are protected - and Container's drag maths (func_94527_a /
    // func_94525_a) is public static, so the preview is recomputed rather than reflected: the drag mode is
    // mirrored out of mouseClicked, where vanilla derives it from nothing but the mouse button, and the
    // remainder is recalculated once per frame with vanilla's own algorithm. That keeps the one drag visual
    // players actually see free of any reflection.
    //
    // The touchscreen-only "dragged stack" and its snap-back animation have no accessible state at all, so
    // those fields are read reflectively, by SRG name first and MCP name second (dev runs deobfuscated). Every
    // read is guarded and falls back to the plain rendering, so a missing or renamed field can only cost the
    // touchscreen preview - it can never throw while drawing.
    // ------------------------------------------------------------------

    private static final Field CLICKED_SLOT = findGuiContainerField("field_147005_v", "clickedSlot");
    private static final Field IS_RIGHT_MOUSE_CLICK = findGuiContainerField("field_147004_w", "isRightMouseClick");
    private static final Field DRAGGED_STACK = findGuiContainerField("field_147012_x", "draggedStack");
    // These two never got an MCP name, so the SRG name is the only one they ever have.
    private static final Field TOUCH_UP_X = findGuiContainerField("field_147011_y");
    private static final Field TOUCH_UP_Y = findGuiContainerField("field_147010_z");
    private static final Field RETURNING_STACK = findGuiContainerField("field_146991_C", "returningStack");
    private static final Field RETURNING_STACK_DEST_SLOT = findGuiContainerField(
        "field_146989_A",
        "returningStackDestSlot");
    private static final Field RETURNING_STACK_TIME = findGuiContainerField("field_146990_B", "returningStackTime");

    private final DankNullTier tier;
    private final ResourceLocation background;
    private Slot theSlot;
    /** Mirror of the private {@code GuiContainer#field_146987_F}: 0 spreads the stack evenly, 1 drops one each. */
    private int dragSplittingLimit;
    /** Mirror of the private {@code GuiContainer#field_146996_I}: what the drag would leave on the cursor. */
    private int dragSplittingRemnant;

    /**
     * Resolves one of {@link GuiContainer}'s private fields, or {@code null} if it cannot be found. Never throws -
     * a {@code null} handle only means the matching preview is skipped.
     */
    private static Field findGuiContainerField(final String... names) {
        try {
            final Field field = ReflectionHelper.findField(GuiContainer.class, names);
            field.setAccessible(true);
            return field;
        } catch (final Throwable t) {
            return null;
        }
    }

    public GuiDankNull(final ContainerDankNull container) {
        super(container);
        tier = container.getHandler()
            .getTier();
        background = tier.getGuiBackground();
        xSize = BACKGROUND_WIDTH;
        ySize = tier.getGuiHeight();
    }

    public IDankNullHandler getDankNullHandler() {
        return ((ContainerDankNull) inventorySlots).getHandler();
    }

    @Override
    public void initGui() {
        super.initGui();
        ModGlobals.GUI_DANKNULL_ISOPEN = true;
        buttonList.clear();
        if (mc.thePlayer != null && mc.thePlayer.capabilities.isCreativeMode && tier.isCreative()) {
            buttonList.add(
                new GuiButton(
                    0,
                    guiLeft + CONTENT_WIDTH / 2 - 25,
                    guiTop - 20,
                    50,
                    20,
                    getLockButtonLabel(getDankNullHandler().isLocked())));
        }
    }

    private static String getLockButtonLabel(final boolean locked) {
        return StatCollector.translateToLocal(locked ? "dn.unlock.desc" : "dn.lock.desc");
    }

    /**
     * Upstream toggled the lock by comparing the button's display string against the localised "Lock" text, which
     * silently breaks in any language whose translation differs. The handler's own state is used instead.
     */
    @Override
    protected void actionPerformed(final GuiButton btn) {
        if (btn.id != 0) {
            return;
        }
        final IDankNullHandler handler = getDankNullHandler();
        if (handler == null) {
            return;
        }
        final boolean nowLocked = !handler.isLocked();
        handler.setLocked(nowLocked);
        btn.displayString = getLockButtonLabel(nowLocked);
        // TODO(net): PacketChangeMode(ChangeType.LOCK / ChangeType.UNLOCK)
        ModNetworking.getInstance()
            .sendToServer(
                new PacketChangeMode(
                    nowLocked ? PacketChangeMode.ChangeType.LOCK : PacketChangeMode.ChangeType.UNLOCK));
    }

    @Override
    public void onGuiClosed() {
        ModGlobals.GUI_DANKNULL_ISOPEN = false;
        super.onGuiClosed();
    }

    @Override
    public void updateScreen() {
        if (mc.thePlayer == null) {
            mc.displayGuiScreen(null);
            return;
        }
        if (!mc.thePlayer.isEntityAlive() || mc.thePlayer.isDead) {
            mc.thePlayer.closeScreen();
        }
    }

    // ------------------------------------------------------------------
    // input
    // ------------------------------------------------------------------

    /**
     * The per-stack mode controls (Ctrl / Alt / O / P + left click).
     *
     * <p>
     * Upstream drove these from {@code ModEvents.onMouseEventCustom}, listening to
     * {@code GuiScreenEvent.MouseInputEvent}. 1.7.10's {@code GuiScreenEvent} has no mouse-input event at all, so
     * the handling lives here; swallowing the click (by not delegating to {@code super}) replaces upstream's
     * {@code event.setCanceled(true)}.
     * </p>
     */
    @Override
    protected void mouseClicked(final int mouseX, final int mouseY, final int mouseButton) {
        if (mouseButton == 0 && handleModeClick(mouseX, mouseY)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
        // GuiContainer#field_146987_F is private, but vanilla sets it from the mouse button at the very moment it
        // raises field_147007_t and never touches it again, so mirroring it here cannot drift out of step.
        if (field_147007_t) {
            dragSplittingLimit = mouseButton == 1 ? 1 : 0;
        }
    }

    private boolean handleModeClick(final int mouseX, final int mouseY) {
        final Slot hoveredSlot = getSlotAtPos(mouseX, mouseY);
        if (!(hoveredSlot instanceof SlotDankNull) || !hoveredSlot.getHasStack()) {
            return false;
        }
        final IDankNullHandler handler = getDankNullHandler();
        if (handler == null) {
            return false;
        }
        final ItemStack hoveredStack = hoveredSlot.getStack();
        final int slotIndex = hoveredSlot.getSlotIndex();
        final boolean ctrl = GuiScreen.isCtrlKeyDown();
        // 1.7.10's GuiScreen has no isAltKeyDown().
        final boolean alt = Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);

        if (ctrl && !alt) {
            handler.cycleExtractionMode(hoveredStack, true);
            // TODO(net): PacketChangeMode(ItemExtractionMode, int slot)
            ModNetworking.getInstance()
                .sendToServer(new PacketChangeMode(handler.getExtractionMode(hoveredStack), slotIndex));
            return true;
        }
        if (alt && !ctrl) {
            if (handler.getSelected() < 0 || !DankNullStackUtils
                .areItemStacksEqualIgnoreSize(handler.getFullStackInSlot(handler.getSelected()), hoveredStack)) {
                handler.setSelected(slotIndex);
                // TODO(net): PacketChangeMode(ChangeType.SELECTED, int slot)
                ModNetworking.getInstance()
                    .sendToServer(new PacketChangeMode(PacketChangeMode.ChangeType.SELECTED, slotIndex));
                return true;
            }
            return false;
        }
        if (ctrl || alt) {
            return false;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_O)) {
            if (isOreDictConfigurable(hoveredStack)) {
                handler.setOre(hoveredStack, !handler.isOre(hoveredStack));
                // TODO(net): PacketChangeMode(ChangeType.ORE_ON / ORE_OFF, int slot)
                ModNetworking.getInstance()
                    .sendToServer(
                        new PacketChangeMode(
                            handler.isOre(hoveredStack) ? PacketChangeMode.ChangeType.ORE_ON
                                : PacketChangeMode.ChangeType.ORE_OFF,
                            slotIndex));
                return true;
            }
            return false;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_P)) {
            handler.cyclePlacementMode(hoveredStack, true);
            // TODO(net): PacketChangeMode(ItemPlacementMode, int slot)
            ModNetworking.getInstance()
                .sendToServer(new PacketChangeMode(handler.getPlacementMode(hoveredStack), slotIndex));
            return true;
        }
        return false;
    }

    private static boolean isOreDictConfigurable(final ItemStack stack) {
        if (Options.disableOreDictMode) {
            return false;
        }
        if (ModConfig.isOreDictBlacklistEnabled()) {
            return !ModConfig.isItemOreDictBlacklisted(stack);
        }
        if (ModConfig.isOreDictWhitelistEnabled()) {
            return ModConfig.isItemOreDictWhitelisted(stack);
        }
        return true;
    }

    @Override
    protected void keyTyped(final char typedChar, final int keyCode) {
        if (keyCode == 1 || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.thePlayer.closeScreen();
            return;
        }
        if (theSlot != null && theSlot.getHasStack()) {
            if (keyCode == mc.gameSettings.keyBindPickBlock.getKeyCode()) {
                handleMouseClick(theSlot, theSlot.slotNumber, 0, 3);
            } else if (keyCode == mc.gameSettings.keyBindDrop.getKeyCode()) {
                handleMouseClick(
                    theSlot,
                    theSlot.slotNumber,
                    isCtrlKeyDown() && !(theSlot instanceof SlotDankNull) ? 1 : 0,
                    4);
            }
        }
    }

    /**
     * 1.12's {@code mouseReleased} is {@code mouseMovedOrUp} here.
     */
    @Override
    protected void mouseMovedOrUp(final int mouseX, final int mouseY, final int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        inventorySlots.detectAndSendChanges();
    }

    // ------------------------------------------------------------------
    // slot lookup
    // ------------------------------------------------------------------

    public Slot getSlotByIndex(final int index) {
        final List<Slot> slots = inventorySlots.inventorySlots;
        final int listIndex = index + ContainerDankNull.PLAYER_SLOT_COUNT;
        if (listIndex < 0 || listIndex >= slots.size()) {
            return null;
        }
        return slots.get(listIndex);
    }

    public Slot getSlotAtPos(final int x, final int y) {
        final List<Slot> slots = inventorySlots.inventorySlots;
        for (int i = 0; i < slots.size(); i++) {
            if (isMouseHovering(slots.get(i), x, y)) {
                return slots.get(i);
            }
        }
        return null;
    }

    private boolean isMouseHovering(final Slot slot, final int mouseX, final int mouseY) {
        final int x = mouseX - guiLeft;
        final int y = mouseY - guiTop;
        return x >= slot.xDisplayPosition - 1 && x < slot.xDisplayPosition + 17
            && y >= slot.yDisplayPosition - 1
            && y < slot.yDisplayPosition + 17;
    }

    // ------------------------------------------------------------------
    // rendering
    // ------------------------------------------------------------------

    @Override
    protected void drawGuiContainerBackgroundLayer(final float partialTicks, final int mouseX, final int mouseY) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager()
            .bindTexture(background);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(final int mouseX, final int mouseY) {
        final IDankNullHandler handler = getDankNullHandler();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_LIGHTING);
        final int fontColor = 0xFFFFFF;
        final int yOffset = tier.getNumRows() * 20 + 18 + tier.getNumRows() - 1;
        final String name = "/d" + (Options.callItDevNull ? "ev" : "ank") + "/null";
        fontRendererObj.drawStringWithShadow(name, 7, 6, tier.getHexColor(true));
        fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"), 7, yOffset, fontColor);
        if (handler.getSelected() > -1) {
            fontRendererObj
                .drawString("=" + StatCollector.translateToLocal("dn.selected.desc"), CONTENT_WIDTH - 64, 6, fontColor);
        }
        GL11.glEnable(GL11.GL_LIGHTING);
    }

    private int getSelectionBoxColor() {
        return tier.ordinal() == 0 ? 0xFFFFFF00 : -1140916224;
    }

    private void drawSelectionBox(final int x) {
        final int color = getSelectionBoxColor();
        drawGradientRect(x - 75, 4, x - 66, 5, color, color);
        drawGradientRect(x - 75, 4, x - 74, 14, color, color);
        drawGradientRect(x - 75, 13, x - 66, 14, color, color);
        drawGradientRect(x - 66, 4, x - 65, 14, color, color);
    }

    private void drawSelectionBox(final int x, final int y) {
        final int color = getSelectionBoxColor();
        drawGradientRect(x - 1, y - 1, x + 16, y, color, color);
        drawGradientRect(x - 1, y - 1, x, y + 17, color, color);
        drawGradientRect(x + 16, y - 1, x + 17, y + 17, color, color);
        drawGradientRect(x - 1, y + 16, x + 17, y + 17, color, color);
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        final IDankNullHandler handler = getDankNullHandler();
        if (handler == null) {
            // The /dank/null left its slot this tick; the container closes itself on the next one.
            return;
        }
        drawDefaultBackground();
        drawGuiContainerBackgroundLayer(partialTicks, mouseX, mouseY);

        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        for (int i = 0; i < buttonList.size(); i++) {
            buttonList.get(i)
                .drawButton(mc, mouseX, mouseY);
        }
        RenderHelper.enableGUIStandardItemLighting();

        GL11.glPushMatrix();
        GL11.glTranslatef(guiLeft, guiTop, 0.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        if (handler.getSelected() > -1) {
            drawSelectionBox(CONTENT_WIDTH);
        }
        updateDragSplittingRemnant();
        theSlot = null;
        final List<Slot> slots = inventorySlots.inventorySlots;
        for (int i = 0; i < slots.size(); i++) {
            final Slot slot = slots.get(i);
            if (slot instanceof SlotDankNull) {
                drawDankNullSlot(slot);
            } else {
                drawVanillaSlot(slot);
            }
            if (isMouseHovering(slot, mouseX, mouseY)) {
                theSlot = slot;
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glColorMask(true, true, true, false);
                drawGradientRect(
                    slot.xDisplayPosition,
                    slot.yDisplayPosition,
                    slot.xDisplayPosition + 16,
                    slot.yDisplayPosition + 16,
                    SLOT_TINT,
                    SLOT_TINT);
                GL11.glColorMask(true, true, true, true);
                GL11.glEnable(GL11.GL_LIGHTING);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            }
            if (handler.getSelected() == i - ContainerDankNull.PLAYER_SLOT_COUNT) {
                drawSelectedMarker(i, slot);
            }
        }

        GL11.glDisable(GL11.GL_LIGHTING);
        drawGuiContainerForegroundLayer(mouseX, mouseY);
        GL11.glEnable(GL11.GL_LIGHTING);

        final ItemStack heldStack = mc.thePlayer.inventory.getItemStack();
        drawCursorStack(heldStack, mouseX, mouseY);
        drawReturningStack();
        GL11.glPopMatrix();

        if (isEmpty(heldStack) && theSlot != null && theSlot.getHasStack()) {
            renderToolTip(theSlot.getStack(), mouseX, mouseY);
        }

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableStandardItemLighting();
    }

    private void drawSelectedMarker(final int slotListIndex, final Slot slot) {
        final IDankNullHandler handler = getDankNullHandler();
        GL11.glDisable(GL11.GL_LIGHTING);
        final int index = handler.getSelected();
        if (index != -1) {
            final Slot selected = getSlotByIndex(index);
            if (selected != null && selected.getHasStack()) {
                drawSelectionBox(slot.xDisplayPosition, slot.yDisplayPosition);
            } else {
                for (int i = index; i >= 0; i--) {
                    final Slot candidate = getSlotByIndex(i);
                    if (candidate != null && candidate.getHasStack()) {
                        drawSelectionBox(candidate.xDisplayPosition, candidate.yDisplayPosition);
                        break;
                    }
                }
            }
        } else {
            for (int i = slotListIndex - (ContainerDankNull.PLAYER_SLOT_COUNT - 1); i
                < tier.getNumRowsMultiplier() * 9; i++) {
                final Slot candidate = getSlotByIndex(i);
                if (candidate != null && candidate.getHasStack()) {
                    drawSelectionBox(candidate.xDisplayPosition, candidate.yDisplayPosition);
                    break;
                }
            }
        }
        GL11.glEnable(GL11.GL_LIGHTING);
    }

    // ------------------------------------------------------------------
    // vanilla drag/split visuals
    // ------------------------------------------------------------------

    /** What a single slot should draw this frame, once vanilla's drag/touch previews are taken into account. */
    private static final class SlotPreview {

        ItemStack stack;
        String altText;
        /** The slot is a click-drag target: vanilla tints it behind the previewed stack. */
        boolean highlight;
        /** The slot's contents are riding the cursor, so nothing is drawn for the slot itself. */
        boolean suppressItem;
        /** The slot is the only click-drag target so far: vanilla draws nothing at all for it. */
        boolean skip;

        SlotPreview(final ItemStack stack) {
            this.stack = stack;
        }
    }

    /**
     * Vanilla's per-slot preview logic, lifted out of {@code GuiContainer#func_146977_a} so both slot renderers
     * can share it: the click-drag distribution ghost, and the touchscreen slot a stack is being dragged out of.
     */
    private SlotPreview computeSlotPreview(final Slot slot) {
        final SlotPreview preview = new SlotPreview(slot.getStack());
        final ItemStack draggedStack = getDraggedStack();
        final ItemStack heldStack = mc.thePlayer.inventory.getItemStack();
        final boolean isTouchDragSource = draggedStack != null && slot == getClickedSlot();
        if (isTouchDragSource && !isRightMouseClick()) {
            preview.suppressItem = true;
        }
        if (isTouchDragSource && isRightMouseClick() && !isEmpty(preview.stack)) {
            preview.stack = preview.stack.copy();
            preview.stack.stackSize /= 2;
        } else if (field_147007_t && field_147008_s.contains(slot) && !isEmpty(heldStack)) {
            if (field_147008_s.size() == 1) {
                preview.skip = true;
                return preview;
            }
            if (Container.func_94527_a(slot, heldStack, true) && inventorySlots.canDragIntoSlot(slot)) {
                final ItemStack split = heldStack.copy();
                Container.func_94525_a(field_147008_s, dragSplittingLimit, split, getCount(slot.getStack()));
                if (split.stackSize > split.getMaxStackSize()) {
                    preview.altText = EnumChatFormatting.YELLOW.toString() + split.getMaxStackSize();
                    split.stackSize = split.getMaxStackSize();
                }
                if (split.stackSize > slot.getSlotStackLimit()) {
                    preview.altText = EnumChatFormatting.YELLOW.toString() + slot.getSlotStackLimit();
                    split.stackSize = slot.getSlotStackLimit();
                }
                preview.stack = split;
                preview.highlight = true;
            } else {
                // Vanilla drops slots that stopped being a valid target mid-drag, then re-derives the remainder.
                field_147008_s.remove(slot);
                updateDragSplittingRemnant();
            }
        }
        return preview;
    }

    /**
     * Vanilla's private {@code GuiContainer#func_146980_g}: how much of the held stack a click-drag would leave on
     * the cursor. Recomputed once per frame rather than tracked, since the field vanilla keeps it in is private.
     */
    private void updateDragSplittingRemnant() {
        final ItemStack heldStack = mc.thePlayer.inventory.getItemStack();
        if (!field_147007_t || isEmpty(heldStack)) {
            dragSplittingRemnant = 0;
            return;
        }
        int remnant = getCount(heldStack);
        for (final Slot slot : field_147008_s) {
            final ItemStack split = heldStack.copy();
            final int existing = getCount(slot.getStack());
            Container.func_94525_a(field_147008_s, dragSplittingLimit, split, existing);
            if (split.stackSize > split.getMaxStackSize()) {
                split.stackSize = split.getMaxStackSize();
            }
            if (split.stackSize > slot.getSlotStackLimit()) {
                split.stackSize = slot.getSlotStackLimit();
            }
            remnant -= split.stackSize - existing;
        }
        dragSplittingRemnant = remnant;
    }

    /**
     * The stack under the cursor: the held stack, or - on a touchscreen - the one being dragged out of a slot.
     * While a click-drag is running the count shown is what the drag would leave behind, as in vanilla.
     */
    private void drawCursorStack(final ItemStack heldStack, final int mouseX, final int mouseY) {
        final ItemStack draggedStack = getDraggedStack();
        ItemStack stack = draggedStack == null ? heldStack : draggedStack;
        if (isEmpty(stack)) {
            return;
        }
        String altText = null;
        if (draggedStack != null && isRightMouseClick()) {
            stack = stack.copy();
            stack.stackSize = MathHelper.ceiling_float_int(stack.stackSize / 2.0F);
        } else if (field_147007_t && field_147008_s.size() > 1) {
            stack = stack.copy();
            stack.stackSize = dragSplittingRemnant;
            if (stack.stackSize == 0) {
                altText = EnumChatFormatting.YELLOW.toString() + "0";
            }
        }
        final int overlayYOffset = draggedStack == null ? 0 : 8;
        drawStack(stack, mouseX - guiLeft - 8, mouseY - guiTop - 8 - overlayYOffset, altText, overlayYOffset);
    }

    /**
     * Vanilla's touchscreen "snap back" animation, tweened from where the drag was released to the source slot.
     */
    private void drawReturningStack() {
        final ItemStack returningStack = getReturningStack();
        final Slot destination = getReturningStackDestSlot();
        if (isEmpty(returningStack) || destination == null) {
            return;
        }
        final float progress = (Minecraft.getSystemTime() - getReturningStackTime()) / 100.0F;
        if (progress >= 1.0F) {
            // Vanilla clears the field on the final frame; if it cannot be written the animation is just dropped,
            // which keeps a failed reflective write from pinning the stack to the screen forever.
            clearReturningStack();
            return;
        }
        final int fromX = getTouchUpX();
        final int fromY = getTouchUpY();
        final int x = fromX + (int) ((destination.xDisplayPosition - fromX) * progress);
        final int y = fromY + (int) ((destination.yDisplayPosition - fromY) * progress);
        drawStack(returningStack, x, y, null, 0);
    }

    // ------------------------------------------------------------------
    // reflective reads of GuiContainer's touchscreen-only state
    //
    // All of these return the "nothing is being dragged" answer if the field could not be resolved, which is
    // exactly the state the GUI rendered before any of this existed.
    // ------------------------------------------------------------------

    private static <T> T read(final Field field, final Object instance, final Class<T> type, final T fallback) {
        if (field == null) {
            return fallback;
        }
        try {
            final Object value = field.get(instance);
            return type.isInstance(value) ? type.cast(value) : fallback;
        } catch (final Throwable t) {
            return fallback;
        }
    }

    private Slot getClickedSlot() {
        return read(CLICKED_SLOT, this, Slot.class, null);
    }

    private boolean isRightMouseClick() {
        return read(IS_RIGHT_MOUSE_CLICK, this, Boolean.class, Boolean.FALSE).booleanValue();
    }

    private ItemStack getDraggedStack() {
        return read(DRAGGED_STACK, this, ItemStack.class, null);
    }

    private int getTouchUpX() {
        return read(TOUCH_UP_X, this, Integer.class, Integer.valueOf(0)).intValue();
    }

    private int getTouchUpY() {
        return read(TOUCH_UP_Y, this, Integer.class, Integer.valueOf(0)).intValue();
    }

    private ItemStack getReturningStack() {
        return read(RETURNING_STACK, this, ItemStack.class, null);
    }

    private Slot getReturningStackDestSlot() {
        return read(RETURNING_STACK_DEST_SLOT, this, Slot.class, null);
    }

    private long getReturningStackTime() {
        return read(RETURNING_STACK_TIME, this, Long.class, Long.valueOf(0L)).longValue();
    }

    private void clearReturningStack() {
        if (RETURNING_STACK == null) {
            return;
        }
        try {
            RETURNING_STACK.set(this, null);
        } catch (final Throwable t) {
            // ignored - drawReturningStack has already stopped drawing it
        }
    }

    /**
     * Reimplementation of vanilla's private {@code GuiContainer#func_146977_a}, including the click-drag
     * distribution preview and the touchscreen drag source.
     */
    private void drawVanillaSlot(final Slot slot) {
        final int x = slot.xDisplayPosition;
        final int y = slot.yDisplayPosition;
        final SlotPreview preview = computeSlotPreview(slot);
        if (preview.skip) {
            return;
        }
        zLevel = 100.0F;
        itemRender.zLevel = 100.0F;
        boolean drawn = preview.suppressItem;
        if (isEmpty(preview.stack)) {
            drawn |= drawSlotBackgroundIcon(slot, x, y);
        }
        if (!drawn && !isEmpty(preview.stack)) {
            if (preview.highlight) {
                drawRect(x, y, x + 16, y + 16, SLOT_TINT);
            }
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), preview.stack, x, y);
            itemRender.renderItemOverlayIntoGUI(
                fontRendererObj,
                mc.getTextureManager(),
                preview.stack,
                x,
                y,
                preview.altText);
        }
        itemRender.zLevel = 0.0F;
        zLevel = 0.0F;
    }

    /**
     * A /dank/null slot can hold far more than a byte's worth of items, so the item is drawn from a size-1 copy
     * (which suppresses vanilla's count text) and the real count is written underneath it, abbreviated and at half
     * scale.
     */
    private void drawDankNullSlot(final Slot slot) {
        final int x = slot.xDisplayPosition;
        final int y = slot.yDisplayPosition;
        final SlotPreview preview = computeSlotPreview(slot);
        if (preview.skip) {
            return;
        }
        zLevel = 100.0F;
        itemRender.zLevel = 100.0F;
        boolean drawn = preview.suppressItem;
        if (isEmpty(preview.stack)) {
            drawn |= drawSlotBackgroundIcon(slot, x, y);
        }
        if (!drawn && !isEmpty(preview.stack)) {
            if (preview.highlight) {
                drawRect(x, y, x + 16, y + 16, SLOT_TINT);
            }
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            final ItemStack renderStack = copyWithSize(preview.stack, 1);
            itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), renderStack, x, y);
            itemRender.renderItemOverlayIntoGUI(fontRendererObj, mc.getTextureManager(), renderStack, x, y, null);
            // The abbreviated count below already shows the previewed total, so vanilla's yellow "clamped to the
            // slot limit" overlay (preview.altText) is redundant here and would not fit at half scale anyway.
            drawStackCount(fontRendererObj, getCount(preview.stack), x, y);
        }
        itemRender.zLevel = 0.0F;
        zLevel = 0.0F;
    }

    /**
     * @return {@code true} if a background icon was drawn, in which case vanilla draws nothing else for the slot.
     */
    private boolean drawSlotBackgroundIcon(final Slot slot, final int x, final int y) {
        final IIcon icon = slot.getBackgroundIconIndex();
        if (icon == null) {
            return false;
        }
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        mc.getTextureManager()
            .bindTexture(slot.getBackgroundIconTexture());
        drawTexturedModelRectFromIcon(x, y, icon, 16, 16);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
        return true;
    }

    private void drawStackCount(final FontRenderer font, final int amount, final int x, final int y) {
        if (amount <= 1) {
            return;
        }
        final String text = toReadableForm(amount);
        final boolean unicodeFlag = font.getUnicodeFlag();
        font.setUnicodeFlag(false);
        final float scale = 0.5F;
        final float inverseScale = 1.0F / scale;
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, scale);
        final int textX = (int) ((x - 1 + 16.0F - font.getStringWidth(text) * scale) * inverseScale);
        final int textY = (int) ((y - 1 + 16.0F - 7.0F * scale) * inverseScale);
        font.drawStringWithShadow(text, textX, textY, 0xFFFFFF);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LIGHTING);
        font.setUnicodeFlag(unicodeFlag);
    }

    /**
     * @param overlayYOffset vanilla lifts the count/durability overlay by 8px while a stack is being dragged off a
     *                       touchscreen slot, because the icon itself is drawn 8px lower.
     */
    private void drawStack(final ItemStack stack, final int x, final int y, final String altText,
        final int overlayYOffset) {
        GL11.glTranslatef(0.0F, 0.0F, 32.0F);
        zLevel = 200.0F;
        itemRender.zLevel = 200.0F;
        FontRenderer font = stack.getItem()
            .getFontRenderer(stack);
        if (font == null) {
            font = fontRendererObj;
        }
        itemRender.renderItemAndEffectIntoGUI(font, mc.getTextureManager(), stack, x, y);
        itemRender.renderItemOverlayIntoGUI(font, mc.getTextureManager(), stack, x, y - overlayYOffset, altText);
        zLevel = 0.0F;
        itemRender.zLevel = 0.0F;
    }

    /**
     * Replacement for {@code p455w0rdslib.util.ReadableNumberConverter#toWideReadableForm} - at most four
     * characters, so the count still fits inside a 16x16 slot at half scale.
     */
    private static String toReadableForm(final long number) {
        if (number < 10000L) {
            return Long.toString(number);
        }
        double value = number;
        int suffix = -1;
        while (value >= 1000.0D && suffix < NUMBER_SUFFIXES.length - 1) {
            value /= 1000.0D;
            suffix++;
        }
        if (value < 10.0D) {
            final long tenths = (long) (value * 10.0D);
            return tenths / 10 + "." + tenths % 10 + NUMBER_SUFFIXES[suffix];
        }
        return (long) value + String.valueOf(NUMBER_SUFFIXES[suffix]);
    }

    // ------------------------------------------------------------------
    // tooltips
    // ------------------------------------------------------------------

    /**
     * Upstream's tooltip, minus the Chisel and Thaumcraft integrations (out of scope for this backport).
     */
    @SuppressWarnings("unchecked")
    @Override
    protected void renderToolTip(final ItemStack stack, final int x, final int y) {
        final List<String> list = isEmpty(stack) ? Lists.<String>newArrayList()
            : (List<String>) stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
        for (int i = 0; i < list.size(); ++i) {
            if (i == 0) {
                list.set(i, stack.getRarity().rarityColor + list.get(i));
            } else {
                list.set(i, EnumChatFormatting.GRAY + list.get(i));
            }
        }
        final Slot slot = getSlotAtPos(x, y);
        final IDankNullHandler handler = getDankNullHandler();
        if (slot instanceof SlotDankNull && slot.getHasStack()) {
            final ItemStack slotStack = slot.getStack();
            final boolean showOreDictMessage = isOreDictConfigurable(slotStack);
            final ItemExtractionMode extractMode = handler.getExtractionMode(slotStack);
            final ItemPlacementMode placementMode = handler.getPlacementMode(slotStack);
            final Block selectedBlock = Block.getBlockFromItem(stack.getItem());
            final boolean isSelectedStackABlock = selectedBlock != null && selectedBlock != Blocks.air;
            if (extractMode != null) {
                insert(
                    list,
                    1,
                    StatCollector.translateToLocal("dn.extract_mode.desc") + ": " + extractMode.getTooltip());
            }

            insert(
                list,
                2,
                EnumChatFormatting.GRAY.toString() + EnumChatFormatting.ITALIC
                    + "  "
                    + StatCollector.translateToLocal("dn.ctrl_click_change.desc"));
            if (isSelectedStackABlock) {
                insert(
                    list,
                    2,
                    EnumChatFormatting.GRAY.toString() + EnumChatFormatting.ITALIC
                        + "  "
                        + StatCollector.translateToLocal("dn.p_click_toggle.desc"));
            }
            if (handler.getSelected() != slot.getSlotIndex()) {
                insert(
                    list,
                    3,
                    EnumChatFormatting.GRAY.toString() + EnumChatFormatting.ITALIC
                        + "  "
                        + StatCollector.translateToLocal("dn.alt_click_set.desc"));
            }
            if (placementMode != null && isSelectedStackABlock) {
                final String extract = StatCollector.translateToLocal("dn.extract.desc");
                final String place = StatCollector.translateToLocal("dn.place.desc");
                insert(
                    list,
                    1,
                    StatCollector.translateToLocal("dn.placement_mode.desc") + ": "
                        + placementMode.getTooltip()
                            .replace(extract.toLowerCase(Locale.ENGLISH), place.toLowerCase(Locale.ENGLISH))
                            .replace(extract, place));
            }
            if (showOreDictMessage && OreDictionary.getOreIDs(slotStack).length > 0) {
                final String oreDictMode = handler.isOre(slotStack) ? StatCollector.translateToLocal("dn.enabled.desc")
                    : StatCollector.translateToLocal("dn.disabled.desc");
                int lineOffset = 0;
                insert(list, 2, StatCollector.translateToLocal("dn.ore_dictionary.desc") + ": " + oreDictMode);
                final List<String> oreNames = DankNullHandler.getOreNames(stack);
                if (isShiftKeyDown()) {
                    if (!oreNames.isEmpty()) {
                        insert(
                            list,
                            3,
                            EnumChatFormatting.YELLOW.toString() + EnumChatFormatting.UNDERLINE
                                + EnumChatFormatting.BOLD
                                + " Enabled OreDict Conversions: ");
                    }
                    for (int i = 0; i < oreNames.size(); i++) {
                        lineOffset = 5 + i;
                        insert(
                            list,
                            4 + i,
                            EnumChatFormatting.GRAY.toString() + EnumChatFormatting.ITALIC + "   - " + oreNames.get(i));
                    }
                }
                insert(
                    list,
                    isShiftKeyDown() ? lineOffset + 1 : 4,
                    EnumChatFormatting.GRAY.toString() + EnumChatFormatting.ITALIC
                        + "  "
                        + StatCollector.translateToLocal("dn.o_click_toggle.desc"));
            }
            if (getCount(slotStack) > 1000) {
                insert(
                    list,
                    1,
                    EnumChatFormatting.GRAY.toString() + EnumChatFormatting.ITALIC
                        + StatCollector.translateToLocal("dn.count.desc")
                        + ": "
                        + (tier.isCreative() ? StatCollector.translateToLocal("dn.infinite.desc")
                            : String.valueOf(getCount(handler.getFullStackInSlot(slot.getSlotIndex())))));
            }
        }
        drawToolTipWithBorderColor(list, x, y, tier.getHexColor(true), tier.getHexColor(false));
    }

    /**
     * Upstream inserted at fixed indices and relied on the tooltip already being long enough; a short tooltip would
     * throw. The index is clamped to the end of the list instead.
     */
    private static void insert(final List<String> list, final int index, final String line) {
        list.add(Math.min(index, list.size()), line);
    }

    /**
     * Replacement for {@code p455w0rdslib.util.GuiUtils#drawToolTipWithBorderColor} - vanilla's
     * {@code drawHoveringText} with the outline gradient taken from the /dank/null's tier colour.
     */
    private void drawToolTipWithBorderColor(final List<String> textLines, final int x, final int y,
        final int borderStart, final int borderEnd) {
        if (textLines.isEmpty()) {
            return;
        }
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        int maxWidth = 0;
        for (final String line : textLines) {
            final int lineWidth = fontRendererObj.getStringWidth(line);
            if (lineWidth > maxWidth) {
                maxWidth = lineWidth;
            }
        }
        int tipX = x + 12;
        int tipY = y - 12;
        int tipHeight = 8;
        if (textLines.size() > 1) {
            tipHeight += 2 + (textLines.size() - 1) * 10;
        }
        if (tipX + maxWidth > width) {
            tipX -= 28 + maxWidth;
        }
        if (tipY + tipHeight + 6 > height) {
            tipY = height - tipHeight - 6;
        }

        zLevel = 300.0F;
        itemRender.zLevel = 300.0F;
        final int backgroundColor = 0xF0100010;
        drawGradientRect(tipX - 3, tipY - 4, tipX + maxWidth + 3, tipY - 3, backgroundColor, backgroundColor);
        drawGradientRect(
            tipX - 3,
            tipY + tipHeight + 3,
            tipX + maxWidth + 3,
            tipY + tipHeight + 4,
            backgroundColor,
            backgroundColor);
        drawGradientRect(
            tipX - 3,
            tipY - 3,
            tipX + maxWidth + 3,
            tipY + tipHeight + 3,
            backgroundColor,
            backgroundColor);
        drawGradientRect(tipX - 4, tipY - 3, tipX - 3, tipY + tipHeight + 3, backgroundColor, backgroundColor);
        drawGradientRect(
            tipX + maxWidth + 3,
            tipY - 3,
            tipX + maxWidth + 4,
            tipY + tipHeight + 3,
            backgroundColor,
            backgroundColor);
        drawGradientRect(tipX - 3, tipY - 2, tipX - 2, tipY + tipHeight + 2, borderStart, borderEnd);
        drawGradientRect(
            tipX + maxWidth + 2,
            tipY - 2,
            tipX + maxWidth + 3,
            tipY + tipHeight + 2,
            borderStart,
            borderEnd);
        drawGradientRect(tipX - 3, tipY - 3, tipX + maxWidth + 3, tipY - 2, borderStart, borderStart);
        drawGradientRect(
            tipX - 3,
            tipY + tipHeight + 2,
            tipX + maxWidth + 3,
            tipY + tipHeight + 3,
            borderEnd,
            borderEnd);

        for (int i = 0; i < textLines.size(); i++) {
            fontRendererObj.drawStringWithShadow(textLines.get(i), tipX, tipY, -1);
            if (i == 0) {
                tipY += 2;
            }
            tipY += 10;
        }

        zLevel = 0.0F;
        itemRender.zLevel = 0.0F;
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
    }
}
