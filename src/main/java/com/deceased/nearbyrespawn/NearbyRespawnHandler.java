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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class NearbyRespawnHandler {

    public static void init(IEventBus eventBus) {
        MinecraftForge.EVENT_BUS.register(NearbyRespawnHandler.class);
        NearbyRespawnMod.LOGGER.info("[NearbyRespawn] Client handler registered.");
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof DeathScreen deathScreen) {
            NearbyRespawnMod.LOGGER.info("[NearbyRespawn] DeathScreen detected, adding button.");
            int screenWidth = deathScreen.width;
            int screenHeight = deathScreen.height;

            // Original respawn button is at y = screenHeight/2 + 20, width 200, height 20
            // Place our button below it
            int buttonWidth = 200;
            int buttonHeight = 20;
            int buttonX = (screenWidth - buttonWidth) / 2;
            int buttonY = screenHeight / 2 + 46; // Below original respawn button (20 + 20 + 6 gap)

            Button nearbyRespawnButton = Button.builder(
                    Component.translatable("nearbyrespawn.death_screen.respawn_nearby"),
                    button -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player == null) {
                            NearbyRespawnMod.LOGGER.warn("[NearbyRespawn] 'Respawn Nearby' clicked but client player is null; aborting.");
                            return;
                        }
                        NearbyRespawnMod.LOGGER.info("[NearbyRespawn] 'Respawn Nearby' clicked; flagging server + triggering vanilla respawn.");
                        // Strictly reuse MC's own respawn flow (fixes the stuck-dead / desync bug):
                        //   1) First flag the server that THIS respawn should land near the death spot.
                        //      Sent before the respawn command so it is processed first (same connection = FIFO).
                        //   2) Then mc.player.respawn() sends the standard PERFORM_RESPAWN command -- the exact
                        //      same thing the vanilla Respawn button does -- so the modpack's normal respawn
                        //      path runs (correct connection acks, capability revive, client player rebuild,
                        //      DeathScreen close). DeathTracker#onPlayerRespawn then teleports to the death spot.
                        NearbyRespawnMod.NETWORK_CHANNEL.sendToServer(
                                new com.deceased.nearbyrespawn.network.NearbyRespawnPacket()
                        );
                        mc.player.respawn();
                        mc.setScreen(null);
                    }
            ).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build();

            event.addListener(nearbyRespawnButton);
            NearbyRespawnMod.LOGGER.info("[NearbyRespawn] Button added to DeathScreen at x=" + buttonX + " y=" + buttonY);
        }
    }
}
