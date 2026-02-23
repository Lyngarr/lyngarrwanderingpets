package com.lyngarr.wanderingpets.mixin;

import com.lyngarr.wanderingpets.LyngarrWanderingPets;
import com.lyngarr.wanderingpets.util.WanderingAccessor;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.SERVER)
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
        return ((Entity) (Object) this).getEntityWorld().getRegistryKey().getValue().toString();
    }

    @Unique
    private static void sendDebugToPlayer(PlayerEntity player, String message) {
        if (LyngarrWanderingPets.DEBUG_MODE) {
            player.sendMessage(Text.literal("[WP DEBUG] " + message), false);
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
        return homePosByDimension.getOrDefault(getCurrentDimensionId(), BlockPos.ORIGIN);
    }

    @Override
    public void setHomePos(BlockPos homePos) {
        homePosByDimension.put(getCurrentDimensionId(), homePos.toImmutable());
    }

    @Override
    public void clearHomePos() {
        homePosByDimension.remove(getCurrentDimensionId());
    }

    @Inject(method = "writeData", at = @At("TAIL"))
    private void onWriteData(WriteView view, CallbackInfo ci) {
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

    @Inject(method = "readData", at = @At("TAIL"))
    private void onReadData(ReadView view, CallbackInfo ci) {
        // Default to false (following) for consistency with initial field value
        this.wandering = view.getBoolean("WanderingPets_isWandering", false);
        this.homePosByDimension.clear();

        int homeCount = view.getInt("WanderingPets_homeCount", 0);
        for (int index = 0; index < homeCount; index++) {
            String keyPrefix = "WanderingPets_home_" + index + "_";
            String dimensionId = view.getString(keyPrefix + "dim", "");
            if (dimensionId.isEmpty()) {
                continue;
            }

            int homeX = view.getInt(keyPrefix + "x", 0);
            int homeY = view.getInt(keyPrefix + "y", 0);
            int homeZ = view.getInt(keyPrefix + "z", 0);
            this.homePosByDimension.put(dimensionId, new BlockPos(homeX, homeY, homeZ));
        }

        if (this.homePosByDimension.isEmpty() && view.getBoolean("WanderingPets_hasHome", false)) {
            int homeX = view.getInt("WanderingPets_homeX", 0);
            int homeY = view.getInt("WanderingPets_homeY", 0);
            int homeZ = view.getInt("WanderingPets_homeZ", 0);
            this.homePosByDimension.put(getCurrentDimensionId(), new BlockPos(homeX, homeY, homeZ));
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
                if (newState) {
                    setHomePos(((Entity) (Object) this).getBlockPos());
                }

                BlockPos currentHome = this.getHomePos();
                String dimensionId = getCurrentDimensionId();

                sendDebugToPlayer(player, tameable.getName().getString()
                    + " -> " + (newState ? "WANDERING" : "FOLLOWING")
                    + " | dim: " + dimensionId
                    + " | home: " + currentHome.getX() + ", " + currentHome.getY() + ", " + currentHome.getZ());

                player.sendMessage(Text.literal(tameable.getName().getString() + (newState ? " Wandering" : " Following")), true);
                cir.setReturnValue(ActionResult.SUCCESS);
                cir.cancel();
            }
        }
    }
}
