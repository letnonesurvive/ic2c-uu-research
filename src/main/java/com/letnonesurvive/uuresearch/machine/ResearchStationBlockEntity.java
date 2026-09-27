package com.letnonesurvive.uuresearch.machine;

import com.letnonesurvive.uuresearch.UUResearchConfig;
import com.letnonesurvive.uuresearch.client.ClientKnowledge;
import com.letnonesurvive.uuresearch.research.ResearchCost;
import com.letnonesurvive.uuresearch.research.ResearchKnowledge;
import com.letnonesurvive.uuresearch.research.ResearchTarget;
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
 * A fluid bucket researches its fluid block, see {@link ResearchTarget}.
 */
public class ResearchStationBlockEntity extends BaseMachineTileEntity implements ITickListener, ITileGui {

    public static final EnumSet<UpgradeType> UPGRADES = EnumSet.of(
            UpgradeType.TRANSPORT_MOD, UpgradeType.CUSTOM_MOD, UpgradeType.MACHINE_MOD, UpgradeType.PROCESSING_MOD);

    public static final int ENERGY_PER_TICK = 32;

    static final int SLOT_BATTERY = 0;
    static final int SLOT_INPUT = 1;
    static final int SLOT_OUTPUT = 2;

    @NetworkInfo
    public int progress = 0;
    @NetworkInfo
    public int maxProgress = 0;

    public ResearchStationBlockEntity(BlockPos pos, BlockState state) {
        // 3 slots, 4 upgrade slots, base EU/t, unused operation length, 10k EU buffer, MV input (128)
        super(pos, state, 3, 4, ENERGY_PER_TICK, 1000, 10_000, 128);
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
    public void saveAdditional(CompoundTag compound) {
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
        return this.inventory.get(SLOT_INPUT).isEmpty() && isResearchable(ResearchTarget.of(stack.getItem())) ? stack.getMaxStackSize() : 0;
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

        Item item = ResearchTarget.of(sample.getItem());
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
