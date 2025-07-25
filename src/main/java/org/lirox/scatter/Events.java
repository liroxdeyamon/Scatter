package org.lirox.scatter;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Map;

import static org.lirox.scatter.Scatter.*;

public class Events implements Listener {
    private final Plugin plugin;

    public int final_hits = 3;
    public float max_final_hp_mul = .5f;

    public ArrayList<Material> offhand_binding_curse = new ArrayList<>();

    public Events(Plugin plugin) {
        this.plugin = plugin;
        offhand_binding_curse.add(Material.TOTEM_OF_UNDYING);
        offhand_binding_curse.add(Material.SHIELD);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (configManager.scatteredPlayers.getOrDefault(player.getName(), false)) event.setCancelled(true);
        Item droppedItem = event.getItemDrop();

        if (Scatter.isReviver(droppedItem.getItemStack())) {
            for (Map.Entry<String, Location> entry : configManager.scatteredPlayersDeathPos.entrySet()) {
                String victimName = entry.getKey();
                Location deathPos = entry.getValue();
                Location deathLocation = new Location(player.getWorld(), deathPos.getX(), deathPos.getY(), deathPos.getZ());

                if (deathLocation.distance(player.getLocation()) <= 5) {
                    Player victim = Bukkit.getPlayer(victimName);
                    if (victim == null) {
                        player.sendMessage(Component.text("<> This player is offline. Go wake them up or smh."));
                        return;
                    }

                    configManager.scatteredPlayers.put(victim.getName(), false);
                    configManager.scatteredPlayersDeathPos.remove(victim.getName());

                    victim.teleport(deathLocation);
                    victim.setGameMode(GameMode.SURVIVAL);
                    victim.setCanPickupItems(true);
                    for (Player otherPlayer : Bukkit.getOnlinePlayers()) {
                        otherPlayer.showPlayer(plugin, victim);
                    }

                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1, 1);
                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 1, 1);
                    victim.sendMessage(Component.text("<> You have been revived by " + player.getName()));
                    droppedItem.remove();

                    break;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;
        if (!hasScatter(killer)) return;

        event.setCancelled(true);

        configManager.victimToKiller.put(victim.getName(), killer.getName());
        configManager.chainAnimation.put(victim.getName(), 0);
        configManager.trappedHitCount.put(victim.getName(), 0);

        victim.setHealth(victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * max_final_hp_mul);

        if (final_hits <= 0) finalizeTrap(victim);
    }

    @EventHandler
    public void onEntityResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player victim) {
            if (hasScatterOffHand(victim) && victim.getInventory().getItemInOffHand().getType().equals(Material.TOTEM_OF_UNDYING)) {
                victim.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                finalizeTrap(victim);
            }
        }
    }


    @EventHandler
    public void onEntityDamagedByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager) || !(event.getEntity() instanceof Player victim)) return;
        if (configManager.trappedHitCount.get(damager.getName()) != null || configManager.scatteredPlayers.getOrDefault(damager.getName(), false)) return;
        if (configManager.trappedHitCount.get(victim.getName()) == null) return;

        if (!damager.getName().equals(configManager.victimToKiller.get(victim.getName()))) {
            damager.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0F, 1.0F);
            event.setCancelled(true);
            return;
        } else if (damager.getInventory().getItemInMainHand().getType().equals(Material.AIR)) {
            configManager.trappedHitCount.remove(victim.getName());
            configManager.chainAnimation.remove(victim.getName());
            configManager.victimToKiller.remove(victim.getName());
            event.setCancelled(true);
            return;
        }

        if (!hasScatter(damager)) return;

        int hits = configManager.trappedHitCount.getOrDefault(victim.getName(), 0) + 1;
        configManager.trappedHitCount.put(victim.getName(), hits);

        victim.damage(1);
        victim.setHealth((double) (final_hits - hits) / final_hits * (victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * max_final_hp_mul));
        damager.sendMessage(Component.text("<> " + hits + "/" + final_hits));
        damager.getWorld().playSound(damager.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1, 1);

        if (hits >= final_hits) {
            Scatter.removeOneScatter(damager);
            finalizeTrap(victim);
        }
    }

    private void finalizeTrap(Player victim) {
        configManager.scatteredPlayers.put(victim.getName(), true);
        configManager.trappedHitCount.remove(victim.getName());
        configManager.chainAnimation.remove(victim.getName());
        configManager.victimToKiller.remove(victim.getName());

        for (Player otherPlayer : Bukkit.getOnlinePlayers()) {
            otherPlayer.showPlayer(plugin, victim);
        }

        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1, 1);
        victim.getWorld().spawnParticle(Particle.TOTEM, victim.getLocation(), 50);

        configManager.scatteredPlayers.put(victim.getName(), true);
        Location deathLocation = victim.getLocation();
        configManager.scatteredPlayersDeathPos.put(victim.getName(), new Location(
                deathLocation.getWorld(),
                deathLocation.getX(),
                deathLocation.getY(),
                deathLocation.getZ()
        ));

        for (ItemStack item : victim.getEnderChest().getContents()) {
            if (item != null) victim.getWorld().dropItemNaturally(victim.getLocation(), item);
        }
        for (ItemStack item : victim.getInventory().getContents()) {
            if (item != null) victim.getWorld().dropItemNaturally(victim.getLocation(), item);
        }
        victim.getEnderChest().clear();
        victim.getInventory().clear();
        victim.kick(Component.text("There is no way back... Or is it?"));
    }

    @EventHandler
    public void onEntityDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && (configManager.scatteredPlayers.getOrDefault(victim.getName(), false) || configManager.victimToKiller.get(victim.getName()) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (configManager.victimToKiller.get(player.getName()) != null) {
            Location loc = event.getFrom();
            loc.setPitch(event.getTo().getPitch());
            loc.setYaw(event.getTo().getYaw());
            if (loc.getY() < event.getTo().getY()) loc.setY(event.getTo().getY());
            player.teleport(loc);
        }
    }



    // -------------------- Ghost movement, interaction, messages, etc
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (configManager.scatteredPlayers.getOrDefault(event.getPlayer().getName(), false)) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (configManager.scatteredPlayers.getOrDefault(player.getName(), false)) {
            event.joinMessage(null);
            player.setGameMode(GameMode.ADVENTURE);

            for (Player otherPlayer : Bukkit.getOnlinePlayers()) {
                otherPlayer.hidePlayer(plugin, player);
            }

            player.setCanPickupItems(false);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (configManager.scatteredPlayers.getOrDefault(event.getPlayer().getName(), false)) event.quitMessage(null);
        if (configManager.trappedHitCount.get(event.getPlayer().getName()) != null) finalizeTrap(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        if (configManager.scatteredPlayers.getOrDefault(event.getPlayer().getName(), false)) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent event) {
        if (configManager.scatteredPlayers.getOrDefault(event.getPlayer().getName(), false)) event.message(null);
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (configManager.scatteredPlayers.getOrDefault(event.getPlayer().getName(), false)) event.setCancelled(true);
    }



    // -------------------------------------- Binding Curse
    @EventHandler
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        ItemStack offHand = event.getOffHandItem();
        if (Scatter.isScatter(offHand) && offHand.containsEnchantment(Enchantment.BINDING_CURSE) && offhand_binding_curse.contains(offHand.getType()) && !event.getPlayer().getGameMode().equals(GameMode.CREATIVE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (Scatter.isScatter(offHand) && offHand.containsEnchantment(Enchantment.BINDING_CURSE) && event.getSlot() == 40 && !player.getGameMode().equals(GameMode.CREATIVE)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (Scatter.isScatter(item) && item.containsEnchantment(Enchantment.BINDING_CURSE) && offhand_binding_curse.contains(offHand.getType()) && !event.getPlayer().getGameMode().equals(GameMode.CREATIVE)) {
            event.setCancelled(true);
        }
    }
}
