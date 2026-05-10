package eu.lackart.simpledungeons;

import eu.lackart.simpledungeons.command.DungeonCommand;
import eu.lackart.simpledungeons.config.DungeonRegistry;
import eu.lackart.simpledungeons.dungeon.DungeonManager;
import eu.lackart.simpledungeons.listener.EntityDeathListener;
import eu.lackart.simpledungeons.listener.PlayerQuitListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class SimpleDungeonsPlugin extends JavaPlugin {

    private DungeonRegistry dungeonRegistry;
    private DungeonManager dungeonManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            getDataFolder().mkdirs();
        }
        this.dungeonRegistry = new DungeonRegistry(this);
        this.dungeonRegistry.load();
        this.dungeonManager = new DungeonManager(this, dungeonRegistry);
        var cmd = new DungeonCommand(dungeonManager, dungeonRegistry);
        var dungeonCmd = getCommand("dungeon");
        if (dungeonCmd != null) {
            dungeonCmd.setExecutor(cmd);
            dungeonCmd.setTabCompleter(cmd);
        }
        getServer().getPluginManager().registerEvents(new EntityDeathListener(dungeonManager), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(dungeonManager), this);
        getLogger().info("SimpleDungeons wlaczony — " + dungeonRegistry.getAll().size() + " loch(ow) w konfiguracji.");
    }

    @Override
    public void onDisable() {
        if (dungeonManager != null) {
            dungeonManager.shutdownAll();
        }
    }

    public DungeonRegistry getDungeonRegistry() {
        return dungeonRegistry;
    }

    public DungeonManager getDungeonManager() {
        return dungeonManager;
    }
}
