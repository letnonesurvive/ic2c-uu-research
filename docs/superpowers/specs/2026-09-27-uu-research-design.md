# UU Research — дизайн v1

Дата: 2026-09-27
Статус: черновик на ревью
Рабочее название: UU Research, mod id `uuresearch` (финальное название выберем до публикации)

## 1. Цель

В IC2 Classic рецепты репликации из UU-материи намеренно скрыты из JEI («секреты»).
Единственный способ увидеть их — выключить опцию `enableSecretRecipeHiding`, которая
открывает сразу всё, включая остальные секреты IC2.

Мод превращает открытие UU-рецептов в игровую механику: игрок **изучает** рецепт
в машине-исследователе, и только после этого рецепт появляется в JEI.

Критерии успеха v1:
- в чистом мире все UU-рецепты скрыты в JEI (независимо от опции IC2);
- положил предмет в машину, подал энергию, дождался — рецепты этого предмета видны в JEI;
- знание сохраняется после перезахода в мир;
- работает в одиночной игре и на выделенном сервере.

Вне цели v1: запрет самого крафта, влияние на другие секреты IC2.

## 2. Окружение

- Minecraft 1.19.2, Forge 43.x (у пользователя 43.5.0), Java 17
- IC2 Classic 1.19.2-**2.1.3.4** (обязательная зависимость)
- JEI 11 (необязательная зависимость; у пользователя 11.68.0.1084)
- Тестовая сборка: Prism instance `~/Library/Application Support/PrismLauncher/instances/1.19.2`
- Каркас Gradle (ForgeGradle, parchment, cursemaven) берётся из `~/Projects/IC2C-UU-Matter`

## 3. Как IC2 устроен (факты из декомпиляции 2.1.3.4)

- UU-рецепты регистрируются через `UUMatterRegistry.registerUUShape(UUMatterBuilder)`;
  тот вызывает `AdvRecipeRegistry.addShapedRecipe(...)` с флагом `RecipeMods.HIDDEN_RECIPE`.
- Итог — `ShapedIC2Recipe extends RecipeIC2Base implements CraftingRecipe`,
  лежит в ванильном `RecipeManager` под `RecipeType.CRAFTING`, `isHidden() == true`.
  У билдера `hidden = true` по умолчанию, `setVisible()` снимает флаг.
- JEI-плагин IC2 (`ic2.jeiplugin.core.JEIPlugin.doJEIHiding`) в `onRuntimeAvailable`
  и при каждой перезагрузке конфига (`IC2.CONFIG.addLoadedListener`) вызывает
  `hideRecipes`/`unhideRecipes(RecipeTypes.CRAFTING, …)` для всех `RecipeIC2Base.isHidden()`,
  в зависимости от `IC2.CONFIG.recipeHiding` (`enableSecretRecipeHiding`).
- Стоимость в UU: `UUMatterRegistry.getEntries()` → `UUMatterEntry(stack, uuNeeded)`,
  `uuNeeded` в milli-UU (1000 = 1 UU-материя), уже поделено на количество на выходе.
- Образец машины: `CropAnalyzerTileEntity extends BaseMachineTileEntity`
  + `ContainerCropAnalyzer extends ContainerComponent` (слоты батарея/вход/выход/апгрейды,
  `ChargeBarComponent`, `ProgressComponent`). Блок регистрируется как `BaseMachineBlock`
  с `ITextureProvider`.

## 4. Поведение в игре

### Машина «UU-исследователь» (UU Research Station)
- Блок-машина, тир MV (32 EU/t), питается от кабелей IC2.
- Слоты: батарея, вход (образец), выход (образец), 4 слота апгрейдов (типы как у Crop Analyzer).
- Вход принимает только предмет, для которого есть **ещё не изученный** UU-рецепт.
- Прогресс: требуется `uuCost(item) / 1000 × euPerUU` EU; `uuCost` — минимальный
  `uuNeeded` среди записей реестра для этого предмета. Если записи нет — берётся
  `defaultCostUU` из конфига.
- По завершении:
  - изучаются **все** UU-рецепты, у которых выход — этот предмет (сравнение по `Item`, без NBT);
  - образец перемещается в выходной слот (не уничтожается);
  - звук + сообщение в чат всем игрокам: «Изучен рецепт репликации: <предмет>».
- Нет энергии — прогресс медленно откатывается (как у Crop Analyzer).

### Знание
- Общее на мир (не по игрокам). Хранится в `SavedData` оверворлда.
- Изучаемая единица — id предмета (`ResourceLocation`).

### JEI
- Мод сам управляет видимостью **только UU-рецептов**:
  неизученные скрыты, изученные показаны — независимо от `enableSecretRecipeHiding`.
- Прочие секреты IC2 (Nuke, ITNT и т.п.) продолжают подчиняться опции IC2.
- Крафт в верстаке не меняется: рецепты работают у всех, мод управляет только подсказками.

### Крафт машины (предварительно, подберём в игре)
```
A U A      A = ic2:advanced_circuit
C M C      U = ic2:uumatter
A C A      C = ic2:circuit
           M = ic2:advanced_machine_block
```

### Команда (уровень оператора 2)
`/uuresearch learn <item> | learnall | forget <item> | reset | list`

### Конфиг (серверный, `uuresearch-server.toml`)
- `euPerUU` — EU за 1 UU стоимости (по умолчанию 10000, подберём на тестах)
- `defaultCostUU` — стоимость для предметов без записи в реестре (по умолчанию 1)

## 5. Архитектура

