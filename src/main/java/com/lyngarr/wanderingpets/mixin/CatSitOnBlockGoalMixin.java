package com.lyngarr.wanderingpets.mixin;

import com.lyngarr.wanderingpets.LyngarrWanderingPets;
import com.lyngarr.wanderingpets.util.WanderingAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.goal.CatSitOnBlockGoal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;

@Mixin(CatSitOnBlockGoal.class)
public class CatSitOnBlockGoalMixin {

    @Shadow
    @Final
    private Cat cat;

    @Unique
    private int wanderingPets$lastSuccessfulSitTick = -1000;

    @Unique
    private static final int REATTEMPT_MIN_TICKS = 400;

    @Unique
    private static void sendDebugToOwner(Cat cat, String message) {
        if (!LyngarrWanderingPets.DEBUG_MODE) return;
        if (cat.getOwner() instanceof Player owner) {
            owner.sendSystemMessage(Component.literal("[WP CAT DEBUG] " + message));
        }
    }

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void wanderingPets$throttleSitOnBlock(CallbackInfoReturnable<Boolean> cir) {
        WanderingAccessor accessor = (WanderingAccessor) this.cat;
        boolean isWandering = accessor.getWandering();

        if (!isWandering) {
            return;
        }

        int ticksSinceLastSuccess = this.cat.tickCount - wanderingPets$lastSuccessfulSitTick;
        sendDebugToOwner(this.cat, "canStart() - ticks since last=" + ticksSinceLastSuccess);

        if (ticksSinceLastSuccess < REATTEMPT_MIN_TICKS) {
            sendDebugToOwner(this.cat, "  -> too soon, BLOCKING");
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        // 40% chance to skip sitting attempt for natural behavior
        if (this.cat.getRandom().nextFloat() < 0.3f) {
            sendDebugToOwner(this.cat, "  -> random skip");
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        sendDebugToOwner(this.cat, "  -> allowing vanilla behavior");
    }

    @Inject(method = "start", at = @At("TAIL"))
    private void wanderingPets$afterStart(CallbackInfo ci) {
        if (((WanderingAccessor) this.cat).getWandering()) {
            wanderingPets$lastSuccessfulSitTick = this.cat.tickCount;
            sendDebugToOwner(this.cat, "start() called - logged at tick=" + wanderingPets$lastSuccessfulSitTick);
        }
    }
}
