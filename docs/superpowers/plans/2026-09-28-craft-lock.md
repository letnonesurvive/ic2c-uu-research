# Блокировка крафта — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** UU-рецепт IC2 нельзя скрафтить (нигде), пока его выход не изучен в этом мире; опция `requireResearchToCraft` (по умолчанию вкл.).

**Architecture:** Чистое правило `CraftLock.blocks(...)` (JUnit) + `CraftLock.shouldBlock(recipe, level)`, берущее знание с сервера (`ResearchKnowledge`) или клиента (`ClientKnowledge`). Mixin `@Inject` в начало `ShapedIC2Recipe.matches(CraftingContainer, Level)` с `remap = false` (метод так называется и в jar IC2), без refmap и MixinGradle: только mixin-класс, `uuresearch.mixins.json`, атрибут `MixinConfigs` в манифесте и аргумент `-mixin.config` для `runClient`.

**Tech Stack:** Forge 1.19.2-43.5.0, SpongePowered Mixin 0.8.5 (идёт с Forge), IC2 Classic 2.1.3.4, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-27-uu-research-design.md` — раздел «Блокировка крафта (дизайн 2026-09-28)».

## Global Constraints

- Блокируются только рецепты `RecipeIC2Base` с `isHidden() == true` и UU-материей в ингредиентах (`UURecipeIndex.isUURecipe`); выход не изучен в мире → `matches` = `false`
- Без исключений (творческий режим тоже), без сообщений игроку (сообщение — в идеях, позже)
- Опция `requireResearchToCraft`, серверный конфиг `uuresearch-server.toml`, по умолчанию `true`
- До загрузки серверного конфига (`UUResearchConfig.SPEC.isLoaded() == false`) — не блокировать
- Сервер: `ResearchKnowledge.get(level.getServer())`; клиент: `ClientKnowledge` (по `level.isClientSide`)
- Цель mixin: `ic2.core.platform.recipes.crafting.ShapedIC2Recipe`, метод `matches(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/level/Level;)Z`, `remap = false`
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17` для всех gradle-команд; комментарии в коде — на английском
- Коммит — только после проверки пользователем в игре

## Review Focus

1. **Производительность `matches`:** вызывается на каждое изменение сетки для каждого рецепта. Нескрытые рецепты должны отсекаться до поиска знаний и перебора ингредиентов — тест `stopsAtFirstFalseCheck` (Task 1).
2. **Главное меню / до загрузки конфига:** `ForgeConfigSpec.get()` бросает, если конфиг не загружен → guard `SPEC.isLoaded()` (Task 2, код).
3. **Mixin не применился молча:** `"defaultRequire": 1` → при неудаче инъекции игра падает при загрузке с понятной ошибкой, а не работает без блокировки (Task 2, конфиг).
4. **Рецепт машины и иридий:** не скрыты → не блокируются. Проверка в игре (Task 3).
5. **Dedicated server:** `CraftLock` ссылается на `ClientKnowledge` — он без клиентских классов (проверено ревью v1), на сервере безопасен.

---

### Task 1: Правило блокировки и опция конфига

**Files:**
- Create: `src/main/java/com/letnonesurvive/uuresearch/research/CraftLock.java` (только чистое правило `blocks`)
- Modify: `src/main/java/com/letnonesurvive/uuresearch/UUResearchConfig.java`
- Test: `src/test/java/com/letnonesurvive/uuresearch/research/CraftLockTest.java`

**Interfaces:**
- Produces: `CraftLock.blocks(boolean enabled, boolean hidden, BooleanSupplier learned, BooleanSupplier usesUUMatter) -> boolean` — `enabled && hidden && !learned && usesUUMatter`, дешёвые проверки раньше дорогих, ленивые поставщики не вызываются после первого `false`; `UUResearchConfig.REQUIRE_RESEARCH_TO_CRAFT` (`ForgeConfigSpec.BooleanValue`, default `true`)

- [ ] **Step 1: Падающий тест `CraftLockTest.java`**

