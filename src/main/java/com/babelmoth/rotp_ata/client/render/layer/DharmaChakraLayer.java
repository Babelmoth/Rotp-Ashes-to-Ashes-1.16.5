package com.babelmoth.rotp_ata.client.render.layer;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.client.render.DharmaChakraRenderTypes;
import com.babelmoth.rotp_ata.init.InitStands;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.IEntityRenderer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DharmaChakraLayer extends LayerRenderer<AbstractClientPlayerEntity, PlayerModel<AbstractClientPlayerEntity>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(AddonMain.MOD_ID, "textures/entity/dharma_chakra.png");
    private final DharmaChakraWheelModel wheelModel;

    private static final float SUMMON_ANIMATION_DURATION = 6.0F;

    private static final float MOVE_LAG_MAX_OFFSET = 0.26F;
    private static final float MOVE_LAG_FACTOR = 1.55F;
    private static final float MOVE_LAG_BASE_SMOOTHING = 0.22F;
    private static final float MOVE_LAG_FAST_SMOOTHING = 0.34F;
    private static final float MOVE_LAG_RETURN_SMOOTHING = 0.18F;
    private static final float MOVE_LAG_DEADZONE = 0.0035F;

    private static final float ROTATION_STEP_DEGREES = 45.0F;
    private static final int ROTATION_PRE_PAUSE_TICKS = 10;
    private static final float STUTTER_FORWARD_DEGREES = 10.0F;
    private static final float STUTTER_BACK_DEGREES = 4.0F;
    private static final int STUTTER_TICKS_PER = 4;
    private static final int STUTTER_COUNT = 2;
    private static final float SPIN_COUNT = 1.0F;
    private static final int SPIN_TICKS = 18;
    private static final int SNAP_TICKS = 2;
    private static final float SNAP_PREP_DEGREES = 12.0F;

    private static final Map<UUID, AnimationState> animationStates = new HashMap<>();

    public DharmaChakraLayer(IEntityRenderer<AbstractClientPlayerEntity, PlayerModel<AbstractClientPlayerEntity>> renderer) {
        super(renderer);
        this.wheelModel = new DharmaChakraWheelModel();
    }

    public static void triggerRotation(UUID playerId) {
        AnimationState state = animationStates.computeIfAbsent(playerId, k -> new AnimationState());
        state.triggerRotation();
    }

    @Override
    public void render(MatrixStack matrixStack, IRenderTypeBuffer buffer, int packedLight,
            AbstractClientPlayerEntity player, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        IStandPower standPower = IStandPower.getStandPowerOptional(player).orElse(null);
        if (standPower == null || !standPower.hasPower()) {
            return;
        }
        if (standPower.getType() != InitStands.STAND_DHARMA_CHAKRA.getStandType()) {
            return;
        }

        boolean isStandActive = standPower.isActive();
        UUID playerId = player.getUUID();
        AnimationState state = animationStates.computeIfAbsent(playerId, k -> new AnimationState());
        state.update(isStandActive, player);

        float animProgress = state.getSummonProgress(partialTick);
        if (animProgress <= 0.0F) {
            return;
        }

        float offsetX = state.getOffsetX(partialTick);
        float offsetZ = state.getOffsetZ(partialTick);
        float wheelYaw = state.getWheelYaw(partialTick);
        float glow = state.getGlow(partialTick);
        renderWheel(matrixStack, buffer, player, animProgress, offsetX, offsetZ, wheelYaw, glow);
    }

    private void renderWheel(MatrixStack matrixStack, IRenderTypeBuffer buffer,
            AbstractClientPlayerEntity player, float animProgress, float offsetX, float offsetZ, float wheelYaw, float glow) {

        matrixStack.pushPose();
        matrixStack.translate(0.0D, -3.0D, 0.0D);
        matrixStack.translate(offsetX, 0.0D, offsetZ);

        float easedProgress = easeOutBack(animProgress);
        float scale = easedProgress;
        matrixStack.scale(scale, scale, scale);

        matrixStack.mulPose(Vector3f.YP.rotationDegrees(wheelYaw));

        float alpha = MathHelper.clamp(animProgress * 1.5F, 0.0F, 1.0F);
        int fullBrightLight = 15728880;

        RenderType mainRenderType = DharmaChakraRenderTypes.dharmaChakraMain(TEXTURE);
        IVertexBuilder mainBuilder = buffer.getBuffer(mainRenderType);
        wheelModel.renderWheelOnly(matrixStack, mainBuilder, fullBrightLight, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, alpha);

        RenderType outlineRenderType = DharmaChakraRenderTypes.dharmaChakraOutline(TEXTURE);
        IVertexBuilder outlineBuilder = buffer.getBuffer(outlineRenderType);
        wheelModel.renderBoneOnly(matrixStack, outlineBuilder, fullBrightLight, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, alpha);

        if (glow > 0.001F) {
            RenderType glowType = RenderType.eyes(TEXTURE);
            IVertexBuilder glowBuilder = buffer.getBuffer(glowType);
            float glowAlpha = MathHelper.clamp(alpha * glow, 0.0F, 1.0F);
            wheelModel.renderWheelOnly(matrixStack, glowBuilder, fullBrightLight, OverlayTexture.NO_OVERLAY,
                    1.0F, 1.0F, 1.0F, glowAlpha);
        }

        matrixStack.popPose();
    }

    private float easeOutBack(float t) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        return 1.0F + c3 * (float) Math.pow(t - 1.0F, 3) + c1 * (float) Math.pow(t - 1.0F, 2);
    }

    private static class AnimationState {
        private float summonProgress = 0.0F;
        private float lastSummonProgress = 0.0F;

        private float offsetX = 0.0F;
        private float offsetZ = 0.0F;
        private float lastOffsetX = 0.0F;
        private float lastOffsetZ = 0.0F;

        private float wheelYaw = 0.0F;
        private float lastWheelYaw = 0.0F;

        private float glow = 0.0F;
        private float lastGlow = 0.0F;

        private double prevPosX = Double.NaN;
        private double prevPosZ = Double.NaN;
        private float smoothedVelX = 0.0F;
        private float smoothedVelZ = 0.0F;

        private int lastUpdateTick = Integer.MIN_VALUE;
        private int pendingRotations = 0;

        private int rotationPhase = 0;
        private int phaseTick = 0;
        private float rotationStartYaw = 0.0F;
        private float spinStartYaw = 0.0F;
        private float preSnapYaw = 0.0F;
        private float rotationTargetYaw = 0.0F;
        private float snapStartYaw = 0.0F;
        private boolean effectsTriggered = false;

        public void update(boolean isActive, AbstractClientPlayerEntity player) {
            if (player.tickCount == lastUpdateTick) {
                return;
            }
            lastUpdateTick = player.tickCount;

            lastSummonProgress = summonProgress;
            if (isActive) {
                summonProgress = Math.min(1.0F, summonProgress + 1.0F / SUMMON_ANIMATION_DURATION);
            } else {
                summonProgress = Math.max(0.0F, summonProgress - 1.0F / SUMMON_ANIMATION_DURATION);
                pendingRotations = 0;
            }

            lastOffsetX = offsetX;
            lastOffsetZ = offsetZ;
            updateMoveLagOffset(player);

            lastWheelYaw = wheelYaw;
            lastGlow = glow;
            updateWheelRotation();
        }

        private void updateMoveLagOffset(AbstractClientPlayerEntity player) {
            if (Double.isNaN(prevPosX) || Double.isNaN(prevPosZ)) {
                prevPosX = player.getX();
                prevPosZ = player.getZ();
            }

            double rawVelX = player.getX() - prevPosX;
            double rawVelZ = player.getZ() - prevPosZ;
            prevPosX = player.getX();
            prevPosZ = player.getZ();

            if (player == net.minecraft.client.Minecraft.getInstance().player) {
                Vector3d localVel = player.getDeltaMovement();
                rawVelX = MathHelper.lerp(0.5D, rawVelX, localVel.x);
                rawVelZ = MathHelper.lerp(0.5D, rawVelZ, localVel.z);
            }

            smoothedVelX = MathHelper.lerp(0.35F, smoothedVelX, (float) rawVelX);
            smoothedVelZ = MathHelper.lerp(0.35F, smoothedVelZ, (float) rawVelZ);

            float yawRad = (float) Math.toRadians(player.yBodyRot);
            double forward = smoothedVelX * (-Math.sin(yawRad)) + smoothedVelZ * (Math.cos(yawRad));
            double side = smoothedVelX * (Math.cos(yawRad)) + smoothedVelZ * (Math.sin(yawRad));

            float targetX = (float) MathHelper.clamp(side * MOVE_LAG_FACTOR, -MOVE_LAG_MAX_OFFSET, MOVE_LAG_MAX_OFFSET);
            float targetZ = (float) MathHelper.clamp(forward * MOVE_LAG_FACTOR, -MOVE_LAG_MAX_OFFSET, MOVE_LAG_MAX_OFFSET);

            float delta = Math.abs(targetX - offsetX) + Math.abs(targetZ - offsetZ);
            float smoothing = MOVE_LAG_BASE_SMOOTHING;
            if (delta > 0.09F) {
                smoothing = MOVE_LAG_FAST_SMOOTHING;
            }
            if (Math.abs(targetX) + Math.abs(targetZ) < 0.02F) {
                smoothing = MOVE_LAG_RETURN_SMOOTHING;
            }

            offsetX = MathHelper.lerp(smoothing, offsetX, targetX);
            offsetZ = MathHelper.lerp(smoothing, offsetZ, targetZ);
            if (Math.abs(offsetX) < MOVE_LAG_DEADZONE) {
                offsetX = 0.0F;
            }
            if (Math.abs(offsetZ) < MOVE_LAG_DEADZONE) {
                offsetZ = 0.0F;
            }
        }

        private void updateWheelRotation() {
            if (rotationPhase == 0) {
                glow = MathHelper.lerp(0.25F, glow, 0.0F);
                if (pendingRotations > 0) {
                    pendingRotations--;
                    beginRotation();
                }
                return;
            }

            phaseTick++;

            if (rotationPhase == 1) {
                wheelYaw = rotationStartYaw;
                glow = 0.0F;
                if (phaseTick >= ROTATION_PRE_PAUSE_TICKS) {
                    rotationPhase = 2;
                    phaseTick = 0;
                }
                return;
            }

            if (rotationPhase == 2) {
                int totalTicks = STUTTER_TICKS_PER * STUTTER_COUNT;
                int cycle = Math.min(STUTTER_COUNT - 1, (phaseTick - 1) / STUTTER_TICKS_PER);
                int local = (phaseTick - 1) % STUTTER_TICKS_PER;

                float cycleBase = rotationStartYaw + cycle * (STUTTER_FORWARD_DEGREES - STUTTER_BACK_DEGREES);
                float stutterYaw;
                if (local == 0) {
                    stutterYaw = cycleBase;
                } else if (local == 1) {
                    stutterYaw = cycleBase + STUTTER_FORWARD_DEGREES;
                } else if (local == 2) {
                    stutterYaw = cycleBase + STUTTER_FORWARD_DEGREES;
                } else {
                    stutterYaw = cycleBase + (STUTTER_FORWARD_DEGREES - STUTTER_BACK_DEGREES);
                }
                wheelYaw = stutterYaw;

                if (phaseTick >= totalTicks) {
                    rotationPhase = 3;
                    phaseTick = 0;
                    spinStartYaw = wheelYaw;
                    effectsTriggered = false;
                }
                return;
            }

            if (rotationPhase == 3) {
                if (!effectsTriggered) {
                    effectsTriggered = true;
                    com.babelmoth.rotp_ata.networking.DharmaChakraPacketManager.sendToServer(
                        new com.babelmoth.rotp_ata.networking.C2SAdaptationEffectsPacket());
                }
                
                float t = MathHelper.clamp((float) phaseTick / (float) SPIN_TICKS, 0.0F, 1.0F);
                float eased = easeInOutSine(t);
                wheelYaw = MathHelper.lerp(eased, spinStartYaw, preSnapYaw);

                glow = Math.max(glow, (float) Math.sin(Math.PI * t));

                if (phaseTick >= SPIN_TICKS) {
                    rotationPhase = 4;
                    phaseTick = 0;
                    snapStartYaw = wheelYaw;
                }
                return;
            }

            if (rotationPhase == 4) {
                float t = MathHelper.clamp((float) phaseTick / (float) SNAP_TICKS, 0.0F, 1.0F);
                float eased = easeOutQuint(t);
                wheelYaw = MathHelper.lerp(eased, snapStartYaw, rotationTargetYaw);
                glow = MathHelper.lerp(0.35F, glow, 0.0F);

                if (phaseTick >= SNAP_TICKS) {
                    wheelYaw = normalizeYaw(rotationTargetYaw);
                    rotationPhase = 0;
                    phaseTick = 0;
                    glow = 0.0F;
                }
            }
        }

        public void triggerRotation() {
            if (pendingRotations < 4) {
                pendingRotations++;
            }
            if (rotationPhase == 0) {
                pendingRotations--;
                beginRotation();
            }
        }

        private void beginRotation() {
            rotationStartYaw = wheelYaw;
            rotationTargetYaw = rotationStartYaw + SPIN_COUNT * 360.0F;
            preSnapYaw = rotationTargetYaw - SNAP_PREP_DEGREES;

            rotationPhase = 1;
            phaseTick = 0;
            glow = 0.0F;
            lastGlow = 0.0F;
        }

        public float getSummonProgress(float partialTick) {
            return MathHelper.lerp(partialTick, lastSummonProgress, summonProgress);
        }

        public float getOffsetX(float partialTick) {
            return MathHelper.lerp(partialTick, lastOffsetX, offsetX);
        }

        public float getOffsetZ(float partialTick) {
            return MathHelper.lerp(partialTick, lastOffsetZ, offsetZ);
        }

        public float getWheelYaw(float partialTick) {
            return MathHelper.lerp(partialTick, lastWheelYaw, wheelYaw);
        }

        public float getGlow(float partialTick) {
            return MathHelper.lerp(partialTick, lastGlow, glow);
        }

        private float normalizeYaw(float yaw) {
            float y = yaw % 360.0F;
            if (y < 0.0F) y += 360.0F;
            return y;
        }

        private float easeInOutCubic(float t) {
            return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0F) / 2.0F;
        }

        private float easeInOutSine(float t) {
            return (float) (-(Math.cos(Math.PI * t) - 1.0D) / 2.0D);
        }

        private float easeOutQuint(float t) {
            return 1.0F - (float) Math.pow(1.0F - t, 5.0F);
        }
    }
}
