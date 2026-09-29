package com.letnonesurvive.uuresearch.machine;

import com.letnonesurvive.uuresearch.UUResearchConfig;
import com.letnonesurvive.uuresearch.client.ClientKnowledge;
import com.letnonesurvive.uuresearch.research.ResearchCost;
import com.letnonesurvive.uuresearch.research.ResearchKnowledge;
import com.letnonesurvive.uuresearch.research.UURecipeIndex;
import ic2.api.items.IUpgradeItem.UpgradeType;
import ic2.api.network.buffer.NetworkInfo;
import ic2.api.tiles.readers.IProgressMachine;
import ic2.api.util.DirectionList;
import ic2.core.IC2;
import ic2.core.audio.AudioManager.SoundType;
import ic2.core.block.base.features.ITickListener;
import ic2.core.block.base.misc.comparator.ComparatorNames;
import ic2.core.block.base.misc.comparator.types.base.FlagComparator;
import ic2.core.block.base.misc.comparator.types.base.ProgressComparator;
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
import ic2.core.inventory.inv.SimpleInventory;
import ic2.core.platform.registries.IC2Items;
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
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumSet;

/**
 * MV machine that researches the UU-Matter recipe of a ghost target. Like IC2's Rare Earth Extractor it processes
 * the input one UU-Matter at a time, each taking an equal share of the research energy, and counts the processed
 * units; once the recipe's amount is reached it outputs one craft and marks the recipe researched. Processed units
 * are lost when the target changes or is researched elsewhere, and when the machine is broken. A researched target
 * is idle.
 */
public class ResearchStationBlockEntity extends BaseMachineTileEntity implements ITickListener, ITileGui {

    public static final EnumSet<UpgradeType> UPGRADES = EnumSet.of(
            UpgradeType.RECIPE_MOD, UpgradeType.TRANSPORT_MOD, UpgradeType.CUSTOM_MOD, UpgradeType.MACHINE_MOD,
            UpgradeType.PROCESSING_MOD, UpgradeType.AUDIO_MOD);

    // IC2's OD scanner beep, played once per interval while working: looped it beeps every 0.36 s
    private static final ResourceLocation WORKING_SOUND = new ResourceLocation("ic2", "sounds/tools/scanner.ogg");
    private static final int SOUND_INTERVAL = 36;

    public static final int ENERGY_PER_TICK = 32;

    static final int SLOT_BATTERY = 0;
    static final int SLOT_INPUT = 1;
    static final int SLOT_OUTPUT = 2;

    // In EU at the base rate: overclockers speed it up while the machine pays their extra energy demand
    @NetworkInfo
    public float progress = 0;
    @NetworkInfo
    public int maxProgress = 0;
    @NetworkInfo
    public int neededUU = 0;
    @NetworkInfo
    public int processedUU = 0;

    private int soundTicks = 0;

