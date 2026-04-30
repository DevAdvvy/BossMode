package com.devadvvy.bossmode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class GameListener implements Listener {
   private final BossModePlugin plugin;
   private final Map<UUID, Long> cooldowns = new HashMap();
   private final NamespacedKey minionKey;

   public GameListener(BossModePlugin plugin) {
      this.plugin = plugin;
      this.minionKey = new NamespacedKey(plugin, "boss_minion");
   }

   @EventHandler
   public void onDamage(EntityDamageByEntityEvent event) {
      if (this.plugin.getGameManager().isRunning()) {
         Entity var4 = event.getEntity();
         if (var4 instanceof Player) {
            Player victim = (Player)var4;
            var4 = event.getDamager();
            if (var4 instanceof Player) {
               Player attacker = (Player)var4;
               boolean victimIsBoss = this.plugin.getGameManager().isBoss(victim);
               boolean attackerIsBoss = this.plugin.getGameManager().isBoss(attacker);
               if (!victimIsBoss && !attackerIsBoss) {
                  event.setCancelled(true);
                  return;
               }

               if (victimIsBoss && !attackerIsBoss) {
                  this.plugin.getGameManager().addDamage(attacker, event.getFinalDamage());
               }
            }
         }

      }
   }

   @EventHandler
   public void onMinionDamage(EntityDamageEvent event) {
      if (event.getEntity().getPersistentDataContainer().has(this.minionKey, PersistentDataType.BYTE) && this.plugin.getConfig().getBoolean("skills.summon.options.fire-immune", true)) {
         EntityDamageEvent.DamageCause cause = event.getCause();
         if (cause == DamageCause.FIRE || cause == DamageCause.FIRE_TICK || cause == DamageCause.LAVA || cause == DamageCause.HOT_FLOOR || cause == DamageCause.MELTING) {
            event.setCancelled(true);
            event.getEntity().setFireTicks(0);
         }
      }

   }

   @EventHandler
   public void onMinionDeath(EntityDeathEvent event) {
      if (event.getEntity().getPersistentDataContainer().has(this.minionKey, PersistentDataType.BYTE)) {
         if (this.plugin.getConfig().getBoolean("skills.summon.options.prevent-vanilla-drops", true)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
         }

         for(String matName : this.plugin.getConfig().getStringList("skills.summon.custom-drops")) {
            Material mat = Material.matchMaterial(matName);
            if (mat != null) {
               event.getDrops().add(new ItemStack(mat, 1));
            }
         }
      }

   }

   @EventHandler
   public void onTarget(EntityTargetLivingEntityEvent event) {
      if (this.plugin.getGameManager().isRunning()) {
         LivingEntity var3 = event.getTarget();
         if (var3 instanceof Player) {
            Player target = (Player)var3;
            if (this.plugin.getGameManager().isBoss(target)) {
               event.setCancelled(true);
            }
         }

      }
   }

   @EventHandler
   public void onDeath(PlayerDeathEvent event) {
      if (this.plugin.getGameManager().isRunning()) {
         Player player = event.getEntity();
         event.setKeepInventory(true);
         event.getDrops().clear();
         event.setKeepLevel(true);
         event.setDroppedExp(0);
         if (this.plugin.getGameManager().isBoss(player)) {
            Bukkit.broadcast(GameManager.color(this.plugin.getConfig().getString("messages.boss-defeated", "&cBoss Defeated!")));
            this.plugin.getGameManager().stopGame("The Chief is dead.");

            for(Entity e : player.getNearbyEntities((double)100.0F, (double)100.0F, (double)100.0F)) {
               if (e.getPersistentDataContainer().has(this.minionKey, PersistentDataType.BYTE)) {
                  e.remove();
               }
            }
         }

      }
   }

   @EventHandler
   public void onInteract(PlayerInteractEvent event) {
      if (this.plugin.getGameManager().isRunning()) {
         Player p = event.getPlayer();
         if (this.plugin.getGameManager().isBoss(p)) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
               ItemStack item = event.getItem();
               if (item != null && item.getType() != Material.AIR) {
                  Material matFire = this.getMat("skills.fireball.item-material", "BLAZE_ROD");
                  Material matSummon = this.getMat("skills.summon.item-material", "WITHER_SKELETON_SKULL");
                  Material matHeal = this.getMat("skills.heal.item-material", "NETHER_STAR");
                  if (item.getType() == matFire) {
                     event.setCancelled(true);
                     this.handleSkill(p, "fireball", 1000, () -> this.useFireball(p));
                  } else if (item.getType() == matSummon) {
                     event.setCancelled(true);
                     this.handleSkill(p, "summon", 10000, () -> this.useSummon(p));
                  } else if (item.getType() == matHeal) {
                     event.setCancelled(true);
                     double maxHealth = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                     if (p.getHealth() >= maxHealth) {
                        p.sendMessage(GameManager.color(this.plugin.getConfig().getString("messages.health-full", "&aSalud llena.")));
                        return;
                     }

                     this.handleSkill(p, "heal", 20000, () -> this.useHeal(p));
                  }

               }
            }
         }
      }
   }

   private void handleSkill(Player p, String skillName, int defaultCd, Runnable action) {
      long now = System.currentTimeMillis();
      long lastUse = (Long)this.cooldowns.getOrDefault(p.getUniqueId(), 0L);
      int cd = this.plugin.getConfig().getInt("skills." + skillName + ".cooldown", defaultCd);
      if (now - lastUse < (long)cd) {
         double remainingSeconds = (double)((long)cd - (now - lastUse)) / (double)1000.0F;
         String msg = this.plugin.getConfig().getString("messages.cooldown-wait", "&cEspera &e%time%s");
         p.sendMessage(GameManager.color(msg.replace("%time%", String.format("%.1f", remainingSeconds))));
      } else {
         action.run();
         this.cooldowns.put(p.getUniqueId(), now);
         String msg = this.plugin.getConfig().getString("skills." + skillName + ".message");
         if (msg != null && !msg.isEmpty()) {
            p.sendMessage(GameManager.color(msg));
         }

         String broadcastMsg = this.plugin.getConfig().getString("skills." + skillName + ".cast-message");
         if (broadcastMsg != null && !broadcastMsg.isEmpty()) {
            Bukkit.broadcast(GameManager.color(broadcastMsg.replace("%player%", p.getName())));
         }

      }
   }

   private Material getMat(String path, String def) {
      String name = this.plugin.getConfig().getString(path, def);
      Material mat = Material.matchMaterial(name);
      return mat != null ? mat : Material.matchMaterial(def);
   }

   private void useFireball(Player boss) {
      int power = this.plugin.getConfig().getInt("skills.fireball.power", 4);
      LargeFireball fireball = (LargeFireball)boss.launchProjectile(LargeFireball.class);
      if (fireball != null) {
         fireball.setYield((float)power);
         fireball.setIsIncendiary(true);
      }

      boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1.0F, 0.5F);
   }

   private void useSummon(Player boss) {
      EntityType type = EntityType.valueOf(this.plugin.getConfig().getString("skills.summon.mob-type", "ZOMBIE"));
      int amount = this.plugin.getConfig().getInt("skills.summon.amount", 3);
      double hp = this.plugin.getConfig().getDouble("skills.summon.attributes.health", (double)20.0F);
      double scale = this.plugin.getConfig().getDouble("skills.summon.attributes.scale", (double)1.0F);
      double range = this.plugin.getConfig().getDouble("skills.summon.attributes.follow-range", (double)20.0F);
      double speed = this.plugin.getConfig().getDouble("skills.summon.attributes.speed", (double)0.25F);
      boolean glowing = this.plugin.getConfig().getBoolean("skills.summon.visual.glowing", true);
      String name = this.plugin.getConfig().getString("skills.summon.visual.name", "&cEsbirro").replace("%player%", boss.getName());
      Location center = boss.getLocation();
      Bukkit.getRegionScheduler().execute(this.plugin, center, () -> {
         for(int i = 0; i < amount; ++i) {
            Location spawnLoc = center.clone().add(Math.random() * (double)6.0F - (double)3.0F, (double)1.0F, Math.random() * (double)6.0F - (double)3.0F);
            boss.getWorld().spawn(spawnLoc, type.getEntityClass(), (entity) -> {
               if (entity instanceof LivingEntity mob) {
                  mob.getPersistentDataContainer().set(this.minionKey, PersistentDataType.BYTE, (byte)1);
                  if (mob.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                     mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(hp);
                     mob.setHealth(hp);
                  }

                  if (mob.getAttribute(Attribute.GENERIC_SCALE) != null) {
                     mob.getAttribute(Attribute.GENERIC_SCALE).setBaseValue(scale);
                  }

                  if (mob.getAttribute(Attribute.GENERIC_FOLLOW_RANGE) != null) {
                     mob.getAttribute(Attribute.GENERIC_FOLLOW_RANGE).setBaseValue(range);
                  }

                  if (mob.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                     mob.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);
                  }

                  mob.customName(GameManager.color(name));
                  mob.setCustomNameVisible(true);
                  mob.setGlowing(glowing);
                  if (mob.getEquipment() != null) {
                     mob.getEquipment().setHelmet(new ItemStack(this.getMat("skills.summon.equipment.helmet", "IRON_HELMET")));
                     mob.getEquipment().setChestplate(new ItemStack(this.getMat("skills.summon.equipment.chestplate", "CHAINMAIL_CHESTPLATE")));
                     mob.getEquipment().setLeggings(new ItemStack(this.getMat("skills.summon.equipment.leggings", "AIR")));
                     mob.getEquipment().setBoots(new ItemStack(this.getMat("skills.summon.equipment.boots", "AIR")));
                     mob.getEquipment().setItemInMainHand(new ItemStack(this.getMat("skills.summon.equipment.main-hand", "IRON_SWORD")));
                     mob.getEquipment().setItemInOffHand(new ItemStack(this.getMat("skills.summon.equipment.off-hand", "AIR")));
                  }
               }

            });
            boss.getWorld().spawnParticle(Particle.POOF, spawnLoc, 10);
         }

      });
      boss.getWorld().playSound(center, Sound.ENTITY_ZOMBIE_AMBIENT, 1.0F, 0.5F);
   }

   private void useHeal(Player boss) {
      double amount = this.plugin.getConfig().getDouble("skills.heal.amount", (double)50.0F);
      double max = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
      double current = boss.getHealth();
      boss.getScheduler().run(this.plugin, (task) -> {
         boss.setHealth(Math.min(max, current + amount));
         boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 1.0F);
         boss.getWorld().spawnParticle(Particle.HEART, boss.getLocation().add((double)0.0F, (double)2.0F, (double)0.0F), 20, (double)0.5F, (double)0.5F, (double)0.5F);
      }, (Runnable)null);
   }
}
