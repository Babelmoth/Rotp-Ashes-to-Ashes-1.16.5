package com.babelmoth.rotp_ata.client.render.particle;

import java.util.Random;

import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.IAnimatedSprite;
import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.client.particle.IParticleRenderType;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteTexturedParticle;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.client.settings.PointOfView;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class StandResolveAuraParticle extends SpriteTexturedParticle {
    private static final Random RANDOM = new Random();
    private static IAnimatedSprite savedSprites;

    private final IAnimatedSprite sprites;
    private final int startingSpriteRandom;
    private final LivingEntity followEntity;
    private Vector3d prevEntityPos;

    private static final float ALPHA_MIN = 0.05F;
    private static final float ALPHA_DIFF = 0.3F;

    private static final double FALL_SPEED = 0.004D;

    private final float entityScale;

    protected StandResolveAuraParticle(ClientWorld world, LivingEntity entity,
                                       double x, double y, double z,
                                       float r, float g, float b,
                                       IAnimatedSprite sprites, float entityScale) {

        super(world, x, y, z, 0, 0, 0);
        this.sprites = sprites;
        this.followEntity = entity;
        this.prevEntityPos = entity != null ? entity.position() : new Vector3d(x, y, z);

        float maxChannel = Math.max(r, Math.max(g, b));
        if (maxChannel > 0) {
            this.rCol = r / maxChannel;
            this.gCol = g / maxChannel;
            this.bCol = b / maxChannel;
        } else {
            this.rCol = 1.0F;
            this.gCol = 1.0F;
            this.bCol = 1.0F;
        }

        this.xd *= 0.1;
        this.yd *= 0.1;
        this.zd *= 0.1;

        this.entityScale = entityScale;

        this.xd *= entityScale;
        this.yd *= entityScale;
        this.zd *= entityScale;

        float scale = 1.2F + 0.6F * RANDOM.nextFloat();
        this.quadSize *= 0.75F * scale;
        this.lifetime = 25 + RANDOM.nextInt(10);
        this.startingSpriteRandom = RANDOM.nextInt(this.lifetime);
        this.alpha = 0.25F;
        this.hasPhysics = false;

        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        setSpriteFromAge(sprites);

        this.yd += FALL_SPEED * entityScale;
        this.move(this.xd, this.yd, this.zd);

        if (this.y == this.yo) {
            this.xd *= 1.1;
            this.zd *= 1.1;
        }

        this.xd *= 0.96;
        this.yd *= 0.96;
        this.zd *= 0.96;

        if (this.onGround) {
            this.xd *= 0.7;
            this.zd *= 0.7;
        }

        if (followEntity != null && followEntity.isAlive()) {
            Vector3d currentPos = followEntity.position();
            Vector3d offset = currentPos.subtract(prevEntityPos);
            this.move(offset.x, offset.y, offset.z);
            this.prevEntityPos = currentPos;
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        return this.quadSize * MathHelper.clamp(((float) this.age + partialTick) / (float) this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    @Override
    public void render(IVertexBuilder vertexBuilder, ActiveRenderInfo camera, float partialTick) {

        if (followEntity != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.cameraEntity == followEntity && mc.options.getCameraType() == PointOfView.FIRST_PERSON) {
                return;
            }
        }

        float ageF = ((float) age + partialTick) / (float) lifetime;
        float alphaFunc = ageF <= 0.5F ? ageF * 2 : (1 - ageF) * 2;
        this.alpha = ALPHA_MIN + alphaFunc * ALPHA_DIFF;
        super.render(vertexBuilder, camera, partialTick);
    }

    public void setSpriteFromAge(IAnimatedSprite pSprite) {
        if (!this.removed) {
            setSprite(pSprite.get((age + startingSpriteRandom) % lifetime, lifetime));
        }
    }

    @Override
    public IParticleRenderType getRenderType() {
        return ResolveAuraRenderType.INSTANCE;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    public static StandResolveAuraParticle create(ClientWorld world, LivingEntity entity,
                                                   double x, double y, double z,
                                                   int standColor, IAnimatedSprite sprites) {
        return create(world, entity, x, y, z, standColor, sprites, 1.0F);
    }

    public static StandResolveAuraParticle create(ClientWorld world, LivingEntity entity,
                                                   double x, double y, double z,
                                                   int standColor, IAnimatedSprite sprites,
                                                   float entityScale) {
        float r = ((standColor >> 16) & 0xFF) / 255.0F;
        float g = ((standColor >> 8) & 0xFF) / 255.0F;
        float b = (standColor & 0xFF) / 255.0F;
        return new StandResolveAuraParticle(world, entity, x, y, z, r, g, b, sprites, entityScale);
    }

    public static IAnimatedSprite getSavedSprites() {
        return savedSprites;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Factory implements IParticleFactory<BasicParticleType> {
        private final IAnimatedSprite sprites;

        public Factory(IAnimatedSprite sprites) {
            this.sprites = sprites;
            savedSprites = sprites;
        }

        @Override
        public Particle createParticle(BasicParticleType type, ClientWorld world,
                                       double x, double y, double z,
                                       double vx, double vy, double vz) {
            float r = (float) Math.max(0, Math.min(1, vx));
            float g = (float) Math.max(0, Math.min(1, vy));
            float b = (float) Math.max(0, Math.min(1, vz));
            return new StandResolveAuraParticle(world, null, x, y, z, r, g, b, sprites, 1.0F);
        }
    }
}
