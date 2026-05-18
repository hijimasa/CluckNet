package com.hijimasa.clucknet.menu;

import com.hijimasa.clucknet.block.ModBlocks;
import com.hijimasa.clucknet.block.SenderBlockEntity;
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
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

public class SenderMenu extends AbstractContainerMenu {
    private static final int INPUT_SLOT_INDEX = 0;
    private static final int INPUT_SLOT_X = 80;
    private static final int INPUT_SLOT_Y = 35;
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y = 84;
    private static final int HOTBAR_Y = 142;

    private final Container inputContainer;
    private final ContainerLevelAccess access;
    private final BlockPos senderPos;
    @Nullable
    private final BlockPos initialDestination;

    /** Server-side constructor (called from SenderBlockEntity.createMenu). */
    public SenderMenu(int containerId, Inventory playerInv, SenderBlockEntity blockEntity) {
        this(containerId, playerInv,
                blockEntity.getInputContainer(),
                ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                blockEntity.getBlockPos(),
                blockEntity.getDestination());
    }

    /** Client-side constructor (network factory via IForgeMenuType). */
    public SenderMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv,
                new SimpleContainer(1),
                ContainerLevelAccess.NULL,
                buf.readBlockPos(),
                buf.readBoolean() ? buf.readBlockPos() : null);
    }

    private SenderMenu(int containerId, Inventory playerInv, Container inputContainer,
                       ContainerLevelAccess access, BlockPos senderPos,
                       @Nullable BlockPos initialDestination) {
        super(ModMenuTypes.SENDER_MENU.get(), containerId);
        this.inputContainer = inputContainer;
        this.access = access;
        this.senderPos = senderPos;
        this.initialDestination = initialDestination;

        checkContainerSize(inputContainer, 1);
        inputContainer.startOpen(playerInv.player);

        addSlot(new Slot(inputContainer, INPUT_SLOT_INDEX, INPUT_SLOT_X, INPUT_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.WRITABLE_BOOK);
            }
        });

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

    public BlockPos getSenderPos() {
        return senderPos;
    }

    @Nullable
    public BlockPos getInitialDestination() {
        return initialDestination;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.SENDER_BLOCK.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index == INPUT_SLOT_INDEX) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (stack.is(Items.WRITABLE_BOOK)) {
                if (!moveItemStackTo(stack, INPUT_SLOT_INDEX, INPUT_SLOT_INDEX + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
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
        inputContainer.stopOpen(player);
    }
}
