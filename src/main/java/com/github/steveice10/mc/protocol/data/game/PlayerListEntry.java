package com.github.steveice10.mc.protocol.data.game;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.data.message.Message;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

@Data
@AllArgsConstructor
public class PlayerListEntry {
    private final @NonNull GameProfile profile;
    private final GameMode gameMode;
    private final int ping;
    private final Message displayName;

    public PlayerListEntry(GameProfile profile) {
        this(profile, null, 0, null);
    }

    public PlayerListEntry(GameProfile profile, GameMode gameMode) {
        this(profile, gameMode, 0, null);
    }

    public PlayerListEntry(GameProfile profile, int ping) {
        this(profile, null, ping, null);
    }

    public PlayerListEntry(GameProfile profile, Message displayName) {
        this(profile, null, 0, displayName);
    }
}
