package com.github.lye.redis.messages;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StockUpdateMessage {
    private String item;
    private int delta;
    
    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }
    public int getDelta() { return delta; }
    public void setDelta(int delta) { this.delta = delta; }
}
