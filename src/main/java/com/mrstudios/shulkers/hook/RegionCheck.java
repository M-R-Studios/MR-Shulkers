package com.mrstudios.shulkers.hook;

import org.bukkit.entity.Player;

/** Whether a region plugin lets {@code player} open shulkers where they stand. */
@FunctionalInterface
public interface RegionCheck {

    boolean allows(Player player);
}
