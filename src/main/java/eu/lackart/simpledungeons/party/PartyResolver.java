package eu.lackart.simpledungeons.party;

import eu.lackart.simpledungeons.config.DungeonDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SimpleClans (opcjonalnie, refleksja) albo party w promieniu od lidera.
 */
public final class PartyResolver {

    private final Logger logger;
    private final boolean useSimpleClans;
    private final double partyRadius;

    public PartyResolver(Logger logger, boolean useSimpleClans, double partyRadius) {
        this.logger = logger;
        this.useSimpleClans = useSimpleClans;
        this.partyRadius = partyRadius;
    }

    public List<Player> resolveParty(Player leader, DungeonDefinition def) {
        Set<Player> raw = new LinkedHashSet<>();
        raw.add(leader);
        if (useSimpleClans) {
            Collection<Player> clan = trySimpleClansOnline(leader);
            if (clan != null && !clan.isEmpty()) {
                raw.addAll(clan);
            }
        }
        if (raw.size() < def.getMinPlayers()) {
            for (Player p : leader.getWorld().getPlayers()) {
                if (p.equals(leader)) {
                    continue;
                }
                if (p.getLocation().distanceSquared(leader.getLocation()) <= partyRadius * partyRadius) {
                    raw.add(p);
                    if (raw.size() >= def.getMaxPlayers()) {
                        break;
                    }
                }
            }
        }
        List<Player> list = new ArrayList<>(raw);
        list.sort(Comparator.comparing(p -> p.getLocation().distanceSquared(leader.getLocation())));
        if (list.size() > def.getMaxPlayers()) {
            list = list.subList(0, def.getMaxPlayers());
        }
        return Collections.unmodifiableList(list);
    }

    private Collection<Player> trySimpleClansOnline(Player leader) {
        Plugin plug = Bukkit.getPluginManager().getPlugin("SimpleClans");
        if (plug == null || !plug.isEnabled()) {
            return null;
        }
        try {
            Class<?> mainCl = Class.forName("net.sacredis.sacredisclans.SimpleClans");
            Method getInst = mainCl.getMethod("getInstance");
            Object api = getInst.invoke(null);
            Object clanManager = mainCl.getMethod("getClanManager").invoke(api);
            Method byPlayer = findMethod(clanManager.getClass(),
                    "getClanByPlayerUUID", "getClanByPlayerUniqueId", "getClanByPlayer");
            if (byPlayer == null) {
                return null;
            }
            Class<?> paramType = byPlayer.getParameterTypes()[0];
            Object arg = paramType == UUID.class ? leader.getUniqueId() : leader;
            Object clan = byPlayer.invoke(clanManager, arg);
            if (clan == null) {
                return List.of();
            }
            Method getMembers = findMethod(clan.getClass(), "getMembers", "getAllMembers");
            if (getMembers == null) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Collection<Object> members = (Collection<Object>) getMembers.invoke(clan);
            List<Player> online = new ArrayList<>();
            for (Object mem : members) {
                Player pl = extractOnlinePlayer(mem);
                if (pl != null && pl.isOnline()) {
                    online.add(pl);
                }
            }
            return online;
        } catch (Exception e) {
            logger.log(Level.WARNING, "SimpleClans (refleksja) — pomijam, uzywam promienia: " + e.getMessage(), e);
            return null;
        }
    }

    private static Method findMethod(Class<?> cl, String... names) {
        for (String n : names) {
            for (Method m : cl.getMethods()) {
                if (m.getName().equals(n) && m.getParameterCount() == 1) {
                    return m;
                }
            }
        }
        return null;
    }

    private static Player extractOnlinePlayer(Object mem) throws ReflectiveOperationException {
        if (mem instanceof Player p) {
            return p;
        }
        for (String mn : new String[]{"toPlayer", "getPlayer", "getBukkitPlayer"}) {
            try {
                Method m = mem.getClass().getMethod(mn);
                Object o = m.invoke(mem);
                if (o instanceof Player pl) {
                    return pl;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        try {
            Method getId = mem.getClass().getMethod("getUniqueId");
            UUID u = (UUID) getId.invoke(mem);
            return Bukkit.getPlayer(u);
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }
}
