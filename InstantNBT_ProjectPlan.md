# InstantNBT Runtime — Project Plan v2.5

**バージョン**: v2.5  
**作成日**: 2026-08-02  
**プロジェクト定義**: Minecraft NBT Runtime の再設計  
**メインターゲット**: Forge 1.20.1（基準実装）  
**対応方針**: Architectury API + StoneCutter による Forge / Fabric / NeoForge 横断  
**対応範囲**: 1.16.5 〜 26.1.2

### 改訂履歴

| バージョン | 日付 | 変更概要 |
|---|---|---|
| v2.0 | 2026-08-02 | Runtime 統合設計として全章再構成 |
| v2.1 | 2026-08-02 | マルチバージョン構築戦略・Assumptions/Risks・用語集を追加、Kill Switch とロック順序規約を補強 |
| v2.2 | 2026-08-02 | ローカル検証（compileOnly + 単一runClient）とCIの役割分担、gh/CurseMaven連携による配布半自動化方針を追加 |
| v2.3 | 2026-08-02 | 3.5節を修正: ローカル検証方式を「compileOnly」から「全Tier compileJava + Tier A単一runClient」へ訂正 |
| v2.4 | 2026-08-02 | CIをGitHub Actionsと明記し全ビルドの責務と規定、付録AにPhase 0（リポジトリ早期作成）を追加 |
| v2.5 | 2026-08-02 | 複製元プロジェクト由来の残存物クリーンアップ方針と、CurseMavenアップロード順（forge→neoforge→fabric、各ローダー内は古い順）を追加 |

---

## 0. この文書のゴール

この文書は「最適化案の集合」ではなく、**実装可能な NBT Runtime 設計仕様**です。  
各章は「責務」「状態遷移」「失敗時挙動」「テスト要件」まで定義し、読むだけで実装タスクに分解できる状態を目標とします。

---

## 1. プロジェクト概要

InstantNBT v2.0 は、Minecraft NBT を対象にした高性能・高互換の実行基盤 **InstantNBT Runtime** を構築します。  
v1.x の FastTagCompound / ObjectPool / CoW / DeltaSync を個別最適化としてではなく、単一ランタイムの機能層として統合します。

### 成果物定義

- NBT メモリ管理ランタイム（OwnedTag + Memory Manager）
- 高速シリアライザ（バージョン適応 + 互換フォールバック）
- 同期ランタイム（Delta / Snapshot / DirectPass）
- 互換性レイヤ（Feature Registry + Capability Negotiation）
- 開発者向け診断基盤（Command + Overlay + Export）

### 最終目標

**「Minecraft で最も設計が明確で、運用しやすい最適化ランタイム Mod」** を実現する。

### 1.1 対応バージョン方針（Tier 制）

1.16.5〜26.1.2 は Java（8→21）、Mojang Mapping、Mixin 挿入点、ローダー API のいずれも大きく変化するため、全バージョン等価保証はせず Tier で扱いを分ける。

| Tier | 対象例 | 保証レベル |
|---|---|---|
| Tier A（基準） | 1.20.1 Forge | 全機能・全 Benchmark・CI 必須 |
| Tier B（主要現行） | 最新 Forge/Fabric/NeoForge | 主要機能 + Tier1 互換検証 |
| Tier C（旧バージョン, 例: 1.16.5） | Java8 制約下の Fabric/Forge | Runtime Core のみ、危険最適化は default OFF |
| Tier D（将来/実験, 例: 26.1.2 系） | 未確定 API | best-effort、破壊的変更時は即時 degraded 化 |

- Tier C/D では `mode = "safe"` を強制 default とし、`aggressive` は明示 opt-in のみ許可する
- Java 言語機能は最小共通項（Tier C の Java バージョン）に合わせ、上位 Tier 専用最適化は version-specific ソースへ隔離する

---

## 2. 設計思想

### 2.1 設計原則

1. **Runtime First**  
   最適化手法より先に Runtime の責務境界を固定する。
2. **Ownership First**  
   すべての NBT 状態は OwnedTag メタデータで説明できるようにする。
3. **Compatibility by Design**  
   互換性は「後追い修正」ではなく Feature Registry により初期設計へ内包する。
4. **Safe-by-Default**  
   デフォルト構成は保守的・高互換、攻めた最適化は明示 opt-in。
5. **Observability Mandatory**  
   すべての主要機能は診断コマンドとメトリクス出力を持つ。

### 2.2 非目標

- NBT フォーマット自体の互換破壊
- 他 Mod のデータ構造を強制置換する API 侵襲
- JVM 実装依存の危険最適化をデフォルト有効化

---

## 3. アーキテクチャ

