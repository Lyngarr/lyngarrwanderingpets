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
    private Boolean lastWanderingState = null;

    @Unique
    private void syncWanderingGoals(boolean isWandering) {
        MobEntity self = (MobEntity) (Object) this;

        if (self instanceof TameableEntity tameable && tameable.isTamed()) {
            if (isWandering) {
                // Remove follow goal and add wander goal
                goalSelector.getGoals().removeIf(goal -> goal.getGoal() instanceof FollowOwnerGoal);

                // Check if wander goal already exists (only check once during state change)
                boolean hasWanderGoal = goalSelector.getGoals().stream()
                        .anyMatch(goal -> goal.getGoal() instanceof WanderAroundFarGoal);

                if (!hasWanderGoal) {
                    goalSelector.add(10, new WanderAroundFarGoal((PathAwareEntity) tameable, 1.0));
                }
            } else {
                // Remove wander goal and add follow goal
                goalSelector.getGoals().removeIf(goal -> goal.getGoal() instanceof WanderAroundFarGoal);

                // Check if follow goal already exists (only check once during state change)
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
        MobEntity self = (MobEntity) (Object) this;

        // Only sync goals when wandering state changes, not every tick
        boolean currentWandering = ((WanderingAccessor) this).getWandering();
        if (lastWanderingState == null || lastWanderingState != currentWandering) {
            lastWanderingState = currentWandering;
            this.syncWanderingGoals(currentWandering);
        }

        // Handle freeze ticks for smooth state transitions
        if (freezeTicks > 0) {
            freezeTicks--;
            self.setAiDisabled(true);
        } else if (self.isAiDisabled()) {
            self.setAiDisabled(false);
        }
    }
}
