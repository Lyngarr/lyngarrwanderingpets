package com.lyngarr.wanderingpets.mixin;

import com.lyngarr.wanderingpets.LyngarrWanderingPets;
import com.lyngarr.wanderingpets.util.WanderingAccessor;

import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public class MobEntityMixin {

    @Unique
    private static final int HOME_RADIUS = 32;

    @Unique
    private static final int HOME_RADIUS_SQUARED = HOME_RADIUS * HOME_RADIUS;

    @Unique
    private static final int HOME_RETURN_RADIUS_SQUARED = 20 * 20;

    @Shadow
    protected net.minecraft.entity.ai.goal.GoalSelector goalSelector;

    @Unique
    private int freezeTicks = 0;

    @Unique
    private Boolean lastWanderingState = null;

    @Unique
    private static void sendDebugToOwner(TameableEntity tameable, String message) {
        if (!LyngarrWanderingPets.DEBUG_MODE) {
            return;
        }

        if (tameable.getOwner() instanceof PlayerEntity owner) {
            owner.sendMessage(Text.literal("[WP DEBUG] " + message), false);
        }
    }

    @Unique
    private static class ReturnToHomeGoal extends Goal {
        private final PathAwareEntity entity;
        private final WanderingAccessor accessor;
        private final double speed;
        private BlockPos targetHome;

        ReturnToHomeGoal(PathAwareEntity entity, double speed) {
            this.entity = entity;
            this.accessor = (WanderingAccessor) entity;
            this.speed = speed;
            this.setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            if (!accessor.getWandering() || !accessor.hasHomePos()) {
                return false;
            }

            BlockPos home = accessor.getHomePos();
            if (home == null) {
                return false;
            }

            double distanceSquared = entity.squaredDistanceTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
            if (distanceSquared <= HOME_RADIUS_SQUARED) {
                return false;
            }

            targetHome = home;
            return true;
        }

        @Override
        public boolean shouldContinue() {
            if (targetHome == null || !accessor.getWandering()) {
                return false;
            }

            double distanceSquared = entity.squaredDistanceTo(targetHome.getX() + 0.5, targetHome.getY(), targetHome.getZ() + 0.5);
            return distanceSquared > HOME_RETURN_RADIUS_SQUARED;
        }

        @Override
        public void start() {
            if (entity instanceof TameableEntity tameable && targetHome != null) {
                sendDebugToOwner(tameable, tameable.getName().getString()
                        + " return start | home: " + targetHome.getX() + ", " + targetHome.getY() + ", " + targetHome.getZ());
            }
            moveToHome();
        }

        @Override
        public void tick() {
            if (targetHome != null && (entity.getNavigation().isIdle() || entity.age % 20 == 0)) {
                moveToHome();
            }
        }

        @Override
        public void stop() {
            if (entity instanceof TameableEntity tameable && targetHome != null) {
                sendDebugToOwner(tameable, tameable.getName().getString()
                        + " return stop | near home: " + targetHome.getX() + ", " + targetHome.getY() + ", " + targetHome.getZ());
            }
            targetHome = null;
            entity.getNavigation().stop();
        }

        private void moveToHome() {
            if (targetHome != null) {
                entity.getNavigation().startMovingTo(targetHome.getX() + 0.5, targetHome.getY(), targetHome.getZ() + 0.5, speed);
            }
        }
    }

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
                boolean hasReturnGoal = goalSelector.getGoals().stream()
                        .anyMatch(goal -> goal.getGoal() instanceof ReturnToHomeGoal);

                if (!hasWanderGoal) {
                    goalSelector.add(10, new WanderAroundFarGoal((PathAwareEntity) tameable, 1.0));
                }
                if (!hasReturnGoal) {
                    goalSelector.add(2, new ReturnToHomeGoal((PathAwareEntity) tameable, 1.1));
                }

                BlockPos home = ((WanderingAccessor) this).getHomePos();
                sendDebugToOwner(tameable, tameable.getName().getString()
                    + " wandering goals | wander=" + (!hasWanderGoal ? "ADDED" : "PRESENT")
                    + " return=" + (!hasReturnGoal ? "ADDED" : "PRESENT")
                    + " | home: " + home.getX() + ", " + home.getY() + ", " + home.getZ());
            } else {
                // Remove wander/home goals and add follow goal
                goalSelector.getGoals().removeIf(goal -> goal.getGoal() instanceof WanderAroundFarGoal || goal.getGoal() instanceof ReturnToHomeGoal);

                // Check if follow goal already exists (only check once during state change)
                boolean hasFollowGoal = goalSelector.getGoals().stream()
                        .anyMatch(goal -> goal.getGoal() instanceof FollowOwnerGoal);

                if (!hasFollowGoal && !tameable.isSitting()) {
                    goalSelector.add(1, new FollowOwnerGoal(tameable, 1.0, 10.0F, 2.0F));
                }

                sendDebugToOwner(tameable, tameable.getName().getString()
                    + " following goals | follow=" + (!hasFollowGoal ? "ADDED" : "PRESENT"));
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
        WanderingAccessor accessor = (WanderingAccessor) this;

        if (self instanceof TameableEntity tameable && tameable.isTamed() && accessor.getWandering() && !accessor.hasHomePos()) {
            accessor.setHomePos(self.getBlockPos());
            sendDebugToOwner(tameable, tameable.getName().getString()
                + " fallback home set to: " + self.getBlockPos().getX() + ", " + self.getBlockPos().getY() + ", " + self.getBlockPos().getZ());
        }

        // Only sync goals when wandering state changes, not every tick
        boolean currentWandering = accessor.getWandering();
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
