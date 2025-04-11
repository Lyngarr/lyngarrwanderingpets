package com.lyngarr.wanderingpets.mixin;

import com.lyngarr.wanderingpets.util.WanderingAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(Entity.class)
public abstract class EntityMixin implements WanderingAccessor {

    @Unique
    private boolean wandering = false;

    @Unique
    private long lastInteractionTime = 0;

    @Override
    public boolean getWandering() {
        return wandering;
    }

    @Override
    public void setWandering(boolean wandering) {
        this.wandering = wandering;
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void onWriteNbt(NbtCompound nbt, CallbackInfoReturnable<NbtCompound> cir) {
        nbt.putBoolean("WanderingPets_isWandering", wandering);
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void onReadNbt(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("WanderingPets_isWandering")) {
            this.wandering = nbt.getBoolean("WanderingPets_isWandering", true);
        }
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void onInteract(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastInteractionTime < 50) {
            cir.setReturnValue(ActionResult.PASS);
            cir.cancel();
            return;
        }

        lastInteractionTime = currentTime;

        if ((Object)this instanceof TameableEntity tameable) {
            if (!tameable.isTamed() || !tameable.isOwner(player)) return;

            if (player.isSneaking()) {
                boolean newState = !wandering;
                wandering = newState;
                player.sendMessage(Text.literal(tameable.getName().getString() + (newState ? " Wandering" : " Following")), true);
                cir.setReturnValue(ActionResult.SUCCESS);
                cir.cancel();
            }
        }
    }
}
