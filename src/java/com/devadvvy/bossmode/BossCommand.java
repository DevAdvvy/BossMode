package com.devadvvy.bossmode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BossCommand implements CommandExecutor, TabCompleter {
   private final BossModePlugin plugin;

   public BossCommand(BossModePlugin plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      if (!sender.hasPermission("bossmode.admin")) {
         MessageUtil.send(sender, "<#ff0000>You don't have permission.");
         return true;
      } else if (args.length == 0) {
         return false;
      } else if (args[0].equalsIgnoreCase("start")) {
         if (args.length < 2) {
            MessageUtil.send(sender, "<#ff5555>Use: <#ffaa00>/boss start <player>");
            return true;
         } else {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
               MessageUtil.send(sender, "<#ff0000>Player not found.");
               return true;
            } else if (this.plugin.getGameManager().isRunning()) {
               MessageUtil.send(sender, "<#ff0000>There is already an event underway.");
               return true;
            } else {
               this.plugin.getGameManager().startGame(target);
               MessageUtil.send(sender, "<gradient:#ff0000:#ff9900><bold>Boss Mode iniciado!</bold></gradient>");
               return true;
            }
         }
      } else if (args[0].equalsIgnoreCase("stop")) {
         if (!this.plugin.getGameManager().isRunning()) {
            MessageUtil.send(sender, "<#ff0000>There are no active events.");
            return true;
         } else {
            this.plugin.getGameManager().stopGame("Detenido por administrador.");
            MessageUtil.send(sender, "<#ff0000>Stopped event.");
            return true;
         }
      } else if (args[0].equalsIgnoreCase("reload")) {
         this.plugin.reloadConfig();
         MessageUtil.send(sender, "<#ff0000>Configuration updated. (Note: Visual changes may require restarting the event)");
         return true;
      } else {
         return false;
      }
   }

   public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      if (!sender.hasPermission("bossmode.admin")) {
         return new ArrayList();
      } else if (args.length == 1) {
         List<String> suggestions = new ArrayList();
         suggestions.add("start");
         suggestions.add("stop");
         suggestions.add("reload");
         return this.filter(suggestions, args[0]);
      } else {
         return args.length == 2 && args[0].equalsIgnoreCase("start") ? null : new ArrayList();
      }
   }

   private List<String> filter(List<String> list, String input) {
      return input != null && !input.isEmpty() ? (List)list.stream().filter((s) -> s.toLowerCase().startsWith(input.toLowerCase())).collect(Collectors.toList()) : list;
   }
}
