package com.hijimasa.clucknet.block;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Reassembly buffer for one message.
 *
 * <p>Two formats are supported:
 * <ul>
 *   <li><b>Legacy</b> ({@code originalLength < 0}): variable-length data chunks, no parity. Complete when all chunks received.</li>
 *   <li><b>Parity-protected</b> ({@code originalLength >= 0}): {@code totalChunks - 1} zero-padded data chunks at indices
 *       {@code [0, totalChunks-1)}, plus a single XOR parity chunk at index {@code totalChunks - 1}. Up to one missing
 *       data chunk can be reconstructed from parity.</li>
 * </ul>
 */
public class MessageBuffer {
    private final UUID messageId;
    private final int totalChunks;
    private final int originalLength; // -1 = legacy
    private final byte[][] chunks;
    private final boolean[] received;
    private int receivedCount;
    private final long firstChunkTick;
    @Nullable
    private BlockPos senderPos;
    private boolean nackSent;

    public MessageBuffer(UUID messageId, int totalChunks, int originalLength, long firstChunkTick) {
        this.messageId = messageId;
        this.totalChunks = totalChunks;
        this.originalLength = originalLength;
        this.chunks = new byte[totalChunks][];
        this.received = new boolean[totalChunks];
        this.firstChunkTick = firstChunkTick;
    }

    @Nullable
    public BlockPos senderPos() {
        return senderPos;
    }

    public void rememberSender(@Nullable BlockPos pos) {
        if (this.senderPos == null && pos != null) {
            this.senderPos = pos.immutable();
        }
    }

    public boolean nackSent() {
        return nackSent;
    }

    public void markNackSent() {
        this.nackSent = true;
    }

    /** Returns the list of missing chunk indices (data + parity). Used for NACK encoding. */
    public int[] missingIndices() {
        int count = 0;
        for (int i = 0; i < totalChunks; i++) {
            if (!received[i]) count++;
        }
        int[] result = new int[count];
        int j = 0;
        for (int i = 0; i < totalChunks; i++) {
            if (!received[i]) result[j++] = i;
        }
        return result;
    }

    public UUID messageId() {
        return messageId;
    }

    public int totalChunks() {
        return totalChunks;
    }

    public int receivedCount() {
        return receivedCount;
    }

    public long firstChunkTick() {
        return firstChunkTick;
    }

    public boolean hasParity() {
        return originalLength >= 0;
    }

    public int parityIndex() {
        return hasParity() ? totalChunks - 1 : -1;
    }

    public int dataChunkCount() {
        return hasParity() ? totalChunks - 1 : totalChunks;
    }

    public boolean acceptChunk(int sequence, byte[] data) {
        if (sequence < 0 || sequence >= totalChunks || received[sequence]) {
            return false;
        }
        chunks[sequence] = data;
        received[sequence] = true;
        receivedCount++;
        return true;
    }

    /** A message is complete if every data chunk is either received directly or recoverable from parity. */
    public boolean isComplete() {
        int missingData = countMissingData();
        if (missingData == 0) {
            return true;
        }
        return hasParity() && missingData == 1 && received[parityIndex()];
    }

    /** True if completion required XOR-recovering a chunk from parity. */
    public boolean wasRecoveredByParity() {
        if (!hasParity()) {
            return false;
        }
        return countMissingData() == 1 && received[parityIndex()];
    }

    private int countMissingData() {
        int dataCount = dataChunkCount();
        int missing = 0;
        for (int i = 0; i < dataCount; i++) {
            if (!received[i]) {
                missing++;
            }
        }
        return missing;
    }

    /** Returns the original UTF-8 text. Assumes {@link #isComplete()} is true. */
    public String reconstruct() {
        if (!hasParity()) {
            // Legacy: just concatenate received chunks in order
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            for (int i = 0; i < totalChunks; i++) {
                if (chunks[i] != null) {
                    bos.writeBytes(chunks[i]);
                }
            }
            return bos.toString(StandardCharsets.UTF_8);
        }

        int dataCount = dataChunkCount();
        // MTU = any received chunk's length. Sender zero-pads data chunks to MTU,
        // so this works whether parity arrived or not.
        int mtu = 0;
        for (int i = 0; i < totalChunks; i++) {
            if (chunks[i] != null) {
                mtu = chunks[i].length;
                break;
            }
        }
        if (mtu == 0) {
            return "";
        }
        byte[] full = new byte[dataCount * mtu];

        for (int i = 0; i < dataCount; i++) {
            byte[] chunk;
            if (received[i]) {
                chunk = chunks[i];
            } else {
                // Recover via XOR: parity ^ all other received data chunks
                chunk = new byte[mtu];
                byte[] parity = chunks[parityIndex()];
                System.arraycopy(parity, 0, chunk, 0, mtu);
                for (int j = 0; j < dataCount; j++) {
                    if (j == i || !received[j]) {
                        continue;
                    }
                    byte[] dataChunk = chunks[j];
                    int copyLen = Math.min(mtu, dataChunk.length);
                    for (int b = 0; b < copyLen; b++) {
                        chunk[b] ^= dataChunk[b];
                    }
                }
            }
            int copyLen = Math.min(mtu, chunk.length);
            System.arraycopy(chunk, 0, full, i * mtu, copyLen);
        }

        int len = Math.min(originalLength, full.length);
        return new String(full, 0, len, StandardCharsets.UTF_8);
    }

    public String missingChunksString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < totalChunks; i++) {
            if (!received[i]) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                if (hasParity() && i == parityIndex()) {
                    sb.append("parity");
                } else {
                    sb.append(i);
                }
            }
        }
        return sb.toString();
    }
}