### 3.1 全体構造

```text
InstantNBT Runtime
        │
 ┌──────┴───────────────┐
 │                      │
Runtime Core         Compatibility Layer
 │                      │
 ├─ Memory              ├─ Feature Registry
 ├─ Serializer          ├─ Capability Check
 ├─ Network             ├─ Compatibility Database
 ├─ Lifecycle           ├─ Fallback Planner
 ├─ Thread Model        └─ Diagnostics Bridge
 └─ Profiler
        │
 Developer Runtime (CLI / Overlay / Export / Benchmark)
```

### 3.2 レイヤ責務

- **Runtime Core**: NBT オブジェクトの生成・共有・同期・破棄の一貫管理
- **Compatibility Layer**: Mod ごとの差異吸収と機能制限の自動判断
- **Developer Runtime**: 可視化、再現、比較、回帰検出

### 3.3 モジュール依存ルール

- 上位層は下位層のみ参照可（逆参照禁止）
- Compatibility Layer は Runtime Core の public API のみ使用
- Diagnostics は read-only 優先（副作用 API を明示的に分離）

### 3.4 マルチバージョン構築戦略（Architectury + StoneCutter）

対応範囲（1.16.5〜26.1.2 / Forge・Fabric・NeoForge）を実装可能にするソースコード構成をここで固定する。

```text
src/
  main/                    # loader/version 非依存 Runtime Core（最大共通部）
  architectury/            # Architectury API 経由の共通プラットフォーム抽象
  forge/ fabric/ neoforge/ # ローダー別 Entrypoint・Mixin 登録
  versioned/1.16.5/        # StoneCutter overlay（旧 API 差分のみ）
  versioned/1.20.1/        # 基準実装（Tier A）
  versioned/26.1.2/        # 実験 overlay（Tier D）
```

- **共通部優先**: Runtime Core（Memory/Ownership/Serializer 抽象）は `main` に置き、version overlay は差分のみ持つ
- **StoneCutter コメントスキーマ**: `//? if >=1.20.1 {` のようなバージョン境界コメントを Mixin/Entrypoint に限定し、Core ロジックへの混入を禁止
- **Mixin 挿入点管理**: 挿入点シグネチャは version overlay ごとに個別ファイル化し、Tier A で挿入点が壊れた場合は該当 Tier のみ `DEGRADED_MINIMAL` に落とす（他 Tier へ伝播させない）
- **CI ビルドマトリクス**: 一括ビルド（全 Tier × 全ローダーの build+test）は常に **GitHub Actions** で実施し、ローカルには要求しない。Tier A/B は全 PR で実行、Tier C/D は nightly のみ（前述 17.5 の Tier 検証運用と対応させる）
- **依存 API 差分吸収**: ローダー固有 API は Architectury Common API を第一候補とし、非対応時のみ platform-specific expect/actual 相当のブリッジで吸収する

### 3.5 ローカル検証と CI の役割分担

全 Tier・全ローダーのクライアントをローカルで起動させる方針は取らず、開発者体験と CI の網羅性を分離する。

- 全 Tier・全ローダーに対しては **`compileJava`**（StoneCutter overlay ごとのコンパイルタスク）のみを実行し、構文・API 差分の破壊がないかを軽量に確認する
- 実機能・実行時挙動の検証は **基準実装（Tier A / 1.20.1）の単一 `runClient`** のみを標準とし、Tier B〜D では起動確認まで求めない
- Tier B〜D を含む全組み合わせの実行時テスト・パッケージング・一括ビルドは **GitHub Actions（CI）側の責務**とし、ローカルには要求しない（3.4 の CI ビルドマトリクスに従う）
- この分担により、クロスローダー対応でも開発者のローカル反復速度は「単一 compileJava 群 + Tier A runClient」相当に抑える

### 3.6 リポジトリ運用・配布の半自動化方針

- リポジトリ初期設定（作成・ブランチ保護・ラベル等）は `gh` CLI を用いた半自動化を目標とし、20章のブランチ戦略と整合させる。この初期設定は付録A Phase 0 として他フェーズより先に着手する
- Compatibility Database（13.4）対象 Mod（AE2/Create 等）への `compileOnly` 依存は **CurseMaven** 経由で解決し、依存バージョンと互換ルールのバージョン条件を同期管理する
- Tier×ローダー×バージョンの組み合わせで増える配布アーティファクトのアップロードは、**GitHub Actions** 上の公開ワークフローによる半自動化を目標とし、手動アップロードは例外時のみに限定する
- 導入順序: (1) `gh` によるリポジトリ運用整備（早期実施） → (2) CurseMaven 依存解決の定着 → (3) GitHub Actions からの一括アップロード半自動化、の段階導入とする
- 本プロジェクトは既存プロジェクト複製を起点とするため、旧名・旧設定・不要ファイル・古いタスク定義などの残存物が混在し得る。Phase 0〜1 で棚卸しし、必要に応じて **置換または削除** して整合性を確保する

