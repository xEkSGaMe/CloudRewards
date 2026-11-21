package ru.cloudwars.cloudrewards.reward;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Изолированный контракт выдачи ключей. ExchangeService не знает о CoolContainers,
 * поэтому отсутствие плагина не приводит к NoClassDefFoundError.
 */
public interface KeyGrantGateway {

    boolean available();

    CompletableFuture<Result> giveKey(UUID playerId, String caseId, String sourcePlugin, UUID sourcePlayer);

    record Result(boolean success, String failure, int newAmount) {
        public static Result ok(int newAmount) {
            return new Result(true, null, newAmount);
        }

        public static Result failed(String failure) {
            return new Result(false, failure, 0);
        }
    }
}
