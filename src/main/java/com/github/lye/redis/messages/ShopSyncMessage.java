package com.github.lye.redis.messages;

import com.github.lye.data.Shop;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShopSyncMessage {
    private String key;
    private Shop shop; // Using Shop object directly requires Jackson configuration or careful handling
}
