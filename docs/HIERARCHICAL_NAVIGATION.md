# Navigation Hiérarchique - Guide d'Architecture

## 📋 Problème Résolu

### Ancien Système (Navigation Chronologique)
```
MainShopGui → SectionGui → PurchaseGui
↓ (clic back)
Revient à PurchaseGui ❌
```
Le bouton "Back" utilisait une pile LIFO (Last-In-First-Out) basée sur l'ordre chronologique des visites.

### Nouveau Système (Navigation Hiérarchique)
```
MainShopGui → SectionGui → PurchaseGui
↓ (clic back)
Revient à SectionGui ✅
↓ (clic back)
Revient à MainShopGui ✅
```
Le bouton "Back" utilise maintenant une hiérarchie parent-enfant fixe, indépendante de l'ordre de visite.

---

## 🏗️ Architecture Hiérarchique

### Diagramme Complet des GUIs Joueur

```
MainShopGui (root)
│
├─ SectionGui
│  └─ PurchaseGui
│
├─ EnchantLevelsGui
│  └─ PurchaseEnchantGui
│
├─ UtilityGui
│  ├─ LicenseGui
│     └─ LicenseConfirmGui (close ou back → LicenseGui)
│  │
│  └─ StatsSelectionGui
│     ├─ PlayerStatsGui
│     ├─ ServerStatsGui
│     └─ OrganizationStatsGui
│
├─ HelpGui
│  └─ DocsGui
│
├─ RumorGui
│
└─ BlackMarketGui
```

### Diagramme des GUIs Admin

```
AdminMainMenu (root)
│
├─ AdminSystemGui
│
├─ AdminEconomyGui
│
├─ AdminShopsGui
│
├─ AdminTransactionsGui
│
├─ AdminNotificationsGui
│
└─ AdminPlayersGui
```

---

## 🔧 Comment Ça Fonctionne

### 1. Définition de la Hiérarchie (`GuiHierarchy.java`)

Chaque GUI a un **parent fixe** défini dans une Map:

```java
// Exemples de définitions
PARENTS.put("main_menu", null);              // root (pas de parent)
PARENTS.put("section", "main_menu");         // SectionGui → MainShopGui
PARENTS.put("purchase", "section");          // PurchaseGui → SectionGui
PARENTS.put("utility", "main_menu");         // UtilityGui → MainShopGui
PARENTS.put("license", "utility");           // LicenseGui → UtilityGui
```

### 2. État de Navigation (`GuiNavigator.java`)

Le navigateur stocke pour chaque joueur:
- **Current GUI ID** : Le GUI actuellement affiché
- **Contexts** : Les données nécessaires pour restaurer l'état (sectionId, itemId, etc.)

```java
// Exemple de stockage
currentGuis.put(playerUuid, "purchase");
itemContexts.put(playerUuid, "DIAMOND");
sectionContexts.put(playerUuid, "Minerais");
```

### 3. Méthode `goBack()` Hiérarchique

```java
public boolean goBack(Player player) {
    // 1. Obtenir le GUI actuel
    String currentGui = getCurrentGui(player); // "purchase"

    // 2. Trouver le parent dans la hiérarchie
    String parentGui = GuiHierarchy.getParent(currentGui); // "section"

    // 3. Naviguer vers le parent en utilisant le contexte stocké
    return navigateToGui(player, parentGui);
}
```

---

## 🎯 Scénarios de Navigation

### Scénario 1 : Achat d'Item Simple

```
1. MainShopGui
   ↓ (clic section "Minerais")
2. SectionGui (current = "section", sectionContext = "Minerais")
   ↓ (clic item "DIAMOND")
3. PurchaseGui (current = "purchase", itemContext = "DIAMOND")
   ↓ (clic back)
   → GuiHierarchy.getParent("purchase") = "section"
   → openSection(player, "Minerais")
4. SectionGui ✅ (revient à la bonne section)
   ↓ (clic back)
   → GuiHierarchy.getParent("section") = "main_menu"
   → openMain(player)
5. MainShopGui ✅
```

### Scénario 2 : Navigation avec Enchantements

```
1. MainShopGui
   ↓ (clic section "Enchantements")
2. SectionGui (sectionContext = "Enchantements")
   ↓ (clic enchant "Sharpness")
3. EnchantLevelsGui (enchantContext = "Sharpness")
   ↓ (clic niveau 5)
4. PurchaseEnchantGui (enchantContext = "Sharpness", level = 5)
   ↓ (clic back)
5. EnchantLevelsGui ✅
   ↓ (clic back)
6. SectionGui ✅ (toujours sur "Enchantements")
   ↓ (clic back)
7. MainShopGui ✅
```

### Scénario 3 : Navigation Utility → Stats

```
1. MainShopGui
   ↓ (clic Utility)
2. UtilityGui
   ↓ (clic Stats)
3. StatsSelectionGui
   ↓ (clic Server Stats)
4. ServerStatsGui
   ↓ (clic back)
5. StatsSelectionGui ✅
   ↓ (clic back)
6. UtilityGui ✅
   ↓ (clic back)
7. MainShopGui ✅
```

