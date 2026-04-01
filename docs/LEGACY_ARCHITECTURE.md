# TradeFlow Cross‑Server Architecture & Roadmap

Ce document décrit une architecture propre pour faire fonctionner TradeFlow en multi‑serveurs, ainsi qu’une roadmap d’implémentation progressive :

- **Phase 1 :** MySQL propre comme source de vérité.
- **Phase 2 :** Ajout de Redis (cache + pub/sub).
- **Phase 3 :** Externalisation partielle dans un service Spring Boot ou Micronaut.

Le but est de garder la logique métier intacte tout en clarifiant où vivent les données, comment elles sont synchronisées, et quelles couches doivent être refactorisées.

---

## 1. Objectifs fonctionnels

- Plusieurs instances Paper/Folia partagent :
  - Les **prix** (auto‑pricing, recalculs, import/export).
  - Les **stocks globaux** (limites de vente par période).
  - Les **événements économiques** (bonus/malus appliqués aux prix).
- Comportement souhaité :
  - **Consistance suffisante** (pas forcément strictement transactionnelle, mais pas de décalages longs).
  - **Faible couplage** entre serveurs (un serveur peut tomber sans bloquer les autres).
  - **Charge maîtrisée** sur MySQL et la JVM du serveur de jeu.

---

## 2. État actuel (simplifié)

- **MySQL** est déjà utilisé pour :
  - `shops` / `prices` via `ShopData`, `PriceDatabaseAPI`, `MySQLPriceDatabaseAPIImpl`.
  - `tradeflow_global_stock` / `tradeflow_global_stock` via `GlobalStockData`.
  - `server_state` via `ServerStateData` (pour les événements économiques).
- **TradeFlow (plugin)** :
  - Garde des caches locaux : `loadedShops`, `loadedEconomyData`, `GlobalStockManager.stockCounts`, etc.
  - `GlobalStockManager` charge les stocks globaux au démarrage, puis met à jour en DB à chaque vente.
  - `EconomicEventManager` gère des événements locaux ou DB, mais la boucle de `tick()` n’est pas câblée dans le plugin.
- **Pas encore** de Redis ou de service externe.

L’architecture est déjà orientée vers MySQL comme point central, mais la synchronisation entre instances est principalement “best effort” (lecture au démarrage + écritures ponctuelles), sans diffusion active des changements.

---

## 3. Phase 1 – MySQL clean comme source de vérité

### 3.1. Objectifs

- Nettoyer les accès à MySQL pour en faire la **source de vérité unique** des données partagées (prix, stocks, événements).
- Supprimer au maximum les accès statiques type `TradeFlow.getInstance()` dans le code bas niveau (Database, services, utilitaires).
- Introduire des interfaces de repository simples pour clarifier les responsabilités.

### 3.2. Refactors à appliquer

1. **Repositories dédiés**
   - Introduire des interfaces pour les accès MySQL :
     - `PriceRepository` (lecture/écriture des prix calculés).
     - `ShopRepository` (lecture/écriture des définitions de shops si nécessaire).
     - `GlobalStockRepository` (GlobalStockData).
     - `ServerStateRepository` (ServerStateData).
   - Implémentations actuelles (`ShopData`, `GlobalStockData`, `ServerStateData`, `MySQLPriceDatabaseAPIImpl`) deviennent des adapters de ces interfaces.

2. **Services métier clairs**
   - `PriceService` existe déjà, mais le lier explicitement à `PriceRepository` plutôt qu’à des appels SQL “en dur”.
   - `GlobalStockManager` :
     - Ne parle qu’à `GlobalStockRepository` et `Database` (pour les shops).
   - `EconomicEventManager` :
     - Ne parle qu’à `ServerStateRepository` + `IEconomicEventSettings`.

3. **Injection propre dans TradeFlow**
   - Dans `TradeFlow.finishBootstrap()` :
     - Créer toutes les implémentations des repositories MySQL.
     - Injecter ces repositories dans :
       - `GlobalStockManager`.
       - `EconomicEventManager`.
       - `PricingManager` / `PriceService`.
   - Éviter les appels `new X()` imbriqués dans le bas niveau : tout le “câblage” doit se faire dans la classe principale.

4. **Cohérence d’EconomyDataUtil**
   - Garder `EconomyDataUtil` comme **service d’instance**, injecté dans :
     - `TradeFlow` (champ `economyDataUtil` non null).
     - `DefaultTransactionService` (déjà fait).
     - `Loan` (via `TradeFlow.getEconomyDataUtil()` ou injection, selon le niveau de refactor souhaité).
   - Supprimer toute confusion entre **méthodes statiques** et **instances** ; conserver uniquement le modèle “service d’instance”.

### 3.3. Comportement cross‑serveur avec MySQL seul

- **Prix** :
  - Recalculés sur un serveur (commande admin, tâches ponctuelles) et écrits dans MySQL.
  - Les autres serveurs lisent en DB au moment où ils en ont besoin ou via une tâche de refresh légère (facultatif).
