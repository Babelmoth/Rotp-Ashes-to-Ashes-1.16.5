package com.babelmoth.rotp_ata.init;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.block.FrozenBarrierBlock;
import com.babelmoth.rotp_ata.block.FrozenBarrierBlockEntity;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class InitBlocks {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(
            ForgeRegistries.BLOCKS, AddonMain.MOD_ID);

    public static final DeferredRegister<TileEntityType<?>> TILE_ENTITIES = DeferredRegister.create(
            ForgeRegistries.TILE_ENTITIES, AddonMain.MOD_ID);

    // Frozen Barrier Block
    public static final RegistryObject<FrozenBarrierBlock> FROZEN_BARRIER = BLOCKS.register("frozen_barrier",
            FrozenBarrierBlock::new);

    // Frozen Barrier Tile Entity
    public static final RegistryObject<TileEntityType<FrozenBarrierBlockEntity>> FROZEN_BARRIER_TILE = 
            TILE_ENTITIES.register("frozen_barrier_tile",
                    () -> TileEntityType.Builder.of(FrozenBarrierBlockEntity::new, FROZEN_BARRIER.get()).build(null));
}
