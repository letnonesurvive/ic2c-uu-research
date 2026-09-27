package com.letnonesurvive.uuresearch.machine;

import com.letnonesurvive.uuresearch.client.ClientKnowledge;
import com.letnonesurvive.uuresearch.research.UURecipeIndex;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import ic2.core.inventory.gui.IC2Screen;
import ic2.core.inventory.gui.components.GuiWidget;
import ic2.core.inventory.gui.components.base.IconButton;
import ic2.core.inventory.gui.components.simple.SliderComponent;
import ic2.core.utils.math.geometry.Box2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Read-only side panel listing every UU-Matter item: researched ones first, the rest darkened.
 * Laid out like IC2's item filter panel and drawn with its texture; toggled by a knowledge book button next to the battery slot.
 */
public class KnowledgePanelComponent extends GuiWidget {

    private static final ResourceLocation TEXTURE = new ResourceLocation("ic2", "textures/gui_sprites/misc/filter_helper.png");
    private static final int BUTTON_ID = 5100;
    private static final int PANEL_X = 176;
    private static final int WIDTH = 118;
    private static final int HEIGHT = 132;
    private static final int COLUMNS = 5;
    private static final int VISIBLE = 25;
    private static final int UNKNOWN_OVERLAY = 0xC0202020;

    // Remembered while the game runs, so the panel stays open between machine visits
    private static boolean open;

    private final SliderComponent slider;
    private final List<ItemStack> items = new ArrayList<>();
    private int learnedCount;
    private int builtVersion = -1;

    public KnowledgePanelComponent() {
        super(new Box2i(PANEL_X, 0, WIDTH, HEIGHT));
        this.slider = this.addChild(new SliderComponent(
                new Box2i(PANEL_X + WIDTH - 19, 37, 12, 88), new Box2i(0, 132, 12, 15), COLUMNS))
                .setNonEmptyRows(4)
                .setCustomTexture(TEXTURE);
        this.setVisible(false);
        this.slider.setVisible(false);
    }

    @Override
    protected void addRequests(Set<ActionRequest> requests) {
        requests.add(ActionRequest.GUI_INIT);
        requests.add(ActionRequest.GUI_TICK);
        requests.add(ActionRequest.DRAW_BACKGROUND);
        requests.add(ActionRequest.TOOLTIP);
        requests.add(ActionRequest.MOUSE_INPUT);
        requests.add(ActionRequest.MOUSE_SCROLL);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void init(IC2Screen gui) {
        // Knowledge book icon left of the battery slot, level with it
        gui.addRenderableWidget(BUTTON_ID, new IconButton(gui.getGuiLeft() + 30, gui.getGuiTop() + 51, 20, 20,
                new ItemStack(Items.KNOWLEDGE_BOOK), button -> setOpen(!open))
                .setToolTip("gui.uuresearch.knowledge.button"));
        setOpen(open);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void tick(IC2Screen gui) {
        if (builtVersion != ClientKnowledge.version()) {
            rebuild();
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawBackground(PoseStack matrix, int mouseX, int mouseY, float partialTicks) {
        int x = this.gui.getGuiLeft() + PANEL_X;
        int y = this.gui.getGuiTop();
        this.gui.bindTexture(TEXTURE);
        this.gui.drawTextureRegion(matrix, x, y, 0.0F, 0.0F, WIDTH, HEIGHT);
        this.gui.drawCenterString(matrix, Component.translatable("gui.uuresearch.knowledge.title"), x + 57, y + 8, 0x404040);
        this.gui.drawCenterString(matrix,
                Component.translatable("gui.uuresearch.knowledge.count", learnedCount, items.size()), x + 57, y + 23, 0xFFFFFF);

        Lighting.setupForFlatItems();
        int offset = this.slider.getCurrent();
        for (int i = 0; i < VISIBLE && i + offset < items.size(); i++) {
            int index = i + offset;
            int itemX = x + 6 + 18 * (i % COLUMNS);
            int itemY = y + 37 + 18 * (i / COLUMNS);
            this.gui.getRenderItem().renderAndDecorateItem(items.get(index), itemX, itemY);
            if (index >= learnedCount) {
                // Darken unresearched items so they read as silhouettes
                RenderSystem.disableDepthTest();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                this.gui.drawColoredRegion(matrix, itemX, itemY, 16.0F, 16.0F, UNKNOWN_OVERLAY);
                RenderSystem.disableBlend();
                RenderSystem.enableDepthTest();
            }
        }
        Lighting.setupFor3DItems();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addTooltips(PoseStack matrix, int mouseX, int mouseY, Consumer<Component> tooltips) {
        int index = indexAt(mouseX, mouseY);
        if (index >= 0) {
            // Item tooltips also run ClientEvents#onItemTooltip, which adds the research status line
            items.get(index).getTooltipLines(this.gui.getPlayer(), TooltipFlag.Default.NORMAL).forEach(tooltips);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean onMouseClick(int mouseX, int mouseY, int mouseButton) {
        // Swallow clicks on the panel so they don't reach the slots or the world behind it
        return this.isVisible() && this.box.isInBox(mouseX, mouseY) && !this.slider.isMouseOver(mouseX, mouseY);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean onMouseScroll(int mouseX, int mouseY, int scroll) {
        if (this.isVisible() && this.box.isInBox(mouseX, mouseY) && !this.slider.isMouseOver(mouseX, mouseY)) {
            this.slider.onMouseScroll(mouseX, mouseY, scroll);
        }
        return false;
    }

    @OnlyIn(Dist.CLIENT)
    private void setOpen(boolean value) {
        open = value;
        this.setVisible(value);
        this.slider.setVisible(value);
        if (value) {
            rebuild();
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void rebuild() {
        builtVersion = ClientKnowledge.version();
        List<ItemStack> learned = new ArrayList<>();
        List<ItemStack> unknown = new ArrayList<>();
        for (ResourceLocation id : UURecipeIndex.outputIds(this.gui.getPlayer().connection.getRecipeManager())) {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null) {
                (ClientKnowledge.isLearned(id) ? learned : unknown).add(new ItemStack(item));
            }
        }
        items.clear();
        items.addAll(learned);
        items.addAll(unknown);
        learnedCount = learned.size();
        this.slider.setMax(items.size());
    }

    private int indexAt(int mouseX, int mouseY) {
        if (!this.isVisible()) {
            return -1;
        }
        int offset = this.slider.getCurrent();
        for (int i = 0; i < VISIBLE && i + offset < items.size(); i++) {
            int itemX = PANEL_X + 6 + 18 * (i % COLUMNS);
            int itemY = 37 + 18 * (i / COLUMNS);
            if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
                return i + offset;
            }
        }
        return -1;
    }
}
