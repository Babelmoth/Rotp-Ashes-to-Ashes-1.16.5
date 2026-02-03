package com.babelmoth.rotp_ata.init;

import java.util.function.Supplier;

import com.github.standobyte.jojo.init.ModSounds;
import com.github.standobyte.jojo.util.mc.OstSoundList;
import com.babelmoth.rotp_ata.AddonMain;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class InitSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(
            ForgeRegistries.SOUND_EVENTS, AddonMain.MOD_ID);
    
    // ======================================== Ashes to Ashes (灰烬归灰烬) ========================================
    
    public static final RegistryObject<SoundEvent> ASHES_TO_ASHES_SUMMON_VOICELINE = SOUNDS.register("ashes_to_ashes_summon_voiceline", 
            () -> new SoundEvent(new ResourceLocation(AddonMain.MOD_ID, "ashes_to_ashes_summon_voiceline")));

    public static final Supplier<SoundEvent> ASHES_TO_ASHES_SUMMON_SOUND = ModSounds.STAND_SUMMON_DEFAULT;
    
    public static final Supplier<SoundEvent> ASHES_TO_ASHES_UNSUMMON_SOUND = ModSounds.STAND_UNSUMMON_DEFAULT;
    
    // Removed punch sounds as no attacks exist
    
    // OST
    public static final OstSoundList ASHES_TO_ASHES_OST = new OstSoundList(
            new ResourceLocation(AddonMain.MOD_ID, "ashes_to_ashes_ost"), SOUNDS);
}
