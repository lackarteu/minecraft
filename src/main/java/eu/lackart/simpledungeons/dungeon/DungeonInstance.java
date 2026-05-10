package eu.lackart.simpledungeons.dungeon;

import eu.lackart.simpledungeons.SimpleDungeonsPlugin;
import eu.lackart.simpledungeons.config.DungeonDefinition;
import eu.lackart.simpledungeons.config.DungeonDefinition.LootEntry;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class DungeonInstance {

    private final SimpleDungeonsPlugin plugin;
    private final DungeonManager manager;
    private final UUID instanceId;
    private final String worldName;
    private final DungeonDefinition definition;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final AtomicInteger remainingVanilla = new AtomicInteger();
    private final AtomicBoolean bossSpawned = new AtomicBoolean();
    private final AtomicBoolean finished = new AtomicBoolean();
    private volatile UUID bossEntityId;

    public DungeonInstance(SimpleDungeonsPlugin plugin, DungeonManager manager, UUID instanceId,
                           String worldName, DungeonDefinition definition, List<Player> party) {
        this.plugin = plugin;
        this.manager = manager;
        this.instanceId = instanceId;
        this.worldName = worldName;
        this.definition = definition;
        party.forEach(p -> members.add(p.getUniqueId()));
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public String getWorldName() {
        return worldName;
    }

    public DungeonDefinition getDefinition() {
        return definition;
    }

    public Set<UUID> getMembers() {
        return Set.copyOf(members);
    }

    public boolean isMember(UUID id) {
        return members.contains(id);
    }

    public void begin() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().severe("Brak swiata instancji: " + worldName);
            return;
        }
        Location enter = world.getSpawnLocation().clone().add(
                definition.getSpawnOffsetX(), definition.getSpawnOffsetY(), definition.getSpawnOffsetZ());
        for (UUID id : members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.teleport(enter);
            }
        }
        int total = spawnVanillaMobs(world);
        remainingVanilla.set(total);
    }

    private int spawnVanillaMobs(World world) {
        int total = 0;
        var keyInst = manager.getInstanceKey();
        var keyMob = manager.getMobKey();
        Location center = new Location(world,
                definition.getMobCenterX(),
                definition.getMobCenterY(),
                definition.getMobCenterZ());
        double spread = definition.getMobSpread();
        for (var e : definition.getMobs().entrySet()) {
            var type = e.getKey();
            int n = e.getValue();
            for (int i = 0; i < n; i++) {
                Location loc = jitter(center, spread);
                Entity ent = world.spawnEntity(loc, type, false);
                if (ent instanceof LivingEntity living) {
                    living.getPersistentDataContainer().set(keyInst, PersistentDataType.STRING, instanceId.toString());
                    living.getPersistentDataContainer().set(keyMob, PersistentDataType.BYTE, (byte) 1);
                }
                total++;
            }
        }
        return total;
    }

    private static Location jitter(Location center, double spread) {
        double dx = (Math.random() * 2 - 1) * spread;
        double dz = (Math.random() * 2 - 1) * spread;
        return center.clone().add(dx, 0, dz);
    }

    public void onVanillaMobDeath(Entity entity) {
        if (finished.get()) {
            return;
        }
        var pdc = entity.getPersistentDataContainer();
        if (!pdc.has(manager.getMobKey(), PersistentDataType.BYTE)) {
            return;
        }
        String idStr = pdc.get(manager.getInstanceKey(), PersistentDataType.STRING);
        if (idStr == null || !instanceId.toString().equals(idStr)) {
            return;
        }
        int left = remainingVanilla.decrementAndGet();
        if (left <= 0 && bossSpawned.compareAndSet(false, true)) {
            spawnBoss();
        }
    }

    private void spawnBoss() {
        if (definition.getBossMythicId() == null || definition.getBossMythicId().isEmpty()) {
            finish(null);
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        Location center = new Location(world,
                definition.getMobCenterX(),
                definition.getMobCenterY(),
                definition.getMobCenterZ());
        Location bossLoc = center.clone().add(
                definition.getBossOffsetX(),
                definition.getBossOffsetY(),
                definition.getBossOffsetZ());
        try {
            MythicBukkit.inst().getMobManager().getMythicMob(definition.getBossMythicId())
                    .ifPresentOrElse(mob -> {
                        var am = mob.spawn(BukkitAdapter.adapt(bossLoc), 1);
                        if (am == null || am.getEntity().getBukkitEntity() == null) {
                            plugin.getLogger().warning("Mythic spawn bossa zwrocil null.");
                            finish(null);
                            return;
                        }
                        bossEntityId = am.getEntity().getBukkitEntity().getUniqueId();
                    }, () -> {
                        plugin.getLogger().warning("Brak MythicMob: " + definition.getBossMythicId() + " — nagrody bez bossa.");
                        finish(null);
                    });
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Spawn bossa MythicMobs", t);
        }
    }

    public void onBossDeath(Entity entity) {
        if (finished.get()) {
            return;
        }
        if (bossEntityId == null || !bossEntityId.equals(entity.getUniqueId())) {
            return;
        }
        Player killer = entity instanceof LivingEntity le ? le.getKiller() : null;
        finish(killer);
    }

    public boolean hasAnyMemberOnline() {
        for (UUID id : members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                return true;
            }
        }
        return false;
    }

    public void finish(Player lastDamager) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        String plainDisplay = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', definition.getDisplayName()));
        String msg = plugin.getDungeonRegistry().getBossBroadcast()
                .replace("{dungeon}", plainDisplay.isEmpty() ? definition.getId() : plainDisplay)
                .replace("{player}", lastDamager != null ? lastDamager.getName() : "Grupa");
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));

        for (UUID id : members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                for (LootEntry loot : definition.getLoot()) {
                    p.getInventory().addItem(new org.bukkit.inventory.ItemStack(loot.material(), loot.amount()));
                }
                manager.teleportToSpawn(p);
            }
        }
        manager.scheduleDestroy(this, 60L);
    }

    public void removeMember(Player player) {
        members.remove(player.getUniqueId());
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    public boolean isFinished() {
        return finished.get();
    }

    public UUID getBossEntityId() {
        return bossEntityId;
    }
}
