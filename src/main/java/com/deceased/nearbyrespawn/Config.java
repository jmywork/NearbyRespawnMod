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

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

public class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SERVER_SPEC;

    // Respawn settings
    private static final ForgeConfigSpec.IntValue RESPAWN_NEARBY_RADIUS;
    private static final ForgeConfigSpec.IntValue RESPAWN_NEARBY_RADIUS_MIN;
    private static final ForgeConfigSpec.IntValue RESPAWN_BLINDNESS_MASK_TICKS;
    private static final ForgeConfigSpec.IntValue RESPAWN_STREET_BASELINE_Y;
    private static final ForgeConfigSpec.IntValue RESPAWN_STREET_BASELINE_TOLERANCE;
    private static final ForgeConfigSpec.IntValue RESPAWN_SKY_CLEARANCE_BLOCKS;
    private static final ForgeConfigSpec.IntValue RESPAWN_RELAXED_SKY_LIGHT;

    // Penalty settings
    private static final ForgeConfigSpec.BooleanValue PENALTY_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> PENALTY_EFFECTS;

    // Baked values
    public static int nearbyRadius = 100;
    public static int nearbyRadiusMin = 50;
    public static int blindnessMaskTicks = 60;
    public static int streetBaselineY = -1;
    public static int streetBaselineTolerance = 2;
    public static int skyClearanceBlocks = 16;
    public static int relaxedSkyLight = 5;
    public static boolean penaltyEnabled = true;
    public static List<String> penaltyEffects = new ArrayList<>();

    static {
        BUILDER.push("respawn");
        RESPAWN_NEARBY_RADIUS = BUILDER
                .comment(
                        "MAXIMUM respawn radius, in blocks (the OUTER limit of where you can land).",
                        "After clicking 'Respawn Nearby' you spawn at a RANDOM safe surface spot whose",
                        "distance from the death point is between nearbyRadiusMin (inner) and nearbyRadius (outer).",
                        "Larger value = you may respawn farther away from where you died.",
                        "Allowed range: 1 ~ 500 whole blocks (default 100). Please enter a whole positive number (no decimals, no negatives).",
                        "Bad input is auto-fixed on load and never crashes: a number outside 1~500 is clamped to the",
                        "nearest limit (9999 -> 500, -5 -> 1), a decimal inside the range is rounded down (25.7 -> 25),",
                        "and non-numeric text (e.g. \"abc\") falls back to the default.",
                        "MUST be >= nearbyRadiusMin -- if nearbyRadius < nearbyRadiusMin the mod refuses to start and logs an error.",
                        "",
                        "最大重生半径（格），即落点距离死亡点的外限。",
                        "点击“附近重生”后，会在距离死亡点 nearbyRadiusMin（内限）~ nearbyRadius（外限）",
                        "之间的环带内随机选择一个安全地表落点。数值越大，可能重生得离死亡点越远。",
                        "允许范围：1 ~ 500 的整数（默认 100）。请输入正整数（不要小数、不要负数）。",
                        "非数字文本（如 \"abc\"）会回退到默认值。",
                        "必须 >= nearbyRadiusMin —— 否则模组拒绝启动并在日志中报错。"
                )
                .defineInRange("nearbyRadius", 100, 1, 500);
        RESPAWN_NEARBY_RADIUS_MIN = BUILDER
                .comment(
                        "MINIMUM respawn radius, in blocks (the INNER limit, measured from the death point).",
                        "Set to 0 to allow respawning right on top of your death spot.",
                        "Allowed range: 0 ~ 100 whole blocks (default 50). Please enter a whole number (0 is allowed, no decimals, no negatives).",
                        "Bad input is auto-fixed on load and never crashes: a number outside 0~100 is clamped to the",
                        "nearest limit (-5 -> 0, 999 -> 100), a decimal inside the range is rounded down (50.7 -> 50),",
                        "and non-numeric text falls back to the default.",
                        "MUST be <= nearbyRadius -- if nearbyRadius < nearbyRadiusMin the mod refuses to start and logs an error.",
                        "",
                        "最小重生半径（格），即落点距离死亡点的内限。",
                        "设为 0 允许直接在死亡点原地重生。",
                        "允许范围：0 ~ 100 的整数（默认 50，0 合法，不要小数、不要负数）。",
                        "必须 <= nearbyRadius —— 否则模组拒绝启动并在日志中报错。"
                )
                .defineInRange("nearbyRadiusMin", 50, 0, 100);
        RESPAWN_BLINDNESS_MASK_TICKS = BUILDER
                .comment(
                        "Blindness duration, in ticks, applied the instant you respawn nearby.",
                        "It hides the visual jump from the vanilla spawn point over to your death spot.",
                        "20 ticks = 1 second, so the default 60 = 3 seconds. Set to 0 to disable it.",
                        "Settable range: 0 ~ 600.",
                        "",
                        "附近重生瞬间施加的失明效果时长（tick，20 tick = 1 秒）。",
                        "用于掩盖从原版出生点传送到死亡点附近的画面跳变。默认 60 = 3 秒，设为 0 关闭。",
                        "可设范围：0 ~ 600。"
                )
                .defineInRange("blindnessMaskTicks", 60, 0, 600);
        RESPAWN_STREET_BASELINE_Y = BUILDER
                .comment(
                        "Street-level baseline Y for city maps (OPTIONAL, default -1 = DISABLED).",
                        "When your world is a custom city map whose streets all sit on one flat height",
                        "(e.g. every street is at Y=65), set this to that Y. Landing spots are then",
                        "PREFERRED to be within streetBaselineTolerance blocks of this height, which stops",
                        "you from respawning on rooftops, elevated roads or skybridges when you die on a",
                        "street shaded by tall buildings.",
                        "-1 = disabled (normal behaviour, any open-sky surface is accepted).",
                        "Allowed range: -1 ~ 512. If enabled but no candidate matches the baseline near the",
                        "death point, the mod automatically falls back to the normal surface rules -- you",
                        "will never be sent to world spawn just because the baseline did not match.",
                        "",
                        "城区地图的街道基准高度 Y（可选，默认 -1 = 关闭）。",
                        "如果你的世界是街道层统一平整的自建城区地图（例如所有街道都在 Y=65），",
                        "把它设为该高度。之后选点会优先落在基准高度 ± streetBaselineTolerance 范围内，",
                        "防止死在被高楼遮阴的街道上时复活到楼顶、高架路或天桥上。",
                        "-1 = 关闭（正常行为，接受任意露天表面）。允许范围：-1 ~ 512。",
                        "开启后如果死亡点附近没有任何候选点匹配基准高度，会自动回退到普通地表规则 ——"
                )
                .defineInRange("streetBaselineY", -1, -1, 512);
        RESPAWN_STREET_BASELINE_TOLERANCE = BUILDER
                .comment(
                        "How many blocks above/below streetBaselineY a landing spot may be and still count",
                        "as street level (only used when streetBaselineY >= 0).",
                        "Example: baseline 65 + tolerance 2 accepts Y 63~67.",
                        "Allowed range: 0 ~ 16 (default 2).",
                        "",
                        "落点与 streetBaselineY 的允许高度差（仅在 streetBaselineY >= 0 时生效）。",
                        "例：基准 65 + 容差 2 = 接受 Y 63~67 的落点。允许范围：0 ~ 16（默认 2）。"
                )
                .defineInRange("streetBaselineTolerance", 2, 0, 16);
        RESPAWN_SKY_CLEARANCE_BLOCKS = BUILDER
                .comment(
                        "How many blocks ABOVE a candidate landing spot are scanned for a light-blocking",
                        "roof (opaque solid blocks only -- leaves, glass and water do NOT count).",
                        "A spot with an opaque block within this distance above it is treated as 'under a roof'",
                        "and rejected in EVERY pass (this is the hard underground/indoor guard).",
                        "Larger value = stricter (big overhangs and eaves are also rejected).",
                        "Allowed range: 1 ~ 64 (default 16).",
                        "",
                        "候选落点头顶向上扫描“挡光屋顶”的距离（格）。只认不透光实体方块 ——",
                        "树叶、玻璃、水都不算遮挡。头顶该距离内存在不透光方块的位置会被视为“屋顶下”，",
                        "在所有轮次中一律拒绝（这是防地下/防室内的硬闸门）。",
                        "数值越大越严格（大挑檐、大屋檐也会被拒）。允许范围：1 ~ 64（默认 16）。"
                )
                .defineInRange("skyClearanceBlocks", 16, 1, 64);
        RESPAWN_RELAXED_SKY_LIGHT = BUILDER
                .comment(
                        "Sky-light threshold (0~15) used by the RELAXED respawn passes, after every",
                        "strict pass (sky light 15 = fully open sky) failed near the death point.",
                        "5 (default) accepts shaded city streets and dense jungle understory while",
                        "still rejecting deep cave interiors; 0 disables the light check entirely",
                        "(the roof geometry guard still applies). Allowed range: 0 ~ 14.",
                        "",
                        "放宽轮次使用的天空光阈值（0~15）。当所有严格轮（天空光 15 = 完全露天）",
                        "在死亡点附近都失败后，放宽轮改用此阈值。默认 5：接受被高楼遮阴的街道和",
                        "密林林下地面，但仍会拒绝深洞穴内部；设为 0 完全关闭光照检查",
                        "（头顶几何遮挡闸门仍然生效）。允许范围：0 ~ 14。"
                )
                .defineInRange("relaxedSkyLight", 5, 0, 14);
        BUILDER.pop();

        BUILDER.push("penalty");
        PENALTY_ENABLED = BUILDER
                .comment(
                        "Master switch for the death-penalty debuffs listed below.",
                        "true = apply the 'effects' list after a nearby respawn; false = apply nothing.",
                        "",
                        "死亡惩罚负面效果的总开关。",
                        "true = 附近重生后施加下方 'effects' 列表中的效果；false = 什么都不施加。"
                )
                .define("enabled", true);
        PENALTY_EFFECTS = BUILDER
                .comment(
                        "Death-penalty debuffs applied after a nearby respawn (only when [penalty] enabled = true).",
                        "Each list entry uses the format:  \"effect_id,duration_ticks,amplifier\"",
                        "  effect_id       registry name, e.g. minecraft:weakness / minecraft:slowness / minecraft:hunger",
                        "  duration_ticks  how long it lasts, in ticks (20 ticks = 1 second, so 600 = 30 seconds)",
                        "  amplifier       effect level minus 1 (0 = level I, 1 = level II, 2 = level III, ...)",
                        "Example: \"minecraft:weakness,600,0\" gives Weakness I for 30 seconds.",
                        "Add, edit or remove entries freely; an empty list means no penalty debuffs.",
                        "",
                        "附近重生后施加的死亡惩罚负面效果（仅当 [penalty] enabled = true 时生效）。",
                        "每条列表项格式：\"效果ID,持续tick,等级\"",
                        "  效果ID    注册名，如 minecraft:weakness（虚弱）/ minecraft:slowness（缓慢）/ minecraft:hunger（饥饿）",
                        "  持续tick  持续时间，单位 tick（20 tick = 1 秒，600 = 30 秒）",
                        "  等级      效果等级减 1（0 = I 级，1 = II 级，2 = III 级，依此类推）",
                        "示例：\"minecraft:weakness,600,0\" = 虚弱 I 持续 30 秒。",
                        "可自由增删改条目；空列表 = 不施加任何惩罚效果。"
                )
                .defineList("effects",
                        List.of("minecraft:weakness,600,0", "minecraft:slowness,600,0"),
                        o -> o instanceof String s && s.matches("[a-z0-9_]+:[a-z0-9_]+,\\d+,\\d+"));
        BUILDER.pop();

        SERVER_SPEC = BUILDER.build();
    }

    public static void bake() {
        nearbyRadius = RESPAWN_NEARBY_RADIUS.get();
        nearbyRadiusMin = RESPAWN_NEARBY_RADIUS_MIN.get();
        blindnessMaskTicks = RESPAWN_BLINDNESS_MASK_TICKS.get();
        streetBaselineY = RESPAWN_STREET_BASELINE_Y.get();
        streetBaselineTolerance = RESPAWN_STREET_BASELINE_TOLERANCE.get();
        skyClearanceBlocks = RESPAWN_SKY_CLEARANCE_BLOCKS.get();
        relaxedSkyLight = RESPAWN_RELAXED_SKY_LIGHT.get();
        penaltyEnabled = PENALTY_ENABLED.get();
        penaltyEffects = new ArrayList<>();
        for (String s : PENALTY_EFFECTS.get()) {
            penaltyEffects.add(s);
        }

        // Fail-fast cross-field validation. Bad single values are already normalized by Forge +
        // night-config BEFORE we read them here, so none of them can crash us: an out-of-range
        // number is clamped to the nearest boundary, an in-range decimal is truncated to a whole
        // number (night-config casts to Number and calls intValue()), and non-numeric input falls
        // back to the default. The ONE thing Forge cannot catch is two individually-valid values
        // that contradict each other (max radius < min radius): findSafeSpawn() would then compute
        // a negative random span and silently pick a reversed range. Refuse to start with a clear,
        // actionable message instead of shipping that confusing bug.
        if (nearbyRadius < nearbyRadiusMin) {
            throw new IllegalArgumentException("[NearbyRespawn] Invalid config in nearbyrespawn-server.toml: "
                    + "'nearbyRadius' (max = " + nearbyRadius + ") must be >= 'nearbyRadiusMin' (min = "
                    + nearbyRadiusMin + "). Raise nearbyRadius or lower nearbyRadiusMin, then restart.");
        }
    }
}
