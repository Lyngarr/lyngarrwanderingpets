package com.lyngarr.wanderingpets.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.text.Text;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.server.world.ServerWorld;


import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.lyngarr.wanderingpets.util.WanderingAccessor;
import com.lyngarr.wanderingpets.util.WanderingUtil;
import com.lyngarr.wanderingpets.mixin.MobEntityMixin;


@Mixin(Entity.class)
public class EntityMixin implements WanderingAccessor {    
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

    @Inject(method = "writeNbt", at = @At("HEAD"))
    private void onWriteNbt(NbtCompound nbt, CallbackInfoReturnable<NbtCompound> cir) {
        nbt.putBoolean("WanderingPets_isWandering", this.wandering);
    }

    @Inject(method = "readNbt", at = @At("HEAD"))
    private void onReadNbt(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("WanderingPets_isWandering")) {
            this.wandering = nbt.getBoolean("WanderingPets_isWandering", true);
        }
    }

    @Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
    private void onTeleport(ServerWorld world, double destX, double destY, double destZ, Set<?> flags, float deltaX, float deltaY, boolean teleportToEnd, CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof WanderingAccessor accessor && accessor.getWandering()) {
            System.out.println("[Mixin] Cancelled teleport for wandering pet");
            cir.setReturnValue(false);
            cir.cancel();
        }
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
                boolean newWandering = !WanderingUtil.isWandering((Entity)(Object)this);
                WanderingUtil.setWandering((Entity)(Object)this, newWandering);
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
