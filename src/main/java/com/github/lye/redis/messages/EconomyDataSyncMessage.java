package com.github.lye.redis.messages;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EconomyDataSyncMessage {
    private String key;
    private double[] value;
}
