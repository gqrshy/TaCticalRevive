# PlayerRevive Mod 開発者向け解析ドキュメント

> **目的**: このドキュメントはPlayerRevive modの内部構造を解析し、TACZとの互換性問題を解決するための新しいmodを開発する際の参考資料として作成されました。

## 目次

1. [概要](#1-概要)
2. [プロジェクト構成](#2-プロジェクト構成)
3. [コアアーキテクチャ](#3-コアアーキテクチャ)
4. [Bleeding（出血）メカニズム](#4-bleeding出血メカニズム)
5. [死亡イベントのインターセプト](#5-死亡イベントのインターセプト)
6. [Revival（蘇生）メカニズム](#6-revival蘇生メカニズム)
7. [Mixin詳細](#7-mixin詳細)
8. [ネットワークパケット](#8-ネットワークパケット)
9. [設定システム](#9-設定システム)
10. [公開API](#10-公開api)
11. [TACZ互換性に関する注目ポイント](#11-tacz互換性に関する注目ポイント)

---

## 1. 概要

### 基本情報

| 項目 | 値 |
|------|-----|
| Mod ID | `playerrevive` |
| バージョン | 2.0.37 |
| ローダー | NeoForge 21.1+ |
| Minecraft | 1.21.1 |
| 依存関係 | CreativeCore v2.13.0+ |
| Java | 21 |

### 機能概要

PlayerReviveは、プレイヤーが致死ダメージを受けた際に即死せず「出血状態（Bleeding）」に入り、他のプレイヤーに蘇生してもらえるシステムを提供するmodです。

**状態遷移図:**
```
生存 → [致死ダメージ] → 出血状態 → [蘇生完了] → 生存
                            ↓
                     [時間切れ/諦め] → 死亡
```

---

## 2. プロジェクト構成

```
src/main/java/team/creative/playerrevive/
├── PlayerRevive.java              # メインクラス、mod初期化
├── PlayerReviveConfig.java        # 設定システム
├── api/
│   ├── IBleeding.java             # 出血状態インターフェース
│   ├── PlayerExtender.java        # オーバーキルダメージ追跡
│   ├── CombatTrackerClone.java    # 戦闘履歴の保存
│   └── event/
│       ├── PlayerBleedOutEvent.java   # 出血死イベント
│       └── PlayerRevivedEvent.java    # 蘇生完了イベント
├── cap/
│   └── Bleeding.java              # IBleeding実装クラス
├── mixin/
│   ├── PlayerMixin.java           # Playerクラスへの注入
│   ├── ServerPlayerMixin.java     # ServerPlayerへの拡張
│   ├── CombatTrackerAccessor.java # 戦闘トラッカーアクセサ
│   ├── MinecraftAccessor.java     # クライアント用アクセサ
│   ├── LocalPlayerAccessor.java   # ローカルプレイヤーアクセサ
│   └── GameRendererAccessor.java  # レンダラーアクセサ
├── packet/
│   ├── ReviveUpdatePacket.java    # 出血状態同期
│   ├── HelperPacket.java          # ヘルパー状態通知
│   └── GiveUpPacket.java          # 諦めリクエスト
├── server/
│   ├── PlayerReviveServer.java    # サーバーAPI・ユーティリティ
│   └── ReviveEventServer.java     # サーバーイベントハンドラ
└── client/
    ├── ReviveEventClient.java     # クライアントUI・エフェクト
    └── TensionSound.java          # 緊張感サウンド
```

---

## 3. コアアーキテクチャ

### データフロー

```
プレイヤー死亡イベント
        ↓
PlayerMixin/ServerPlayerMixin（死亡をインターセプト）
        ↓
ReviveEventServer（イベントをキャンセル、出血状態開始）
        ↓
IBleeding/Bleeding（状態管理）
        ↓
PlayerReviveServer（公開API）
        ↓
Network Packets（クライアントへ同期）
        ↓
ReviveEventClient（UI/サウンド/シェーダー）
```

### 主要クラスの責務

| クラス | 責務 |
|--------|------|
| `PlayerRevive` | Mod初期化、イベントバス登録、ネットワーク設定 |
| `Bleeding` | 出血状態の完全な管理（時間、進捗、ヘルパー） |
| `PlayerReviveServer` | 静的APIメソッド群（状態クエリ、操作） |
| `ReviveEventServer` | 全サーバーイベントハンドリング |
| `ReviveEventClient` | UI描画、シェーダー、サウンド管理 |

---

## 4. Bleeding（出血）メカニズム

### IBleeding インターフェース

```java
public interface IBleeding {
    // 状態クエリ
    boolean isBleeding();          // 出血中かどうか
    boolean bledOut();             // 時間切れかどうか
    float getProgress();           // 蘇生進捗（0〜100）
    boolean revived();             // 蘇生完了条件を満たしたか
    int timeLeft();                // 残り時間（tick）
    int downedTime();              // ダウンしてからの経過時間

    // 状態遷移
    void knockOut(Player player, DamageSource source);  // 出血開始
    void revive();                                       // 蘇生完了
    void forceBledOut();                                 // 強制死亡

    // 毎tick処理
    void tick(Player player);

    // ヘルパー管理
    List<Player> revivingPlayers();

    // 戦闘トラッカー
    CombatTrackerClone getTrackerClone();
    DamageSource getSource(RegistryAccess access);
}
```

### Bleeding 実装クラスの状態フィールド

```java
public class Bleeding implements IBleeding {
    private boolean bleeding;           // 出血中フラグ
    private float progress;             // 蘇生進捗
    private int timeLeft;               // 残り時間（tick）
    private int downedTime;             // ダウン経過時間
    private DamageSource lastSource;    // 致死ダメージソース
    private CombatTrackerClone trackerClone;  // 戦闘履歴クローン
    private boolean itemConsumed;       // 蘇生アイテム消費済み
    private List<Player> revivingPlayers;     // 蘇生中のプレイヤー
}
```

### tick() メソッドの処理内容

毎tick（1/20秒）で以下を実行:

1. **ポーズ設定**: `player.setForcedPose(Pose.SWIMMING)` - 這いつくばりアニメーション
2. **ヘルパー検証**: 距離チェック（デフォルト3ブロック）、範囲外は除外
3. **時間減少**: `timeLeft--`（haltBleedTimeがfalseまたはヘルパーなしの場合）
4. **進捗加算**: `progress += helpers.size() * progressPerPlayer`
5. **空腹適用**: ヘルパーにexhaustion、本人にhunger設定
6. **エフェクト適用**: Slowness II、Glowing（設定による）
7. **状態チェック**: `revived()` または `bledOut()` 判定

### 出血開始処理 (knockOut)

```java
public void knockOut(Player player, DamageSource source) {
    bleeding = true;
    progress = 0;
    timeLeft = CONFIG.bleeding.bleedTime;  // デフォルト1200tick = 60秒
    downedTime = 0;
    lastSource = source;
    trackerClone = new CombatTrackerClone(player.getCombatTracker());
    itemConsumed = false;
    revivingPlayers.clear();
}
```

---

## 5. 死亡イベントのインターセプト

### イベントハンドラの優先度

```java
@SubscribeEvent(priority = EventPriority.HIGHEST)
public void playerDied(LivingDeathEvent event)
```

**HIGHEST優先度**を使用し、他のmodより先に処理を行う。

### 死亡インターセプトのフロー

```
1. ガード条件チェック:
   - サーバーサイドのみ
   - Playerインスタンスである
   - isReviveActive() = true
     - クリエイティブでない（または triggerForCreative=true）
     - マルチプレイヤー（または bleedInSingleplayer=true）

2. バイパスチェック:
   - doesByPass(): ダメージソースがバイパスリストにあるか
   - doesByPassDamageAmount(): オーバーキルダメージ閾値を超えたか

3. 全条件をパスした場合:
   - event.setCanceled(true)  ← バニラの死亡をキャンセル
   - PlayerReviveServer.startBleeding(player, source)
```

### バイパスシステム

特定条件で蘇生をスキップし即死させる:

```java
// ダメージソースによるバイパス
bypassDamageSources: [
    "gorgon",
    "death.attack.sgcraft:transient",
    "death.attack.sgcraft:iris",
    "vampirism_dbno",
    "hordes:infection"
]

// ダメージ量によるバイパス
enableBypassDamage: true
bypassDamage: 1000.0  // この量を超えるオーバーキルで即死
```

### オーバーキルダメージの追跡

```java
// ServerPlayerMixin で実装
@Unique
private float overkill;

// LivingDamageEvent.Pre で計算
overkill = Math.max(0, incomingDamage - currentHealth);
```

---

## 6. Revival（蘇生）メカニズム

### 蘇生インタラクション

```java
@SubscribeEvent(priority = EventPriority.HIGH)
public void playerInteract(PlayerInteractEvent.EntityInteract event)
```

**処理フロー:**

1. 右クリック対象が出血中プレイヤーか確認
2. 蘇生アイテム要件チェック（設定による）
3. ヘルパーを`revivingPlayers`リストに追加
4. `HelperPacket`でクライアントに通知

### 蘇生進捗の計算

```java
// 毎tick
progress += revivingPlayers.size() * CONFIG.revive.progressPerPlayer;

// デフォルト設定
requiredReviveProgress = 100.0f
progressPerPlayer = 1.0f

// 蘇生完了条件
boolean revived() {
    return progress >= CONFIG.revive.requiredReviveProgress;
}
```

**蘇生時間の計算:**
- 1人: 100tick = 5秒
- 2人: 50tick = 2.5秒
- 3人: 約33tick = 1.7秒

### 蘇生完了処理

```java
public static void revive(Player player) {
    IBleeding bleeding = getBleeding(player);
    bleeding.revive();  // 状態リセット

    // エフェクト適用
    for (MobEffectConfig effect : CONFIG.revive.revivedEffects) {
        effect.apply(player);
    }

    // 体力設定（デフォルト: 2 = ハート1個）
    player.setHealth(CONFIG.revive.healthAfter);

    // サウンド再生
    CONFIG.sounds.revived.play(player, SoundSource.PLAYERS);

    // イベント発火
    NeoForge.EVENT_BUS.post(new PlayerRevivedEvent(player, bleeding));

    // ポーズリセット
    player.setForcedPose(null);

    // パケット送信
    sendUpdatePacket(player);
}
```

---

## 7. Mixin詳細

### PlayerMixin.java

**対象クラス:** `net.minecraft.world.entity.player.Player`

```java
@Mixin(Player.class)
public abstract class PlayerMixin {

    // モブから見えなくする
    @Inject(method = "canBeSeenAsEnemy", at = @At("HEAD"), cancellable = true)
    private void canBeSeenAsEnemy(CallbackInfoReturnable<Boolean> info) {
        if (isBleeding && (withinCooldown || disableMobDamage)) {
            info.setReturnValue(false);
        }
    }

    // 押し出されなくする
    @Override
    public boolean isPushable() {
        if (isBleeding && !CONFIG.bleeding.canBePushed) {
            return false;
        }
        return super.isPushable();
    }
}
```

### ServerPlayerMixin.java

**対象クラス:** `net.minecraft.server.level.ServerPlayer`

```java
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements PlayerExtender {

    @Unique
    private float overkill;  // オーバーキルダメージ追跡

    // 権限レベル変更
    @Inject(method = "getPermissionLevel", at = @At("HEAD"), cancellable = true)
    private void getPermissionLevel(CallbackInfoReturnable<Integer> info) {
        if (isBleeding && CONFIG.bleeding.changePermissionLevel) {
            info.setReturnValue(CONFIG.bleeding.permissionLevel);
        }
    }

    // PlayerExtender実装
    @Override
    public float getOverkill() { return overkill; }

    @Override
    public void setOverkill(float amount) { overkill = amount; }
}
```

### CombatTrackerAccessor.java

**目的:** 死亡メッセージに正しいキラーを表示するため

```java
@Mixin(CombatTracker.class)
public interface CombatTrackerAccessor {
    @Accessor List<CombatEntry> getEntries();
    @Accessor int getLastDamageTime();
    @Accessor int getCombatStartTime();
    @Accessor void setCombatStartTime(int time);
    @Accessor int getCombatEndTime();
    @Accessor void setCombatEndTime(int time);
    @Accessor boolean getInCombat();
    @Accessor void setInCombat(boolean inCombat);
    @Accessor boolean getTakingDamage();
    @Accessor void setTakingDamage(boolean takingDamage);
}
```

---

## 8. ネットワークパケット

### ReviveUpdatePacket

**方向:** サーバー → クライアント

**内容:**
```java
UUID uuid;        // 対象プレイヤー
CompoundTag nbt;  // シリアライズされたBleeding状態
```

**送信タイミング:**
- 出血開始時
- 5tickごとの状態更新
- 蘇生/死亡時

### HelperPacket

**方向:** サーバー → クライアント（ヘルパーへ）

**内容:**
```java
@Nullable UUID helping;  // 助けている対象（nullで終了）
boolean start;           // 開始/終了フラグ
```

### GiveUpPacket

**方向:** クライアント → サーバー

**内容:** なし（信号パケット）

**トリガー:** 攻撃キーを5秒間長押し

---

## 9. 設定システム

### 主要設定項目

#### 出血関連 (CONFIG.bleeding)

| 設定 | デフォルト | 説明 |
|------|-----------|------|
| `bleedTime` | 1200 | 出血時間（tick）= 60秒 |
| `bleedingHealth` | 10 | 出血中の体力 |
| `initialDamageCooldown` | 10 | 初期ダメージ無効時間（tick） |
| `disableMobDamage` | false | モブダメージ無効化 |
| `disablePlayerDamage` | false | プレイヤーダメージ無効化 |
| `shouldGlow` | false | 発光エフェクト |
| `bleedingEffects` | [Slowness II] | 適用エフェクトリスト |

#### 蘇生関連 (CONFIG.revive)

| 設定 | デフォルト | 説明 |
|------|-----------|------|
| `requiredReviveProgress` | 100.0 | 必要蘇生進捗 |
| `progressPerPlayer` | 1.0 | 1人あたりの進捗/tick |
| `maxDistance` | 3.0 | 最大蘇生距離（ブロック） |
| `healthAfter` | 2 | 蘇生後体力 |
| `haltBleedTime` | false | 蘇生中は時間停止 |
| `reviveItem` | Paper | 必要アイテム |
| `needReviveItem` | false | アイテム要件有効化 |

---

## 10. 公開API

### PlayerReviveServer 静的メソッド

```java
// 状態クエリ
public static boolean isBleeding(Player player)
public static int timeLeft(Player player)
public static int downedTime(Player player)
public static IBleeding getBleeding(Player player)

// 操作
public static void startBleeding(Player player, DamageSource source)
public static void revive(Player player)
public static void kill(Player player)

// ヘルパー管理
public static void removePlayerAsHelper(Player player)

// 同期
public static void sendUpdatePacket(Player player)
```

### イベントAPI

```java
// 出血死前（キャンセル可能）
PlayerBleedOutEvent extends PlayerEvent {
    Player getPlayer();
    IBleeding getBleeding();
    void setCanceled(boolean cancel);
}

// 蘇生完了時
PlayerRevivedEvent extends PlayerEvent {
    Player getPlayer();
    IBleeding getBleeding();
}
```

### エンティティセレクタ

```
@e[bleeding=true]   # 出血中の全プレイヤー
@e[bleeding=false]  # 出血していない全プレイヤー
```

---

## 11. TACZ互換性に関する注目ポイント

### 潜在的な問題領域

#### 1. ダメージ処理の競合

PlayerReviveは`LivingDeathEvent`を**HIGHEST優先度**でインターセプトします。TACZの銃ダメージが:
- カスタムダメージソースを使用している場合
- 独自の死亡処理を持っている場合
- ダメージ計算にMixinを使用している場合

これらで競合が発生する可能性があります。

#### 2. オーバーキルダメージ追跡

```java
// ServerPlayerMixin.overkillフィールド
// LivingDamageEvent.Preで設定される
```

TACZの高ダメージ武器がオーバーキル閾値を超えると、蘇生がバイパスされる可能性があります。

#### 3. ダメージソースの識別

```java
// バイパスチェックで使用
source.getMsgId()
source.typeHolder().unwrapKey().get().location()
```

TACZのダメージソース名を確認し、必要に応じてバイパスリストから除外する必要があります。

#### 4. プレイヤー状態の干渉

出血中のプレイヤーは:
- `Pose.SWIMMING`が強制される
- `isPushable() = false`
- `canBeSeenAsEnemy() = false`（一時的）
- インベントリアクセスが制限される可能性

これらがTACZの銃の挙動に影響を与える可能性があります。

#### 5. イベント優先度

| PlayerRevive | 優先度 |
|--------------|--------|
| LivingDeathEvent | HIGHEST |
| LivingIncomingDamageEvent | default |
| LivingDamageEvent.Pre | LOWEST |
| PlayerInteractEvent | HIGH |

TACZのイベントハンドラとの優先度競合を確認する必要があります。

### 調査すべきTACZのコード

1. **ダメージソース定義**: TACZがどのようなDamageSourceを使用しているか
2. **死亡処理**: 独自の死亡イベントハンドラがあるか
3. **Mixin**: Playerクラスへの変更があるか
4. **武器ダメージ計算**: ダメージ値とオーバーキルの関係
5. **エンティティインタラクション**: 銃を持った状態での右クリック処理

### 推奨される解決アプローチ

1. **互換性レイヤーの作成**: 両modの間に立つブリッジmod
2. **イベントフックの追加**: TACZダメージをPlayerReviveが認識できるようにする
3. **設定の拡張**: TACZ武器用のカスタムバイパス/非バイパス設定
4. **ダメージソースマッピング**: TACZのダメージソースを適切に処理

---

## 付録: 重要な定数・リソース

### Mod定数

```java
public static final String MODID = "playerrevive";
public static final ResourceLocation BLEEDING_NAME =
    ResourceLocation.tryBuild(MODID, "bleeding");
public static final ResourceKey<DamageType> BLED_TO_DEATH =
    ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.tryBuild(MODID, "bled_to_death"));
```

### リソースファイル

- `data/playerrevive/damage_type/bled_to_death.json` - カスタムダメージタイプ
- `assets/playerrevive/sounds.json` - サウンド定義
- `assets/playerrevive/shaders/post/blobs2.json` - ブラーシェーダー

---

*このドキュメントはPlayerRevive v2.0.37 (NeoForge 1.21.1)を基に作成されました。*
