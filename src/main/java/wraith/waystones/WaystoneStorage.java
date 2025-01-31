package wraith.waystones;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import wraith.waystones.block.WaystoneBlock;
import wraith.waystones.block.WaystoneBlockEntity;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WaystoneStorage {
    private final PersistentState state;

    private final ConcurrentHashMap<String, WaystoneBlockEntity> WAYSTONES = new ConcurrentHashMap<>();
    private final MinecraftServer server;

    private static final String ID = Waystones.MOD_ID + ":waystones";
    private final CompatibilityLayer compat;

    public WaystoneStorage(MinecraftServer server) {
        CompatibilityLayer compatLoading = new CompatibilityLayer(this, server);
        this.server = server;
        state = this.server.getWorld(ServerWorld.OVERWORLD).getPersistentStateManager().getOrCreate(() -> new PersistentState(ID) {
            @Override
            public void fromTag(CompoundTag tag) {
                WaystoneStorage.this.fromTag(tag);
            }
            @Override
            public CompoundTag toTag(CompoundTag tag) {
                return WaystoneStorage.this.toTag(tag);
            }
        }, ID);


        if (!compatLoading.loadCompatibility()) {
            compatLoading = null;
        }
        compat = compatLoading;

        loadOrSaveWaystones(false);
    }

    public void fromTag(CompoundTag tag) {
        if (server == null || tag == null || !tag.contains("waystones")) {
            return;
        }
        WAYSTONES.clear();
        ListTag waystones = tag.getList("waystones", 10);

        for (int i = 0; i < waystones.size(); ++i) {
            CompoundTag waystoneTag = waystones.getCompound(i);
            if (!waystoneTag.contains("hash") || !waystoneTag.contains("dimension") || !waystoneTag.contains("position")) {
                continue;
            }
            String hash = waystoneTag.getString("hash");
            String dimension = waystoneTag.getString("dimension");
            int[] coordinates = waystoneTag.getIntArray("position");
            BlockPos pos = new BlockPos(coordinates[0], coordinates[1], coordinates[2]);
            for (ServerWorld world : server.getWorlds()) {
                if (WaystoneBlock.getDimensionName(world).equals(dimension)) {
                    WaystoneBlockEntity entity = WaystoneBlock.getEntity(world, pos);
                    if (entity != null) {
                        WAYSTONES.put(hash, entity);
                    }
                    break;
                }
            }
        }

    }

    public CompoundTag toTag(CompoundTag tag) {
        if (tag == null) {
            tag = new CompoundTag();
        }
        ListTag waystones = new ListTag();
        for (Map.Entry<String, WaystoneBlockEntity> waystone : WAYSTONES.entrySet()) {
            String hash = waystone.getKey();
            WaystoneBlockEntity entity = waystone.getValue();

            CompoundTag waystoneTag = new CompoundTag();
            waystoneTag.putString("hash", hash);
            waystoneTag.putString("name", entity.getWaystoneName());
            BlockPos pos = entity.getPos();
            waystoneTag.putIntArray("position", Arrays.asList(pos.getX(), pos.getY(), pos.getZ()));
            waystoneTag.putString("dimension", WaystoneBlock.getDimensionName(entity.getWorld()));

            waystones.add(waystoneTag);
        }
        tag.put("waystones", waystones);
        ListTag globals = new ListTag();
        ArrayList<String> globalWaystones = getGlobals();
        for (String globalWaystone : globalWaystones) {
            globals.add(StringTag.of(globalWaystone));
        }
        tag.put("global_waystones", globals);
        return tag;
    }

    public boolean hasWaystone(WaystoneBlockEntity waystone) {
        return WAYSTONES.containsValue(waystone);
    }

    public void addWaystone(WaystoneBlockEntity waystone) {
        WAYSTONES.put(waystone.getHash(), waystone);
        loadOrSaveWaystones(true);
    }

    public void addWaystones(HashSet<WaystoneBlockEntity> waystones) {
        for (WaystoneBlockEntity waystone : waystones) {
            WAYSTONES.put(waystone.getHash(), waystone);
        }
        loadOrSaveWaystones(true);
    }

    public void loadOrSaveWaystones(boolean save) {
        if (server == null) {
            return;
        }
        ServerWorld world = server.getWorld(ServerWorld.OVERWORLD);

        if (save) {
            state.markDirty();
            sendToAllPlayers();
        }
        else {
            try {
                CompoundTag compoundTag = world.getPersistentStateManager().readTag(ID, SharedConstants.getGameVersion().getWorldVersion());
                state.fromTag(compoundTag.getCompound("data"));
            } catch (IOException ignored) {
            }
        }
        world.getPersistentStateManager().save();
    }

    public void sendToAllPlayers() {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendToPlayer(player);
        }
    }

    public void sendToPlayer(ServerPlayerEntity player) {
        PacketByteBuf data = new PacketByteBuf(Unpooled.buffer());
        data.writeCompoundTag(toTag(new CompoundTag()));
        ServerPlayNetworking.send(player, Utils.ID("waystone_packet"), data);
    }

    public void removeWaystone(String hash) {
        WAYSTONES.remove(hash);
        forgetForAllPlayers(hash);
        loadOrSaveWaystones(true);
    }

    private void forgetForAllPlayers(String hash) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ((PlayerEntityMixinAccess)player).forgetWaystone(hash);
        }
    }

    public void removeWaystone(WaystoneBlockEntity waystone) {
        WAYSTONES.remove(waystone.getHash());
        forgetForAllPlayers(waystone.getHash());
        loadOrSaveWaystones(true);
    }

    public void renameWaystone(WaystoneBlockEntity waystone, String name) {
        waystone.setName(name);
        loadOrSaveWaystones(true);
    }

    public void renameWaystone(String hash, String name) {
        if (WAYSTONES.containsKey(hash)) {
            WAYSTONES.get(hash).setName(name);
            loadOrSaveWaystones(true);
        }
    }

    public WaystoneBlockEntity getWaystone(String hash) {
        return WAYSTONES.getOrDefault(hash, null);
    }

    public boolean containsHash(String hash) {
        return WAYSTONES.containsKey(hash);
    }

    public ArrayList<String> getGlobals() {
        ArrayList<String> globals = new ArrayList<>();
        for (Map.Entry<String, WaystoneBlockEntity> waystone : WAYSTONES.entrySet()) {
            if (waystone.getValue().isGlobal()) {
                globals.add(waystone.getKey());
            }
        }
        return globals;
    }

    public void toggleGlobal(String hash) {
        WaystoneBlockEntity waystone = getWaystone(hash);
        if (waystone == null) {
            return;
        }
        waystone.toggleGlobal();
        sendToAllPlayers();
    }

    public void setOwner(String hash, PlayerEntity owner) {
        if (WAYSTONES.containsKey(hash)) {
            WAYSTONES.get(hash).setOwner(owner);
        }
    }

    public HashSet<String> getAllHashes() {
        return new HashSet<>(WAYSTONES.keySet());
    }

    public int getCount() {
        return WAYSTONES.size();
    }

    public void sendCompatData(ServerPlayerEntity player) {
        if (this.compat != null) {
            this.compat.updatePlayerCompatibility(player);
        }
    }

}
