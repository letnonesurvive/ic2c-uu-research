package com.letnonesurvive.uuresearch.machine;

import com.letnonesurvive.uuresearch.client.ClientKnowledge;
import com.mojang.blaze3d.vertex.PoseStack;
import ic2.core.inventory.gui.components.GuiWidget;
import ic2.core.inventory.gui.components.simple.FilterComponent;
import ic2.core.inventory.gui.components.simple.IFilterTarget;
import ic2.core.inventory.slot.GhostSlot;
import ic2.core.utils.math.geometry.Box2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/**
 * Draws the ghost target's slot frame (the Crop Analyzer texture has no fourth slot) and a status line:
 * no target / already researched / UU processed out of UU needed. Also handles clicks on the target slot that
 * IC2 leaves to vanilla: IC2 only opens its picker for an empty slot with an empty hand, so a chosen target could
 * not be changed, and vanilla would put a held real item into the ghost slot.
 */
public class StatusComponent extends GuiWidget {

    static final int TARGET_X = 30;
    static final int TARGET_Y = 17;
    // Centered under the output slot, but kept clear of the upgrade column
    private static final int STATUS_CENTER_X = 124;
    private static final int STATUS_MAX_RIGHT = 149;
    private static final int STATUS_Y = 58;

    private final ResearchStationBlockEntity tile;
    private final GhostSlot targetSlot;
    private final FilterComponent picker;

    public StatusComponent(ResearchStationBlockEntity tile, GhostSlot targetSlot, FilterComponent picker) {
        super(new Box2i(TARGET_X - 1, TARGET_Y - 1, 18, 18));
        this.tile = tile;
        this.targetSlot = targetSlot;
        this.picker = picker;
    }

    @Override
    protected void addRequests(Set<ActionRequest> requests) {
        requests.add(ActionRequest.DRAW_BACKGROUND);
        requests.add(ActionRequest.MOUSE_INPUT);
    }

    // Held item: target it without taking it. Chosen target: left click reopens the picker, right click clears it.
    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean onMouseClick(int mouseX, int mouseY, int mouseButton) {
        if (this.gui.getSlotUnderMouse() != this.targetSlot) {
            return false;
        }
        IFilterTarget ghost = IFilterTarget.ghost(this.targetSlot);
        ItemStack carried = this.gui.getMenu().getCarried();
        if (!carried.isEmpty()) {
            if (this.targetSlot.isStackValid(carried)) {
                ghost.accept(carried.copy());
            }
            return true;
        }
        if (!this.targetSlot.hasItem()) {
            return false;
        }
        if (mouseButton == 1) {
            ghost.accept(ItemStack.EMPTY);
        } else {
            this.picker.setGhost(ghost);
        }
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawBackground(PoseStack matrix, int mouseX, int mouseY, float partialTicks) {
        int left = this.gui.getGuiLeft();
        int top = this.gui.getGuiTop();
        // Reuse the input slot's frame from the same texture
        this.gui.bindTexture(ResearchStationContainer.TEXTURE);
        this.gui.drawTextureRegion(matrix, left + TARGET_X - 1, top + TARGET_Y - 1, 55.0F, 16.0F, 18.0F, 18.0F);
        Component status = status();
        int width = this.gui.getFont().width(status);
        int x = Math.min(STATUS_CENTER_X - width / 2, STATUS_MAX_RIGHT - width);
        this.gui.drawString(matrix, status, left + x, top + STATUS_Y, 0x404040);
    }

    @OnlyIn(Dist.CLIENT)
    private Component status() {
        ItemStack goal = this.tile.getTarget().getStackInSlot(0);
        if (goal.isEmpty()) {
            return Component.translatable("gui.uuresearch.status.no_target");
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(goal.getItem());
        if (ClientKnowledge.isLearned(id)) {
            return Component.translatable("gui.uuresearch.status.learned");
        }
        return Component.translatable("gui.uuresearch.status.uu", this.tile.processedUU, this.tile.neededUU);
    }
}
