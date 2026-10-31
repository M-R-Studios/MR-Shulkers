# MR-Shulkers

Open shulker boxes straight from your inventory, without ever losing or duplicating items.

By **MR Studios**. For Paper 1.21.11, Java 21.

## Why MR-Shulkers

- **Items are never lost or duplicated.** The shulker's contents live in the item itself and are saved after every change, and again on close, quit, death and server stop. An open shulker can't be moved, dropped, crafted with, put in a hopper, crafter, item frame or anything else while it's open. Every one of those paths is blocked and tested.
- **Identical shulkers never share contents.** Each open shulker is marked with its own random ID while it's open, never matched by contents.
- **Kit plugins keep working.** The mark is removed when the shulker closes, so idle shulkers are plain vanilla items: they stack and compare exactly as before. Shulkers never open from other plugins' menus (kit previews, auction houses, `/invsee`).
- **Works for Bedrock players.** Open from hand or with `/shulker`, no right-click in the inventory needed. With Floodgate, Bedrock players can skip the sneak requirement.

## Features

- **Open from your inventory:** right-click a shulker box in your own inventory screen (`E`). The click can be `RIGHT` or `SHIFT_RIGHT`; the cursor must be empty, and other clicks behave as in vanilla. Clicking another shulker while one is open switches to it.
- **Open from your hand:** right-click the air while holding a shulker box. Right-clicking a block still places it. Sneaking can be required.
- **`/shulker`:** opens the shulker box in your main hand. The reliable way for Bedrock players.
- **Open from your ender chest:** click a shulker inside your ender chest (permission, op by default). Closing it takes you back to your ender chest.
- **Checks:** per-player cooldown, disabled worlds, WorldGuard regions, creative mode. Each has a message and a bypass permission.
- **Sounds:** separate open and close sounds, each with its own sound, volume and pitch, or turned off.
- **Window title:** the shulker's anvil name, or your own title from `messages.yml`.
- **Every message can be changed** (MiniMessage).
- **`/mrshulkers reload`** reloads both files and tells you exactly which file, line or setting is wrong. A broken file keeps its previous settings instead of breaking the plugin.

## Install

1. Put `MR-Shulkers-<version>.jar` in your server's `plugins/` folder.
2. Restart the server. `plugins/MR-Shulkers/config.yml` and `messages.yml` are created.
3. Optional: install **WorldGuard** for region control and **Floodgate** (with Geyser) for Bedrock detection. MR-Shulkers works the same without them.

Server-side only; players don't need any mod.

## Commands

| Command | Aliases | Permission | What it does |
|---|---|---|---|
| `/shulker` | `/openshulker` | `mrshulkers.command.shulker` | Opens the shulker box in your main hand. Works for Bedrock players too. |
| `/mrshulkers` | `/mrs` | `mrshulkers.admin` | Shows the list of subcommands. |
| `/mrshulkers reload` | | `mrshulkers.admin` | Reloads `config.yml` and `messages.yml`. Tells you exactly what failed and why; a file that fails to load keeps its previous settings. |
| `/mrshulkers version` | | `mrshulkers.admin` | Shows the plugin version, the server version and active hooks (WorldGuard, Floodgate). |

## Permissions

| Permission | Default | Meaning |
|---|---|---|
| `mrshulkers.*` | op | All of the below. |
| `mrshulkers.open` | everyone | Open shulker boxes by clicking them in your inventory screen. Without it, the click stays vanilla. |
| `mrshulkers.open.hand` | everyone | Open by right-clicking the air with a shulker box in your main hand. |
| `mrshulkers.open.enderchest` | op | Open shulker boxes stored in your ender chest. |
| `mrshulkers.command.shulker` | everyone | Use `/shulker`. |
| `mrshulkers.bypass.cooldown` | op | Ignore the cooldown between opens. |
| `mrshulkers.bypass.world` | op | Open in worlds listed in `disabled-worlds`. |
| `mrshulkers.bypass.region` | op | Open in WorldGuard regions with `mrshulkers-open` set to deny. |
| `mrshulkers.admin` | op | `/mrshulkers` and its subcommands. |

