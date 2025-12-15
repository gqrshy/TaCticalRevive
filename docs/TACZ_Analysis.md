# TACZ-Refabricated Mod 開発者向け解析ドキュメント

> **目的**: このドキュメントはTACZ-Refabricated modの内部構造を解析し、PlayerReviveとの互換性問題を解決するための新しいmodを開発する際の参考資料として作成されました。

## 目次

1. [概要](#1-概要)
2. [プロジェクト構成](#2-プロジェクト構成)
3. [コアアーキテクチャ](#3-コアアーキテクチャ)
4. [銃システム](#4-銃システム)
5. [ダメージシステム](#5-ダメージシステム)
6. [弾丸エンティティ](#6-弾丸エンティティ)
7. [イベントシステム](#7-イベントシステム)
8. [Mixin詳細](#8-mixin詳細)
9. [ネットワークパケット](#9-ネットワークパケット)
10. [設定システム](#10-設定システム)
11. [PlayerRevive互換性の重要ポイント](#11-playerrevive互換性の重要ポイント)

---

## 1. 概要

### 基本情報

| 項目 | 値 |
|------|-----|
| Mod ID | `tacz` |
| ローダー | Fabric |
| Minecraft | 1.20.1 |
| Java | 17 |
| 主要依存 | Fabric API, Cardinal Components API, Forge Config API Port |

### 機能概要

TACZ-Refabricatedは、リアルな銃火器システムをMinecraftに追加するmodです。弾丸物理、リロードシステム、照準、アタッチメントなど、詳細な銃のメカニクスを実装しています。

### 名前空間

```
com.tacz.guns/     - 主要mod機能
cn.sh1rocu.tacz/   - APIとコアインフラ
```

---

## 2. プロジェクト構成

```
src/main/java/
├── cn/sh1rocu/tacz/
│   ├── TaCZFabric.java           # メインエントリーポイント
│   ├── api/                      # 公開API
│   │   ├── item/IGun.java        # 銃アイテムインターフェース
│   │   ├── entity/               # エンティティAPI
│   │   └── event/                # イベントAPI
│   ├── client/                   # クライアント実装
│   ├── compat/                   # 他mod互換性
│   ├── config/                   # 設定システム
│   ├── mixin/                    # Mixin（Fabric固有）
│   └── network/                  # ネットワーク
│
└── com/tacz/guns/
    ├── GunMod.java               # コアmod設定
    ├── entity/                   # エンティティ定義
    │   ├── EntityKineticBullet.java  # 弾丸エンティティ
    │   └── shooter/              # 射撃ロジック
    ├── event/                    # イベントハンドラ
    ├── init/                     # レジストリ
    │   └── ModDamageTypes.java   # ダメージタイプ定義
    ├── item/                     # アイテム定義
    ├── mixin/                    # Mixin（銃固有）
    └── network/                  # パケット定義
```

---

## 3. コアアーキテクチャ

### 初期化フロー

```
TaCZFabric.onInitialize()
    ↓
PreLoadConfig.init()          # 早期設定読み込み
    ↓
GunMod.setup()                # ブロック、アイテム、エンティティ登録
    ↓
CommandRegistry              # コマンド登録
    ↓
CompatRegistry               # 互換性設定
    ↓
CommonRegistry.onSetupEvent() # イベント・ネットワーク設定
    ↓
subscribeEvents()            # 全イベントリスナー登録
```

### コアインターフェース

#### IGun（銃アイテムインターフェース）

```java
public interface IGun {
    // 発射モード
    FireMode getFireMode(ItemStack gun);

    // 弾薬
    int getCurrentAmmoCount(ItemStack gun);
    void setCurrentAmmoCount(ItemStack gun, int count);

    // ズーム
    float getAimingZoom(ItemStack gun);

    // ダミー弾薬システム
    boolean useDummyAmmo(ItemStack gun);
}
```

#### IGunOperator（銃操作インターフェース）

**重要**: LivingEntityへのMixinで実装され、全てのLivingEntityが銃を使用可能。

```java
public interface IGunOperator {
    // 状態取得
    ShooterDataHolder getDataHolder();

    // 射撃
    ShootResult shoot();
    void cancelShoot();

    // リロード
    void reload();
    void cancelReload();

    // 照準
    void aim(boolean aiming);
    float getAimingProgress();

    // その他
    void draw(Supplier<ItemStack> gun);
    void bolt();
    void melee();
    void crawl(boolean crawling);
    void fireSelect();

    // 初期化
    void initialData();
}
```

---

## 4. 銃システム

### ShooterDataHolder（射撃状態保持）

```java
public class ShooterDataHolder {
    // 現在の銃
    Supplier<ItemStack> currentGunItem;

    // リロード状態
    ReloadState.StateType reloadStateType;

    // タイミング
    long shootTimestamp;
    long lastShootTimestamp;
    long drawCoolDown;
    long shootCoolDown;

    // 照準
    float aimingProgress;  // 0.0 〜 1.0
    boolean isAiming;

    // その他状態
    boolean isCrawling;
    boolean isBolting;
    float sprintTimeS;

    // アタッチメント
    AttachmentCacheProperty cacheProperty;

    // カウンター
    int shootCount;  // トレーサー弾用

    // ノックバック
    double knockbackStrength;
}
```

### リロード状態

```java
public enum StateType {
    NOT_RELOADING,              // リロード中でない
    EMPTY_RELOAD_FEEDING,       // 空マガジンリロード（装填中）
    EMPTY_RELOAD_FINISHING,     // 空マガジンリロード（完了処理）
    TACTICAL_RELOAD_FEEDING,    // タクティカルリロード（装填中）
    TACTICAL_RELOAD_FINISHING   // タクティカルリロード（完了処理）
}
```

### 射撃結果

```java
public enum ShootResult {
    SUCCESS,           // 成功
    UNKNOWN_FAIL,      // 不明な失敗
    COOL_DOWN,         // クールダウン中
    NO_AMMO,           // 弾薬なし
    NOT_DRAW,          // 銃が構えられていない
    NOT_GUN,           // 銃でない
    ID_NOT_EXIST,      // IDが存在しない
    NEED_BOLT,         // ボルトアクション必要
    IS_RELOADING,      // リロード中
    IS_DRAWING,        // 銃を構え中
    IS_BOLTING,        // ボルト操作中
    IS_MELEE,          // 近接攻撃中
    IS_SPRINTING,      // スプリント中
    NETWORK_FAIL,      // ネットワークエラー
    FORGE_EVENT_CANCEL,// イベントキャンセル
    OVERHEATED         // オーバーヒート
}
```

### 射撃処理フロー

```
1. クライアント: ClientMessagePlayerShoot送信
        ↓
2. サーバー: LivingEntityShoot.shoot()
        ↓
3. 検証チェック:
   - クールダウン確認
   - リロード状態確認
   - 弾薬確認
   - 構え状態確認
   - ボルト状態確認
   - スプリント状態確認
        ↓
4. GunShootEvent発火（キャンセル可能）
        ↓
5. AbstractGunItem.shoot()実行
        ↓
6. EntityKineticBullet生成・発射
        ↓
7. 追跡プレイヤーへ同期
```

---

## 5. ダメージシステム

### カスタムダメージタイプ

**定義場所:** `ModDamageTypes.java`

```java
public class ModDamageTypes {
    // ダメージタイプキー
    public static final ResourceKey<DamageType> BULLET;
    public static final ResourceKey<DamageType> BULLET_IGNORE_ARMOR;
    public static final ResourceKey<DamageType> BULLET_VOID;
    public static final ResourceKey<DamageType> BULLET_VOID_IGNORE_ARMOR;

    // タグ（全弾丸ダメージ）
    public static final TagKey<DamageType> BULLETS_TAG;

    // ダメージソース生成
    public static class Sources {
        public static DamageSource bullet(
            RegistryAccess access,
            Entity directCause,    // 弾丸エンティティ
            Entity shooter,        // 射撃者
            boolean ignoreArmor    // 防具無視
        );

        public static DamageSource bulletVoid(
            RegistryAccess access,
            Entity directCause,
            Entity shooter,
            boolean ignoreArmor
        );
    }
}
```

### ダメージ計算フロー

```
1. 基本ダメージ取得（弾薬データから）
        ↓
2. 距離減衰適用（DistanceDamagePair）
        ↓
3. ヘッドショット判定・倍率適用
        ↓
4. 防具貫通率計算（0〜1）
        ↓
5. ダメージ分割:
   - 通常ダメージ = damage × (1 - armorIgnore)
   - 貫通ダメージ = damage × armorIgnore
        ↓
6. EntityHurtByGunEvent.Pre発火
        ↓
7. 2回のhurt()呼び出し:
   - entity.hurt(normalSource, normalDamage)
   - entity.hurt(armorPiercingSource, piercingDamage)
        ↓
8. EntityHurtByGunEvent.Post または EntityKillByGunEvent発火
```

### 防具貫通システム

```java
// EntityKineticBullet.tacAttackEntity() より
float armorDamagePercent = Mth.clamp(armorIgnore, 0.0F, 1.0F);
float normalDamagePercent = 1 - armorDamagePercent;

// 通常ダメージ（防具で軽減される）
entity.hurt(normalDamageSource, damage * normalDamagePercent);

// 貫通ダメージ（防具を無視）
entity.hurt(armorPiercingSource, damage * armorDamagePercent);
```

**重要**: 1発の弾丸で**2回**のダメージ適用が行われる。

### 無敵時間のリセット

```java
// EntityKineticBullet.java 内
targetEntity.invulnerableTime = 0;  // 無敵時間をリセット
```

これにより連続ヒットが可能になる。

---

## 6. 弾丸エンティティ

### EntityKineticBullet

**ファイル:** `entity/EntityKineticBullet.java`（766行）

#### プロパティ

```java
// 識別
ResourceLocation ammoId;   // 弾薬ID
ResourceLocation gunId;    // 銃ID

// 物理
int life;           // 寿命（tick）、デフォルト200
float gravity;      // 重力加速度
float friction;     // 空気抵抗

// ダメージ
LinkedList<DistanceDamagePair> damage;  // 距離ベースダメージ
int pierce;         // 貫通回数（1〜MAX）
float knockback;    // ノックバック強度
float headShot;     // ヘッドショット倍率
float armorIgnore;  // 防具貫通率（0〜1）

// 特殊効果
boolean isTracerAmmo;     // トレーサー弾
boolean explosion;        // 爆発
boolean igniteEntity;     // エンティティ着火
boolean igniteBlock;      // ブロック着火
```

#### Tickサイクル

```
毎tick処理:
1. クライアント: パーティクル・視覚エフェクト
2. サーバー:
   a. 爆発カウントダウン確認
   b. ブロック衝突検出（レイキャスト）
   c. エンティティ衝突検出（貫通対応）
   d. ヒット処理（ブロックまたはエンティティ）
   e. 重力・摩擦適用
   f. 寿命カウントダウン
```

#### ヒット検出

```java
// ブロック衝突
BlockRayTrace.rayTraceBlocks(level, startPos, endPos, ...);

// エンティティ衝突
EntityUtil.findEntityOnPath(level, startPos, endPos, ...);

// 複数エンティティ（貫通時）
EntityUtil.findEntitiesOnPath(level, startPos, endPos, ...);
// → 距離でソートして順番に処理
```

#### onHitEntity処理

```java
void onHitEntity(Entity target, Vec3 hitLocation, boolean isHeadShot) {
    // 1. ターゲットエンティティチェック（訓練用標的など）
    if (target instanceof ITargetEntity) {
        // 特殊処理
        return;
    }

    // 2. ダメージソース作成
    DamageSource normalSource = ModDamageTypes.Sources.bullet(..., false);
    DamageSource armorPiercingSource = ModDamageTypes.Sources.bullet(..., true);

    // 3. EntityHurtByGunEvent.Pre発火（キャンセル可能）
    EntityHurtByGunEvent.Pre preEvent = new EntityHurtByGunEvent.Pre(...);
    if (preEvent.isCanceled()) {
        return;  // ダメージ適用しない
    }

    // 4. 着火エフェクト
    if (igniteEntity) {
        target.setRemainingFireTicks(100);
    }

    // 5. ヘッドショット倍率適用
    float finalDamage = isHeadShot ? damage * headShotMultiplier : damage;

    // 6. カスタムノックバック設定
    KnockBackModifier.setKnockBackStrength(target, knockback);

    // 7. ダメージ適用（2回）
    tacAttackEntity(target, normalSource, armorPiercingSource, finalDamage);

    // 8. ノックバックリセット
    KnockBackModifier.resetKnockBackStrength(target);

    // 9. 爆発処理
    if (explosion) {
        explode();
    }

    // 10. イベント発火
    if (target.isDeadOrDying()) {
        // EntityKillByGunEvent発火
    } else {
        // EntityHurtByGunEvent.Post発火
    }

    // 11. ネットワーク同期
    sendHitPacket();
}
```

---

## 7. イベントシステム

### 銃関連イベント（API）

| イベント | キャンセル可 | 発火タイミング |
|----------|-------------|----------------|
| `GunShootEvent` | ✅ | トリガー引き時 |
| `GunFireEvent` | ✅ | 実際の発射時（バースト各発） |
| `GunDrawEvent` | ❌ | 銃切り替え時 |
| `GunMeleeEvent` | ✅ | 銃剣攻撃時 |
| `GunReloadEvent` | ❌ | リロード開始時 |
| `GunFinishReloadEvent` | ❌ | リロード完了時 |
| `GunFireSelectEvent` | ❌ | 発射モード変更時 |
| `EntityHurtByGunEvent.Pre` | ✅ | **ダメージ適用前** |
| `EntityHurtByGunEvent.Post` | ❌ | ダメージ後（生存時） |
| `EntityKillByGunEvent` | ❌ | **銃で死亡時** |
| `AmmoHitBlockEvent` | ✅ | 弾丸がブロックに命中時 |

### EntityHurtByGunEvent.Pre（最重要）

```java
public class EntityHurtByGunEvent {
    public static class Pre extends EntityHurtByGunEvent implements Cancellable {
        // 取得可能データ
        Entity getHurtEntity();        // ダメージを受けるエンティティ
        float getDamage();             // ダメージ量
        DamageSource getDamageSource();// ダメージソース
        boolean isHeadShot();          // ヘッドショットか
        float getHeadShotMultiplier(); // ヘッドショット倍率
        ResourceLocation getGunId();   // 銃ID
        ResourceLocation getAmmoId();  // 弾薬ID
        @Nullable Entity getAttacker();// 攻撃者（射撃者）

        // 変更可能
        void setDamage(float damage);
        void setHeadShotMultiplier(float multiplier);

        // キャンセル
        void setCanceled(boolean canceled);
        boolean isCanceled();
    }
}
```

### EntityKillByGunEvent

```java
public class EntityKillByGunEvent extends EntityHurtByGunEvent {
    // 取得可能データ（Preと同様）
    Entity getHurtEntity();    // 死亡したエンティティ
    float getDamage();         // 致死ダメージ量
    boolean isHeadShot();      // ヘッドショットか
    // ...

    // キャンセル不可
}
```

### イベント優先度

```java
public class EventPriority {
    HIGHEST = "tacz:event_highest_priority"
    HIGH    = "tacz:event_high_priority"
    LOW     = "tacz:event_low_priority"     // デフォルト
    LOWEST  = "tacz:event_lowest_priority"
}
```

### 登録済みイベントハンドラ

```java
// TaCZFabric.subscribeEvents() より

// ダメージ処理
LivingHurtEvent.CALLBACK → EntityDamageEvent::onLivingHurt
  // BULLET_RESISTANCE属性によるダメージ軽減

// ノックバック変更
LivingKnockBackEvent.CALLBACK → KnockbackChange::onKnockback
  // カスタムノックバック強度適用

// ブロック操作防止
AttackBlockCallback.EVENT → PreventGunClick::onLeftClickBlock
  // 銃を持っている時のブロック左クリック防止

// 特殊弾薬効果
AmmoHitBlockEvent.CALLBACK → BellRing::onAmmoHitBlock
  // ベルを鳴らす
AmmoHitBlockEvent.CALLBACK → DestroyGlassBlock::onAmmoHitBlock
  // ガラスを破壊

// プレイヤーイベント
ServerPlayerEvents.AFTER_RESPAWN → PlayerRespawnEvent::onPlayerRespawn
  // リスポーン後の初期化
ServerPlayerEvents.COPY_FROM → SyncedEntityDataEvent::onPlayerClone
  // データコピー
```

---

## 8. Mixin詳細

### 主要Mixin一覧

#### LivingEntityMixin（最重要）

**パッケージ:** `com.tacz.guns.mixin.common`

```java
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements IGunOperator {

    // ShooterDataHolder注入
    @Unique
    private ShooterDataHolder shooterDataHolder;

    // 全ての射撃関連ロジックを注入:
    // - LivingEntityShoot（射撃）
    // - LivingEntityDrawGun（銃切り替え）
    // - LivingEntityAim（照準）
    // - LivingEntityReload（リロード）
    // - LivingEntityBolt（ボルトアクション）
    // - LivingEntityMelee（近接攻撃）
    // - LivingEntityCrawl（匍匐）
    // - LivingEntityFireSelect（発射モード）
    // - LivingEntityAmmoCheck（弾薬確認）
    // - LivingEntitySpeedModifier（移動速度）
    // - LivingEntitySprint（スプリント）
    // - LivingEntityHeat（武器温度）
}
```

#### ServerPlayerMixin

**目的:** プレイヤーリスポーン時のデータ初期化

```java
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void initialGunOperateData(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
        IGunOperator.fromLivingEntity(this).initialData();
    }
}
```

#### ExplosionMixin

**目的:** カスタム爆発処理

#### ItemStackMixin

**目的:** NBTデータ操作

### Mixinファイル構成

```
tacz.mixins.json           # 共通・オプションMixin
tacz.fabric.mixins.json    # Fabric固有Mixin
```

---

## 9. ネットワークパケット

### クライアント→サーバー（C2S）

| パケット | 目的 |
|----------|------|
| `ClientMessagePlayerShoot` | 射撃実行 |
| `ClientMessagePlayerReloadGun` | リロード開始 |
| `ClientMessagePlayerCancelReload` | リロードキャンセル |
| `ClientMessagePlayerFireSelect` | 発射モード変更 |
| `ClientMessagePlayerAim` | 照準トグル |
| `ClientMessagePlayerCrawl` | 匍匐/しゃがみ |
| `ClientMessagePlayerDrawGun` | 銃切り替え |
| `ClientMessagePlayerBoltGun` | ボルト操作 |
| `ClientMessagePlayerMelee` | 近接攻撃 |
| `ClientMessagePlayerZoom` | スコープズーム |

### サーバー→クライアント（S2C）

| パケット | 目的 |
|----------|------|
| `ServerMessageSound` | サウンド再生 |
| `ServerMessageGunShoot` | 射撃アニメーション |
| `ServerMessageGunFire` | 発射エフェクト |
| `ServerMessageGunDraw` | 銃切り替えアニメ |
| `ServerMessageGunMelee` | 近接攻撃アニメ |
| `ServerMessageGunReload` | リロードアニメ |
| `ServerMessageGunHurt` | **ヒットエフェクト** |
| `ServerMessageGunKill` | **キルエフェクト** |
| `ServerMessageUpdateEntityData` | エンティティ同期 |

---

## 10. 設定システム

### 設定ファイル種別

| 種別 | クラス | 説明 |
|------|--------|------|
| Common | `CommonConfig` | クライアント・サーバー共通 |
| Server | `ServerConfig` | サーバーのみ |
| Client | `ClientConfig` | クライアントのみ |

### 主要設定項目

#### 弾薬設定（AmmoConfig）

```java
EXPLOSIVE_AMMO_DESTROYS_BLOCK  // 爆発でブロック破壊
EXPLOSIVE_AMMO_FIRE            // 爆発で着火
EXPLOSIVE_AMMO_KNOCK_BACK      // 爆発ノックバック
DESTROY_GLASS                  // ガラス破壊
IGNITE_BLOCK                   // ブロック着火
IGNITE_ENTITY                  // エンティティ着火
GLOBAL_BULLET_SPEED_MODIFIER   // 弾速倍率（デフォルト2.0）
PASS_THROUGH_BLOCKS            // 貫通可能ブロック
```

#### サーバー設定（SyncConfig）

```java
SERVER_SHOOT_COOLDOWN_V    // 射撃クールダウン検証
SERVER_SHOOT_NETWORK_V     // ネットワークタイミング検証
DAMAGE_BASE_MULTIPLIER     // グローバルダメージ倍率
```

---

## 11. PlayerRevive互換性の重要ポイント

### 競合領域の特定

#### 1. ダメージ適用の二重呼び出し

```java
// EntityKineticBullet.tacAttackEntity() より
entity.hurt(normalSource, normalDamage);      // 1回目
entity.hurt(armorPiercingSource, piercingDamage); // 2回目
```

**問題**: PlayerReviveは`LivingDeathEvent`を1回キャンセルするが、2回目のダメージで死亡する可能性。

**解決策**: `EntityHurtByGunEvent.Pre`でダメージをインターセプトし、PlayerReviveの出血状態中はキャンセル。

#### 2. 無敵時間のリセット

```java
// EntityKineticBullet.java
targetEntity.invulnerableTime = 0;
```

**問題**: PlayerReviveが設定する無敵時間を無効化。

**解決策**: 出血状態中のプレイヤーへの無敵時間リセットを防止。

#### 3. カスタムダメージソース

```java
ModDamageTypes.BULLET
ModDamageTypes.BULLET_IGNORE_ARMOR
ModDamageTypes.BULLET_VOID
ModDamageTypes.BULLET_VOID_IGNORE_ARMOR
```

**問題**: PlayerReviveのバイパスリストに含まれていない場合、蘇生が機能する。含まれている場合、即死。

**解決策**: PlayerRevive設定でこれらのダメージソースを適切に処理。

#### 4. イベント優先度

| Mod | イベント | 優先度 |
|-----|----------|--------|
| PlayerRevive | LivingDeathEvent | HIGHEST |
| TACZ | EntityHurtByGunEvent.Pre | 任意 |
| TACZ | LivingHurtEvent | 通常 |

**考慮点**: TACZのダメージはLivingDeathEventの前に処理される。

#### 5. EntityKillByGunEvent

```java
// エンティティ死亡後に発火
if (target.isDeadOrDying()) {
    EntityKillByGunEvent.CALLBACK.invoker().onKill(event);
}
```

**問題**: PlayerReviveが死亡をキャンセルした場合でも、TACZがキルイベントを発火する可能性。

### 推奨フックポイント

#### 最優先: EntityHurtByGunEvent.Pre

```java
EntityHurtByGunEvent.Pre.CALLBACK.register((event) -> {
    Entity target = event.getHurtEntity();
    if (target instanceof Player player) {
        if (PlayerReviveServer.isBleeding(player)) {
            // 出血中はダメージをキャンセル
            event.setCanceled(true);
            return;
        }
    }
});
```

#### 代替: LivingHurtEvent + DamageSourceチェック

```java
LivingHurtEvent.CALLBACK.register((entity, source, amount) -> {
    if (source.is(ModDamageTypes.BULLETS_TAG)) {
        // 弾丸ダメージの特別処理
    }
});
```

### 互換性modの実装方針

```
1. EntityHurtByGunEvent.Preをリッスン
   → 出血中プレイヤーへのダメージをキャンセル
   → または、ダメージを0に設定

2. EntityKillByGunEventをリッスン
   → PlayerReviveの状態と同期

3. 無敵時間の保護
   → 出血中プレイヤーのinvulnerableTimeをMixinで保護

4. ダメージソースの登録
   → PlayerReviveのバイパスリストにTACZダメージを含めない設定

5. 二重ダメージの対応
   → 両方のhurt()呼び出しをインターセプト
```

### コード参照

| ファイル | 重要度 | 内容 |
|----------|--------|------|
| `EntityKineticBullet.java` | ⭐⭐⭐ | ダメージ適用ロジック |
| `ModDamageTypes.java` | ⭐⭐⭐ | ダメージソース定義 |
| `EntityHurtByGunEvent.java` | ⭐⭐⭐ | Pre/Postイベント |
| `EntityKillByGunEvent.java` | ⭐⭐ | キルイベント |
| `LivingEntityMixin.java` | ⭐⭐ | IGunOperator実装 |
| `EntityDamageEvent.java` | ⭐ | ダメージ耐性処理 |

---

## 付録: 重要な定数

### ダメージタイプResourceLocation

```java
"tacz:bullet"
"tacz:bullet_ignore_armor"
"tacz:bullet_void"
"tacz:bullet_void_ignore_armor"
```

### タグ

```java
TagKey<DamageType> BULLETS_TAG = TagKey.create(
    Registries.DAMAGE_TYPE,
    ResourceLocation.tryBuild("tacz", "bullets")
);
```

### エンティティタグ

```java
TagKey<EntityType<?>> USE_MAGIC_DAMAGE_ON     // 魔法ダメージ使用
TagKey<EntityType<?>> USE_VOID_DAMAGE_ON      // voidダメージ使用
TagKey<EntityType<?>> PRETEND_MELEE_DAMAGE_ON // 近接扱い
```

---

*このドキュメントはTACZ-Refabricated (Fabric 1.20.1)を基に作成されました。*
