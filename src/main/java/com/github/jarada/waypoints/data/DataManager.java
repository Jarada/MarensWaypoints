package com.github.jarada.waypoints.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.jarada.waypoints.listeners.BeaconListener;
import com.github.jarada.waypoints.listeners.RespawnListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

import com.github.jarada.waypoints.PluginMain;
import com.github.jarada.waypoints.Util;
import com.github.jarada.waypoints.WaypointManager;

public class DataManager {

    private static DataManager dm;
    private PluginMain         pm;
    private WaypointManager    wm;
    private YamlConfiguration  wc;

    private File               playerFolder, waypointDataFile, waypointConfigFile;
    private Map<Msg, String>   messages;

    public int                 MAX_HOME_WAYPOINTS;
    public int                 WP_NAME_MAX_LENGTH, WP_DESC_MAX_LENGTH;
    public boolean             ENABLE_BEACON;
    public boolean             BEACON_UNLIMITED_PERMANENT;
    public ItemStack           BEACON;
    public boolean             HANDLE_RESPAWNING;
    public boolean             RESPAWN_INCLUDE_BED_IN_HOME_SPAWN_MODE;
    public SpawnMode           SPAWN_MODE;
    public String              CITY_WORLD_NAME;
    public boolean             SHOW_DISCOVERABLE_WAYPOINTS;

    public DataManager() {
        pm = PluginMain.getPluginInstance();
        wm = WaypointManager.getManager();
        wc = new YamlConfiguration();

        playerFolder = new File(pm.getDataFolder(), "players");
        messages = new HashMap<Msg, String>();

        // (v2.1.0) Note: For transition; remove data file later
        waypointDataFile = new File(pm.getDataFolder(), "waypoints.db");
        waypointConfigFile = new File (pm.getDataFolder(), "waypoints.yml");
    }

    public static DataManager getManager() {
        if (dm == null)
            dm = new DataManager();

        return dm;
    }

    public void loadConfig() {
        if (!playerFolder.exists())
            playerFolder.mkdirs();

        FileConfiguration config = pm.getConfig();

        config.addDefault("Waypoints.MAX_HOME_WAYPOINTS", 3);
        config.addDefault("Waypoints.WP_NAME_MAX_LENGTH", 18);
        config.addDefault("Waypoints.WP_DESC_MAX_LENGTH", 100);
        config.addDefault("Waypoints.ENABLE_BEACON", true);
        config.addDefault("Waypoints.BEACON_UNLIMITED_PERMANENT", false);
        config.addDefault("Waypoints.HANDLE_RESPAWNING", true);
        config.addDefault("Waypoints.RESPAWN_INCLUDE_BED_IN_HOME_WAYPOINT_LIST", false);
        config.addDefault("Waypoints.SPAWN_MODE", "home");
        config.addDefault("Waypoints.CITY_WORLD_NAME", "world");
        config.addDefault("Waypoints.SHOW_DISCOVERABLE_WAYPOINTS", false);

        for (Msg msg : Msg.values()) {
            String path = "Waypoints.Messages." + msg.name();
            config.addDefault(path, msg.getDefaultMsg());
            messages.put(msg, config.getString(path));
        }

        MAX_HOME_WAYPOINTS = config.getInt("Waypoints.MAX_HOME_WAYPOINTS");
        WP_NAME_MAX_LENGTH = config.getInt("Waypoints.WP_NAME_MAX_LENGTH");
        WP_DESC_MAX_LENGTH = config.getInt("Waypoints.WP_DESC_MAX_LENGTH");
        ENABLE_BEACON = config.getBoolean("Waypoints.ENABLE_BEACON");
        BEACON_UNLIMITED_PERMANENT = config.getBoolean("Waypoints.BEACON_UNLIMITED_PERMANENT");
        HANDLE_RESPAWNING = config.getBoolean("Waypoints.HANDLE_RESPAWNING");
        RESPAWN_INCLUDE_BED_IN_HOME_SPAWN_MODE = config.getBoolean("Waypoints.RESPAWN_INCLUDE_BED_IN_HOME_SPAWN_MODE");
        SPAWN_MODE = SpawnMode.valueOf(config.getString("Waypoints.SPAWN_MODE").toUpperCase());
        CITY_WORLD_NAME = config.getString("Waypoints.CITY_WORLD_NAME");
        SHOW_DISCOVERABLE_WAYPOINTS = config.getBoolean("Waypoints.SHOW_DISCOVERABLE_WAYPOINTS");

        config.options().copyDefaults(true);
        pm.saveConfig();

        if (ENABLE_BEACON) {
            List<String> lore = new ArrayList<String>();
            lore.add(Util.color(Msg.LORE_BEACON_1.toString()));
            lore.add(Util.color(Msg.LORE_BEACON_2.toString()));
            lore.add(Util.color(Msg.LORE_BEACON_3.toString()));
            lore.add(Util.color(Msg.LORE_BEACON_4.toString()));

            BEACON = Util.setItemNameAndLore(new ItemStack(Material.COMPASS, 1), Msg.LORE_BEACON_NAME.toString(), lore);

            ShapedRecipe sr = new ShapedRecipe(new NamespacedKey(pm, "waypointbeacon"), BEACON);
            sr.shape("RRR", "RCR", "RRR").setIngredient('R', Material.REDSTONE).setIngredient('C', Material.COMPASS);
            Bukkit.addRecipe(sr);

            Bukkit.getPluginManager().registerEvents(BeaconListener.getListener(), pm);
        }

        if (HANDLE_RESPAWNING)
            Bukkit.getPluginManager().registerEvents(RespawnListener.getListener(), pm);
    }

