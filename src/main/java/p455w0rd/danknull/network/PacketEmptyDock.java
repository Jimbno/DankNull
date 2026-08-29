package p455w0rd.danknull.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import p455w0rd.danknull.blocks.tiles.TileDankNullDock;
import p455w0rd.danknull.container.ContainerDankNullItem;
import p455w0rd.danknull.init.ModEvents;

/**
 * 1.7.10 has no {@code BlockPos}, so the position travels as three raw ints.
 *
 * @author p455w0rd
 */
public class PacketEmptyDock implements IMessage {

    private int x;
    private int y;
    private int z;

    public PacketEmptyDock() {}

    public PacketEmptyDock(final int x, final int y, final int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public PacketEmptyDock(final TileEntity dock) {
        this(dock.xCoord, dock.yCoord, dock.zCoord);
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    public static class Handler implements IMessageHandler<PacketEmptyDock, IMessage> {

        @Override
        public IMessage onMessage(final PacketEmptyDock message, final MessageContext ctx) {
            if (ctx.side != Side.CLIENT) {
                return null;
            }
            // Closes a screen and mutates a tile entity - must not happen on the netty thread.
            ModEvents.scheduleClientTask(() -> {
                final EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
                if (player == null) {
                    return;
                }
                if (player.openContainer instanceof ContainerDankNullItem) {
                    player.closeScreen();
                }
                final World world = player.worldObj;
                if (world == null) {
                    return;
                }
                final TileEntity te = world.getTileEntity(message.x, message.y, message.z);
                if (te instanceof TileDankNullDock) {
                    ((TileDankNullDock) te).removeDankNull();
                }
            });
            return null;
        }
    }

}