## Configuration

`config.yml` (every option is also explained in the file). After editing, run `/mrshulkers reload`.

| Setting | Default | Meaning |
|---|---|---|
| `open.inventory.enabled` | `true` | Open by clicking a shulker in your own inventory screen. |
| `open.inventory.click` | `RIGHT` | Which click opens it: `RIGHT` or `SHIFT_RIGHT`. Also used in the ender chest. |
| `open.hand.enabled` | `true` | Open by right-clicking the air with a shulker in your main hand. |
| `open.hand.require-sneak` | `false` | Only open from hand while sneaking. |
| `open.ender-chest.enabled` | `true` | Open shulkers stored in your ender chest (also needs `mrshulkers.open.enderchest`). |
| `open.ender-chest.return-on-close` | `true` | Reopen the ender chest after closing a shulker opened from it. |
| `open.allow-creative` | `true` | Allow players in creative mode to open shulkers this way. |
| `open.cooldown-ms` | `500` | Milliseconds between opens per player. `0` turns it off. |
| `open.disabled-worlds` | `[]` | Worlds where opening is disabled. Names are case-insensitive. |
| `open.worldguard` | `true` | Respect the WorldGuard flag `mrshulkers-open` when WorldGuard is installed. |
| `bedrock.floodgate` | `true` | Use Floodgate (if installed) to detect Bedrock players. |
| `bedrock.skip-sneak-requirement` | `true` | Bedrock players open from hand without sneaking, even if `require-sneak` is on. Needs Floodgate. |
| `window.use-item-name` | `true` | Use the shulker's anvil name as the window title when it has one. |
| `sounds.open.*` / `sounds.close.*` | shulker box sounds | `enabled`, `sound` (a sound key), `volume` (0.0–10.0), `pitch` (0.5–2.0). |
| `safety.watchdog-interval-ticks` | `5` | How often (in ticks, 1–20) to check that every open shulker is still in its owner's inventory. A backup for changes made by other plugins. |
| `safety.log-blocked-actions` | `false` | Log every attempt to move an open shulker. Useful for spotting dupe attempts. |

An invalid value falls back to its default, with a warning naming the setting, the bad value and what's accepted. When an update changes the file layout, the file is upgraded automatically and a backup (`config.yml.v1.bak`) is written first. Leave `config-version` and `messages-version` alone.

`messages.yml` holds every message in [MiniMessage](https://docs.advntr.dev/minimessage/format) format. `<prefix>` inserts the prefix; set a message to `""` to send nothing. A message with a broken tag falls back to the default, and reload tells you which one.

## WorldGuard

With WorldGuard installed, MR-Shulkers adds the region flag **`mrshulkers-open`** (default: allow). To stop opening shulkers in a region:

```
/rg flag <region> mrshulkers-open deny
```

Players there get "You can't open shulkers here." unless they have `mrshulkers.bypass.region`. The region is checked when opening; walking into a deny region with a shulker open doesn't close it. Set `open.worldguard: false` to ignore the flag.

## Bedrock (Geyser)

- Bedrock touch and controller players can't right-click a slot in the inventory screen. They open shulkers from hand (hold it and tap or use while looking at the air) or with `/shulker`. Both work with no extra plugins.
- With **Floodgate** installed, Bedrock players can open from hand without sneaking even when `open.hand.require-sneak` is on, since sneak and tap is awkward on touch screens. Turn this off with `bedrock.skip-sneak-requirement: false`.
- Without Floodgate, Bedrock players are treated like Java players; everything else works the same.

## Reporting bugs

Open an issue using the bug report form: [new issue](https://github.com/M-R-Studios/MR-Shulkers/issues/new/choose). Include your server version, MR-Shulkers version (`/mrshulkers version`), steps to reproduce, and for item problems what was in the shulker before and after. For questions and setup help, join the [MR Studios Discord](https://discord.gg/hTsrHsGcTg).

## Download

Get the latest jar from [Releases](https://github.com/M-R-Studios/MR-Shulkers/releases/latest).

---

© MR Studios. All rights reserved. The source code is not public; this repository hosts releases and bug reports.
