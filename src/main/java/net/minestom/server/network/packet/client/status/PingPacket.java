package net.minestom.server.network.packet.client.status;

import net.minestom.server.network.packet.client.ClientPreplayPacket;
import net.minestom.server.network.packet.server.status.PongPacket;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.utils.binary.BinaryReader;
import org.jetbrains.annotations.NotNull;

public class PingPacket implements ClientPreplayPacket {

    private long number;

    @Override
    public void process(@NotNull PlayerConnection connection) {
        PongPacket pongPacket = new PongPacket(number);
        connection.sendPacket(pongPacket);
        connection.disconnect();
    }

    @Override
    public void read(@NotNull BinaryReader reader) {
        this.number = reader.readLong();
    }
}
