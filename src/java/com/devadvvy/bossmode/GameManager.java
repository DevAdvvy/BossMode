package com.devadvvy.bossmode;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.bossbar.BarColor;
import me.neznamy.tab.api.bossbar.BarStyle;
import me.neznamy.tab.api.bossbar.BossBar;
import me.neznamy.tab.api.bossbar.BossBarManager;
import me.neznamy.tab.api.scoreboard.Scoreboard;
import me.neznamy.tab.api.scoreboard.ScoreboardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class GameManager {
   private final BossModePlugin plugin;
   private Player boss;
   private volatile boolean isRunning = false;
   private final Map<UUID, Double> damageMap = new ConcurrentHashMap();
   private ItemStack[] savedContents;
   private ItemStack[] savedArmor;
   private Scoreboard tabScoreboard;
   private BossBar tabBossBar;
   private ScheduledTask updateTask;

   public GameManager(BossModePlugin plugin) {
      this.plugin = plugin;
   }

   public void startGame(Player player) {
      if (!this.isRunning) {
         if (Bukkit.getPluginManager().getPlugin("TAB") == null) {
            this.plugin.getLogger().severe("ERROR CRÍTICO: El plugin TAB no está instalado.");
         } else {
            this.boss = player;
            this.isRunning = true;
            this.damageMap.clear();
            this.boss.getScheduler().run(this.plugin, (task) -> {
               this.savedContents = (ItemStack[])this.boss.getInventory().getContents().clone();
               this.savedArmor = (ItemStack[])this.boss.getInventory().getArmorContents().clone();
               this.boss.getInventory().clear();
               this.boss.getInventory().addItem(new ItemStack[]{createItem(this.getMat("skills.fireball.item-material", "BLAZE_ROD"), this.plugin.getConfig().getString("skills.fireball.item-name", "Fireball")), createItem(this.getMat("skills.summon.item-material", "WITHER_SKELETON_SKULL"), this.plugin.getConfig().getString("skills.summon.item-name", "Summon")), createItem(this.getMat("skills.heal.item-material", "NETHER_STAR"), this.plugin.getConfig().getString("skills.heal.item-name", "Heal"))});
               TabPlayer tabBoss = TabAPI.getInstance().getPlayer(this.boss.getUniqueId());
               if (tabBoss != null) {
                  TabAPI.getInstance().getNameTagManager().setPrefix(tabBoss, "&c&lBOSS &c");
               }

               this.boss.setGlowing(true);
            }, (Runnable)null);
            this.boss.getScheduler().runDelayed(this.plugin, (task) -> this.setupBossAttributes(this.boss), (Runnable)null, 2L);
            Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> {
               this.setupTabUI();
               String msg = this.plugin.getConfig().getString("messages.prefix") + this.plugin.getConfig().getString("messages.event-start", "Start").replace("%player%", this.boss.getName());
               Bukkit.broadcast(color(msg));
            });
         }
      }
   }

   public void stopGame(String reason) {
      if (this.isRunning) {
         this.announceTopDamage();
         this.isRunning = false;
         Player finalBoss = this.boss;
         ItemStack[] contentToRestore = this.savedContents;
         ItemStack[] armorToRestore = this.savedArmor;
         if (finalBoss != null) {
            finalBoss.getScheduler().run(this.plugin, (task) -> {
               finalBoss.setGlowing(false);
               this.resetAttributes(finalBoss);
               finalBoss.getInventory().clear();
               if (contentToRestore != null) {
                  finalBoss.getInventory().setContents(contentToRestore);
               }

               if (armorToRestore != null) {
                  finalBoss.getInventory().setArmorContents(armorToRestore);
               }

               finalBoss.getActivePotionEffects().forEach((effect) -> finalBoss.removePotionEffect(effect.getType()));
               TabPlayer tabBoss = TabAPI.getInstance().getPlayer(finalBoss.getUniqueId());
               if (tabBoss != null) {
                  TabAPI.getInstance().getNameTagManager().setPrefix(tabBoss, (String)null);
               }

            }, (Runnable)null);
         }

         if (this.updateTask != null) {
            this.updateTask.cancel();
            this.updateTask = null;
         }

         if (this.tabBossBar != null) {
            for(TabPlayer tp : TabAPI.getInstance().getOnlinePlayers()) {
               this.tabBossBar.removePlayer(tp);
            }

            this.tabBossBar = null;
         }

         ScoreboardManager sbManager = TabAPI.getInstance().getScoreboardManager();
         if (sbManager != null && this.tabScoreboard != null) {
            this.tabScoreboard.unregister();
            this.tabScoreboard = null;

            for(TabPlayer tp : TabAPI.getInstance().getOnlinePlayers()) {
               sbManager.resetScoreboard(tp);
            }
         }

         this.savedContents = null;
         this.savedArmor = null;
         this.boss = null;
         this.damageMap.clear();
         String var10000 = this.plugin.getConfig().getString("messages.prefix");
         Bukkit.broadcast(color(var10000 + this.plugin.getConfig().getString("messages.event-stop").replace("%reason%", reason)));
      }
   }

   private void setupTabUI() {
      ScoreboardManager sbManager = TabAPI.getInstance().getScoreboardManager();
      BossBarManager bbManager = TabAPI.getInstance().getBossBarManager();
      if (sbManager == null) {
         this.plugin.getLogger().warning("TAB ERROR: The Scoreboard is disabled in the TAB config or there is an error.");
      }

      if (bbManager == null) {
         this.plugin.getLogger().warning("TAB ERROR: The BossBar is disabled in the TAB settings.");
      }

      if (sbManager != null || bbManager != null) {
         String barTitle = this.plugin.getConfig().getString("ui.bossbar.title", "&cBOSS: %player%").replace("%player%", this.boss.getName());
         String barColor = this.plugin.getConfig().getString("ui.bossbar.color", "RED");
         String barStyle = this.plugin.getConfig().getString("ui.bossbar.style", "NOTCHED_10");
         if (bbManager != null) {
            try {
               this.tabBossBar = bbManager.createBossBar(barTitle, 100.0F, BarColor.valueOf(barColor), BarStyle.valueOf(barStyle));
            } catch (Exception var12) {
               this.tabBossBar = bbManager.createBossBar(barTitle, 100.0F, BarColor.RED, BarStyle.NOTCHED_10);
            }
         }

         String title = this.plugin.getConfig().getString("ui.scoreboard.title", "&4BOSS EVENT");
         List<String> emptyLines = new ArrayList(this.plugin.getConfig().getStringList("ui.scoreboard.lines"));
         if (sbManager != null) {
            this.tabScoreboard = sbManager.createScoreboard("boss_event_sb", title, emptyLines);
         }

         for(TabPlayer tp : TabAPI.getInstance().getOnlinePlayers()) {
            if (this.tabBossBar != null) {
               this.tabBossBar.addPlayer(tp);
            }

            if (this.tabScoreboard != null && sbManager != null) {
               sbManager.showScoreboard(tp, this.tabScoreboard);
            }
         }

         this.updateTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, (task) -> {
            if (this.isRunning && this.boss != null && this.boss.isOnline()) {
               double maxH = this.boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
               double currH = this.boss.getHealth();
               if (this.tabBossBar != null) {
                  this.tabBossBar.setProgress((float)(currH / maxH) * 100.0F);
                  BossBar var10000 = this.tabBossBar;
                  String var10001 = barTitle.replace("%player%", this.boss.getName());
                  var10000.setTitle(var10001 + " &7(" + (int)currH + "/" + (int)maxH + ")");
               }

               List<Map.Entry<UUID, Double>> top = this.getTopDamage();
               List<String> lines = new ArrayList();

               for(String line : this.plugin.getConfig().getStringList("ui.scoreboard.lines")) {
                  line = line.replace("%health%", String.valueOf((int)currH)).replace("%max_health%", String.valueOf((int)maxH));

                  for(int i = 0; i < 3; ++i) {
                     String n = "Nadie";
                     String d = "0";
                     if (top.size() > i) {
                        Player p = Bukkit.getPlayer((UUID)((Map.Entry)top.get(i)).getKey());
                        n = p != null ? p.getName() : "Desc.";
                        d = String.valueOf(((Double)((Map.Entry)top.get(i)).getValue()).intValue());
                     }

                     line = line.replace("%top_name_" + (i + 1) + "%", n).replace("%top_dmg_" + (i + 1) + "%", d);
                  }

                  lines.add(line);
               }

               if (this.tabScoreboard != null && sbManager != null) {
                  this.tabScoreboard.unregister();
                  this.tabScoreboard = sbManager.createScoreboard("boss_event_sb", title, lines);

                  for(TabPlayer tp : TabAPI.getInstance().getOnlinePlayers()) {
                     sbManager.showScoreboard(tp, this.tabScoreboard);
                  }
               }

            } else {
               task.cancel();
            }
         }, 20L, 20L);
      }
   }

   private void setupBossAttributes(Player p) {
      double hp = this.plugin.getConfig().getDouble("boss-attributes.health", (double)500.0F);
      p.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(hp);
      p.setHealth(hp);
      p.setHealthScaled(true);
      p.setHealthScale((double)40.0F);
      p.getAttribute(Attribute.GENERIC_SCALE).setBaseValue(this.plugin.getConfig().getDouble("boss-attributes.scale", (double)1.5F));
      p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(this.plugin.getConfig().getDouble("boss-attributes.movement-speed", 0.15));
      p.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(this.plugin.getConfig().getDouble("boss-attributes.attack-damage", (double)15.0F));
      p.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(this.plugin.getConfig().getDouble("boss-attributes.knockback-resistance", (double)1.0F));
      p.getAttribute(Attribute.GENERIC_ATTACK_KNOCKBACK).setBaseValue(this.plugin.getConfig().getDouble("boss-attributes.attack-knockback", (double)2.0F));
   }

   private void resetAttributes(Player p) {
      if (p.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
         p.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue((double)20.0F);
      }

      p.setHealthScaled(false);
      p.setHealthScale((double)20.0F);
      if (p.getAttribute(Attribute.GENERIC_SCALE) != null) {
         p.getAttribute(Attribute.GENERIC_SCALE).setBaseValue((double)1.0F);
      }

      if (p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
         p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.1);
      }

      if (p.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
         p.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue((double)1.0F);
      }

      if (p.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE) != null) {
         p.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue((double)0.0F);
      }

      if (p.getAttribute(Attribute.GENERIC_ATTACK_KNOCKBACK) != null) {
         p.getAttribute(Attribute.GENERIC_ATTACK_KNOCKBACK).setBaseValue((double)0.0F);
      }

   }

   private Material getMat(String path, String def) {
      String name = this.plugin.getConfig().getString(path, def);
      Material mat = Material.matchMaterial(name);
      return mat != null ? mat : Material.matchMaterial(def);
   }

   public static Component color(String text) {
      return text == null ? Component.empty() : LegacyComponentSerializer.legacyAmpersand().deserialize(text);
   }

   private static ItemStack createItem(Material mat, String name) {
      ItemStack item = new ItemStack(mat);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(color(name));
      item.setItemMeta(meta);
      return item;
   }

   public void addDamage(Player damager, double damage) {
      this.damageMap.merge(damager.getUniqueId(), damage, Double::sum);
   }

   private List<Map.Entry<UUID, Double>> getTopDamage() {
      return (List)this.damageMap.entrySet().stream().sorted(Entry.comparingByValue().reversed()).collect(Collectors.toList());
   }

   private void announceTopDamage() {
      List<Map.Entry<UUID, Double>> top = this.getTopDamage();
      Bukkit.broadcast(color(this.plugin.getConfig().getString("messages.top-damage-header", "&6TOP DAMAGE")));

      for(int i = 0; i < Math.min(top.size(), 3); ++i) {
         Map.Entry<UUID, Double> entry = (Map.Entry)top.get(i);
         Player p = Bukkit.getPlayer((UUID)entry.getKey());
         String name = p != null ? p.getName() : "Desc.";
         Bukkit.broadcast(color(this.plugin.getConfig().getString("messages.top-damage-entry", "#%rank% %player% %damage%").replace("%rank%", String.valueOf(i + 1)).replace("%player%", name).replace("%damage%", String.format("%.0f", entry.getValue()))));
      }

   }

   public boolean isBoss(Player p) {
      return this.isRunning && this.boss != null && p != null && this.boss.getUniqueId().equals(p.getUniqueId());
   }

   public boolean isRunning() {
      return this.isRunning;
   }

   public Player getBoss() {
      return this.boss;
   }
}
