package p455w0rd.danknull.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import p455w0rd.danknull.api.DankNullItemModes.ItemExtractionMode;
import p455w0rd.danknull.api.DankNullItemModes.ItemPlacementMode;
import p455w0rd.danknull.api.IDankNullHandler;
import p455w0rd.danknull.container.ContainerDankNull;
import p455w0rd.danknull.init.ModEvents;
import p455w0rd.danknull.util.DankNullStackUtils;
import p455w0rd.danknull.util.DankNullUtils;

/**
 * @author BrockWS
 */
public class PacketChangeMode implements IMessage {

    private ChangeType changeType;
    private int slot = -1;
    private boolean isGui = true;

    public PacketChangeMode() {}

    public PacketChangeMode(final ChangeType changeType) {
        this.changeType = changeType;
    }

    public PacketChangeMode(final ItemPlacementMode mode, final int slot) {
        switch (mode) {
            case KEEP_NONE:
                changeType = ChangeType.PLACE_KEEP_NONE;
                break;
            case KEEP_1:
                changeType = ChangeType.PLACE_KEEP_1;
                break;
            case KEEP_16:
                changeType = ChangeType.PLACE_KEEP_16;
                break;
            case KEEP_64:
                changeType = ChangeType.PLACE_KEEP_64;
                break;
            case KEEP_ALL:
                changeType = ChangeType.PLACE_KEEP_ALL;
                break;
            default:
                throw new RuntimeException("Unknown ItemPlacementMode " + mode.name());
        }
        this.slot = slot;
    }

    public PacketChangeMode(final ItemExtractionMode mode, final int slot) {
        switch (mode) {
            case KEEP_NONE:
                changeType = ChangeType.EXTRACT_KEEP_NONE;
                break;
            case KEEP_1:
                changeType = ChangeType.EXTRACT_KEEP_1;
                break;
            case KEEP_16:
                changeType = ChangeType.EXTRACT_KEEP_16;
                break;
            case KEEP_64:
                changeType = ChangeType.EXTRACT_KEEP_64;
                break;
            case KEEP_ALL:
                changeType = ChangeType.EXTRACT_KEEP_ALL;
                break;
            default:
                throw new RuntimeException("Unknown ItemExtractionMode " + mode.name());
        }
        this.slot = slot;
    }

    public PacketChangeMode(final ChangeType type, final int slot) {
        this(type, slot, true);
    }

    /**
     * 1.7.10 has no off-hand, so upstream's trailing {@code EnumHand}/{@code mainHand} flag is gone from both the
     * constructor and the wire format - a non-GUI change always targets the single held item.
     */
    public PacketChangeMode(final ChangeType type, final int slot, final boolean isGui) {
        changeType = type;
        this.slot = slot;
        this.isGui = isGui;
    }

    private static void handleModeUpdate(final IDankNullHandler handler, final ChangeType changeType, final int slot) {
        final ItemStack slotStack = slot >= 0 && slot < handler.getSlots() ? handler.getFullStackInSlot(slot) : null;
        switch (changeType) {
            case SELECTED:
                handler.setSelected(slot);
                break;

            case LOCK:
                // Only the creative tier supports locking; without this a modified client could lock any tier.
                if (handler.isLockingSupported()) {
                    handler.setLocked(true);
                }
                break;
            case UNLOCK:
                if (handler.isLockingSupported()) {
                    handler.setLocked(false);
                }
                break;

            case ORE_ON:
                handler.setOre(slotStack, true);
                break;
            case ORE_OFF:
                handler.setOre(slotStack, false);
                break;

            case EXTRACT_KEEP_ALL:
                handler.setExtractionMode(slotStack, ItemExtractionMode.KEEP_ALL);
                break;
            case EXTRACT_KEEP_1:
                handler.setExtractionMode(slotStack, ItemExtractionMode.KEEP_1);
                break;
            case EXTRACT_KEEP_16:
                handler.setExtractionMode(slotStack, ItemExtractionMode.KEEP_16);
                break;
            case EXTRACT_KEEP_64:
                handler.setExtractionMode(slotStack, ItemExtractionMode.KEEP_64);
                break;
            case EXTRACT_KEEP_NONE:
                handler.setExtractionMode(slotStack, ItemExtractionMode.KEEP_NONE);
                break;

            case PLACE_KEEP_ALL:
                handler.setPlacementMode(slotStack, ItemPlacementMode.KEEP_ALL);
                break;
            case PLACE_KEEP_1:
                handler.setPlacementMode(slotStack, ItemPlacementMode.KEEP_1);
                break;
            case PLACE_KEEP_16:
                handler.setPlacementMode(slotStack, ItemPlacementMode.KEEP_16);
                break;
            case PLACE_KEEP_64:
                handler.setPlacementMode(slotStack, ItemPlacementMode.KEEP_64);
                break;
            case PLACE_KEEP_NONE:
                handler.setPlacementMode(slotStack, ItemPlacementMode.KEEP_NONE);
                break;
            default:
                break;
        }
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        changeType = ChangeType.VALUES[buf.readInt()];
        slot = buf.readInt();
        isGui = buf.readBoolean();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(changeType.ordinal());
        buf.writeInt(slot);
        buf.writeBoolean(isGui);
    }

    public enum ChangeType {

        LOCK,
        UNLOCK,
        SELECTED,
        ORE_ON,
        ORE_OFF,
        EXTRACT_KEEP_ALL,
        EXTRACT_KEEP_1,
        EXTRACT_KEEP_16,
        EXTRACT_KEEP_64,
        EXTRACT_KEEP_NONE,
        PLACE_KEEP_ALL,
        PLACE_KEEP_1,
        PLACE_KEEP_16,
        PLACE_KEEP_64,
        PLACE_KEEP_NONE;

        // Learned this from McJty..if your Enum won't be modified later, cache the values
        public static ChangeType[] VALUES = values();

    }

    public static class Handler implements IMessageHandler<PacketChangeMode, IMessage> {

        @Override
        public IMessage onMessage(final PacketChangeMode message, final MessageContext ctx) {
            final EntityPlayer player = ctx.getServerHandler().playerEntity;
            // onMessage runs on the netty thread; this mutates the player's inventory NBT, so it is deferred to the
            // server tick. 1.7.10 has no FMLCommonHandler#getWorldThread, hence ModEvents' own task queue.
            ModEvents.scheduleServerTask(() -> {
                if (message.isGui) {
                    final Container container = player.openContainer;
                    if (container instanceof ContainerDankNull) {
                        handleModeUpdate(
                            ((ContainerDankNull) container).getHandler(),
                            message.changeType,
                            message.slot);
                    }
                } else {
                    final ItemStack stack = DankNullUtils.getDankNullInHand(player);
                    if (!DankNullStackUtils.isEmpty(stack)) {
                        final IDankNullHandler handler = DankNullUtils.getHandler(stack);
                        if (handler != null) {
                            handleModeUpdate(handler, message.changeType, message.slot);
                        }
                    }
                }
            });
            return null;
        }
    }
}
