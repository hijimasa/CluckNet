# CluckNet

An implementation of [RFC 1149 "A Standard for the Transmission of IP Datagrams on Avian Carriers"](https://datatracker.ietf.org/doc/html/rfc1149) for Minecraft.

Packets are physically carried by chickens between sender and receiver blocks. Packet loss, latency, and routing follies are reproduced by the chickens themselves &mdash; obstacles, water, mobs and accidental player attacks all become legitimate sources of network failure.

- Minecraft 1.20.6 / Forge 50.1.0 / Java 21
- See [`../IPoAC_Minecraft_Mod_方針資料.md`](../IPoAC_Minecraft_Mod_%E6%96%B9%E9%87%9D%E8%B3%87%E6%96%99.md) for the design plan (in Japanese)

## Quick start

```bash
./runClient.sh   # launches a dev Minecraft client with the mod loaded
```

The first run takes a while as Forge sets up MDK and decompiles Minecraft.

## Status

Phase 0: project skeleton derived from [minecraft_ros2](https://github.com/minecraft-ros2/minecraft_ros2).
