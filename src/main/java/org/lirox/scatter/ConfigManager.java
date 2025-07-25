package org.lirox.scatter;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final FileConfiguration config;
    public final Map<String, Boolean> scatteredPlayers = new HashMap<>();
    public final Map<String, Location> scatteredPlayersDeathPos = new HashMap<>();
    public final Map<String, Integer> trappedHitCount = new HashMap<>();
    public final Map<String, Integer> chainAnimation = new HashMap<>();
    public final Map<String, String> victimToKiller = new HashMap<>();

    public ConfigManager(FileConfiguration config) {
        this.config = config;
    }

    public void load() {
        if (config.isConfigurationSection("players")) {
            for (String playerName : config.getConfigurationSection("players").getKeys(false)) {
                boolean scattered = config.getBoolean("players." + playerName + ".scattered", false);
                scatteredPlayers.put(playerName, scattered);

                double x = config.getDouble("players." + playerName + ".death_pos.x", 0);
                double y = config.getDouble("players." + playerName + ".death_pos.y", 0);
                double z = config.getDouble("players." + playerName + ".death_pos.z", 0);
                String worldName = config.getString("players." + playerName + ".death_world", "");

                if (Bukkit.getWorld(worldName) != null && (x != 0 || y != 0 || z != 0)) {
                    scatteredPlayersDeathPos.put(playerName, new Location(Bukkit.getWorld(worldName), x, y, z));
                }
            }
        }
    }

    public void save() {
        for (Map.Entry<String, Boolean> entry : scatteredPlayers.entrySet()) {
            String playerName = entry.getKey();
            config.set("players." + playerName + ".scattered", entry.getValue());

            Location deathPos = scatteredPlayersDeathPos.get(playerName);
            if (deathPos != null) {
                config.set("players." + playerName + ".death_pos.x", deathPos.getX());
                config.set("players." + playerName + ".death_pos.y", deathPos.getY());
                config.set("players." + playerName + ".death_pos.z", deathPos.getZ());
                config.set("players." + playerName + ".death_world", deathPos.getWorld().getName());
            }
        }
    }
}
