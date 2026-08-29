package p455w0rd.danknull.init;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;

import com.google.common.collect.Lists;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import p455w0rd.danknull.api.IDankNullHandler;
import p455w0rd.danknull.client.render.HUDRenderer;
import p455w0rd.danknull.network.PacketChangeMode;
import p455w0rd.danknull.network.PacketChangeMode.ChangeType;
import p455w0rd.danknull.network.PacketOpenGui;
import p455w0rd.danknull.util.DankNullStackUtils;
import p455w0rd.danknull.util.DankNullUtils;

/**
 * Event handling for the /dank/null.
 *
 * <p>
 * 1.12's {@code @EventBusSubscriber} does not exist in 1.7.10, and 1.7.10 has <b>two</b> event buses. The split
 * used here is:
 * </p>
 * <ul>
 * <li>{@code MinecraftForge.EVENT_BUS} - world/entity/player events ({@link EntityItemPickupEvent}) and the client
 * input/render events ({@link MouseEvent}, {@link RenderGameOverlayEvent}).</li>
 * <li>{@code FMLCommonHandler.instance().bus()} - FML lifecycle events: {@link TickEvent}, {@link KeyInputEvent},
 * {@link PlayerEvent.PlayerLoggedInEvent} and {@link ConfigChangedEvent}.</li>
 * </ul>
 *
 * <p>
 * Registration is explicit: {@code CommonProxy#preInit} must call {@link #register()}, and
 * {@code ClientProxy#preInit} must additionally call {@link Client#register()}. The client half lives in a nested
 * {@code @SideOnly(Side.CLIENT)} class so the dedicated server never loads {@code Minecraft} or the client-only
 * {@code cpw.mods.fml.client.event.ConfigChangedEvent}.
 * </p>
 *
 * <p>
 * Item registration, model registration, recipes and missing-mapping remaps were handled here upstream via
 * {@code RegistryEvent}; 1.7.10 registers those imperatively in preInit, so they are not part of this class.
 * </p>
 *
 * @author p455w0rd
 */
public class ModEvents {

    // ------------------------------------------------------------------
    // main-thread task queues
    // ------------------------------------------------------------------

    /**
     * 1.7.10's FML has no {@code FMLCommonHandler#getWorldThread} and no {@code IThreadListener}, so packet handlers
     * - which run on the netty worker thread - have nowhere to hand work back to the game thread. These queues fill
     * that gap: they are drained at the end of each server/client tick below.
     */
    private static final Queue<Runnable> SERVER_TASKS = new ConcurrentLinkedQueue<>();
    private static final Queue<Runnable> CLIENT_TASKS = new ConcurrentLinkedQueue<>();

    public static void scheduleServerTask(final Runnable task) {
        SERVER_TASKS.add(task);
    }

    public static void scheduleClientTask(final Runnable task) {
        CLIENT_TASKS.add(task);
    }

    private static void drain(final Queue<Runnable> queue) {
        Runnable task;
        while ((task = queue.poll()) != null) {
            task.run();
        }
    }

    // ------------------------------------------------------------------
    // registration
    // ------------------------------------------------------------------

    public static void register() {
        final ModEvents handler = new ModEvents();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
    }

