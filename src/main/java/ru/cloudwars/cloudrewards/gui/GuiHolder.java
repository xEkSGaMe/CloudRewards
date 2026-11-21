package ru.cloudwars.cloudrewards.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class GuiHolder implements InventoryHolder {

    public enum Kind {
        MAIN,
        MISSIONS
    }

    private final Kind kind;
    private final Inventory inventory;

    public GuiHolder(Kind kind, int size, Component title) {
        this.kind = kind;
        this.inventory = Bukkit.createInventory(this, Math.max(9, Math.min(54, size)), title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Kind kind() {
        return kind;
    }
}
