package com.github.lye.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour TaxRecord — enregistrement de taxe.
 */
@DisplayName("TaxRecord — Enregistrement fiscal")
class TaxRecordTest {

    @Nested
    @DisplayName("Constructeur et getters")
    class ConstructeurGetters {

        @Test
        @DisplayName("Tous les champs sont initialises correctement")
        void tousLesChamps() {
            UUID player = UUID.randomUUID();
            TaxRecord record = new TaxRecord("id-1", player, "TestPlayer",
                    1000.0, 50.0, 0.05, TaxRecord.TaxType.PURCHASE, "DIAMOND");

            assertEquals("id-1", record.getId());
            assertEquals(player, record.getPlayerUuid());
            assertEquals("TestPlayer", record.getPlayerName());
            assertEquals(1000.0, record.getTransactionAmount(), 0.001);
            assertEquals(50.0, record.getTaxAmount(), 0.001);
            assertEquals(0.05, record.getTaxRate(), 0.001);
            assertEquals(TaxRecord.TaxType.PURCHASE, record.getTaxType());
            assertEquals("DIAMOND", record.getShopName());
            assertTrue(record.getTimestamp() > 0);
        }

        @Test
        @DisplayName("TaxType a 5 valeurs")
        void taxTypeCinqValeurs() {
            assertEquals(5, TaxRecord.TaxType.values().length);
        }

        @Test
        @DisplayName("TaxType PURCHASE existe")
        void taxTypePurchase() {
            assertNotNull(TaxRecord.TaxType.valueOf("PURCHASE"));
        }

        @Test
        @DisplayName("TaxType SALE existe")
        void taxTypeSale() {
            assertNotNull(TaxRecord.TaxType.valueOf("SALE"));
        }

        @Test
        @DisplayName("TaxType LARGE_TRANSACTION existe")
        void taxTypeLargeTransaction() {
            assertNotNull(TaxRecord.TaxType.valueOf("LARGE_TRANSACTION"));
        }

        @Test
        @DisplayName("TaxType PROGRESSIVE existe")
        void taxTypeProgressive() {
            assertNotNull(TaxRecord.TaxType.valueOf("PROGRESSIVE"));
        }

        @Test
        @DisplayName("TaxType EXEMPT existe")
        void taxTypeExempt() {
            assertNotNull(TaxRecord.TaxType.valueOf("EXEMPT"));
        }
    }

    @Nested
    @DisplayName("Factory create()")
    class FactoryCreate {

        @Test
        @DisplayName("create() genere un ID unique")
        void createGenereId() {
            TaxRecord r1 = TaxRecord.create(UUID.randomUUID(), "p1", 100.0, 5.0, 0.05, TaxRecord.TaxType.SALE, "GOLD");
            TaxRecord r2 = TaxRecord.create(UUID.randomUUID(), "p2", 200.0, 10.0, 0.05, TaxRecord.TaxType.PURCHASE, "IRON");

            assertNotNull(r1.getId());
            assertNotNull(r2.getId());
            assertNotEquals(r1.getId(), r2.getId());
        }

        @Test
        @DisplayName("create() initialise le timestamp")
        void createTimestamp() {
            TaxRecord record = TaxRecord.create(UUID.randomUUID(), "p", 100.0, 5.0, 0.05, TaxRecord.TaxType.SALE, "DIAMOND");
            assertTrue(record.getTimestamp() > 0);
            assertTrue(record.getTimestamp() <= System.currentTimeMillis());
        }

        @Test
        @DisplayName("create() copie tous les parametres")
        void createCopieParametres() {
            UUID player = UUID.randomUUID();
            TaxRecord record = TaxRecord.create(player, "Player1", 500.0, 25.0, 0.05, TaxRecord.TaxType.PROGRESSIVE, "EMERALD");

            assertEquals(player, record.getPlayerUuid());
            assertEquals("Player1", record.getPlayerName());
            assertEquals(500.0, record.getTransactionAmount(), 0.001);
            assertEquals(25.0, record.getTaxAmount(), 0.001);
            assertEquals(0.05, record.getTaxRate(), 0.001);
            assertEquals(TaxRecord.TaxType.PROGRESSIVE, record.getTaxType());
            assertEquals("EMERALD", record.getShopName());
        }
    }
}
