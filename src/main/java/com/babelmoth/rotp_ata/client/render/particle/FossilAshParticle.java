package com.babelmoth.rotp_ata.client.render.particle;

import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particles.BasicParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FossilAshParticle extends SpriteTexturedParticle {
    
    private final double targetX, targetY, targetZ;
    private final boolean shouldGather; // true = gather toward target, false = spread/fall
    
    protected FossilAshParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, boolean gather) {
        super(world, x, y, z, vx, vy, vz);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.shouldGather = gather;
        
        if (gather) {
            // Target = current position + velocity * estimated time
            this.targetX = x + vx * 8;
            this.targetY = y + vy * 8;
            this.targetZ = z + vz * 8;
        } else {
            this.targetX = x;
            this.targetY = y;
            this.targetZ = z;
        }
        
        // Ash brown with variation
        float colorVariation = this.random.nextFloat() * 0.2f - 0.1f;
        this.rCol = 0.54f + colorVariation + this.random.nextFloat() * 0.15f;
        this.gCol = 0.45f + colorVariation + this.random.nextFloat() * 0.12f;
        this.bCol = 0.33f + colorVariation + this.random.nextFloat() * 0.1f;
        
        this.lifetime = gather ? 12 : 35;
        this.quadSize = 0.06f + this.random.nextFloat() * 0.06f;
        this.gravity = gather ? 0 : 0.012f;
        this.hasPhysics = !gather;
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
                double dx = this.targetX - this.x;
                double dy = this.targetY - this.y;
                double dz = this.targetZ - this.z;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (dist > 0.05) {
                    this.move(this.xd, this.yd, this.zd);
                    this.xd *= 0.85;
                    this.yd *= 0.85;
                    this.zd *= 0.85;
                }
            } else {
                this.yd -= this.gravity;
                this.move(this.xd, this.yd, this.zd);
                this.xd *= 0.95;
                this.yd *= 0.95;
                this.zd *= 0.95;
            }
            
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
            boolean gather = type == com.babelmoth.rotp_ata.init.InitParticles.FOSSIL_ASH_GATHER.get();
            FossilAshParticle particle = new FossilAshParticle(world, x, y, z, vx, vy, vz, gather);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