```java
package com.letnonesurvive.uuresearch.research;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class CraftLockTest {

    @Test
    void blocksUnlearnedHiddenUURecipe() {
        assertTrue(CraftLock.blocks(true, true, () -> false, () -> true));
    }

    @Test
    void allowsWhenDisabled() {
        assertFalse(CraftLock.blocks(false, true, () -> false, () -> true));
    }

    @Test
    void allowsVisibleRecipes() {
        assertFalse(CraftLock.blocks(true, false, () -> false, () -> true));
    }

    @Test
    void allowsLearnedRecipes() {
        assertFalse(CraftLock.blocks(true, true, () -> true, () -> true));
    }

    @Test
    void allowsHiddenRecipesWithoutUUMatter() {
        assertFalse(CraftLock.blocks(true, true, () -> false, () -> false));
    }

    @Test
    void stopsAtFirstFalseCheck() {
        AtomicInteger calls = new AtomicInteger();
        BooleanSupplier counted = () -> {
            calls.incrementAndGet();
            return true;
        };
        CraftLock.blocks(false, true, counted, counted);
        CraftLock.blocks(true, false, counted, counted);
        assertEquals(0, calls.get());
        CraftLock.blocks(true, true, counted, counted);
        assertEquals(1, calls.get(), "learned=true must skip the ingredient scan");
    }
}
```

- [ ] **Step 2: Убедиться, что падает**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test`
Expected: FAIL компиляции — `cannot find symbol ... CraftLock`.

- [ ] **Step 3: `CraftLock.java` (чистая часть)**

```java
package com.letnonesurvive.uuresearch.research;

import java.util.function.BooleanSupplier;

/**
 * Decides whether an IC2 UU-Matter recipe may be crafted. Called from recipe matching, which runs for every
 * recipe on every crafting grid change, so checks go from cheapest to most expensive and stop early.
 */
public final class CraftLock {

    private CraftLock() {
    }

    public static boolean blocks(boolean enabled, boolean hidden, BooleanSupplier learned, BooleanSupplier usesUUMatter) {
        return enabled && hidden && !learned.getAsBoolean() && usesUUMatter.getAsBoolean();
    }
}
```

- [ ] **Step 4: Опция в `UUResearchConfig.java`**

Добавить поле `public static final ForgeConfigSpec.BooleanValue REQUIRE_RESEARCH_TO_CRAFT;` и в `static`-блок перед `SPEC = builder.build();`:
```java
        REQUIRE_RESEARCH_TO_CRAFT = builder
                .comment("UU-Matter recipes can only be crafted after the item has been researched")
                .define("requireResearchToCraft", true);
```

- [ ] **Step 5: Тесты зелёные**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test`
Expected: `BUILD SUCCESSFUL`, 23 теста (17 + 6).

---

### Task 2: Mixin и привязка к миру

**Files:**
- Modify: `src/main/java/com/letnonesurvive/uuresearch/research/CraftLock.java` (добавить `shouldBlock`)
- Create: `src/main/java/com/letnonesurvive/uuresearch/mixin/ShapedIC2RecipeMixin.java`
- Create: `src/main/resources/uuresearch.mixins.json`
- Modify: `build.gradle` (манифест `MixinConfigs`, `-mixin.config` для `runClient`)

**Interfaces:**
- Consumes: `CraftLock.blocks`, `UUResearchConfig.REQUIRE_RESEARCH_TO_CRAFT`/`SPEC` (Task 1); `UURecipeIndex.outputId/isUURecipe`, `ResearchKnowledge.get/isLearned`, `ClientKnowledge.isLearned` (v1)
- Produces: `CraftLock.shouldBlock(CraftingRecipe recipe, Level level) -> boolean`

- [ ] **Step 1: `shouldBlock` в `CraftLock.java`**

Добавить импорты (`UUResearchConfig`, `ClientKnowledge`, `RecipeIC2Base`, `ResourceLocation`, `MinecraftServer`, `CraftingRecipe`, `Level`, `javax.annotation.Nullable`) и метод:
```java
    /** True if the recipe must not match in this level because its UU-Matter recipe is not researched yet. */
    public static boolean shouldBlock(CraftingRecipe recipe, @Nullable Level level) {
        if (level == null || !UUResearchConfig.SPEC.isLoaded()) {
            return false;
        }
        return blocks(UUResearchConfig.REQUIRE_RESEARCH_TO_CRAFT.get(),
                recipe instanceof RecipeIC2Base base && base.isHidden(),
                () -> isLearned(UURecipeIndex.outputId(recipe), level),
                () -> UURecipeIndex.isUURecipe(recipe));
    }

    private static boolean isLearned(ResourceLocation id, Level level) {
        if (level.isClientSide) {
            return ClientKnowledge.isLearned(id);
        }
        MinecraftServer server = level.getServer();
        return server != null && ResearchKnowledge.get(server).isLearned(id);
    }
```

