# UU Research v1 — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Forge-мод для IC2 Classic 1.19.2: машина «UU-исследователь» открывает UU-рецепты в JEI по одному; знание общее на мир.

**Architecture:** Чистая логика (`ResearchCost`, `KnowledgeSet`) покрыта JUnit. Серверная часть: `UURecipeIndex` (поиск UU-рецептов в `RecipeManager`), `ResearchKnowledge` (`SavedData`), команда, машина на базовых классах IC2 (`BaseMachineTileEntity`, `BaseMachineBlock`, `ContainerComponent`). Клиент: пакет полной синхронизации → `ClientKnowledge` → JEI-плагин прячет/показывает UU-рецепты отложенно, после плагина IC2.

**Tech Stack:** Java 17, Forge 1.19.2-43.5.0, ForgeGradle 5.1 + parchment 2022.11.27, IC2 Classic 1.19.2-2.1.3.4 (локальный jar), JEI 11 API, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-27-uu-research-design.md`

## Global Constraints

- mod id: `uuresearch`; пакет: `com.letnonesurvive.uuresearch`; корень проекта: `~/Projects/ic2c-uu-research`
- Minecraft `1.19.2`, Forge `43.5.0` (в `mods.toml` диапазон `[43,)`), Java 17 (`JAVA_HOME=/opt/homebrew/opt/openjdk@17`)
- IC2 Classic **1.19.2-2.1.3.4** — обязательная зависимость (`modId="ic2"`); JEI — необязательная
- Знание — общее на мир, единица — id предмета (`ResourceLocation`)
- Мод управляет видимостью **только UU-рецептов** (`RecipeIC2Base.isHidden()` + UU-материя в ингредиентах), независимо от `enableSecretRecipeHiding`
- Крафт в верстаке не меняется
- Машина: MV, 32 EU/t, maxInput 128, слоты: 0 батарея, 1 вход, 2 выход, 3–6 апгрейды
- Стоимость: `milliUU × euPerUU / 1000`, `euPerUU` по умолчанию 10000, `defaultCostUU` по умолчанию 1
- Образец не уничтожается
- Комментарии и документация в коде — на английском
- Коммиты: только после проверки пользователем в игре (пользователь просил не коммитить до работающей реализации)
- Тестовая сборка: `~/Library/Application Support/PrismLauncher/instances/1.19.2/minecraft` (далее `$INST`)

## Review Focus

1. **Выделенный сервер:** клиентские классы (`Minecraft`, JEI) не должны загружаться на сервере. Пакет обрабатывается через `DistExecutor`, JEI-плагин грузит только JEI. Проверка — ревью кода Task 4 (запуск dedicated server вне v1).
2. **Смена мира в одной сессии:** знание мира A не должно «протечь» в мир B. `ClientKnowledge.clear()` на `LoggingOut`, проверка в Task 4, шаг 6.
3. **Перезагрузка конфига IC2** (`enableSecretRecipeHiding` true↔false в игре): наш результат должен применяться после IC2. Проверка в Task 4, шаг 6.
4. **Рецепт изучен командой, пока образец в машине:** машина выталкивает образец и не тратит энергию. Проверка в Task 5, шаг 9.
5. **Битые данные сохранения** (невалидный id, id удалённого мода): загрузка не падает. Тест `fromTagSkipsInvalidIds` в Task 2; неизвестный id просто не совпадает ни с одним рецептом.

---

## Карта файлов

```
~/Projects/ic2c-uu-research/
  build.gradle, settings.gradle, gradle.properties, gradlew, gradle/wrapper/*, .gitignore
  libs/IC2Classic-1.19.2-2.1.3.4.jar            (копия из сборки, в .gitignore)
  src/main/resources/META-INF/mods.toml
  src/main/resources/pack.mcmeta
  src/main/resources/assets/uuresearch/lang/en_us.json, ru_ru.json
  src/main/resources/data/uuresearch/recipes/research_station.json
  src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json
  src/main/java/com/letnonesurvive/uuresearch/
    UUResearch.java                 entry point, config + network + command registration
    UUResearchConfig.java           server config (euPerUU, defaultCostUU)
    research/ResearchCost.java      pure EU cost math
    research/KnowledgeSet.java      pure learned-set + NBT (de)serialization
    research/UURecipeIndex.java     finds IC2 UU recipes, UU cost lookup
    research/ResearchKnowledge.java world SavedData
    research/ResearchCommand.java   /uuresearch
    network/ModNetwork.java         SimpleChannel
    network/KnowledgeSyncPacket.java
    client/ClientKnowledge.java     client cache + listeners
    client/ClientEvents.java        clear on logout
    ServerEvents.java               sync on login
    compat/jei/UUResearchJeiPlugin.java
    machine/ModContent.java         block / item / block entity registration
    machine/ResearchStationBlockEntity.java
    machine/ResearchStationContainer.java
  src/test/java/com/letnonesurvive/uuresearch/research/
    ResearchCostTest.java, KnowledgeSetTest.java
```

---

### Task 1: Каркас проекта, который собирается и загружается в игре

**Files:**
- Create: `settings.gradle`, `gradle.properties`, `build.gradle`, `.gitignore`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat` (wrapper копируется из `~/Projects/IC2C-UU-Matter`)
- Create: `libs/IC2Classic-1.19.2-2.1.3.4.jar`
- Create: `src/main/resources/META-INF/mods.toml`, `src/main/resources/pack.mcmeta`
- Create: `src/main/java/com/letnonesurvive/uuresearch/UUResearch.java`

**Interfaces:**
- Produces: `UUResearch.MOD_ID = "uuresearch"`, `UUResearch.LOGGER`

- [ ] **Step 1: Скопировать wrapper и jar IC2, git init**

```bash
cd ~/Projects/ic2c-uu-research
cp -R ~/Projects/IC2C-UU-Matter/gradle ~/Projects/IC2C-UU-Matter/gradlew ~/Projects/IC2C-UU-Matter/gradlew.bat ~/Projects/IC2C-UU-Matter/settings.gradle .
chmod +x gradlew
mkdir -p libs
cp "$HOME/Library/Application Support/PrismLauncher/instances/1.19.2/minecraft/mods/IC2Classic-1.19.2-2.1.3.4.jar" libs/
git init
```

- [ ] **Step 2: `.gitignore`**

```
.gradle/
build/
run/
out/
.idea/
*.iml
libs/
```

- [ ] **Step 3: `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false
minecraft_version=1.19.2
forge_version=43.5.0
parchment_version=2022.11.27
ic2_version=2.1.3.4
jei_version=11.+
mod_version=0.1.0
```

- [ ] **Step 4: `build.gradle`**

```groovy
plugins {
    id 'net.minecraftforge.gradle' version '5.1.+'
    id 'org.parchmentmc.librarian.forgegradle' version '1.+'
}

version = "${minecraft_version}-${mod_version}"
group = 'com.letnonesurvive.uuresearch'
archivesBaseName = 'uuresearch'

java.toolchain.languageVersion = JavaLanguageVersion.of(17)

minecraft {
    mappings channel: 'parchment', version: "${parchment_version}-${minecraft_version}"
}

repositories {
    flatDir { dirs 'libs' }
    maven {
        name = "Progwml6 maven"
        url = "https://dvs1.progwml6.com/files/maven/"
        content { includeGroup "mezz.jei" }
    }
    maven {
        name = "ModMaven"
        url = "https://modmaven.dev/"
        content { includeGroup "mezz.jei" }
    }
}

dependencies {
    minecraft "net.minecraftforge:forge:${minecraft_version}-${forge_version}"
    implementation fg.deobf("local:IC2Classic-1.19.2:${ic2_version}")
    compileOnly fg.deobf("mezz.jei:jei-${minecraft_version}-common-api:${jei_version}")
    compileOnly fg.deobf("mezz.jei:jei-${minecraft_version}-forge-api:${jei_version}")
}

jar {
    manifest {
        attributes([
                "Specification-Title"   : "uuresearch",
                "Specification-Vendor"  : "letnonesurvive",
                "Specification-Version" : "1",
                "Implementation-Title"  : project.name,
                "Implementation-Version": project.jar.archiveVersion,
                "Implementation-Vendor" : "letnonesurvive"
        ])
    }
}

jar.finalizedBy('reobfJar')

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}
```

- [ ] **Step 5: `mods.toml`**

```toml
modLoader="javafml"
loaderVersion="[43,)"
license="MIT"
[[mods]]
modId="uuresearch"
version="${file.jarVersion}"
displayName="UU Research"
authors="letnonesurvive"
description='''
Research UU-Matter replication recipes of IC2 Classic in a machine to reveal them in JEI.
'''
[[dependencies.uuresearch]]
    modId="forge"
    mandatory=true
    versionRange="[43,)"
    ordering="NONE"
    side="BOTH"
[[dependencies.uuresearch]]
    modId="minecraft"
    mandatory=true
    versionRange="[1.19.2,1.19.3)"
    ordering="NONE"
    side="BOTH"
[[dependencies.uuresearch]]
    modId="ic2"
    mandatory=true
    versionRange="[1.19.2-2.1.0,)"
    ordering="AFTER"
    side="BOTH"
[[dependencies.uuresearch]]
    modId="jei"
    mandatory=false
    versionRange="[11,)"
    ordering="AFTER"
    side="BOTH"
```

- [ ] **Step 6: `pack.mcmeta`**

```json
{
    "pack": {
        "description": "UU Research resources",
        "pack_format": 9,
        "forge:resource_pack_format": 9,
        "forge:data_pack_format": 10
    }
}
```

- [ ] **Step 7: `UUResearch.java`**

```java
package com.letnonesurvive.uuresearch;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(UUResearch.MOD_ID)
public class UUResearch {

    public static final String MOD_ID = "uuresearch";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UUResearch() {
        LOGGER.info("UU Research loaded");
    }
}
```

- [ ] **Step 8: Собрать**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew build`
Expected: `BUILD SUCCESSFUL`, файл `build/libs/uuresearch-1.19.2-0.1.0.jar`.
Если `fg.deobf("local:…")` не резолвится — запасной путь: найти file id IC2C 2.1.3.4 на CurseForge и использовать `curse.maven:ic2_classic-242942:<id>` с репозиторием `https://www.cursemaven.com`.

- [ ] **Step 9: Проверка в игре (пользователь)**

```bash
cp build/libs/uuresearch-1.19.2-0.1.0.jar "$HOME/Library/Application Support/PrismLauncher/instances/1.19.2/minecraft/mods/"
```
Запустить сборку в Prism → главное меню → Mods: в списке есть «UU Research». Игра не падает.

---

### Task 2: Чистая логика — стоимость и множество знаний (JUnit)

**Files:**
- Modify: `build.gradle` (JUnit)
- Create: `src/main/java/com/letnonesurvive/uuresearch/research/ResearchCost.java`
- Create: `src/main/java/com/letnonesurvive/uuresearch/research/KnowledgeSet.java`
- Test: `src/test/java/com/letnonesurvive/uuresearch/research/ResearchCostTest.java`
- Test: `src/test/java/com/letnonesurvive/uuresearch/research/KnowledgeSetTest.java`

**Interfaces:**
- Produces:
  - `ResearchCost.resolveMilliUU(Integer registryMilliUU, int defaultCostUU) -> int`
  - `ResearchCost.totalEu(int milliUU, int euPerUU) -> int` (минимум 1, насыщение до `Integer.MAX_VALUE`)
  - `KnowledgeSet`: `learn(ResourceLocation) -> boolean`, `forget(ResourceLocation) -> boolean`, `isLearned(ResourceLocation) -> boolean`, `learnAll(Collection<ResourceLocation>) -> boolean`, `clear() -> boolean`, `replaceAll(Collection<ResourceLocation>)`, `view() -> Set<ResourceLocation>`, `toTag() -> ListTag`, `static fromTag(ListTag) -> KnowledgeSet`

- [ ] **Step 1: Подключить JUnit в `build.gradle`**

В блок `dependencies` добавить:
```groovy
    testImplementation 'org.junit.jupiter:junit-jupiter:5.9.1'
```
В конец файла:
```groovy
test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Написать падающие тесты**

`ResearchCostTest.java`:
```java
package com.letnonesurvive.uuresearch.research;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResearchCostTest {

    @Test
    void usesRegistryCostWhenPresent() {
        assertEquals(3500, ResearchCost.resolveMilliUU(3500, 1));
    }

    @Test
    void fallsBackToDefaultWhenMissingOrInvalid() {
        assertEquals(2000, ResearchCost.resolveMilliUU(null, 2));
        assertEquals(2000, ResearchCost.resolveMilliUU(0, 2));
        assertEquals(2000, ResearchCost.resolveMilliUU(-5, 2));
    }

    @Test
    void totalEuScalesWithCost() {
        assertEquals(10_000, ResearchCost.totalEu(1000, 10_000));
        assertEquals(35_000, ResearchCost.totalEu(3500, 10_000));
    }

    @Test
    void totalEuIsAtLeastOne() {
        assertEquals(1, ResearchCost.totalEu(1, 1));
    }

    @Test
    void totalEuSaturatesInsteadOfOverflowing() {
        assertEquals(Integer.MAX_VALUE, ResearchCost.totalEu(Integer.MAX_VALUE, 1_000_000));
    }
}
```

`KnowledgeSetTest.java`:
```java
package com.letnonesurvive.uuresearch.research;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeSetTest {

    private static final ResourceLocation DIAMOND = new ResourceLocation("minecraft", "diamond");
    private static final ResourceLocation IRON = new ResourceLocation("minecraft", "iron_ingot");

    @Test
    void learnReportsChangeOnlyOnce() {
        KnowledgeSet set = new KnowledgeSet();
        assertTrue(set.learn(DIAMOND));
        assertFalse(set.learn(DIAMOND));
        assertTrue(set.isLearned(DIAMOND));
    }

    @Test
    void forgetAndClear() {
        KnowledgeSet set = new KnowledgeSet();
        set.learnAll(List.of(DIAMOND, IRON));
        assertTrue(set.forget(DIAMOND));
        assertFalse(set.forget(DIAMOND));
        assertTrue(set.clear());
        assertFalse(set.clear());
        assertTrue(set.view().isEmpty());
    }

    @Test
    void replaceAllOverwrites() {
        KnowledgeSet set = new KnowledgeSet();
        set.learn(DIAMOND);
        set.replaceAll(List.of(IRON));
        assertEquals(Set.of(IRON), set.view());
    }

    @Test
    void roundTripsThroughTag() {
        KnowledgeSet set = new KnowledgeSet();
        set.learnAll(List.of(DIAMOND, IRON));
        assertEquals(set.view(), KnowledgeSet.fromTag(set.toTag()).view());
    }

    @Test
    void fromTagSkipsInvalidIds() {
        ListTag tag = new ListTag();
        tag.add(StringTag.valueOf("minecraft:diamond"));
        tag.add(StringTag.valueOf("Not A Valid:Id!"));
        assertEquals(Set.of(DIAMOND), KnowledgeSet.fromTag(tag).view());
    }

    @Test
    void viewIsReadOnly() {
        KnowledgeSet set = new KnowledgeSet();
        assertThrows(UnsupportedOperationException.class, () -> set.view().add(DIAMOND));
    }
}
```

- [ ] **Step 3: Убедиться, что тесты падают**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test`
Expected: FAIL — компиляция: `cannot find symbol ResearchCost` / `KnowledgeSet`.

- [ ] **Step 4: Реализация `ResearchCost.java`**

```java
package com.letnonesurvive.uuresearch.research;

/**
 * Energy cost math for researching an item. IC2 expresses UU cost in milli-UU (1000 = one UU-Matter).
 */
