package ru.cloudwars.cloudrewards.reward;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.coolcontainers.api.CoolContainersAPI;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Единственный класс, который ссылается на CoolContainers API. */
public final class CoolContainersHook implements KeyGrantGateway {

    private final JavaPlugin plugin;

    public CoolContainersHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean available() {
        if (Bukkit.getPluginManager().getPlugin("CoolContainers") == null) return false;
        try {
            return CoolContainersAPI.get() != null;
        } catch (LinkageError | RuntimeException ex) {
            // Установленный на сервере jar может быть старше исходников API.
            plugin.getLogger().warning("CoolContainers API недоступен: " + ex);
            return false;
        }
    }

    @Override
    public CompletableFuture<Result> giveKey(UUID playerId, String caseId, String sourcePlugin, UUID sourcePlayer) {
        CoolContainersAPI api;
        try {
            api = CoolContainersAPI.get();
        } catch (LinkageError | RuntimeException ex) {
            return CompletableFuture.completedFuture(Result.failed("PLUGIN_DISABLED"));
        }
        if (api == null) {
            return CompletableFuture.completedFuture(Result.failed("PLUGIN_DISABLED"));
        }
        if (!api.hasCase(caseId)) {
            return CompletableFuture.completedFuture(Result.failed("UNKNOWN_CASE"));
        }
        return api.giveKeys(playerId, caseId, 1, sourcePlugin, sourcePlayer)
                .handle((result, ex) -> {
                    if (ex != null) {
                        plugin.getLogger().warning("CoolContainers giveKeys exception: " + ex.getMessage());
                        return Result.failed("DB_ERROR");
                    }
                    if (result == null || !result.success()) {
                        String failure = result == null ? "NULL_RESULT" : String.valueOf(result.failure());
                        return Result.failed(failure);
                    }
                    return Result.ok(result.newAmount());
                });
    }
}
