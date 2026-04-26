package com.lyngarr.wanderingpets.mixin;

import com.lyngarr.wanderingpets.LyngarrWanderingPets;
import com.lyngarr.wanderingpets.util.WanderingAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.HashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin implements WanderingAccessor {

    @Unique
    private boolean wandering = false;

    @Unique
    private long lastInteractionTime = 0;

    @Unique
    private final Map<String, BlockPos> homePosByDimension = new HashMap<>();

    @Unique
    private String getCurrentDimensionId() {
        return ((Entity) (Object) this).level().dimension().identifier().toString();
    }

    @Unique
    private static void sendDebugToPlayer(Player player, String message) {
        if (LyngarrWanderingPets.DEBUG_MODE) {
            player.sendSystemMessage(Component.literal("[WP DEBUG] " + message));
        }
    }

    @Override
    public boolean getWandering() {
        return wandering;
    }

    @Override
    public void setWandering(boolean wandering) {
        this.wandering = wandering;
    }

    @Override
    public boolean hasHomePos() {
        return homePosByDimension.containsKey(getCurrentDimensionId());
    }

    @Override
    public BlockPos getHomePos() {
        return homePosByDimension.getOrDefault(getCurrentDimensionId(), BlockPos.ZERO);
    }

    @Override
    public void setHomePos(BlockPos homePos) {
        homePosByDimension.put(getCurrentDimensionId(), homePos.immutable());
    }

    @Override
    public void clearHomePos() {
        homePosByDimension.remove(getCurrentDimensionId());
    }

    @Inject(method = "saveWithoutId", at = @At("TAIL"))
    private void onWriteData(ValueOutput view, CallbackInfo ci) {
        view.putBoolean("WanderingPets_isWandering", this.wandering);
        view.putInt("WanderingPets_homeCount", this.homePosByDimension.size());

        int index = 0;
        for (Map.Entry<String, BlockPos> entry : this.homePosByDimension.entrySet()) {
            String keyPrefix = "WanderingPets_home_" + index + "_";
            BlockPos pos = entry.getValue();
            view.putString(keyPrefix + "dim", entry.getKey());
            view.putInt(keyPrefix + "x", pos.getX());
            view.putInt(keyPrefix + "y", pos.getY());
            view.putInt(keyPrefix + "z", pos.getZ());
            index++;
        }

        boolean hasCurrentDimHome = this.hasHomePos();
        BlockPos currentDimHome = this.getHomePos();
        view.putBoolean("WanderingPets_hasHome", hasCurrentDimHome);
        if (hasCurrentDimHome) {
            view.putInt("WanderingPets_homeX", currentDimHome.getX());
            view.putInt("WanderingPets_homeY", currentDimHome.getY());
            view.putInt("WanderingPets_homeZ", currentDimHome.getZ());
        }
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void onReadData(ValueInput view, CallbackInfo ci) {
        // Default to false (following) for consistency with initial field value
        this.wandering = view.getBooleanOr("WanderingPets_isWandering", false);
        this.homePosByDimension.clear();

        int homeCount = view.getIntOr("WanderingPets_homeCount", 0);
        for (int index = 0; index < homeCount; index++) {
            String keyPrefix = "WanderingPets_home_" + index + "_";
            String dimensionId = view.getStringOr(keyPrefix + "dim", "");
            if (dimensionId.isEmpty()) {
                continue;
            }

            int homeX = view.getIntOr(keyPrefix + "x", 0);
            int homeY = view.getIntOr(keyPrefix + "y", 0);
            int homeZ = view.getIntOr(keyPrefix + "z", 0);
            this.homePosByDimension.put(dimensionId, new BlockPos(homeX, homeY, homeZ));
        }

        if (this.homePosByDimension.isEmpty() && view.getBooleanOr("WanderingPets_hasHome", false)) {
            int homeX = view.getIntOr("WanderingPets_homeX", 0);
            int homeY = view.getIntOr("WanderingPets_homeY", 0);
            int homeZ = view.getIntOr("WanderingPets_homeZ", 0);
            this.homePosByDimension.put(getCurrentDimensionId(), new BlockPos(homeX, homeY, homeZ));
        }

    }


    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void onInteract(Player player, InteractionHand hand, Vec3 hitPosition, CallbackInfoReturnable<InteractionResult> cir) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastInteractionTime < 50) {
            cir.setReturnValue(InteractionResult.PASS);
            cir.cancel();
            return;
        }

        lastInteractionTime = currentTime;

        if ((Object)this instanceof TamableAnimal tameable) {
            if (!tameable.isTame() || !tameable.isOwnedBy(player)) return;

            if (player.isShiftKeyDown()) {
                if (((Entity) (Object) this).level().isClientSide()) {
                    cir.setReturnValue(InteractionResult.SUCCESS);
                    cir.cancel();
                    return;
                }

                boolean newState = !wandering;
                wandering = newState;
                if (newState) {
                    setHomePos(((Entity) (Object) this).blockPosition());
                }

                BlockPos currentHome = this.getHomePos();
                String dimensionId = getCurrentDimensionId();

                sendDebugToPlayer(player, tameable.getName().getString()
                    + " -> " + (newState ? "WANDERING" : "FOLLOWING")
                    + " | dim: " + dimensionId
                    + " | home: " + currentHome.getX() + ", " + currentHome.getY() + ", " + currentHome.getZ());

                player.sendOverlayMessage(Component.literal(tameable.getName().getString() + (newState ? " Wandering" : " Following")));
                cir.setReturnValue(InteractionResult.SUCCESS);
                cir.cancel();
            }
        }
    }
}