### 3.7 CurseMaven アップロード順序規約

- アップロード順は **forge -> neoforge -> fabric** を固定し、各ローダー内は **古いバージョンから順に** 実施する
- 実運用上は forge から開始するため、公開ページ上の見た目（新しい投稿が上に来る表示）と作業順が逆に見える場合があるが、作業基準は本規約を優先する

```text
forge:
  1.17.1 -> 1.18.2 -> 1.20.1

neoforge:
  1.20.4 -> 1.21.1 -> 26.1.2

fabric:
  1.16.5 -> 1.18.2 -> 1.21.8
```

---

## 4. Runtime

### 4.1 Runtime コンポーネント

1. **Memory Runtime**: OwnedTag の割り当て・追跡・解放
2. **Serializer Runtime**: encode/decode とバージョン適応
3. **Network Runtime**: Delta/Batch/Snapshot 転送戦略
4. **Lifecycle Runtime**: init/tick/shutdown 時の整合性保証
5. **Thread Runtime**: スレッド境界を跨ぐ所有権移譲
6. **Profiler Runtime**: 低コスト計測とイベント集約

### 4.2 Runtime ライフサイクル

```text
BOOTSTRAP
  -> CAPABILITY_SCAN
  -> RUNTIME_INIT
  -> WARMUP
  -> RUNNING
  -> DEGRADED (互換問題検出時)
  -> SHUTDOWN
```

### 4.3 Runtime 起動順

1. Platform bootstrap
2. Config load + validation
3. Feature Registry 初期化
4. Memory Manager 起動
5. Serializer / Network 起動
6. Diagnostics command 登録
7. Warmup benchmark（任意）

### 4.4 障害時モード

- `DEGRADED_SAFE`: 高リスク機能のみ停止
- `DEGRADED_COMPAT`: 特定 Mod 向けフォールバックへ切替
- `DEGRADED_MINIMAL`: Runtime API は維持、最適化はほぼ無効

---

## 5. Memory Manager

### 5.1 階層設計

```text
Memory Manager
  └─ Allocator
      └─ Pool
          └─ Arena
              ├─ Ref Counter (Lightweight, default)
              ├─ Reference Tracker (Advanced, optional)
              └─ Cache
                  └─ Garbage Monitor
```

### 5.2 各コンポーネント責務

- **Allocator**: サイズクラスごとの割り当てポリシー選択
- **Pool**: Tag 型別の再利用戦略（Compound/List/Primitive）
- **Arena**: Tick 単位・スレッド単位の局所割り当て領域
- **Ref Counter**: 参照増減の軽量カウント（標準経路）
- **Reference Tracker**: 詳細参照追跡（必要時のみ有効化）
- **Cache**: 読み取りホットデータの短期固定
- **Garbage Monitor**: 圧迫度監視と回収イベント発火

### 5.3 割り当てポリシー

- `SMALL (<=256B)`: thread-local arena 優先
- `MEDIUM (<=4KB)`: shared pool + generation tracking
- `LARGE (>4KB)`: direct alloc + monitor 対象

### 5.4 メモリ圧迫時の動作

1. Cache eviction（LRU + generation 優先）
2. Pool shrink
3. Arena compaction
4. SharedTag release request
5. 強制 snapshot 無効化（必要時）

### 5.5 テスト要件

- メモリリーク検知（10k tick 継続試験）
- 断片化率測定（alloc/free パターン負荷）
- 圧迫時フォールバック時間（P95 5ms 未満目標）

### 5.6 Lightweight First 方針

- 初期実装は Ref Counter を標準とし、DAG 追跡は必須機能にしない
- Reference Tracker は debug / benchmark で必要性が確認された場合のみ有効化
- 「計測で得を確認できた機能だけ残す」をメモリ設計の原則とする

---

## 6. Ownership Model

### 6.1 中核概念: OwnedTag

`OwnedTag` は全 NBT に常時付与せず、必要時のみメタデータを持つ **Lazy Ownership** を採用する。

```java
public final class OwnedTag {
    Tag payload;
    OwnedMeta meta; // null の間は通常 NBT と同等コスト
}

public final class OwnedMeta {
    Owner owner;
    int refCount;
    OwnershipState state;
    boolean immutable;
    long generation;
    boolean dirty;
}
```

### 6.2 メタデータ定義

