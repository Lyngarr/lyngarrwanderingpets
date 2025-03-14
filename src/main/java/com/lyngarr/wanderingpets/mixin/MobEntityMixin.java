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
public class MobEntityMixin {
    @Unique
    private boolean wandering = false;

    public boolean getWandering() {
        return wandering;
    }
    
    public void setWandering(boolean wandering) {
        this.wandering = wandering;
    }

    @Shadow
    protected net.minecraft.entity.ai.goal.GoalSelector goalSelector;


    @Inject(at = @At("HEAD"), method = "interactMob", cancellable = true)
    public void onInteractMob(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        System.out.println("Interacted with: " + ((Entity) (Object) this).getDisplayName().getString());
        System.out.println("Entity on ground? " + ((Entity) (Object) this).isOnGround());
        System.out.println("Entity flying? " + !((Entity) (Object) this).isOnGround());
        if ((Object) this instanceof TameableEntity tameable) {
            if (!tameable.isTamed() || !tameable.isOwner(player)) {
                return;
            }

            if (player.isSneaking()) {
                this.wandering = !this.wandering;
                System.out.println("before if");
                if(this.getWandering()) {
                    System.out.println("if wandering");
                    player.sendMessage(Text.literal(((Entity) (Object) this).getDisplayName().getString() + " Wandering"), true);
                } else {
                    System.out.println("else wandering");
                    player.sendMessage(Text.literal(((Entity) (Object) this).getDisplayName().getString() + " Following"), true);
                }
                cir.setReturnValue(ActionResult.SUCCESS);
                cir.cancel();
            }
        }
    }

    @Inject( at = @At("TAIL"), method = "tickMovement")
    public void onTickMovement(CallbackInfo ci) {
        if ((Object) this instanceof TameableEntity tameable && tameable.isTamed()) {
            if (this.wandering) { // WANDERING
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
