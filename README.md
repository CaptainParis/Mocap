# Mocap

Paper plugin. Record player motion and world data. Play the recording as packet actors.

A packet actor is a ProtocolLib entity. It is not an NPC. It is not a player.

## Requirements

- Paper 1.21 or later (this build uses 1.21.11)
- Java 21
- ProtocolLib 5.3 or later (required)

## Install

1. Copy `Mocap-2.0.0.jar` and ProtocolLib into `plugins/`.
2. Start the server.
3. Run `/mocap`. Permission: `mocap.use` (default: op).

## Build

```bash
mvn -q package
```

Output file: `target/Mocap-2.0.0.jar`

## Commands

| Command | Function |
|---------|----------|
| `/mocap` | Open the home dialog |
| `/mocap record [name]` | Start a recording. If you omit the name, open the record dialog |
| `/mocap stop` | Stop the recording and save it |
| `/mocap play <id> [player]` | Play a recording. Optional: one player track |
| `/mocap library` | List saved recordings |
| `/mocap games` | List recordings by game type |
| `/mocap settings` | Change capture settings |
| `/mocap track [session]` | Control an active playback session |

## Permissions

| Node | Default | Function |
|------|---------|----------|
| `mocap.use` | op | Use commands and dialogs |
| `mocap.admin` | op | Delete recordings |

## Capture cycle

```
IDLE -> RECORDING -> FINALIZING -> READY -> PREPARING -> PLAYING <-> LOOPING -> STOPPING -> READY
```

- RECORDING: store pose and actions. Do not store a pose that did not change.
- FINALIZING: complete the chunk snapshot. Write the `.mcpb` file.
- PREPARING: copy the world snapshot to the stage world. Limit the work per tick.
- PLAYING: move actors. Apply world events.
- LOOPING: restore changed blocks. Spawn scene entities. Return to PLAYING.
- STOPPING: restore changed blocks. Clear the snapshot. Free the stage slot.

If one packet or world event fails, the plugin writes a log and continues. If a session fails many times, the plugin pauses that session.

When playback ends, actors despawn. The viewer returns to the start location.

## World capture modes

Set the mode in the settings dialog or in `config.yml`.

| Mode | Data stored |
|------|-------------|
| `OFF` | Players. Block place and break in the auto-box. No terrain snapshot |
| `AREA` | Terrain in the set radius |
| `AUTO_BOX` | Terrain around recorded motion |
| `LOADED_CHUNKS` | Nearby loaded chunks (default) |

## Plugin API

Other plugins can start a group recording with the Bukkit service.

```java
MocapApi api = MocapApi.get();
if (api != null) {
    String id = api.recordGroup("spleef", players);
    api.stopNamedRecording(id);
    api.play(id, viewer);
}
```

| Method or event | Function |
|-----------------|----------|
| `recordGroup` | Start one recording for many players |
| `joinRecording` | Add a player to an active recording |
| `detach` | Remove one player. Do not stop the recording |
| `stopNamedRecording` | Save the recording |
| `recordingsForGame` | List recordings for a game type |
| `MocapRecordingStartEvent` | Fired before capture. You can cancel it |
| `MocapRecordingStopEvent` | Fired after capture |
| `MocapPlaybackStartEvent` | Fired before playback. You can cancel it |
| `MocapPlaybackStopEvent` | Fired when playback stops |

The plugin always records held items and block place/break. This is true when world capture is `OFF`.

## Storage

Recordings are gzip binary files.

- Extension: `.mcpb`
- Magic: `MCPB`
- Format version: 5 (skin parts, entity reach, ping per frame). Version 4 added the game-type field.

Default path: `plugins/Mocap/recordings/`. If a file is corrupt, the plugin quarantines it on load. The plugin does not load `.json.gz` files.

## Configuration

See `src/main/resources/config.yml` for:

- storage path
- playback view distance
- world-capture defaults
- stage world name
- work limits per tick
- `games.*` group-capture settings

## Packages

| Package | Function |
|---------|----------|
| `com.paris.mocap` | Plugin start and stop |
| `runtime` | Error isolation. Session pause after repeated faults |
| `cycle` | Capture and playback phases. Settings tables |
| `actor` | Entity IDs. Packet actors. Viewer culling |
| `recording` | Pose sample. Action capture |
| `playback` | Shared ticker. Sessions. Exclusive viewers |
| `scene` | World capture. Snapshot copy. Stage world. Replay |
| `storage` | MCPB codec. Async file I/O |
| `dialog` | Paper dialog menus |
| `api` | Bukkit service and events |