- **Owner**: 現在の主所有者（ThreadDomain + ModuleDomain）
- **Reference Count**: 生存参照数（acquire/release で更新）
- **State**: `UNIQUE | SHARED | FROZEN | DETACHED`
- **Immutable**: 書込禁止フラグ（freeze/snapshot で true）
- **Generation**: 書込世代（変更ごとにインクリメント）
- **Dirty Flag**: 同期対象判定用の変更フラグ
- **非必須項目**: DAG/詳細参照グラフは初期実装で要求しない

### 6.3 状態遷移

```text
UNIQUE --share--> SHARED
SHARED --write--> UNIQUE (CoW split)
UNIQUE --freeze--> FROZEN
FROZEN --copy--> UNIQUE
* --release(ref=0)--> DETACHED
```

### 6.4 Ownership 契約

- 書込前に `ensureWritable()` を必須化
- スレッド越境時は `acquire(owner)` を必須化
- `FROZEN` 状態は API でのみ解除可（直接変更禁止）

### 6.5 Ownership が説明する機能

- CoW: `SHARED -> write -> split`
- SharedTag: `share()` による参照共有
- Snapshot: `freeze() + generation pin`
- Delta Sync: `dirty + generation diff`

### 6.6 Lazy Ownership 適用条件

- `share`, `snapshot`, `freeze`, `acquire` のいずれか呼び出し時に `OwnedMeta` を遅延生成
- 小型・短命 NBT は通常経路を維持し、余計なメタデータ確保を避ける
- 目標比率は「通常 NBT 95%+、Ownership 適用 NBT 5%-」を初期目安とする

### 6.7 Reference Count 最適化

- `Atomic` の都度更新は避け、thread-local バッファに加算差分を蓄積
- tick 終端または handoff 境界で差分を統合する
- 厳密同期が必要な経路のみ強制フラッシュを行う

### 6.8 段階的実装ルール

- Phase 1: `state + generation + refCount + dirty` の最小セット
- Phase 2: 必要性が確認された機能のみ追加（例: tracker 強化）
- 追加条件は「JMH/JFR で有意差あり」を必須とする

---

## 7. Copy-on-Write

### 7.1 CoW の設計目標

- 読み取りパスでコピー発生ゼロを維持
- 書き込み時に最小粒度で分岐
- 互換性優先の安全分岐（unsafe path は opt-in）

### 7.2 分岐アルゴリズム

1. `ensureWritable(ownedTag)`
2. `state == SHARED || immutable == true` なら split
3. split 時に子 Tag の shallow/deep 戦略を選択
4. 新インスタンスへ owner 移譲
5. `generation++`, `dirty = true`

### 7.3 分岐戦略

- **Shallow First**: 小変更時に高速
- **Adaptive Deep**: ネスト深度が閾値超過時に深いコピーへ移行

### 7.4 CoW 無効化条件

- 互換 DB で競合判定済み Mod が存在
- デバッグモード `ownership.strict = false`
- Serializer が legacy path 強制

---

## 8. Shared Tag

### 8.1 SharedTag の目的

同一内容の NBT を複数箇所で読み取り中心に使う場面で、重複メモリと不要コピーを削減する。

### 8.2 共有ポリシー

- `InstantNBT.share(tag)` で共有化
- 共有対象は `immutable=true` を推奨
- `refCount` 0 到達で自動解放候補

### 8.3 共有ハッシュ

- 構造ハッシュ + サイズ + generation をキー化
- 衝突時は内容比較で検証
- arena 跨ぎ共有は tracker 有効時のみ登録（通常は軽量カウンタ運用）

### 8.4 失敗時挙動

- ハッシュ衝突過多で共有率低下 -> 共有機能を段階的に抑制
- 競合 Mod 検出時 -> SharedTag を read-only cache のみに縮退

---

## 9. Object Pool

### 9.1 対象

- `CompoundTag`
- `ListTag`
- `ByteArrayTag` / `IntArrayTag` / `LongArrayTag`

### 9.2 プール設計

- 型別プール + サイズクラス別キュー
- warmup prefill（設定可能）
- 高水位線越えで自動 shrink

### 9.3 Arena 連携

- arena 終了時に返却バッチ化
- 同一 tick での再利用率を優先
- cross-thread 返却は lock-free queue 経由

### 9.4 KPI

- Pool hit rate 85%+
- alloc stall P99 < 0.2ms
- GC 圧縮イベントの発生回数 30% 以上減少

---

## 10. Serializer

### 10.1 目的

- NBT encode/decode のスループット最大化
- Mod/Version 差異を含む互換性維持
- Runtime 状態（Ownership/Generation）と整合

### 10.2 レイヤ構造

