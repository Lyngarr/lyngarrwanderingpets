package com.lyngarr.wanderingpets.mixin;

import com.lyngarr.wanderingpets.LyngarrWanderingPets;
import com.lyngarr.wanderingpets.util.WanderingAccessor;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public class MobEntityMixin {

    @Unique
    private static final int HOME_RADIUS = 32;

    @Unique
    private static final int HOME_RADIUS_SQUARED = HOME_RADIUS * HOME_RADIUS;

    @Unique
    private static final int HOME_RETURN_RADIUS_SQUARED = 20 * 20;

    @Shadow
    protected net.minecraft.world.entity.ai.goal.GoalSelector goalSelector;

    @Unique
    private int freezeTicks = 0;

    @Unique
    private Boolean lastWanderingState = null;

    @Unique
    private static void sendDebugToOwner(TamableAnimal tameable, String message) {
        if (!LyngarrWanderingPets.DEBUG_MODE) {
            return;
        }

        if (tameable.getOwner() instanceof Player owner) {
            owner.sendSystemMessage(Component.literal("[WP DEBUG] " + message));
        }
    }

    @Unique
    private static class ReturnToHomeGoal extends Goal {
        private final PathfinderMob entity;
        private final WanderingAccessor accessor;
        private final double speed;
        private BlockPos targetHome;

        ReturnToHomeGoal(PathfinderMob entity, double speed) {
            this.entity = entity;
            this.accessor = (WanderingAccessor) entity;
            this.speed = speed;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!accessor.getWandering() || !accessor.hasHomePos()) {
                return false;
            }

            BlockPos home = accessor.getHomePos();
            if (home == null) {
                return false;
            }

            double distanceSquared = entity.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
            if (distanceSquared <= HOME_RADIUS_SQUARED) {
                return false;
            }

            targetHome = home;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            if (targetHome == null || !accessor.getWandering()) {
                return false;
            }

            double distanceSquared = entity.distanceToSqr(targetHome.getX() + 0.5, targetHome.getY(), targetHome.getZ() + 0.5);
            return distanceSquared > HOME_RETURN_RADIUS_SQUARED;
        }

        @Override
        public void start() {
            if (entity instanceof TamableAnimal tameable && targetHome != null) {
                sendDebugToOwner(tameable, tameable.getName().getString()
                        + " return start | home: " + targetHome.getX() + ", " + targetHome.getY() + ", " + targetHome.getZ());
            }
            moveToHome();
        }

        @Override
        public void tick() {
            if (targetHome != null && (entity.getNavigation().isDone() || entity.tickCount % 20 == 0)) {
                moveToHome();
            }
        }

        @Override
        public void stop() {
            if (entity instanceof TamableAnimal tameable && targetHome != null) {
                sendDebugToOwner(tameable, tameable.getName().getString()
                        + " return stop | near home: " + targetHome.getX() + ", " + targetHome.getY() + ", " + targetHome.getZ());
            }
            targetHome = null;
            entity.getNavigation().stop();
        }

        private void moveToHome() {
            if (targetHome != null) {
                entity.getNavigation().moveTo(targetHome.getX() + 0.5, targetHome.getY(), targetHome.getZ() + 0.5, speed);
            }
        }
    }

    @Unique
    private void syncWanderingGoals(boolean isWandering) {
        Mob self = (Mob) (Object) this;

        if (self instanceof TamableAnimal tameable && tameable.isTame()) {
            if (isWandering) {
                // Remove follow goal and add wander goal
                goalSelector.getAvailableGoals().removeIf(goal -> goal.getGoal() instanceof FollowOwnerGoal);

                // Check if wander goal already exists (only check once during state change)
                boolean hasWanderGoal = goalSelector.getAvailableGoals().stream()
                        .anyMatch(goal -> goal.getGoal() instanceof WaterAvoidingRandomStrollGoal);
                boolean hasReturnGoal = goalSelector.getAvailableGoals().stream()
                        .anyMatch(goal -> goal.getGoal() instanceof ReturnToHomeGoal);

                if (!hasWanderGoal) {
                    goalSelector.addGoal(10, new WaterAvoidingRandomStrollGoal((PathfinderMob) tameable, 1.0));
                }
                if (!hasReturnGoal) {
                    goalSelector.addGoal(2, new ReturnToHomeGoal((PathfinderMob) tameable, 1.1));
                }

                BlockPos home = ((WanderingAccessor) this).getHomePos();
                sendDebugToOwner(tameable, tameable.getName().getString()
                    + " wandering goals | wander=" + (!hasWanderGoal ? "ADDED" : "PRESENT")
                    + " return=" + (!hasReturnGoal ? "ADDED" : "PRESENT")
                    + " | home: " + home.getX() + ", " + home.getY() + ", " + home.getZ());
            } else {
                // Remove wander/home goals and add follow goal
                goalSelector.getAvailableGoals().removeIf(goal -> goal.getGoal() instanceof WaterAvoidingRandomStrollGoal || goal.getGoal() instanceof ReturnToHomeGoal);

                // Check if follow goal already exists (only check once during state change)
                boolean hasFollowGoal = goalSelector.getAvailableGoals().stream()
                        .anyMatch(goal -> goal.getGoal() instanceof FollowOwnerGoal);

                if (!hasFollowGoal && !tameable.isOrderedToSit()) {
                    goalSelector.addGoal(1, new FollowOwnerGoal(tameable, 1.0, 10.0F, 2.0F));
                }

                sendDebugToOwner(tameable, tameable.getName().getString()
                    + " following goals | follow=" + (!hasFollowGoal ? "ADDED" : "PRESENT"));
            }
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void onReadCustomData(ValueInput view, CallbackInfo ci) {
        boolean isWandering = view.getBooleanOr("WanderingPets_isWandering", false);
        if (isWandering) {
            freezeTicks = 1;
        }
    }


    @Inject(method = "aiStep", at = @At("HEAD"))
    private void onTickMovement(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        WanderingAccessor accessor = (WanderingAccessor) this;

        if (self instanceof TamableAnimal tameable && tameable.isTame() && accessor.getWandering() && !accessor.hasHomePos()) {
            accessor.setHomePos(self.blockPosition());
            sendDebugToOwner(tameable, tameable.getName().getString()
                + " fallback home set to: " + self.blockPosition().getX() + ", " + self.blockPosition().getY() + ", " + self.blockPosition().getZ());
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
            self.setNoAi(true);
        } else if (self.isNoAi()) {
            self.setNoAi(false);
        }
    }
}
