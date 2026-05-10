package eu.lackart.simpledungeons.dungeon;

import eu.lackart.simpledungeons.SimpleDungeonsPlugin;
import eu.lackart.simpledungeons.config.DungeonDefinition;
import eu.lackart.simpledungeons.config.DungeonRegistry;
import eu.lackart.simpledungeons.party.PartyResolver;
import eu.lackart.simpledungeons.util.WorldCopy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class DungeonManager {

    private final SimpleDungeonsPlugin plugin;
    private final DungeonRegistry registry;
    private final PartyResolver partyResolver;
    private final NamespacedKey instanceKey;
    private final NamespacedKey mobKey;
    private final Map<UUID, DungeonInstance> instances = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToInstance = new ConcurrentHashMap<>();
    private final Map<String, DungeonInstance> worldToInstance = new ConcurrentHashMap<>();
    private final AtomicInteger idSeq = new AtomicInteger();

    public DungeonManager(SimpleDungeonsPlugin plugin, DungeonRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.partyResolver = new PartyResolver(
                plugin.getLogger(),
                registry.useSimpleClans(),
                registry.getPartyRadius()
        );
        this.instanceKey = new NamespacedKey(plugin, "instance");
        this.mobKey = new NamespacedKey(plugin, "sd_mob");
    }

    public NamespacedKey getInstanceKey() {
        return instanceKey;
    }

    public NamespacedKey getMobKey() {
        return mobKey;
    }

    public DungeonInstance getByPlayer(UUID playerId) {
        UUID inst = playerToInstance.get(playerId);
        return inst == null ? null : instances.get(inst);
    }

    public DungeonInstance getByWorld(String worldName) {
        return worldToInstance.get(worldName);
    }

    public boolean join(Player leader, String dungeonKey) {
        if (getByPlayer(leader.getUniqueId()) != null) {
            leader.sendMessage(ChatColor.RED + "Juz jestes w instancji lochu. Uzyj /dungeon leave.");
            return false;
        }
        DungeonDefinition def = registry.get(dungeonKey);
        if (def == null) {
            leader.sendMessage(ChatColor.RED + "Nieznany loch: " + dungeonKey);
            return false;
        }
        if (!def.getBossMythicId().isEmpty() && Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            leader.sendMessage(ChatColor.RED + "Ten loch wymaga MythicMobs (boss).");
            return false;
        }
        List<Player> party = new ArrayList<>(partyResolver.resolveParty(leader, def));
        if (!party.contains(leader)) {
            party.add(0, leader);
        }
        if (party.size() < def.getMinPlayers()) {
            leader.sendMessage(ChatColor.RED + "Za malo graczy (min " + def.getMinPlayers()
                    + "). Zbierz klan (SimpleClans) lub stancie blisko lidera.");
            return false;
        }
        if (party.size() > def.getMaxPlayers()) {
            leader.sendMessage(ChatColor.RED + "Za duzo graczy (max " + def.getMaxPlayers() + ").");
            return false;
        }

        String worldName = "sd_" + def.getId() + "_" + idSeq.incrementAndGet();
        Path template = resolveTemplate(def);
        Path target = Bukkit.getWorldContainer().toPath().resolve(worldName);

        try {
            if (java.nio.file.Files.exists(target)) {
                WorldCopy.deleteDirectory(target);
            }
            WorldCopy.copyDirectory(template, target);
        } catch (IOException e) {
            leader.sendMessage(ChatColor.RED + "Blad kopiowania mapy. Sprawdz sciezke szablonu w dungeons.yml.");
            plugin.getLogger().log(Level.SEVERE, "Kopiowanie swiata lochu", e);
            return false;
        }

        World world = Bukkit.createWorld(WorldCreator.name(worldName));
        if (world == null) {
            leader.sendMessage(ChatColor.RED + "Nie udalo sie zaladowac swiata instancji.");
            try {
                WorldCopy.deleteDirectory(target);
            } catch (IOException ignored) {
            }
            return false;
        }

        UUID iid = UUID.randomUUID();
        DungeonInstance instance = new DungeonInstance(plugin, this, iid, worldName, def, party);
        instances.put(iid, instance);
        worldToInstance.put(worldName, instance);
        for (Player p : party) {
            playerToInstance.put(p.getUniqueId(), iid);
        }
        instance.begin();
        party.forEach(p -> p.sendMessage(ChatColor.GREEN + "Instancja lochu " + ChatColor.WHITE + def.getId()
                + ChatColor.GREEN + " — powodzenia!"));
        return true;
    }

    private Path resolveTemplate(DungeonDefinition def) {
        java.io.File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
        return serverRoot.toPath().resolve(def.getTemplateBasePath()).resolve(def.getTemplateFolder());
    }

    public boolean leave(Player player) {
        DungeonInstance inst = getByPlayer(player.getUniqueId());
        if (inst == null) {
            player.sendMessage(ChatColor.RED + "Nie jestes w lochu.");
            return false;
        }
        inst.removeMember(player.getUniqueId());
        playerToInstance.remove(player.getUniqueId());
        teleportToSpawn(player);
        player.sendMessage(ChatColor.YELLOW + "Opuszczasz loch.");
        if (inst.isEmpty() || !inst.hasAnyMemberOnline()) {
            destroyInstance(inst);
        }
        return true;
    }

    public void onPlayerQuit(Player player) {
        DungeonInstance inst = getByPlayer(player.getUniqueId());
        if (inst == null) {
            return;
        }
        inst.removeMember(player.getUniqueId());
        playerToInstance.remove(player.getUniqueId());
        if (inst.isEmpty() || !inst.hasAnyMemberOnline()) {
            destroyInstance(inst);
        }
    }

    public void teleportToSpawn(Player player) {
        String wn = registry.getSpawnWorldName();
        World w = Bukkit.getWorld(wn);
        if (w == null && !Bukkit.getWorlds().isEmpty()) {
            w = Bukkit.getWorlds().get(0);
        }
        if (w != null) {
            Location loc = w.getSpawnLocation();
            player.teleport(loc);
        }
    }

    public void handleVanillaDeath(org.bukkit.entity.Entity entity) {
        if (!(entity.getWorld().getName().startsWith("sd_"))) {
            return;
        }
        DungeonInstance inst = getByWorld(entity.getWorld().getName());
        if (inst != null && !inst.isFinished()) {
            inst.onVanillaMobDeath(entity);
        }
    }

    public void scheduleDestroy(DungeonInstance instance, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> destroyInstance(instance), delayTicks);
    }

    public void destroyInstance(DungeonInstance instance) {
        if (instance == null) {
            return;
        }
        instances.remove(instance.getInstanceId());
        worldToInstance.remove(instance.getWorldName());
        for (UUID id : instance.getMembers()) {
            playerToInstance.remove(id);
        }
        String wn = instance.getWorldName();
        World world = Bukkit.getWorld(wn);
        if (world != null) {
            for (Player p : new ArrayList<>(world.getPlayers())) {
                teleportToSpawn(p);
            }
            Bukkit.unloadWorld(world, false);
        }
        Path path = Bukkit.getWorldContainer().toPath().resolve(wn);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                WorldCopy.deleteDirectory(path);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Usuwanie folderu swiata " + wn, e);
            }
        });
    }

    public void shutdownAll() {
        for (DungeonInstance inst : new ArrayList<>(instances.values())) {
            destroyInstance(inst);
        }
    }
}