public final class ResearchCost {

    public static final int MILLI_UU_PER_UU = 1000;

    private ResearchCost() {
    }

    /** Returns the registry cost if it is positive, otherwise the configured default. */
    public static int resolveMilliUU(Integer registryMilliUU, int defaultCostUU) {
        if (registryMilliUU != null && registryMilliUU > 0) {
            return registryMilliUU;
        }
        return defaultCostUU * MILLI_UU_PER_UU;
    }

    /** Total EU to research an item; at least 1, saturated at {@link Integer#MAX_VALUE}. */
    public static int totalEu(int milliUU, int euPerUU) {
        long eu = (long) milliUU * euPerUU / MILLI_UU_PER_UU;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, eu));
    }
}
```

- [ ] **Step 5: Реализация `KnowledgeSet.java`**

```java
package com.letnonesurvive.uuresearch.research;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Set of researched item ids. Mutators return whether the set changed.
 */
public final class KnowledgeSet {

    private final Set<ResourceLocation> learned = new LinkedHashSet<>();

    public boolean learn(ResourceLocation id) {
        return learned.add(id);
    }

    public boolean forget(ResourceLocation id) {
        return learned.remove(id);
    }

    public boolean isLearned(ResourceLocation id) {
        return learned.contains(id);
    }

    public boolean learnAll(Collection<ResourceLocation> ids) {
        return learned.addAll(ids);
    }