    // Kept apart from the main inventory, like IC2's filter tubes: never dropped and invisible to automation,
    // otherwise the ghost would turn into a real item
    private final SimpleInventory target = new SimpleInventory(1) {
        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            ResearchStationBlockEntity machine = ResearchStationBlockEntity.this;
            boolean server = machine.level != null && !machine.level.isClientSide;
            // Only the bare item is kept; the server also rejects targets a client should not have been able to pick
            ItemStack ghost = stack.isEmpty() || server && !machine.isTargetValid(stack)
                    ? ItemStack.EMPTY
                    : new ItemStack(stack.getItem());
            boolean changed = !ItemStack.isSameItemSameTags(getStackInSlot(slot), ghost);
            super.setStackInSlot(slot, ghost);
            // A different target starts over and loses the processed UU; the client only mirrors the slot
            if (server && changed) {
                machine.setProgress(0);
                machine.setProcessedUU(0);
                machine.setChanged();
            }
        }
    };

    public ResearchStationBlockEntity(BlockPos pos, BlockState state) {
        // 3 slots, 4 upgrade slots, base EU/t, unused operation length, 10k EU buffer, MV input (128)
        super(pos, state, 3, 4, ENERGY_PER_TICK, 1000, 10_000, 128);
        this.setFuelSlot(SLOT_BATTERY);
        this.addGuiFields("progress", "maxProgress", "neededUU", "processedUU");
        // Like IC2's machines, but the progress signal follows the whole research rather than the current UU unit
        this.addComparator(new ProgressComparator("progress", ComparatorNames.PROGRESS, new IProgressMachine() {
            @Override
            public float getProgress() {
                return processedUU;
            }

            @Override
            public float getMaxProgress() {
                return neededUU;
            }
        }));
        this.addComparator(FlagComparator.createTile("active", ComparatorNames.ACTIVE, this));
    }

    public SimpleInventory getTarget() {
        return target;
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
        this.target.load(compound.getCompound("target"));
        this.progress = compound.getFloat("progress");
        this.processedUU = NBTUtils.getInt(compound, "processedUU", 0);
    }

    @Override
    public void saveAdditional(CompoundTag compound) {
        super.saveAdditional(compound);
        compound.putFloat("progress", this.progress);
        NBTUtils.putInt(compound, "processedUU", this.processedUU, 0);
        compound.put("target", this.target.save(new CompoundTag()));
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

    // The input takes UU-Matter only
    @Override
    public int getValidRoom(ItemStack stack) {
        if (!stack.is(IC2Items.UUMATTER)) {
            return 0;
        }
        ItemStack input = this.inventory.get(SLOT_INPUT);
        return input.isEmpty() ? stack.getMaxStackSize() : Math.max(0, input.getMaxStackSize() - input.getCount());
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
        MinecraftServer server = this.level == null ? null : this.level.getServer();
        if (server == null) {
            return;
        }
        // Reflects the previous tick's state, which is enough for a comparator
        this.handleComparators();
        ItemStack goal = this.target.getStackInSlot(0);
        CraftingRecipe recipe = goal.isEmpty() ? null : UURecipeIndex.recipeFor(server.getRecipeManager(), goal.getItem());
        ResearchKnowledge knowledge = ResearchKnowledge.get(server);
        if (recipe == null || knowledge.isLearned(ForgeRegistries.ITEMS.getKey(goal.getItem()))) {
            setNeededUU(0);
            setProcessedUU(0);
            idle(true);
            return;
        }

        Item item = goal.getItem();
        int milliUU = UURecipeIndex.milliUUCost(item, UUResearchConfig.DEFAULT_COST_UU.get());
        ItemStack result = recipe.getResultItem().copy();
        int need = ResearchCost.uuPerCraft(milliUU, result.getCount());
        setNeededUU(need);
        int perUnit = ResearchCost.euPerUnit(ResearchCost.totalEu(milliUU, UUResearchConfig.EU_PER_UU.get()), need);
        if (this.maxProgress != perUnit) {
            this.maxProgress = perUnit;
            this.updateGuiField("maxProgress");
        }
        // Already enough (e.g. the config lowered the cost): only waits for room in the output
        if (this.processedUU >= need) {
            if (fitsOutput(result)) {
                complete(server, knowledge, item, result);
            }
            idle(false);
            return;
        }
        ItemStack input = this.inventory.get(SLOT_INPUT);
        boolean lastUnit = this.processedUU + 1 >= need;
        if (!input.is(IC2Items.UUMATTER) || lastUnit && !fitsOutput(result)) {
            idle(false);
            return;
        }

        if (this.hasEnergy(this.energyConsume)) {
            this.setActive(true);
            playWorkingSound();
            this.useEnergy(this.energyConsume);
            this.setProgress(this.progress + this.defaultEnergyConsume * this.progressPerTick);
            if (this.progress >= this.maxProgress) {
                // The unit leaves the input only once processed, so an interrupted one can still be taken back
                input.shrink(1);
                this.setProgress(0);
                setProcessedUU(this.processedUU + 1);
                this.setChanged();
                if (this.processedUU >= need) {
                    complete(server, knowledge, item, result);
                }
            }
        } else {
            this.setActive(false);
        }
        this.storage.onTick(this.inventory, this);
    }

    public boolean isTargetValid(ItemStack stack) {
        if (stack.isEmpty() || this.level == null || !UURecipeIndex.hasUURecipe(this.level.getRecipeManager(), stack.getItem())) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return this.level.isClientSide
                ? !ClientKnowledge.isLearned(id)
                : !ResearchKnowledge.get(this.level.getServer()).isLearned(id);
    }

    private void complete(MinecraftServer server, ResearchKnowledge knowledge, Item item, ItemStack result) {
        setProcessedUU(0);
        ItemStack output = this.inventory.get(SLOT_OUTPUT);
        if (output.isEmpty()) {
            this.inventory.set(SLOT_OUTPUT, result);
        } else {
            output.grow(result.getCount());
        }
        this.setProgress(0);
        knowledge.learn(ForgeRegistries.ITEMS.getKey(item));
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.uuresearch.learned", item.getDescription()), false);
        this.level.playSound(null, this.worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.0F);
        this.storage.onRecipeFinished(this.inventory, this);
        this.notifyListeners();
        this.setChanged();
    }

    private boolean fitsOutput(ItemStack result) {
        ItemStack output = this.inventory.get(SLOT_OUTPUT);
        return output.isEmpty()
                || ItemStack.isSameItemSameTags(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void idle(boolean resetProgress) {
        this.setActive(false);
        if (resetProgress) {
            this.setProgress(0);
        }
        this.storage.onTick(this.inventory, this);
    }

    private void setNeededUU(int value) {
        if (this.neededUU != value) {
            this.neededUU = value;
            this.updateGuiField("neededUU");
        }
    }

    private void playWorkingSound() {
        if (this.soundTicks-- <= 0) {
            this.soundTicks = SOUND_INTERVAL - 1;
            IC2.AUDIO.playSound(this, WORKING_SOUND, SoundType.STATIC, this.soundLevel, 1.0F);
        }
    }

    private void setProcessedUU(int value) {
        if (this.processedUU != value) {
            this.processedUU = value;
            this.updateGuiField("processedUU");
        }
    }

    private void setProgress(float value) {
        if (this.progress != value) {
            this.progress = value;
            this.updateGuiField("progress");
        }
    }
}
