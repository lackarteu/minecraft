package eu.lackart.simpledungeons.listener;

import eu.lackart.simpledungeons.dungeon.DungeonManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerQuitListener implements Listener {

    private final DungeonManager manager;

    public PlayerQuitListener(DungeonManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.onPlayerQuit(event.getPlayer());
    }
}
