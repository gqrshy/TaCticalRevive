package com.tacz.guns.api.event.common;

import cn.sh1rocu.tacz.api.LogicalSide;
import cn.sh1rocu.tacz.api.event.BaseEvent;
import cn.sh1rocu.tacz.api.event.ICancellableEvent;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * TACZ API Stub - for compilation only
 */
public class EntityHurtByGunEvent extends BaseEvent {
    protected Entity bullet;
    protected @Nullable Entity hurtEntity;
    protected @Nullable LivingEntity attacker;
    protected ResourceLocation gunId;
    protected float baseAmount;
    protected boolean isHeadShot;
    protected float headshotMultiplier;
    protected LogicalSide logicalSide;

    public static final Event<PreCallBack> PRE = EventFactory.createArrayBacked(PreCallBack.class, callbacks -> event -> {
        for (PreCallBack callback : callbacks) {
            callback.post(event);
        }
    });

    public static final Event<PostCallBack> POST = EventFactory.createArrayBacked(PostCallBack.class, callbacks -> event -> {
        for (PostCallBack callback : callbacks) {
            callback.post(event);
        }
    });

    public interface PreCallBack {
        void post(Pre event);
    }

    public interface PostCallBack {
        void post(Post event);
    }

    public static class Pre extends EntityHurtByGunEvent implements ICancellableEvent {
        public void setBaseAmount(float amount) {
            this.baseAmount = amount;
        }
    }

    public static class Post extends EntityHurtByGunEvent {
    }

    public Entity getBullet() {
        return bullet;
    }

    @Nullable
    public Entity getHurtEntity() {
        return hurtEntity;
    }

    @Nullable
    public LivingEntity getAttacker() {
        return attacker;
    }

    public ResourceLocation getGunId() {
        return gunId;
    }

    public float getBaseAmount() {
        return baseAmount;
    }

    public float getAmount() {
        return baseAmount * headshotMultiplier;
    }

    public boolean isHeadShot() {
        return isHeadShot;
    }

    public float getHeadshotMultiplier() {
        return headshotMultiplier;
    }

    public LogicalSide getLogicalSide() {
        return logicalSide;
    }
}
