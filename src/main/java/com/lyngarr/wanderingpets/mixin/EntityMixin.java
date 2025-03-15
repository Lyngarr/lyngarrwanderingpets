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

@Mixin(Entity.class)
public class EntityMixin {
    @Unique
    private boolean wandering = false;

    @Unique
    private long lastInteractionTime = 0;

    public boolean getWandering() {
        return wandering;
    }
    
    public void setWandering(boolean wandering) {
        this.wandering = wandering;
    }

    @Inject(at = @At("HEAD"), method = "interact", cancellable = true)
    public void onInteract(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastInteractionTime < 50) { // 50ms pour éviter le double appel
            cir.setReturnValue(ActionResult.PASS);
            cir.cancel();
            return;
        }
        lastInteractionTime = currentTime;
        if ((Object) this instanceof TameableEntity tameable) {
            if (!tameable.isTamed() || !tameable.isOwner(player)) {
                return;
            }

            if (player.isSneaking()) {
                this.wandering = !this.wandering;
                if(this.getWandering()) {
                    player.sendMessage(Text.literal(((Entity) (Object) this).getDisplayName().getString() + " Wandering"), true);
                } else {
                    player.sendMessage(Text.literal(((Entity) (Object) this).getDisplayName().getString() + " Following"), true);
                }
                cir.setReturnValue(ActionResult.SUCCESS);
                cir.cancel();
                return;
            }
        }
    }
}
