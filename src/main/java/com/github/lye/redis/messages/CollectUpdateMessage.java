package com.github.lye.redis.messages;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
public class CollectUpdateMessage {
    private String type; // "PLAYER" or "SERVER"
    private String item;
    private UUID uuid; // Optional, for PLAYER type
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }
    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }
}
