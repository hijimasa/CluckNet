package com.hijimasa.clucknet.stats;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide counters for visualizing CluckNet traffic.
 *
 * <p>Numbers are best-effort: we increment from the server thread when entities are
 * spawned / captured / discarded. The client HUD reads them locally (works in
 * integrated singleplayer; in dedicated multiplayer the numbers reflect the local
 * server-side process only).</p>
 */
public final class CluckNetStats {
    public static final CluckNetStats INSTANCE = new CluckNetStats();

    private final AtomicLong dataChickensFired = new AtomicLong();
    private final AtomicLong parityChickensFired = new AtomicLong();
    private final AtomicLong retransmitted = new AtomicLong();
    private final AtomicLong chunksReceived = new AtomicLong();
    private final AtomicLong messagesReassembled = new AtomicLong();
    private final AtomicLong messagesRecoveredByParity = new AtomicLong();
    private final AtomicLong messagesTimedOut = new AtomicLong();
    private final AtomicLong nacksSent = new AtomicLong();
    private final AtomicLong chickensLost = new AtomicLong();

    private CluckNetStats() {}

    public void recordFire(int dataChunks, int parityChunks) {
        dataChickensFired.addAndGet(dataChunks);
        parityChickensFired.addAndGet(parityChunks);
    }

    public void recordRetransmit(int chunks) {
        retransmitted.addAndGet(chunks);
    }

    public void recordChunkReceived() {
        chunksReceived.incrementAndGet();
    }

    public void recordMessageComplete(boolean recoveredByParity) {
        messagesReassembled.incrementAndGet();
        if (recoveredByParity) {
            messagesRecoveredByParity.incrementAndGet();
        }
    }

    public void recordMessageTimeout() {
        messagesTimedOut.incrementAndGet();
    }

    public void recordNackSent() {
        nacksSent.incrementAndGet();
    }

    public void recordChickenLost() {
        chickensLost.incrementAndGet();
    }

    public long dataChickensFired() { return dataChickensFired.get(); }
    public long parityChickensFired() { return parityChickensFired.get(); }
    public long retransmitted() { return retransmitted.get(); }
    public long chunksReceived() { return chunksReceived.get(); }
    public long messagesReassembled() { return messagesReassembled.get(); }
    public long messagesRecoveredByParity() { return messagesRecoveredByParity.get(); }
    public long messagesTimedOut() { return messagesTimedOut.get(); }
    public long nacksSent() { return nacksSent.get(); }
    public long chickensLost() { return chickensLost.get(); }
}
