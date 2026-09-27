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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stateless helpers for the nearby-respawn flow:
 * - picking a safe landing spot near the death location
 * - applying the death-penalty effects and the short blindness "teleport mask"
 *
 * These run on the SERVER thread against the freshly respawned (alive) player.
 */
public final class RespawnUtils {

    private RespawnUtils() {
    }

    // --- Safe spawn selection ---

    public static BlockPos findSafeSpawn(ServerLevel level, BlockPos center, int minRadius, int maxRadius) {
        Random random = ThreadLocalRandom.current();
        // Nether: fewer attempts to avoid excessive chunk loading (128 layers x many attempts)
        int attempts = (level.dimension() == Level.NETHER) ? 20 : 100;
        // Nether is roofed terrain by nature -- both the sky-geometry gate and the sky-light
        // thresholds are meaningless there.
        boolean nether = level.dimension() == Level.NETHER;
        // Optional city-map anchor: when a street baseline Y is configured, street-level
        // candidates are tried first so rooftops / elevated roads are not mistaken for the street.
        boolean useBaseline = Config.streetBaselineY >= 0 && !nether;
        int strictSky = 15;
        int relaxedSky = nether ? 0 : Math.max(0, Math.min(15, Config.relaxedSkyLight));

        // Per-pass rule sets: {requireBaseline, minSkyLight}. Ordered strictest -> most relaxed,
        // the first pass that finds a landing wins. The open-sky requirement is no longer a pass
        // flag: the vertical geometry gate (no opaque block within skyClearanceBlocks above) is
        // now a HARD requirement in every pass -- that is what actually keeps landings off cave
        // floors and under roofs. What degrades between passes is only the sky-light threshold
        // (15 = truly open sky -> relaxedSky = shaded street / dense jungle understory) and the
        // optional street-baseline preference.
        //   {true,  15}         street level with open sky          (only when baseline configured)
        //   {false, 15}         any open-sky surface, lowest one wins (default first pass)
        //   {true,  relaxedSky} street level, shaded                (streets between tall buildings)
        //   {false, relaxedSky} any shaded-but-uncovered surface    (jungle floor under a canopy)
        boolean[][] passRules = useBaseline
                ? new boolean[][]{{true, false}, {false, false}, {true, true}, {false, true}}
                : new boolean[][]{{false, false}, {false, true}};
        int[] passSky = useBaseline
                ? new int[]{strictSky, strictSky, relaxedSky, relaxedSky}
                : new int[]{strictSky, relaxedSky};

        for (int pass = 0; pass < passRules.length; pass++) {
            for (int i = 0; i < attempts; i++) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double distance = minRadius + random.nextDouble() * (maxRadius - minRadius);
                int x = center.getX() + (int) (Math.cos(angle) * distance);
                int z = center.getZ() + (int) (Math.sin(angle) * distance);

                BlockPos safePos = findSafeY(level, x, z, passSky[pass], passRules[pass][0]);
                if (safePos != null) {
                    return safePos;
                }
            }
        }

        // Fallback: safe position directly at the death point column (relaxed rules, geometry
        // gate still active so this can never be a sealed cave).
        BlockPos fallback = findSafeY(level, center.getX(), center.getZ(), relaxedSky, false);
        if (fallback != null) {
            return fallback;
        }

        // Ultimate fallback to world spawn if no safe position found
        BlockPos worldSpawn = level.getSharedSpawnPos();
        if (worldSpawn != null) {
            return worldSpawn;
        }

