package com.github.lye.gui;

import me.devnatan.inventoryframework.View;
import me.devnatan.inventoryframework.ViewConfigBuilder;
import me.devnatan.inventoryframework.ViewType;
import me.devnatan.inventoryframework.context.OpenContext;
import me.devnatan.inventoryframework.context.RenderContext;
import me.devnatan.inventoryframework.context.CloseContext;
import me.devnatan.inventoryframework.context.SlotClickContext;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.github.lye.TradeFlow;
import com.github.lye.access.AccessResolver;
import com.github.lye.config.Config;
import com.github.lye.config.ConfigResolver;
import com.github.lye.data.Database;
import com.github.lye.data.Section;
import com.github.lye.util.Format;

/**
 * Inventory Framework (DevNatan IF 3.5.5 platform-paper) GUI service.
 * Uses the platform View API (View, ViewConfigBuilder, RenderContext, etc.).
 */
public final class IfGuiService implements GuiService {

    private final TradeFlow plugin;
    private final Database database;
    private final AccessResolver accessResolver;
    private final ConfigResolver configResolver;
    private final java.util.Map<java.util.UUID, java.util.Map<String, Integer>> sectionPages = new java.util.concurrent.ConcurrentHashMap<>();

    public IfGuiService(@NotNull TradeFlow plugin,
                        @NotNull Database database,
                        @NotNull AccessResolver accessResolver,
                        @NotNull ConfigResolver configResolver) {
        this.plugin = plugin;
        this.database = database;
        this.accessResolver = accessResolver;
        this.configResolver = configResolver;
    }

    @Override
    public void openMainShop(@NotNull Player player) {
        final java.util.Map<Integer, String> slotToSection = new java.util.HashMap<>();
        View view = new View() {
            @Override
            public void onInit(@NotNull ViewConfigBuilder config) {
                config.title(Config.get().getGuiTitleShop());
                config.size(6);
                config.type(ViewType.CHEST);
                config.cancelInteractions();
            }

            @Override
            public void onOpen(@NotNull OpenContext open) {
                // no-op for now
            }

            @Override
            public void onFirstRender(@NotNull RenderContext render) {
                // Render sections at configured positions
                String[] names = com.github.lye.data.ShopUtil.getSectionNames(database);
                for (String sectionName : names) {
                    com.github.lye.data.Section s = com.github.lye.data.ShopUtil.getSection(database, sectionName);
                    if (s == null) continue;
                    int slot = Math.max(0, Math.min(53, s.getPosY() * 9 + s.getPosX()));
                    render.getContainer().renderItem(slot, s.getItem());
                    slotToSection.put(slot, sectionName);
                }
            }

            @Override
            public void onClick(@NotNull SlotClickContext click) {
                click.setCancelled(true);
                String sectionName = slotToSection.get(click.getSlot());
                if (sectionName != null) {
                    com.github.lye.data.Section s = com.github.lye.data.ShopUtil.getSection(database, sectionName);
                    if (s != null) openSection(player, s);
                }
            }

            @Override
            public void onClose(@NotNull CloseContext close) {
                // no-op
            }
        };

        openWithFrame(player, view);
    }

