package com.paris.mocap;

import com.paris.mocap.actor.ActorService;
import com.paris.mocap.api.MocapApi;
import com.paris.mocap.api.MocapApiImpl;
import com.paris.mocap.command.MocapCommand;
import com.paris.mocap.command.MocapTabCompleter;
import com.paris.mocap.config.MocapConfig;
import com.paris.mocap.dialog.MocapDialogs;
import com.paris.mocap.playback.PlaybackService;
import com.paris.mocap.recording.ActionCaptureListener;
import com.paris.mocap.recording.RecordingService;
import com.paris.mocap.runtime.FailForward;
import com.paris.mocap.scene.SceneCaptureListener;
import com.paris.mocap.scene.StageWorldService;
import com.paris.mocap.storage.RecordingRepository;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class Mocap extends JavaPlugin {
    private FailForward failForward;
    private MocapConfig config;
    private RecordingRepository repository;
    private ActorService actors;
    private StageWorldService stage;
    private RecordingService recordings;
    private PlaybackService playback;
    private MocapDialogs dialogs;
    private MocapApi api;

    @Override
    public void onEnable() {
        this.failForward = new FailForward(getLogger());
        try {
            if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
                getLogger().severe("ProtocolLib is required. Disabling Mocap.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            this.config = new MocapConfig(this);
            this.config.reload();

            this.repository = new RecordingRepository(this, this.config);
            this.failForward.run("load-recordings", this.repository::loadAllSync);

            this.actors = new ActorService(this, this.config);
            this.actors.start();

            this.stage = new StageWorldService(this, this.config);

            this.recordings = new RecordingService(this, this.repository, this.config, this.failForward);
            this.playback = new PlaybackService(this, this.actors, this.stage, this.failForward, this.config);
            this.playback.startEngine();

            this.dialogs = new MocapDialogs(this);
            this.api = new MocapApiImpl(this, this.dialogs);
            getServer().getServicesManager().register(MocapApi.class, this.api, this, ServicePriority.Normal);

            getServer().getPluginManager().registerEvents(new ActionCaptureListener(this.recordings), this);
            getServer().getPluginManager().registerEvents(new SceneCaptureListener(this.recordings.scenes()), this);

            PluginCommand command = getCommand("mocap");
            if (command != null) {
                command.setExecutor(new MocapCommand(this));
                command.setTabCompleter(new MocapTabCompleter(this));
            }

            getLogger().info("Mocap enabled — dialogs, packet actors, MocapApi service.");
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to enable Mocap", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (this.api != null) {
            getServer().getServicesManager().unregister(MocapApi.class, this.api);
        }
        if (this.recordings != null) {
            this.recordings.close();
        }
        if (this.playback != null) {
            this.playback.close();
        }
        if (this.actors != null) {
            this.actors.close();
        }
        if (this.stage != null) {
            this.stage.close();
        }
        if (this.repository != null) {
            this.repository.close();
        }
        getLogger().info("Mocap disabled.");
    }

    public FailForward failForward() {
        return this.failForward;
    }

    public MocapConfig mocapConfig() {
        return this.config;
    }

    public RecordingRepository repository() {
        return this.repository;
    }

    public RecordingService recordings() {
        return this.recordings;
    }

    public PlaybackService playback() {
        return this.playback;
    }

    public ActorService actors() {
        return this.actors;
    }

    public StageWorldService stage() {
        return this.stage;
    }

    public MocapDialogs dialogs() {
        return this.dialogs;
    }

    public MocapApi api() {
        return this.api;
    }
}
