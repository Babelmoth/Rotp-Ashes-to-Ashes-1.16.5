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

/**
 * 替身觉悟状态的火焰光环粒子。
 * 使用银色波纹纹理作为模板，通过 rCol/gCol/bCol 染上替身颜色。
 * 具有实体跟随效果和动画帧循环。
 */
@OnlyIn(Dist.CLIENT)
public class StandResolveAuraParticle extends SpriteTexturedParticle {
    private static final Random RANDOM = new Random();
    private static IAnimatedSprite savedSprites;

    private final IAnimatedSprite sprites;
    private final int startingSpriteRandom;
    private final LivingEntity followEntity;
    private Vector3d prevEntityPos;

    // Alpha 渐变参数（与RotP HamonAuraParticle完全一致）
    private static final float ALPHA_MIN = 0.05F;
    private static final float ALPHA_DIFF = 0.3F;

    // RisingParticle 上升速度（与RotP完全一致：0.004D）
    private static final double FALL_SPEED = 0.004D;

    // 实体体型缩放系数（影响上升速度）
    private final float entityScale;

    protected StandResolveAuraParticle(ClientWorld world, LivingEntity entity,
                                       double x, double y, double z,
                                       float r, float g, float b,
                                       IAnimatedSprite sprites, float entityScale) {
        // 使用7参数构造器获取随机初始速度（与RisingParticle完全一致）
        super(world, x, y, z, 0, 0, 0);
        this.sprites = sprites;
        this.followEntity = entity;
        this.prevEntityPos = entity != null ? entity.position() : new Vector3d(x, y, z);

        // 颜色归一化：将最大通道提升到1.0，保持色相比例不变
        // RotP的纹理本身最亮通道≈1.0，加法混合亮度高
        // 我们通过归一化使有效亮度与RotP一致
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

        // 初始速度乘以0.1（与RisingParticle完全一致：xd *= p8(0.1)）
        this.xd *= 0.1;
        this.yd *= 0.1;
        this.zd *= 0.1;

        // 体型缩放系数（仅影响上升速度和初始速度，不影响粒子大小和生命周期）
        this.entityScale = entityScale;

        // 初始速度按体型缩放（让粒子不会飘太远）
        this.xd *= entityScale;
        this.yd *= entityScale;
        this.zd *= entityScale;

        // 粒子大小（与RisingParticle完全一致：base(0.1) * 0.75 * scale(1.2~1.8)）
        float scale = 1.2F + 0.6F * RANDOM.nextFloat();
        this.quadSize *= 0.75F * scale;
        this.lifetime = 25 + RANDOM.nextInt(10);
        this.startingSpriteRandom = RANDOM.nextInt(this.lifetime);
        this.alpha = 0.25F;
        this.hasPhysics = false;

        // 初始帧
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

        // 动画帧循环（与RisingParticle一致：在move之前更新sprite）
        setSpriteFromAge(sprites);

        // 上升运动（与RisingParticle完全一致，按体型缩放）
        this.yd += FALL_SPEED * entityScale;
        this.move(this.xd, this.yd, this.zd);

        // 碰到天花板时横向扩散（与RisingParticle完全一致）
        if (this.y == this.yo) {
            this.xd *= 1.1;
            this.zd *= 1.1;
        }

        // 阻力（与RisingParticle完全一致：0.96）
        this.xd *= 0.96;
        this.yd *= 0.96;
        this.zd *= 0.96;

        // 地面摩擦（与RisingParticle完全一致）
        if (this.onGround) {
            this.xd *= 0.7;
            this.zd *= 0.7;
        }

        // 实体跟随（与RotP HamonAura3PersonParticle完全一致：在super.tick()后调用move()）
        if (followEntity != null && followEntity.isAlive()) {
            Vector3d currentPos = followEntity.position();
            Vector3d offset = currentPos.subtract(prevEntityPos);
            this.move(offset.x, offset.y, offset.z);
            this.prevEntityPos = currentPos;
        }
    }

    /**
     * 粒子大小渐变（与RisingParticle.getQuadSize完全一致）
     * 从0快速渐变到满大小（lifetime的前1/32内完成）
     */
    @Override
    public float getQuadSize(float partialTick) {
        return this.quadSize * MathHelper.clamp(((float) this.age + partialTick) / (float) this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    @Override
    public void render(IVertexBuilder vertexBuilder, ActiveRenderInfo camera, float partialTick) {
        // 第一人称视角下不渲染自身粒子（与RotP HamonAura3PersonParticle一致）
        if (followEntity != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.cameraEntity == followEntity && mc.options.getCameraType() == PointOfView.FIRST_PERSON) {
                return;
            }
        }

        // Alpha 渐变：先升后降（与RotP HamonAuraParticle完全一致）
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

    /**
     * 工厂方法：在指定位置为指定实体生成觉悟光环粒子（玩家用，默认缩放）。
     */
    public static StandResolveAuraParticle create(ClientWorld world, LivingEntity entity,
                                                   double x, double y, double z,
                                                   int standColor, IAnimatedSprite sprites) {
        return create(world, entity, x, y, z, standColor, sprites, 1.0F);
    }

    /**
     * 工厂方法：在指定位置为指定实体生成觉悟光环粒子，带体型缩放。
     */
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
