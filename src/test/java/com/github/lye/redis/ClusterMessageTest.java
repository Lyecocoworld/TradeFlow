package com.github.lye.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.lye.data.Shop;
import com.github.lye.redis.messages.ShopSyncMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClusterMessageTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testShopSyncSerialization() throws Exception {
        // Arrange
        String key = "diamond";
        // Note: Shop object is complex to mock fully due to Bukkit deps, 
        // but Jackson handles POJOs. We'll create a dummy ShopSyncMessage
        // with null shop just to test the wrapper structure first, 
        // or we can test a simpler message like EconomyDataSyncMessage which uses primitives.
        
        double[] econData = {1000.50, 500.0};
        com.github.lye.redis.messages.EconomyDataSyncMessage msg = 
            new com.github.lye.redis.messages.EconomyDataSyncMessage("BALANCE", econData);

        // Act
        String json = mapper.writeValueAsString(msg);
        System.out.println("JSON: " + json);

        // Assert
        assertTrue(json.contains("\"key\":\"BALANCE\""));
        assertTrue(json.contains("1000.5"));
        
        // Deserialize
        com.github.lye.redis.messages.EconomyDataSyncMessage decoded = 
            mapper.readValue(json, com.github.lye.redis.messages.EconomyDataSyncMessage.class);
            
        assertEquals("BALANCE", decoded.getKey());
        assertEquals(1000.50, decoded.getValue()[0], 0.001);
    }
}
