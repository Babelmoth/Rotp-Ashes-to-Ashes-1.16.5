package com.babelmoth.rotp_ata.adaptation;

import net.minecraftforge.event.entity.living.LivingHurtEvent;

public interface IAdaptationStrategy {
    void apply(LivingHurtEvent event);
    String getId();
}
