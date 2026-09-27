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

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import com.deceased.nearbyrespawn.network.NearbyRespawnPacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(NearbyRespawnMod.MODID)
public class NearbyRespawnMod {

    public static final String MODID = "nearbyrespawn";
    public static final Logger LOGGER = LogManager.getLogger("NearbyRespawn");
    public static final String VERSION = "1.0.3";
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel NETWORK_CHANNEL = NetworkRegistry.newSimpleChannel(
            new net.minecraft.resources.ResourceLocation(MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public NearbyRespawnMod() {
        LOGGER.info("[NearbyRespawn] Mod initializing...");
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        IEventBus forgeEventBus = MinecraftForge.EVENT_BUS;

        // Register config
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC, "nearbyrespawn-server.toml");
        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);

        // Register network packets
        int packetId = 0;
        // C2S: client requests a nearby respawn. The server handler drives the FULL vanilla
        // respawn (PlayerList.respawn) itself, so no S2C packet is needed anymore: the vanilla
        // ClientboundRespawnPacket closes the DeathScreen and rebuilds the client player.
        NETWORK_CHANNEL.registerMessage(packetId++, NearbyRespawnPacket.class,
                NearbyRespawnPacket::encode, NearbyRespawnPacket::decode, NearbyRespawnPacket::handle);

        // Initialize handlers
        DeathTracker.init(forgeEventBus);
        // Client-only: NearbyRespawnHandler uses Minecraft/DeathScreen/Button classes
        // that don't exist on dedicated servers. DistExecutor ensures the lambda
        // (and thus the class reference) is only resolved on the CLIENT dist.
        DistExecutor.safeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> NearbyRespawnHandler.init(forgeEventBus));
        LOGGER.info("[NearbyRespawn] Mod initialized successfully. Network channel registered.");
    }

    private void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == Config.SERVER_SPEC) {
            Config.bake();
        }
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == Config.SERVER_SPEC) {
            Config.bake();
        }
    }
}
