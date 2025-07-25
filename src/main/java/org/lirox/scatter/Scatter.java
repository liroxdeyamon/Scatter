package org.lirox.scatter;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Consumer;
import org.lirox.scatter.commands.ScatterCommand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class Scatter extends JavaPlugin implements Listener {

    public static ConfigManager configManager;
    private final Random rand = new Random();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new Events(this), this);
        getCommand("scatter").setExecutor(new ScatterCommand());
        saveDefaultConfig();
        configManager = new ConfigManager(getConfig());
        spawnParticlesLoop();
    }

    @Override
    public void onDisable() {
        configManager.save();
    }


    public static void removeOneScatter(Player player) {
        Consumer<ItemStack> decrementOrRemove = item -> {
            if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
            else item.setAmount(0);
        };

        if (hasScatterMainHand(player)) {
            decrementOrRemove.accept(player.getInventory().getItemInMainHand());
            return;
        }

        if (hasScatterOffHand(player)) {
            decrementOrRemove.accept(player.getInventory().getItemInOffHand());
            return;
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack item = player.getInventory().getItem(slot);
            if (isScatter(item)) {
                decrementOrRemove.accept(item);
                return;
            }
        }
    }

    public static boolean isScatter(ItemStack item) {
        return item != null && item.getType() != Material.AIR &&
                item.getItemMeta() != null &&
                item.getItemMeta().hasCustomModelData() &&
                (item.getItemMeta().getCustomModelData() == 1 || item.getItemMeta().getCustomModelData() == 3);
    }

    public static boolean hasScatterOffHand(Player player) {
        return isScatter(player.getInventory().getItemInOffHand());
    }

    public static boolean hasScatterMainHand(Player player) {
        return isScatter(player.getInventory().getItemInMainHand());
    }

    public static boolean hasScatter(Player player) {
        return (hasScatterMainHand(player) || hasScatterOffHand(player)) ||
                isScatter(player.getInventory().getItem(EquipmentSlot.HEAD)) ||
                isScatter(player.getInventory().getItem(EquipmentSlot.CHEST)) ||
                isScatter(player.getInventory().getItem(EquipmentSlot.LEGS)) ||
                isScatter(player.getInventory().getItem(EquipmentSlot.FEET));
    }

    public static boolean isReviver(ItemStack item) {
        return item != null && item.getType() != Material.AIR &&
                item.getItemMeta() != null &&
                item.getItemMeta().hasCustomModelData() &&
                (item.getItemMeta().getCustomModelData() == 2 || item.getItemMeta().getCustomModelData() == 3);
    }

    public static boolean hasReviverOffHand(Player player) {
        return isReviver(player.getInventory().getItemInOffHand());
    }

    public static boolean hasReviverMainHand(Player player) {
        return isReviver(player.getInventory().getItemInMainHand());
    }

    public static boolean hasReviver(Player player) {
        return (hasReviverMainHand(player) || hasReviverOffHand(player));
    }

    private void spawnParticlesLoop() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Map.Entry<String, Location> entry : configManager.scatteredPlayersDeathPos.entrySet()) {
                String playerName = entry.getKey();
                Location deathPos = entry.getValue();
                deathPos.getWorld().spawnParticle(Particle.END_ROD, deathPos, 5, 10, 10, 10, 0.05);
                deathPos.getWorld().spawnParticle(Particle.DRAGON_BREATH, deathPos, 1, 0.3, 0.3, 0.3, 0.05);
                Player player = Bukkit.getPlayer(playerName);
                if (player != null) {
                    deathPos.getWorld().spawnParticle(Particle.TOTEM, deathPos, 1, 0.3, 0.3, 0.3, 0.02);
                    player.setExp(rand.nextFloat());
                }
            }
            for (Map.Entry<String, Integer> entry : configManager.chainAnimation.entrySet()) {
                String playerName = entry.getKey();
                Integer state = entry.getValue();
                Player player = Bukkit.getPlayer(playerName);

                if (player != null) {
                    float radius = (float) ((state < 20) ? 0.5 + (1 - ((float) state / 20)) : 0.5);
                    Location center = player.getLocation();

                    for (int i = 0; i < 4; i++) {
                        double angle = Math.toRadians(state / 5.0 + i * 90);
                        double offsetX = radius * Math.cos(angle);
                        double offsetZ = radius * Math.sin(angle);

                        double rotatedY = offsetX * Math.sin(Math.toRadians(45)) + offsetZ * Math.cos(Math.toRadians(45)) + 1;
                        double rotatedX = offsetX * Math.cos(Math.toRadians(45)) - offsetZ * Math.sin(Math.toRadians(45));

                        center.add(rotatedX, rotatedY, offsetZ);
                        player.getWorld().spawnParticle(Particle.REDSTONE, center, 1, new Particle.DustOptions(Color.YELLOW, 1));
                        center.subtract(rotatedX, rotatedY, offsetZ);


                        offsetX = radius * Math.cos(-angle);
                        offsetZ = radius * Math.sin(-angle);

                        rotatedY = offsetX * Math.sin(Math.toRadians(-45)) + offsetZ * Math.cos(Math.toRadians(-45)) + 1;
                        rotatedX = offsetX * Math.cos(Math.toRadians(-45)) - offsetZ * Math.sin(Math.toRadians(-45));

                        center.add(rotatedX, rotatedY, offsetZ);
                        player.getWorld().spawnParticle(Particle.REDSTONE, center, 1, new Particle.DustOptions(Color.YELLOW, 1));
                        center.subtract(rotatedX, rotatedY, offsetZ);
                    }
                    configManager.chainAnimation.put(playerName, state+1);
                    if (state > 3600) {
                        configManager.chainAnimation.remove(playerName);
                        configManager.trappedHitCount.remove(playerName);
                        configManager.victimToKiller.remove(playerName);
                    }
                }
            }
        }, 0L, 1L);
    }
}
