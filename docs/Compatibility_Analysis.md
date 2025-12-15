# PlayerRevive × TACZ 互換性解析ドキュメント

> **目的**: このドキュメントはPlayerReviveとTACZの両modを分析し、互換性問題と解決策をまとめたものです。新しい互換性modの開発ガイドとして使用します。

## 目次

1. [問題の概要](#1-問題の概要)
2. [技術的な競合ポイント](#2-技術的な競合ポイント)
3. [イベントフロー比較](#3-イベントフロー比較)
4. [解決すべき問題一覧](#4-解決すべき問題一覧)
5. [推奨アーキテクチャ](#5-推奨アーキテクチャ)
6. [実装ガイド](#6-実装ガイド)

---

## 1. 問題の概要

### 両modの目的

| Mod | 目的 | プラットフォーム |
|-----|------|-----------------|
| PlayerRevive | 死亡時に「出血状態」にして蘇生可能にする | NeoForge 1.21.1 |
| TACZ | リアルな銃火器システムを追加 | Fabric 1.20.1 |

### 根本的な競合

PlayerReviveは`LivingDeathEvent`をインターセプトして死亡を防ぐが、TACZの銃ダメージは特殊な方法で適用されるため、以下の問題が発生する可能性がある：

1. **二重ダメージ**: 1発の弾丸で2回hurt()が呼ばれる
2. **無敵時間リセット**: TACZは無敵時間をリセットする
3. **イベントタイミング**: ダメージイベントと死亡イベントの順序
4. **ダメージソース識別**: カスタムダメージソースの処理

---

## 2. 技術的な競合ポイント

### 2.1 ダメージ適用の流れ

#### PlayerRevive側

```
プレイヤーがダメージを受ける
    ↓
LivingEntity.hurt()
    ↓
体力が0以下になる
    ↓
LivingDeathEvent発火（HIGHEST優先度）
    ↓
PlayerRevive: イベントをキャンセル
    ↓
出血状態に移行
```

#### TACZ側

```
弾丸がプレイヤーにヒット
    ↓
EntityHurtByGunEvent.Pre発火（キャンセル可能）
    ↓
無敵時間リセット (invulnerableTime = 0)
    ↓
カスタムノックバック設定
    ↓
hurt() 1回目: 通常ダメージ
    ↓
hurt() 2回目: 防具貫通ダメージ
    ↓
EntityHurtByGunEvent.Post または EntityKillByGunEvent発火
```

### 2.2 二重ダメージ問題

```java
// EntityKineticBullet.tacAttackEntity()
float armorDamagePercent = Mth.clamp(armorIgnore, 0.0F, 1.0F);
float normalDamagePercent = 1 - armorDamagePercent;

// 1回目のダメージ
entity.hurt(normalDamageSource, damage * normalDamagePercent);

// 2回目のダメージ（防具無視）
entity.hurt(armorPiercingSource, damage * armorDamagePercent);
```

**問題シナリオ**:
1. 1回目のhurt()で体力0以下 → LivingDeathEvent発火
2. PlayerReviveがキャンセル → 出血状態に
3. 2回目のhurt()が発生 → 出血中に再度ダメージ

### 2.3 無敵時間の競合

```java
// TACZ: EntityKineticBullet.java
targetEntity.invulnerableTime = 0;  // 無敵時間リセット

// PlayerRevive: initialDamageCooldown設定
// 出血開始から10tickは無敵のはずだが...
```

**問題**: TACZが無敵時間をリセットするため、PlayerReviveの保護が無効化される。

### 2.4 ダメージソースの識別

#### TACZのダメージソース

```
tacz:bullet
tacz:bullet_ignore_armor
tacz:bullet_void
tacz:bullet_void_ignore_armor
```

#### PlayerReviveのバイパスチェック

```java
// PlayerReviveはダメージソースをチェック
if (bypassDamageSources.contains(source.getMsgId())) {
    // 蘇生をスキップ、即死
}
```

**考慮点**: TACZのダメージソースをバイパスリストに入れると即死、入れないと蘇生可能。

---

## 3. イベントフロー比較

### 時系列での処理順序

```
[弾丸ヒット]
    │
    ▼
┌─────────────────────────────────────────┐
│ TACZ: EntityHurtByGunEvent.Pre          │ ← ここでインターセプト可能
│ (キャンセル可能)                          │
└─────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────┐
│ TACZ: invulnerableTime = 0              │ ← 無敵時間リセット
└─────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────┐
│ Vanilla: LivingEntity.hurt()            │
│ → LivingHurtEvent (Fabric)              │
│ → LivingDamageEvent (Forge/NeoForge)    │
└─────────────────────────────────────────┘
    │
    ▼
[体力が0以下?]
    │
    ├─ No → 生存
    │
    ▼ Yes
┌─────────────────────────────────────────┐
│ Vanilla: LivingDeathEvent               │
│ PlayerRevive: HIGHEST優先度でリッスン     │
│ → イベントをキャンセル                    │
│ → 出血状態に移行                         │
└─────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────┐
│ TACZ: 2回目のhurt()呼び出し              │ ← 問題発生ポイント
│ (防具貫通ダメージ)                        │
└─────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────┐
│ TACZ: EntityHurtByGunEvent.Post         │
│ または EntityKillByGunEvent              │
└─────────────────────────────────────────┘
```

---

## 4. 解決すべき問題一覧

### 優先度: 高

| # | 問題 | 影響 | 解決策 |
|---|------|------|--------|
| 1 | 二重ダメージ | 出血中に追加ダメージ | EntityHurtByGunEvent.Preで出血中プレイヤーへのダメージをキャンセル |
| 2 | 無敵時間リセット | 保護が無効化 | Mixinで出血中プレイヤーのinvulnerableTimeを保護 |
| 3 | 死亡判定タイミング | 不整合 | イベント順序を制御 |

### 優先度: 中

| # | 問題 | 影響 | 解決策 |
|---|------|------|--------|
| 4 | EntityKillByGunEvent | 誤発火 | イベントをモニタリングして状態同期 |
| 5 | ダメージソース識別 | 設定依存 | 設定ファイルで制御可能に |
| 6 | プラットフォーム差異 | NeoForge vs Fabric | アーキテクチャレイヤーで抽象化 |

### 優先度: 低

| # | 問題 | 影響 | 解決策 |
|---|------|------|--------|
| 7 | ノックバック | 出血中の移動 | 必要に応じて調整 |
| 8 | 視覚エフェクト | 不整合 | UI調整 |

---

## 5. 推奨アーキテクチャ

### 互換性modの構造

```
TacticalRevive/
├── src/main/java/
│   └── com/example/tacticalrevive/
│       ├── TacticalRevive.java          # メインクラス
│       ├── compat/
│       │   ├── PlayerReviveCompat.java  # PlayerRevive連携
│       │   └── TACZCompat.java          # TACZ連携
│       ├── event/
│       │   └── GunDamageHandler.java    # ダメージイベント処理
│       ├── mixin/
│       │   └── EntityKineticBulletMixin.java  # 弾丸処理修正
│       └── config/
│           └── TacticalReviveConfig.java # 設定
└── src/main/resources/
    └── tacticalrevive.mixins.json
```

### コア処理フロー

```
[弾丸ヒット]
    │
    ▼
┌─────────────────────────────────────────┐
│ TacticalRevive: EntityHurtByGunEvent.Pre│
│                                         │
│ if (target is bleeding) {               │
│     event.setCanceled(true);            │
│     return;                             │
│ }                                       │
└─────────────────────────────────────────┘
    │
    ▼
[通常のTACZ処理]
    │
    ▼
[通常のPlayerRevive処理]
```

---

## 6. 実装ガイド

### 6.1 EntityHurtByGunEvent.Preのハンドリング

```java
public class GunDamageHandler {

    public static void register() {
        EntityHurtByGunEvent.Pre.CALLBACK.register(
            EventPriority.HIGHEST,  // 最高優先度
            GunDamageHandler::onGunDamagePre
        );
    }

    private static void onGunDamagePre(EntityHurtByGunEvent.Pre event) {
        Entity target = event.getHurtEntity();

        if (!(target instanceof Player player)) {
            return;  // プレイヤー以外は通常処理
        }

        // PlayerReviveの出血状態をチェック
        if (isPlayerBleeding(player)) {
            // 出血中のプレイヤーへのダメージをキャンセル
            event.setCanceled(true);
            return;
        }

        // 致死ダメージの場合の特別処理
        if (wouldKillPlayer(player, event.getDamage())) {
            // PlayerReviveに処理を委譲するためにダメージを調整
            // または、ここで出血状態を開始
        }
    }

    private static boolean isPlayerBleeding(Player player) {
        // PlayerRevive APIを使用
        return PlayerReviveServer.isBleeding(player);
    }

    private static boolean wouldKillPlayer(Player player, float damage) {
        return player.getHealth() <= damage;
    }
}
```

### 6.2 無敵時間の保護（Mixin）

```java
@Mixin(EntityKineticBullet.class)
public abstract class EntityKineticBulletMixin {

    @Inject(
        method = "onHitEntity",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/entity/Entity;invulnerableTime:I",
            opcode = Opcodes.PUTFIELD
        ),
        cancellable = true
    )
    private void protectBleedingPlayerInvulnerability(
        Entity target, Vec3 hitLocation, boolean isHeadShot,
        CallbackInfo ci
    ) {
        if (target instanceof Player player) {
            if (PlayerReviveServer.isBleeding(player)) {
                // 無敵時間のリセットをスキップ
                // 注: ci.cancel()は使わない（処理全体をキャンセルしないため）
                // 代わりに、直後に無敵時間を復元する
            }
        }
    }
}
```

### 6.3 ダメージソースの登録

```java
public class TacticalReviveConfig {

    // PlayerReviveのバイパスリストに追加しないダメージソース
    public static final List<String> TACZ_DAMAGE_SOURCES = List.of(
        "tacz:bullet",
        "tacz:bullet_ignore_armor",
        "tacz:bullet_void",
        "tacz:bullet_void_ignore_armor"
    );

    // これらをPlayerReviveのbypassDamageSourcesに含めないことで
    // 蘇生システムが機能するようにする
}
```

### 6.4 イベント状態の同期

```java
public class ReviveStateSync {

    // EntityKillByGunEventをリッスンしてPlayerReviveと同期
    public static void onEntityKillByGun(EntityKillByGunEvent event) {
        Entity killed = event.getHurtEntity();

        if (killed instanceof Player player) {
            // PlayerReviveが出血状態を開始していれば
            // キルイベントは無視（プレイヤーは実際には死んでいない）
            if (PlayerReviveServer.isBleeding(player)) {
                // 必要に応じてTACZ側にフィードバック
                // 例: キル統計をカウントしないなど
            }
        }
    }
}
```

### 6.5 プラットフォーム抽象化

```java
// Fabric/NeoForge両対応のための抽象化層

public interface PlatformHelper {
    boolean isPlayerBleeding(Player player);
    void startBleeding(Player player, DamageSource source);
    void revivePlayer(Player player);
}

// Fabric実装
public class FabricPlatformHelper implements PlatformHelper {
    // Cardinal Components APIを使用
}

// NeoForge実装
public class NeoForgePlatformHelper implements PlatformHelper {
    // NeoForge Attachmentsを使用
}
```

---

## 付録: クイックリファレンス

### PlayerRevive API

```java
// 状態確認
PlayerReviveServer.isBleeding(Player player)
PlayerReviveServer.timeLeft(Player player)
PlayerReviveServer.getBleeding(Player player)

// 操作
PlayerReviveServer.startBleeding(Player player, DamageSource source)
PlayerReviveServer.revive(Player player)
PlayerReviveServer.kill(Player player)
```

### TACZ API

```java
// イベント登録
EntityHurtByGunEvent.Pre.CALLBACK.register(priority, handler)
EntityHurtByGunEvent.Post.CALLBACK.register(handler)
EntityKillByGunEvent.CALLBACK.register(handler)

// ダメージソースチェック
source.is(ModDamageTypes.BULLETS_TAG)

// イベントデータ
event.getHurtEntity()
event.getDamage()
event.getAttacker()
event.isHeadShot()
event.setCanceled(true)
```

### 重要なフィールド/メソッド

```java
// 無敵時間
entity.invulnerableTime

// 出血状態
IBleeding.isBleeding()
IBleeding.bledOut()
IBleeding.knockOut(player, source)

// ダメージ適用
entity.hurt(damageSource, amount)
```

---

*このドキュメントはPlayerRevive v2.0.37 (NeoForge 1.21.1)とTACZ-Refabricated (Fabric 1.20.1)の解析に基づいています。*
