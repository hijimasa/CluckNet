package com.hijimasa.clucknet.entity.goal;

import com.hijimasa.clucknet.entity.PacketChicken;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class WanderTowardReceiverGoal extends Goal {
    private static final double WAYPOINT_STEP = 8.0D;
    private static final double NOISE_STDDEV_RAD = Math.PI / 6.0D;
    private static final double EXPLORATION_PROB = 0.05D;

    private final PacketChicken chicken;
    private final double speedModifier;
    private final RandomSource random;

    public WanderTowardReceiverGoal(PacketChicken chicken, double speedModifier) {
        this.chicken = chicken;
        this.speedModifier = speedModifier;
        this.random = chicken.getRandom();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return chicken.getDestination() != null && chicken.getNavigation().isDone();
    }

    @Override
    public boolean canContinueToUse() {
        return chicken.getDestination() != null && !chicken.getNavigation().isDone();
    }

    @Override
    public void start() {
        BlockPos dest = chicken.getDestination();
        if (dest == null) {
            return;
        }

        Vec3 from = chicken.position();
        double dx = (dest.getX() + 0.5D) - from.x;
        double dz = (dest.getZ() + 0.5D) - from.z;
        double remaining = Math.sqrt(dx * dx + dz * dz);

        double step = Math.min(WAYPOINT_STEP, Math.max(1.0D, remaining));

        double angle;
        if (random.nextDouble() < EXPLORATION_PROB || remaining < 1.0E-3D) {
            angle = random.nextDouble() * Math.PI * 2.0D;
        } else {
            double bearing = Math.atan2(dz, dx);
            angle = bearing + random.nextGaussian() * NOISE_STDDEV_RAD;
        }

        double tx = from.x + Math.cos(angle) * step;
        double tz = from.z + Math.sin(angle) * step;
        chicken.getNavigation().moveTo(tx, from.y, tz, speedModifier);
    }
}