    // ------------------------------------------------------------------
    // common handlers
    // ------------------------------------------------------------------

    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            drain(SERVER_TASKS);
        }
    }

    @SubscribeEvent
    public void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            drain(CLIENT_TASKS);
        }
    }

    @SubscribeEvent
    public void tickEvent(final TickEvent.PlayerTickEvent event) {
        if (event.side == Side.CLIENT) {
            if (ModGlobals.TIME >= 360.1F) {
                ModGlobals.TIME = 0.0F;
            }
            ModGlobals.TIME += 0.75F;
        }
    }

    @SubscribeEvent
    public void onItemPickUp(final EntityItemPickupEvent event) {
        final EntityPlayer player = event.entityPlayer;
        final EntityItem entityItem = event.item;
        if (entityItem == null || !(player instanceof EntityPlayerMP)) {
            return;
        }
        final ItemStack entityStack = entityItem.getEntityItem();
        if (DankNullStackUtils.isEmpty(entityStack)) {
            return;
        }
        // Demagnetize integration
        if (entityItem.getEntityData()
            .hasKey("PreventRemoteMovement")) {
            return;
        }
        final List<ItemStack> dankNulls = getDankNullsForStack(player, entityStack);
        if (dankNulls.isEmpty()) {
            return;
        }
        final int originalCount = entityStack.stackSize;
        ItemStack inProgress = entityStack;
        for (final ItemStack dankNullStack : dankNulls) {
            final IDankNullHandler dankNullHandler = DankNullUtils.getHandler(dankNullStack);
            if (dankNullHandler == null) {
                continue;
            }
            // One batch per /dank/null: each insert would otherwise re-serialise its whole inventory to NBT.
            DankNullUtils.beginBatch(dankNullHandler);
            try {
                for (final int position : dankNullHandler.findItemStacks(entityStack)) {
                    inProgress = dankNullHandler.insertItem(position, inProgress, false);
                    if (DankNullStackUtils.isEmpty(inProgress)) {
                        break;
                    }
                }
                // Ore-dict absorption: slots whose stored stack has ore-dict mode enabled and ore-matches the
                // pickup take the remainder too - insertItem converts it to the stored form on the way in.
                if (!DankNullStackUtils.isEmpty(inProgress)) {
                    for (final int position : dankNullHandler.findOreMatchingStacks(entityStack)) {
                        inProgress = dankNullHandler.insertItem(position, inProgress, false);
                        if (DankNullStackUtils.isEmpty(inProgress)) {
                            break;
                        }
                    }
                }
            } finally {
                DankNullUtils.endBatch(dankNullHandler);
            }
        }
        final int remaining = DankNullStackUtils.getCount(inProgress);
        if (remaining < originalCount) {
            // Upstream zeroed the stack whenever a matching slot merely existed, silently voiding whatever the
            // /dank/null could not take and claiming the pickup even when nothing moved. Carrying the remainder back
            // onto the entity leaves the normal (fully absorbed) case identical.
            entityStack.stackSize = remaining;
            event.setResult(Event.Result.ALLOW);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP && FMLCommonHandler.instance()
            .getEffectiveSide()
            .isServer()) {
            ModConfig.sendConfigsToClient((EntityPlayerMP) event.player);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Upstream returned {@code PlayerSlot}s taken from {@code ItemDankNull.getDankNullsForPlayer}, and added the same
     * slot twice when a /dank/null both contained the stack and ore-dict filtered it (making the pickup insert into
     * it twice). Here the main inventory is scanned directly - there is no off-hand or armour slot to consider - and
     * each /dank/null appears at most once.
     */
    private static List<ItemStack> getDankNullsForStack(final EntityPlayer player, final ItemStack stack) {
        final List<ItemStack> validDankNulls = Lists.newArrayList();
        if (player == null || player.inventory == null) {
            return validDankNulls;
        }
        for (final ItemStack invStack : player.inventory.mainInventory) {
            if (!DankNullUtils.isDankNull(invStack)) {
                continue;
            }
            final IDankNullHandler dankNullHandler = DankNullUtils.getHandler(invStack);
            if (dankNullHandler == null) {
                continue;
            }
            if (dankNullHandler.containsItemStack(stack) || dankNullHandler.isOreDictFiltered(stack)) {
                validDankNulls.add(invStack);
            }
        }
        return validDankNulls;
    }

    private static int findFirstDankNullSlot(final EntityPlayer player) {
        if (player == null || player.inventory == null) {
            return -1;
        }
        if (DankNullUtils.isDankNull(player.getHeldItem())) {
            return player.inventory.currentItem;
        }
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            if (DankNullUtils.isDankNull(player.inventory.mainInventory[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Upstream returned a {@code Pair<EnumHand, IDankNullHandler>}; with no off-hand there is only ever one candidate.
     */
    private static IDankNullHandler getHandlerFromHeld(final EntityPlayer player) {
        return DankNullUtils.getHandler(DankNullUtils.getDankNullInHand(player));
    }

    // ------------------------------------------------------------------
    // client handlers
    // ------------------------------------------------------------------

    @SideOnly(Side.CLIENT)
    public static class Client {

        public static void register() {
            final Client handler = new Client();
            MinecraftForge.EVENT_BUS.register(handler);
            FMLCommonHandler.instance()
                .bus()
                .register(handler);
        }

        @SubscribeEvent
        public void renderOverlayEvent(final RenderGameOverlayEvent event) {
            if (ModGlobals.GUI_DANKNULL_ISOPEN && event.isCancelable() && (//@formatter:off
                    event.type == ElementType.HOTBAR ||
                    event.type == ElementType.CROSSHAIRS ||
                    event.type == ElementType.EXPERIENCE ||
                    event.type == ElementType.FOOD ||
                    event.type == ElementType.HEALTH ||
                    event.type == ElementType.ARMOR)//@formatter:on
            ) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void onPostRenderOverlay(final RenderGameOverlayEvent.Post event) {
            if (event.type == ElementType.HOTBAR) {
                // TODO(render): HUDRenderer is owned by the rendering workstream; this is only the trigger point.
                HUDRenderer.renderHUD(Minecraft.getMinecraft(), event.resolution);
            }
        }

        @SubscribeEvent
        public void onKeyInput(final KeyInputEvent event) {
            if (!ModKeyBindings.isAnyModKeybindPressed()) {
                return;
            }
            if (ModKeyBindings.getToggleHUDKeyBind()
                .isPressed()) {
                // TODO(render): HUDRenderer is owned by the rendering workstream.
                HUDRenderer.toggleHUD();
            }
            final EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player == null) {
                return;
            }
            // Only check keybinds if player has DankNulls
            final int dankNullSlot = findFirstDankNullSlot(player);
            if (dankNullSlot < 0) {
                return;
            }
            if (ModKeyBindings.getOpenDankNullKeyBind()
                .isPressed()) {
                ModNetworking.getInstance()
                    .sendToServer(new PacketOpenGui(dankNullSlot));
            }
            if (ModKeyBindings.getNextItemKeyBind()
                .isPressed()
                || ModKeyBindings.getPreviousItemKeyBind()
                    .isPressed()) {
                final IDankNullHandler dankNullHandler = getHandlerFromHeld(player);
                if (dankNullHandler != null) {
                    dankNullHandler.cycleSelected(
                        ModKeyBindings.getNextItemKeyBind()
                            .getIsKeyPressed());
                    ModNetworking.getInstance()
                        .sendToServer(new PacketChangeMode(ChangeType.SELECTED, dankNullHandler.getSelected(), false));
                }
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onMouseEvent(final MouseEvent event) {
            final Minecraft mc = Minecraft.getMinecraft();
            final EntityPlayer player = mc.thePlayer;
            final World world = mc.theWorld;
            if (player == null || world == null) {
                return;
            }

            // middle-click a block to select the matching slot
            if (event.buttonstate && event.button == 2 && event.dwheel == 0) {
                final IDankNullHandler dankNullHandler = getHandlerFromHeld(player);
                if (dankNullHandler == null) {
                    return;
                }
                final MovingObjectPosition target = mc.objectMouseOver;
                if (target != null && target.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    final int x = target.blockX;
                    final int y = target.blockY;
                    final int z = target.blockZ;
                    if (world.isAirBlock(x, y, z)) {
                        return;
                    }
                    final Block block = world.getBlock(x, y, z);
                    // 1.7.10 has no IBlockState; getPickBlock takes the raw coordinates instead.
                    final ItemStack stackToSelect = block.getPickBlock(target, world, x, y, z);
                    if (!DankNullStackUtils.isEmpty(stackToSelect)
                        && (dankNullHandler.containsItemStack(stackToSelect) || dankNullHandler.isOre(stackToSelect))) {
                        final int newIndex = dankNullHandler.findItemStack(stackToSelect);
                        dankNullHandler.setSelected(newIndex);
                        ModNetworking.getInstance()
                            .sendToServer(new PacketChangeMode(ChangeType.SELECTED, newIndex, false));
                        event.setCanceled(true);
                    }
                }
            }

            if (ModKeyBindings.isAnyModKeybindPressed() && event.dwheel == 0) {
                final IDankNullHandler dankNullHandler = getHandlerFromHeld(player);
                if (dankNullHandler == null) {
                    return;
                }
                // Do NOT bail on getSelected() == -1: cycleSelected() bootstraps an unset selection to slot 0,
                // so refusing here would make the selection permanently stuck at -1 (no preview, nothing to place).
                if (dankNullHandler.stackCount() == 0) {
                    return;
                }
                if (ModKeyBindings.getNextItemKeyBind()
                    .isPressed()) {
                    cycleAndSync(dankNullHandler, true);
                    event.setCanceled(true);
                } else if (ModKeyBindings.getPreviousItemKeyBind()
                    .isPressed()) {
                        cycleAndSync(dankNullHandler, false);
                        event.setCanceled(true);
                    }
            } else if (event.dwheel != 0 && player.isSneaking()) {
                // done separately to avoid resolving the held /dank/null every time the mouse is used
                final IDankNullHandler dankNullHandler = getHandlerFromHeld(player);
                if (dankNullHandler == null) {
                    return;
                }
                // Do NOT bail on getSelected() == -1: cycleSelected() bootstraps an unset selection to slot 0,
                // so refusing here would make the selection permanently stuck at -1 (no preview, nothing to place).
                if (dankNullHandler.stackCount() == 0) {
                    return;
                }
                if (event.dwheel < 0) {
                    cycleAndSync(dankNullHandler, true);
                    event.setCanceled(true);
                } else {
                    cycleAndSync(dankNullHandler, false);
                    event.setCanceled(true);
                }
            }
        }

        private static void cycleAndSync(final IDankNullHandler dankNullHandler, final boolean forward) {
            dankNullHandler.cycleSelected(forward);
            ModNetworking.getInstance()
                .sendToServer(new PacketChangeMode(ChangeType.SELECTED, dankNullHandler.getSelected(), false));
        }

        @SubscribeEvent
        public void onConfigChange(final ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.modID.equals(ModGlobals.MODID)) {
                ModConfig.sync();
            }
        }
    }

}
