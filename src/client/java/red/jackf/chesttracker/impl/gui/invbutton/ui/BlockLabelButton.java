package red.jackf.chesttracker.impl.gui.invbutton.ui;

import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import red.jackf.chesttracker.api.memory.Memory;
import red.jackf.chesttracker.api.providers.MemoryLocation;
import red.jackf.chesttracker.impl.memory.MemoryBankImpl;
import red.jackf.chesttracker.impl.util.GuiUtil;
import red.jackf.whereisit.config.WhereIsItConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class BlockLabelButton extends SecondaryButton {
    private static final WidgetSprites BLOCK_SPRITES = GuiUtil.twoSprite("inventory_button/block_label");
    private static final WidgetSprites UNBLOCK_SPRITES = GuiUtil.twoSprite("inventory_button/unblock_label");

    private final MemoryBankImpl bank;
    private final MemoryLocation memoryLocation;
    private State state = State.BLOCK;

    public BlockLabelButton(MemoryBankImpl bank, MemoryLocation memoryLocation) {
        super(BLOCK_SPRITES, Component.translatable("chesttracker.inventoryButton.blockLabel"), () -> {});
        this.bank = bank;
        this.memoryLocation = memoryLocation;
        this.onClick = this::toggleCurrentContainerLabel;
        this.syncState();
    }

    @Override
    protected WidgetSprites getSprites() {
        this.syncState();
        return this.state.sprites;
    }

    private void toggleCurrentContainerLabel() {
        Optional<String> labelToToggle = this.bank.getMemory(this.memoryLocation)
                .map(Memory::renderName)
                .map(Component::getString)
                .map(String::trim)
                .filter(s -> !s.isEmpty());

        if (labelToToggle.isEmpty()) {
            this.syncState();
            return;
        }

        String label = labelToToggle.get();
        String normalizedLabel = normalizeLabel(label);

        var whereIsItClientConfig = WhereIsItConfig.INSTANCE.instance().getClient();
        if (whereIsItClientConfig.blockedContainerLabelNames == null) {
            whereIsItClientConfig.blockedContainerLabelNames = new ArrayList<>();
        }

        List<String> blockedLabels = whereIsItClientConfig.blockedContainerLabelNames;
        boolean alreadyBlocked = blockedLabels.stream().map(BlockLabelButton::normalizeLabel).anyMatch(normalizedLabel::equals);

        if (alreadyBlocked) {
            blockedLabels.removeIf(blockedName -> normalizedLabel.equals(normalizeLabel(blockedName)));
            this.setState(State.BLOCK);
        } else {
            blockedLabels.add(label);
            this.setState(State.UNBLOCK);
        }

        WhereIsItConfig.INSTANCE.save();
        this.syncState();
    }

    private void syncState() {
        Optional<String> currentLabel = this.bank.getMemory(this.memoryLocation)
                .map(Memory::renderName)
                .map(Component::getString)
                .map(String::trim)
                .filter(s -> !s.isEmpty());

        if (currentLabel.isEmpty()) {
            this.setState(State.BLOCK);
            return;
        }

        List<String> blockedLabels = WhereIsItConfig.INSTANCE.instance().getClient().blockedContainerLabelNames;
        if (blockedLabels == null || blockedLabels.isEmpty()) {
            this.setState(State.BLOCK);
            return;
        }

        String normalizedLabel = normalizeLabel(currentLabel.get());
        boolean blocked = blockedLabels.stream()
                .map(BlockLabelButton::normalizeLabel)
                .anyMatch(normalizedLabel::equals);
        this.setState(blocked ? State.UNBLOCK : State.BLOCK);
    }

    private void setState(State state) {
        this.state = state;
        this.setMessage(state.tooltip);
        this.setTooltip(net.minecraft.client.gui.components.Tooltip.create(state.tooltip));
    }

    private static String normalizeLabel(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private enum State {
        BLOCK(BLOCK_SPRITES, Component.translatable("chesttracker.inventoryButton.blockLabel")),
        UNBLOCK(UNBLOCK_SPRITES, Component.translatable("chesttracker.inventoryButton.unblockLabel"));

        private final WidgetSprites sprites;
        private final Component tooltip;

        State(WidgetSprites sprites, Component tooltip) {
            this.sprites = sprites;
            this.tooltip = tooltip;
        }
    }
}
