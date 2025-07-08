package com.lyngarr.wanderingpets.mixin;

import com.lyngarr.wanderingpets.util.WanderingAccessor;

import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.storage.ReadView;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public class MobEntityMixin {

    @Shadow
    protected net.minecraft.entity.ai.goal.GoalSelector goalSelector;

    @Unique
    private int freezeTicks = 0;

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

    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void onReadCustomData(ReadView view, CallbackInfo ci) {
        boolean isWandering = view.getBoolean("WanderingPets_isWandering", false);
        if (isWandering) {
            freezeTicks = 1;
        }
    }


    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void onTickMovement(CallbackInfo ci) {
        this.syncWanderingGoals();
        if (freezeTicks > 0) {
            System.out.println("freeze tick");
            freezeTicks--;
            ((MobEntity)(Object)this).setAiDisabled(true);
        } else if (((MobEntity)(Object)this).isAiDisabled()) {
            ((MobEntity)(Object)this).setAiDisabled(false);
        }
    }
}