    public boolean clear() {
        boolean changed = !learned.isEmpty();
        learned.clear();
        return changed;
    }

    public void replaceAll(Collection<ResourceLocation> ids) {
        learned.clear();
        learned.addAll(ids);
    }

    public Set<ResourceLocation> view() {
        return Collections.unmodifiableSet(learned);
    }

    public ListTag toTag() {
        ListTag tag = new ListTag();
        for (ResourceLocation id : learned) {
            tag.add(StringTag.valueOf(id.toString()));
        }
        return tag;
    }

    /** Invalid ids are skipped so a damaged save never prevents the world from loading. */
    public static KnowledgeSet fromTag(ListTag tag) {
        KnowledgeSet set = new KnowledgeSet();
        for (int i = 0; i < tag.size(); i++) {
            if (tag.get(i).getId() != Tag.TAG_STRING) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(i));
            if (id != null) {
                set.learned.add(id);
            }
        }
        return set;
    }
}
```

- [ ] **Step 6: Тесты зелёные**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test`
Expected: `BUILD SUCCESSFUL`, 11 тестов пройдено.

---

### Task 3: Индекс UU-рецептов, знание мира, команда

**Files:**
- Create: `src/main/java/com/letnonesurvive/uuresearch/research/UURecipeIndex.java`
- Create: `src/main/java/com/letnonesurvive/uuresearch/research/ResearchKnowledge.java`
- Create: `src/main/java/com/letnonesurvive/uuresearch/research/ResearchCommand.java`
- Create: `src/main/java/com/letnonesurvive/uuresearch/UUResearchConfig.java`
- Create: `src/main/resources/assets/uuresearch/lang/en_us.json`, `ru_ru.json`
- Modify: `src/main/java/com/letnonesurvive/uuresearch/UUResearch.java`

**Interfaces:**
- Consumes: `KnowledgeSet`, `ResearchCost` (Task 2)
- Produces:
  - `UURecipeIndex.isUURecipe(CraftingRecipe) -> boolean`
  - `UURecipeIndex.all(RecipeManager) -> List<CraftingRecipe>`
  - `UURecipeIndex.outputId(CraftingRecipe) -> ResourceLocation`
  - `UURecipeIndex.outputIds(RecipeManager) -> Set<ResourceLocation>`
  - `UURecipeIndex.hasUURecipe(RecipeManager, Item) -> boolean`
  - `UURecipeIndex.milliUUCost(Item, int defaultCostUU) -> int` (только на сервере)
  - `ResearchKnowledge.get(MinecraftServer) -> ResearchKnowledge`; методы `isLearned(ResourceLocation)`, `learned() -> Set<ResourceLocation>`, `learn(ResourceLocation)`, `forget(ResourceLocation)`, `learnAll(Collection<ResourceLocation>)`, `reset()` — все мутаторы `-> boolean`
  - `UUResearchConfig.SPEC`, `UUResearchConfig.EU_PER_UU`, `UUResearchConfig.DEFAULT_COST_UU` (`ForgeConfigSpec.IntValue`)

- [ ] **Step 1: `UURecipeIndex.java`**

```java
package com.letnonesurvive.uuresearch.research;

import ic2.api.recipes.registries.IUUMatterRegistry;
import ic2.core.IC2;
import ic2.core.platform.recipes.crafting.RecipeIC2Base;
import ic2.core.platform.registries.IC2Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Locates IC2 Classic UU-Matter replication recipes: hidden IC2 crafting recipes that use UU-Matter.
 * Not cached on purpose: the recipe set is small and changes on /reload.
 */
public final class UURecipeIndex {

    private UURecipeIndex() {
    }

    public static boolean isUURecipe(CraftingRecipe recipe) {
        if (!(recipe instanceof RecipeIC2Base base) || !base.isHidden()) {
            return false;
        }
        ItemStack uuMatter = new ItemStack(IC2Items.UUMATTER);
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty() && ingredient.test(uuMatter)) {
                return true;
            }
        }
        return false;
    }

    public static List<CraftingRecipe> all(RecipeManager manager) {
        return manager.getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(UURecipeIndex::isUURecipe)
                .toList();
    }

    public static ResourceLocation outputId(CraftingRecipe recipe) {
        return ForgeRegistries.ITEMS.getKey(recipe.getResultItem().getItem());
    }

    public static Set<ResourceLocation> outputIds(RecipeManager manager) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (CraftingRecipe recipe : all(manager)) {
            ids.add(outputId(recipe));
        }
        return ids;
    }

    public static boolean hasUURecipe(RecipeManager manager, Item item) {
        return manager.getAllRecipesFor(RecipeType.CRAFTING).stream()
                .anyMatch(recipe -> recipe.getResultItem().getItem() == item && isUURecipe(recipe));
    }

    /** Cheapest registered UU cost of the item in milli-UU. Server side only. */
    public static int milliUUCost(Item item, int defaultCostUU) {
        Integer cheapest = null;
        for (IUUMatterRegistry.UUMatterEntry entry : IC2.RECIPES.get(true).UU.getEntries()) {
            if (entry.getStack().getItem() == item) {
                cheapest = cheapest == null ? entry.getUUNeeded() : Math.min(cheapest, entry.getUUNeeded());
            }
        }
        return ResearchCost.resolveMilliUU(cheapest, defaultCostUU);
    }
}
```