```text
SerializerFacade
  ├─ Codec Router
  │   ├─ Fast Binary Codec
  │   ├─ SNBT Fast Parser
  │   └─ Legacy Compatible Codec
  ├─ Version Adapter
  └─ Validation Guard
```

### 10.3 エンコード方針

- default: Fast Binary Codec
- `compat.requireLegacyCodec=true` 時は Legacy へ固定
- 大型 Tag は chunk encode を選択可能

### 10.4 デコード方針

- lazy decode は default OFF
- unsafe path は JVM 条件を満たす場合のみ候補化
- decode 後に OwnedTag を付与（metadata 初期化）

### 10.5 エラー制御

- decode 失敗時は legacy 再試行（1回）
- 連続失敗時は runtime degraded へ遷移
- diagnostics へ raw error を構造化出力

---

## 11. Network

### 11.1 同期モード

1. **Full Sync**: 完全送信（初回・安全重視）
2. **Delta Sync**: generation 差分送信
3. **Snapshot Sync**: 参照固定スナップショット送信
4. **Direct Pass**: Integrated Server 内ローカル受け渡し

### 11.2 Delta Sync 要件

- `dirty=true` かつ generation 差分ありで対象化
- 変更連鎖は generation/dirty を基本に集約し、必要時のみ tracker を併用
- batch window 内で複数変更を圧縮

### 11.3 転送パイプライン

```text
Collect Dirty -> Build Delta -> Encode -> Transport -> Decode -> Apply
```

### 11.4 失敗時挙動

- delta apply 失敗 -> full sync へロールバック
- パケット順序不整合 -> generation ベースで reject + resync
- 負荷過多時 -> batch サイズを自動縮小

---

## 12. Integrated Server Runtime

### 12.1 基本方針

シングルプレイは「同一 JVM 上の Client/Server 共存」として扱い、両側導入と同等の Runtime を適用する。

### 12.2 スレッドモデル

- Main Thread
- Render Thread
- Integrated Server Thread
- IO Thread
- Worker Thread

### 12.3 スレッド境界ルール

- 境界越えデータは `acquire/release` を必須化
- mutable 共有は禁止、Shared/Frozen を優先
- direct pass は ownership lock を取得して適用
- Integrated Server では「mutable 共有参照」を禁止し Snapshot handoff を既定にする
- **ロック順序規約**: 複数ロックを取得する経路は常に `Arena -> OwnedMeta -> SharedTag registry` の順で固定し、逆順取得を検出した場合は diagnostics に deadlock-risk として記録する
- ロック保持時間は「メタデータ更新のみ」に限定し、I/O・シリアライズ処理をロック内で実行しない

### 12.4 Direct Pass 最適化

- Netty 層をバイパスし **immutable snapshot handle** を直接受け渡し
- server 側で `freeze(snapshot)` 済みデータのみ direct pass 対象
- serializer は省略可能だが、検証失敗時は即時 fallback
- generation mismatch 時は自動 full sync

### 12.5 テスト要件

- Integrated 判定の正確性
- スレッド競合（race）再現試験
- DirectPass vs NetworkPass の整合比較
- Snapshot handoff 後に client 側で write 不可であることの確認

---

## 13. Compatibility Layer

### 13.1 フロー設計

```text
Feature Registry
  -> Capability Check
      -> Compatibility Database
          -> Fallback Planner
              -> Diagnostics
```

### 13.2 Feature Registry

- 各最適化を feature flag として一元管理
- 依存関係（requires/conflicts）を宣言可能
- Runtime 起動時に可用 feature を確定

### 13.3 Capability Check

- ロード済み Mod 一覧から能力を抽出
- Mixin 競合、API 差分、既知バグを判定
- side（server/client/integrated）を含めて評価

### 13.4 Compatibility Database

- AE2 / Create / Curios / EMI など主要 Mod のプロファイル保持
- バージョン条件付きルールを許容
- 更新しやすい JSON/YAML 定義で外部化
- 未知 Mod は「未対応」として扱い、既定で safe fallback へ遷移

### 13.5 Fallback 戦略

- 機能単位停止（module level）
- API 経路切替（fast -> legacy）
- 同期モード切替（delta -> full）
- 未知 Mod 検出時は `compat-unknown-safe` プロファイルを自動適用

### 13.6 Diagnostics 出力

- 何が無効化されたか
- どのルールが発火したか
- 推奨設定（ユーザー向け）

### 13.7 検証スコープ方針（Tier 制）

- **Tier 1（必須）**: 主要 Mod（例: AE2 / Create / Curios）を継続検証
- **Tier 2（推奨）**: カテゴリ代表 Mod をスポット検証
- **Tier 3（非網羅）**: 未知 Mod は総当たり検証せず safe fallback で吸収
- リリース判定は Tier 1 + 回帰なしを基準とし、Tier 2/3 は後追い更新

