package com.tacz.guns.api.event.common;

import cn.sh1rocu.tacz.api.LogicalSide;
import cn.sh1rocu.tacz.api.event.BaseEvent;
import cn.sh1rocu.tacz.api.event.ICancellableEvent;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * TACZ API Stub - for compilation only
 */
public class GunFireEvent extends BaseEvent implements ICancellableEvent {
    private LivingEntity shooter;
    private ItemStack gunItemStack;
    private LogicalSide logicalSide;

    public static final Event<Callback> CALLBACK = EventFactory.createArrayBacked(Callback.class, callbacks -> event -> {
        for (Callback callback : callbacks) {
            callback.post(event);
        }
    });

    public interface Callback {
        void post(GunFireEvent event);
    }

    public LivingEntity getShooter() {
        return shooter;
    }

    public ItemStack getGunItemStack() {
        return gunItemStack;
    }

    public LogicalSide getLogicalSide() {
        return logicalSide;
    }
}
