/*
 * NearbyRespawn Mod - Nearby Respawn & Death Penalty
 * Copyright (C) 2026 jmywork
 *
 * This library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, version 3 of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see <https://www.gnu.org/licenses/>.
 */
package com.deceased.nearbyrespawn;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DeathTracker {

    private static final ConcurrentHashMap<UUID, DeathLocation> DEATH_LOCATIONS = new ConcurrentHashMap<>();

    /**
     * UUIDs that clicked "Respawn Nearby" and are waiting for the vanilla respawn to finish.
     * UUID-based (NOT entity-based) because PlayerList.respawn() destroys the old ServerPlayer
     * and creates a brand-new one that keeps the same UUID.
     */
    private static final Set<UUID> PENDING_NEARBY_RESPAWN = ConcurrentHashMap.newKeySet();

    public static void markNearbyRespawn(UUID playerId) {
        PENDING_NEARBY_RESPAWN.add(playerId);
    }

    public static void init(IEventBus eventBus) {
        eventBus.register(DeathTracker.class);
        NearbyRespawnMod.LOGGER.info("[NearbyRespawn] DeathTracker registered to Forge EventBus.");
    }

    // --- Death location tracking ---

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerId = player.getUUID();
            ResourceKey<Level> dimension = player.level().dimension();
            BlockPos pos = player.blockPosition();
            DEATH_LOCATIONS.put(playerId, new DeathLocation(dimension, pos));
            NearbyRespawnMod.LOGGER.info("[NearbyRespawn] Death recorded for " + player.getName().getString()
                    + " at " + pos + " in " + dimension);
        }
    }

    public static DeathLocation getDeathLocation(ServerPlayer player) {
        return DEATH_LOCATIONS.get(player.getUUID());
    }

    public static void clearDeathLocation(ServerPlayer player) {
        DEATH_LOCATIONS.remove(player.getUUID());
    }

    // --- Respawn handling: move the freshly-respawned player next to the death spot ---

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();

        // Any respawn resolves the recorded death, so grab + clear it up front (avoids stale records).
        DeathLocation deathLoc = DEATH_LOCATIONS.remove(id);

        // Only redirect the spawn when this respawn came from our "Respawn Nearby" button.
        // A vanilla bed / world-spawn respawn is left untouched.
        if (!PENDING_NEARBY_RESPAWN.remove(id)) {
            return;
        }

        if (deathLoc == null) {
            NearbyRespawnMod.LOGGER.warn("[NearbyRespawn] Pending nearby-respawn but no death location for "
                    + player.getName().getString());
            return;
        }

        // The death may have happened in a different dimension than the vanilla respawn point.
        ServerLevel deathLevel = player.server.getLevel(deathLoc.getDimension());
        if (deathLevel == null) {
            NearbyRespawnMod.LOGGER.warn("[NearbyRespawn] Death dimension unavailable: " + deathLoc.getDimension());
            return;
        }

        BlockPos spawnPos = RespawnUtils.findSafeSpawn(deathLevel, deathLoc.getPos(),
                Config.nearbyRadiusMin, Config.nearbyRadius);
        double x = spawnPos.getX() + 0.5;
        double y = spawnPos.getY();
        double z = spawnPos.getZ() + 0.5;
        float yaw = player.getYRot();
        float pitch = player.getXRot();

        // 'player' is the NEW, ALIVE entity produced by vanilla respawn, so teleporting is safe;
        // the client is already rebuilding itself from the ClientboundRespawnPacket.
        if (player.level() == deathLevel) {
            player.connection.teleport(x, y, z, yaw, pitch);
        } else {
            player.teleportTo(deathLevel, x, y, z, yaw, pitch);
        }

        // Mask the teleport moment, then apply the configurable death penalty.
        RespawnUtils.applyBlindnessMask(player);
        if (Config.penaltyEnabled) {
            RespawnUtils.applyPenaltyEffects(player);
        }

        NearbyRespawnMod.LOGGER.info("[NearbyRespawn] Respawned " + player.getName().getString()
                + " near death spot " + spawnPos + " in " + deathLoc.getDimension());
    }

    // --- Logout event: clean up state to prevent memory leaks ---

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID id = player.getUUID();
            DEATH_LOCATIONS.remove(id);
            PENDING_NEARBY_RESPAWN.remove(id);
        }
    }

    public static class DeathLocation {
        private final ResourceKey<Level> dimension;
        private final BlockPos pos;

        public DeathLocation(ResourceKey<Level> dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos;
        }

        public ResourceKey<Level> getDimension() {
            return dimension;
        }

        public BlockPos getPos() {
            return pos;
        }
    }
}
