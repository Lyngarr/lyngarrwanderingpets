package com.lyngarr.wanderingpets.util;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;

public class WanderingUtil {
    public static boolean isWandering(Entity entity) {
        NbtCompound nbt = new NbtCompound();
        entity.writeNbt(nbt);
        return nbt.getBoolean("WanderingPets_isWandering");
    }

    public static void setWandering(Entity entity, boolean wandering) {
        NbtCompound nbt = new NbtCompound();
        entity.writeNbt(nbt);
        nbt.putBoolean("WanderingPets_isWandering", wandering);
        entity.readNbt(nbt);
    }
}
