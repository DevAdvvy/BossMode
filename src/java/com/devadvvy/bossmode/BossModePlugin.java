package com.devadvvy.bossmode;

import org.bukkit.plugin.java.JavaPlugin;

public class BossModePlugin extends JavaPlugin {
   private GameManager gameManager;

   public void onEnable() {
      this.saveDefaultConfig();
      this.gameManager = new GameManager(this);
      BossCommand cmd = new BossCommand(this);
      this.getCommand("boss").setExecutor(cmd);
      this.getCommand("boss").setTabCompleter(cmd);
      this.getServer().getPluginManager().registerEvents(new GameListener(this), this);
      this.getLogger().info("BossMode (Folia Support + TAB API) habilitado.");
   }

   public void onDisable() {
      if (this.gameManager != null) {
         this.gameManager.stopGame("Server reload/restart");
      }

   }

   public GameManager getGameManager() {
      return this.gameManager;
   }
}
