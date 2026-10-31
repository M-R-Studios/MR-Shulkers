package com.mrstudios.shulkers.rules;

/** How the player asked to open a shulker, with the permission that path needs. */
public enum OpenPath {
    INVENTORY("mrshulkers.open"),
    HAND("mrshulkers.open.hand"),
    COMMAND("mrshulkers.command.shulker"),
    ENDER_CHEST("mrshulkers.open.enderchest");

    private final String permission;

    OpenPath(String permission) {
        this.permission = permission;
    }

    public String permission() {
        return permission;
    }
}