- [ ] **Step 2: `ResearchKnowledge.java`**

```java
package com.letnonesurvive.uuresearch.research;

import com.letnonesurvive.uuresearch.UUResearch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Set;

/**
 * World-wide research knowledge, stored with the overworld data.
 */
public final class ResearchKnowledge extends SavedData {

    private static final String NAME = UUResearch.MOD_ID + "_knowledge";
    private static final String TAG_LEARNED = "learned";

    private final KnowledgeSet set = new KnowledgeSet();

    public static ResearchKnowledge get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(ResearchKnowledge::load, ResearchKnowledge::new, NAME);
    }

    private static ResearchKnowledge load(CompoundTag tag) {
        ResearchKnowledge knowledge = new ResearchKnowledge();
        knowledge.set.replaceAll(KnowledgeSet.fromTag(tag.getList(TAG_LEARNED, Tag.TAG_STRING)).view());
        return knowledge;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put(TAG_LEARNED, set.toTag());
        return tag;
    }

    public boolean isLearned(ResourceLocation id) {
        return set.isLearned(id);
    }

    public Set<ResourceLocation> learned() {
        return set.view();
    }

    public boolean learn(ResourceLocation id) {
        return changed(set.learn(id));
    }

    public boolean forget(ResourceLocation id) {
        return changed(set.forget(id));
    }

    public boolean learnAll(Collection<ResourceLocation> ids) {
        return changed(set.learnAll(ids));
    }

    public boolean reset() {
        return changed(set.clear());
    }

    private boolean changed(boolean changed) {
        if (changed) {
            setDirty();
        }
        return changed;
    }
}
```

- [ ] **Step 3: `UUResearchConfig.java`**

```java
package com.letnonesurvive.uuresearch;

import net.minecraftforge.common.ForgeConfigSpec;

public final class UUResearchConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue EU_PER_UU;
    public static final ForgeConfigSpec.IntValue DEFAULT_COST_UU;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        EU_PER_UU = builder
                .comment("EU needed per 1 UU-Matter of an item's replication cost")
                .defineInRange("euPerUU", 10_000, 1, 1_000_000);
        DEFAULT_COST_UU = builder
                .comment("Replication cost in UU-Matter used when IC2 has no cost entry for the item")
                .defineInRange("defaultCostUU", 1, 1, 1_000);
        SPEC = builder.build();
    }

    private UUResearchConfig() {
    }
}
```

- [ ] **Step 4: `ResearchCommand.java`**

```java
package com.letnonesurvive.uuresearch.research;

import com.letnonesurvive.uuresearch.UUResearch;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * /uuresearch learn|forget &lt;item&gt;, learnall, reset, list. Requires permission level 2.
 */
public final class ResearchCommand {

    private ResearchCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal(UUResearch.MOD_ID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("learn")
                        .then(Commands.argument("item", ItemArgument.item(context))
                                .executes(ctx -> learn(ctx, item(ctx)))))
                .then(Commands.literal("forget")
                        .then(Commands.argument("item", ItemArgument.item(context))
                                .executes(ctx -> forget(ctx, item(ctx)))))
                .then(Commands.literal("learnall").executes(ResearchCommand::learnAll))
                .then(Commands.literal("reset").executes(ResearchCommand::reset))
                .then(Commands.literal("list").executes(ResearchCommand::list)));
    }

    private static Item item(CommandContext<CommandSourceStack> ctx) {
        return ItemArgument.getItem(ctx, "item").getItem();
    }

    private static int learn(CommandContext<CommandSourceStack> ctx, Item item) {
        MinecraftServer server = ctx.getSource().getServer();
        if (!UURecipeIndex.hasUURecipe(server.getRecipeManager(), item)) {
            ctx.getSource().sendFailure(Component.translatable("commands.uuresearch.no_recipe", item.getDescription()));
            return 0;
        }
        boolean changed = ResearchKnowledge.get(server).learn(ForgeRegistries.ITEMS.getKey(item));
        ctx.getSource().sendSuccess(Component.translatable(
                changed ? "commands.uuresearch.learn.success" : "commands.uuresearch.learn.already",
                item.getDescription()), true);
        return changed ? 1 : 0;
    }

    private static int forget(CommandContext<CommandSourceStack> ctx, Item item) {
        boolean changed = ResearchKnowledge.get(ctx.getSource().getServer()).forget(ForgeRegistries.ITEMS.getKey(item));
        ctx.getSource().sendSuccess(Component.translatable(
                changed ? "commands.uuresearch.forget.success" : "commands.uuresearch.forget.not_learned",
                item.getDescription()), true);
        return changed ? 1 : 0;
    }

    private static int learnAll(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        Set<ResourceLocation> all = UURecipeIndex.outputIds(server.getRecipeManager());
        ResearchKnowledge.get(server).learnAll(all);
        ctx.getSource().sendSuccess(Component.translatable("commands.uuresearch.learnall.success", all.size()), true);
        return all.size();
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        ResearchKnowledge.get(ctx.getSource().getServer()).reset();
        ctx.getSource().sendSuccess(Component.translatable("commands.uuresearch.reset.success"), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        Set<ResourceLocation> learned = ResearchKnowledge.get(server).learned();
        int total = UURecipeIndex.outputIds(server.getRecipeManager()).size();
        String names = learned.stream().map(ResourceLocation::toString).collect(Collectors.joining(", "));
        ctx.getSource().sendSuccess(Component.translatable("commands.uuresearch.list", learned.size(), total, names), false);
        return learned.size();
    }
}
```

- [ ] **Step 5: Регистрация в `UUResearch.java`**

Заменить содержимое на:
```java
package com.letnonesurvive.uuresearch;

import com.letnonesurvive.uuresearch.research.ResearchCommand;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(UUResearch.MOD_ID)
public class UUResearch {

    public static final String MOD_ID = "uuresearch";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UUResearch() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, UUResearchConfig.SPEC);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ResearchCommand.register(event.getDispatcher(), event.getBuildContext());
    }
}
```

- [ ] **Step 6: Локализация**

