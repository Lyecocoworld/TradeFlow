package com.github.lye.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * TradeFlowEvent class for events that are fired by TradeFlow.
 */
public class TradeFlowEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    public TradeFlowEvent(boolean isAsync) {
        super(isAsync);
    }

    /**
     * For classic spigot.
     *
     * @return The handler list.
     */
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    /**
     * For other server types.
     */
    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

}
