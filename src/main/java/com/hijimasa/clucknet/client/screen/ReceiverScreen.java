package com.hijimasa.clucknet.client.screen;

import com.hijimasa.clucknet.menu.ReceiverMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ReceiverScreen extends AbstractContainerScreen<ReceiverMenu> {
    private static final int BG_COLOR = 0xFFC6C6C6;
    private static final int SLOT_INDENT_COLOR = 0xFF8B8B8B;
    private static final int LABEL_COLOR = 0xFF404040;

    public ReceiverScreen(ReceiverMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics gg, float partialTick, int mouseX, int mouseY) {
        gg.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG_COLOR);
        // Output row: 9 slots at (8, 35) → frames (7, 34) to (169, 52)
        for (int col = 0; col < ReceiverMenu.OUTPUT_SLOTS; col++) {
            int x = leftPos + 7 + col * 18;
            int y = topPos + 34;
            gg.fill(x, y, x + 18, y + 18, SLOT_INDENT_COLOR);
        }
        // Player inventory 9x3 at (8, 84)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int x = leftPos + 7 + col * 18;
                int y = topPos + 83 + row * 18;
                gg.fill(x, y, x + 18, y + 18, SLOT_INDENT_COLOR);
            }
        }
        // Hotbar at (8, 142)
        for (int col = 0; col < 9; col++) {
            int x = leftPos + 7 + col * 18;
            int y = topPos + 141;
            gg.fill(x, y, x + 18, y + 18, SLOT_INDENT_COLOR);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gg, int mouseX, int mouseY) {
        gg.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, LABEL_COLOR, false);
        BlockPos pos = menu.getReceiverPos();
        Component posLine = Component.translatable("clucknet.gui.receiver.position",
                pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        gg.drawString(this.font, posLine, 8, 18, LABEL_COLOR, false);
        gg.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, LABEL_COLOR, false);
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gg, mouseX, mouseY, partialTick);
        super.render(gg, mouseX, mouseY, partialTick);
        this.renderTooltip(gg, mouseX, mouseY);
    }
}
