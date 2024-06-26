package net.minecraft.world.level.pathfinder;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import java.util.EnumSet;
import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsFluid;
import net.minecraft.util.MathHelper;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.level.ChunkCache;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockCampfire;
import net.minecraft.world.level.block.BlockDoor;
import net.minecraft.world.level.block.BlockFenceGate;
import net.minecraft.world.level.block.BlockLeaves;
import net.minecraft.world.level.block.BlockMinecartTrackAbstract;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidTypes;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec3D;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PathfinderNormal extends PathfinderAbstract {

    public static final double SPACE_BETWEEN_WALL_POSTS = 0.5D;
    private static final double DEFAULT_MOB_JUMP_HEIGHT = 1.125D;
    private final Long2ObjectMap<PathType> pathTypesByPosCache = new Long2ObjectOpenHashMap();
    private final Object2BooleanMap<AxisAlignedBB> collisionCache = new Object2BooleanOpenHashMap();

    public PathfinderNormal() {}

    @Override
    public void prepare(ChunkCache chunkcache, EntityInsentient entityinsentient) {
        super.prepare(chunkcache, entityinsentient);
        entityinsentient.onPathfindingStart();
    }

    @Override
    public void done() {
        this.mob.onPathfindingDone();
        this.pathTypesByPosCache.clear();
        this.collisionCache.clear();
        super.done();
    }

    @Override
    public PathPoint getStart() {
        BlockPosition.MutableBlockPosition blockposition_mutableblockposition = new BlockPosition.MutableBlockPosition();
        int i = this.mob.getBlockY();
        IBlockData iblockdata = this.level.getBlockState(blockposition_mutableblockposition.set(this.mob.getX(), (double) i, this.mob.getZ()));
        BlockPosition blockposition;

        if (this.mob.canStandOnFluid(iblockdata.getFluidState())) {
            while (this.mob.canStandOnFluid(iblockdata.getFluidState())) {
                ++i;
                iblockdata = this.level.getBlockState(blockposition_mutableblockposition.set(this.mob.getX(), (double) i, this.mob.getZ()));
            }

            --i;
        } else if (this.canFloat() && this.mob.isInWater()) {
            while (iblockdata.is(Blocks.WATER) || iblockdata.getFluidState() == FluidTypes.WATER.getSource(false)) {
                ++i;
                iblockdata = this.level.getBlockState(blockposition_mutableblockposition.set(this.mob.getX(), (double) i, this.mob.getZ()));
            }

            --i;
        } else if (this.mob.onGround()) {
            i = MathHelper.floor(this.mob.getY() + 0.5D);
        } else {
            for (blockposition = this.mob.blockPosition(); (this.level.getBlockState(blockposition).isAir() || this.level.getBlockState(blockposition).isPathfindable(this.level, blockposition, PathMode.LAND)) && blockposition.getY() > this.mob.level().getMinBuildHeight(); blockposition = blockposition.below()) {
                ;
            }

            i = blockposition.above().getY();
        }

        blockposition = this.mob.blockPosition();
        if (!this.canStartAt(blockposition_mutableblockposition.set(blockposition.getX(), i, blockposition.getZ()))) {
            AxisAlignedBB axisalignedbb = this.mob.getBoundingBox();

            if (this.canStartAt(blockposition_mutableblockposition.set(axisalignedbb.minX, (double) i, axisalignedbb.minZ)) || this.canStartAt(blockposition_mutableblockposition.set(axisalignedbb.minX, (double) i, axisalignedbb.maxZ)) || this.canStartAt(blockposition_mutableblockposition.set(axisalignedbb.maxX, (double) i, axisalignedbb.minZ)) || this.canStartAt(blockposition_mutableblockposition.set(axisalignedbb.maxX, (double) i, axisalignedbb.maxZ))) {
                return this.getStartNode(blockposition_mutableblockposition);
            }
        }

        return this.getStartNode(new BlockPosition(blockposition.getX(), i, blockposition.getZ()));
    }

    protected PathPoint getStartNode(BlockPosition blockposition) {
        PathPoint pathpoint = this.getNode(blockposition);

        pathpoint.type = this.getBlockPathType(this.mob, pathpoint.asBlockPos());
        pathpoint.costMalus = this.mob.getPathfindingMalus(pathpoint.type);
        return pathpoint;
    }

    protected boolean canStartAt(BlockPosition blockposition) {
        PathType pathtype = this.getBlockPathType(this.mob, blockposition);

        return pathtype != PathType.OPEN && this.mob.getPathfindingMalus(pathtype) >= 0.0F;
    }

    @Override
    public PathDestination getGoal(double d0, double d1, double d2) {
        return this.getTargetFromNode(this.getNode(MathHelper.floor(d0), MathHelper.floor(d1), MathHelper.floor(d2)));
    }

    @Override
    public int getNeighbors(PathPoint[] apathpoint, PathPoint pathpoint) {
        int i = 0;
        int j = 0;
        PathType pathtype = this.getCachedBlockType(this.mob, pathpoint.x, pathpoint.y + 1, pathpoint.z);
        PathType pathtype1 = this.getCachedBlockType(this.mob, pathpoint.x, pathpoint.y, pathpoint.z);

        if (this.mob.getPathfindingMalus(pathtype) >= 0.0F && pathtype1 != PathType.STICKY_HONEY) {
            j = MathHelper.floor(Math.max(1.0F, this.mob.maxUpStep()));
        }

        double d0 = this.getFloorLevel(new BlockPosition(pathpoint.x, pathpoint.y, pathpoint.z));
        PathPoint pathpoint1 = this.findAcceptedNode(pathpoint.x, pathpoint.y, pathpoint.z + 1, j, d0, EnumDirection.SOUTH, pathtype1);

        if (this.isNeighborValid(pathpoint1, pathpoint)) {
            apathpoint[i++] = pathpoint1;
        }

        PathPoint pathpoint2 = this.findAcceptedNode(pathpoint.x - 1, pathpoint.y, pathpoint.z, j, d0, EnumDirection.WEST, pathtype1);

        if (this.isNeighborValid(pathpoint2, pathpoint)) {
            apathpoint[i++] = pathpoint2;
        }

        PathPoint pathpoint3 = this.findAcceptedNode(pathpoint.x + 1, pathpoint.y, pathpoint.z, j, d0, EnumDirection.EAST, pathtype1);

        if (this.isNeighborValid(pathpoint3, pathpoint)) {
            apathpoint[i++] = pathpoint3;
        }

        PathPoint pathpoint4 = this.findAcceptedNode(pathpoint.x, pathpoint.y, pathpoint.z - 1, j, d0, EnumDirection.NORTH, pathtype1);

        if (this.isNeighborValid(pathpoint4, pathpoint)) {
            apathpoint[i++] = pathpoint4;
        }

        PathPoint pathpoint5 = this.findAcceptedNode(pathpoint.x - 1, pathpoint.y, pathpoint.z - 1, j, d0, EnumDirection.NORTH, pathtype1);

        if (this.isDiagonalValid(pathpoint, pathpoint2, pathpoint4, pathpoint5)) {
            apathpoint[i++] = pathpoint5;
        }

        PathPoint pathpoint6 = this.findAcceptedNode(pathpoint.x + 1, pathpoint.y, pathpoint.z - 1, j, d0, EnumDirection.NORTH, pathtype1);

        if (this.isDiagonalValid(pathpoint, pathpoint3, pathpoint4, pathpoint6)) {
            apathpoint[i++] = pathpoint6;
        }

        PathPoint pathpoint7 = this.findAcceptedNode(pathpoint.x - 1, pathpoint.y, pathpoint.z + 1, j, d0, EnumDirection.SOUTH, pathtype1);

        if (this.isDiagonalValid(pathpoint, pathpoint2, pathpoint1, pathpoint7)) {
            apathpoint[i++] = pathpoint7;
        }

        PathPoint pathpoint8 = this.findAcceptedNode(pathpoint.x + 1, pathpoint.y, pathpoint.z + 1, j, d0, EnumDirection.SOUTH, pathtype1);

        if (this.isDiagonalValid(pathpoint, pathpoint3, pathpoint1, pathpoint8)) {
            apathpoint[i++] = pathpoint8;
        }

        return i;
    }

    protected boolean isNeighborValid(@Nullable PathPoint pathpoint, PathPoint pathpoint1) {
        return pathpoint != null && !pathpoint.closed && (pathpoint.costMalus >= 0.0F || pathpoint1.costMalus < 0.0F);
    }

    protected boolean isDiagonalValid(PathPoint pathpoint, @Nullable PathPoint pathpoint1, @Nullable PathPoint pathpoint2, @Nullable PathPoint pathpoint3) {
        if (pathpoint3 != null && pathpoint2 != null && pathpoint1 != null) {
            if (pathpoint3.closed) {
                return false;
            } else if (pathpoint2.y <= pathpoint.y && pathpoint1.y <= pathpoint.y) {
                if (pathpoint1.type != PathType.WALKABLE_DOOR && pathpoint2.type != PathType.WALKABLE_DOOR && pathpoint3.type != PathType.WALKABLE_DOOR) {
                    boolean flag = pathpoint2.type == PathType.FENCE && pathpoint1.type == PathType.FENCE && (double) this.mob.getBbWidth() < 0.5D;

                    return pathpoint3.costMalus >= 0.0F && (pathpoint2.y < pathpoint.y || pathpoint2.costMalus >= 0.0F || flag) && (pathpoint1.y < pathpoint.y || pathpoint1.costMalus >= 0.0F || flag);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private static boolean doesBlockHavePartialCollision(PathType pathtype) {
        return pathtype == PathType.FENCE || pathtype == PathType.DOOR_WOOD_CLOSED || pathtype == PathType.DOOR_IRON_CLOSED;
    }

    private boolean canReachWithoutCollision(PathPoint pathpoint) {
        AxisAlignedBB axisalignedbb = this.mob.getBoundingBox();
        Vec3D vec3d = new Vec3D((double) pathpoint.x - this.mob.getX() + axisalignedbb.getXsize() / 2.0D, (double) pathpoint.y - this.mob.getY() + axisalignedbb.getYsize() / 2.0D, (double) pathpoint.z - this.mob.getZ() + axisalignedbb.getZsize() / 2.0D);
        int i = MathHelper.ceil(vec3d.length() / axisalignedbb.getSize());

        vec3d = vec3d.scale((double) (1.0F / (float) i));

        for (int j = 1; j <= i; ++j) {
            axisalignedbb = axisalignedbb.move(vec3d);
            if (this.hasCollisions(axisalignedbb)) {
                return false;
            }
        }

        return true;
    }

    protected double getFloorLevel(BlockPosition blockposition) {
        return (this.canFloat() || this.isAmphibious()) && this.level.getFluidState(blockposition).is(TagsFluid.WATER) ? (double) blockposition.getY() + 0.5D : getFloorLevel(this.level, blockposition);
    }

    public static double getFloorLevel(IBlockAccess iblockaccess, BlockPosition blockposition) {
        BlockPosition blockposition1 = blockposition.below();
        VoxelShape voxelshape = iblockaccess.getBlockState(blockposition1).getCollisionShape(iblockaccess, blockposition1);

        return (double) blockposition1.getY() + (voxelshape.isEmpty() ? 0.0D : voxelshape.max(EnumDirection.EnumAxis.Y));
    }

    protected boolean isAmphibious() {
        return false;
    }

    @Nullable
    protected PathPoint findAcceptedNode(int i, int j, int k, int l, double d0, EnumDirection enumdirection, PathType pathtype) {
        PathPoint pathpoint = null;
        BlockPosition.MutableBlockPosition blockposition_mutableblockposition = new BlockPosition.MutableBlockPosition();
        double d1 = this.getFloorLevel(blockposition_mutableblockposition.set(i, j, k));

        if (d1 - d0 > this.getMobJumpHeight()) {
            return null;
        } else {
            PathType pathtype1 = this.getCachedBlockType(this.mob, i, j, k);
            float f = this.mob.getPathfindingMalus(pathtype1);
            double d2 = (double) this.mob.getBbWidth() / 2.0D;

            if (f >= 0.0F) {
                pathpoint = this.getNodeAndUpdateCostToMax(i, j, k, pathtype1, f);
            }

            if (doesBlockHavePartialCollision(pathtype) && pathpoint != null && pathpoint.costMalus >= 0.0F && !this.canReachWithoutCollision(pathpoint)) {
                pathpoint = null;
            }

            if (pathtype1 != PathType.WALKABLE && (!this.isAmphibious() || pathtype1 != PathType.WATER)) {
                if ((pathpoint == null || pathpoint.costMalus < 0.0F) && l > 0 && (pathtype1 != PathType.FENCE || this.canWalkOverFences()) && pathtype1 != PathType.UNPASSABLE_RAIL && pathtype1 != PathType.TRAPDOOR && pathtype1 != PathType.POWDER_SNOW) {
                    pathpoint = this.findAcceptedNode(i, j + 1, k, l - 1, d0, enumdirection, pathtype);
                    if (pathpoint != null && (pathpoint.type == PathType.OPEN || pathpoint.type == PathType.WALKABLE) && this.mob.getBbWidth() < 1.0F) {
                        double d3 = (double) (i - enumdirection.getStepX()) + 0.5D;
                        double d4 = (double) (k - enumdirection.getStepZ()) + 0.5D;
                        AxisAlignedBB axisalignedbb = new AxisAlignedBB(d3 - d2, this.getFloorLevel(blockposition_mutableblockposition.set(d3, (double) (j + 1), d4)) + 0.001D, d4 - d2, d3 + d2, (double) this.mob.getBbHeight() + this.getFloorLevel(blockposition_mutableblockposition.set((double) pathpoint.x, (double) pathpoint.y, (double) pathpoint.z)) - 0.002D, d4 + d2);

                        if (this.hasCollisions(axisalignedbb)) {
                            pathpoint = null;
                        }
                    }
                }

                if (!this.isAmphibious() && pathtype1 == PathType.WATER && !this.canFloat()) {
                    if (this.getCachedBlockType(this.mob, i, j - 1, k) != PathType.WATER) {
                        return pathpoint;
                    }

                    while (j > this.mob.level().getMinBuildHeight()) {
                        --j;
                        pathtype1 = this.getCachedBlockType(this.mob, i, j, k);
                        if (pathtype1 != PathType.WATER) {
                            return pathpoint;
                        }

                        pathpoint = this.getNodeAndUpdateCostToMax(i, j, k, pathtype1, this.mob.getPathfindingMalus(pathtype1));
                    }
                }

                if (pathtype1 == PathType.OPEN) {
                    int i1 = 0;
                    int j1 = j;

                    while (pathtype1 == PathType.OPEN) {
                        --j;
                        if (j < this.mob.level().getMinBuildHeight()) {
                            return this.getBlockedNode(i, j1, k);
                        }

                        if (i1++ >= this.mob.getMaxFallDistance()) {
                            return this.getBlockedNode(i, j, k);
                        }

                        pathtype1 = this.getCachedBlockType(this.mob, i, j, k);
                        f = this.mob.getPathfindingMalus(pathtype1);
                        if (pathtype1 != PathType.OPEN && f >= 0.0F) {
                            pathpoint = this.getNodeAndUpdateCostToMax(i, j, k, pathtype1, f);
                            break;
                        }

                        if (f < 0.0F) {
                            return this.getBlockedNode(i, j, k);
                        }
                    }
                }

                if (doesBlockHavePartialCollision(pathtype1) && pathpoint == null) {
                    pathpoint = this.getNode(i, j, k);
                    pathpoint.closed = true;
                    pathpoint.type = pathtype1;
                    pathpoint.costMalus = pathtype1.getMalus();
                }

                return pathpoint;
            } else {
                return pathpoint;
            }
        }
    }

    private double getMobJumpHeight() {
        return Math.max(1.125D, (double) this.mob.maxUpStep());
    }

    private PathPoint getNodeAndUpdateCostToMax(int i, int j, int k, PathType pathtype, float f) {
        PathPoint pathpoint = this.getNode(i, j, k);

        pathpoint.type = pathtype;
        pathpoint.costMalus = Math.max(pathpoint.costMalus, f);
        return pathpoint;
    }

    private PathPoint getBlockedNode(int i, int j, int k) {
        PathPoint pathpoint = this.getNode(i, j, k);

        pathpoint.type = PathType.BLOCKED;
        pathpoint.costMalus = -1.0F;
        return pathpoint;
    }

    private boolean hasCollisions(AxisAlignedBB axisalignedbb) {
        return this.collisionCache.computeIfAbsent(axisalignedbb, (object) -> {
            return !this.level.noCollision(this.mob, axisalignedbb);
        });
    }

    @Override
    public PathType getBlockPathType(IBlockAccess iblockaccess, int i, int j, int k, EntityInsentient entityinsentient) {
        EnumSet<PathType> enumset = EnumSet.noneOf(PathType.class);
        PathType pathtype = PathType.BLOCKED;

        pathtype = this.getBlockPathTypes(iblockaccess, i, j, k, enumset, pathtype, entityinsentient.blockPosition());
        if (enumset.contains(PathType.FENCE)) {
            return PathType.FENCE;
        } else if (enumset.contains(PathType.UNPASSABLE_RAIL)) {
            return PathType.UNPASSABLE_RAIL;
        } else {
            PathType pathtype1 = PathType.BLOCKED;
            Iterator iterator = enumset.iterator();

            while (iterator.hasNext()) {
                PathType pathtype2 = (PathType) iterator.next();

                if (entityinsentient.getPathfindingMalus(pathtype2) < 0.0F) {
                    return pathtype2;
                }

                if (entityinsentient.getPathfindingMalus(pathtype2) >= entityinsentient.getPathfindingMalus(pathtype1)) {
                    pathtype1 = pathtype2;
                }
            }

            if (pathtype == PathType.OPEN && entityinsentient.getPathfindingMalus(pathtype1) == 0.0F && this.entityWidth <= 1) {
                return PathType.OPEN;
            } else {
                return pathtype1;
            }
        }
    }

    public PathType getBlockPathTypes(IBlockAccess iblockaccess, int i, int j, int k, EnumSet<PathType> enumset, PathType pathtype, BlockPosition blockposition) {
        for (int l = 0; l < this.entityWidth; ++l) {
            for (int i1 = 0; i1 < this.entityHeight; ++i1) {
                for (int j1 = 0; j1 < this.entityDepth; ++j1) {
                    int k1 = l + i;
                    int l1 = i1 + j;
                    int i2 = j1 + k;
                    PathType pathtype1 = this.getBlockPathType(iblockaccess, k1, l1, i2);

                    pathtype1 = this.evaluateBlockPathType(iblockaccess, blockposition, pathtype1);
                    if (l == 0 && i1 == 0 && j1 == 0) {
                        pathtype = pathtype1;
                    }

                    enumset.add(pathtype1);
                }
            }
        }

        return pathtype;
    }

    protected PathType evaluateBlockPathType(IBlockAccess iblockaccess, BlockPosition blockposition, PathType pathtype) {
        boolean flag = this.canPassDoors();

        if (pathtype == PathType.DOOR_WOOD_CLOSED && this.canOpenDoors() && flag) {
            pathtype = PathType.WALKABLE_DOOR;
        }

        if (pathtype == PathType.DOOR_OPEN && !flag) {
            pathtype = PathType.BLOCKED;
        }

        if (pathtype == PathType.RAIL && !(iblockaccess.getBlockState(blockposition).getBlock() instanceof BlockMinecartTrackAbstract) && !(iblockaccess.getBlockState(blockposition.below()).getBlock() instanceof BlockMinecartTrackAbstract)) {
            pathtype = PathType.UNPASSABLE_RAIL;
        }

        return pathtype;
    }

    protected PathType getBlockPathType(EntityInsentient entityinsentient, BlockPosition blockposition) {
        return this.getCachedBlockType(entityinsentient, blockposition.getX(), blockposition.getY(), blockposition.getZ());
    }

    protected PathType getCachedBlockType(EntityInsentient entityinsentient, int i, int j, int k) {
        return (PathType) this.pathTypesByPosCache.computeIfAbsent(BlockPosition.asLong(i, j, k), (l) -> {
            return this.getBlockPathType(this.level, i, j, k, entityinsentient);
        });
    }

    @Override
    public PathType getBlockPathType(IBlockAccess iblockaccess, int i, int j, int k) {
        return getBlockPathTypeStatic(iblockaccess, new BlockPosition.MutableBlockPosition(i, j, k));
    }

    public static PathType getBlockPathTypeStatic(IBlockAccess iblockaccess, BlockPosition.MutableBlockPosition blockposition_mutableblockposition) {
        int i = blockposition_mutableblockposition.getX();
        int j = blockposition_mutableblockposition.getY();
        int k = blockposition_mutableblockposition.getZ();
        PathType pathtype = getBlockPathTypeRaw(iblockaccess, blockposition_mutableblockposition);

        if (pathtype == PathType.OPEN && j >= iblockaccess.getMinBuildHeight() + 1) {
            PathType pathtype1;

            switch (getBlockPathTypeRaw(iblockaccess, blockposition_mutableblockposition.set(i, j - 1, k))) {
                case OPEN:
                case WATER:
                case LAVA:
                case WALKABLE:
                    pathtype1 = PathType.OPEN;
                    break;
                case DAMAGE_FIRE:
                    pathtype1 = PathType.DAMAGE_FIRE;
                    break;
                case DAMAGE_OTHER:
                    pathtype1 = PathType.DAMAGE_OTHER;
                    break;
                case STICKY_HONEY:
                    pathtype1 = PathType.STICKY_HONEY;
                    break;
                case POWDER_SNOW:
                    pathtype1 = PathType.DANGER_POWDER_SNOW;
                    break;
                case DAMAGE_CAUTIOUS:
                    pathtype1 = PathType.DAMAGE_CAUTIOUS;
                    break;
                case TRAPDOOR:
                    pathtype1 = PathType.DANGER_TRAPDOOR;
                    break;
                default:
                    pathtype1 = checkNeighbourBlocks(iblockaccess, blockposition_mutableblockposition.set(i, j, k), PathType.WALKABLE);
            }

            return pathtype1;
        } else {
            return pathtype;
        }
    }

    public static PathType checkNeighbourBlocks(IBlockAccess iblockaccess, BlockPosition.MutableBlockPosition blockposition_mutableblockposition, PathType pathtype) {
        int i = blockposition_mutableblockposition.getX();
        int j = blockposition_mutableblockposition.getY();
        int k = blockposition_mutableblockposition.getZ();

        for (int l = -1; l <= 1; ++l) {
            for (int i1 = -1; i1 <= 1; ++i1) {
                for (int j1 = -1; j1 <= 1; ++j1) {
                    if (l != 0 || j1 != 0) {
                        blockposition_mutableblockposition.set(i + l, j + i1, k + j1);
                        IBlockData iblockdata = iblockaccess.getBlockState(blockposition_mutableblockposition);

                        if (iblockdata.is(Blocks.CACTUS) || iblockdata.is(Blocks.SWEET_BERRY_BUSH)) {
                            return PathType.DANGER_OTHER;
                        }

                        if (isBurningBlock(iblockdata)) {
                            return PathType.DANGER_FIRE;
                        }

                        if (iblockaccess.getFluidState(blockposition_mutableblockposition).is(TagsFluid.WATER)) {
                            return PathType.WATER_BORDER;
                        }

                        if (iblockdata.is(Blocks.WITHER_ROSE) || iblockdata.is(Blocks.POINTED_DRIPSTONE)) {
                            return PathType.DAMAGE_CAUTIOUS;
                        }
                    }
                }
            }
        }

        return pathtype;
    }

    protected static PathType getBlockPathTypeRaw(IBlockAccess iblockaccess, BlockPosition blockposition) {
        IBlockData iblockdata = iblockaccess.getBlockState(blockposition);
        Block block = iblockdata.getBlock();

        if (iblockdata.isAir()) {
            return PathType.OPEN;
        } else if (!iblockdata.is(TagsBlock.TRAPDOORS) && !iblockdata.is(Blocks.LILY_PAD) && !iblockdata.is(Blocks.BIG_DRIPLEAF)) {
            if (iblockdata.is(Blocks.POWDER_SNOW)) {
                return PathType.POWDER_SNOW;
            } else if (!iblockdata.is(Blocks.CACTUS) && !iblockdata.is(Blocks.SWEET_BERRY_BUSH)) {
                if (iblockdata.is(Blocks.HONEY_BLOCK)) {
                    return PathType.STICKY_HONEY;
                } else if (iblockdata.is(Blocks.COCOA)) {
                    return PathType.COCOA;
                } else if (!iblockdata.is(Blocks.WITHER_ROSE) && !iblockdata.is(Blocks.POINTED_DRIPSTONE)) {
                    Fluid fluid = iblockaccess.getFluidState(blockposition);

                    if (fluid.is(TagsFluid.LAVA)) {
                        return PathType.LAVA;
                    } else if (isBurningBlock(iblockdata)) {
                        return PathType.DAMAGE_FIRE;
                    } else if (block instanceof BlockDoor) {
                        BlockDoor blockdoor = (BlockDoor) block;

                        return (Boolean) iblockdata.getValue(BlockDoor.OPEN) ? PathType.DOOR_OPEN : (blockdoor.type().canOpenByHand() ? PathType.DOOR_WOOD_CLOSED : PathType.DOOR_IRON_CLOSED);
                    } else {
                        return block instanceof BlockMinecartTrackAbstract ? PathType.RAIL : (block instanceof BlockLeaves ? PathType.LEAVES : (!iblockdata.is(TagsBlock.FENCES) && !iblockdata.is(TagsBlock.WALLS) && (!(block instanceof BlockFenceGate) || (Boolean) iblockdata.getValue(BlockFenceGate.OPEN)) ? (!iblockdata.isPathfindable(iblockaccess, blockposition, PathMode.LAND) ? PathType.BLOCKED : (fluid.is(TagsFluid.WATER) ? PathType.WATER : PathType.OPEN)) : PathType.FENCE));
                    }
                } else {
                    return PathType.DAMAGE_CAUTIOUS;
                }
            } else {
                return PathType.DAMAGE_OTHER;
            }
        } else {
            return PathType.TRAPDOOR;
        }
    }

    public static boolean isBurningBlock(IBlockData iblockdata) {
        return iblockdata.is(TagsBlock.FIRE) || iblockdata.is(Blocks.LAVA) || iblockdata.is(Blocks.MAGMA_BLOCK) || BlockCampfire.isLitCampfire(iblockdata) || iblockdata.is(Blocks.LAVA_CAULDRON);
    }
}