`en_us.json`:
```json
{
  "commands.uuresearch.no_recipe": "%s has no hidden UU-Matter recipe",
  "commands.uuresearch.learn.success": "Researched: %s",
  "commands.uuresearch.learn.already": "Already researched: %s",
  "commands.uuresearch.forget.success": "Forgot: %s",
  "commands.uuresearch.forget.not_learned": "Not researched: %s",
  "commands.uuresearch.learnall.success": "Researched all %s UU-Matter items",
  "commands.uuresearch.reset.success": "Research knowledge reset",
  "commands.uuresearch.list": "Researched %s of %s: %s"
}
```
`ru_ru.json`:
```json
{
  "commands.uuresearch.no_recipe": "У предмета %s нет скрытого UU-рецепта",
  "commands.uuresearch.learn.success": "Изучено: %s",
  "commands.uuresearch.learn.already": "Уже изучено: %s",
  "commands.uuresearch.forget.success": "Забыто: %s",
  "commands.uuresearch.forget.not_learned": "Не изучено: %s",
  "commands.uuresearch.learnall.success": "Изучены все UU-предметы: %s",
  "commands.uuresearch.reset.success": "Знания сброшены",
  "commands.uuresearch.list": "Изучено %s из %s: %s"
}
```

- [ ] **Step 7: Сборка и тесты**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew build`
Expected: `BUILD SUCCESSFUL` (включая 11 тестов из Task 2).

- [ ] **Step 8: Проверка в игре (пользователь)**

Скопировать jar в `$INST/mods` (как в Task 1, шаг 9), открыть мир с читами:
1. `/uuresearch list` → «Изучено 0 из N», N > 0 (ожидается порядка 50–60).
2. `/uuresearch learn minecraft:diamond` → «Изучено: Алмаз». Если «нет UU-рецепта» — взять любой предмет, который `learnall` подтвердит.
3. `/uuresearch learn ic2:uumatter` → «нет скрытого UU-рецепта».
4. Выйти из мира и зайти снова → `/uuresearch list` показывает `minecraft:diamond`.
5. `/uuresearch reset` → `list` снова 0.

JEI на этом шаге ещё не меняется — это нормально.

---

### Task 4: Синхронизация с клиентом и JEI

**Files:**
- Create: `src/main/java/com/letnonesurvive/uuresearch/network/ModNetwork.java`
- Create: `src/main/java/com/letnonesurvive/uuresearch/network/KnowledgeSyncPacket.java`
- Create: `src/main/java/com/letnonesurvive/uuresearch/client/ClientKnowledge.java`
- Create: `src/main/java/com/letnonesurvive/uuresearch/client/ClientEvents.java`
- Create: `src/main/java/com/letnonesurvive/uuresearch/ServerEvents.java`
- Create: `src/main/java/com/letnonesurvive/uuresearch/compat/jei/UUResearchJeiPlugin.java`
- Modify: `src/main/java/com/letnonesurvive/uuresearch/research/ResearchKnowledge.java` (метод `changed`)
- Modify: `src/main/java/com/letnonesurvive/uuresearch/UUResearch.java` (регистрация сети)

**Interfaces:**
- Consumes: `ResearchKnowledge`, `UURecipeIndex.all`, `UURecipeIndex.outputId`, `KnowledgeSet` (Task 2–3)
- Produces:
  - `ModNetwork.register()`, `ModNetwork.sendTo(ServerPlayer, Set<ResourceLocation>)`, `ModNetwork.sendToAll(Set<ResourceLocation>)`
  - `ClientKnowledge.set(Collection<ResourceLocation>)`, `ClientKnowledge.clear()`, `ClientKnowledge.isLearned(ResourceLocation) -> boolean`, `ClientKnowledge.addListener(Runnable)`

- [ ] **Step 1: `KnowledgeSyncPacket.java`**

```java
package com.letnonesurvive.uuresearch.network;

import com.letnonesurvive.uuresearch.client.ClientKnowledge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Server to client: the complete set of researched item ids.
 */
public record KnowledgeSyncPacket(Set<ResourceLocation> learned) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(learned, FriendlyByteBuf::writeResourceLocation);
    }

    public static KnowledgeSyncPacket decode(FriendlyByteBuf buf) {
        return new KnowledgeSyncPacket(buf.readCollection(HashSet::new, FriendlyByteBuf::readResourceLocation));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientKnowledge.set(learned));
    }
}
```

- [ ] **Step 2: `ModNetwork.java`**

```java
package com.letnonesurvive.uuresearch.network;

import com.letnonesurvive.uuresearch.UUResearch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashSet;
import java.util.Set;

public final class ModNetwork {

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(UUResearch.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(KnowledgeSyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(KnowledgeSyncPacket::encode)
                .decoder(KnowledgeSyncPacket::decode)
                .consumerMainThread(KnowledgeSyncPacket::handle)
                .add();
    }

    public static void sendTo(ServerPlayer player, Set<ResourceLocation> learned) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new KnowledgeSyncPacket(new HashSet<>(learned)));
    }

    public static void sendToAll(Set<ResourceLocation> learned) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new KnowledgeSyncPacket(new HashSet<>(learned)));
    }
}
```

- [ ] **Step 3: `ClientKnowledge.java` и `ClientEvents.java`**

```java
package com.letnonesurvive.uuresearch.client;

import com.letnonesurvive.uuresearch.research.KnowledgeSet;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client-side copy of the world knowledge received from the server.
 */
public final class ClientKnowledge {

    private static final KnowledgeSet SET = new KnowledgeSet();
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private ClientKnowledge() {
    }

    public static void set(Collection<ResourceLocation> learned) {
        SET.replaceAll(learned);
        LISTENERS.forEach(Runnable::run);
    }

    public static void clear() {
        set(List.of());
    }

    public static boolean isLearned(ResourceLocation id) {
        return SET.isLearned(id);
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }
}
```

```java
package com.letnonesurvive.uuresearch.client;

import com.letnonesurvive.uuresearch.UUResearch;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = UUResearch.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {
    }

    // Prevents knowledge of one world from leaking into the next one in the same session
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientKnowledge.clear();
    }
}
```

- [ ] **Step 4: `ServerEvents.java` и рассылка при изменении**

```java
package com.letnonesurvive.uuresearch;

import com.letnonesurvive.uuresearch.network.ModNetwork;
import com.letnonesurvive.uuresearch.research.ResearchKnowledge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = UUResearch.MOD_ID)
public final class ServerEvents {

    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModNetwork.sendTo(player, ResearchKnowledge.get(player.server).learned());
        }
    }
}
```

В `ResearchKnowledge.java` заменить метод `changed`:
```java
    private boolean changed(boolean changed) {
        if (changed) {
            setDirty();
            ModNetwork.sendToAll(set.view());
        }
        return changed;
    }
```
и добавить импорт `import com.letnonesurvive.uuresearch.network.ModNetwork;`.

В конструкторе `UUResearch` первой строкой добавить `ModNetwork.register();` и импорт `import com.letnonesurvive.uuresearch.network.ModNetwork;`.

- [ ] **Step 5: `UUResearchJeiPlugin.java`**

```java
package com.letnonesurvive.uuresearch.compat.jei;