    public void reload() {
        if (ENABLE_BEACON) {
            Iterator<Recipe> recipes = Bukkit.recipeIterator();
            Recipe recipe;

            while (recipes.hasNext()) {
                recipe = recipes.next();

                if (recipe != null && recipe.getResult().isSimilar(BEACON))
                    recipes.remove();
            }

            HandlerList.unregisterAll(BeaconListener.getListener());
        }

        if (HANDLE_RESPAWNING)
            HandlerList.unregisterAll(RespawnListener.getListener());

        pm.reloadConfig();
        loadConfig();
    }

    public String getMsg(Msg msg) {
        return messages.get(msg);
    }

    public void loadWaypoints() {
        if (!waypointConfigFile.exists() && !waypointDataFile.exists())
            return;

        if (waypointConfigFile.exists()) {
            // Load
            try {
                wc.load(waypointConfigFile);
                ConfigurationSection waypoints = wc.getConfigurationSection("waypoints");
                if (waypoints != null) {
                    for (String key : waypoints.getKeys(false)) {
                        Waypoint wp = new Waypoint(wc, "waypoints." + key);
                        wm.getWaypoints().put(Util.getKey(wp.getName()), wp);
                    }
                }
            } catch (IOException | InvalidConfigurationException e) {
                e.printStackTrace();
            }
        }
        // (v2.1.0) Note: For transition; remove later
        else if (waypointDataFile.exists()) {
            List<?> uncasted = null;
            try {
                FileInputStream fis = new FileInputStream(waypointDataFile);

                try (ObjectInputStream ois = new ObjectInputStream(fis)) {
                    uncasted = (List<?>) ois.readObject();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (uncasted != null) {
                for (Object obj : uncasted) {
                    // (v2.1.0) Note: For transition; remove later
                    if (obj instanceof Waypoint) {
                        Waypoint wp = (Waypoint) obj;
                        wm.getWaypoints().put(Util.getKey(wp.getName()), wp);
                    }
                }
                saveWaypoints();
            }
        }
    }

    public void saveWaypoints() {
        if (!waypointConfigFile.exists()) {
            try {
                waypointConfigFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (waypointConfigFile.exists()) {
            try {
                wc.set("waypoints", null);
                for (Waypoint wp : wm.getWaypoints().values()) {
                    wp.serialize(wc, "waypoints." + Util.getKey(wp.getName()));
                }
                wc.save(waypointConfigFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // (v2.1.0) Note: For transition; remove later
        if (waypointDataFile.exists()) {
            waypointDataFile.delete();
        }
    }

    public void unloadPlayerData(UUID player) {
        wm.getPlayers().remove(player);
    }

    public PlayerData loadPlayerData(UUID playerUUID) {
        Map<UUID, PlayerData> players = wm.getPlayers();

        File playerFile = new File(playerFolder, String.format("%s.yml", playerUUID.toString()));
        if (playerFile.exists()) {
            players.put(playerUUID, loadPlayerDataConfig(playerUUID, playerFile));
        } else {
            // (v2.1.0) Note: For transition; remove later
            playerFile = new File(playerFolder, playerUUID.toString());
            if (playerFile.exists()) {
                PlayerData playerData = loadPlayerDataSerialized(playerUUID, playerFile);
                if (playerData != null) {
                    players.put(playerUUID, playerData);
                    savePlayerData(playerUUID);
                }
            } else {
                players.put(playerUUID, new PlayerData(playerUUID));
            }
        }

        return players.get(playerUUID);
    }

    private PlayerData loadPlayerDataConfig(UUID playerUUID, File playerFile) {
        YamlConfiguration playerConfigurator = new YamlConfiguration();
        try {
            playerConfigurator.load(playerFile);
            ConfigurationSection player = playerConfigurator.getConfigurationSection("player");
            if (player != null) {
                return new PlayerData(playerConfigurator, "player");
            }
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        return null;
    }

    private PlayerData loadPlayerDataSerialized(UUID playerUUID, File playerFile) {
        Object uncasted = null;

        try {
            FileInputStream fis = new FileInputStream(playerFile);

            try (ObjectInputStream ois = new ObjectInputStream(fis)) {
                uncasted = ois.readObject();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (uncasted != null) {
            if (uncasted instanceof PlayerData) {
                return (PlayerData) uncasted;
            } else if (uncasted instanceof ArrayList<?>) {
                // (v1.1.0) Note: For transition; remove later
                PlayerData pd = new PlayerData(playerUUID);

                for (Object obj : (ArrayList<?>) uncasted)
                    if (obj instanceof Waypoint)
                        pd.addWaypoint((Waypoint) obj);

                return pd;
            }
        }
        return null;
    }

    public void savePlayerData(UUID playerUUID) {
        File playerFile = new File(playerFolder, String.format("%s.yml", playerUUID.toString()));
        YamlConfiguration pc = new YamlConfiguration();

        if (!playerFile.exists()) {
            try {
                playerFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (playerFile.exists()) {
            try {
                wm.getPlayerData(playerUUID).serialize(pc, "player");
                pc.save(playerFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // (v2.1.0) Note: For transition; remove later
        File playerDataFile = new File(playerFolder, playerUUID.toString());
        if (playerDataFile.exists()) {
            playerDataFile.delete();
        }
    }

    public void saveWaypoint(CommandSender sender, Waypoint wp) {
        if (wm.getWaypoints().containsValue(wp))
            saveWaypoints();
        else
            savePlayerData(((Player) sender).getUniqueId());
    }

}
