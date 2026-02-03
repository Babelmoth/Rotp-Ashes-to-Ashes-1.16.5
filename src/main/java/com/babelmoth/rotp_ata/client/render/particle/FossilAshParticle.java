package com.babelmoth.rotp_ata.client.render.particle;

import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particles.BasicParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FossilAshParticle extends SpriteTexturedParticle {
    
    private final double targetX, targetY, targetZ;
    private final boolean shouldGather; // true = 聚集模式, false = 扩散模式(下落)
    
    protected FossilAshParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, boolean gather) {
        super(world, x, y, z, vx, vy, vz);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.shouldGather = gather;
        
        // 记录目标位置（用于聚集模式）
        if (gather) {
            // 目标是当前位置 + 速度方向 * 一个估计时间
            this.targetX = x + vx * 8;
            this.targetY = y + vy * 8;
            this.targetZ = z + vz * 8;
        } else {
            this.targetX = x;
            this.targetY = y;
            this.targetZ = z;
        }
        
        // 灰褐色 - 增加变化范围
        float colorVariation = this.random.nextFloat() * 0.2f - 0.1f; // -0.1 to 0.1
        this.rCol = 0.54f + colorVariation + this.random.nextFloat() * 0.15f;
        this.gCol = 0.45f + colorVariation + this.random.nextFloat() * 0.12f;
        this.bCol = 0.33f + colorVariation + this.random.nextFloat() * 0.1f;
        
        this.lifetime = gather ? 12 : 35; // 聚集粒子较短寿命，下落粒子较长
        this.quadSize = 0.06f + this.random.nextFloat() * 0.06f; // 更大的尺寸变化 0.06-0.12
        this.gravity = gather ? 0 : 0.012f; // 聚集无重力，扩散有较轻重力
        this.hasPhysics = !gather; // 扩散粒子与方块碰撞
    }
    
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            if (shouldGather) {
                // 聚集模式：向目标移动，接近后停止
                double dx = this.targetX - this.x;
                double dy = this.targetY - this.y;
                double dz = this.targetZ - this.z;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                
                if (dist > 0.05) {
                    // 向目标移动
                    this.move(this.xd, this.yd, this.zd);
                    // 逐渐减速
                    this.xd *= 0.85;
                    this.yd *= 0.85;
                    this.zd *= 0.85;
                }
                // 接近目标后停滞（不移动）
            } else {
                // 扩散模式：正常物理下落
                this.yd -= this.gravity;
                this.move(this.xd, this.yd, this.zd);
                this.xd *= 0.95;
                this.yd *= 0.95;
                this.zd *= 0.95;
            }
            
            // 渐隐
            this.alpha = 1.0f - ((float)this.age / (float)this.lifetime) * 0.7f;
        }
    }
    
    @Override
    public IParticleRenderType getRenderType() {
        return IParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
    
    @OnlyIn(Dist.CLIENT)
    public static class Factory implements IParticleFactory<BasicParticleType> {
        private final IAnimatedSprite sprites;
        
        public Factory(IAnimatedSprite sprites) {
            this.sprites = sprites;
        }
        
        @Override
        public Particle createParticle(BasicParticleType type, ClientWorld world, double x, double y, double z, double vx, double vy, double vz) {
            // 根据粒子类型判断模式
            boolean gather = type == com.babelmoth.rotp_ata.init.InitParticles.FOSSIL_ASH_GATHER.get();
            FossilAshParticle particle = new FossilAshParticle(world, x, y, z, vx, vy, vz, gather);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
