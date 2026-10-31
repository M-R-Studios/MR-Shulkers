# Changelog

## [Unreleased]

## [1.0] - 2026-10-29

First release. Open shulker boxes without placing them, without ever losing or duplicating items.

### Added
- **Open shulker boxes from anywhere you carry them:**
  - right-click one in your inventory screen (`RIGHT` or `SHIFT_RIGHT`);
  - right-click the air with one in your hand (sneaking can be required);
  - `/shulker` (alias `/openshulker`) for the one in your hand;
  - click one in your own ender chest (permission, op by default), and closing it takes you back to the ender chest.
- **Clicking another shulker while one is open switches to it.**
- **Item safety:**
  - Contents are saved into the item after every change, and again on close, quit, death and server stop. Nothing is kept anywhere else.
  - An open shulker can't be moved, dropped, swapped, placed, copied, crafted with, put in hoppers, crafters, anvils or other workstations, or handed to item frames, armor stands or mobs.
  - Shulkers can't be put inside the open shulker.
  - Identical shulkers never share contents.
  - Stacked shulkers can't be opened.
  - If another plugin removes the open shulker (`/clear`, kits, invsee), the window simply closes. Your items are already in the item, and nothing is duplicated.
- **Kit-plugin friendly:** closed shulkers carry no extra data, and shulkers never open from other plugins' menus.
- **Checks with messages and bypass permissions:** per-player cooldown, disabled worlds, creative mode.
- **WorldGuard support (optional):** the `mrshulkers-open` region flag.
- **Bedrock support through Geyser:**
  - open from hand or with `/shulker`;
  - with Floodgate, Bedrock players can skip the sneak requirement.
- **Customisation:**
  - open and close sounds;
  - window title from the shulker's anvil name or your own text;
  - every message editable (MiniMessage).
- **Admin:**
  - `/mrshulkers reload` says exactly which file, line or setting is wrong, and keeps the last good settings;
  - `/mrshulkers version` shows the active hooks;
  - config files upgrade themselves on future updates, with a backup;
  - optional logging of blocked actions.
