package com.lyngarr.wanderingpets.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public class MobEntityMixin extends EntityMixin {

    @Shadow
    protected net.minecraft.entity.ai.goal.GoalSelector goalSelector;

    @Inject( at = @At("TAIL"), method = "tickMovement")
    public void onTickMovement(CallbackInfo ci) {
        if ((Object) this instanceof TameableEntity tameable && tameable.isTamed()) {
            if (((EntityMixin)(Object)this).getWandering()) { // WANDERING
                goalSelector.getGoals().removeIf(goal -> goal.getGoal() instanceof FollowOwnerGoal);
                boolean hasWanderGoal = goalSelector.getGoals().stream()
                        .anyMatch(goal -> goal.getGoal() instanceof WanderAroundFarGoal);

                if (!hasWanderGoal) {
                    goalSelector.add(10, new WanderAroundFarGoal((PathAwareEntity) tameable, 1.0));
                }
            } else { // SORTIE DU MODE WANDERING
                goalSelector.getGoals().removeIf(goal -> goal.getGoal() instanceof WanderAroundFarGoal);
                if (!tameable.isSitting()) {
                    boolean hasFollowGoal = goalSelector.getGoals().stream()
                            .anyMatch(goal -> goal.getGoal() instanceof FollowOwnerGoal);

                    if (!hasFollowGoal) {
                        goalSelector.add(1, new FollowOwnerGoal(tameable, 1.0, 10.0F, 2.0F));
                    }
                }
            }
        }
    }
}
