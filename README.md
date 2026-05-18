# CluckNet

**English** | [日本語](README.ja.md)

> An implementation of [RFC 1149 "A Standard for the Transmission of IP Datagrams on Avian Carriers"](https://datatracker.ietf.org/doc/html/rfc1149) for Minecraft.

Packets are physically carried between blocks by **chickens**. Their natural inability to fly, navigate obstacles, or survive mob attacks reproduces real IP-style packet loss and latency &mdash; obstacles, water, mobs and accidental player attacks all become legitimate sources of network failure.

This Minecraft Forge mod is, primarily, a joke. It is secondarily a hands-on sandbox for thinking about how lossy networks, forward error correction, and retransmission protocols actually behave when they are run on top of physics instead of fiber.

---

## Background

[RFC 1149](https://datatracker.ietf.org/doc/html/rfc1149) (April 1, 1990) and its successor [RFC 2549](https://datatracker.ietf.org/doc/html/rfc2549) (April 1, 1999) define the transmission of IP datagrams using avian carriers (i.e. pigeons). CluckNet brings that joke into a world where you can actually watch it run.

Why chickens and not parrots?
- **Chickens cannot fly.** They glide, get stuck on 1.5-block ledges, drown in water, and die to wolves. This makes them an excellent best-effort delivery substrate.
- A 13-block walk between sender and receiver is *the* throughput bottleneck and a constant source of dropped packets, exactly as one would hope.

### What this mod actually adds

| Concept | RFC 1149 term | CluckNet realisation |
|---|---|---|
| Sender host | Origin gateway | **Packet Sender block** (red top) |
| Receiver host | Destination gateway | **Packet Receiver block** (blue top) |
| Datagram | IP datagram | `CompoundTag` payload carried on a chicken |
| Avian carrier | Carrier | **Packet Chicken** entity |
| MTU | 1500 bytes | **16 bytes** (small on purpose so chunking is visible) |
| ACK / NACK | TCP ACK | A return chicken with a packed list of missing chunk indices |
| Forward error correction | (none in RFC 1149) | One **XOR parity chunk** per message |

---

## Requirements

- Minecraft **1.20.6**
- Minecraft Forge **50.1.x**
- **Java 21** (downloaded automatically by Gradle if missing)
- A reasonably permissive Linux inotify configuration:
  ```
  /proc/sys/fs/inotify/max_user_instances >= 256
  /proc/sys/fs/inotify/max_user_watches   >= 262144
  ```
  If you see `Couldn't watch config file` on startup, see [Troubleshooting](#troubleshooting).

---

## Quick start (development client)

```bash
git clone <this-repo-url> CluckNet
cd CluckNet
./runClient.sh
```

The first run takes several minutes &mdash; Forge MDK downloads Minecraft, decompiles it, and applies mappings. Subsequent runs are fast.

When the title screen appears: Singleplayer → Create New World → Creative, Flat preset is fine.

---

## Walkthrough: send a message

### 1. Get the blocks
Open the creative inventory and switch to the **CluckNet** tab. You will see:
- **Packet Sender** (red top, wooden body) &mdash; the outbound endpoint
- **Packet Receiver** (blue top, wooden body) &mdash; the inbound endpoint

In survival, both blocks can be crafted:

```
Sender                Receiver
P R P                 P L P
P F P                 P E P
P P P                 P P P
```

- `P` = Oak Planks &nbsp; `R` = Redstone Block &nbsp; `F` = Feather
- `L` = Lapis Block &nbsp; `E` = Ender Eye

### 2. Place them somewhere they can reach each other
Put a Sender and a Receiver on the ground, separated by some distance with one block of clearance above each.

### 3. Link them
Two ways:

**A. Sneak-right-click pairing (recommended)**

1. Sneak (Shift) and right-click the **Receiver** &mdash; chat: `Receiver -32, 64, 92 captured. Sneak-right-click a Sender to complete.`
2. Sneak and right-click the **Sender** &mdash; chat: `Linked Sender ... → Receiver ...`

**B. Type the coordinates in the Sender GUI**

1. Right-click the Sender (no sneak) &mdash; the menu opens.
2. In the **Destination** field, type the receiver's coordinates (e.g. `-32 64 92` or `-32,64,92`).
3. The status line reads `Linked → -32, 64, 92` and the Sender is now bound.

**C. Command-based fallback**

```
/clucknet link <sender_pos> <destination_pos>
```

### 4. Write and send a message
1. Get a **Book and Quill** (Writable Book) &mdash; either from the Creative inventory ("Tools & Utilities" tab) or craft `Book + Ink Sac + Feather`.
2. Right-click while holding it, write a message, close the book (do **not** sign it &mdash; signed books are kept for archiving and are not accepted by the Sender).
3. Right-click the Sender to open its GUI.
4. Drop the Book and Quill into the central input slot.
5. The book is consumed; one chicken per data chunk plus one parity chicken is released.

### 5. Watch the chickens
- Each chicken walks toward the receiver via a biased random walk (heading + Gaussian noise).
- Right-click a chicken with an empty hand to **inspect its payload**:
  ```
  Packet 8bc05e5f — chunk 1/3 (16 B) → -32,64,92
  Payload: "Hello CluckNet"
  ```
- Kill a chicken to drop a **paper item** containing the lost chunk's preview.

### 6. Receiving
- On successful reassembly:
  - Chat: `[CluckNet @ -32,64,92] received: "Hello CluckNet RFC..."` (aqua)
  - A **Written Book** is added to the receiver's internal inventory (open it by right-clicking the receiver).
- If one chicken was lost but the parity arrived: same as above with `(1 chunk recovered via parity)` (gold).
- If more than one chunk is missing after 20 s: the receiver releases a **NACK chicken** back toward the sender. The sender, on capture, retransmits exactly the missing chunks.
- If the message is still incomplete at 60 s: `[CluckNet @ ...] incomplete: M/N chunks (missing 0,2)` (red).

### 7. Stats command
Run `/clucknet stats` to print process-wide packet counters to chat:
```
[CluckNet stats] sent: data=12 parity=4 retransmit=1  recv: chunks=11 msgs=3 parity-fix=1  loss: timeouts=0 nacks=1 chickens=2
```
(Forge 1.20.6 removed the HUD-overlay event system in favour of Mojang's
`LayeredDraw` API, which currently has no Forge-side registration hook — a
chat command is the simplest way to expose the same counters.)

---

## Protocol summary

```
[Sender Block]
   message (UTF-8 bytes)
     ↓  chunk into 16-byte data chunks, zero-pad the last one
     ↓  XOR all data chunks → parity chunk
   spawn (N+1) Packet Chickens → walk toward receiver
   keep SentMessageState for 60s for retransmission

[Packet Chicken]                      [Receiver Block]
   ground path with                       ticks every 5 ticks
   noise(8 block waypoints +              capture chickens with destination == self
        Gaussian σ ≈ 30°)                 reassemble per-messageId buffer
   TTL = 3 minutes                        complete? → write Written Book to inventory
   right-click → reveal payload                       broadcast "received"
   die → drop a labelled paper             stuck 20 s? → emit NACK chicken back
                                          incomplete at 60 s? → broadcast "incomplete"
```

NACK encoding: the chicken's `payload` is a packed big-endian array of `int32` missing-chunk indices. The chicken's destination is the original sender's BlockPos.

XOR parity recovery: if exactly one data chunk is missing AND the parity chunk was received, the receiver reconstructs the missing chunk by XOR-ing the parity with the remaining received data chunks (zero-padded to MTU).

---

## Delivery semantics (TCP-style eager output)

The receiver follows standard TCP-like delivery semantics: **a message is delivered as soon as enough chunks have arrived to reconstruct it.** It does **not** wait for every expected chicken to land before declaring the message complete.

| Arrival pattern | What the receiver does |
|---|---|
| All N data chunks arrive | Reconstruct immediately; deliver. The parity chunk, when it later arrives, is silently discarded as redundant. |
| N − 1 data chunks + parity arrive | XOR-recover the missing data chunk; deliver immediately. A late-arriving data chunk for the same message is silently discarded. |
| Fewer than enough chunks arrive within 60 s | Broadcast incomplete; the messageId is also remembered as "finalised" so any very-late chunks are not mistaken for a new message. |

Each message carries a freshly generated `UUID`. The receiver keeps a `Map<UUID, finalisedTick>` for 60 seconds after finalisation:

```java
if (finalizedMessages.containsKey(data.messageId())) continue; // late redundant chunk — drop
```

Because the key is the per-message UUID, **the suppression is strictly per-message** &mdash; the next message has a fresh UUID, never collides with a finalised entry, and is processed normally. There is no cross-message blocking.

**Why eager and not "wait for all chickens"?**
- Parity is purely a redundancy / recovery aid. When the data is already complete, the parity chunk carries no new information; making the user wait for it adds latency for no benefit.
- If parity is the chunk that gets lost, waiting for it would force the user to sit through the 60 s timeout before seeing a message that was, in fact, already reassembled.
- This matches RFC 793 (TCP) receiver behaviour and every standards-compliant transport: deliver in-order data as soon as it is available; deduplicate redundant arrivals.

The trade-off: visually, the chat message and the Written Book in the receiver inventory often appear **before all the chickens have walked in**. The late chickens then arrive, are recognised as redundant, and are silently dropped. This is normal &mdash; it's the network operating at maximum throughput.

---

## Project layout

```
src/main/java/com/hijimasa/clucknet/
├── CluckNet.java              entry point; registers everything
├── block/                     Sender / Receiver blocks + their block entities
├── entity/
│   ├── ModEntities.java       registers the Packet Chicken
│   ├── PacketChicken.java     extends vanilla Chicken; carries chunk NBT
│   └── goal/WanderTowardReceiverGoal.java   biased random walk AI
├── menu/                      AbstractContainerMenu + IForgeMenuType wiring
├── client/                    GUI screens, MenuScreens registration, stats HUD
├── network/                   Forge SimpleChannel + UpdateSenderDestinationPacket
├── event/LinkingHandler.java  sneak-click pairing
├── command/CluckNetCommand.java  /clucknet send/link/fire/message
├── stats/CluckNetStats.java   per-process counters
└── item/                      block items + creative tab

src/main/resources/
├── META-INF/mods.toml
├── data/clucknet/             recipes + loot tables
└── assets/clucknet/           blockstates, models, lang, textures (vanilla refs)
```

---

## Commands

| Command | Effect |
|---|---|
| `/clucknet send <pos>` | Spawn a payload-less chicken at the player's position heading to `<pos>` (Phase 1 sanity check). |
| `/clucknet link <sender_pos> <destination_pos>` | Bind a Sender block to a destination. |
| `/clucknet fire <sender_pos>` | Manually trigger a Sender block. |
| `/clucknet message <sender_pos> <text...>` | Set the Sender's pending message (sent on next redstone pulse or manual fire). |
| `/clucknet stats` | Print packet counters to chat (replacement for the removed HUD overlay). |

All commands are also useable from the in-game `/` console, so the GUI flow and command flow can be mixed.

---

## Troubleshooting

### `Couldn't watch config file` on startup

This is **not a CluckNet bug**. Forge uses the JVM `WatchService`, which on Linux backs onto `inotify`. Many development setups (multiple VSCode/IntelliJ/Cursor instances, file watchers, etc.) exhaust the default `fs.inotify.max_user_instances = 128` long before Forge gets to load.

Temporary fix:
```bash
sudo sysctl -w fs.inotify.max_user_instances=512
sudo sysctl -w fs.inotify.max_user_watches=524288
```

Permanent fix:
```bash
sudo tee /etc/sysctl.d/99-inotify-limits.conf <<'EOF'
fs.inotify.max_user_instances = 512
fs.inotify.max_user_watches   = 524288
EOF
sudo sysctl --system
```

### `Module jopt.simple not found` on startup

Forge 1.20.6 + Java 21 needs `jopt-simple` pinned to `5.0.4` (otherwise its Automatic-Module-Name does not match what `cpw.mods.modlauncher` requests). This is already pinned in [`build.gradle`](build.gradle); if you fork the build script, keep that constraint.

### Chickens get stuck and never deliver

This is the feature, not the bug. Real fixes:
- Flatten the path between Sender and Receiver (chickens can step 1 block but not 2).
- Don't leave water flowing across the route.
- Don't shoot the chickens with a bow when bored.

If you want guaranteed delivery, raise the parity chunk count (TODO &mdash; currently fixed at 1) or wait for the NACK + retransmit cycle.

---

## Phase status

| Phase | Feature | State |
|---|---|---|
| 0 | Forge skeleton from `minecraft_ros2` template | ✅ |
| 1 | `PacketChicken` entity + biased random walk + `/clucknet send` | ✅ |
| 2 | Sender block, redstone trigger, `/clucknet link/fire` | ✅ |
| 3 | Receiver block, payload chunking, message reassembly | ✅ |
| 4a | Sender GUI: Writable Book input + destination EditBox | ✅ |
| 4b | Receiver GUI: output inventory of received Written Books | ✅ |
| 4c | Sneak-right-click pairing | ✅ |
| 5a | XOR parity chunk for single-chunk loss recovery | ✅ |
| 5b | NACK return chickens, retransmission of missing chunks | ✅ |
| 6a | Crafting recipes for the two blocks | ✅ |
| 6b | Stats counter (`/clucknet stats` — HUD overlay deferred; see note below) | ✅ |
| 6c | Drop-on-death (lost chunks materialise as labelled paper) | ✅ |
| 7 (future) | ROS 2 bridge (optional, mirror of [`minecraft_ros2`](https://github.com/minecraft-ros2/minecraft_ros2)) | — |

Design notes: [`../IPoAC_Minecraft_Mod_方針資料.md`](../IPoAC_Minecraft_Mod_%E6%96%B9%E9%87%9D%E8%B3%87%E6%96%99.md) (in Japanese).

---

## License

MIT. See [LICENSE](LICENSE).

Derived from the structure of [`minecraft_ros2`](https://github.com/minecraft-ros2/minecraft_ros2) (Kazusa Hashimoto), used as a Forge 1.20.6 / Java 21 reference. None of the ROS 2 integration code is included here.
