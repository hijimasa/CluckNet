<!--
  Draft copy for the Modrinth project page.
  Paste sections into the corresponding Modrinth fields when creating the project.
  Not consumed by any tooling — kept in-repo so the published page and the
  README stay aligned.
-->

# Modrinth project setup notes

## 1. Project metadata (fill in the create-project form)

| Field | Value |
|---|---|
| Project name | `CluckNet` |
| Project ID / slug | `clucknet` |
| Project type | Mod |
| Summary (≤ 256 chars, plain text) | *(see below)* |
| License | `MIT` |
| Environments | Client: required, Server: required |
| Source code | `https://github.com/hijimasa/CluckNet` |
| Issue tracker | `https://github.com/hijimasa/CluckNet/issues` |
| Categories (pick from Modrinth's list) | `technology`, `transportation`, `mobs` |
| Loaders | `forge` |
| Game versions | `1.20.6` |

### Summary (one-liner)

> RFC 1149 (IP over Avian Carriers) for Minecraft — packets are physically carried by chickens between sender and receiver blocks. Lossy by design.

---

## 2. Long description (paste into the "Description" Markdown editor)

> An implementation of [RFC 1149 "A Standard for the Transmission of IP Datagrams on Avian Carriers"](https://datatracker.ietf.org/doc/html/rfc1149) for Minecraft.

![Demo](https://raw.githubusercontent.com/hijimasa/CluckNet/main/figs/CluckNet_demo.webp)

Packets are physically carried between blocks by **chickens**. Their natural inability to fly, navigate obstacles, or survive mob attacks reproduces real IP-style packet loss and latency — obstacles, water, mobs and accidental player attacks all become legitimate sources of network failure.

This Forge mod is, primarily, a joke. It is secondarily a hands-on sandbox for thinking about how lossy networks, forward error correction, and retransmission protocols actually behave when they run on top of physics instead of fiber.

---

## What it adds

| Concept | RFC 1149 term | CluckNet realisation |
|---|---|---|
| Sender host | Origin gateway | **Packet Sender** block (red top) |
| Receiver host | Destination gateway | **Packet Receiver** block (blue top) |
| Datagram | IP datagram | `CompoundTag` payload on a chicken |
| Avian carrier | Carrier | **Packet Chicken** entity |
| MTU | 1500 bytes | **16 bytes** (small on purpose so chunking is visible) |
| ACK / NACK | TCP ACK | A return chicken with packed missing-chunk indices |
| Forward error correction | (none in RFC 1149) | One **XOR parity chunk** per message |

## How to use it (60-second version)

1. Place a **Packet Sender** and a **Packet Receiver** somewhere a chicken can walk between (1-block step max, no water across the path).
2. Sneak-right-click the Receiver, then sneak-right-click the Sender — they are now linked.
3. Write a message in a **Book and Quill** (do **not** sign it) and drop it into the Sender's GUI.
4. Watch chickens walk across the world carrying chunks of your packet.
5. The Receiver hands you back a **Written Book** when reassembly succeeds.
6. If a chicken dies, you get a paper item with the lost chunk's preview. If too many die, a **NACK chicken** is automatically sent back and the Sender retransmits the missing chunks.

`/clucknet stats` prints process-wide packet counters (data / parity / retransmit / NACK / chickens lost).

## Why chickens specifically?

- Chickens **cannot fly**. They glide, get stuck on 1.5-block ledges, drown in water, and die to wolves.
- A 13-block walk between sender and receiver is *the* throughput bottleneck and a constant source of dropped packets — exactly what you want from a best-effort delivery substrate.

## Delivery semantics

The receiver follows TCP-like delivery: **a message is delivered as soon as enough chunks have arrived to reconstruct it.** It does *not* wait for every expected chicken.

- All N data chunks arrive → reconstruct & deliver. Late parity chunk silently dropped as redundant.
- N − 1 data chunks + parity arrive → XOR-recover the missing chunk & deliver. Any later-arriving data chunk for the same message is dropped.
- Fewer than enough within 60 s → broadcast `incomplete`; the message ID is remembered for another 60 s so very-late chunks are not mistaken for a new message.

The trade-off: visually, the chat message often appears **before all the chickens have walked in**. The late chickens then arrive, are recognised as redundant, and are silently dropped. This is the network operating at maximum throughput.

## Requirements

- Minecraft **1.20.6**
- Minecraft Forge **50.1.x**
- Java 21 (Gradle downloads it on first run if missing)

> CluckNet currently targets 1.20.6 / Forge only. Ports to 1.20.1 (Forge) or 1.21.x (NeoForge) as PRs are welcome — see the [GitHub repository](https://github.com/hijimasa/CluckNet) for details.

## More

- **Full README, protocol details, troubleshooting:** <https://github.com/hijimasa/CluckNet>
- **Source / issues:** <https://github.com/hijimasa/CluckNet/issues>
- **Background:** [RFC 1149](https://datatracker.ietf.org/doc/html/rfc1149) (1990-04-01) and [RFC 2549](https://datatracker.ietf.org/doc/html/rfc2549) (1999-04-01).

## License

MIT. See [LICENSE](https://github.com/hijimasa/CluckNet/blob/main/LICENSE).
