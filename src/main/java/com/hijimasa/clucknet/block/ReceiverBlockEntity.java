package com.hijimasa.clucknet.block;

import com.hijimasa.clucknet.CluckNet;
import com.hijimasa.clucknet.entity.ModEntities;
import com.hijimasa.clucknet.entity.PacketChicken;
import com.hijimasa.clucknet.menu.ReceiverMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReceiverBlockEntity extends BlockEntity implements MenuProvider {
    private static final double CAPTURE_RADIUS = 3.0D;
    private static final int NACK_THRESHOLD_TICKS = 20 * 20;       // 20 seconds — send NACK if still incomplete
    private static final int REASSEMBLY_TIMEOUT_TICKS = 20 * 60;   // 60 seconds — final timeout
    private static final int TICK_INTERVAL = 5;

    public static final int OUTPUT_SLOTS = 9;
    private static final int CHARS_PER_PAGE = 250;
    private static final int PREVIEW_LIMIT = 30;
    /** How long a finalized message id is remembered to suppress late-arriving redundant chunks. */
    private static final int FINALIZED_TTL_TICKS = 20 * 60; // 60 seconds, matches sender's SENT_STATE_TTL_TICKS
    private static final String TAG_OUTPUT_ITEMS = "OutputItems";

    private final Map<UUID, MessageBuffer> buffers = new HashMap<>();
    /** messageId → game-time tick at which it was finalized (complete or timed out). */
    private final Map<UUID, Long> finalizedMessages = new HashMap<>();
    private int tickCounter;

    private final SimpleContainer outputContainer = new SimpleContainer(OUTPUT_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            ReceiverBlockEntity.this.setChanged();
        }
    };

    public ReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RECEIVER_BLOCK_ENTITY.get(), pos, state);
    }

    public Container getOutputContainer() {
        return outputContainer;
    }

    public int bufferCount() {
        return buffers.size();
    }

    public void serverTick() {
        if (++tickCounter % TICK_INTERVAL != 0) {
            return;
        }
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        capturePackets(serverLevel);
        expireStaleBuffers(serverLevel);
        purgeFinalized(serverLevel.getGameTime());
    }

    private void purgeFinalized(long now) {
        if (finalizedMessages.isEmpty()) {
            return;
        }
        finalizedMessages.values().removeIf(t -> now - t > FINALIZED_TTL_TICKS);
    }

    private void capturePackets(ServerLevel level) {
        AABB box = new AABB(getBlockPos()).inflate(CAPTURE_RADIUS);
        BlockPos self = getBlockPos();
        List<PacketChicken> nearby = level.getEntitiesOfClass(PacketChicken.class, box,
                c -> self.equals(c.getDestination()));
        if (nearby.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (PacketChicken chicken : nearby) {
            PacketChicken.ChunkData data = chicken.getChunkData();
            chicken.discard();
            if (data == null) {
                CluckNet.LOGGER.info("Receiver at {} captured payload-less chicken", self);
                continue;
            }
            if (data.chunkType() != PacketChicken.TYPE_DATA) {
                // Receivers ignore NACK chickens — only Senders handle those.
                continue;
            }
            if (finalizedMessages.containsKey(data.messageId())) {
                // Late-arriving redundant chunk for a message already finalized — drop silently.
                continue;
            }
            MessageBuffer buf = buffers.computeIfAbsent(data.messageId(),
                    id -> new MessageBuffer(id, data.totalChunks(), data.originalLength(), now));
            buf.rememberSender(data.senderPos());
            if (!buf.acceptChunk(data.sequence(), data.payload())) {
                continue;
            }
            com.hijimasa.clucknet.stats.CluckNetStats.INSTANCE.recordChunkReceived();
            String chunkLabel = (buf.hasParity() && data.sequence() == buf.parityIndex())
                    ? "parity" : (data.sequence() + 1) + "/" + buf.dataChunkCount();
            CluckNet.LOGGER.info("Receiver at {} got chunk {} of msg {}",
                    self, chunkLabel, data.messageId());
            if (buf.isComplete()) {
                boolean recovered = buf.wasRecoveredByParity();
                String text = buf.reconstruct();
                com.hijimasa.clucknet.stats.CluckNetStats.INSTANCE.recordMessageComplete(recovered);
                if (recovered) {
                    CluckNet.LOGGER.info("Receiver at {} reassembled msg {} via parity recovery: \"{}\"",
                            self, data.messageId(), text);
                } else {
                    CluckNet.LOGGER.info("Receiver at {} reassembled msg {}: \"{}\"",
                            self, data.messageId(), text);
                }
                materializeMessage(level, data.messageId(), text);
                broadcastShortNotice(level.getServer(), self, data.messageId(), text, recovered);
                buffers.remove(data.messageId());
                finalizedMessages.put(data.messageId(), now);
            }
        }
    }

    private void expireStaleBuffers(ServerLevel level) {
        if (buffers.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        BlockPos self = getBlockPos();
        Iterator<Map.Entry<UUID, MessageBuffer>> it = buffers.entrySet().iterator();
        while (it.hasNext()) {
            MessageBuffer buf = it.next().getValue();
            long age = now - buf.firstChunkTick();

            // Mid-window NACK: at 20s, if not complete and we know the sender, request retransmit once.
            if (age > NACK_THRESHOLD_TICKS && !buf.nackSent() && buf.senderPos() != null && !buf.isComplete()) {
                sendNack(level, buf);
                buf.markNackSent();
                com.hijimasa.clucknet.stats.CluckNetStats.INSTANCE.recordNackSent();
            }

            if (age <= REASSEMBLY_TIMEOUT_TICKS) {
                continue;
            }
            String missing = buf.missingChunksString();
            String partial = buf.reconstruct();
            CluckNet.LOGGER.info(
                    "Receiver at {} timeout on msg {}: got {}/{}, missing [{}], partial=\"{}\"",
                    self, buf.messageId(), buf.receivedCount(), buf.totalChunks(), missing, partial);
            broadcast(level.getServer(), Component
                    .literal("[CluckNet @ " + self.toShortString() + "] incomplete: "
                            + buf.receivedCount() + "/" + buf.totalChunks()
                            + " chunks (missing " + missing + ")")
                    .withStyle(ChatFormatting.RED));
            com.hijimasa.clucknet.stats.CluckNetStats.INSTANCE.recordMessageTimeout();
            finalizedMessages.put(buf.messageId(), now);
            it.remove();
        }
    }

    private void sendNack(ServerLevel level, MessageBuffer buf) {
        BlockPos senderPos = buf.senderPos();
        if (senderPos == null) {
            return;
        }
        int[] missing = buf.missingIndices();
        if (missing.length == 0) {
            return;
        }
        byte[] payload = new byte[missing.length * 4];
        for (int i = 0; i < missing.length; i++) {
            int idx = missing[i];
            payload[i * 4]     = (byte) ((idx >>> 24) & 0xFF);
            payload[i * 4 + 1] = (byte) ((idx >>> 16) & 0xFF);
            payload[i * 4 + 2] = (byte) ((idx >>> 8) & 0xFF);
            payload[i * 4 + 3] = (byte) (idx & 0xFF);
        }
        PacketChicken chicken = ModEntities.PACKET_CHICKEN.get().create(level);
        if (chicken == null) {
            return;
        }
        BlockPos here = getBlockPos();
        chicken.moveTo(here.getX() + 0.5D, here.getY() + 1.0D, here.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        chicken.setDestination(senderPos);
        chicken.setChunkData(buf.messageId(), 0, 0, payload, -1,
                PacketChicken.TYPE_NACK, here);
        level.addFreshEntity(chicken);
        CluckNet.LOGGER.info("Receiver at {} sent NACK to {} for msg {} missing {} chunks",
                here, senderPos, buf.messageId(), missing.length);
    }

    private void materializeMessage(ServerLevel level, UUID messageId, String text) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        String shortId = messageId.toString().substring(0, 8);
        String title = "Pkt " + shortId;
        List<Filterable<Component>> pages = new ArrayList<>();
        if (text.isEmpty()) {
            pages.add(Filterable.passThrough(Component.literal("(empty)")));
        } else {
            for (int start = 0; start < text.length(); start += CHARS_PER_PAGE) {
                int end = Math.min(start + CHARS_PER_PAGE, text.length());
                pages.add(Filterable.passThrough(Component.literal(text.substring(start, end))));
            }
        }
        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough(title),
                "CluckNet",
                0,
                pages,
                true);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);

        if (!tryAddToOutput(book)) {
            BlockPos pos = getBlockPos();
            ItemEntity drop = new ItemEntity(level,
                    pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, book);
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
            CluckNet.LOGGER.info("Receiver at {} output full, dropped book msg {}", pos, messageId);
        }
    }

    private boolean tryAddToOutput(ItemStack stack) {
        for (int i = 0; i < outputContainer.getContainerSize(); i++) {
            if (outputContainer.getItem(i).isEmpty()) {
                outputContainer.setItem(i, stack);
                return true;
            }
        }
        return false;
    }

    private static void broadcastShortNotice(MinecraftServer server, BlockPos self, UUID messageId,
                                             String text, boolean recovered) {
        String preview = text.length() > PREVIEW_LIMIT ? text.substring(0, PREVIEW_LIMIT) + "..." : text;
        String suffix = recovered ? "  (1 chunk recovered via parity)" : "";
        broadcast(server, Component
                .literal("[CluckNet @ " + self.toShortString() + "] received: \"" + preview + "\"" + suffix)
                .withStyle(recovered ? ChatFormatting.GOLD : ChatFormatting.AQUA));
    }

    private static void broadcast(MinecraftServer server, Component msg) {
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(msg, false);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.clucknet.receiver_block");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ReceiverMenu(containerId, playerInv, this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level instanceof ServerLevel serverLevel) {
            Containers.dropContents(serverLevel, getBlockPos(), outputContainer);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        ListTag list = new ListTag();
        for (int i = 0; i < outputContainer.getContainerSize(); i++) {
            ItemStack stack = outputContainer.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("Slot", i);
                stack.save(lookup, itemTag);
                list.add(itemTag);
            }
        }
        tag.put(TAG_OUTPUT_ITEMS, list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        outputContainer.clearContent();
        if (tag.contains(TAG_OUTPUT_ITEMS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_OUTPUT_ITEMS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                int slot = itemTag.getInt("Slot");
                ItemStack stack = ItemStack.parseOptional(lookup, itemTag);
                if (slot >= 0 && slot < outputContainer.getContainerSize()) {
                    outputContainer.setItem(slot, stack);
                }
            }
        }
    }
}