- **Stocks globaux** :
  - Chaque vente met à jour la table de stocks.
  - Les serveurs chargent l’état initial au démarrage. Tant qu’ils ne rechargent pas, ils ne voient pas les ventes des autres serveurs (forte dépendance à la période de reset).
- **Événements économiques** :
  - `ServerStateData` stocke l’événement global actif et les timestamps.
  - La cohérence dépend de la fréquence d’appel de `EconomicEventManager.tick()`.

**Pros** :
- Archi simple, aucun composant supplémentaire.
- Déjà supporté par ton code avec quelques refactors.

**Cons** :
- Pas de propagation instantanée des changements (prix, stocks) entre serveurs.
- Pression accrue sur MySQL si on veut des “refresh” fréquents (polling).

---

## 4. Phase 2 – Ajout de Redis (cache + Pub/Sub)

### 4.1. Objectifs

- Diminuer la latence de propagation des changements cross‑serveur (prix, stocks, événements).
- Réduire le polling MySQL en s’appuyant sur une couche de cache + messages.

### 4.2. Composants Redis

1. **Cache clé/valeur**  
   - `redis://tradeflow:prices:<item>` → dernier prix.
   - `redis://tradeflow:stock:<item>` → sold_count / next_reset_timestamp.
   - TTL court (ex : 5–30 secondes) pour éviter le stale infini.

2. **Pub/Sub**  
   - Canal `tradeflow:price-updates`  
     - Payload minimal : `{"item":"diamond","price":123.45}`.
   - Canal `tradeflow:stock-updates`  
     - Payload : `{"item":"diamond","delta":64}` ou `{"item":"diamond","count":128}`.
   - Canal `tradeflow:event-updates`  
     - Payload : `{"eventName":"crash","state":"started"}` ou `{"state":"ended"}`.

### 4.3. Refactors nécessaires

1. **Adapter Redis dans la couche “infrastructure”**  
   - Créer un `RedisClient` simple dans le plugin ou dans une lib partagée :
     - `RedisCache` (get/set avec TTL).  
     - `RedisPubSub` (subscribe / publish).
   - Ces adapters ne connaissent pas la logique métier, uniquement des clés et des JSON.

2. **Hooks d’écriture (Write‑through + publish)**  
   - Lors d’un **recalcul de prix** :
     - Écrire dans MySQL (PriceRepository).
     - Écrire dans Redis (cache prix).
     - Publier sur `price-updates` avec la nouvelle valeur.
   - Lors d’une **vente** (stock global) :
     - Mettre à jour MySQL via `GlobalStockRepository`.
     - Publier sur `stock-updates` la nouvelle valeur ou le delta.
   - Lors d’un **changement d’événement** :
     - Mettre à jour `ServerStateRepository` (MySQL).
     - Publier sur `event-updates`.

3. **Listeners dans TradeFlow**  
   - Au démarrage d’un serveur :
     - S’abonner à `price-updates` : invalider le cache local (`loadedShops`/`PriceService`) pour l’item concerné, éventuellement recharger la valeur depuis Redis/MySQL.
     - S’abonner à `stock-updates` : synchroniser `GlobalStockManager.stockCounts`.
     - S’abonner à `event-updates` : informer `EconomicEventManager` d’un changement d’état (reload de la config d’event, recalcul des coefficients, etc.).

4. **Gestion des erreurs / fallback**  
   - En cas de panne Redis :
     - Continuer à fonctionner sur la base de MySQL uniquement (phase 1), au prix d’une latence plus forte.  
     - Les listeners doivent être robustes à un `disconnect` et se réabonner.

### 4.4. Pros / Cons de Redis

**Pros** :
- Propagation quasi instantanée des changements (millisecondes).
- Polling MySQL fortement réduit.
- Permet d’ajouter des features “temps réel” (graphes live, dashboards web, etc.).

**Cons** :
- Nouveau composant à administrer (redis-server, monitoring, sauvegardes AOF/RDB).  
- Complexité accrue : il faut gérer les cas où Redis n’est pas disponible.  
- Pour des petites infra, MySQL seul peut suffire si la réactivité n’est pas critique.

---

## 5. Phase 3 – Service externe (Spring Boot / Micronaut)

### 5.1. Objectifs

- Sortir les parties les plus lourdes / complexes de la JVM du serveur de jeu :
  - Calculs de pricing avancés (PricingManager, graphes, règles complexes).
  - Agrégations globales (statistiques, historiques de transactions).
- Offrir une API HTTP/REST ou gRPC pour qu’un ou plusieurs plugins Minecraft puissent consommer des services communs.

### 5.2. Design global

1. **Service “Market” standalone** (Spring Boot ou Micronaut) :
   - Connecté à la même base MySQL (et au même Redis) que les serveurs Minecraft.
   - Expose des endpoints :
     - `GET /prices/{itemId}` → `PricingData` (courant, historique).  
     - `POST /prices/recalculate` → lance un recalcul global (optionnellement asynchrone).  
     - `GET /stock/{itemId}` → stock global restant.  
     - `POST /stock/sale` → enregistre une vente globale.  
     - `GET /events/active` → événement économique actif.