---

## 14. API

### 14.1 公開 API 方針

- Runtime 契約を守る最小かつ十分な API
- `CompoundTag` 直接改変ではなく `OwnedTag` 管理を通す
- 非同期用途を見据えた acquire/release 契約を提供
- API は任意利用（optional）で、既存 Mod へ導入を強制しない
- API 未使用でも Runtime は従来経路（legacy/safe fallback）で動作する
- API 変更は SemVer と非破壊移行（deprecated -> remove）を厳守

### 14.2 コア API

```java
InstantNBT.share(tag);
InstantNBT.snapshot(tag);
InstantNBT.freeze(tag);
InstantNBT.copy(tag);
InstantNBT.isShared(tag);
InstantNBT.pin(tag);
InstantNBT.unpin(tag);
InstantNBT.acquire(tag);
InstantNBT.release(tag);
```

### 14.3 API 契約

- `freeze` 後の write は例外または CoW split
- `pin` 中の強制解放は禁止
- `acquire/release` 不整合は diagnostics warning

### 14.4 拡張 API（将来）

- `InstantNBT.diff(oldTag, newTag)`
- `InstantNBT.merge(base, delta)`
- `InstantNBT.trace(tag)`（ownership 経路追跡）

### 14.5 互換性に関する注意書き

- Core API の公開自体は互換性破壊要因ではない
- 互換性破壊の主因は「API必須化」と「既存 `CompoundTag` 意味変更」であり、本 Runtime はどちらも行わない
- 既存 Mod は API 非依存のまま動作し、問題時は自動的に safe fallback へ縮退する

---

## 15. Config

### 15.1 設定原則

- セーフ設定を default
- 危険設定は分離 namespace 化
- 変更時に再起動要否を明示

### 15.2 主要カテゴリ

```toml
[runtime]
enabled = true
mode = "balanced" # safe|balanced|aggressive

[memory]
allocator = "adaptive"
arenaEnabled = true
poolEnabled = true
sharedTagEnabled = true
gcMonitorEnabled = true

[ownership]
strict = true
enforceAcquireRelease = true
autoFreezeSnapshot = true

[serializer]
fastCodec = true
legacyFallback = true
lazyDeserialize = false
unsafeIO = false

[network]
deltaSync = true
snapshotSync = true
packetBatching = true
integratedDirectPass = true

[compat]
autoDetectMods = true
compatDatabase = "default"
forceLegacyForUnknown = true

[diagnostics]
commandEnabled = true
overlayEnabled = true
exportJson = true

[safety]
killSwitch = false          # true 時は全最適化を即時無効化し vanilla 相当経路のみ稼働
killSwitchPersistDisable = false # true 時は次回起動以降も無効化を維持
```

### 15.3 プリセット

- `safe`: 高互換優先
- `balanced`: 推奨 default
- `aggressive`: 高性能優先（互換注意）

---

## 16. Developer Tooling

### 16.1 診断コマンド

- `/instantnbt memory`
- `/instantnbt pool`
- `/instantnbt ownership`
- `/instantnbt profiler`
- `/instantnbt compat`
- `/instantnbt benchmark`

### 16.2 各コマンド出力仕様

- **memory**: 使用量、arena 数、圧迫度、回収統計
- **pool**: hit/miss、サイズクラス別在庫、shrink 履歴
- **ownership**: state 分布、acquire/release 不整合数
- **profiler**: module 別時間、P50/P95/P99
- **compat**: 無効化 feature と理由
- **benchmark**: 現在値と baseline 差分

### 16.3 Overlay / Export

- F3 拡張パネルで Runtime 状態を表示
- JSON / CSV エクスポート
- issue 添付用の anonymized dump を生成

---

## 17. Benchmark

### 17.1 ベンチマーク体系

1. Micro: 単体関数性能（codec, alloc, diff）
2. Meso: システム連携（ownership + serializer + network）
3. Macro: 実環境シナリオ（modpack + integrated server）

### 17.2 基準シナリオ

- 1000+ エンティティ同時更新
- 大量 ItemStack NBT 比較
- SNBT 大容量ロード
- シングルプレイ高速更新ループ

### 17.3 指標

- Throughput ops/s
- Latency P50/P95/P99
- Allocation rate
- GC pause
- Network payload size
- FPS / TPS 変動

### 17.4 回帰判定

- PR 単位で baseline 比較
- 閾値超過時は CI failed
- レポートを Markdown + JSON で保存

### 17.5 互換検証運用

