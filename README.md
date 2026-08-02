# InstantNBT

High-performance Minecraft **NBT Runtime** (Memory / Ownership / Sync) for Fabric, Forge, and NeoForge.

InstantNBT optimizes NBT copy, save, and sync paths to reduce GC pressure and save-time hitching. It targets heavy-NBT workloads — not raw render FPS.

Maintained by MisoPy.

## What it helps / what it does not

| Good fit | Poor fit |
|---|---|
| World save / chunk I/O | Empty-world render FPS |
| Large compound / inventory copies | Entity rendering or AI itself |
| GC hitch / save-spike reduction | “Install and FPS jumps forever” expectations |

Use Spark (`/spark profiler start --timeout 30 --alloc`) and inspect NBT / save-related frames when measuring.

## Requirements

- Minecraft **1.16.5 ~ 26.1.2** (Fabric / Forge / NeoForge — see matrix)
- **Architectury API** (all loaders)
- **Fabric API** on Fabric only

| Loader | Primary versions |
|---|---|
| Fabric | 1.16.5 … 26.1.2 |
| Forge | 1.17.1 … 1.20.1 |
| NeoForge | 1.20.4 … 26.1.2 |

- Tier A baseline: **1.20.1 Forge**
- Forge 1.16.5 is not supported by the current LegacyForge toolchain
- Design notes: [InstantNBT_ProjectPlan.md](InstantNBT_ProjectPlan.md)

## Install

1. Drop the loader-matching jar into `mods`
2. First launch creates `config/instantnbt-common.toml`
3. Default mode is **BALANCED** (`nbtIoRedirect`, CoW, pool, and related safe defaults)

Restart after major config changes. If an older heavy config remains, delete the toml and let it regenerate.

## Diagnostics (OP)

```text
/instantnbt cow
/instantnbt benchmark
/instantnbt killswitch engage|reset
```

## Local development

```bash
./gradlew :1.20.1-forge:compileJava
./gradlew :1.21.1-fabric:build
./gradlew runActiveClient
```

Active StoneCutter version is stored in `.sc_active_version` (default: `1.20.1-forge`).

## License

MIT — see [LICENSE](LICENSE).
