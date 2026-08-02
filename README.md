# InstantNBT

High-performance Minecraft **NBT Runtime** (Memory / Ownership / Sync) for Fabric, Forge, and NeoForge.

Maintained by MisoPy.

## Status

Phase 0 scaffold: multi-loader / multi-version build matrix via Architectury + StoneCutter. Runtime Core lands in later phases — see [InstantNBT_ProjectPlan.md](InstantNBT_ProjectPlan.md).

## Requirements

- Minecraft **1.16.5 ~ 26.1.2** (Fabric / Forge / NeoForge — see matrix)
- **Architectury API** (all loaders)
- **Fabric API** on Fabric only

### Version matrix (compile targets)

| Loader | Primary versions |
|---|---|
| Fabric | 1.16.5 … 26.1.2 |
| Forge | 1.17.1 … 1.20.1 |
| NeoForge | 1.20.4 … 26.1.2 |

Notes:

- Tier A baseline: **1.20.1 Forge**
- Forge 1.16.5 is not supported by the current LegacyForge toolchain
- NeoForge covers 1.20.2+ (via 1.20.4+ targets + additional versions)

## Local development

```bash
./gradlew :1.20.1-forge:compileJava
./gradlew runActiveClient
```

Active StoneCutter version is stored in `.sc_active_version` (default: `1.20.1-forge`).

## License

MIT — see [LICENSE](LICENSE).
