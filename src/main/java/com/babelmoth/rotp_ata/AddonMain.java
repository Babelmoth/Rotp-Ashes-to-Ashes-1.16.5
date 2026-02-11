package com.babelmoth.rotp_ata;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.init.InitEntities;
import com.babelmoth.rotp_ata.init.InitSounds;
import com.babelmoth.rotp_ata.init.InitStands;

import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import software.bernie.geckolib3.GeckoLib;

// Your addon's main file

@Mod(AddonMain.MOD_ID)
public class AddonMain {
    // The mod's id. Used quite often, mostly when creating ResourceLocation (objects).
    // Its value should match the "modid" entry in the META-INF/mods.toml file
    public static final String MOD_ID = "rotp_ata";
    public static final Logger LOGGER = LogManager.getLogger();

    public AddonMain() {
        GeckoLib.initialize();
        com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler.register();
        
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // All DeferredRegister objects are registered here.
        // A DeferredRegister needs to be created for each type of objects that need to be registered in the game 
        // (see ForgeRegistries or JojoCustomRegistries)
        InitEntities.ENTITIES.register(modEventBus);
        InitSounds.SOUNDS.register(modEventBus);
        InitStands.ACTIONS.register(modEventBus);
        InitStands.STANDS.register(modEventBus);
        com.babelmoth.rotp_ata.init.InitParticles.PARTICLES.register(modEventBus);
        com.babelmoth.rotp_ata.init.InitBlocks.BLOCKS.register(modEventBus);
        com.babelmoth.rotp_ata.init.InitBlocks.TILE_ENTITIES.register(modEventBus);
        
        modEventBus.addListener(this::onEntityAttributeCreation);
        modEventBus.addListener(this::setup);
    }
    
    private void setup(final net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        net.minecraftforge.common.capabilities.CapabilityManager.INSTANCE.register(
            com.babelmoth.rotp_ata.capability.IMothPool.class,
            new net.minecraftforge.common.capabilities.Capability.IStorage<com.babelmoth.rotp_ata.capability.IMothPool>() {
                @Override
                public net.minecraft.nbt.CompoundNBT writeNBT(net.minecraftforge.common.capabilities.Capability<com.babelmoth.rotp_ata.capability.IMothPool> capability, com.babelmoth.rotp_ata.capability.IMothPool instance, net.minecraft.util.Direction side) {
                    return instance.serializeNBT();
                }

                @Override
                public void readNBT(net.minecraftforge.common.capabilities.Capability<com.babelmoth.rotp_ata.capability.IMothPool> capability, com.babelmoth.rotp_ata.capability.IMothPool instance, net.minecraft.util.Direction side, net.minecraft.nbt.INBT nbt) {
                    if (nbt instanceof net.minecraft.nbt.CompoundNBT) {
                        instance.deserializeNBT((net.minecraft.nbt.CompoundNBT) nbt);
                    }
                }
            },
            com.babelmoth.rotp_ata.capability.MothPool::new
        );
    }
    
    private void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(InitEntities.FOSSIL_MOTH.get(), FossilMothEntity.createAttributes().build());
    }
}
