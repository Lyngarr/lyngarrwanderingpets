package com.lyngarr.wanderingpets.mixin;

import com.lyngarr.wanderingpets.util.WanderingAccessor;

import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.EntityData;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.ServerWorldAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public class MobEntityMixin {

    @Shadow
    protected net.minecraft.entity.ai.goal.GoalSelector goalSelector;

    @Unique
    private void syncWanderingGoals() {
        if ((Object) this instanceof TameableEntity tameable && tameable.isTamed()) {
            if (((WanderingAccessor) this).getWandering()) {
                goalSelector.getGoals().removeIf(goal -> goal.getGoal() instanceof FollowOwnerGoal);
                boolean hasWanderGoal = goalSelector.getGoals().stream()
                        .anyMatch(goal -> goal.getGoal() instanceof WanderAroundFarGoal);

                if (!hasWanderGoal) {
                    goalSelector.add(10, new WanderAroundFarGoal((PathAwareEntity) tameable, 1.0));
                }
            } else {
                goalSelector.getGoals().removeIf(goal -> goal.getGoal() instanceof WanderAroundFarGoal);
                boolean hasFollowGoal = goalSelector.getGoals().stream()
                        .anyMatch(goal -> goal.getGoal() instanceof FollowOwnerGoal);

                if (!hasFollowGoal && !tameable.isSitting()) {
                    goalSelector.add(1, new FollowOwnerGoal(tameable, 1.0, 10.0F, 2.0F));
                }
            }
        }
    }

    @Inject(method = "initialize", at = @At("TAIL"))
    private void onInitialize(
                            net.minecraft.world.ServerWorldAccess world,
                            net.minecraft.world.LocalDifficulty difficulty, 
                            net.minecraft.entity.SpawnReason spawnReason,
                            net.minecraft.entity.EntityData entityData,
                            CallbackInfoReturnable<EntityData> cir) {
        this.syncWanderingGoals();
    }

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void onInitGoals(CallbackInfo ci) {
        if ((Object) this instanceof TameableEntity tameable) {
            this.goalSelector.getGoals().removeIf(goal -> goal.getGoal() instanceof FollowOwnerGoal);
            this.goalSelector.add(1, new FollowOwnerGoal(tameable, 1.0, 10.0f, 2.0f));
        }
    }



    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void onReadNbt(NbtCompound nbt, CallbackInfo ci) {
        this.syncWanderingGoals();
    }


    @Inject( at = @At("TAIL"), method = "tickMovement")
    public void onTickMovement(CallbackInfo ci) {
        this.syncWanderingGoals();
    }
}
