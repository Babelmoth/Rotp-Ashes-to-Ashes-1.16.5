package com.babelmoth.rotp_ata.client.render.particle;

import net.minecraft.client.particle.IAnimatedSprite;
import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.client.particle.IParticleRenderType;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteTexturedParticle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particles.BasicParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AshSmokeLargeParticle extends SpriteTexturedParticle {

    protected AshSmokeLargeParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz) {
        super(world, x, y, z, 0, 0, 0);
        this.scale(25.0F); // Very large smoke bomb particles
        this.setSize(0.5F, 0.5F);
        this.lifetime = this.random.nextInt(100) + 200; // 10-15 seconds
        this.gravity = 0;
        this.hasPhysics = false;
        
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        
        // Gray/ash color (original)
        float gray = 0.4f + this.random.nextFloat() * 0.2f;
        this.rCol = gray;
        this.gCol = gray;
        this.bCol = gray;
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ < this.lifetime) {
            // Very gentle random drift
            this.xd += (double)(this.random.nextFloat() / 10000.0F * (float)(this.random.nextBoolean() ? 1 : -1));
            this.zd += (double)(this.random.nextFloat() / 10000.0F * (float)(this.random.nextBoolean() ? 1 : -1));
            this.move(this.xd, this.yd, this.zd);
            // No fade - instant disappear when lifetime ends
        } else {
            this.remove();
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
            AshSmokeLargeParticle particle = new AshSmokeLargeParticle(world, x, y, z, vx, vy, vz);
            particle.setAlpha(0.9F);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
