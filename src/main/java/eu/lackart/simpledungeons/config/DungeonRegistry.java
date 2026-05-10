package eu.lackart.simpledungeons.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DungeonRegistry {

    private final JavaPlugin plugin;
    private final Map<String, DungeonDefinition> dungeons = new LinkedHashMap<>();
    private FileConfiguration settings = new YamlConfiguration();

    public DungeonRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        dungeons.clear();
        File data = plugin.getDataFolder();
        if (!data.exists()) {
            //noinspection ResultOfMethodCallIgnored
            data.mkdirs();
        }
        plugin.saveResource("dungeons.yml", false);
        File yml = new File(data, "dungeons.yml");
        FileConfiguration root = YamlConfiguration.loadConfiguration(yml);
        settings = root;
        ConfigurationSection dunSec = root.getConfigurationSection("dungeons");
        if (dunSec != null) {
            for (String id : dunSec.getKeys(false)) {
                ConfigurationSection sec = dunSec.getConfigurationSection(id);
                if (sec != null) {
                    dungeons.put(id.toLowerCase(), new DungeonDefinition(id.toLowerCase(), sec));
                }
            }
        }
    }

    public DungeonDefinition get(String id) {
        return id == null ? null : dungeons.get(id.toLowerCase());
    }

    public Map<String, DungeonDefinition> getAll() {
        return Collections.unmodifiableMap(dungeons);
    }

    public FileConfiguration getSettings() {
        return settings;
    }

    public String getSpawnWorldName() {
        return settings.getString("settings.spawn-world", "world");
    }

    public double getPartyRadius() {
        return settings.getDouble("settings.party-radius", 24);
    }

    public boolean useSimpleClans() {
        return settings.getBoolean("settings.use-simple-clans", true);
    }

    public String getBossBroadcast() {
        return settings.getString("settings.boss-broadcast",
                "&6[Loch] &eGrupa &f{player} &eukończyła &f{dungeon}&e!");
    }
}