- 全 Mod 総当たり検証は実施しない
- CI の標準互換検証は Tier 1 に限定
- Tier 2/3 は issue ベースで追加検証し、必要時に Compatibility Database を更新

### 17.6 実装可能性の検証方針

- JMH で機能別コスト（CPU/alloc/latency）を定量化する
- JFR で GC 圧力と pause 起点を継続監視する
- 「最適化前より悪化」が検出された機能は default OFF または削除候補に降格する

---

## 18. Security

### 18.1 脅威モデル

- 不正 NBT payload による decode 負荷増大
- 異常差分パケットによる同期破壊
- 共有オブジェクト悪用によるメモリ保持攻撃

### 18.2 対策

- Decode guard（サイズ・深度・再帰上限）
- Generation 検証付き delta apply
- pin/unpin 異常検知
- 高リスク機能 default OFF

### 18.3 セキュリティ運用

- 既知問題の advisory 管理
- 重大 issue 用 hotfix ブランチ
- 監査ログの匿名化出力

### 18.4 ワールドデータ保護（Kill Switch）

- `safety.killSwitch` により、コマンド一つで全最適化を無効化し vanilla 相当の読み書き経路へ即時縮退できる
- decode/encode 時の異常（サイズ・深度異常、generation 不整合の多発）を検知した場合、Runtime は自動的に `killSwitch` 相当の状態へ遷移し、ワールドファイルへの書き込みは常に vanilla 互換フォーマットを維持する
- Kill Switch 発動はユーザー可視ログ + diagnostics export に必ず記録する

---

## 19. Future Plans

### 19.1 v2.x 拡張候補

- 差分圧縮アルゴリズムの適応選択
- Off-heap arena 実験実装
- DataFixerUpper 連携最適化
- API 安定化（semver 厳格化）

### 19.2 v3.0 構想

- Runtime plugin architecture
- Mod 別最適化プロファイル自動生成
- Telemetry 駆動の自己調整（opt-in）

---

## 20. Contribution Guide

### 20.1 開発ルール

- 仕様変更は必ず design note を添付
- runtime core 変更は benchmark 必須
- compat 変更は対象 Mod 再現手順を添付

### 20.2 ブランチ戦略

```text
main              : リリース
dev               : 統合
feature/runtime-* : Runtime 機能
feature/compat-*  : 互換機能
fix/*             : バグ修正
```

### 20.3 PR 要件

- 最低 1 つの性能指標を提示
- 互換影響の有無を明記
- diagnostics 出力例を添付

### 20.4 レビュー観点

- Ownership 契約を破っていないか
- fallback が安全側に倒れているか
- 計測可能性（observability）を維持しているか
- Mixin 介入点が最小化され、代替経路が用意されているか

---

## 21. Runtime Lifecycle（Object Lifecycle）

### 21.1 NBT オブジェクトライフサイクル

```text
Create
  -> Pool Acquire
  -> Plain Tag (default)
  -> Owned Promotion (lazy)
  -> Shared / Snapshot
  -> Network / Serializer Handoff
  -> Release
  -> Pool Return or GC
```

### 21.2 状態ごとの不変条件

- Plain Tag: 追加メタデータを持たない通常経路
- Owned Promotion: meta 生成は 1 回のみ
- Snapshot: immutable 保証、write は split または拒否
- Release: refCount 0 で返却候補、pin 中は返却禁止

### 21.3 失敗時の扱い

- lifecycle 不整合検出時は `DEGRADED_SAFE` へ遷移
- ownership 異常は diagnostics に構造化記録
- 必要時は plain/legacy 経路へロールバック

---

## 22. Performance Budget

### 22.1 予算設計の原則

- すべての最適化は「期待利益 > 実装オーバーヘッド」を満たす必要がある
- 予算超過機能は default OFF、または削除候補とする
- 予算は JMH/JFR 実測でのみ更新する

### 22.2 初期ターゲット（1.20.1 baseline）

- Pool acquire/release: 平均 `<= 120ns`
- Ownership promotion（lazy）: 平均 `<= 220ns`
- refCount update（batched）: 平均 `<= 40ns`
- CoW split（small tag）: 平均 `<= 900ns`
- Snapshot handoff metadata: 平均 `<= 180ns`
- Delta encode（small diff）: 平均 `<= 1.8us`

### 22.3 監視指標

- alloc rate、retained heap、GC pause、P95/P99 latency
- feature 別 on/off 比較（最低 30 サンプル）
- Integrated Server と Dedicated Server を分離測定

---

## 23. 前提・リスク（Assumptions & Risks）

### 23.1 前提条件

