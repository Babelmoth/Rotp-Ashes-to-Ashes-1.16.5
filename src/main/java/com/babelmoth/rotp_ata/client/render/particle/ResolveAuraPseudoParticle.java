package com.babelmoth.rotp_ata.client.render.particle;

import com.github.standobyte.jojo.client.particle.custom.FirstPersonHamonAura;

import net.minecraft.client.particle.IAnimatedSprite;
import net.minecraft.client.particle.IParticleRenderType;
import net.minecraft.util.HandSide;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ResolveAuraPseudoParticle extends FirstPersonHamonAura.HamonAuraPseudoParticle {

    public ResolveAuraPseudoParticle(double x, double y, double z,
                                      IAnimatedSprite sprites, HandSide handSide,
                                      float r, float g, float b) {
        super(x, y, z, sprites, handSide);

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
    }

    @Override
    public IParticleRenderType getRenderType() {
        return ResolveAuraRenderType.INSTANCE;
    }
}
