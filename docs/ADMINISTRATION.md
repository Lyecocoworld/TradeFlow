# 🛠️ TradeFlow - Guide d'Administration

Ce guide couvre l'installation, la configuration et la gestion d'un réseau TradeFlow.

---

## 📦 Installation

1.  Déposez `TradeFlow.jar` dans le dossier `plugins/`.
2.  Lancez le serveur.
3.  Par défaut, le plugin utilise **MapDB** (fichiers locaux). Aucune base de données n'est requise pour commencer.

---

## ⚙️ Configuration Avancée (`config.yml`)

### 1. Base de Données (MySQL/MariaDB)
Pour les gros serveurs, passez à MySQL.
```yaml
database-enabled: true
database:
  host: "127.0.0.1"
  port: 3306
  database: "tradeflow"
  username: "root"
  password: "password"
  async-writes: true  # RECOMMANDÉ : Empêche les lags lors des sauvegardes
```

### 2. Clustering Multi-Serveur (Redis)
Pour synchroniser plusieurs serveurs (BungeeCord/Velocity).
```yaml
redis:
  enabled: true
  host: "127.0.0.1"
  cluster:
    enabled: true
    channel-prefix: "tradeflow" # Changez ceci pour avoir plusieurs réseaux distincts
```
*   **Fonctionnement :** Quand un prix change sur le Serveur A, un message Redis est envoyé. Le Serveur B le reçoit et met à jour son shop instantanément.

---

## 🛒 Configuration des Shops (`shops.yml`)

Chaque item peut être configuré individuellement.

```yaml
items:
  diamond:
    section: minerals
    price: 100.0          # Prix de base (Anchor)
    volatility: 0.5       # À quel point le prix bouge (0.1 = stable, 1.0 = crypto)
    max-buy: 64           # Limite par joueur
    sell-price-difference: 40 # Le prix de vente est 40% plus bas que l'achat (Anti-Farm)
```

---

## 💻 Commandes Admin

| Commande | Permission | Description |
| :--- | :--- | :--- |
| `/tfadmin reload` | `tradeflow.admin` | Recharge la configuration et les prix. |
| `/tfadmin import` | `tradeflow.admin` | Importe les shops de shops.yml vers la base de données. |
| `/tfadmin migrate` | `tradeflow.admin` | Migre les données de MapDB vers MySQL. |
| `/tfadmin setprice <item> <prix>` | `tradeflow.admin` | Force le prix d'un item (attention, l'économie réagira). |

---

## 🚀 Performance Tuning

### Async Write-Behind
TradeFlow utilise un système d'écriture asynchrone.
*   **Avantage :** Le serveur ne freeze jamais, même si la DB est lente.
*   **Sécurité :** À l'arrêt du serveur, TradeFlow force la sauvegarde de toutes les données en attente (Graceful Shutdown).

### Pruning
Les vieilles transactions sont automatiquement purgées après 30 jours pour garder la base de données légère.
