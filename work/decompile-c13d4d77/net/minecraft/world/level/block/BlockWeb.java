package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPosition;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.phys.Vec3D;

public class BlockWeb extends Block {

    public static final MapCodec<BlockWeb> CODEC = simpleCodec(BlockWeb::new);

    @Override
    public MapCodec<BlockWeb> codec() {
        return BlockWeb.CODEC;
    }

    public BlockWeb(BlockBase.Info blockbase_info) {
        super(blockbase_info);
    }

    @Override
    public void entityInside(IBlockData iblockdata, World world, BlockPosition blockposition, Entity entity) {
        entity.makeStuckInBlock(iblockdata, new Vec3D(0.25D, 0.05000000074505806D, 0.25D));
    }
}
