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
