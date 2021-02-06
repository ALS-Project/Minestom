package net.minestom.server.thread;

import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Separates work between instance (1 instance = 1 thread execution).
 */
public class PerInstanceThreadProvider extends ThreadProvider {

    private final Object2ObjectArrayMap<Instance, LongSet> instanceChunkMap = new Object2ObjectArrayMap<>();

    private static final AtomicInteger vanillaTick = new AtomicInteger();
    long lastNormalUpdate = 0;

    @Override
    public void onInstanceCreate(@NotNull Instance instance) {
        this.instanceChunkMap.putIfAbsent(instance, new LongArraySet());
    }

    @Override
    public void onInstanceDelete(@NotNull Instance instance) {
        this.instanceChunkMap.remove(instance);
    }

    @Override
    public void onChunkLoad(@NotNull Instance instance, int chunkX, int chunkZ) {
        // Add the loaded chunk to the instance chunks list
        LongSet chunkCoordinates = getChunkCoordinates(instance);
        final long index = ChunkUtils.getChunkIndex(chunkX, chunkZ);
        chunkCoordinates.add(index);
    }

    @Override
    public void onChunkUnload(@NotNull Instance instance, int chunkX, int chunkZ) {
        LongSet chunkCoordinates = getChunkCoordinates(instance);
        final long index = ChunkUtils.getChunkIndex(chunkX, chunkZ);
        // Remove the unloaded chunk from the instance list
        chunkCoordinates.remove(index);

    }

    @NotNull
    @Override
    public List<Future<?>> update(long time) {
        List<Future<?>> futures = new ArrayList<>();

        instanceChunkMap.forEach((instance, chunkIndexes) -> futures.add(pool.submit(() -> {
            boolean updateVanillaTick = vanillaTick.getAndIncrement() >= (MinecraftServer.TICK_PER_SECOND / MinecraftServer.VANILLA_TICK_PER_SECOND);
            // Tick instance
            if (updateVanillaTick)
                updateInstance(instance, time);
            // Tick chunks
            chunkIndexes.forEach((long chunkIndex) -> {
                //TODO: ALS CHANGE

                final int chunkX = ChunkUtils.getChunkCoordX(chunkIndex);
                final int chunkZ = ChunkUtils.getChunkCoordZ(chunkIndex);

                final Chunk chunk = instance.getChunk(chunkX, chunkZ);

                if (!ChunkUtils.isLoaded(chunk))
                    return;

                if (updateVanillaTick) {
                    vanillaTick.set(0);
                    //Update Chunk
                    updateChunk(instance, chunk, time);

                    //Update Entities
                    updateEntities(instance, chunk, time);
                } else {
                    conditionalEntityUpdate(instance, chunk, time, value -> value instanceof Player);
                }
            });
        })));
        return futures;
    }

    private LongSet getChunkCoordinates(Instance instance) {
        return instanceChunkMap.computeIfAbsent(instance, inst -> new LongArraySet());
    }

}
