package powercyphe.farmtweaks;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.gamerules.GameRules;
import powercyphe.farmtweaks.event.LeafDecayEvent;

public class LeafDecayGameTest {
    @GameTest(maxTicks = 100)
    public void canopyDecaysAfterLastLogRemoved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // No lucky random tick can hide a missed leaf or a broken mixin.
        level.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, level.getServer());
        helper.setBlock(1, 2, 3, Blocks.OAK_LOG);
        for (int x = 2; x <= 6; x++) {
            helper.setBlock(x, 2, 3, Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(LeavesBlock.DISTANCE, x - 1));
        }
        helper.setBlock(3, 4, 3, Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, true));
        helper.setBlock(1, 2, 6, Blocks.OAK_LOG);
        helper.setBlock(2, 2, 6, Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(LeavesBlock.DISTANCE, 1));
        helper.runAtTickTime(5, () -> helper.setBlock(1, 2, 3, Blocks.AIR));
        helper.runAtTickTime(60, () -> {
            for (int x = 2; x <= 6; x++) {
                helper.assertBlockPresent(Blocks.AIR, x, 2, 3);
            }
            helper.assertBlockPresent(Blocks.OAK_LEAVES, 3, 4, 3);
            helper.assertBlockPresent(Blocks.OAK_LEAVES, 2, 2, 6);
            helper.succeed();
        });
    }

    @GameTest
    public void queueIsIsolatedAndBounded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LeafDecayEvent event = new LeafDecayEvent();
        int oldSpeed = FarmTweaksConfig.leafDecaySpeed;
        boolean oldEnabled = FarmTweaksConfig.fastLeafDecay;
        try {
            FarmTweaksConfig.fastLeafDecay = true;
            FarmTweaksConfig.leafDecaySpeed = 1;
            BlockPos first = helper.absolutePos(new BlockPos(1, 2, 1));
            BlockPos second = helper.absolutePos(new BlockPos(5, 2, 5));
            level.setBlock(first, Blocks.OAK_LEAVES.defaultBlockState(), 2);
            level.setBlock(second, Blocks.OAK_LEAVES.defaultBlockState(), 2);
            event.queue(level, first);
            event.queue(level, first);
            event.queue(level, second);
            event.onEndTick(level.getServer().getLevel(Level.NETHER));
            event.onEndTick(level);
            helper.assertTrue(level.getBlockState(first).isAir(), "Other dimension must not consume queued leaves");
            helper.assertTrue(level.getBlockState(second).is(Blocks.OAK_LEAVES), "One-tick budget must be respected");
            event.onEndTick(level);
            helper.assertTrue(level.getBlockState(second).isAir(), "Duplicate entries must not consume the next budget");

            level.setBlock(first, Blocks.OAK_LEAVES.defaultBlockState(), 2);
            event.queue(level, first);
            FarmTweaksConfig.fastLeafDecay = false;
            event.onEndTick(level);
            helper.assertTrue(level.getBlockState(first).is(Blocks.OAK_LEAVES), "Disabled decay must not process pending work");
            FarmTweaksConfig.fastLeafDecay = true;
            event.onEndTick(level);
            helper.assertTrue(level.getBlockState(first).is(Blocks.OAK_LEAVES), "Disabled queue must be cleared");
            level.setBlock(first, Blocks.AIR.defaultBlockState(), 2);
            helper.succeed();
        } finally {
            FarmTweaksConfig.leafDecaySpeed = oldSpeed;
            FarmTweaksConfig.fastLeafDecay = oldEnabled;
        }
    }
}
