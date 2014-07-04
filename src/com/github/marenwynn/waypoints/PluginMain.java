package com.github.marenwynn.waypoints;

import java.util.ArrayList;
import java.util.HashMap;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.marenwynn.waypoints.commands.PluginCommand;
import com.github.marenwynn.waypoints.commands.SetHomeCmd;
import com.github.marenwynn.waypoints.commands.SetSpawnCmd;
import com.github.marenwynn.waypoints.commands.WPAddCmd;
import com.github.marenwynn.waypoints.commands.WPDescCmd;
import com.github.marenwynn.waypoints.commands.WPDiscoverCmd;
import com.github.marenwynn.waypoints.commands.WPIconCmd;
import com.github.marenwynn.waypoints.commands.WPMoveCmd;
import com.github.marenwynn.waypoints.commands.WPReloadCmd;
import com.github.marenwynn.waypoints.commands.WPRemoveCmd;
import com.github.marenwynn.waypoints.commands.WPRenameCmd;
import com.github.marenwynn.waypoints.commands.WPSelectCmd;
import com.github.marenwynn.waypoints.data.Database;
import com.github.marenwynn.waypoints.data.Msg;
import com.github.marenwynn.waypoints.data.Waypoint;
import com.github.marenwynn.waypoints.listeners.PlayerListener;
import com.github.marenwynn.waypoints.listeners.WaypointListener;
import com.github.marenwynn.waypoints.tasks.TeleportTask;

public class PluginMain extends JavaPlugin {

    private Database                       data;
    private HashMap<String, Waypoint>      selectedWaypoints;
    private HashMap<String, PluginCommand> commands;
    private ItemStack                      beacon;

    public Permission                      perms;

    @Override
    public void onEnable() {
        saveResource("CHANGELOG.txt", true);

        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager()
                    .getRegistration(net.milkbowl.vault.permission.Permission.class);

            if (permissionProvider != null)
                perms = permissionProvider.getProvider();
        } else {
            getLogger().info("Vault not found. Discovery mode disabled.");
        }

        data = new Database(this);
        selectedWaypoints = new HashMap<String, Waypoint>();
        commands = new HashMap<String, PluginCommand>();

        commands.put("sethome", new SetHomeCmd(this));
        commands.put("setspawn", new SetSpawnCmd());
        commands.put("add", new WPAddCmd(this));
        commands.put("desc", new WPDescCmd(this));

        if (perms != null)
            commands.put("discover", new WPDiscoverCmd(this));

        commands.put("icon", new WPIconCmd(this));
        commands.put("move", new WPMoveCmd(this));
        commands.put("reload", new WPReloadCmd(this));
        commands.put("remove", new WPRemoveCmd(this));
        commands.put("rename", new WPRenameCmd(this));
        commands.put("select", new WPSelectCmd(this));

        getCommand("wp").setExecutor(this);
        getCommand("sethome").setExecutor(this);

        if (data.ENABLE_BEACON) {
            beacon = new ItemStack(Material.COMPASS, 1);

            ItemMeta im = beacon.getItemMeta();
            im.setDisplayName(Util.color("&aWaypoint Beacon"));

            ArrayList<String> lore = new ArrayList<String>();
            lore.add(Util.color("&fBroadcasts signal to"));
            lore.add(Util.color("&fwaypoint directory for"));
            lore.add(Util.color("&fremote connection."));
            lore.add(Util.color("&8&oRight-click to use"));

            im.setLore(lore);
            beacon.setItemMeta(im);

            ShapedRecipe sr = new ShapedRecipe(beacon);
            sr.shape("RRR", "RCR", "RRR");
            sr.setIngredient('R', Material.REDSTONE);
            sr.setIngredient('C', Material.COMPASS);
            getServer().addRecipe(sr);
        }

        getServer().getPluginManager().registerEvents(new WaypointListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();
        PluginCommand executor = null;

        if (cmd.equals("wp")) {
            if (args.length > 0) {
                String key = args[0].toLowerCase();

                if (commands.containsKey(key))
                    executor = commands.get(key);
            }

            if (executor == null) {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    ArrayList<Waypoint> playerPoints = data.getWaypointsForPlayer(p.getUniqueId());
                    StringBuilder sb = new StringBuilder();

                    for (int i = 0; i < playerPoints.size(); i++) {
                        sb.append(playerPoints.get(i).getName());

                        if (i < playerPoints.size() - 1)
                            sb.append("&6, ");
                    }

                    if (sb.length() > 0)
                        Msg.LIST_HOME_WAYPOINTS.sendTo(sender, sb.toString());
                }

                sender.sendMessage("Maren's Waypoints v" + getDescription().getVersion() + " by Marenwynn.");
                return false;
            }
        } else if (cmd.equals("sethome")) {
            executor = commands.get("sethome");
        } else if (cmd.equals("setspawn")) {
            executor = commands.get("setspawn");
        }

