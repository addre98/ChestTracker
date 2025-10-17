package red.jackf.chesttracker.impl.compat.servers.hypixel;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

interface SMP {
    static boolean isSMPJoinMessage(Component message) {
        ClickEvent click = message.getStyle().getClickEvent();
        return message.getString().startsWith("SMP ID: ")
                && click != null
                && click.action() == ClickEvent.Action.SUGGEST_COMMAND;
    }
}