---

## ✅ Avantages du Système Hiérarchique

### 1. **Prédictibilité**
Le joueur sait toujours où le bouton "Back" va le mener, car la hiérarchie est fixe.

### 2. **Indépendance du Chemin**
Peu importe comment le joueur est arrivé à un GUI, le bouton "Back" mène toujours au même parent.

### 3. **Pas de Boucles Infinies**
Impossible de créer des cycles dans la hiérarchie (contrairement à une pile chronologique).

### 4. **Contexte Préservé**
Les données importantes (sectionId, itemId) sont stockées et restaurées automatiquement.

### 5. **Fallback Naturel**
Si un contexte est manquant, le système remonte automatiquement à la racine (MainShopGui).

---

## 🔨 Implémentation pour les Nouveaux GUIs

### Étape 1 : Ajouter le GuiId dans `NavigationHistory.GuiIds`

```java
public static final class GuiIds {
    // ...
    public static final String MY_NEW_GUI = "my_new_gui";
}
```

### Étape 2 : Définir le Parent dans `GuiHierarchy`

```java
static {
    // ...
    PARENTS.put(NavigationHistory.GuiIds.MY_NEW_GUI, NavigationHistory.GuiIds.UTILITY);
}
```

### Étape 3 : Créer la méthode d'ouverture dans `GuiNavigator`

```java
public void openMyNewGui(Player player, String context) {
    setCurrentGui(player, NavigationHistory.GuiIds.MY_NEW_GUI);
    setMyContext(player, context); // Stocker le contexte

    MyNewGui gui = new MyNewGui(plugin, this, player);
    player.getScheduler().run(plugin, task -> gui.open(player), null);
}
```

### Étape 4 : Gérer la navigation dans `navigateToGui()`

```java
private boolean navigateToGui(Player player, String guiId) {
    return switch (guiId) {
        // ...
        case NavigationHistory.GuiIds.MY_NEW_GUI -> {
            String context = getMyContext(player);
            if (context != null) {
                openMyNewGui(player, context);
                yield true;
            }
            openMain(player); // Fallback
            yield true;
        }
        // ...
    };
}
```

---

## 🧪 Tester la Navigation

### Test Manuel

1. Ouvrir le shop: `/trade` ou `/tf`
2. Naviguer vers une section
3. Cliquer sur un item
4. Cliquer sur "Back" → doit revenir à la section
5. Cliquer sur "Back" → doit revenir au menu principal

### Test Automatisé (Unit Test)

```java
@Test
public void testHierarchicalNavigation() {
    Player player = mockPlayer();
    GuiNavigator navigator = new GuiNavigator(plugin);

    // Simuler navigation
    navigator.openMain(player);
    navigator.openSection(player, "Minerais");
    navigator.openPurchase(player, "DIAMOND");

    // Vérifier current GUI
    assertEquals("purchase", navigator.getCurrentGui(player));

    // Simuler clic back
    navigator.goBack(player);

    // Vérifier retour à section
    assertEquals("section", navigator.getCurrentGui(player));

    // Simuler clic back
    navigator.goBack(player);

    // Vérifier retour au main
    assertEquals("main_menu", navigator.getCurrentGui(player));

    // Plus de back possible (root)
    assertFalse(navigator.canGoBack(player));
}
```

---

## 📝 Checklist d'Intégration

- [x] `GuiHierarchy.java` créé avec définitions parent-enfant
- [x] `GuiNavigator.java` mis à jour pour utiliser la hiérarchie
- [x] `NavigationHistory.GuiIds` mis à jour avec tous les GUIs
- [x] Méthode `goBack()` utilise `GuiHierarchy.getParent()`
- [x] Contextes stockés et restaurés (section, item, enchant)
- [x] Méthode `cleanup()` pour nettoyer l'état joueur
- [ ] Tester tous les GUIs joueur
- [ ] Tester tous les GUIs admin
- [ ] Mettre à jour les GUIs admin pour utiliser la hiérarchie
- [ ] Ajouter navigation hiérarchique pour AdminNavigator

---

## 🚀 Améliorations Futures Possibles

### 1. Bouton "Home" (Raccourci vers la racine)
```java
NavigationBar.apply(gui, new Config(rows)
    .showHome(true)
    .onBack(() -> navigator.goBack(player))
    .onHome(() -> navigator.openMain(player))
    .showClose(true)
);
```

### 2. Breadcrumbs (Fil d'Ariane)
```
Main > Minerais > Diamond
```
Permet de voir et cliquer sur n'importe quel niveau de la hiérarchie.

### 3. Navigation Admin avec Historique
Les admins pourraient avoir un bouton "History" pour voir les GUIs visités
et revenir à n'importe lequel (utile pour les tâches d'administration).

---

**Auteur**: lye
**Version**: 0.2
**Date**: 2025-01-16
