package com.lyngarr.wanderingpets.util;

import net.minecraft.util.math.BlockPos;

public interface WanderingAccessor {
    boolean getWandering();
    void setWandering(boolean wandering);
    boolean hasHomePos();
    BlockPos getHomePos();
    void setHomePos(BlockPos homePos);
    void clearHomePos();
}