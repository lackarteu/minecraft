package eu.lackart.simpledungeons.listener;

import eu.lackart.simpledungeons.dungeon.DungeonInstance;
import eu.lackart.simpledungeons.dungeon.DungeonManager;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public final class EntityDeathListener implements Listener {

    private final DungeonManager manager;

    public EntityDeathListener(DungeonManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        String wn = entity.getWorld().getName();
        if (!wn.startsWith("sd_")) {
            return;
        }
        manager.handleVanillaDeath(entity);
        DungeonInstance inst = manager.getByWorld(wn);
        if (inst != null) {
            inst.onBossDeath(entity);
        }
    }
}
