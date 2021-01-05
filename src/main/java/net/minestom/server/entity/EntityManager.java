package net.minestom.server.entity;

import com.google.common.collect.Queues;
import net.minestom.server.MinecraftServer;
import net.minestom.server.chat.ChatColor;
import net.minestom.server.chat.ColoredText;
import net.minestom.server.entity.fakeplayer.FakePlayerOption;
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.ConnectionManager;
import net.minestom.server.network.packet.server.play.KeepAlivePacket;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.utils.async.AsyncUtils;
import net.minestom.server.utils.validate.Check;
import org.jetbrains.annotations.NotNull;

import java.util.Queue;
import java.util.UUID;
import java.util.function.Consumer;

public final class EntityManager {

    private static final ConnectionManager CONNECTION_MANAGER = MinecraftServer.getConnectionManager();

    private static final long KEEP_ALIVE_DELAY = 10_000;
    private static final long KEEP_ALIVE_KICK = 30_000;
    private static final ColoredText TIMEOUT_TEXT = ColoredText.of(ChatColor.RED + "Timeout");

    private final Queue<Player> waitingPlayers = Queues.newConcurrentLinkedQueue();

    /**
     * Connects waiting players.
     */
    public void updateWaitingPlayers() {
        // Connect waiting players
        waitingPlayersTick();
    }

    /**
     * Updates keep alive by checking the last keep alive packet and send a new one if needed.
     *
     * @param tickStart the time of the update in milliseconds, forwarded to the packet
     */
    public void handleKeepAlive(long tickStart) {
        final KeepAlivePacket keepAlivePacket = new KeepAlivePacket(tickStart);
        for (Player player : CONNECTION_MANAGER.getUnwrapOnlinePlayers()) {
            final long lastKeepAlive = tickStart - player.getLastKeepAlive();
            if (lastKeepAlive > KEEP_ALIVE_DELAY && player.didAnswerKeepAlive()) {
                player.refreshKeepAlive(tickStart);
                player.getPlayerConnection().sendPacket(keepAlivePacket);
            } else if (lastKeepAlive >= KEEP_ALIVE_KICK) {
                player.kick(TIMEOUT_TEXT);
            }
        }
    }

    /**
     * Adds connected clients after the handshake (used to free the networking threads).
     */
    private void waitingPlayersTick() {
        Player waitingPlayer;
        while ((waitingPlayer = waitingPlayers.poll()) != null) {

            PlayerLoginEvent loginEvent = new PlayerLoginEvent(waitingPlayer);
            waitingPlayer.callEvent(PlayerLoginEvent.class, loginEvent);
            final Instance spawningInstance = loginEvent.getSpawningInstance();

            Check.notNull(spawningInstance, "You need to specify a spawning instance in the PlayerLoginEvent");

            waitingPlayer.init();

            // Spawn the player at Player#getRespawnPoint during the next instance tick
            spawningInstance.scheduleNextTick(waitingPlayer::setInstance);
        }
    }

    /**
     * Calls the player initialization callbacks and the event {@link AsyncPlayerPreLoginEvent}.
     * If the {@link Player} hasn't been kicked, add him to the waiting list.
     * <p>
     * Can be considered as a pre-init thing,
     * currently executed in {@link ConnectionManager#createPlayer(UUID, String, PlayerConnection)}
     * and {@link net.minestom.server.entity.fakeplayer.FakePlayer#initPlayer(UUID, String, FakePlayerOption, Consumer)}.
     *
     * @param player the {@link Player player} to add to the waiting list
     */
    public void addWaitingPlayer(@NotNull Player player) {

        // Init player (register events)
        for (Consumer<Player> playerInitialization : MinecraftServer.getConnectionManager().getPlayerInitializations()) {
            playerInitialization.accept(player);
        }

        AsyncUtils.runAsync(() -> {
            // Call pre login event
            AsyncPlayerPreLoginEvent asyncPlayerPreLoginEvent = new AsyncPlayerPreLoginEvent(player, player.getUsername(), player.getUuid());
            player.callEvent(AsyncPlayerPreLoginEvent.class, asyncPlayerPreLoginEvent);

            // Ignore the player if he has been disconnected (kick)
            final boolean online = player.isOnline();
            if (!online)
                return;

            // Change UUID/Username based on the event
            {
                final String username = asyncPlayerPreLoginEvent.getUsername();
                final UUID uuid = asyncPlayerPreLoginEvent.getPlayerUuid();

                if (!player.getUsername().equals(username)) {
                    player.setUsername(username);
                }

                if (!player.getUuid().equals(uuid)) {
                    player.setUuid(uuid);
                }

            }

            // Add the player to the waiting list
            this.waitingPlayers.add(player);
        });
    }
}
