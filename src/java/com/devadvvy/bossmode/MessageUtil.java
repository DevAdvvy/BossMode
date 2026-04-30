package com.devadvvy.bossmode;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public class MessageUtil {

    private static final MiniMessage mini = MiniMessage.miniMessage();

    public static void send(CommandSender sender, String message) {
        if (message == null) return;
        Component component = mini.deserialize(message);
        sender.sendMessage(component);
    }

    public static void send(CommandSender sender, String message, String... placeholders) {
        if (message == null) return;

        for (int i = 0; i < placeholders.length; i += 2) {
            message = message.replace(placeholders[i], placeholders[i + 1]);
        }

        sender.sendMessage(mini.deserialize(message));
    }
}