| Компонент | Ответственность | Зависит от |
|---|---|---|
| `UURecipeIndex` | Из `RecipeManager` выбрать `RecipeIC2Base`, `isHidden()`, с UU-материей среди ингредиентов; карта `Item → List<CraftingRecipe>`; стоимость `Item → uuCost` из `UUMatterRegistry` | IC2, ванильный `RecipeManager` |
| `ResearchKnowledge` | `SavedData`: множество изученных id; `learn / isLearned / forget / reset / learnAll`; при изменении — `setDirty` и рассылка | `Network` |
| `Network` | `SimpleChannel`, пакет `KnowledgeSyncPacket(Set<ResourceLocation>)` сервер→клиент, полный список | — |
| `ClientKnowledge` | Кэш на клиенте; слушатели изменений | — |
| `JeiCompat` | `@JeiPlugin`; по событиям пересчитывает видимость UU-рецептов | JEI API, `UURecipeIndex`, `ClientKnowledge` |
| `ResearchStationBlockEntity` | Логика машины на `BaseMachineTileEntity` | IC2, `UURecipeIndex`, `ResearchKnowledge` |
| `ResearchStationContainer` / экран | GUI на `ContainerComponent` | IC2 |
| `ModConfig` | `ForgeConfigSpec` SERVER | — |
| `ResearchCommand` | Команды | `ResearchKnowledge`, `UURecipeIndex` |
| Регистрация | Блок, BlockItem, BlockEntityType, MenuType | Forge `DeferredRegister` |

### Поток данных
```
образец в слот → (сервер) тик машины, расход EU → прогресс готов
  → ResearchKnowledge.learn(item) → setDirty (сохранение с миром)
  → KnowledgeSyncPacket всем игрокам → ClientKnowledge.update
  → JeiCompat.refresh() → unhide изученных / hide неизученных
```

### Синхронизация
- Полный список отправляется: при входе игрока (`PlayerLoggedInEvent`), при каждом изменении.
- Список маленький (порядка 60 предметов) — дельты не нужны.

### Порядок с JEI-плагином IC2
IC2 скрывает/раскрывает все свои секреты в `onRuntimeAvailable` и в слушателе конфига.
`JeiCompat.refresh()` выполняется отложенно (`Minecraft.getInstance().tell(...)`) после:
- `onRuntimeAvailable` нашего плагина;
- `IC2.CONFIG.addLoadedListener` (регистрируем один раз);
- обновления `ClientKnowledge`.
Так наш результат всегда применяется последним. Сброс ссылки на runtime — в `onRuntimeUnavailable`.

## 6. Ошибки и крайние случаи
- Предмет без UU-рецепта или уже изученный — слот не принимает.
- Рецепт изучили, пока образец в машине (например, командой) — прогресс сбрасывается, образец уходит в выход.
- Выходной слот занят — машина ждёт (как Crop Analyzer).
- `/reload` датапаков меняет рецепты — индекс пересобирается при `RecipesUpdatedEvent` (клиент) и `AddReloadListenerEvent`/`ServerStartedEvent` (сервер).
- Знание содержит id предмета, которого больше нет (удалили мод) — игнорируется, не падаем.
- JEI не установлен — `JeiCompat` не загружается, остальное работает.

## 7. Риски (проверяются первыми в плане)
1. **Подключение IC2C 2.1.3.4 к сборке.** Найти file id на CurseForge для cursemaven;
   запасной вариант — локальный jar из сборки через `fg.deobf(files(...))`.
2. **Блок стороннего мода на системе текстур IC2** (`ITextureProvider`, `BaseMachineBlock`).
   Запасной вариант — свой блок с обычной Forge-моделью, логика `BlockEntity` остаётся на IC2.
3. **`createType()` у IC2-тайлов** ссылается на `IC2Tiles.*`; нужно убедиться, что свой
   `BlockEntityType` корректно подставляется.

## 8. Тестирование
- Сборка: `./gradlew build`, jar кладётся в `mods` тестовой сборки.
- Ручной чек-лист в игре (выполняет пользователь), с `enableSecretRecipeHiding=true` и `=false`:
  1. новый мир — UU-рецептов в JEI нет (напр. «Рецепт» у алмаза не показывает UU);
  2. Nuke/ITNT видимость соответствует опции IC2;
  3. изучение алмаза — рецепт появляется, сообщение в чате, алмаз в выходе;
  4. перезаход в мир — рецепт виден;
  5. `/uuresearch reset` — рецепты снова скрыты;
  6. машина без энергии не прогрессирует; неподходящий предмет не кладётся.
- Автотесты: `UURecipeIndex` и расчёт стоимости — чистая логика, по возможности через
  Forge GameTest; если окажется дорого — только ручной чек-лист (решим в плане).

## 9. Идеи на будущее (не в v1)
- Предметы-чертежи (лут в данжах / крафт), открывают случайный рецепт; гибрид чертёж + машина.
- Уничтожение образца при изучении (опция конфига).
- Расход UU-материи при изучении.
- «Жёсткий режим»: крафт UU-рецепта невозможен до изучения.
- Знание по игрокам / командам вместо общего на мир.
- Своя JEI-категория «Репликация UU» со стоимостью.
- Баланс времени: элитры (112 UU) изучаются ~29 мин при 32 EU/t — слишком долго; ограничить максимум
  или сделать шкалу нелинейной. Большинство предметов изучаются за секунды — возможно, поднять минимум.
- Вода и лава: IC2 регистрирует предметы-блоки `minecraft:water`/`minecraft:lava` (`FluidBlockItem`),
  рецепт работает, но в выживании такой предмет получить можно только из самого UU-рецепта — замкнутый
  круг для исследования. Решение: принимать ведро воды/лавы как образец для этих рецептов (ведро возвращается).
