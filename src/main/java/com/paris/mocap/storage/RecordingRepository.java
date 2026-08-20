package com.paris.mocap.storage;

import com.paris.mocap.config.MocapConfig;
import com.paris.mocap.model.Recording;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public final class RecordingRepository implements AutoCloseable {
    public static final String EXTENSION = ".mcpb";

    private final JavaPlugin plugin;
    private final MocapConfig config;
    private final BinaryRecordingCodec codec = new BinaryRecordingCodec();
    private final ConcurrentHashMap<String, Recording> cache = new ConcurrentHashMap<>();
    private final ExecutorService ioExecutor;

    public RecordingRepository(JavaPlugin plugin, MocapConfig config) {
        this.plugin = plugin;
        this.config = config;
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "mocap-io-" + this.seq.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        this.ioExecutor = Executors.newSingleThreadExecutor(factory);
    }

    public void loadAllSync() {
        Path folder = this.config.recordingsFolder().toPath();
        try {
            Files.createDirectories(folder);
        } catch (IOException ex) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not create recordings folder", ex);
            return;
        }
        try (var stream = Files.list(folder)) {
            stream.filter(path -> path.getFileName().toString().endsWith(EXTENSION))
                .forEach(path -> {
                    try {
                        Recording recording = readFile(path);
                        this.cache.put(recording.id(), recording);
                    } catch (Exception ex) {
                        quarantine(path, ex);
                    }
                });
        } catch (IOException ex) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed listing recordings", ex);
        }
        this.plugin.getLogger().info("Loaded " + this.cache.size() + " recording(s).");
    }

    public List<Recording> list() {
        return new ArrayList<>(this.cache.values());
    }

    public Recording get(String id) {
        return this.cache.get(id);
    }

    public boolean exists(String id) {
        return this.cache.containsKey(id);
    }

    public void putMemory(Recording recording) {
        this.cache.put(recording.id(), recording);
    }

    public void save(Recording recording) {
        Objects.requireNonNull(recording, "recording");
        this.cache.put(recording.id(), recording);
        if (this.config.asyncIo()) {
            CompletableFuture.runAsync(() -> saveSync(recording), this.ioExecutor)
                .exceptionally(ex -> {
                    this.plugin.getLogger().log(Level.SEVERE, "Async save failed for " + recording.id(), ex);
                    return null;
                });
        } else {
            saveSync(recording);
        }
    }

    public void delete(String id) {
        this.cache.remove(id);
        Path path = fileFor(id);
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            this.plugin.getLogger().log(Level.WARNING, "Failed deleting " + path, ex);
        }
    }

    private void saveSync(Recording recording) {
        Path target = fileFor(recording.id());
        Path temp = target.resolveSibling(recording.id() + EXTENSION + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            try (OutputStream out = Files.newOutputStream(temp)) {
                this.codec.encode(recording, out);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed saving recording " + recording.id(), ex);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
        }
    }

    private Recording readFile(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return this.codec.decode(in);
        }
    }

    private Path fileFor(String id) {
        return this.config.recordingsFolder().toPath().resolve(id + EXTENSION);
    }

    private void quarantine(Path path, Exception ex) {
        Path corrupt = path.resolveSibling(path.getFileName() + ".corrupt." + System.currentTimeMillis());
        try {
            Files.move(path, corrupt, StandardCopyOption.REPLACE_EXISTING);
            this.plugin.getLogger().severe(
                "Corrupt recording " + path.getFileName() + " (" + ex.getMessage() + ") moved to " + corrupt.getFileName()
            );
        } catch (IOException moveEx) {
            this.plugin.getLogger().log(Level.SEVERE, "Corrupt recording " + path + " and could not quarantine", ex);
        }
    }

    @Override
    public void close() {
        this.ioExecutor.shutdownNow();
    }
}