    @Override
    public void openSection(@NotNull Player player, @NotNull Section section) {
        final java.util.Map<Integer, String> slotToShop = new java.util.HashMap<>();
        final int[] pageRef = new int[]{0};
        View view = new View() {
            @Override
            public void onInit(@NotNull ViewConfigBuilder config) {
                config.title(section.getItem().displayName());
                config.size(6);
                config.type(ViewType.CHEST);
                config.cancelInteractions();
            }

            @Override
            public void onFirstRender(@NotNull RenderContext render) {
                java.util.List<String> items = new java.util.ArrayList<>(section.getShops().keySet());
                int start = pageRef[0] * 28;
                int end = Math.min(items.size(), start + 28);
                int[] slots = new int[]{10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
                slotToShop.clear();
                for (int sl : slots) render.getContainer().removeItem(sl);
                int idx = 0;
                for (int i = start; i < end && idx < slots.length; i++, idx++) {
                    String shopName = items.get(i);
                    com.github.lye.data.Shop shop = section.getShops().get(shopName);
                    if (shop == null) continue;
                    org.bukkit.Material mat = org.bukkit.Material.matchMaterial(shopName.toUpperCase());
                    if (mat == null) mat = org.bukkit.Material.BARRIER;
                    org.bukkit.inventory.ItemStack is = new org.bukkit.inventory.ItemStack(mat);
                    render.getContainer().renderItem(slots[idx], is);
                    slotToShop.put(slots[idx], shopName);
                }
                // Controls
                org.bukkit.inventory.ItemStack prev = new org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW);
                prev.editMeta(m -> m.displayName(net.kyori.adventure.text.Component.text("Prev")));
                org.bukkit.inventory.ItemStack next = new org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW);
                next.editMeta(m -> m.displayName(net.kyori.adventure.text.Component.text("Next")));
                org.bukkit.inventory.ItemStack back = new org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW);
                back.editMeta(m -> m.displayName(net.kyori.adventure.text.Component.text("Back")));
                if (pageRef[0] > 0) render.getContainer().renderItem(45, prev); else render.getContainer().removeItem(45);
                if (end < items.size()) render.getContainer().renderItem(53, next); else render.getContainer().removeItem(53);
                render.getContainer().renderItem(0, back);
            }

