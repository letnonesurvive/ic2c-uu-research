package com.letnonesurvive.uuresearch.machine;

import ic2.core.inventory.container.ContainerComponent;
import ic2.core.inventory.filter.special.MachineFilter;
import ic2.core.inventory.gui.IC2Screen;
import ic2.core.inventory.gui.components.simple.ChargeBarComponent;
import ic2.core.inventory.gui.components.simple.ProgressComponent;
import ic2.core.inventory.gui.components.simple.FilterComponent;
import ic2.core.inventory.slot.FilterSlot;
import ic2.core.inventory.slot.GhostSlot;
import ic2.core.inventory.slot.UpgradeSlot;
import ic2.core.utils.math.geometry.Box2i;
import ic2.core.utils.math.geometry.Vec2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Crop Analyzer layout (and GUI texture) plus a ghost target slot left of the UU-Matter input and a Rare Earth
 * Extractor style bar of processed UU along the top.
 */
public class ResearchStationContainer extends ContainerComponent<ResearchStationBlockEntity> {

    public static final ResourceLocation TEXTURE =
            new ResourceLocation("ic2", "textures/gui_sprites/blocks/machines/mv/gui_crop_analyzer.png");
    public static final Vec2i CHARGE_POS = new Vec2i(176, 0);
    public static final Box2i CHARGE_BOX = new Box2i(56, 36, 14, 14);
    public static final Vec2i PROGRESS_POS = new Vec2i(176, 14);
    public static final Box2i PROGRESS_BOX = new Box2i(79, 34, 24, 16);
    // Same shift as the Rare Earth Extractor, making room for the top bar
    public static final Vec2i BUTTON_OFFSET = new Vec2i(0, 12);

    private final ResearchStationBlockEntity tile;
    private final GhostSlot targetSlot;

    public ResearchStationContainer(ResearchStationBlockEntity tile, Player player, int id) {
        super(tile, player, id);
        this.tile = tile;
        this.addSlot(FilterSlot.createDischargeSlot(tile, tile.tier, ResearchStationBlockEntity.SLOT_BATTERY, 56, 53));
        this.addSlot(new FilterSlot(tile, ResearchStationBlockEntity.SLOT_INPUT, 56, 17, new MachineFilter(tile)));
        this.addSlot(FilterSlot.createOutputSlot(tile, ResearchStationBlockEntity.SLOT_OUTPUT, 116, 35));
        // Ghost target: picked from IC2's item filter panel, stored apart from the machine inventory
        this.targetSlot = new GhostSlot(tile.getTarget(), 0, StatusComponent.TARGET_X, StatusComponent.TARGET_Y, tile::isTargetValid);
        this.addSlot(this.targetSlot);
        for (int i = 0; i < 4; i++) {
            this.addSlot(new UpgradeSlot(tile, 3 + i, 152, 8 + i * 18));
        }
        this.addPlayerInventory(player.getInventory());
        this.addComponent(new ChargeBarComponent(CHARGE_BOX, tile, CHARGE_POS, true));
        this.addComponent(new ProgressComponent(PROGRESS_BOX, tile, PROGRESS_POS, false));
        this.addComponent(new UUBarComponent(tile));
        this.addComponent(new KnowledgePanelComponent());
        FilterComponent picker = new FilterComponent(this.getPreviewOffset());
        this.addComponent(picker);
        // Added after the picker so it sees clicks on the target slot first
        this.addComponent(new StatusComponent(tile, this.targetSlot, picker));
    }

    // The ghost slot sits among the machine slots, so shift-click ranges must count it; otherwise the last
    // upgrade slot is treated as a player slot and shift-clicking it duplicates upgrades
    @Override
    public int getInventorySize() {
        return super.getInventorySize() + 1;
    }

    // Vanilla handling would put real items into the ghost slot (number keys, drag-splitting), destroying them.
    // A plain click with an item only targets that item; everything else on the ghost slot is ignored.
    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId != this.targetSlot.index) {
            super.clicked(slotId, button, type, player);
            return;
        }
        ItemStack carried = this.getCarried();
        if (type == ClickType.PICKUP && !carried.isEmpty() && this.tile.isTargetValid(carried)) {
            this.targetSlot.set(new ItemStack(carried.getItem()));
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void onGuiLoaded(IC2Screen screen) {
        screen.setContainerOffset(0, -3);
    }

    @Override
    public Vec2i getInvButtonOffset() {
        return BUTTON_OFFSET;
    }

    @Override
    public ResourceLocation getTexture() {
        return TEXTURE;
    }
}
