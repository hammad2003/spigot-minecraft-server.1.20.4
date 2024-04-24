package net.minecraft.world.level.block.entity;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.SystemUtils;
import net.minecraft.core.BlockPosition;
import net.minecraft.nbt.GameProfileSerializer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.game.PacketPlayOutTileEntityData;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.server.Services;
import net.minecraft.util.UtilColor;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.BlockSkull;
import net.minecraft.world.level.block.state.IBlockData;

public class TileEntitySkull extends TileEntity {

    public static final String TAG_SKULL_OWNER = "SkullOwner";
    public static final String TAG_NOTE_BLOCK_SOUND = "note_block_sound";
    @Nullable
    private static Executor mainThreadExecutor;
    @Nullable
    private static LoadingCache<String, CompletableFuture<Optional<GameProfile>>> profileCache;
    private static final Executor CHECKED_MAIN_THREAD_EXECUTOR = (runnable) -> {
        Executor executor = TileEntitySkull.mainThreadExecutor;

        if (executor != null) {
            executor.execute(runnable);
        }

    };
    @Nullable
    public GameProfile owner;
    @Nullable
    public MinecraftKey noteBlockSound;
    private int animationTickCount;
    private boolean isAnimating;

    public TileEntitySkull(BlockPosition blockposition, IBlockData iblockdata) {
        super(TileEntityTypes.SKULL, blockposition, iblockdata);
    }

    public static void setup(final Services services, Executor executor) {
        TileEntitySkull.mainThreadExecutor = executor;
        final BooleanSupplier booleansupplier = () -> {
            return TileEntitySkull.profileCache == null;
        };

        TileEntitySkull.profileCache = CacheBuilder.newBuilder().expireAfterAccess(Duration.ofMinutes(10L)).maximumSize(256L).build(new CacheLoader<String, CompletableFuture<Optional<GameProfile>>>() {
            public CompletableFuture<Optional<GameProfile>> load(String s) {
                return booleansupplier.getAsBoolean() ? CompletableFuture.completedFuture(Optional.empty()) : TileEntitySkull.loadProfile(s, services, booleansupplier);
            }
        });
    }

    public static void clear() {
        TileEntitySkull.mainThreadExecutor = null;
        TileEntitySkull.profileCache = null;
    }

    static CompletableFuture<Optional<GameProfile>> loadProfile(String s, Services services, BooleanSupplier booleansupplier) {
        return services.profileCache().getAsync(s).thenApplyAsync((optional) -> {
            if (optional.isPresent() && !booleansupplier.getAsBoolean()) {
                UUID uuid = ((GameProfile) optional.get()).getId();
                ProfileResult profileresult = services.sessionService().fetchProfile(uuid, true);

                return profileresult != null ? Optional.ofNullable(profileresult.profile()) : optional;
            } else {
                return Optional.empty();
            }
        }, SystemUtils.backgroundExecutor());
    }

    @Override
    protected void saveAdditional(NBTTagCompound nbttagcompound) {
        super.saveAdditional(nbttagcompound);
        if (this.owner != null) {
            NBTTagCompound nbttagcompound1 = new NBTTagCompound();

            GameProfileSerializer.writeGameProfile(nbttagcompound1, this.owner);
            nbttagcompound.put("SkullOwner", nbttagcompound1);
        }

        if (this.noteBlockSound != null) {
            nbttagcompound.putString("note_block_sound", this.noteBlockSound.toString());
        }

    }

    @Override
    public void load(NBTTagCompound nbttagcompound) {
        super.load(nbttagcompound);
        if (nbttagcompound.contains("SkullOwner", 10)) {
            this.setOwner(GameProfileSerializer.readGameProfile(nbttagcompound.getCompound("SkullOwner")));
        } else if (nbttagcompound.contains("ExtraType", 8)) {
            String s = nbttagcompound.getString("ExtraType");

            if (!UtilColor.isNullOrEmpty(s)) {
                this.setOwner(new GameProfile(SystemUtils.NIL_UUID, s));
            }
        }

        if (nbttagcompound.contains("note_block_sound", 8)) {
            this.noteBlockSound = MinecraftKey.tryParse(nbttagcompound.getString("note_block_sound"));
        }

    }

    public static void animation(World world, BlockPosition blockposition, IBlockData iblockdata, TileEntitySkull tileentityskull) {
        if (iblockdata.hasProperty(BlockSkull.POWERED) && (Boolean) iblockdata.getValue(BlockSkull.POWERED)) {
            tileentityskull.isAnimating = true;
            ++tileentityskull.animationTickCount;
        } else {
            tileentityskull.isAnimating = false;
        }

    }

    public float getAnimation(float f) {
        return this.isAnimating ? (float) this.animationTickCount + f : (float) this.animationTickCount;
    }

    @Nullable
    public GameProfile getOwnerProfile() {
        return this.owner;
    }

    @Nullable
    public MinecraftKey getNoteBlockSound() {
        return this.noteBlockSound;
    }

    @Override
    public PacketPlayOutTileEntityData getUpdatePacket() {
        return PacketPlayOutTileEntityData.create(this);
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    public void setOwner(@Nullable GameProfile gameprofile) {
        synchronized (this) {
            this.owner = gameprofile;
        }

        this.updateOwnerProfile();
    }

    private void updateOwnerProfile() {
        if (this.owner != null && !SystemUtils.isBlank(this.owner.getName()) && !hasTextures(this.owner)) {
            fetchGameProfile(this.owner.getName()).thenAcceptAsync((optional) -> {
                this.owner = (GameProfile) optional.orElse(this.owner);
                this.setChanged();
            }, TileEntitySkull.CHECKED_MAIN_THREAD_EXECUTOR);
        } else {
            this.setChanged();
        }
    }

    @Nullable
    public static GameProfile getOrResolveGameProfile(NBTTagCompound nbttagcompound) {
        if (nbttagcompound.contains("SkullOwner", 10)) {
            return GameProfileSerializer.readGameProfile(nbttagcompound.getCompound("SkullOwner"));
        } else {
            if (nbttagcompound.contains("SkullOwner", 8)) {
                String s = nbttagcompound.getString("SkullOwner");

                if (!SystemUtils.isBlank(s)) {
                    nbttagcompound.remove("SkullOwner");
                    resolveGameProfile(nbttagcompound, s);
                }
            }

            return null;
        }
    }

    public static void resolveGameProfile(NBTTagCompound nbttagcompound) {
        String s = nbttagcompound.getString("SkullOwner");

        if (!SystemUtils.isBlank(s)) {
            resolveGameProfile(nbttagcompound, s);
        }

    }

    private static void resolveGameProfile(NBTTagCompound nbttagcompound, String s) {
        fetchGameProfile(s).thenAccept((optional) -> {
            nbttagcompound.put("SkullOwner", GameProfileSerializer.writeGameProfile(new NBTTagCompound(), (GameProfile) optional.orElse(new GameProfile(SystemUtils.NIL_UUID, s))));
        });
    }

    private static CompletableFuture<Optional<GameProfile>> fetchGameProfile(String s) {
        LoadingCache<String, CompletableFuture<Optional<GameProfile>>> loadingcache = TileEntitySkull.profileCache;

        return loadingcache != null && EntityHuman.isValidUsername(s) ? (CompletableFuture) loadingcache.getUnchecked(s) : CompletableFuture.completedFuture(Optional.empty());
    }

    private static boolean hasTextures(GameProfile gameprofile) {
        return gameprofile.getProperties().containsKey("textures");
    }
}