            @Override
            public void onClick(@NotNull SlotClickContext click) {
                click.setCancelled(true);
                int slot = click.getSlot();
                if (slot == 0) { openMainShop(player); return; }
                java.util.List<String> items = new java.util.ArrayList<>(section.getShops().keySet());
                int maxPage = (items.size() + 27) / 28 - 1;
                if (slot == 45 && pageRef[0] > 0) { pageRef[0]--; openSection(player, section); return; }
                if (slot == 53 && pageRef[0] < Math.max(0, maxPage)) { pageRef[0]++; openSection(player, section); return; }
                String shopName = slotToShop.get(slot);
                if (shopName != null) openPurchase(player, shopName);
            }
        };

        openWithFrame(player, view);
    }

    @Override
    public void openPurchase(@NotNull Player player, @NotNull String shopName) {
        final int[] amount = new int[]{1};
        View view = new View() {
            @Override
            public void onInit(@NotNull ViewConfigBuilder config) {
                config.title(Config.get().getGuiTitleShop());
                config.size(6);
                config.type(ViewType.CHEST);
                config.cancelInteractions();
            }

            @Override
            public void onFirstRender(@NotNull RenderContext render) {
                org.bukkit.Material mat = org.bukkit.Material.matchMaterial(shopName.toUpperCase());
                if (mat == null) mat = org.bukkit.Material.BARRIER;
                org.bukkit.inventory.ItemStack display = new org.bukkit.inventory.ItemStack(mat);
                render.getContainer().renderItem(22, display);

                org.bukkit.inventory.ItemStack p1 = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LIME_DYE);
                p1.editMeta(m -> m.displayName(net.kyori.adventure.text.Component.text("+1")));
                org.bukkit.inventory.ItemStack p8 = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LIME_DYE);
                p8.editMeta(m -> m.displayName(net.kyori.adventure.text.Component.text("+8")));
                org.bukkit.inventory.ItemStack p64 = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LIME_DYE);
                p64.editMeta(m -> m.displayName(net.kyori.adventure.text.Component.text("+64")));
                render.getContainer().renderItem(20, p1);
                render.getContainer().renderItem(21, p8);
                render.getContainer().renderItem(23, p64);

                org.bukkit.inventory.ItemStack buy = new org.bukkit.inventory.ItemStack(org.bukkit.Material.GREEN_CONCRETE);
                buy.editMeta(m -> m.displayName(net.kyori.adventure.text.Component.text("Buy")));
                org.bukkit.inventory.ItemStack sell = new org.bukkit.inventory.ItemStack(org.bukkit.Material.RED_CONCRETE);
                sell.editMeta(m -> m.displayName(net.kyori.adventure.text.Component.text("Sell")));
                render.getContainer().renderItem(29, buy);
                render.getContainer().renderItem(33, sell);
            }

            @Override
            public void onClick(@NotNull me.devnatan.inventoryframework.context.SlotClickContext click) {
                click.setCancelled(true);
                int slot = click.getSlot();
                if (slot == 20) { amount[0] = Math.min(64, amount[0] + 1); openPurchase(player, shopName); return; }
                if (slot == 21) { amount[0] = Math.min(64, amount[0] + 8); openPurchase(player, shopName); return; }
                if (slot == 23) { amount[0] = Math.min(64, amount[0] + 64); openPurchase(player, shopName); return; }
                if (slot == 29) { com.github.lye.data.PurchaseUtil.purchaseItem(database, shopName, player, amount[0], true); return; }
                if (slot == 33) { com.github.lye.data.PurchaseUtil.purchaseItem(database, shopName, player, amount[0], false); return; }
            }
        };

        openWithFrame(player, view);
    }

    @Override
    public void openEnchantLevels(@NotNull Player player, @NotNull String enchantShopName) {
        View view = new View() {
            @Override
            public void onInit(@NotNull ViewConfigBuilder config) {
                config.title(Config.get().getGuiTitleShop());
                config.size(6);
                config.type(ViewType.CHEST);
                config.cancelInteractions();
            }

            @Override
            public void onFirstRender(@NotNull RenderContext render) {
                org.bukkit.enchantments.Enchantment ench = org.bukkit.enchantments.Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(enchantShopName));
                if (ench == null) return;
                int max = ench.getMaxLevel();
                int[] slots = new int[]{10,11,12,13,14,15,16, 19,20,21,22,23,24,25};
                for (int i = 1; i <= max && i-1 < slots.length; i++) {
                    org.bukkit.inventory.ItemStack book = new org.bukkit.inventory.ItemStack(org.bukkit.Material.ENCHANTED_BOOK);
                    int level = i;
                    book.editMeta(m -> m.displayName(ench.displayName(level)));
                    render.getContainer().renderItem(slots[i-1], book);
                }
            }

            @Override
            public void onClick(@NotNull me.devnatan.inventoryframework.context.SlotClickContext click) {
                click.setCancelled(true);
                int[] slots = new int[]{10,11,12,13,14,15,16, 19,20,21,22,23,24,25};
                for (int i = 0; i < slots.length; i++) {
                    if (click.getSlot() == slots[i]) { openPurchaseEnchant(player, enchantShopName, i+1); return; }
                }
            }
        };
        openWithFrame(player, view);
    }

    @Override
    public void openPurchaseEnchant(@NotNull Player player, @NotNull String enchantShopName, int level) {
        final int[] amount = new int[]{1};
        View view = new View() {
            @Override
            public void onInit(@NotNull ViewConfigBuilder config) {
                config.title(Config.get().getGuiTitleShop());
                config.size(6);
                config.type(ViewType.CHEST);
                config.cancelInteractions();
            }

            @Override
            public void onFirstRender(@NotNull RenderContext render) {
                org.bukkit.inventory.ItemStack display = new org.bukkit.inventory.ItemStack(org.bukkit.Material.ENCHANTED_BOOK);
                render.getContainer().renderItem(22, display);
                org.bukkit.inventory.ItemStack p1 = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LIME_DYE);
                p1.editMeta(m -> m.displayName(net.kyori.adventure.text.Component.text("+1")));
                org.bukkit.inventory.ItemStack p8 = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LIME_DYE);
                p8.editMeta(m -> m.displayName(net.kyori.adventure.text.Component.text("+8")));
                org.bukkit.inventory.ItemStack p64 = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LIME_DYE);
                p64.editMeta(m -> m.displayName(net.kyori.adventure.text.Component.text("+64")));
                render.getContainer().renderItem(20, p1);
                render.getContainer().renderItem(21, p8);
                render.getContainer().renderItem(23, p64);
                org.bukkit.inventory.ItemStack buy = new org.bukkit.inventory.ItemStack(org.bukkit.Material.GREEN_CONCRETE);
                buy.editMeta(m -> m.displayName(net.kyori.adventure.text.Component.text("Buy")));
                org.bukkit.inventory.ItemStack sell = new org.bukkit.inventory.ItemStack(org.bukkit.Material.RED_CONCRETE);
                sell.editMeta(m -> m.displayName(net.kyori.adventure.text.Component.text("Sell")));
                render.getContainer().renderItem(29, buy);
                render.getContainer().renderItem(33, sell);
            }

            @Override
            public void onClick(@NotNull me.devnatan.inventoryframework.context.SlotClickContext click) {
                click.setCancelled(true);
                int slot = click.getSlot();
                if (slot == 20) { amount[0] = Math.min(64, amount[0] + 1); openPurchaseEnchant(player, enchantShopName, level); return; }
                if (slot == 21) { amount[0] = Math.min(64, amount[0] + 8); openPurchaseEnchant(player, enchantShopName, level); return; }
                if (slot == 23) { amount[0] = Math.min(64, amount[0] + 64); openPurchaseEnchant(player, enchantShopName, level); return; }
                String key = enchantShopName + ":" + level;
                if (slot == 29) { com.github.lye.data.PurchaseUtil.purchaseItem(database, key, player, amount[0], true); return; }
                if (slot == 33) { com.github.lye.data.PurchaseUtil.purchaseItem(database, key, player, amount[0], false); return; }
            }
        };
        openWithFrame(player, view);
    }

    @Override
    public void openSellPanel(@NotNull Player player) {
        View view = new View() {
            @Override
            public void onInit(@NotNull ViewConfigBuilder config) {
                config.title(Config.get().getGuiTitleSellPanel());
                config.size(5);
                config.type(ViewType.CHEST);
                // Allow item placement in GUI, handle settlement on close
            }

            @Override
            public void onClose(@NotNull CloseContext close) {
                // TODO: compute profits and return leftovers
            }
        };
        openWithFrame(player, view);
    }

    private void openWithFrame(@NotNull Player player, @NotNull View view) {
        Runnable openTask = () -> {
            try {
                Class<?> frameClass = Class.forName("me.devnatan.inventoryframework.ViewFrame");
                Object frame = null;
                for (java.lang.reflect.Constructor<?> ctor : frameClass.getDeclaredConstructors()) {
                    Class<?>[] params = ctor.getParameterTypes();
                    try {
                        ctor.setAccessible(true); // Allow access to private constructors
                        if (params.length == 1 && org.bukkit.plugin.Plugin.class.isAssignableFrom(params[0])) {
                            frame = ctor.newInstance(plugin);
                            break;
                        }
                    } catch (Throwable ignored) {
                    }
                }
                if (frame == null) {
                    throw new IllegalStateException("No suitable ViewFrame constructor found");
                }
                java.lang.reflect.Method withMethod = frameClass.getDeclaredMethod("with", View.class);
                withMethod.setAccessible(true);
                withMethod.invoke(frame, view);
                frameClass.getMethod("register").invoke(frame);
                // Try to find an an open-like method reflectively
                java.lang.reflect.Method chosen = null;
                Object[] args = null;
                for (java.lang.reflect.Method m : frameClass.getDeclaredMethods()) {
                    if (!m.getName().toLowerCase().contains("open")) continue;
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 3) {
                        // pattern: (Class, Player, Object)
                        if (Class.class.isAssignableFrom(p[0]) && Player.class.isAssignableFrom(p[1])) {
                            chosen = m; args = new Object[]{ view.getClass(), player, null }; break;
                        }
                    }
                }
                if (chosen == null) throw new NoSuchMethodException("No suitable open method on ViewFrame");
                chosen.setAccessible(true);
                chosen.invoke(frame, args);
            } catch (Throwable t) {
                try {
                    player.sendMessage("Failed to open GUI.");
                } catch (Throwable ignoredMsg) {}
                try {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "IF view open failed", t);
                } catch (Throwable ignoredLog) {}
            }
        };
        try {
            player.getScheduler().run(plugin, task -> openTask.run(), null);
        } catch (Throwable ignored) {
            openTask.run();
        }
    }
}


