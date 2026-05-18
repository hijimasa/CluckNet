package com.hijimasa.clucknet.client.screen;

import com.hijimasa.clucknet.menu.SenderMenu;
import com.hijimasa.clucknet.network.CluckNetNetwork;
import com.hijimasa.clucknet.network.UpdateSenderDestinationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

public class SenderScreen extends AbstractContainerScreen<SenderMenu> {
    private static final int BG_COLOR = 0xFFC6C6C6;
    private static final int SLOT_INDENT_COLOR = 0xFF8B8B8B;
    private static final int LABEL_COLOR = 0xFF404040;

    private static final int EDITBOX_X = 8;
    private static final int EDITBOX_Y = 18;
    private static final int EDITBOX_W = 160;
    private static final int EDITBOX_H = 14;

    private static final int STATUS_Y = 56;

    private EditBox destinationField;
    @Nullable
    private Component statusText;

    public SenderScreen(SenderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();

        destinationField = new EditBox(this.font,
                leftPos + EDITBOX_X, topPos + EDITBOX_Y, EDITBOX_W, EDITBOX_H,
                Component.translatable("clucknet.gui.sender.destination"));
        destinationField.setMaxLength(48);
        destinationField.setHint(Component.literal("x y z  (e.g. 30 64 0)"));

        BlockPos initial = menu.getInitialDestination();
        if (initial != null) {
            destinationField.setValue(initial.getX() + " " + initial.getY() + " " + initial.getZ());
            setStatusFromPos(initial);
        } else {
            statusText = Component.translatable("clucknet.gui.sender.unlinked");
        }

        destinationField.setResponder(this::onDestinationTyped);
        addRenderableWidget(destinationField);
        setInitialFocus(destinationField);
    }

    private void onDestinationTyped(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            destinationField.setTextColor(0xA0A0A0);
            statusText = Component.translatable("clucknet.gui.sender.unlinked");
            CluckNetNetwork.sendToServer(
                    new UpdateSenderDestinationPacket(menu.getSenderPos(), null));
            return;
        }
        BlockPos parsed = parseBlockPos(trimmed);
        if (parsed == null) {
            destinationField.setTextColor(0xFF6060);
            statusText = Component.translatable("clucknet.gui.sender.invalid");
            return;
        }
        destinationField.setTextColor(0xE0E0E0);
        setStatusFromPos(parsed);
        CluckNetNetwork.sendToServer(
                new UpdateSenderDestinationPacket(menu.getSenderPos(), parsed));
    }

    private void setStatusFromPos(BlockPos pos) {
        statusText = Component.translatable("clucknet.gui.sender.linked",
                pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    @Override
    protected void renderBg(GuiGraphics gg, float partialTick, int mouseX, int mouseY) {
        // Outer background
        gg.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG_COLOR);
        // Input slot indent at (80, 35) — frame is (79, 34) to (97, 52)
        gg.fill(leftPos + 79, topPos + 34, leftPos + 79 + 18, topPos + 34 + 18, SLOT_INDENT_COLOR);
        // Player inventory grid 9x3 starting at slot pos (8, 84) — frame (7, 83) to (171, 137)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int x = leftPos + 7 + col * 18;
                int y = topPos + 83 + row * 18;
                gg.fill(x, y, x + 18, y + 18, SLOT_INDENT_COLOR);
            }
        }
        // Hotbar at slot pos (8, 142) — frame (7, 141) to (171, 159)
        for (int col = 0; col < 9; col++) {
            int x = leftPos + 7 + col * 18;
            int y = topPos + 141;
            gg.fill(x, y, x + 18, y + 18, SLOT_INDENT_COLOR);
        }
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gg, mouseX, mouseY, partialTick);
        super.render(gg, mouseX, mouseY, partialTick);
        if (statusText != null) {
            gg.drawString(this.font, statusText, leftPos + 8, topPos + STATUS_Y, LABEL_COLOR, false);
        }
        this.renderTooltip(gg, mouseX, mouseY);
    }

    @Nullable
    private static BlockPos parseBlockPos(String input) {
        String s = input.replace(',', ' ').trim();
        if (s.isEmpty()) {
            return null;
        }
        String[] parts = s.split("\\s+");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
