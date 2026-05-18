package com.hijimasa.clucknet.entity;

import com.hijimasa.clucknet.CluckNet;
import com.hijimasa.clucknet.entity.goal.WanderTowardReceiverGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class PacketChicken extends Chicken {
    private static final String TAG_DESTINATION = "Destination";
    private static final String TAG_TTL = "TTL";

    public static final int DEFAULT_TTL_TICKS = 20 * 60 * 3;
    private static final double ARRIVAL_DIST_SQ = 4.0;

    private static final Ingredient SEED_INGREDIENT = Ingredient.of(
            Items.WHEAT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS,
            Items.BEETROOT_SEEDS, Items.TORCHFLOWER_SEEDS);

    @Nullable
    private BlockPos destination;
    private int ttlTicks = DEFAULT_TTL_TICKS;

    public PacketChicken(EntityType<? extends PacketChicken> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.4D));
        this.goalSelector.addGoal(2, new TemptGoal(this, 1.0D, SEED_INGREDIENT, false));
        this.goalSelector.addGoal(3, new WanderTowardReceiverGoal(this, 1.2D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    @Nullable
    public BlockPos getDestination() {
        return destination;
    }

    public void setDestination(@Nullable BlockPos pos) {
        this.destination = pos;
    }

    public void setTtl(int ticks) {
        this.ttlTicks = ticks;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide || destination == null) {
            return;
        }

        if (this.blockPosition().distSqr(destination) < ARRIVAL_DIST_SQ) {
            CluckNet.LOGGER.info("PacketChicken delivered at {}", destination);
            this.discard();
            return;
        }

        if (--ttlTicks <= 0) {
            CluckNet.LOGGER.info("PacketChicken TTL expired en route to {}", destination);
            this.discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (destination != null) {
            tag.putLong(TAG_DESTINATION, destination.asLong());
        }
        tag.putInt(TAG_TTL, ttlTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_DESTINATION, Tag.TAG_LONG)) {
            this.destination = BlockPos.of(tag.getLong(TAG_DESTINATION));
        }
        if (tag.contains(TAG_TTL, Tag.TAG_INT)) {
            this.ttlTicks = tag.getInt(TAG_TTL);
        }
    }
}
