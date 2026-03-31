package com.github.lye.redis.messages;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatMessage {
    private String serverId;
    private long timestamp;
    private double tps;
    private int playerCount;
}
