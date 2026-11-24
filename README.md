# CloudRewards

Плагин ежедневных миссий для Purpur/Paper 1.21.11 (Java 21, Maven).

## Сборка

Проект использует локальный репозиторий `C:\maven-repo` (указан в `.mvn/maven.config`), поэтому достаточно:

```powershell
mvn clean package
```

Если Maven не добавлен в PATH, можно использовать встроенный в IntelliJ IDEA:

```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\plugins\maven\lib\maven3\bin\mvn.cmd" -B clean package
```

Готовый jar: `target/CloudRewards-1.0.0.jar` (SQLite уже в jar'е, relocation `org.sqlite` → `ru.cloudwars.cloudrewards.libs.sqlite`).

## Первичная настройка окружения

CoolContainers API должен быть установлен в локальный репозиторий. **Важно:** использовать свежий jar со API-классами (в jar'е на сервере `C:\CloudWars 2.0\towny\plugins\CoolContainers-2.0.0.jar` классов `CoolContainersAPI`/`KeyGrantResult` нет — он устарел).

```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\plugins\maven\lib\maven3\bin\mvn.cmd" -B install:install-file `
  "-Dfile=C:/Users/общи/IdeaProjects/CoolContainers/target/CoolContainers-2.0.0.jar" `
  "-DgroupId=ru.coolcontainers" `
  "-DartifactId=coolcontainers" `
  "-Dversion=2.0.0" `
  "-Dpackaging=jar"
```

Кавычки вокруг `-D...` обязательны: PowerShell иначе разбивает аргументы с точками.

## Установка на сервер

1. Положите `CloudRewards-1.0.0.jar` в `plugins/`.
2. Выдайте право: `lp user <ник> permission set cloudrewards.command.missions true`.
3. При первом запуске создадутся `config.yml`, `messages.yml`, `missions.yml`, `cloudrewards.db`.
4. Проверьте `exchange.cases` в `config.yml` — указаны реальные ID кейсов CoolContainers:
   `TREASURE_SMALL` / `TREASURE_MEDIUM` / `TREASURE_LARGE` (из `plugins/CoolContainers/cases/`).
5. Режим обмена по умолчанию безопасный: `mode: COOLDOWNTAINERS`, `stub-refund-shards: true`
   (если CC недоступен — шарды не списываются; при `DB_ERROR` — возвращаются).

## Команды

| Команда | Право | Назначение |
|---|---|---|
| `/missions` | `cloudrewards.command.missions` | Главное меню |
| `/cloudrewards reload` | `cloudrewards.admin.reload` | Перезагрузка конфигов и таймера ресета |
| `/cloudrewards reset` | `cloudrewards.admin.reset` | Принудительная новая ротация |
| `/cloudrewards giveshard <player> [amount]` | `cloudrewards.admin.giveshard` | Выдача PDC-шардов |
| `/cloudrewards setprogress <player> <id\|slot> <value>` | `cloudrewards.admin.setprogress` | Установка прогресса |

## Особенности реализации

- Ротация 2 NORMAL + 1 HARD, исключение ID вчерашней ротации, ресет 13:00 `Europe/Moscow`. Порядок слотов жёстко закреплён: 1-я и 3-я — обычные, 2-я — всегда сложная (старые ротации исправляются при запуске).
- Миссии выполняются строго последовательно: `Обычная → Сложная → Обычная`. Следующая миссия скрыта, в её иконке нет подсказок и она не засчитывается, пока не выполнена предыдущая.
- Дистанционные миссии (лодка, вагонетка, элитры, плавание, ходьба) считаются по фактическому перемещению игрока и пишутся в прогресс раз в секунду — без «догоняющего» отставания от ванильной статистики.
- Обратный отсчёт до обновления в меню миссий обновляется раз в секунду без переоткрытия меню.
- Прогресс и флаги `claimed`/`pending` хранятся в SQLite и восстанавливаются после рестарта.
- Шард — физический `PRISMARINE_CRYSTALS` только с PDC-меткой `cloudrewards_shard`; выдача при нехватке места не производится, награда остаётся pending и забирается по клику/открытию меню/входу.
- CoolContainers изолирован в `reward/CoolContainersHook.java`; остальной код зависит только от интерфейса `KeyGrantGateway`. Callback API приходит с DB-потока — все обращения к Bukkit вынесены через `runTask`.
- Поддержаны все 30 типов миссий из `missions.yml`, включая сложные (`RUN_COMMAND`, `NO_SLEEP_DAYS`, `OBTAIN_COLLECTION`, `FILL_SHULKER`, `BUILD_IRON_GOLEM` и др.) с флагом `enabled`.
