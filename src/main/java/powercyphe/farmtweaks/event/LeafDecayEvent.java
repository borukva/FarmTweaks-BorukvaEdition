package powercyphe.farmtweaks.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import powercyphe.farmtweaks.mixin.accessor.LeavesBlockAccessor;
import powercyphe.farmtweaks.util.FarmTweaksUtil;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.WeakHashMap;

public class LeafDecayEvent implements ServerTickEvents.EndLevelTick {
    private static final LeafDecayEvent INSTANCE = new LeafDecayEvent();
    // Values do not retain their level; unloaded worlds can be collected.
    private final Map<ServerLevel, LinkedHashSet<BlockPos>> queues = new WeakHashMap<>();

    public static LeafDecayEvent get() {
        return INSTANCE;
    }

    @Override
    public void onEndTick(ServerLevel level) {
        if (!FarmTweaksUtil.fastLeafDecay()) {
            this.queues.clear();
            return;
        }
        LinkedHashSet<BlockPos> queue = this.queues.get(level);
        if (queue == null) {
            return;
        }

        int budget = Math.min(queue.size(), FarmTweaksUtil.leafDecaySpeed());
        for (int i = 0; i < budget; i++) {
            BlockPos pos = queue.removeFirst();
            // Never load chunks for decay. Keep pending work until the chunk returns.
            if (!level.hasChunkAt(pos)) {
                queue.add(pos);
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof LeavesBlock leaves
                    && ((LeavesBlockAccessor) leaves).farmtweaks$decaying(state)) {
                state.randomTick(level, pos, level.getRandom());
            }
        }
        if (queue.isEmpty()) {
            this.queues.remove(level);
        }
    }

    public void queue(ServerLevel level, BlockPos blockPos) {
        if (FarmTweaksUtil.fastLeafDecay()) {
            this.queues.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(blockPos.immutable());
        }
    }

    public void queueNearby(ServerLevel level, BlockPos rootPos) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    BlockPos adjPos = rootPos.offset(x, y, z);
                    if (!level.hasChunkAt(adjPos)) {
                        continue;
                    }
                    BlockState adjState = level.getBlockState(adjPos);

                    if (adjState.getBlock() instanceof LeavesBlock leavesBlock
                            && ((LeavesBlockAccessor) leavesBlock).farmtweaks$decaying(adjState)) {
                        this.queue(level, adjPos);
                    }
                }
            }
        }
    }
}
