package com.mrstudios.shulkers.hook;

import org.bukkit.entity.Player;

/** Whether {@code player} is on a Bedrock client (through Geyser). */
@FunctionalInterface
public interface BedrockCheck {

    boolean isBedrock(Player player);
}
