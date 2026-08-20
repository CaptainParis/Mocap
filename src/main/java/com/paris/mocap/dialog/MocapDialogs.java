package com.paris.mocap.dialog;

import com.paris.mocap.Mocap;
import com.paris.mocap.model.Recording;
import com.paris.mocap.model.RecordingSettings;
import com.paris.mocap.playback.PlaybackSession;
import com.paris.mocap.util.Text;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class MocapDialogs {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final int PAGE_SIZE = 6;
    private static final ClickCallback.Options ONCE =
        ClickCallback.Options.builder().uses(1).build();

    private final Mocap plugin;

    public MocapDialogs(Mocap plugin) {
        this.plugin = plugin;
    }

    public void home(Player player) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(mm("<gray>Capture motion, replay packet actors, and return home when done."), 320));
        if (this.plugin.recordings().isRecording(player)) {
            body.add(DialogBody.plainMessage(mm("<green>You are recording right now."), 320));
        }
        int active = (int) this.plugin.playback().sessions().stream().filter(PlaybackSession::active).count();
        if (active > 0) {
            body.add(DialogBody.plainMessage(mm("<aqua>" + active + " playback session(s) running."), 320));
        }

        List<ActionButton> actions = new ArrayList<>();
        actions.add(button("<gold>Library", "<gray>Play or delete recordings", () -> library(player, 0)));
        actions.add(button("<light_purple>Games", "<gray>Takes grouped by game type", () -> games(player, 0)));
        actions.add(button("<yellow>Settings", "<gray>Capture options", () -> settings(player)));
        actions.add(button("<green>Record", "<gray>Start a new capture", () -> record(player)));
        if (this.plugin.recordings().isRecording(player)) {
            actions.add(button("<red>Stop recording", "<gray>Save to disk", () -> {
                this.plugin.recordings().stop(player);
                later(() -> home(player));
            }));
        }
        actions.add(button("<aqua>Playback", "<gray>Control active sessions", () -> sessions(player)));

        show(player, "Mocap", body, actions, 2);
    }

    public void library(Player player, int page) {
        List<Recording> all = new ArrayList<>(this.plugin.repository().list());
        all.sort(Comparator.comparing(Recording::id, String.CASE_INSENSITIVE_ORDER));
        int maxPage = Math.max(0, (all.size() - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, maxPage));
        int from = current * PAGE_SIZE;
        int to = Math.min(all.size(), from + PAGE_SIZE);

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(mm(
            all.isEmpty()
                ? "<gray>No recordings yet. Use Record to capture one."
                : "<gray>Click a take to play it. Page " + (current + 1) + "/" + (maxPage + 1)
        ), 320));

        List<ActionButton> actions = new ArrayList<>();
        for (int i = from; i < to; i++) {
            Recording recording = all.get(i);
            String scene = recording.hasWorldScene() ? " · scene" : "";
            String game = recording.gameType() == null ? "" : " · " + recording.gameType();
            actions.add(button(
                "<aqua>" + recording.id(),
                "<gray>" + recording.durationTicks() + " ticks · "
                    + recording.tracks().size() + " tracks" + scene + game,
                () -> playConfirm(player, recording, () -> library(player, current))
            ));
        }
        if (current > 0) {
            actions.add(button("<gray>◀ Previous", null, () -> library(player, current - 1)));
        }
        if (current < maxPage) {
            actions.add(button("<gray>Next ▶", null, () -> library(player, current + 1)));
        }
        actions.add(button("<yellow>Settings", null, () -> settings(player)));
        actions.add(button("<gray>◀ Home", null, () -> home(player)));

        show(player, "Library", body, actions, 2);
    }

    public void games(Player player, int page) {
        List<String> types = new ArrayList<>(this.plugin.recordings().gameTypes());
        types.sort(String.CASE_INSENSITIVE_ORDER);
        int maxPage = Math.max(0, (types.size() - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, maxPage));
        int from = current * PAGE_SIZE;
        int to = Math.min(types.size(), from + PAGE_SIZE);

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(mm(
            types.isEmpty()
                ? "<gray>No game takes yet. Other plugins can tag recordings with a game type."
                : "<gray>Pick a game to browse its takes. Page " + (current + 1) + "/" + (maxPage + 1)
        ), 320));

        List<ActionButton> actions = new ArrayList<>();
        for (int i = from; i < to; i++) {
            String type = types.get(i);
            int count = this.plugin.recordings().recordingsForGame(type).size();
            actions.add(button(
                "<light_purple>" + type,
                "<gray>" + count + " take(s)",
                () -> gameTakes(player, type, 0)
            ));
        }
        if (current > 0) {
            actions.add(button("<gray>◀ Previous", null, () -> games(player, current - 1)));
        }
        if (current < maxPage) {
            actions.add(button("<gray>Next ▶", null, () -> games(player, current + 1)));
        }
        actions.add(button("<gray>◀ Home", null, () -> home(player)));
        show(player, "Games", body, actions, 2);
    }

    public void gameTakes(Player player, String gameType, int page) {
        List<Recording> all = new ArrayList<>(this.plugin.recordings().recordingsForGame(gameType));
        int maxPage = Math.max(0, (all.size() - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, maxPage));
        int from = current * PAGE_SIZE;
        int to = Math.min(all.size(), from + PAGE_SIZE);

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(mm(
            all.isEmpty()
                ? "<gray>No takes for this game."
                : "<gray>" + gameType + " — click a take to play it."
        ), 320));

        List<ActionButton> actions = new ArrayList<>();
        for (int i = from; i < to; i++) {
            Recording recording = all.get(i);
            actions.add(button(
                "<aqua>" + recording.id(),
                "<gray>" + recording.durationTicks() + " ticks · " + recording.tracks().size() + " players",
                () -> playConfirm(player, recording, () -> gameTakes(player, gameType, current))
            ));
        }
        if (current > 0) {
            actions.add(button("<gray>◀ Previous", null, () -> gameTakes(player, gameType, current - 1)));
        }
        if (current < maxPage) {
            actions.add(button("<gray>Next ▶", null, () -> gameTakes(player, gameType, current + 1)));
        }
        actions.add(button("<gray>◀ Games", null, () -> games(player, 0)));
        show(player, gameType, body, actions, 2);
    }

    public void settings(Player player) {
        RecordingSettings s = this.plugin.recordings().settings();
        List<DialogBody> body = List.of(
            DialogBody.plainMessage(mm("<gray>Click a row to cycle that setting."), 320)
        );
        List<ActionButton> actions = new ArrayList<>();
        actions.add(cycle("<yellow>Duration", s.durationLabel(), () -> {
            s.cycleMaxDuration();
            settings(player);
        }));
        actions.add(cycle("<aqua>Radius", s.areaLabel(), () -> {
            s.cycleArea(player.getLocation());
            settings(player);
        }));
        actions.add(cycle("<dark_green>World capture", s.worldCaptureMode().label(), () -> {
            s.cycleWorldCaptureMode();
            settings(player);
        }));
        actions.add(cycle("<green>Ops only", String.valueOf(s.recordOnlyOps()), () -> {
            s.setRecordOnlyOps(!s.recordOnlyOps());
            settings(player);
        }));
        actions.add(cycle("<light_purple>Sample rate", s.tickRateLabel(), () -> {
            s.cycleTickRate();
            settings(player);
        }));
        actions.add(toggle("Arm animations", s.recordAnimations(), () -> {
            s.setRecordAnimations(!s.recordAnimations());
            settings(player);
        }));
        actions.add(toggle("Equipment", s.recordEquipment(), () -> {
            s.setRecordEquipment(!s.recordEquipment());
            settings(player);
        }));
        actions.add(toggle("Sneak", s.recordSneak(), () -> {
            s.setRecordSneak(!s.recordSneak());
            settings(player);
        }));
        actions.add(toggle("Sprint", s.recordSprint(), () -> {
            s.setRecordSprint(!s.recordSprint());
            settings(player);
        }));
        actions.add(toggle("Blocking", s.recordBlocking(), () -> {
            s.setRecordBlocking(!s.recordBlocking());
            settings(player);
        }));
        actions.add(toggle("Chest open", s.recordChestOpen(), () -> {
            s.setRecordChestOpen(!s.recordChestOpen());
            settings(player);
        }));
        actions.add(toggle("Fishing", s.recordFishing(), () -> {
            s.setRecordFishing(!s.recordFishing());
            settings(player);
        }));
        actions.add(button("<gray>◀ Home", null, () -> home(player)));
        show(player, "Capture Settings", body, actions, 2);
    }

    public void record(Player player) {
        DialogInput nameInput = DialogInput.text("name", mm("<gray>Recording name"))
            .maxLength(32)
            .width(220)
            .build();
        ActionButton start = ActionButton.create(
            mm("<green>Start"),
            mm("<gray>Begin capture with current settings"),
            150,
            DialogAction.customClick((view, audience) -> {
                if (!(audience instanceof Player clicker)) {
                    return;
                }
                String name = view.getText("name");
                later(() -> this.plugin.recordings().start(clicker, name == null ? "" : name.trim()));
            }, ONCE)
        );
        ActionButton cancel = ActionButton.create(mm("<gray>Cancel"), null, 120,
            DialogAction.customClick((view, audience) -> {
                if (audience instanceof Player clicker) {
                    later(() -> home(clicker));
                }
            }, ONCE));

        Dialog dialog = Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(mm("<gold><bold>Record</bold>"))
                .canCloseWithEscape(true)
                .body(List.of(DialogBody.plainMessage(mm("<gray>Name this take, then start capturing."), 320)))
                .inputs(List.of(nameInput))
                .build())
            .type(DialogType.confirmation(start, cancel)));
        show(player, dialog);
    }

    public void sessions(Player player) {
        List<PlaybackSession> active = this.plugin.playback().sessions().stream()
            .filter(PlaybackSession::active)
            .toList();
        if (active.isEmpty()) {
            player.sendMessage(Text.prefix("No active playback.", NamedTextColor.RED));
            home(player);
            return;
        }
        if (active.size() == 1) {
            playback(player, active.get(0));
            return;
        }
        List<ActionButton> actions = new ArrayList<>();
        for (PlaybackSession session : active) {
            actions.add(button(
                "<aqua>" + session.name(),
                "<gray>Tick " + session.tick() + " / " + session.recording().durationTicks(),
                () -> playback(player, session)
            ));
        }
        actions.add(button("<gray>◀ Home", null, () -> home(player)));
        show(player, "Active Sessions", List.of(
            DialogBody.plainMessage(mm("<gray>Choose a session to control."), 320)
        ), actions, 1);
    }

    public void playback(Player player, PlaybackSession session) {
        if (session == null || !session.active()) {
            home(player);
            return;
        }
        List<DialogBody> body = List.of(
            DialogBody.plainMessage(mm(
                "<gray>Tick <white>" + session.tick() + "<gray> / <white>"
                    + session.recording().durationTicks()
                    + " <gray>· Speed <white>" + session.speed() + "x"
                    + " <gray>· Loop <white>" + session.settings().loopLabel()
            ), 320)
        );
        List<ActionButton> actions = new ArrayList<>();
        actions.add(button(
            session.paused() ? "<green>Resume" : "<gold>Pause",
            null,
            () -> {
                session.togglePause();
                playback(player, session);
            }
        ));
        actions.add(button("<red>Stop", "<gray>Despawn mannequins and return home", () -> {
            this.plugin.playback().stop(session);
            player.sendMessage(Text.prefix("Playback stopped.", NamedTextColor.YELLOW));
        }));
        actions.add(button("<aqua>Speed", session.speed() + "x", () -> {
            session.cycleSpeed();
            playback(player, session);
        }));
        actions.add(button("<gray>Seek −2s", null, () -> {
            session.setTick(session.tick() - 40);
            playback(player, session);
        }));
        actions.add(button("<gray>Seek +2s", null, () -> {
            session.setTick(session.tick() + 40);
            playback(player, session);
        }));
        actions.add(button("<light_purple>Loop", session.settings().loopLabel(), () -> {
            session.settings().setLoop(!session.settings().loop());
            if (session.settings().loop()) {
                session.settings().cycleLoopCount();
            }
            playback(player, session);
        }));
        actions.add(button("<green>Visibility", session.settings().visibilityMode().label(), () -> {
            session.settings().cycleVisibility();
            session.actors().forEach(actor -> actor.setVisibilityMode(session.settings().visibilityMode()));
            playback(player, session);
        }));
        actions.add(button("<gray>◀ Home", null, () -> home(player)));
        show(player, "Playback: " + session.name(), body, actions, 2);
    }

    private void playConfirm(Player player, Recording recording, Runnable back) {
        ActionButton play = ActionButton.create(
            mm("<green>Play"),
            mm("<gray>You will be teleported, then sent home when it ends"),
            150,
            DialogAction.customClick((view, audience) -> {
                if (audience instanceof Player clicker) {
                    later(() -> this.plugin.playback().play(recording, null, clicker));
                }
            }, ONCE)
        );
        ActionButton delete = ActionButton.create(
            mm("<red>Delete"),
            mm("<gray>Requires admin"),
            120,
            DialogAction.customClick((view, audience) -> {
                if (audience instanceof Player clicker) {
                    later(() -> confirmDelete(clicker, recording, back));
                }
            }, ONCE)
        );
        ActionButton backButton = ActionButton.create(mm("<gray>Back"), null, 120,
            DialogAction.customClick((view, audience) -> {
                if (audience instanceof Player) {
                    later(back);
                }
            }, ONCE));

        Dialog dialog = Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(mm("<gold><bold>" + recording.id() + "</bold>"))
                .canCloseWithEscape(true)
                .body(List.of(
                    DialogBody.plainMessage(mm(
                        "<gray>" + recording.durationTicks() + " ticks · "
                            + recording.tracks().size() + " tracks"
                            + (recording.hasWorldScene() ? " · world scene" : "")
                            + (recording.gameType() == null ? "" : " · " + recording.gameType())
                    ), 320)
                ))
                .build())
            .type(DialogType.multiAction(List.of(play, delete), backButton, 2)));
        show(player, dialog);
    }

    private void confirmDelete(Player player, Recording recording, Runnable back) {
        if (!player.hasPermission("mocap.admin")) {
            player.sendMessage(Text.prefix("No permission to delete.", NamedTextColor.RED));
            back.run();
            return;
        }
        ActionButton yes = ActionButton.create(mm("<red>Delete"), null, 120,
            DialogAction.customClick((view, audience) -> {
                if (audience instanceof Player clicker) {
                    later(() -> {
                        this.plugin.repository().delete(recording.id());
                        clicker.sendMessage(Text.prefix("Deleted " + recording.id(), NamedTextColor.YELLOW));
                        back.run();
                    });
                }
            }, ONCE));
        ActionButton no = ActionButton.create(mm("<gray>Cancel"), null, 120,
            DialogAction.customClick((view, audience) -> {
                if (audience instanceof Player clicker) {
                    later(back);
                }
            }, ONCE));
        Dialog dialog = Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(mm("<red>Delete " + recording.id() + "?"))
                .canCloseWithEscape(true)
                .body(List.of(DialogBody.plainMessage(mm("<gray>This cannot be undone."), 320)))
                .build())
            .type(DialogType.confirmation(yes, no)));
        show(player, dialog);
    }

    private ActionButton button(String label, String tooltip, Runnable click) {
        return ActionButton.create(
            mm(label),
            tooltip == null ? null : mm(tooltip),
            150,
            DialogAction.customClick((view, audience) -> {
                if (audience instanceof Player) {
                    later(click);
                }
            }, ONCE)
        );
    }

    private ActionButton cycle(String label, String value, Runnable click) {
        return button(label + " <white>" + value, "<gray>Click to cycle", click);
    }

    private ActionButton toggle(String label, boolean enabled, Runnable click) {
        return button(
            (enabled ? "<green>" : "<red>") + label,
            enabled ? "<gray>Enabled — click to disable" : "<gray>Disabled — click to enable",
            click
        );
    }

    private void later(Runnable task) {
        Bukkit.getScheduler().runTask(this.plugin, task);
    }

    private void show(Player player, String title, List<DialogBody> body, List<ActionButton> actions, int columns) {
        ActionButton close = ActionButton.create(mm("<gray>Close"), null, 120, null);
        Dialog dialog = Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(mm("<gold><bold>" + title + "</bold>"))
                .canCloseWithEscape(true)
                .body(body)
                .build())
            .type(DialogType.multiAction(actions, close, columns)));
        show(player, dialog);
    }

    private void show(Player player, Dialog dialog) {
        player.playSound(player, Sound.UI_BUTTON_CLICK, 1F, 1F);
        player.showDialog(dialog);
    }

    private static Component mm(String miniMessage) {
        return MINI.deserialize(miniMessage == null ? "" : miniMessage);
    }
}
