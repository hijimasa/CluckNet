package com.hijimasa.clucknet.menu;

import com.hijimasa.clucknet.block.ModBlocks;
import com.hijimasa.clucknet.block.ReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ReceiverMenu extends AbstractContainerMenu {
    public static final int OUTPUT_SLOTS = ReceiverBlockEntity.OUTPUT_SLOTS;

    private static final int OUTPUT_ROW_X = 8;
    private static final int OUTPUT_ROW_Y = 35;
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y = 84;
    private static final int HOTBAR_Y = 142;

    private final Container outputContainer;
    private final ContainerLevelAccess access;
    private final BlockPos receiverPos;

    /** Server-side constructor (called from ReceiverBlockEntity.createMenu). */
    public ReceiverMenu(int containerId, Inventory playerInv, ReceiverBlockEntity blockEntity) {
        this(containerId, playerInv,
                blockEntity.getOutputContainer(),
                ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                blockEntity.getBlockPos());
    }

    /** Client-side constructor (network factory via IForgeMenuType). */
    public ReceiverMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv,
                new SimpleContainer(OUTPUT_SLOTS),
                ContainerLevelAccess.NULL,
                buf.readBlockPos());
    }

    private ReceiverMenu(int containerId, Inventory playerInv, Container outputContainer,
                         ContainerLevelAccess access, BlockPos receiverPos) {
        super(ModMenuTypes.RECEIVER_MENU.get(), containerId);
        this.outputContainer = outputContainer;
        this.access = access;
        this.receiverPos = receiverPos;

        checkContainerSize(outputContainer, OUTPUT_SLOTS);
        outputContainer.startOpen(playerInv.player);

        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            addSlot(new Slot(outputContainer, i,
                    OUTPUT_ROW_X + i * 18, OUTPUT_ROW_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlot(new Slot(playerInv, col + row * 9 + 9,
                        PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            addSlot(new Slot(playerInv, col, PLAYER_INV_X + col * 18, HOTBAR_Y));
        }
    }

    public BlockPos getReceiverPos() {
        return receiverPos;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.RECEIVER_BLOCK.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index < OUTPUT_SLOTS) {
            if (!moveItemStackTo(stack, OUTPUT_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        outputContainer.stopOpen(player);
    }
}
