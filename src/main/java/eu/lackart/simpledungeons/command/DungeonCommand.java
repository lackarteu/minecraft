package eu.lackart.simpledungeons.command;

import eu.lackart.simpledungeons.config.DungeonRegistry;
import eu.lackart.simpledungeons.dungeon.DungeonManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class DungeonCommand implements CommandExecutor, TabCompleter {

    private final DungeonManager manager;
    private final DungeonRegistry registry;

    public DungeonCommand(DungeonManager manager, DungeonRegistry registry) {
        this.manager = manager;
        this.registry = registry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tylko gracze.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "join" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Uzycie: /dungeon join <nazwa>");
                    return true;
                }
                manager.join(player, args[1]);
            }
            case "leave" -> manager.leave(player);
            case "list" -> {
                player.sendMessage(ChatColor.GOLD + "Dostepne lochy:");
                registry.getAll().forEach((id, def) -> player.sendMessage(ChatColor.GRAY + " - "
                        + ChatColor.WHITE + id + ChatColor.GRAY + " ("
                        + def.getMinPlayers() + "-" + def.getMaxPlayers() + " graczy)"));
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.YELLOW + "/dungeon join <nazwa>" + ChatColor.GRAY + " — start instancji (lider grupy)");
        player.sendMessage(ChatColor.YELLOW + "/dungeon leave" + ChatColor.GRAY + " — wyjscie do spawnu");
        player.sendMessage(ChatColor.YELLOW + "/dungeon list" + ChatColor.GRAY + " — lista lochow");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("join");
            out.add("leave");
            out.add("list");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            out.addAll(registry.getAll().keySet());
        }
        return out.stream().filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase())).toList();
    }
}
