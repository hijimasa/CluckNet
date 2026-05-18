package com.hijimasa.clucknet.entity;

import com.hijimasa.clucknet.CluckNet;
import com.hijimasa.clucknet.entity.goal.WanderTowardReceiverGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class PacketChicken extends Chicken {
    private static final String TAG_DESTINATION = "Destination";
    private static final String TAG_TTL = "TTL";
    private static final String TAG_MESSAGE_ID = "MessageId";
    private static final String TAG_SEQUENCE = "Sequence";
    private static final String TAG_TOTAL_CHUNKS = "TotalChunks";
    private static final String TAG_PAYLOAD = "Payload";
    private static final String TAG_ORIGINAL_LENGTH = "OriginalLength";
    private static final String TAG_CHUNK_TYPE = "ChunkType";
    private static final String TAG_SENDER_POS = "SenderPos";

    public static final byte TYPE_DATA = 0;
    public static final byte TYPE_NACK = 1;

    public static final int DEFAULT_TTL_TICKS = 20 * 60 * 3;
    private static final double ARRIVAL_DIST_SQ = 4.0;

    private static final Ingredient SEED_INGREDIENT = Ingredient.of(
            Items.WHEAT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS,
            Items.BEETROOT_SEEDS, Items.TORCHFLOWER_SEEDS);

    @Nullable
    private BlockPos destination;
    private int ttlTicks = DEFAULT_TTL_TICKS;

    @Nullable
    private UUID messageId;
    private int sequence;
    private int totalChunks;
    @Nullable
    private byte[] payload;
    /** UTF-8 byte length of the full message. -1 means legacy chunks without parity. */
    private int originalLength = -1;
    private byte chunkType = TYPE_DATA;
    @Nullable
    private BlockPos senderPos;

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
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        InteractionResult result = super.mobInteract(player, hand);
        if (result.consumesAction()) {
            return result;
        }
        if (!this.level().isClientSide) {
            sendInspectionInfo(player);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    private void sendInspectionInfo(Player player) {
        if (messageId == null || payload == null) {
            player.sendSystemMessage(Component.translatable("clucknet.chicken.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        String shortId = messageId.toString().substring(0, 8);
        String destStr = destination == null ? "?" : destination.toShortString();
        if (chunkType == TYPE_NACK) {
            String missing = formatMissingIndices(payload);
            player.sendSystemMessage(Component.translatable("clucknet.chicken.nack",
                            shortId, missing, destStr)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        String preview = previewPayload(payload);
        player.sendSystemMessage(Component.translatable("clucknet.chicken.info",
                        shortId, sequence + 1, totalChunks, payload.length, destStr)
                .withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.translatable("clucknet.chicken.preview", preview)
                .withStyle(ChatFormatting.WHITE));
    }

    private static String previewPayload(byte[] bytes) {
        String s = new String(bytes, StandardCharsets.UTF_8);
        s = s.replace('\n', ' ').replace('\r', ' ');
        if (s.length() > 48) {
            s = s.substring(0, 48) + "...";
        }
        return s;
    }

    private static String formatMissingIndices(byte[] payload) {
        int count = payload.length / 4;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(',');
            int idx = ((payload[i * 4] & 0xFF) << 24)
                    | ((payload[i * 4 + 1] & 0xFF) << 16)
                    | ((payload[i * 4 + 2] & 0xFF) << 8)
                    | (payload[i * 4 + 3] & 0xFF);
            sb.append(idx);
        }
        sb.append(']');
        return sb.toString();
    }

    public void setChunkData(UUID messageId, int sequence, int totalChunks, byte[] payload,
                             int originalLength, byte chunkType, @Nullable BlockPos senderPos) {
        this.messageId = messageId;
        this.sequence = sequence;
        this.totalChunks = totalChunks;
        this.payload = payload;
        this.originalLength = originalLength;
        this.chunkType = chunkType;
        this.senderPos = senderPos == null ? null : senderPos.immutable();
    }

    @Nullable
    public ChunkData getChunkData() {
        if (messageId == null || payload == null) {
            return null;
        }
        return new ChunkData(messageId, sequence, totalChunks, payload, originalLength, chunkType, senderPos);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean damaged = super.hurt(source, amount);
        // Death is detected after super.hurt; isDeadOrDying() reflects new HP
        if (damaged && this.isDeadOrDying() && !this.level().isClientSide) {
            dropPayloadOnDeath();
        }
        return damaged;
    }

    private void dropPayloadOnDeath() {
        com.hijimasa.clucknet.stats.CluckNetStats.INSTANCE.recordChickenLost();
        if (messageId == null || payload == null || chunkType != TYPE_DATA) {
            return;
        }
        ItemStack paper = new ItemStack(Items.PAPER);
        String preview = previewPayload(payload);
        String shortId = messageId.toString().substring(0, 8);
        paper.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("Lost chunk " + (sequence + 1) + "/" + totalChunks + " of " + shortId));
        paper.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                        Component.literal("\"" + preview + "\"").withStyle(ChatFormatting.GRAY))));
        ItemEntity drop = new ItemEntity(this.level(),
                this.getX(), this.getY() + 0.5, this.getZ(), paper);
        drop.setDefaultPickUpDelay();
        this.level().addFreshEntity(drop);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide || destination == null) {
            return;
        }

        if (this.blockPosition().distSqr(destination) < ARRIVAL_DIST_SQ) {
            CluckNet.LOGGER.info("PacketChicken delivered at {} (msg={} seq={}/{} type={})",
                    destination, messageId, sequence, totalChunks, chunkType);
            this.discard();
            return;
        }

        if (--ttlTicks <= 0) {
            CluckNet.LOGGER.info("PacketChicken TTL expired en route to {} (msg={} seq={}/{} type={})",
                    destination, messageId, sequence, totalChunks, chunkType);
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
        if (messageId != null && payload != null) {
            tag.putUUID(TAG_MESSAGE_ID, messageId);
            tag.putInt(TAG_SEQUENCE, sequence);
            tag.putInt(TAG_TOTAL_CHUNKS, totalChunks);
            tag.putByteArray(TAG_PAYLOAD, payload);
            if (originalLength >= 0) {
                tag.putInt(TAG_ORIGINAL_LENGTH, originalLength);
            }
            tag.putByte(TAG_CHUNK_TYPE, chunkType);
            if (senderPos != null) {
                tag.putLong(TAG_SENDER_POS, senderPos.asLong());
            }
        }
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
        if (tag.hasUUID(TAG_MESSAGE_ID) && tag.contains(TAG_PAYLOAD, Tag.TAG_BYTE_ARRAY)) {
            this.messageId = tag.getUUID(TAG_MESSAGE_ID);
            this.sequence = tag.getInt(TAG_SEQUENCE);
            this.totalChunks = tag.getInt(TAG_TOTAL_CHUNKS);
            this.payload = tag.getByteArray(TAG_PAYLOAD);
            this.originalLength = tag.contains(TAG_ORIGINAL_LENGTH, Tag.TAG_INT)
                    ? tag.getInt(TAG_ORIGINAL_LENGTH) : -1;
            this.chunkType = tag.contains(TAG_CHUNK_TYPE, Tag.TAG_BYTE) ? tag.getByte(TAG_CHUNK_TYPE) : TYPE_DATA;
            this.senderPos = tag.contains(TAG_SENDER_POS, Tag.TAG_LONG)
                    ? BlockPos.of(tag.getLong(TAG_SENDER_POS)) : null;
        }
    }

    /**
     * @param originalLength UTF-8 byte length of the full message. -1 = legacy chunk without parity.
     * @param chunkType {@link #TYPE_DATA} (sender→receiver) or {@link #TYPE_NACK} (receiver→sender retransmit request).
     * @param senderPos for DATA chunks this is the originating sender; for NACK chunks it is the originating receiver.
     */
    public record ChunkData(UUID messageId, int sequence, int totalChunks, byte[] payload,
                            int originalLength, byte chunkType, @Nullable BlockPos senderPos) {}
}