import com.letnonesurvive.uuresearch.UUResearch;
import com.letnonesurvive.uuresearch.client.ClientKnowledge;
import com.letnonesurvive.uuresearch.research.UURecipeIndex;
import ic2.core.IC2;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows researched UU-Matter recipes and hides the rest. Runs deferred so it always applies after
 * IC2's own plugin, which toggles every secret recipe on start and on each config reload.
 */
@JeiPlugin
public class UUResearchJeiPlugin implements IModPlugin {

    private static IJeiRuntime runtime;
    private static boolean listenersRegistered;

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return new ResourceLocation(UUResearch.MOD_ID, "jei");
    }

    @Override
    public void onRuntimeAvailable(@NotNull IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        if (!listenersRegistered) {
            listenersRegistered = true;
            ClientKnowledge.addListener(UUResearchJeiPlugin::scheduleRefresh);
            IC2.CONFIG.addLoadedListener(UUResearchJeiPlugin::scheduleRefresh);
        }
        scheduleRefresh();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    private static void scheduleRefresh() {
        Minecraft.getInstance().tell(UUResearchJeiPlugin::refresh);
    }

    private static void refresh() {
        IJeiRuntime current = runtime;
        LocalPlayer player = Minecraft.getInstance().player;
        if (current == null || player == null) {
            return;
        }
        List<CraftingRecipe> learned = new ArrayList<>();
        List<CraftingRecipe> unknown = new ArrayList<>();
        for (CraftingRecipe recipe : UURecipeIndex.all(player.connection.getRecipeManager())) {
            (ClientKnowledge.isLearned(UURecipeIndex.outputId(recipe)) ? learned : unknown).add(recipe);
        }
        IRecipeManager manager = current.getRecipeManager();
        manager.hideRecipes(RecipeTypes.CRAFTING, unknown);
        manager.unhideRecipes(RecipeTypes.CRAFTING, learned);
    }
}
```

- [ ] **Step 6: Сборка и проверка в игре (пользователь)**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew build` → `BUILD SUCCESSFUL`; скопировать jar в `$INST/mods`.

В игре, с `enableSecretRecipeHiding=false` (как сейчас у пользователя):
1. Новый мир с читами: у UU-материи в JEI «Использование» (U) нет крафтов верстака с UU. У Nuke / ITNT рецепты **видны** (опция IC2 = false).
2. `/uuresearch learn minecraft:diamond` → у алмаза в JEI «Рецепт» (R) сразу появился UU-крафт.
3. `/uuresearch reset` → UU-крафт алмаза пропал.
4. `learn` алмаза → выйти в меню → зайти в **другой** мир → UU-крафта алмаза нет. Вернуться в первый → есть.
5. Mods → IC2 Classic → Config → включить `enableSecretRecipeHiding` → Nuke/ITNT скрылись, UU-крафт алмаза остался. Выключить → Nuke/ITNT вернулись, неизученные UU-рецепты не появились.

- [ ] **Step 7: Коммит (после подтверждения пользователя)**

```bash
git add -A
git commit -m "feat: world research knowledge, sync and JEI visibility of UU recipes"
```

---

### Task 5: Машина «UU-исследователь»

**Files:**
- Create: `src/main/java/com/letnonesurvive/uuresearch/machine/ModContent.java`
- Create: `src/main/java/com/letnonesurvive/uuresearch/machine/ResearchStationBlockEntity.java`
- Create: `src/main/java/com/letnonesurvive/uuresearch/machine/ResearchStationContainer.java`
- Create: `src/main/resources/data/uuresearch/recipes/research_station.json`
- Create: `src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json`
- Modify: `src/main/resources/assets/uuresearch/lang/en_us.json`, `ru_ru.json`

**Interfaces:**
- Consumes: `ResearchKnowledge.get/isLearned/learn`, `UURecipeIndex.hasUURecipe/milliUUCost`, `ResearchCost.totalEu`, `ClientKnowledge.isLearned`, `UUResearchConfig.EU_PER_UU/DEFAULT_COST_UU`
- Produces: `ModContent.RESEARCH_STATION` (`BaseMachineBlock`), `ModContent.RESEARCH_STATION_TYPE` (`IC2TileType<ResearchStationBlockEntity>`), `ModContent.RESEARCH_STATION_ID`

Текстуры блока и GUI в v1 — временно берём у Crop Analyzer из IC2 (`machine/lv/crop_analyzer`, `gui_crop_analyzer.png`), ничего не копируя. Свои текстуры — до публикации.

- [ ] **Step 1: `ModContent.java`**

Тип block entity создаётся до блока, потому что конструктор `BaseMachineBlock` требует его, а Forge регистрирует блоки раньше block entity types. `IC2TileType` валиден для любого состояния, поэтому ему не нужен список блоков.

```java
package com.letnonesurvive.uuresearch.machine;

import com.letnonesurvive.uuresearch.UUResearch;
import ic2.core.block.base.IC2TileType;
import ic2.core.block.base.drops.IBlockDropProvider;
import ic2.core.block.machines.BaseMachineBlock;
import ic2.core.platform.rendering.features.ITextureProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid = UUResearch.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModContent {

    public static final ResourceLocation RESEARCH_STATION_ID = new ResourceLocation(UUResearch.MOD_ID, "research_station");

    public static IC2TileType<ResearchStationBlockEntity> RESEARCH_STATION_TYPE;
    public static BaseMachineBlock RESEARCH_STATION;

    private ModContent() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            RESEARCH_STATION_TYPE = new IC2TileType<>(ResearchStationBlockEntity::new);
            RESEARCH_STATION = new BaseMachineBlock(RESEARCH_STATION_ID.toString(), IBlockDropProvider.SELF_OR_MACHINE,
                    ITextureProvider.toggleIC2("machine/lv/crop_analyzer"), RESEARCH_STATION_TYPE);
            helper.register(RESEARCH_STATION_ID, RESEARCH_STATION);
        });
        event.register(ForgeRegistries.Keys.ITEMS,
                helper -> helper.register(RESEARCH_STATION_ID, RESEARCH_STATION.createItem()));
        event.register(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES,
                helper -> helper.register(RESEARCH_STATION_ID, RESEARCH_STATION_TYPE));
    }
}
```

- [ ] **Step 2: `ResearchStationBlockEntity.java`**

Логика по образцу `ic2.core.block.machines.tiles.mv.CropAnalyzerTileEntity` (2.1.3.4).

