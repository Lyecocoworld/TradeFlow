package com.github.lye.redis.messages;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EventUpdateMessage {
    private String serverId;
    private String eventName;
    private String state; // "started" or "ended"
    private long endTimeTimestamp; // epoch millis when event ends (0 for "ended")
    private int durationTicks;    // duration in ticks (for remote replay)
    private String display;
    private String startMessage;
    private String endMessage;

    public EventUpdateMessage(String serverId, String eventName, String state,
                              long endTimeTimestamp, int durationTicks,
                              String display, String startMessage, String endMessage) {
        this.serverId = serverId;
        this.eventName = eventName;
        this.state = state;
        this.endTimeTimestamp = endTimeTimestamp;
        this.durationTicks = durationTicks;
        this.display = display;
        this.startMessage = startMessage;
        this.endMessage = endMessage;
    }
}
