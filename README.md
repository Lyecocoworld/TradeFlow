# 💱 TradeFlow  
### Cross-Server Dynamic Economy System for Minecraft

**TradeFlow** is a high-performance, scalable economy engine for Spigot, Paper, and Folia.  
It features real-time multi-server synchronization, dynamic pricing based on supply/demand, and advanced gameplay mechanics like a Black Market and Economic Events.

---

## 📚 Documentation

| Guide | Description |
| :--- | :--- |
| **[🎮 Gameplay Guide](docs/GAMEPLAY.md)** | For Players: How pricing works, Black Market, Rumors. |
| **[🛠️ Admin Guide](docs/ADMINISTRATION.md)** | For Admins: Installation, MySQL/Redis Config, Commands. |
| **[🏗️ Architecture](docs/ARCHITECTURE.md)** | For Devs: Repository Pattern, Async Write-Behind, Redis Protocol. |

---

## 🚀 Key Features

*   **Dynamic Pricing:** Prices adjust automatically based on player trading.
*   **Async Performance:** Built with a non-blocking "Write-Behind" architecture. Zero lag.
*   **Cluster Ready:** Syncs instantly across multiple servers via Redis.
*   **Anti-Farm:** Smart "Price Spread" prevents infinite money glitches.
*   **Black Market:** Rumors, Flash Sales, and secret trades.

---

## 💾 Database Support
- **MapDB** (Local File - Zero setup)
- **MySQL / MariaDB** (Enterprise storage)
- **Redis** (Real-time Sync)

---

*Documentation generated for TradeFlow v2.0*