- [ ] **Step 2: `ShapedIC2RecipeMixin.java`**

```java
package com.letnonesurvive.uuresearch.mixin;

import com.letnonesurvive.uuresearch.research.CraftLock;
import ic2.core.platform.recipes.crafting.ShapedIC2Recipe;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes unresearched UU-Matter recipes not match anywhere recipes are matched. IC2's jar keeps this method's
 * readable name in production (the obfuscated m_5818_ is only a bridge to it), hence remap = false.
 */
@Mixin(value = ShapedIC2Recipe.class, remap = false)
public abstract class ShapedIC2RecipeMixin {

    @Inject(method = "matches(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/level/Level;)Z",
            at = @At("HEAD"), cancellable = true)
    private void uuresearch$requireResearch(CraftingContainer container, Level level, CallbackInfoReturnable<Boolean> cir) {
        if (CraftLock.shouldBlock((CraftingRecipe) (Object) this, level)) {
            cir.setReturnValue(false);
        }
    }
}
```

- [ ] **Step 3: `uuresearch.mixins.json`**

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.letnonesurvive.uuresearch.mixin",
  "compatibilityLevel": "JAVA_17",
  "mixins": ["ShapedIC2RecipeMixin"],
  "injectors": {
    "defaultRequire": 1
  }
}
```

- [ ] **Step 4: `build.gradle`**

В `runs { client { … } }` добавить: `arg '-mixin.config=uuresearch.mixins.json'`.
В `jar { manifest { attributes([...]) } }` добавить пару `"MixinConfigs": "uuresearch.mixins.json"`.
Если компиляция не видит `org.spongepowered.asm.mixin` — добавить в `repositories` `maven { url = 'https://repo.spongepowered.org/repository/maven-public/' }` и в `dependencies` `compileOnly 'org.spongepowered:mixin:0.8.5'`.

- [ ] **Step 5: Сборка и содержимое jar**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew build && unzip -p build/libs/uuresearch-1.19.2-0.1.0.jar META-INF/MANIFEST.MF | grep MixinConfigs && unzip -l build/libs/uuresearch-1.19.2-0.1.0.jar | grep -E "uuresearch.mixins.json|ShapedIC2RecipeMixin"`
Expected: `BUILD SUCCESSFUL`, 23 теста; `MixinConfigs: uuresearch.mixins.json`; оба файла в jar.

- [ ] **Step 6: Mixin применяется в dev-клиенте**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew runClient` (в фоне), дождаться главного меню.
Expected: в `run/logs/latest.log` нет `InvalidInjectionException`/`MixinApplyError`/`Critical injection failure`; игра дошла до меню (`Sound engine started`). Закрыть клиент.

---

### Task 3: Проверка в игре (пользователь) и коммит

**Files:** нет (только исправления найденного)

- [ ] **Step 1: Чек-лист** (новый мир с читами, `runClient` или jar в Prism)

1. Узор алмаза (9 UU) в верстаке → результата нет.
2. `/uuresearch learn minecraft:diamond` → результат появился; `/uuresearch forget minecraft:diamond` → пропал.
3. Булыжник (1 UU в углу) в сетке 2×2 инвентаря → результата нет.
4. Рецепт машины «UU-исследователь» (с UU-материей, не скрыт) → крафтится.
5. Иридиевая руда (7 UU, видимый рецепт) → крафтится.
6. `saves/<мир>/serverconfig/uuresearch-server.toml`: `requireResearchToCraft = false`, перезайти → алмаз крафтится без изучения; вернуть `true`.
7. Изучить предмет в машине (как раньше) → после сообщения в чате крафт доступен.

- [ ] **Step 2: Коммит (после подтверждения пользователя)**

```bash
git add -A
git commit -m "feat: UU recipes can only be crafted after research"
```
