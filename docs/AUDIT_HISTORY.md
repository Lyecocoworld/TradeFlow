# TradeFlow – Audit Technique Complet

Date: 2025-11-16

Ce document présente une analyse exhaustive du plugin TradeFlow (Paper/Folia), couvrant l’architecture, la qualité de code, la performance, la stabilité, les risques, et des pistes d’amélioration concrètes avec priorisation. Il est conçu pour être immédiatement exploitable par les développeurs et mainteneurs.

---

## 1) Synthèse Générale

- Objet du plugin: Système d’économie et de marché dynamique avec interface GUI d’achat/vente, gestion des enchants, limites par joueur, stock global serveur, pricing automatique, événements économiques et exposition web (Jetty).
- Expérience joueur: Navigation en inventaires via Triumph GUI (3.1.2) pour la majorité des écrans et Inventory Framework (IF 3.5.5) pour une vue unifiée expérimentale, avec Adventure/MiniMessage pour un UI riche. Flux principaux:
  - MainShop → Section (pagination) → Purchase (item normal)
  - MainShop → Section → EnchantLevels → PurchaseEnchant (enchants; prix par niveau)
  - Mode vente coffre: sélection dans l’UI → clic droit coffre → vente en vrac
- Stockage: MapDB (mémoire + fichier) pour runtime; MySQL disponible pour certaines entités (ex. economy_data) et initialisation au démarrage. Vault gère l’économie.
- Folia: Ouvertures GUI avec `player.getScheduler()`; synchronisme respecté, logique d’achat/vente sérialisée par joueur.

---

## 2) Analyse Technique Approfondie

### 2.1 Structure et Packages

- Plugin core: `com.github.lye.TradeFlow` (cycle de vie, init, commandes, services)
  - Fichier: `src/main/java/com/github/lye/TradeFlow.java`
- Configuration et messages: `com.github.lye.config.Config` charge `config.yml`, `shops.yml`, `messages.yml`, `playerdata.yml`
  - Fichier: `src/main/java/com/github/lye/config/Config.java`
- Données (domaine): `com.github.lye.data`
  - `Shop` (prix courants, spread, change, limites, collect-first).
  - `PurchaseUtil` (achat/vente, sérialisation par joueur, messages).
  - `GlobalStockManager` (stock global).
  - `ShopUtil` (util DB/sections/limites).
  - `Transaction`, `Loan`, `CollectFirst`, `ShopSerializer`, `Database`.
- GUI: combinaison Triumph GUI + Inventory Framework
  - Navigation: `com.github.lye.gui.GuiNavigator` + `com.github.lye.gui.state.PlayerShopState` (état par joueur dans un `ConcurrentHashMap`, purge sur quit via `PlayerConnectionListener`).
  - Inventaires principaux (Triumph GUI): `MainShopGui`, `SectionGui`, `PurchaseGui`, `EnchantLevelsGui`, `PurchaseEnchantGui`.
  - Vue unifiée expérimentale (IF): `UnifiedShopView` (`View` + `renderer.*`), utilisée pour valider la migration depuis l'ancien `IfGuiService` (supprimé).
- Accès/collect-first: `com.github.lye.access.*` + `AccessGateway`.
- Pricing (expérimental): `com.yourplugin.pricing.*` (PriceEngine, PriceService, GUI de variants).
- Web: Jetty `server` (présence dépendances; UI statique sous `resources/web`).
- Événements joueur/coffre: `PlayerConnectionListener` (nettoyage état GUI) et `ChestSellSelector` (vente directe depuis un coffre).

### 2.2 Dépendances

- Paper/Folia API 1.21.8, Vault, Adventure (MiniMessage), MapDB, HikariCP, Jetty, Inventory Framework platform-bukkit 3.5.5, Triumph GUI 3.1.2.
- Ombrellage (ShadowJar): dépendances relocatées; IF et Triumph GUI intégrés dans le fatjar (selon configuration `shade`).
- Build: `build.gradle` définit la toolchain Java 21.

### 2.3 Patterns et Qualité de Code

- Points positifs:
  - Nav GUI structurée via `GuiNavigator` + `PlayerShopState`.
  - Locks par joueur dans `PurchaseUtil` (évite les doubles achats concurrents).
  - Abstraction config/messages via `Config` et `Format` (MiniMessage, i18n ready).
  - `AccessGateway` et `ConfigResolver` pour collect-first.
  - Nettoyage automatique de l’état GUI à la déconnexion (`PlayerConnectionListener`).
- Points perfectibles:
  - `PurchaseUtil`: méthode monolithique mêlant validation, économie, DB, messages → à refactorer en services spécialisés.
  - Rendu GUI (Triumph + IF) calcule dynamiquement des Components MiniMessage à chaque ouverture, sans cache global.
  - `Config` très longue (loading + valeurs runtime + messages).
  - Couplage fort entre commandes et implémentations internes (migration GUI en cours).

### 2.4 GUI & Interactions (Triumph GUI + IF 3.5.5)

_Remarque_: l'ancienne pile `IfGuiService`/`ViewFrame` a été retirée au profit d'une navigation `GuiNavigator` + Triumph GUI, avec une vue IF unifiée expérimentale (`UnifiedShopView`) pour la migration.

