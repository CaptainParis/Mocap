package com.paris.mocap.command;

import com.paris.mocap.Mocap;
import com.paris.mocap.model.Recording;
import com.paris.mocap.model.RecordingSettings;
import com.paris.mocap.playback.PlaybackSession;
import com.paris.mocap.util.Text;
import java.util.List;
import java.util.stream.Collectors;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class MocapCommand implements CommandExecutor {
    private final Mocap plugin;

    public MocapCommand(Mocap plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.prefix("Players only.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("mocap.use")) {
            player.sendMessage(Text.prefix("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            this.plugin.dialogs().home(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "record" -> {
                if (args.length < 2) {
                    this.plugin.dialogs().record(player);
                    return true;
                }
                RecordingSettings settings = this.plugin.recordings().settings();
                if (!settings.global()) {
                    settings.setArea(player.getLocation(), settings.radius());
                }
                List<Player> targets = Bukkit.getOnlinePlayers().stream()
                    .filter(settings::matches)
                    .collect(Collectors.toList());
                if (targets.isEmpty()) {
                    player.sendMessage(Text.prefix("No players in recording area.", NamedTextColor.RED));
                    return true;
                }
                for (Player target : targets) {
                    this.plugin.recordings().start(target, args[1]);
                }
            }
            case "stop" -> this.plugin.recordings().stop(player);
            case "play" -> {
                if (args.length < 2) {
                    this.plugin.dialogs().library(player, 0);
                    return true;
                }
                Recording recording = this.plugin.repository().get(args[1]);
                if (recording == null) {
                    player.sendMessage(Text.prefix("Unknown recording.", NamedTextColor.RED));
                    return true;
                }
                String track = args.length >= 3 ? args[2] : null;
                this.plugin.playback().play(recording, track, player);
            }
            case "library" -> this.plugin.dialogs().library(player, 0);
            case "games" -> this.plugin.dialogs().games(player, 0);
            case "settings" -> this.plugin.dialogs().settings(player);
            case "track" -> {
                if (args.length >= 2) {
                    PlaybackSession session = this.plugin.playback().sessionByName(args[1]);
                    if (session == null) {
                        player.sendMessage(Text.prefix("Session not found.", NamedTextColor.RED));
                        return true;
                    }
                    this.plugin.dialogs().playback(player, session);
                } else {
                    this.plugin.dialogs().sessions(player);
                }
            }
            default -> this.plugin.dialogs().home(player);
        }
        return true;
    }
}
