package com.github.lye.redis.messages;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PriceUpdateMessage {
    private String item;
    private double price;
    
    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
}
