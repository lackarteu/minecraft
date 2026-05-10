package eu.lackart.simpledungeons.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class DungeonDefinition {

    private final String id;
    private final String displayName;
    private final String templateFolder;
    private final String templateBasePath;
    private final int minPlayers;
    private final int maxPlayers;
    private final double spawnOffsetX;
    private final double spawnOffsetY;
    private final double spawnOffsetZ;
    private final double mobCenterX;
    private final double mobCenterY;
    private final double mobCenterZ;
    private final double mobSpread;
    private final Map<org.bukkit.entity.EntityType, Integer> mobs;
    private final String bossMythicId;
    private final double bossOffsetX;
    private final double bossOffsetY;
    private final double bossOffsetZ;
    private final List<LootEntry> loot;

    public DungeonDefinition(String id, ConfigurationSection sec) {
        this.id = id;
        this.displayName = sec.getString("display-name", id);
        this.templateFolder = sec.getString("template-folder", id);
        this.templateBasePath = sec.getString("template-base-path", "plugins/DungeonsXL/maps");
        this.minPlayers = sec.getInt("min-players", 1);
        this.maxPlayers = sec.getInt("max-players", 4);
        ConfigurationSection pso = sec.getConfigurationSection("player-spawn-offset");
        this.spawnOffsetX = pso != null ? pso.getDouble("x", 0.5) : 0.5;
        this.spawnOffsetY = pso != null ? pso.getDouble("y", 0) : 0;
        this.spawnOffsetZ = pso != null ? pso.getDouble("z", 0.5) : 0.5;
        ConfigurationSection ma = sec.getConfigurationSection("mob-arena");
        this.mobCenterX = ma != null ? ma.getDouble("center-x", 0) : 0;
        this.mobCenterY = ma != null ? ma.getDouble("center-y", 64) : 64;
        this.mobCenterZ = ma != null ? ma.getDouble("center-z", 0) : 0;
        this.mobSpread = ma != null ? ma.getDouble("spread", 5) : 5;
        this.mobs = new EnumMap<>(org.bukkit.entity.EntityType.class);
        ConfigurationSection mobSec = sec.getConfigurationSection("mobs");
        if (mobSec != null) {
            for (String key : mobSec.getKeys(false)) {
                try {
                    org.bukkit.entity.EntityType et = org.bukkit.entity.EntityType.valueOf(key.toUpperCase());
                    int amt = mobSec.getInt(key);
                    if (amt > 0) {
                        mobs.put(et, amt);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection boss = sec.getConfigurationSection("boss");
        String mid = boss != null ? boss.getString("mythic-id", "") : "";
        this.bossMythicId = mid == null ? "" : mid;
        this.bossOffsetX = boss != null ? boss.getDouble("offset-x", 0) : 0;
        this.bossOffsetY = boss != null ? boss.getDouble("offset-y", 0) : 0;
        this.bossOffsetZ = boss != null ? boss.getDouble("offset-z", 8) : 8;
        this.loot = new ArrayList<>();
        for (Map<?, ?> map : sec.getMapList("loot")) {
            String mat = String.valueOf(map.get("material"));
            int amount = map.get("amount") instanceof Number n ? n.intValue() : 1;
            try {
                loot.add(new LootEntry(Material.valueOf(mat.toUpperCase()), Math.max(1, amount)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTemplateFolder() {
        return templateFolder;
    }

    public String getTemplateBasePath() {
        return templateBasePath;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public double getSpawnOffsetX() {
        return spawnOffsetX;
    }

    public double getSpawnOffsetY() {
        return spawnOffsetY;
    }

    public double getSpawnOffsetZ() {
        return spawnOffsetZ;
    }

    public double getMobCenterX() {
        return mobCenterX;
    }

    public double getMobCenterY() {
        return mobCenterY;
    }

    public double getMobCenterZ() {
        return mobCenterZ;
    }

    public double getMobSpread() {
        return mobSpread;
    }

    public Map<org.bukkit.entity.EntityType, Integer> getMobs() {
        return Collections.unmodifiableMap(mobs);
    }

    public String getBossMythicId() {
        return bossMythicId == null ? "" : bossMythicId;
    }

    public double getBossOffsetX() {
        return bossOffsetX;
    }

    public double getBossOffsetY() {
        return bossOffsetY;
    }

    public double getBossOffsetZ() {
        return bossOffsetZ;
    }

    public List<LootEntry> getLoot() {
        return Collections.unmodifiableList(loot);
    }

    public record LootEntry(Material material, int amount) {
    }
}
