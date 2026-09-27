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
package com.deceased.nearbyrespawn.network;

import com.deceased.nearbyrespawn.DeathTracker;
import com.deceased.nearbyrespawn.NearbyRespawnMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> Server: the player clicked "Respawn Nearby" on the DeathScreen.
 *
 * This packet ONLY flags the player's UUID as "wants nearby respawn". It deliberately does
 * NOT force a respawn itself: an earlier version called PlayerList.respawn() from here, which
 * in this heavily-modded pack bypassed the standard client respawn path and left the client
 * stuck on the dead LocalPlayer (no HUD, couldn't move or be killed) and broke capability
 * revival (AllTheLeaks: "Cannot have null removal reason on Clone event!").
 *
 * Correct flow (strictly mirrors the vanilla Respawn button):
 *   1. Client sends THIS packet first -> server marks the UUID pending (flag consumed once).
 *   2. Client then calls mc.player.respawn() -> standard PERFORM_RESPAWN -> PlayerList.respawn()
 *      runs the fully-supported vanilla/modpack respawn (new entity, ClientboundRespawnPacket,
 *      capability revive, DeathScreen close) and fires PlayerRespawnEvent.
 *   3. DeathTracker#onPlayerRespawn sees the pending UUID and teleports the fresh, alive player
 *      next to the death spot, then applies the blindness mask + penalty effects.
 *
 * Both packets travel the same connection (FIFO) and run on the server main thread, so the
 * pending flag is always set before the respawn event fires.
 */
public class NearbyRespawnPacket {

    public NearbyRespawnPacket() {
    }

    public static void encode(NearbyRespawnPacket msg, FriendlyByteBuf buf) {
        // No data to send
    }

    public static NearbyRespawnPacket decode(FriendlyByteBuf buf) {
        return new NearbyRespawnPacket();
    }

    public static void handle(NearbyRespawnPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                NearbyRespawnMod.LOGGER.warn("[NearbyRespawn] C2S packet: sender is null!");
                return;
            }

            // Guard on OUR death record (set in LivingDeathEvent) rather than isDeadOrDying()/
            // isSpectator(), which some modpacks alter. No record => already respawned or a
            // stray/duplicate packet => ignore (this also prevents double-respawn duplicates).
            if (DeathTracker.getDeathLocation(player) == null) {
                NearbyRespawnMod.LOGGER.warn("[NearbyRespawn] Ignoring nearby-respawn for "
                        + player.getName().getString() + ": no death record (already respawned or stray packet).");
                return;
            }

            NearbyRespawnMod.LOGGER.info("[NearbyRespawn] C2S received for " + player.getName().getString()
                    + "; marking pending, awaiting the client's vanilla PERFORM_RESPAWN.");

            // ONLY mark the UUID as pending. We deliberately do NOT call PlayerList.respawn() here:
            // forcing respawn() from a custom packet bypasses the standard client respawn path,
            // which in this modpack desyncs the client (player stuck dead) and breaks capability
            // revival. The client calls mc.player.respawn() (PERFORM_RESPAWN) right after this
            // packet; that drives the proper respawn and DeathTracker#onPlayerRespawn consumes
            // this pending flag to teleport the fresh player next to the death spot.
            DeathTracker.markNearbyRespawn(player.getUUID());
        });
        ctx.get().setPacketHandled(true);
    }
}
