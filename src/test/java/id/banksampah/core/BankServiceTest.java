package id.banksampah.core;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BankServiceTest {
    @TempDir Path dir;

    @AfterEach void logout() { AdminSession.getInstance().logout(); }

    private BankService service() { return new BankService(dir.resolve("bank.db").toString()); }

    @Test void seedLoginAndMasterData() {
        BankService bank = service();
        assertEquals(3, bank.listKategori().size());
        assertThrows(IllegalArgumentException.class, () -> bank.login("admin", "wrong".toCharArray()));
        assertEquals("Administrator Utama", bank.login("admin", "admin123".toCharArray()).nama());
        assertEquals("admin", AdminSession.getInstance().getAdmin().username());
        assertEquals(3, service().listKategori().size());
        try (Connection conn = new Database(dir.resolve("bank.db").toString()).open();
             Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("PRAGMA foreign_keys")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        } catch (java.sql.SQLException e) {
            fail(e);
        }
    }

    @Test void depositCarriesFractionAndPersistsExactText() throws Exception {
        BankService bank = service();
        int admin = bank.login("admin", "admin123".toCharArray()).id();
        int warga = bank.addWarga("1234567890123456", "Aji", "081234567890").id();
        BankService.Preview preview = bank.previewSetor(warga, "KERTAS", "0.001");
        assertEquals(new BigDecimal("0.15"), preview.poinDidapat());
        assertEquals(0, preview.tambahanPoin());
        for (int i = 0; i < 7; i++) bank.setor(admin, warga, "KERTAS", "0.001");
        BankService.Warga balance = bank.listWarga("Aji").get(0);
        assertEquals(1, balance.saldoPoin());
        assertEquals(new BigDecimal("0.05"), balance.poinReceh());
        assertEquals(7, bank.history("1234567890123456", "SETOR").size());
        assertTrue(bank.auditSaldo(warga));
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("bank.db"));
             Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT typeof(poin_receh), poin_receh, (SELECT typeof(poin_didapat) FROM transaksi_setor LIMIT 1) FROM warga")) {
            assertTrue(rs.next());
            assertEquals("text", rs.getString(1));
            assertEquals("0.05", rs.getString(2));
            assertEquals("text", rs.getString(3));
        }
    }

    @Test void withdrawIsAtomicAndHistoryIsAppendOnly() throws Exception {
        BankService bank = service();
        int admin = bank.login("admin", "admin123".toCharArray()).id();
        int warga = bank.addWarga("1111111111111111", "Budi", "081234567890").id();
        bank.setor(admin, warga, "PLASTIK", "4");
        assertThrows(IllegalArgumentException.class, () -> bank.tarik(admin, warga, 1300, "CASH"));
        assertThrows(IllegalArgumentException.class, () -> bank.tarik(admin, warga, 1000, "OTHER"));
        assertEquals(0, bank.history(null, "TARIK").size());
        bank.tarik(admin, warga, 1000, "E-WALLET");
        assertEquals(200, bank.listWarga(null).get(0).saldoPoin());
        assertEquals("10000", bank.history(null, "TARIK").get(0).rupiah());
        assertThrows(IllegalStateException.class, () -> bank.deleteWarga(warga));
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("bank.db"));
             Statement s = conn.createStatement()) {
            assertThrows(java.sql.SQLException.class, () -> s.executeUpdate("DELETE FROM transaksi_tarik"));
            assertThrows(java.sql.SQLException.class, () -> s.executeUpdate("UPDATE transaksi_setor SET tarif_per_kg = 1"));
        }
        Path pdf = dir.resolve("report.pdf");
        bank.exportPdf(pdf, "1111111111111111", "SEMUA");
        assertTrue(Files.size(pdf) > 500);
        assertArrayEquals(new byte[] {'%', 'P', 'D', 'F'}, java.util.Arrays.copyOf(Files.readAllBytes(pdf), 4));
    }

    @Test void concurrentWithdrawalsCannotOverdraw() throws Exception {
        BankService bank = service();
        int admin = bank.login("admin", "admin123".toCharArray()).id();
        int warga = bank.addWarga("4444444444444444", "Dewi", "081234567890").id();
        bank.setor(admin, warga, "LOGAM", "2");
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> withdraw = () -> {
                start.await();
                try { bank.tarik(admin, warga, 1000, "CASH"); return true; }
                catch (IllegalArgumentException | IllegalStateException rejected) { return false; }
            };
            var first = executor.submit(withdraw);
            var second = executor.submit(withdraw);
            start.countDown();
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        } finally {
            executor.shutdownNow();
        }
        assertEquals(0, bank.listWarga(null).get(0).saldoPoin());
        assertEquals(1, bank.history(null, "TARIK").size());
        assertTrue(bank.auditSaldo(warga));
    }

    @Test void auditIgnoresFailedWithdrawalsAndDetectsMismatch() throws Exception {
        BankService bank = service();
        int admin = bank.login("admin", "admin123".toCharArray()).id();
        int warga = bank.addWarga("5555555555555555", "Warga Audit", "081234567890").id();
        bank.setor(admin, warga, "PLASTIK", "4");
        try (Connection conn = new Database(dir.resolve("bank.db").toString()).open();
             Statement statement = conn.createStatement()) {
            statement.executeUpdate("INSERT INTO transaksi_tarik(id_warga,id_admin,poin_ditarik,nominal_rupiah,jenis_penukaran,status) "
                    + "VALUES(" + warga + "," + admin + ",1000,10000,'CASH','FAILED')");
            assertTrue(bank.auditSaldo(warga));
            statement.executeUpdate("UPDATE warga SET saldo_poin = 1199 WHERE id_warga = " + warga);
            assertFalse(bank.auditSaldo(warga));
        }
    }

    @Test void tarifChangeAffectsOnlyFutureDeposits() {
        BankService bank = service();
        int admin = bank.login("admin", "admin123".toCharArray()).id();
        int warga = bank.addWarga("6666666666666666", "Warga Tarif", "081234567890").id();
        bank.setor(admin, warga, "PLASTIK", "1");
        assertThrows(IllegalArgumentException.class, () -> bank.updateTarifKategori(admin, "PLASTIK", 0));
        assertThrows(IllegalArgumentException.class, () -> bank.updateTarifKategori(admin, "LAIN", 450));
        bank.updateTarifKategori(admin, "PLASTIK", 450);
        assertEquals(450, bank.listKategori().stream().filter(k -> k.id().equals("PLASTIK")).findFirst().orElseThrow().tarif());
        assertEquals(new BigDecimal("450.00"), bank.previewSetor(warga, "PLASTIK", "1").poinDidapat());
        bank.setor(admin, warga, "PLASTIK", "1");
        assertEquals(750, bank.listWarga("").get(0).saldoPoin());
        var history = bank.history("6666666666666666", "SETOR");
        assertEquals(2, history.size());
        assertTrue(history.stream().anyMatch(row -> row.detail().contains("300 poin/kg") && row.poin().equals("300.00")));
        assertTrue(history.stream().anyMatch(row -> row.detail().contains("450 poin/kg") && row.poin().equals("450.00")));
        assertTrue(bank.auditSaldo(warga));
        AdminSession.getInstance().logout();
        assertThrows(IllegalArgumentException.class, () -> bank.updateTarifKategori(admin, "PLASTIK", 600));
        assertEquals(450, bank.listKategori().stream().filter(k -> k.id().equals("PLASTIK")).findFirst().orElseThrow().tarif());
    }

    @Test void masterCrudAndValidation() {
        BankService bank = service();
        BankService.Warga warga = bank.addWarga("2222222222222222", "Siti", "081234567890");
        assertThrows(IllegalStateException.class, () -> bank.addWarga(warga.nik(), "Other", warga.hp()));
        bank.updateWarga(warga.id(), "3333333333333333", "Siti Baru", "+6281234567890");
        assertEquals(1, bank.listWarga("Baru").size());
        assertTrue(bank.listWarga("2222222222222222").isEmpty());
        bank.deleteWarga(warga.id());
        assertTrue(bank.listWarga(null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> bank.previewSetor(warga.id(), "LOGAM", "1"));
    }
}