        // Last resort: death point + 1
        return center.above();
    }

    public static BlockPos findSafeY(ServerLevel level, int x, int z) {
        return findSafeY(level, x, z, 0, false);
    }

    /**
     * Find the LOWEST qualifying standable spot in the given column.
     *
     * @param minSkyLight     required sky light at the feet position (15 = fully open sky,
     *                        lower values accept shaded streets / jungle understory; Nether ignores it)
     * @param requireBaseline when true, only spots within the configured street-baseline band qualify
     */
    public static BlockPos findSafeY(ServerLevel level, int x, int z, int minSkyLight, boolean requireBaseline) {
        int minBuildHeight = level.getMinBuildHeight();
        if (x < -30000000 || x > 30000000 || z < -30000000 || z > 30000000) {
            return null;
        }
        // Nether has no sky: both the light threshold and the geometry gate are skipped there.
        boolean nether = level.dimension() == Level.NETHER;

        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);

        // Water-surface priority: MOTION_BLOCKING counts water, so for a water column
        // surfaceY is exactly the block above the topmost water. If the two body blocks
        // there are safe, land right on the surface (feet one block above the water, the
        // player drops one block and floats) instead of scanning down to the sea floor --
        // no more surfacing from a blind deep-ocean swim.
        if (level.getBlockState(new BlockPos(x, surfaceY - 1, z)).getFluidState().is(Fluids.WATER)) {
            BlockPos feet = new BlockPos(x, surfaceY, z);
            if (isSafeBodyBlock(level, feet) && isSafeBodyBlock(level, feet.above())
                    && !isHazard(level, feet) && !isHazard(level, feet.above())
                    && skyLightAtLeast(level, feet, minSkyLight)
                    && (nether || hasOpenSkyGeometry(level, feet))
                    && (!requireBaseline || matchesStreetBaseline(feet))) {
                return feet;
            }
            // Water surface itself is disqualified (roofed over, too dark for this pass, or off
            // the street baseline) -- anything deeper in the water column sits under the same
            // obstruction / even less light, so this column cannot offer a better landing.
            return null;
        }

        int scanTop;
        int scanBottom;
        if (nether) {
            // Nether: scan from surface down, but limit range for performance
            scanTop = Math.min(surfaceY + 10, level.getMaxBuildHeight() - 2);
            scanBottom = Math.max(minBuildHeight, 1);
        } else {
            scanTop = Math.min(surfaceY + 5, level.getMaxBuildHeight() - 2);
            scanBottom = minBuildHeight;
        }
        return scanForSafeY(level, x, z, scanTop, scanBottom, minSkyLight, requireBaseline);
    }

    /**
     * Scans the column top-down and returns the FEET position of the lowest qualifying spot.
     * Because the scan is top-down and never abandons the column early, the first candidate that
     * passes every check is by construction the lowest qualifying one -- no rooftop can win over
     * a qualifying street floor below it.
     */
    private static BlockPos scanForSafeY(ServerLevel level, int x, int z, int topY, int bottomY,
                                         int minSkyLight, boolean requireBaseline) {
        boolean nether = level.dimension() == Level.NETHER;
        for (int y = topY; y >= bottomY; y--) {
            BlockPos ground = new BlockPos(x, y, z);
            BlockPos feet = new BlockPos(x, y + 1, z);
            BlockPos head = new BlockPos(x, y + 2, z);

            if (y + 2 >= level.getMaxBuildHeight()) {
                continue;
            }

            // Ground: anything with a collision box that is not a fluid (dirt paths, farmland included).
            // Body: air / water / non-colliding decoration (short grass, flowers, top snow).
            if (!isStandableGround(level, ground)
                    || !isSafeBodyBlock(level, feet)
                    || !isSafeBodyBlock(level, head)
                    || isHazard(level, ground) || isHazard(level, feet) || isHazard(level, head)) {
                continue;
            }

            // Sky-light threshold: a per-pass SOFT condition (15 in strict passes, Config.relaxedSkyLight
            // in relaxed ones). No longer an unconditional gate -- shaded streets and jungle floors are
            // exactly what the relaxed passes exist for.
            if (!skyLightAtLeast(level, feet, minSkyLight)) {
                continue;
            }

            // Vertical geometry gate (hard, every pass): no light-blocking block within
            // skyClearanceBlocks above the feet. Opaque solids only -- leaves, glass and water
            // do NOT count, so a jungle floor or a spot under a glass canopy stays eligible.
            // On failure CONTINUE scanning down instead of abandoning the column: a thin or
            // nearby obstruction (canopy 4 blocks up, awning 6 blocks up) does not necessarily
            // shade the deeper parts of the same column.
            if (!nether && !hasOpenSkyGeometry(level, feet)) {
                continue;
            }

            // Street-baseline guard (city maps): reject landings too far from the configured
            // street height -- rooftops, elevated roads, skybridges. Keep scanning down: the
            // real street level may still be found lower in this very column.
            if (requireBaseline && !matchesStreetBaseline(feet)) {
                continue;
            }

            return feet;
        }
        return null;
    }

    /** True when the feet position is within Config.streetBaselineTolerance of Config.streetBaselineY. */
    private static boolean matchesStreetBaseline(BlockPos feet) {
        return Math.abs(feet.getY() - Config.streetBaselineY) <= Config.streetBaselineTolerance;
    }

    /** Standable ground: has a collision box and is not a fluid. Leaves are excluded so a forest column scans past the canopy to the ground. */
    private static boolean isStandableGround(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.LEAVES)) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    /** A block the player's body can occupy: air, water, or a non-colliding decoration (short grass, flowers, top snow...). */
    private static boolean isSafeBodyBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty()) {
            // Water is fine (the player can swim out); lava is caught by isHazard / the fluid
            // is not water so it is rejected here.
            return state.getFluidState().is(Fluids.WATER) || state.getFluidState().is(Fluids.FLOWING_WATER);
        }
        // Decoration without a collision box: short grass, flowers, top snow layers, ...
        return state.getCollisionShape(level, pos).isEmpty();
    }

    /** Dangerous blocks that must never be part of the landing spot. Lava is already excluded via fluid checks. */
    private static boolean isHazard(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.getBlock() instanceof CampfireBlock
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS)
                || state.getBlock() instanceof SweetBerryBushBlock
                || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW);
    }

    /** Sky-light soft threshold for the current pass (Nether is exempt -- it has no sky). */
    private static boolean skyLightAtLeast(ServerLevel level, BlockPos pos, int minSkyLight) {
        if (minSkyLight <= 0 || level.dimension() == Level.NETHER) {
            return true;
        }
        return level.getBrightness(LightLayer.SKY, pos) >= minSkyLight;
    }

    /**
     * Vertical geometry gate: true when no light-OPaque block (getLightBlock >= 15) sits above
     * the feet position within Config.skyClearanceBlocks. Unlike the old collision-box test this
     * ignores leaves, glass and water, so natural shade (jungle canopy) and thin built shade
     * (glass awning) no longer disqualify a column -- only real roofs do.
     */
    private static boolean hasOpenSkyGeometry(ServerLevel level, BlockPos feet) {
        int clearance = Math.max(1, Config.skyClearanceBlocks);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= clearance; dy++) {
            cursor.set(feet.getX(), feet.getY() + dy, feet.getZ());
            if (cursor.getY() >= level.getMaxBuildHeight()) {
                break;
            }
            if (level.getBlockState(cursor).getLightBlock(level, cursor) >= 15) {
                return false;
            }
        }
        return true;
    }

    // --- Effects ---

    /**
     * Short blindness applied immediately after the nearby respawn so the player
     * does not visually notice the teleport from the vanilla spawn to the death spot.
     */
    public static void applyBlindnessMask(ServerPlayer player) {
        int ticks = Config.blindnessMaskTicks;
        if (ticks > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0, false, false));
        }
    }

    /**
     * Configurable death-penalty effects (e.g. weakness / slowness).
     */
    public static void applyPenaltyEffects(ServerPlayer player) {
        for (String effectStr : Config.penaltyEffects) {
            try {
                String[] parts = effectStr.split(",");
                if (parts.length != 3) continue;

                ResourceLocation effectId = new ResourceLocation(parts[0]);
                int duration = Integer.parseInt(parts[1]);
                int amplifier = Integer.parseInt(parts[2]);

                Optional<MobEffect> effectOpt = BuiltInRegistries.MOB_EFFECT.getOptional(effectId);
                if (effectOpt.isPresent()) {
                    MobEffect effect = effectOpt.get();
                    player.addEffect(new MobEffectInstance(effect, duration, amplifier));
                }
            } catch (Exception e) {
                // Skip invalid effect entries
            }
        }
    }
}