2. **Plugin TradeFlow “mince”** :
   - Devient un client du service :
     - Pour lire les prix, les stocks, les événements.  
     - Pour enregistrer les transactions (callback HTTP) si souhaité.
   - Les caches locaux (TradeFlow) peuvent être conservés mais deviennent facultatifs, puisqu’ils peuvent être externalisés dans le service.

3. **Transition progressive** :
   - Extraire d’abord la logique de pricing (`PricingManager`, calculs) dans le service externe, en conservant `PriceRepository` et `PriceService` comme clients HTTP.  
   - Ensuite externaliser progressivement les agrégations, puis la gestion des événements si besoin.

### 5.3. Refactors nécessaires côté plugin

- Introduire des interfaces de service “remote” :
  - `RemotePriceService`, `RemoteStockService`, `RemoteEventService`.  
  - Implémentations HTTP (REST) via un petit client (par ex. `HttpClient`, `OkHttp`, `WebClient`).
- Adapter `TradeFlow` pour choisir à l’exécution :
  - Soit les implementations locales (MySQL/Redis dans le plugin).  
  - Soit les implémentations “remote” qui appellent le service Spring/Micronaut.

### 5.4. Pros / Cons du service externe

**Pros** :
- Scalabilité : le workload de pricing/stock peut être monté en plusieurs instances, indépendamment des serveurs de jeu.  
- Observabilité : plus facile de monitorer un service HTTP (metrics, traces) que chaque plugin.  
- Évolutivité métier : ajout de nouvelles règles / algos de pricing sans redéployer les serveurs Minecraft.

**Cons** :
- Complexité infra significative (service supplémentaire, déploiement, CI/CD, HA).  
- Latence réseau ajoutée (il faut gérer correctement les timeouts et les erreurs).  
- Nécessite une bonne discipline d’API (versionnage, compatibilité ascendante).

---

## 6. Roadmap détaillée (étapes concrètes)

### Étape 1 : MySQL clean

1. Introduire les interfaces de repository (Price, Shop, GlobalStock, ServerState).  
2. Adapter `ShopData`, `GlobalStockData`, `ServerStateData`, `MySQLPriceDatabaseAPIImpl` pour implémenter ces interfaces.  
3. Nettoyer `Database`, `GlobalStockManager`, `EconomicEventManager`, `DefaultTransactionService` pour qu’ils n’utilisent que ces interfaces et des services injectés.  
4. S’assurer que `EconomyDataUtil` est une instance injectée et non plus une pseudo‑utilitaire statique.

### Étape 2 : Redis

1. Ajouter une dépendance Redis (client Java léger, ex. Lettuce ou Jedis).  
2. Créer `RedisCache` et `RedisPubSub` (adapters).  
3. Brancher l’écriture : après chaque update MySQL (prix, stock, events), écrire dans Redis (cache) et publier un message Pub/Sub.  
4. Brancher la lecture :  
   - au démarrage, peupler les caches depuis MySQL ;  
   - écouter les events Redis et invalider/mise à jour des caches locaux (`ShopUtil`, `GlobalStockManager`, `EconomicEventManager`).  
5. Tester en condition multi‑serveurs (2–3 serveurs Paper connectés à la même DB + Redis).

### Étape 3 : Service externe Spring Boot / Micronaut

1. Créer un projet `tradeflow-market-service` (Spring Boot ou Micronaut) :  
   - y déplacer la logique de pricing (lecture/écriture MySQL/Redis).  
   - exposer des endpoints lisibles par le plugin.  
2. Introduire dans TradeFlow les interfaces de service remote ; implémenter un client HTTP minimal.  
3. Ajouter une configuration pour choisir entre “mode local” (logiciel actuel) ou “mode remote” (service externe).  
4. Migrer progressivement les features (d’abord le pricing, puis les stats et events globaux si besoin).  

---

## 7. Bilan global : pour et contre

**Points positifs du système proposé** :
- Isolation claire des responsabilités (repositories, services, plugin).  
- MySQL comme source de vérité : concept simple, déjà en place.  
- Redis apporte une propagation rapide des changements sans surcharger MySQL.  
- Un service externe optionnel permet d’aller vers une architecture micro‑services si ta plateforme grandit.

**Points négatifs / points d’attention** :
- Chaque étape ajoute de la complexité :
  - Phase 1 : refactors non triviaux mais localisés au plugin.  
  - Phase 2 : dépendance à Redis + gestion de la tolérance aux pannes.  
  - Phase 3 : architecture distribuée complète (latence, sécurité, versionnage d’API).  
- La cohérence “forte” (strictement idem sur tous les serveurs à chaque instant) reste difficile sans accepter une complexité très élevée (verrous distribués, consensus, etc.). On vise une **cohérence eventual** raisonnable.

En pratique, pour un plugin comme TradeFlow, **la combinaison MySQL propre + Redis (cache + Pub/Sub)** offre un très bon compromis entre simplicité, performance et cohérence. L’étape Spring Boot / Micronaut est surtout intéressante si tu veux faire évoluer le projet vers un véritable backend indépendant avec d’autres clients (webapp, services d’analyse, etc.).

