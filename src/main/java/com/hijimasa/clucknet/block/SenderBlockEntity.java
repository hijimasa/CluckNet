package com.hijimasa.clucknet.block;

import com.hijimasa.clucknet.CluckNet;
import com.hijimasa.clucknet.entity.ModEntities;
import com.hijimasa.clucknet.entity.PacketChicken;
import com.hijimasa.clucknet.menu.SenderMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SenderBlockEntity extends BlockEntity implements MenuProvider {
    private static final String TAG_DESTINATION = "Destination";
    private static final String TAG_WAS_POWERED = "WasPowered";
    private static final String TAG_MESSAGE = "Message";
    private static final String TAG_INPUT_ITEM = "InputItem";

    /** MTU in bytes — small on purpose so multi-chunk behavior is visible. */
    public static final int MTU_BYTES = 16;

    private static final int TICK_INTERVAL = 5;
    private static final double NACK_CAPTURE_RADIUS = 3.0D;
    private static final int SENT_STATE_TTL_TICKS = 20 * 60; // 60 sec — match receiver final timeout

    @Nullable
    private BlockPos destination;
    private boolean wasPowered;
    @Nullable
    private String pendingMessage;

    private boolean inAutoFire;
    private boolean deferAutoFire;
    private int tickCounter;

    /** Recently fired messages, kept for potential retransmission on NACK arrival. */
    private final Map<UUID, SentMessageState> sentMessages = new HashMap<>();

    private final SimpleContainer inputContainer = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            SenderBlockEntity.this.setChanged();
            SenderBlockEntity.this.tryAutoFire();
        }
    };

    public SenderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SENDER_BLOCK_ENTITY.get(), pos, state);
    }

    public Container getInputContainer() {
        return inputContainer;
    }

    @Nullable
    public BlockPos getDestination() {
        return destination;
    }

    public void setDestination(@Nullable BlockPos pos) {
        this.destination = pos == null ? null : pos.immutable();
        setChanged();
        tryAutoFire();
    }

    @Nullable
    public String getPendingMessage() {
        return pendingMessage;
    }

    public void setPendingMessage(@Nullable String message) {
        this.pendingMessage = (message == null || message.isEmpty()) ? null : message;
        setChanged();
    }

    public void onPowerStateChanged(boolean nowPowered) {
        if (nowPowered && !wasPowered) {
            firePackets();
        }
        if (wasPowered != nowPowered) {
            wasPowered = nowPowered;
            setChanged();
        }
    }

    /** Fires the buffered pendingMessage (or a payload-less packet if none). */
    public int firePackets() {
        return doFire(pendingMessage);
    }

    private int doFire(@Nullable String message) {
        Level level = this.level;
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        if (destination == null) {
            CluckNet.LOGGER.info("Sender at {} fired but has no destination set", getBlockPos());
            return 0;
        }

        if (message == null) {
            return spawnChicken(serverLevel, null, 0, 0, null, -1) ? 1 : 0;
        }

        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        int originalLength = bytes.length;
        int dataCount = Math.max(1, (originalLength + MTU_BYTES - 1) / MTU_BYTES);
        int totalChunks = dataCount + 1; // + 1 parity chunk at index dataCount
        UUID messageId = UUID.randomUUID();

        byte[][] chunks = new byte[totalChunks][MTU_BYTES];
        for (int i = 0; i < dataCount; i++) {
            int start = i * MTU_BYTES;
            int len = Math.min(MTU_BYTES, originalLength - start);
            if (len > 0) {
                System.arraycopy(bytes, start, chunks[i], 0, len);
            }
        }
        // parity = XOR of all data chunks
        byte[] parity = chunks[totalChunks - 1];
        for (int i = 0; i < dataCount; i++) {
            for (int b = 0; b < MTU_BYTES; b++) {
                parity[b] ^= chunks[i][b];
            }
        }

        int released = 0;
        for (int i = 0; i < totalChunks; i++) {
            if (spawnChicken(serverLevel, messageId, i, totalChunks, chunks[i], originalLength)) {
                released++;
            }
        }
        sentMessages.put(messageId, new SentMessageState(messageId, destination, chunks, totalChunks,
                originalLength, serverLevel.getGameTime()));
        com.hijimasa.clucknet.stats.CluckNetStats.INSTANCE.recordFire(dataCount, 1);
        CluckNet.LOGGER.info("Sender at {} released {} chickens (data={}, parity=1) for msg {} ({} B) toward {}",
                getBlockPos(), released, dataCount, messageId, originalLength, destination);
        return released;
    }

    private boolean spawnChicken(ServerLevel level, @Nullable UUID messageId, int seq, int totalChunks,
                                 @Nullable byte[] payload, int originalLength) {
        PacketChicken chicken = ModEntities.PACKET_CHICKEN.get().create(level);
        if (chicken == null) {
            return false;
        }
        BlockPos here = getBlockPos();
        RandomSource rng = level.getRandom();
        double dx = (rng.nextDouble() - 0.5D) * 0.4D;
        double dz = (rng.nextDouble() - 0.5D) * 0.4D;
        chicken.moveTo(here.getX() + 0.5D + dx, here.getY() + 1.0D, here.getZ() + 0.5D + dz,
                rng.nextFloat() * 360.0F, 0.0F);
        chicken.setDestination(destination);
        if (messageId != null && payload != null) {
            chicken.setChunkData(messageId, seq, totalChunks, payload, originalLength,
                    PacketChicken.TYPE_DATA, here);
        }
        level.addFreshEntity(chicken);
        return true;
    }

    public void serverTick() {
        if (++tickCounter % TICK_INTERVAL != 0) {
            return;
        }
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        captureNackChickens(serverLevel);
        purgeOldSentMessages(serverLevel.getGameTime());
    }

    private void captureNackChickens(ServerLevel level) {
        AABB box = new AABB(getBlockPos()).inflate(NACK_CAPTURE_RADIUS);
        BlockPos self = getBlockPos();
        List<PacketChicken> nearby = level.getEntitiesOfClass(PacketChicken.class, box,
                c -> self.equals(c.getDestination()));
        if (nearby.isEmpty()) {
            return;
        }
        for (PacketChicken chicken : nearby) {
            PacketChicken.ChunkData data = chicken.getChunkData();
            if (data == null || data.chunkType() != PacketChicken.TYPE_NACK) {
                continue;
            }
            chicken.discard();
            handleNack(level, data);
        }
    }

    private void handleNack(ServerLevel level, PacketChicken.ChunkData nack) {
        SentMessageState state = sentMessages.get(nack.messageId());
        if (state == null) {
            CluckNet.LOGGER.info("Sender at {} got NACK for unknown msg {} (already purged?)",
                    getBlockPos(), nack.messageId());
            return;
        }
        int[] missingIndices = decodeMissingIndices(nack.payload());
        if (missingIndices.length == 0) {
            return;
        }
        int retransmitted = 0;
        for (int idx : missingIndices) {
            if (idx < 0 || idx >= state.totalChunks()) {
                continue;
            }
            if (spawnChicken(level, state.messageId(), idx, state.totalChunks(),
                    state.chunks()[idx], state.originalLength())) {
                retransmitted++;
            }
        }
        com.hijimasa.clucknet.stats.CluckNetStats.INSTANCE.recordRetransmit(retransmitted);
        CluckNet.LOGGER.info("Sender at {} retransmitted {} chunks for msg {} (requested {})",
                getBlockPos(), retransmitted, state.messageId(), missingIndices.length);
    }

    private static int[] decodeMissingIndices(byte[] payload) {
        int count = payload.length / 4;
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = ((payload[i * 4] & 0xFF) << 24)
                    | ((payload[i * 4 + 1] & 0xFF) << 16)
                    | ((payload[i * 4 + 2] & 0xFF) << 8)
                    | (payload[i * 4 + 3] & 0xFF);
        }
        return out;
    }

    private void purgeOldSentMessages(long now) {
        if (sentMessages.isEmpty()) {
            return;
        }
        Iterator<SentMessageState> it = sentMessages.values().iterator();
        while (it.hasNext()) {
            SentMessageState s = it.next();
            if (now - s.fireTime() > SENT_STATE_TTL_TICKS) {
                it.remove();
            }
        }
    }

    private void tryAutoFire() {
        if (inAutoFire || deferAutoFire) {
            return;
        }
        if (!(this.level instanceof ServerLevel)) {
            return;
        }
        if (destination == null) {
            return;
        }
        ItemStack stack = inputContainer.getItem(0);
        if (stack.isEmpty() || !stack.is(Items.WRITABLE_BOOK)) {
            return;
        }
        String text = extractText(stack);
        if (text.isEmpty()) {
            return;
        }

        inAutoFire = true;
        try {
            int released = doFire(text);
            if (released > 0) {
                if (stack.getCount() <= 1) {
                    inputContainer.setItem(0, ItemStack.EMPTY);
                } else {
                    stack.shrink(1);
                    inputContainer.setChanged();
                }
            }
        } finally {
            inAutoFire = false;
        }
    }

    private static String extractText(ItemStack stack) {
        WritableBookContent content = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (content == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Filterable<String> page : content.pages()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(page.raw());
        }
        return sb.toString().trim();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.clucknet.sender_block");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new SenderMenu(containerId, playerInv, this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        // Drop the input item into the world so it isn't lost
        if (this.level instanceof ServerLevel serverLevel) {
            ItemStack stack = inputContainer.getItem(0);
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropContents(serverLevel, getBlockPos(), inputContainer);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        if (destination != null) {
            tag.putLong(TAG_DESTINATION, destination.asLong());
        }
        tag.putBoolean(TAG_WAS_POWERED, wasPowered);
        if (pendingMessage != null) {
            tag.putString(TAG_MESSAGE, pendingMessage);
        }
        ItemStack stack = inputContainer.getItem(0);
        if (!stack.isEmpty()) {
            tag.put(TAG_INPUT_ITEM, stack.save(lookup, new CompoundTag()));
        }
        // sentMessages is intentionally NOT persisted — short-lived retransmission state
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        deferAutoFire = true;
        try {
            if (tag.contains(TAG_DESTINATION, Tag.TAG_LONG)) {
                this.destination = BlockPos.of(tag.getLong(TAG_DESTINATION));
            } else {
                this.destination = null;
            }
            this.wasPowered = tag.getBoolean(TAG_WAS_POWERED);
            if (tag.contains(TAG_MESSAGE, Tag.TAG_STRING)) {
                String msg = tag.getString(TAG_MESSAGE);
                this.pendingMessage = msg.isEmpty() ? null : msg;
            } else {
                this.pendingMessage = null;
            }
            if (tag.contains(TAG_INPUT_ITEM, Tag.TAG_COMPOUND)) {
                ItemStack stack = ItemStack.parseOptional(lookup, tag.getCompound(TAG_INPUT_ITEM));
                inputContainer.setItem(0, stack);
            } else {
                inputContainer.setItem(0, ItemStack.EMPTY);
            }
        } finally {
            deferAutoFire = false;
        }
    }

    private record SentMessageState(UUID messageId, BlockPos destination, byte[][] chunks,
                                    int totalChunks, int originalLength, long fireTime) {}
}