        if (!(sender instanceof Player) && !executor.isConsoleExecutable()) {
            Msg.CMD_NO_CONSOLE.sendTo(sender);
            return true;
        }

        if (!executor.hasRequiredPerm(sender)) {
            Msg.NO_PERMS.sendTo(sender);
            return true;
        }

        return executor.onCommand(sender, command, label, args);
    }

    public void teleportPlayer(Player p, Waypoint wp) {
        new TeleportTask(this, p, wp).runTask(this);
    }

    public void openWaypointMenu(Player p, Waypoint currentWaypoint, boolean addServerWaypoints,
            boolean addHomeWaypoints, boolean select) {
        ArrayList<Waypoint> accessList = new ArrayList<Waypoint>();

        if (!select) {
            if (currentWaypoint != null) {
                Msg.OPEN_WP_MENU.sendTo(p, currentWaypoint.getName());
                p.teleport(currentWaypoint.getLocation(), TeleportCause.PLUGIN);
            } else {
                Msg.REMOTELY_ACCESSED.sendTo(p);
            }

            if (p.hasPermission("wp.access.spawn")) {
                if (currentWaypoint != null && currentWaypoint.getName().equals("Spawn")) {
                    accessList.add(currentWaypoint);
                } else {
                    Waypoint spawn = new Waypoint("Spawn", p.getWorld().getSpawnLocation());
                    spawn.setIcon(Material.NETHER_STAR);
                    accessList.add(spawn);
                }
            }

            if (p.hasPermission("wp.access.bed") && p.getBedSpawnLocation() != null) {
                Waypoint bed = new Waypoint("Bed", p.getBedSpawnLocation());
                bed.setIcon(Material.BED);
                accessList.add(bed);
            }
        }

        if (addServerWaypoints && (!select || p.hasPermission("wp.admin")))
            for (Waypoint wp : data.getAllWaypoints().values())
                if (p.hasPermission("wp.access." + Util.getKey(wp.getName())))
                    accessList.add(wp);

        if (addHomeWaypoints)
            for (Waypoint wp : data.getWaypointsForPlayer(p.getUniqueId()))
                accessList.add(wp);

        new WaypointMenu(this, p, currentWaypoint, accessList, select).open();
    }

    public boolean setHome(Player p, String waypointName) {
        Location playerLoc = p.getLocation();

        for (Waypoint wp : data.getWaypointsForPlayer(p.getUniqueId())) {
            Location waypointLoc = wp.getLocation();

            if (Util.isSameLoc(playerLoc, waypointLoc, true)) {
                Msg.HOME_WP_ALREADY_HERE.sendTo(p, wp.getName());
                return false;
            }

            if (waypointName.equals(wp.getName()) || waypointName.equals("Bed") || waypointName.equals("Spawn")) {
                Msg.WP_DUPLICATE_NAME.sendTo(p, waypointName);
                return false;
            }
        }

        Waypoint wp = new Waypoint(waypointName, playerLoc);
        wp.setDescription(Msg.SETHOME_DEFAULT_DESC.toString());
        Waypoint replaced = data.addWaypointForPlayer(p.getUniqueId(), wp);

        if (replaced != null)
            Msg.HOME_WP_REPLACED.sendTo(p, replaced.getName(), wp.getName());
        else
            Msg.HOME_WP_CREATED.sendTo(p, waypointName);

        return true;
    }

    public Waypoint getSelectedWaypoint(String playerName) {
        if (selectedWaypoints.containsKey(playerName))
            return selectedWaypoints.get(playerName);

        return null;
    }

    public void setSelectedWaypoint(CommandSender sender, Waypoint wp) {
        selectedWaypoints.put(sender.getName(), wp);

        Location loc = wp.getLocation();

        Msg.BORDER.sendTo(sender);
        Msg.SELECTED_1.sendTo(sender, wp.getName(), loc.getWorld().getName());
        Msg.BORDER.sendTo(sender);
        Msg.SELECTED_2.sendTo(sender, loc.getBlockX(), (int) loc.getPitch());
        Msg.SELECTED_3.sendTo(sender, loc.getBlockY(), (int) loc.getYaw());
        Msg.SELECTED_4.sendTo(sender, loc.getBlockZ());
        Msg.BORDER.sendTo(sender);

        if (wp.getDescription().equals("")) {
            Msg.WP_NO_DESC.sendTo(sender);
        } else {
            for (String line : Util.getWrappedLore(wp.getDescription(), 40))
                Msg.LORE_LINE.sendTo(sender, line);
        }

        Msg.BORDER.sendTo(sender);
    }

    public void clearSelectedWaypoint(String playerName) {
        if (selectedWaypoints.containsKey(playerName))
            selectedWaypoints.remove(playerName);
    }

    public ItemStack getBeacon() {
        return beacon;
    }

    public Database getData() {
        return data;
    }

}
