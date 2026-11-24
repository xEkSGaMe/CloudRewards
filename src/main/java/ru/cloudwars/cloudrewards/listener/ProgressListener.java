package ru.cloudwars.cloudrewards.listener;

import io.papermc.paper.event.block.TargetHitEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.cloudwars.cloudrewards.mission.ActiveMission;
import ru.cloudwars.cloudrewards.mission.MissionDefinition;
import ru.cloudwars.cloudrewards.mission.MissionService;
import ru.cloudwars.cloudrewards.mission.MissionType;
import ru.cloudwars.cloudrewards.mission.MissionTypeHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ProgressListener implements Listener {

    private final JavaPlugin plugin;
    private final MissionService missions;
    private final Map<UUID, Long> lastDay = new HashMap<>();

    public ProgressListener(JavaPlugin plugin, MissionService missions) {
        this.plugin = plugin;
        this.missions = missions;
        startTickTask();
    }

    private void startTickTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                apply(player, MissionType.PLAY_TIME, 1, null);
                UUID uuid = player.getUniqueId();
                // Дистанцию считает DistanceTracker по фактическому перемещению.
                long day = player.getWorld().getFullTime() / 24000L;
                Long previous = lastDay.put(uuid, day);
                if (previous != null && day > previous) {
                    apply(player, MissionType.NO_SLEEP_DAYS, 1, null);
                }
            }
        }, 100L, 20L);
    }

    private void apply(Player player, MissionType type, int amount,
                       java.util.function.Predicate<MissionDefinition> filter) {
        missions.apply(player, type, amount, filter);
    }

    private void applyUnique(Player player, MissionType type, String key,
                             java.util.function.Predicate<MissionDefinition> filter) {
        missions.applyUnique(player, type, key, filter);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Material material = event.getBlock().getType();
        apply(event.getPlayer(), MissionType.BREAK_BLOCK, 1,
                definition -> MissionTypeHandler.material(definition, material));
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        apply(player, MissionType.CONSUME_ITEM, item.getAmount(),
                definition -> MissionTypeHandler.material(definition, item.getType()));
        apply(player, MissionType.DRINK_POTION, 1,
                definition -> MissionTypeHandler.potion(definition, item));
    }

    @EventHandler
    public void onStatistic(PlayerStatisticIncrementEvent event) {
        missions.syncStatistic(event.getPlayer(), event.getStatistic());
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        Player attacker = null;
        if (damager instanceof Player player) {
            attacker = player;
        } else if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            attacker = player;
        }
        if (attacker != null && !(victim instanceof Player)) {
            apply(attacker, MissionType.DEAL_DAMAGE, Math.max(1, (int) Math.ceil(event.getFinalDamage())), null);
        }

        if (damager instanceof Player player && victim instanceof Player) {
            Material weapon = player.getInventory().getItemInMainHand().getType();
            apply(player, MissionType.HIT_PLAYER_WITH_ITEM, 1,
                    definition -> MissionTypeHandler.material(definition, weapon));
        }

        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter
                && victim instanceof Player) {
            apply(shooter, MissionType.SHOOT_PLAYER, 1,
                    definition -> MissionTypeHandler.projectile(definition, projectile.getType()));
        }

        if (victim instanceof Player defender && defender.isBlocking() && damager instanceof Trident) {
            apply(defender, MissionType.BLOCK_TRIDENT, 1, null);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.hasItem() && event.getItem().getType() == Material.BONE_MEAL
                && (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR)) {
            apply(player, MissionType.USE_BONE_MEAL, 1, null);
        }
        if (event.getAction() == Action.PHYSICAL && event.hasBlock()
                && event.getClickedBlock().getType() == Material.FARMLAND) {
            apply(player, MissionType.TRAMPLE_FARMLAND, 1, null);
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        Entity target = event.getRightClicked();
        if (item == null || item.getType().isAir()) return;

        if (item.getType() == Material.NAME_TAG) {
            String actual = "";
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                actual = item.getItemMeta().getDisplayName();
            }
            String name = actual;
            apply(player, MissionType.RENAME_ENTITY, 1, definition -> {
                String expected = definition.text("name", "");
                boolean matches = definition.flag("ignore-case")
                        ? expected.equalsIgnoreCase(name)
                        : expected.equals(name);
                return matches && MissionTypeHandler.entity(definition, target.getType());
            });
        }

        if (item.getType() == Material.FLINT_AND_STEEL) {
            apply(player, MissionType.IGNITE_ENTITY, 1,
                    definition -> MissionTypeHandler.entity(definition, target.getType()));
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        Material placed = event.getBlockPlaced().getType();
        Material on = event.getBlockAgainst().getType();
        apply(event.getPlayer(), MissionType.PLANT_ON, 1, definition ->
                placed.name().equalsIgnoreCase(definition.text("place", ""))
                        && on.name().equalsIgnoreCase(definition.text("on", "")));
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        apply(event.getPlayer(), MissionType.RUN_COMMAND, 1,
                definition -> MissionTypeHandler.command(definition, event.getMessage()));
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType().isAir()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Material material = result.getType();
        int amount = craftAmount(event);

        apply(player, MissionType.CRAFT_ITEM, amount,
                definition -> MissionTypeHandler.material(definition, material));
        applyUnique(player, MissionType.CRAFT_COLLECTION, material.name(),
                definition -> MissionTypeHandler.material(definition, material));
        applyUnique(player, MissionType.OBTAIN_COLLECTION, material.name(),
                definition -> MissionTypeHandler.material(definition, material));

        if (material == Material.FIREWORK_ROCKET && result.getItemMeta() instanceof FireworkMeta meta) {
            int power = meta.getPower();
            apply(player, MissionType.CRAFT_FIREWORK_POWER, amount,
                    definition -> definition.number("power", -1) == power);
        }
    }

    private int craftAmount(CraftItemEvent event) {
        ItemStack result = event.getInventory().getResult();
        int base = Math.max(1, result.getAmount());
        if (event.getClick() != ClickType.SHIFT_LEFT && event.getClick() != ClickType.SHIFT_RIGHT) return base;

        Map<Material, Integer> counts = new HashMap<>();
        Map<Material, Integer> slots = new HashMap<>();
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item == null || item.getType().isAir()) continue;
            counts.merge(item.getType(), item.getAmount(), Integer::sum);
            slots.merge(item.getType(), 1, Integer::sum);
        }
        int max = 64;
        for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
            max = Math.min(max, entry.getValue() / slots.get(entry.getKey()));
        }
        return base * Math.max(1, max);
    }

    @EventHandler
    public void onSmith(SmithItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType().isAir()) return;
        ItemStack mineral = event.getInventory().getInputMineral();
        boolean netherite = result.getType().name().startsWith("NETHERITE")
                || (mineral != null && mineral.getType().name().contains("NETHERITE"));
        if (netherite) apply(player, MissionType.SMITHING_NETHERITE, 1, null);
    }

    @EventHandler
    public void onAnvil(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.ANVIL || event.getSlot() != 2) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        apply(player, MissionType.ANVIL_COMBINE, 1, null);
    }

    @EventHandler
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Material source = event.getBlock().getType();
        Material extracted = event.getItemType();
        apply(event.getPlayer(), MissionType.FURNACE_EXTRACT, event.getItemAmount(),
                definition -> definition.strings("sources").stream()
                        .anyMatch(name -> name.equalsIgnoreCase(source.name())
                                || name.equalsIgnoreCase(extracted.name())));
        // Выплавка выдаёт предмет сразу в инвентарь, без EntityPickupItemEvent.
        apply(event.getPlayer(), MissionType.OBTAIN_ITEM, event.getItemAmount(),
                definition -> MissionTypeHandler.material(definition, extracted));
        applyUnique(event.getPlayer(), MissionType.OBTAIN_COLLECTION, extracted.name(),
                definition -> MissionTypeHandler.material(definition, extracted));
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();
        apply(player, MissionType.OBTAIN_ITEM, item.getAmount(),
                definition -> MissionTypeHandler.material(definition, item.getType()));
        applyUnique(player, MissionType.OBTAIN_COLLECTION, item.getType().name(),
                definition -> MissionTypeHandler.material(definition, item.getType()));
    }

    @EventHandler
    public void onItemBreak(PlayerItemBreakEvent event) {
        Material material = event.getBrokenItem().getType();
        apply(event.getPlayer(), MissionType.ITEM_BREAK, 1,
                definition -> MissionTypeHandler.material(definition, material));
    }

    @EventHandler
    public void onHarvest(PlayerHarvestBlockEvent event) {
        Material block = event.getHarvestedBlock().getType();
        if (block != Material.BEEHIVE && block != Material.BEE_NEST) return;
        int count = 0;
        for (ItemStack item : event.getItemsHarvested()) {
            if (item != null && item.getType() == Material.HONEY_BOTTLE) count += item.getAmount();
        }
        apply(event.getPlayer(), MissionType.COLLECT_HONEY_BOTTLE, Math.max(1, count), null);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player shooter)) return;

        if (event instanceof TargetHitEvent target
                && target.getHitBlock() != null
                && target.getHitBlock().getType() == Material.TARGET
                && target.getSignalStrength() >= 15) {
            apply(shooter, MissionType.TARGET_BULLSEYE, 1, null);
        }

        Entity hit = event.getHitEntity();
        if (hit != null) {
            apply(shooter, MissionType.HIT_ENTITY_PROJECTILE, 1, definition ->
                    MissionTypeHandler.projectile(definition, projectile.getType())
                            && MissionTypeHandler.entity(definition, hit.getType()));
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Creature creature)) return;
        if (creature.getType() != org.bukkit.entity.EntityType.IRON_GOLEM
                || event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.BUILD_IRONGOLEM) return;
        for (Player player : creature.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(creature.getLocation()) <= 64.0) {
                apply(player, MissionType.BUILD_IRON_GOLEM, 1, null);
            }
        }
    }

    @EventHandler
    public void onBed(PlayerBedEnterEvent event) {
        missions.resetType(event.getPlayer().getUniqueId(), MissionType.NO_SLEEP_DAYS);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inventory = event.getInventory();
        if (!inventory.getType().name().contains("SHULKER")) return;

        for (ActiveMission mission : missions.active(player.getUniqueId())) {
            if (!missions.isUnlocked(player.getUniqueId(), mission.slotKey())) continue;
            MissionDefinition definition = mission.definition();
            if (definition.type() != MissionType.FILL_SHULKER) continue;
            String itemName = definition.text("item", "");
            int needed = definition.number("slots", 27);
            int count = 0;
            for (ItemStack item : inventory.getContents()) {
                if (item != null && item.getType().name().equalsIgnoreCase(itemName)) count += item.getAmount();
            }
            if (count >= needed && !mission.completed()) {
                apply(player, MissionType.FILL_SHULKER, 1,
                        candidate -> candidate.id().equals(definition.id()));
            }
        }
    }
}
