package ru.cloudwars.cloudrewards.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.function.Predicate;

public final class InventoryUtil {

    private InventoryUtil() {
    }

    /** Сколько слотов реально потребуется под {amount} предметов. */
    public static int slotsNeeded(PlayerInventory inv, int amount, int maxStack, Predicate<ItemStack> mergeable) {
        return fit(inv, amount, maxStack, mergeable).slots();
    }

    public static boolean hasSpace(PlayerInventory inv, int amount, int maxStack, Predicate<ItemStack> mergeable) {
        return fit(inv, amount, maxStack, mergeable).fits();
    }

    private static Fit fit(PlayerInventory inv, int amount, int maxStack, Predicate<ItemStack> mergeable) {
        int remaining = Math.max(0, amount);
        int slots = 0;
        int max = Math.max(1, maxStack);
        for (ItemStack item : inv.getStorageContents()) {
            if (remaining <= 0) break;
            if (item == null || item.getType().isAir()) {
                int take = Math.min(remaining, max);
                if (take > 0) {
                    remaining -= take;
                    slots++;
                }
            } else if (mergeable.test(item) && item.getAmount() < max) {
                int take = Math.min(remaining, max - item.getAmount());
                if (take > 0) {
                    remaining -= take;
                    slots++;
                }
            }
        }
        return new Fit(slots, remaining <= 0);
    }

    private record Fit(int slots, boolean fits) {
    }
}