- Architectury API / StoneCutter は本プロジェクトの対応バージョン範囲を通じて継続保守される
- Mixin は挿入対象クラスの obfuscation mapping が Tier A/B で安定している
- ベンチマーク基準環境（JMH/JFR）は CI 上で再現可能な負荷プロファイルを用意できる

### 23.2 リスク register

| リスク | 影響 | 対応方針 |
|---|---|---|
| 旧バージョン(Tier C)での Mixin 挿入点破壊 | Tier C のみ機能停止 | version overlay を分離済みのため他 Tier へ非伝播、`DEGRADED_MINIMAL` へ縮退 |
| 未知 Mod による NBT 構造改変との競合 | データ破損・クラッシュ | `compat-unknown-safe` 既定適用 + Kill Switch（18.4） |
| Lazy Ownership のメタデータ確保が想定より高頻度化 | メモリ予算超過 | 22章 Performance Budget を CI 回帰指標化し超過時は該当機能を default OFF |
| Architectury/StoneCutter のメジャー更新による overlay 破壊 | ビルド不能 | Tier A/B の nightly ビルドで早期検知、pin されたバージョンからの計画的追従 |
| Tier D（将来バージョン）API の未確定変更 | 実装前提の崩壊 | best-effort 明示、リリース判定基準（付録B）から除外 |

### 23.3 非確定事項（要決定）

- Off-heap arena（19.1）の対象範囲とセキュリティ境界
- Telemetry 駆動自己調整（19.2）の opt-in データ収集ポリシー
- Tier D 対応バージョンの正式サポート開始基準

---

## 付録A: 実装フェーズ（提案）

### Phase 0: リポジトリ初期化（最優先・着手前提）

- `gh` によるリポジトリ作成、ブランチ保護、ラベル等の初期設定を他の全フェーズより先に実施する
- GitHub Actions ワークフローの骨組み（Tier A/B の compileJava + runClient 検証、Tier C/D nightly ビルドの雛形）をこの段階で先行配置する
- 3.6 の導入順序（gh → CurseMaven → 一括アップロード自動化）における最初のステップとして位置づける
- 複製元由来の残存物（旧パッケージ名、不要リソース、旧CI設定、旧リリーススクリプト）を点検し、必要に応じて置換/削除してから Runtime 実装へ進む

### Phase 1: Project Plan v2.0 骨格

- 章立て確定
- 設計思想確定
- アーキテクチャと責務境界固定

### Phase 2: 核心実装仕様

- Memory Manager
- Ownership Model
- Runtime Lifecycle / Thread Model

### Phase 3: データ経路実装仕様

- Network Runtime
- Serializer Runtime
- Integrated Server Runtime

### Phase 4: 外部接続仕様

- Compatibility Layer
- Public API
- Config / Preset

### Phase 5: 開発運用仕様

- Developer Tooling
- Benchmark / CI
- 公開仕様 / 文書整備

---

## 付録B: 完了定義（Definition of Done）

- 各章に責務・状態遷移・失敗時挙動・テスト要件がある
- Runtime Core の依存方向が文書化どおりである
- diagnostics コマンドが主要機能を可視化できる
- benchmark 回帰検知が CI に組み込まれている
- Tier 1 互換検証が通過し、未知 Mod 向け safe fallback が動作する
- Runtime Lifecycle の不変条件テストが通過している
- Performance Budget の主要項目（Pool/Ownership/refCount）が予算内である

---

## 付録C: 用語集

| 用語 | 定義 |
|---|---|
| OwnedTag | 所有権メタデータ（`OwnedMeta`）を遅延付与できる NBT ラッパー（6.1） |
| Lazy Ownership | 通常 NBT は無コストのまま、共有・凍結等が必要な時のみメタデータを生成する方針（6.6） |
| CoW（Copy-on-Write） | `SHARED` 状態への書込時に最小粒度で分岐コピーする機構（7章） |
| SharedTag | 内容一致 NBT を複数箇所から参照させ重複を削減する仕組み（8章） |
| Arena | Tick/スレッド単位の局所メモリ割り当て領域（5.1） |
| Generation | Tag の書込世代カウンタ。Delta Sync・整合性検証に使用（6.2, 11.2） |
| Direct Pass | Integrated Server 内で Netty をバイパスして immutable snapshot を受け渡す経路（12.4） |
| Kill Switch | 全最適化を即時無効化し vanilla 相当経路へ縮退させる安全機構（18.4） |
| Tier（バージョン Tier） | 対応 MC バージョンを保証レベルで分類する制度（1.1） |
| Fallback Planner | Compatibility Layer が機能停止・経路切替を決定する構成要素（13.1） |

---

*InstantNBT Runtime Project Plan v2.5 — 2026-08-02*
