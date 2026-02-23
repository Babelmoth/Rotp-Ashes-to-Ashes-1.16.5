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
                .strength(5.0F, 3600000.0F) // Diamond hardness; getDestroyProgress overridden for persistent mining
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
        spawnDustParticles(world, pos);
    }

    /**
     * 抑制默认方块破坏粒子（紫黑块），改用自定义FOSSIL_ASH粒子
     */
    @Override
    public boolean addDestroyEffects(BlockState state, World world, BlockPos pos, net.minecraft.client.particle.ParticleManager manager) {
        spawnDustParticles(world, pos);
        return true; // true = 抑制默认粒子
    }

    /**
     * 抑制默认挖掘命中粒子（紫黑块），改用自定义FOSSIL_ASH粒子
     */
    @Override
    public boolean addHitEffects(BlockState state, World world, net.minecraft.util.math.RayTraceResult target, net.minecraft.client.particle.ParticleManager manager) {
        if (target instanceof net.minecraft.util.math.BlockRayTraceResult) {
            spawnDustParticles(world, ((net.minecraft.util.math.BlockRayTraceResult) target).getBlockPos());
        }
        return true; // true = 抑制默认粒子
    }

    private static void spawnDustParticles(World world, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        for (int i = 0; i < 6; i++) {
            double dx = (world.random.nextDouble() - 0.5) * 0.3;
            double dy = (world.random.nextDouble() - 0.5) * 0.3;
            double dz = (world.random.nextDouble() - 0.5) * 0.3;
            world.addParticle(com.babelmoth.rotp_ata.init.InitParticles.FOSSIL_ASH.get(),
                    cx + dx, cy + dy, cz + dz,
                    dx * 0.5, dy * 0.5, dz * 0.5);
        }
    }

    /**
     * 钻石硬度(5.0F)，但覆写返回极小正值防止vanilla破坏。
     * 触发BreakSpeed事件，实际进度由BlockEntity管理（持久化、不重置）。
     */
    @Override
    public float getDestroyProgress(BlockState state, PlayerEntity player, IBlockReader world, BlockPos pos) {
        // 调用getDigSpeed以触发PlayerEvent.BreakSpeed（事件处理器中累计进度）
        player.getDigSpeed(state, pos);
        // 返回极小值：vanilla永远不会破坏此方块，但挖掘动画保持进行
        return 0.00001F;
    }

    /**
     * 方块被外部移除时（替身破坏等），确保生成飞蛾并清理屏障状态。
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            TileEntity te = world.getBlockEntity(pos);
            if (te instanceof FrozenBarrierBlockEntity) {
                FrozenBarrierBlockEntity barrier = (FrozenBarrierBlockEntity) te;
                barrier.onBarrierDestroyed();
            }
        }
        super.onRemove(state, world, pos, newState, isMoving);
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