```java
package com.letnonesurvive.uuresearch.machine;

import com.letnonesurvive.uuresearch.UUResearchConfig;
import com.letnonesurvive.uuresearch.client.ClientKnowledge;
import com.letnonesurvive.uuresearch.research.ResearchCost;
import com.letnonesurvive.uuresearch.research.ResearchKnowledge;
import com.letnonesurvive.uuresearch.research.UURecipeIndex;
import ic2.api.items.IUpgradeItem.UpgradeType;
import ic2.api.network.buffer.NetworkInfo;
import ic2.api.util.DirectionList;
import ic2.core.block.base.features.ITickListener;
import ic2.core.block.base.tiles.impls.machine.single.BaseMachineTileEntity;
import ic2.core.inventory.base.IHasInventory;
import ic2.core.inventory.base.ITileGui;
import ic2.core.inventory.container.IC2Container;
import ic2.core.inventory.filter.SpecialFilters;
import ic2.core.inventory.filter.special.ElectricItemFilter;
import ic2.core.inventory.filter.special.MachineFilter;
import ic2.core.inventory.handler.AccessRule;
import ic2.core.inventory.handler.InventoryHandler;
import ic2.core.inventory.handler.SlotType;
import ic2.core.inventory.inv.RangedInventory;
import ic2.core.utils.helpers.NBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumSet;

/**
 * MV machine that researches the UU-Matter recipes of the sample item. The sample is returned.
 */
public class ResearchStationBlockEntity extends BaseMachineTileEntity implements ITickListener, ITileGui {

    public static final EnumSet<UpgradeType> UPGRADES = EnumSet.of(
            UpgradeType.TRANSPORT_MOD, UpgradeType.CUSTOM_MOD, UpgradeType.MACHINE_MOD, UpgradeType.PROCESSING_MOD);

    static final int SLOT_BATTERY = 0;
    static final int SLOT_INPUT = 1;
    static final int SLOT_OUTPUT = 2;

    @NetworkInfo
    public int progress = 0;
    @NetworkInfo
    public int maxProgress = 0;

    public ResearchStationBlockEntity(BlockPos pos, BlockState state) {
        // 3 slots, 4 upgrade slots, 32 EU/t, unused operation length, 10k EU buffer, MV input (128)
        super(pos, state, 3, 4, 32, 1000, 10_000, 128);
        this.setFuelSlot(SLOT_BATTERY);
        this.addGuiFields("progress", "maxProgress");
    }

    @Override
    protected void addSlotInfo(InventoryHandler handler) {
        handler.registerBlockSides(DirectionList.ALL);
        handler.registerBlockAccess(DirectionList.ALL, AccessRule.BOTH);
        handler.registerSlotAccess(AccessRule.BOTH, SLOT_BATTERY);
        handler.registerSlotAccess(AccessRule.IMPORT, SLOT_INPUT);
        handler.registerSlotAccess(AccessRule.EXPORT, SLOT_OUTPUT);
        handler.registerSlotsForSide(DirectionList.DOWN, SLOT_BATTERY);
        handler.registerSlotsForSide(DirectionList.DOWN.invert(), SLOT_INPUT);
        handler.registerSlotsForSide(DirectionList.UP.invert(), SLOT_OUTPUT);
        handler.registerInputFilter(SpecialFilters.createChargeFilter(), SLOT_BATTERY);
        handler.registerOutputFilter(ElectricItemFilter.NOT_DISCHARGE_FILTER, SLOT_BATTERY);
        handler.registerInputFilter(new MachineFilter(this), SLOT_INPUT);
        handler.registerNamedSlot(SlotType.BATTERY, SLOT_BATTERY);
        handler.registerNamedSlot(SlotType.INPUT, SLOT_INPUT);
        handler.registerNamedSlot(SlotType.OUTPUT, SLOT_OUTPUT);
    }

    @Override
    public void load(CompoundTag compound) {
        super.load(compound);
        this.progress = NBTUtils.getInt(compound, "progress", 0);
    }

    @Override
    protected void saveAdditional(CompoundTag compound) {
        super.saveAdditional(compound);
        NBTUtils.putInt(compound, "progress", this.progress, 0);
    }

    @Override
    public IC2Container createContainer(Player player, InteractionHand hand, Direction side, int windowID) {
        return new ResearchStationContainer(this, player, windowID);
    }

    @Override
    public BlockEntityType<?> createType() {
        return ModContent.RESEARCH_STATION_TYPE;
    }

    @Override
    protected void createInvCaches() {
        this.inOut = new IHasInventory[2];
        this.inOut[0] = new RangedInventory(this, SLOT_INPUT);
        this.inOut[1] = new RangedInventory(this, SLOT_OUTPUT).setOutputOnly();
    }

    @Override
    public float getProgress() {
        return this.progress;
    }

    @Override
    public float getMaxProgress() {
        return this.maxProgress;
    }

    @Override
    public int getValidRoom(ItemStack stack) {
        return this.inventory.get(SLOT_INPUT).isEmpty() && isResearchable(stack.getItem()) ? stack.getMaxStackSize() : 0;
    }

    @Override
    public EnumSet<UpgradeType> getSupportedUpgradeTypes() {
        return UPGRADES;
    }

    @Override
    protected void handleMods() {
    }

    @Override
    public void onTick() {
        this.handleChargeSlot(this.maxEnergy);
        ItemStack sample = this.inventory.get(SLOT_INPUT);
        MinecraftServer server = this.level == null ? null : this.level.getServer();
        if (server == null || sample.isEmpty() || !this.inventory.get(SLOT_OUTPUT).isEmpty()) {
            this.setActive(false);
            this.setProgress(0);
            this.storage.onTick(this.inventory, this);
            return;
        }

        Item item = sample.getItem();
        ResearchKnowledge knowledge = ResearchKnowledge.get(server);
        // Also covers a recipe learned by command while the sample was waiting
        if (knowledge.isLearned(ForgeRegistries.ITEMS.getKey(item))
                || (this.progress == 0 && !UURecipeIndex.hasUURecipe(server.getRecipeManager(), item))) {
            this.ejectSample();
            this.storage.onTick(this.inventory, this);
            return;
        }

        int needed = ResearchCost.totalEu(
                UURecipeIndex.milliUUCost(item, UUResearchConfig.DEFAULT_COST_UU.get()), UUResearchConfig.EU_PER_UU.get());
        if (this.maxProgress != needed) {
            this.maxProgress = needed;
            this.updateGuiField("maxProgress");
        }

        if (this.hasEnergy(this.energyConsume)) {
            this.setActive(true);
            this.useEnergy(this.energyConsume);
            this.setProgress(this.progress + this.energyConsume);
            if (this.progress >= this.maxProgress) {
                this.complete(server, knowledge, item);
            }
        } else {
            this.setActive(false);
            this.setProgress(Math.max(0, this.progress - 1));
        }
        this.storage.onTick(this.inventory, this);
    }

    private void complete(MinecraftServer server, ResearchKnowledge knowledge, Item item) {
        knowledge.learn(ForgeRegistries.ITEMS.getKey(item));
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.uuresearch.learned", item.getDescription()), false);
        this.level.playSound(null, this.worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.0F);
        this.ejectSample();
    }

    private void ejectSample() {
        this.inventory.set(SLOT_OUTPUT, this.inventory.get(SLOT_INPUT));
        this.inventory.set(SLOT_INPUT, ItemStack.EMPTY);
        this.setProgress(0);
        this.storage.onRecipeFinished(this.inventory, this);
        this.notifyListeners();
    }

    private void setProgress(int value) {
        if (this.progress != value) {
            this.progress = value;
            this.updateGuiField("progress");
        }
    }

    private boolean isResearchable(Item item) {
        if (this.level == null) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        boolean learned = this.level.isClientSide
                ? ClientKnowledge.isLearned(id)
                : ResearchKnowledge.get(this.level.getServer()).isLearned(id);
        return !learned && UURecipeIndex.hasUURecipe(this.level.getRecipeManager(), item);
    }
}
```

