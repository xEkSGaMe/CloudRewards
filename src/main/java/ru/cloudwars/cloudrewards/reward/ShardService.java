package ru.cloudwars.cloudrewards.reward;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;
import ru.cloudwars.cloudrewards.config.ConfigManager;
import ru.cloudwars.cloudrewards.util.InventoryUtil;
import ru.cloudwars.cloudrewards.util.TextUtil;

import java.util.UUID;

public final class ShardService {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private NamespacedKey key;
    private ConfigManager.ShardSettings settings;

    public ShardService(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void load() {
        settings = config.shard();
        key = new NamespacedKey(plugin, settings.pdcKey());
    }

    public ItemStack create(int amount) {
        ItemStack item = new ItemStack(settings.material(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(TextUtil.mm(settings.name()));
        meta.lore(TextUtil.mmList(settings.lore()));
        if (settings.hideFlags()) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        }
        if (settings.glint()) meta.setEnchantmentGlintOverride(true);
        if (settings.customModelData() != 0) meta.setCustomModelData(settings.customModelData());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isShard(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte marker = meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public int countShards(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (isShard(item)) total += item.getAmount();
        }
        return total;
    }

    private int maxStack() {
        return Math.max(1, new ItemStack(settings.material()).getMaxStackSize());
    }

    public boolean hasSpace(Player player, int amount) {
        return InventoryUtil.hasSpace(player.getInventory(), amount, maxStack(), this::isShard);
    }

    public int slotsNeeded(Player player, int amount) {
        return InventoryUtil.slotsNeeded(player.getInventory(), amount, maxStack(), this::isShard);
    }

    /** Выдача с учётом частично заполненных стаков PDC-шардов. */
    public boolean giveShards(Player player, int amount) {
        int remaining = amount;
        int max = maxStack();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!isShard(item) || item.getAmount() >= max) continue;
            int add = Math.min(remaining, max - item.getAmount());
            item.setAmount(item.getAmount() + add);
            player.getInventory().setItem(i, item);
            remaining -= add;
        }
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack current = player.getInventory().getStorageContents()[i];
            if (current != null && !current.getType().isAir()) continue;
            int give = Math.min(remaining, max);
            player.getInventory().setItem(i, create(give));
            remaining -= give;
        }
        return remaining <= 0;
    }

    /** Списание строго реальных PDC-шардов. Вызывать только в main thread. */
    public boolean removeShards(Player player, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!isShard(item)) continue;
            int take = Math.min(remaining, item.getAmount());
            remaining -= take;
            int left = item.getAmount() - take;
            if (left <= 0) {
                player.getInventory().setItem(i, null);
            } else {
                ItemStack copy = item.clone();
                copy.setAmount(left);
                player.getInventory().setItem(i, copy);
            }
        }
        return remaining <= 0;
    }

    /** Возврат после ошибки CoolContainers: онлайн — в инвентарь/на землю, офлайн — к последней точки. */
    public void refund(UUID uuid, int amount) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            if (!giveShards(player, amount)) {
                player.getWorld().dropItemNaturally(player.getLocation(), create(amount));
            }
            return;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        Location location = offline.getRespawnLocation();
        if (location != null && location.getWorld() != null) {
            location.getWorld().dropItemNaturally(location, create(amount));
        } else {
            plugin.getLogger().warning("Не удалось вернуть " + amount + " шардов игроку " + uuid + ": нет локации");
        }
    }

    public Component displayName() {
        return TextUtil.mm(settings.name());
    }
}
