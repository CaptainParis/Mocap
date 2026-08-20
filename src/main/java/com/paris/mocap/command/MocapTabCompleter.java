package com.paris.mocap.command;

import com.paris.mocap.Mocap;
import com.paris.mocap.model.Recording;
import com.paris.mocap.model.Track;
import com.paris.mocap.playback.PlaybackSession;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MocapTabCompleter implements TabCompleter {
    private static final List<String> ROOT = Arrays.asList("record", "stop", "play", "library", "games", "settings", "track");
    private final Mocap plugin;

    public MocapTabCompleter(Mocap plugin) {
        this.plugin = plugin;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (!(sender instanceof Player)) {
            return List.of();
        }
        if (args.length == 1) {
            return ROOT.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("play")) {
            String prefix = args[1].toLowerCase();
            return this.plugin.repository().list().stream()
                .map(Recording::id)
                .filter(id -> id.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("track")) {
            String prefix = args[1].toLowerCase();
            return this.plugin.playback().sessions().stream()
                .filter(PlaybackSession::active)
                .map(PlaybackSession::name)
                .filter(name -> name.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("play")) {
            Recording recording = this.plugin.repository().get(args[1]);
            if (recording == null) {
                return List.of();
            }
            String prefix = args[2].toLowerCase();
            return recording.tracks().stream()
                .map(Track::playerName)
                .filter(name -> name.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
        }
        return List.of();
    }
}
