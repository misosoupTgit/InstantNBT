# InstantNBT

Minecraft 向け **NBT Runtime** 最適化 Mod（Fabric / Forge / NeoForge）。

InstantNBTは、NBTのコピー・保存・同期まわりを効率化し、GCやセーブ時の負荷を抑える最適化Modです。描画FPSそのものより、重いNBTを扱う環境でのカクつき軽減を狙います。

Maintained by MisoPy.

## できること / できないこと

| 向いている | 向いていない |
|---|---|
| ワールドセーブ・チャンク I/O | 空ワールドの描画 FPS 向上 |
| 大きい Compound／Inventory の copy | エンティティ描画・AI 自体の高速化 |
| GC ヒッチやセーブ時のスパイク軽減 | 「置くだけで常時 FPS が跳ねる」用途 |

計測は Spark（`/spark profiler start --timeout 30 --alloc`）などで、NBT／save 系を見るのが妥当です。

## 対応環境

- Minecraft **1.16.5 ~ 26.1.2**（Fabric / Forge / NeoForge）
- **Architectury API**（全ローダー）
- **Fabric API**（Fabric のみ）

| Loader | 主な対象 |
|---|---|
| Fabric | 1.16.5 … 26.1.2 |
| Forge | 1.17.1 … 1.20.1 |
| NeoForge | 1.20.4 … 26.1.2 |

- Tier A 基準: **1.20.1 Forge**
- Forge 1.16.5 は現行 LegacyForge ツールチェーン非対応
- 詳細設計: [InstantNBT_ProjectPlan.md](InstantNBT_ProjectPlan.md)

## 導入

1. ローダーに合わせた jar を `mods` へ入れる
2. 初回起動で `config/instantnbt-common.toml` が生成される
3. 既定は **BALANCED**（`nbtIoRedirect` / CoW / pool など安全寄り）

設定を大きく変えたあとは再起動を推奨します。古い重い設定が残っている場合は toml を削除して再生成してください。

## 診断コマンド（OP）

```text
/instantnbt cow          # CoW / NbtIo / codec カウンタ
/instantnbt stress 30    # 放置用 NBT ストレステスト（InstantNBT 導入時のみ）
/instantnbt stress stop
/instantnbt benchmark
/instantnbt killswitch engage|reset
```

## 開発

```bash
./gradlew :1.20.1-forge:compileJava
./gradlew :1.21.1-fabric:build
./gradlew runActiveClient
```

Active StoneCutter バージョンは `.sc_active_version`（既定: `1.20.1-forge`）。

## License

MIT — see [LICENSE](LICENSE).
