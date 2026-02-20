package com.babelmoth.rotp_ata.block;

import com.babelmoth.rotp_ata.init.InitBlocks;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.PushReaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.UUID;

public class FrozenBarrierBlock extends Block {

    public FrozenBarrierBlock() {
        super(AbstractBlock.Properties.of(Material.BARRIER)
                .strength(-1.0F, 3600000.0F) // Unbreakable like bedrock
                .noDrops()
                .noOcclusion()
                .isValidSpawn((state, world, pos, type) -> false)
                .isRedstoneConductor((state, world, pos) -> false)
                .isSuffocating((state, world, pos) -> false)
                .isViewBlocking((state, world, pos) -> false));
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return InitBlocks.FROZEN_BARRIER_TILE.get().create();
    }

    @Override
    public BlockRenderType getRenderShape(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED; // Rendered by TileEntityRenderer
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        return VoxelShapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        // Owner can pass through if barrierPassthrough is enabled in config
        Entity entity = context.getEntity();
        if (entity != null && world instanceof World) {
            TileEntity te = ((World)world).getBlockEntity(pos);
            if (te instanceof FrozenBarrierBlockEntity) {
                UUID ownerUUID = ((FrozenBarrierBlockEntity) te).getOwnerUUID();
                if (ownerUUID != null && entity.getUUID().equals(ownerUUID)) {
                    if (entity instanceof LivingEntity) {
                        boolean passthrough = entity.getCapability(
                                com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY)
                                .map(com.babelmoth.rotp_ata.capability.IMothPool::isBarrierPassthrough)
                                .orElse(true);
                        if (passthrough) {
                            return VoxelShapes.empty(); // Owner can pass through
                        }
                    } else {
                        return VoxelShapes.empty(); // Non-living owner entities pass through by default
                    }
                }
            }
        }
        return VoxelShapes.block(); // Others are blocked
    }

    @Override
    public void attack(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        // Add kinetic energy when attacked
        if (!world.isClientSide) {
            TileEntity te = world.getBlockEntity(pos);
            if (te instanceof FrozenBarrierBlockEntity) {
                FrozenBarrierBlockEntity barrier = (FrozenBarrierBlockEntity) te;
                // Base damage from punch = ~1-2
                barrier.addKineticEnergy(2);
            }
        }
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, IBlockReader world, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, IBlockReader world, BlockPos pos) {
        return 1.0F;
    }
}