- MainShop (`MainShopGui`):
  - Affiche les sections (slots d’icônes) avec fond configuré.
  - Clic sur une section → `GuiNavigator.openSection`.
- Section (`SectionGui`):
  - Grille de shops: slots 10-16, 19-25, 28-34, 37-43.
  - 0 = retour MainShop; 45/53 = pagination; 49 = indicateur de page.
  - Lore shop via `messages.yml: shop-lore` avec placeholders (prix, sell-price, achats/ventes restantes, max, change).
- Purchase (`PurchaseGui`):
  - 0=retour Section; 22=showcase avec quantité et prix unitaires; 20/21/23=+1/+8/+64; 29=BUY, 33=SELL avec lore (`purchase-buy-lore` / `purchase-sell-lore`).
  - Interaction alignée sur les limites de joueur/stock et la disponibilité en base.
- EnchantLevels / PurchaseEnchant:
  - Niveaux en grille; achat via `purchaseEnchantment` (prix par niveau).
  - SELL d’abord géré via SellPanel / vente coffre.
- SellPanel (legacy / en refonte):
  - Anciennement: vue IF dédiée avec slots autorisés pour dépôt; 0=retour (rend tout), 40=confirmer (vente via `sellItemStack`), 44=annuler.
  - Actuellement: la commande `/tfsell` agit comme placeholder ("sell-panel-migrating") en attendant une nouvelle implémentation basée sur Triumph GUI / IF.
- Vue unifiée (`UnifiedShopView`):
  - `View` unique qui, en fonction du `PlayerShopState`, délègue le rendu à un `ScreenRenderer` spécialisé (MAIN, SECTION, PURCHASE, ENCHANT_LEVELS, PURCHASE_ENCHANT).
  - `onInit` configure taille (6×9), titre, annulation d’interactions.
  - `onFirstRender` / `onUpdate` redessinent complètement l’écran selon l’état.
  - Fond en verre (pane) sur l’écran principal, basé sur `Config.get().getBackground()` et `getBackgroundPaneText()`.
- Mode vente directe coffre (nouveau, `ChestSellSelector`):
  - Séquence: action de vente dans l’UI → `ChestSellSelector.beginSelection` → clic droit sur un coffre → scan et retrait des stacks correspondants dans l’inventaire du coffre → vente via `PurchaseUtil.sellItemStack`.
  - Supporte items classiques (Material) et livres enchantés (via `EnchantmentStorageMeta`).

### 2.5 Threads et Synchronisation

- GUI: ouvertures avec `player.getScheduler().run(...)` / `runDelayed(...)` (Folia-safe).
- Achats/Ventes: synchro par lock `playerLocks` (ConcurrentHashMap) dans `PurchaseUtil`, garantissant une seule opération critique par joueur.
- DB: initialisation MySQL majoritairement en onEnable (synchrones), runtime principalement MapDB/HashMap.

### 2.6 Gestion d'état

- État GUI par joueur centralisé dans `PlayerShopState` (stocké dans un `ConcurrentHashMap` au sein de `GuiNavigator`).
- Persistances partielles (quantité, page, section, item) conservées dans l'état pour une UX fluide.
- Purge automatique de l'état sur quit/kick via `PlayerConnectionListener`, limitant les états orphelins à long terme.

### 2.7 Accès BDD

- MySQL: tables d’économie (ex. `EconomyDataData`) avec create/insert/select synchrones au démarrage.
- MapDB: `Database` (HTreeMap) pour shops, transactions, economyData. Opérations runtime en mémoire (écriture amortie sur disque).
- `PurchaseUtil`: enregistre les transactions, met à jour les soldes et le stock.

---

## 3) Analyse des Performances

### 3.1 Principaux Risques

- Blocage main thread au démarrage: création/chargement tables MySQL synchrone.
- Coût de rendu GUI: MiniMessage pour chaque item (shop-lore) et boutons d’achat/vente — acceptable mais à optimiser par cache.
- Rendu fond: remplissage des 54 slots — coût minime mais répétitif.

### 3.2 Charge estimée

- CPU: faible à moyenne (pics lors de l’ouverture d’inventaires paginés et lors du parsing MiniMessage).
- Mémoire: MapDB + caches d’objets (shops) — raisonnable. Attention aux structures d’état conservées si la purge échoue.
- Scalabilité multi-nœuds: pas de cohérence forte inter-serveurs — utilisable en stand-alone. Pour réseau: envisager bus d’événements (Redis) et locks distribués.

### 3.3 Optimisations possibles

- Caching des Components (lore de shop, boutons) par clé (shop, page, amount) avec invalidations simples.
- Pré-création d’`ItemStack` statiques (panneaux de fond, flèches) clonés + set meta léger.
- Initialisation MySQL asynchrone au boot (voir plan d’actions).

---

## 4) Points Forts

- Architecture claire et proche du “feature complete” côté GUI.
- Respect de Folia (ouvertures via scheduler joueur).
- Sécurité logique d’achat/vente (locks par joueur; limites par joueur; stock global).
- Utilisation cohérente de Vault et Adventure.