Примечание для исполнителя: если компилятор сообщит, что `registerSlotAccess`/`registerSlotsForSide`/`registerInputFilter`/`RangedInventory` принимают `int[]`, а не varargs, — обернуть аргументы в `new int[]{…}`, как в декомпилированном `CropAnalyzerTileEntity`. Если `notifyListeners()` или `setFuelSlot(int)` не найдены — посмотреть их точное имя через `javap -cp libs/IC2Classic-1.19.2-2.1.3.4.jar ic2.core.block.base.tiles.BaseInventoryTileEntity` / `BaseElectricTileEntity`.

- [ ] **Step 3: `ResearchStationContainer.java`**

```java
package com.letnonesurvive.uuresearch.machine;

import ic2.core.inventory.container.ContainerComponent;
import ic2.core.inventory.filter.special.MachineFilter;
import ic2.core.inventory.gui.components.simple.ChargeBarComponent;
import ic2.core.inventory.gui.components.simple.ProgressComponent;
import ic2.core.inventory.slot.FilterSlot;
import ic2.core.inventory.slot.UpgradeSlot;
import ic2.core.utils.math.geometry.Box2i;
import ic2.core.utils.math.geometry.Vec2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Same layout as IC2's Crop Analyzer machine; reuses its GUI texture for v1.
 */
public class ResearchStationContainer extends ContainerComponent<ResearchStationBlockEntity> {

    public static final ResourceLocation TEXTURE =
            new ResourceLocation("ic2", "textures/gui_sprites/blocks/machines/mv/gui_crop_analyzer.png");
    public static final Vec2i CHARGE_POS = new Vec2i(176, 0);
    public static final Box2i CHARGE_BOX = new Box2i(56, 36, 14, 14);
    public static final Vec2i PROGRESS_POS = new Vec2i(176, 14);
    public static final Box2i PROGRESS_BOX = new Box2i(79, 34, 24, 16);

    public ResearchStationContainer(ResearchStationBlockEntity tile, Player player, int id) {
        super(tile, player, id);
        this.addSlot(FilterSlot.createDischargeSlot(tile, tile.tier, ResearchStationBlockEntity.SLOT_BATTERY, 56, 53));
        this.addSlot(new FilterSlot(tile, ResearchStationBlockEntity.SLOT_INPUT, 56, 17, new MachineFilter(tile)));
        this.addSlot(FilterSlot.createOutputSlot(tile, ResearchStationBlockEntity.SLOT_OUTPUT, 116, 35));
        for (int i = 0; i < 4; i++) {
            this.addSlot(new UpgradeSlot(tile, 3 + i, 152, 8 + i * 18));
        }
        this.addPlayerInventory(player.getInventory());
        this.addComponent(new ChargeBarComponent(CHARGE_BOX, tile, CHARGE_POS, true));
        this.addComponent(new ProgressComponent(PROGRESS_BOX, tile, PROGRESS_POS, false));
    }

    @Override
    public ResourceLocation getTexture() {
        return TEXTURE;
    }
}
```

- [ ] **Step 4: Рецепт и тег кирки**

`data/uuresearch/recipes/research_station.json`:
```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": ["AUA", "CMC", "ACA"],
  "key": {
    "A": { "item": "ic2:advanced_circuit" },
    "U": { "item": "ic2:uumatter" },
    "C": { "item": "ic2:circuit" },
    "M": { "item": "ic2:advanced_machine_block" }
  },
  "result": { "item": "uuresearch:research_station" }
}
```

`data/minecraft/tags/blocks/mineable/pickaxe.json`:
```json
{
  "replace": false,
  "values": ["uuresearch:research_station"]
}
```

- [ ] **Step 5: Локализация**

Добавить в `en_us.json`:
```json
  "block.uuresearch.research_station": "UU Research Station",
  "message.uuresearch.learned": "Replication recipe researched: %s"
```
Добавить в `ru_ru.json`:
```json
  "block.uuresearch.research_station": "UU-исследователь",
  "message.uuresearch.learned": "Изучен рецепт репликации: %s"
```

- [ ] **Step 6: Сборка**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew build`
Expected: `BUILD SUCCESSFUL`. Ошибки сигнатур IC2 — исправлять по примечанию в Step 2, сверяясь с `javap` по `libs/IC2Classic-1.19.2-2.1.3.4.jar`.

- [ ] **Step 7: Проверка блока в игре (пользователь)**

Скопировать jar в `$INST/mods`. В креативе:
1. Блок «UU-исследователь» есть в JEI; его рецепт виден; блок ставится, выглядит как Crop Analyzer (не фиолетово-чёрный куб).
2. Название в JEI и в окне — «UU-исследователь», а не ключ `block.uuresearch…`. Если IC2 строит ключ по-своему — посмотреть ключ в подсказке (F3+H) и добавить его в lang.
3. ПКМ открывает окно: батарея, вход, выход, стрелка прогресса, 4 слота апгрейдов.
4. Ломается киркой, выпадает (блок или машинный корпус, как у машин IC2).

- [ ] **Step 8: Проверка исследования (пользователь)**

1. Без энергии: алмаз кладётся во вход, прогресс стоит.
2. Подключить MV-источник (или батарею в слот): прогресс идёт. По окончании — сообщение «Изучен рецепт репликации: Алмаз», звук, алмаз в выходе, UU-крафт алмаза виден в JEI.
3. Повторно положить алмаз — не кладётся (уже изучен). Булыжник/UU-материя — не кладутся.
4. Время исследования разумное (порядка десятков секунд – пары минут на MV). Иначе поменять `euPerUU` в `saves/<мир>/serverconfig/uuresearch-server.toml` и записать выбранное значение.

- [ ] **Step 9: Крайний случай — изучение командой во время работы**

Положить предмет с UU-рецептом, дождаться начала прогресса, выполнить `/uuresearch learn <этот предмет>` → образец сразу уходит в выход, прогресс 0, энергия больше не тратится.

- [ ] **Step 10: Коммит (после подтверждения пользователя)**

```bash
git add -A
git commit -m "feat: UU Research Station machine"
```

---

### Task 6: Финальная проверка по спецификации

**Files:** нет изменений (только исправления найденного).

- [ ] **Step 1: Полный прогон**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew clean build` → `BUILD SUCCESSFUL`, тесты зелёные.

- [ ] **Step 2: Чек-лист спецификации (пользователь), по разу с `enableSecretRecipeHiding=true` и `=false`**

1. Новый мир — UU-рецептов в JEI нет.
2. Nuke/ITNT видимость соответствует опции IC2.
3. Исследование алмаза в машине — рецепт появился, сообщение в чате, алмаз в выходе.
4. Перезаход в мир — рецепт виден.
5. `/uuresearch reset` — рецепты снова скрыты.
6. Машина без энергии не прогрессирует; неподходящий предмет не кладётся.

- [ ] **Step 3: Коммит исправлений (если были, после подтверждения пользователя)**

```bash
git add -A
git commit -m "fix: issues found in final in-game check"
```
