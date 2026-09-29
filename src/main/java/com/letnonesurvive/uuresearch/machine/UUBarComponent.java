package com.letnonesurvive.uuresearch.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import ic2.core.inventory.gui.components.GuiWidget;
import ic2.core.utils.math.geometry.Box2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Set;

/**
 * The Rare Earth Extractor's top bar, filled by processed UU out of UU needed. The Crop Analyzer texture has no
 * track there, so both the track and the fill come from the extractor texture; the track is shortened to end
 * before the upgrade column.
 */
public class UUBarComponent extends GuiWidget {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("ic2", "textures/gui_sprites/blocks/machines/lv/gui_rare_earth_extractor.png");
    private static final int X = 7;
    private static final int Y = 12;
    private static final int WIDTH = 141;
    private static final int HEIGHT = 2;

    private final ResearchStationBlockEntity tile;

    public UUBarComponent(ResearchStationBlockEntity tile) {
        super(new Box2i(X, Y, WIDTH, HEIGHT));
        this.tile = tile;
    }

    @Override
    protected void addRequests(Set<ActionRequest> requests) {
        requests.add(ActionRequest.DRAW_BACKGROUND);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawBackground(PoseStack matrix, int mouseX, int mouseY, float partialTicks) {
        int left = this.gui.getGuiLeft();
        int top = this.gui.getGuiTop();
        this.gui.bindTexture(TEXTURE);
        // Track: frame with its left edge, then the extractor's right edge moved in
        this.gui.drawTextureRegion(matrix, left + X - 1, top + Y - 1, X - 1, Y - 1, WIDTH + 1, HEIGHT + 2);
        this.gui.drawTextureRegion(matrix, left + X + WIDTH, top + Y - 1, 168.0F, Y - 1, 1.0F, HEIGHT + 2);
        int needed = this.tile.neededUU;
        if (needed > 0 && this.tile.processedUU > 0) {
            int fill = Math.min(WIDTH, WIDTH * this.tile.processedUU / needed);
            this.gui.drawTextureRegion(matrix, left + X, top + Y, X, 168.0F, fill, HEIGHT);
        }
        // Later widgets may draw from the screen's own texture
        this.gui.bindTexture(ResearchStationContainer.TEXTURE);
    }
}