---

## 5) Principaux Risques / Dettes

- `PurchaseUtil` monolithique — complexité, couplage fort, testabilité réduite.
- Rendu GUI sans cache — parsing MiniMessage répétitif; overhead évitable.
- onEnable MySQL synchrone — risque de boot lent sur I/O.
- Multi-serveur: pas de cohérence temps réel → risque d’incohérences prix/stock si DB partagée.

---

## 6) Notes (/10)

- Qualité du code: **7.0**
- Architecture: **7.5**
- Optimisation: **6.5**
- Lisibilité: **8.0**
- Stabilité: **7.5**
- Performances: **7.0**
- Usage API (Paper/Folia/IF/Vault): **8.0**
- Maintenabilité: **7.0**
- Scalabilité: **6.0**
- Cohérence globale: **7.5**

---

## 7) Pistes d’Amélioration (Plan d’Action)

### 7.1 Court Terme

1) Cache de lore GUI
   - Ajouter un cache pour les l ore de Section/Purchase (clé `(shopName, page)` / `(shopName, amount)`), invalidé sur modification de shop.
2) Normalisation encodage
   - S’assurer que les sources et messages sont en UTF-8 (Gradle OK; vérifier IDE, fichiers YAML).
3) Nettoyage logs debug GUI
   - Réduire le bruit des logs `[DEBUG] UnifiedShopView` en environnement prod.

### 7.2 Moyen Terme

4) Refactor `PurchaseUtil`
   - Extraire services: `PurchaseValidationService`, `PricingService`, `TransactionService`, `MessageService`.
   - Réduire le statique, introduire de l’“injection” simple via `TradeFlow`.
5) Sessions GUI enrichies
   - Étendre `PlayerShopState` pour couvrir les futurs écrans (SellPanel Triumph, filtres, etc.) tout en évitant l’état global.
6) Asynchronisme au boot
   - Déporter les initialisations MySQL non critiques dans une tâche asynchrone, avec logs d’état en console.

### 7.3 Long Terme

7) Cohérence multi-serveurs
   - Introduire un bus d’événements (Redis pub/sub) et un mécanisme de versionnage des prix/stock (optimistic locking) ou un “single writer”.
8) Observabilité & télémétrie
   - Compteurs (Micrometer/Prometheus) pour: achats/ventes/min, latences, failures, taille des caches GUI, durée render.
   - Dashboard web (Jetty) minimal.

---

## 8) Résumé Exécutif

TradeFlow est un plugin d’économie avancé avec une base solide (architecture, respect Folia, gestion d’état GUI par joueur, intégration Triumph/IF). Les principaux axes de travail sont la réduction de la complexité de `PurchaseUtil`, la mise en place de caches pour le rendu GUI, l’amélioration de l’initialisation MySQL et, à plus long terme, la cohérence multi-serveurs et l’observabilité.


---

## 9) Comment viser une moyenne de 9/10

- **Qualit� du code (7.0 ? 9.0)**
  - Refactorer \\PurchaseUtil\\ en plusieurs services focalis�s (validation, pricing, persistance, messages) avec tests unitaires cibl�s.
  - Rendre les d�pendances explicites (constructeurs ou factories dans \\TradeFlow\\) pour r�duire le statique et faciliter les tests et la substitution.

- **Architecture (7.5 ? 9.0)**
  - Stabiliser d�finitivement la nouvelle couche GUI (Triumph + IF unifi�) avec une responsabilit� claire par classe (Navigator, State, Renderers).
  - Clarifier le module web/pricing (packages d�di�s, interfaces nettes pour les int�grations futures type Redis, Prometheus, etc.).

- **Optimisation & performances (6.5/7.0 ? 9.0)**
  - Mettre en place le cache de lore/boutons (MiniMessage) et mesurer le gain (profilage simple ou compteurs de tendances).
  - Rendre l'initialisation MySQL non bloquante (async avec indicateurs de sant�) et limiter les E/S disque MapDB dans la boucle critique.
  - Introduire des compteurs de latence pour les op�rations sensibles (achat, vente, rendu GUI) et corriger syst�matiquement les hotspots.

- **Maintenabilit� & scalabilit� (7.0/6.0 ? 9.0)**
  - Documenter les principaux flux (s�quences GUI, transaction d'achat/vente, synchro stock) dans le code et dans ce document (diagrammes simples).
  - Isoler la logique multi-serveurs dans un module optionnel (pub/sub, locking) permettant de passer de 'stand-alone' � 'cluster' sans toucher au coeur.
  - Rendre l'observabilit� native (m�triques + quelques logs structur�s) pour que les r�gressions de perfs soient visibles rapidement.
- **Coh�rence globale & exp�rience dev (7.5 ? 9.0)**
  - Uniformiser les conventions (nommage, mise en forme, patterns d'acc�s aux services) et ajouter un guide CONTRIBUTING minimal.
  - Rendre reproductible l'environnement de dev/test (profil Gradle, jeux de donn�es d'exemple, sc�narios de test manuel) pour faciliter l'onboarding et les revues.
