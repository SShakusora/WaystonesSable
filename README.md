# WaystonesSable

A compatibility mod that bridges **[Waystones](https://www.curseforge.com/minecraft/mc-mods/waystones)** and **[Sable](https://www.curseforge.com/minecraft/mc-mods/sable)**, allowing waystones to function correctly inside Sable's **SubLevel** system.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Minecraft: 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)]()
[![Loader: NeoForge](https://img.shields.io/badge/Loader-NeoForge-orange.svg)]()

---

## 📖 Overview

[Sable](https://github.com/ryanhcode/Sable) introduces **SubLevels** — a system that embeds independent physical structures (plots) within the same dimension. However, Waystones does not natively understand these SubLevels, causing broken teleportation, invalid waystones, and desync when waystones are placed inside or target SubLevels.

**WaystonesSable** fixes these issues so that waystones work seamlessly across both normal space and SubLevels.

---

## ✨ Features

- **Teleportation Fix** — Correctly teleports players into and out of SubLevels, handling coordinate transforms, entity motion resets, and cross-dimensional logic.
- **Waystone Validation** — Waystones placed inside SubLevels are properly recognized as valid, even when the SubLevel is stored or unloaded.
- **Distance Calculation** — Sorting and distance checks account for SubLevel projections, so the closest waystone is actually the closest.
- **Client Synchronization** — SubLevel state, tracking, and freeze packets are synchronized to the client after teleportation to prevent rubber-banding and ghost waystones.
- **Warp Plate Support** — Fixes entity detection and arrival logic for Warp Plates located inside SubLevels.
- **SubLevel Labeling** — Waystones inside SubLevels are visually marked with a `[SubLevel]` tag in the selection screen.

---

## 📋 Requirements

| Dependency | Version      | Required |
|------------|--------------|----------|
| Minecraft | 1.21.1       | ✅ |
| NeoForge | \>= 21.1.219 | ✅ |
| [Waystones](https://www.curseforge.com/minecraft/mc-mods/waystones) | \>= 21.1.29  | ✅ |
| [Sable](https://www.curseforge.com/minecraft/mc-mods/sable) | \>= 1.1.0    | ✅ |
| [Create](https://www.curseforge.com/minecraft/mc-mods/create) | \>= 6.0.9    | ✅ |

> **Note:** This is a **server-side + client-side** mod. Both sides must have it installed for full functionality.

---

## 🚀 Installation

1. Install **NeoForge** for Minecraft 1.21.1.
2. Download and install **Waystones**, **Sable**, **Create**, and their dependencies.
3. Download the latest **WaystonesSable** jar and place it in your `mods` folder.
4. Launch the game — no additional configuration required.

---

## 🐛 Reporting Issues

If you encounter any bugs or unexpected behavior:

1. Make sure all dependencies are up to date.
2. Provide the **Minecraft version**, **NeoForge version**, and **mod versions**.
3. Include steps to reproduce the issue, and if possible, the relevant part of the log.

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

*Made with 💜 by Shinonome Shakusora.*
