package com.paris.mocap.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class Text {
    private Text() {
    }

    public static Component prefix(String message, NamedTextColor color) {
        return Component.text("[Mocap] ", NamedTextColor.GOLD).append(Component.text(message, color));
    }
}
