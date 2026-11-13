package id.banksampah.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;

final class Database {
    private final String url;

    Database(String dbPath) {
        if (dbPath == null || dbPath.isBlank()) {
            throw new IllegalArgumentException("Lokasi database wajib diisi");
        }
        url = "jdbc:sqlite:" + dbPath;
        try (Connection conn = open(); Statement statement = conn.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS admin (id_admin INTEGER PRIMARY KEY AUTOINCREMENT, username VARCHAR(50) NOT NULL UNIQUE, nama_admin VARCHAR(100) NOT NULL, password_hash VARCHAR(64) NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS master_kategori (id_kategori VARCHAR(20) PRIMARY KEY, nama_kategori VARCHAR(50) NOT NULL, tarif_poin_per_kg INTEGER NOT NULL CHECK(tarif_poin_per_kg > 0))");
            // SQLite DECIMAL has NUMERIC affinity: it can coerce decimal strings to binary REAL.
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS warga (id_warga INTEGER PRIMARY KEY AUTOINCREMENT, nik VARCHAR(20) NOT NULL UNIQUE, nama VARCHAR(100) NOT NULL, no_hp VARCHAR(15) NOT NULL, saldo_poin INTEGER NOT NULL DEFAULT 0 CHECK(saldo_poin >= 0), poin_receh TEXT NOT NULL DEFAULT '0.00')");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS transaksi_setor (id_setor INTEGER PRIMARY KEY AUTOINCREMENT, id_warga INTEGER NOT NULL REFERENCES warga(id_warga), id_admin INTEGER NOT NULL REFERENCES admin(id_admin), kategori VARCHAR(50) NOT NULL, berat_kg TEXT NOT NULL, tarif_per_kg INTEGER NOT NULL, poin_didapat TEXT NOT NULL, tanggal DATETIME NOT NULL DEFAULT (DATETIME('now', 'localtime')))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS transaksi_tarik (id_tarik INTEGER PRIMARY KEY AUTOINCREMENT, id_warga INTEGER NOT NULL REFERENCES warga(id_warga), id_admin INTEGER NOT NULL REFERENCES admin(id_admin), poin_ditarik INTEGER NOT NULL, nominal_rupiah INTEGER NOT NULL, jenis_penukaran VARCHAR(20) NOT NULL, status VARCHAR(15) NOT NULL DEFAULT 'SUCCESS', catatan VARCHAR(255), tanggal DATETIME NOT NULL DEFAULT (DATETIME('now', 'localtime')))");
            statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS setor_no_update BEFORE UPDATE ON transaksi_setor BEGIN SELECT RAISE(ABORT, 'Riwayat tidak dapat diubah'); END");
            statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS setor_no_delete BEFORE DELETE ON transaksi_setor BEGIN SELECT RAISE(ABORT, 'Riwayat tidak dapat dihapus'); END");
            statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS tarik_no_update BEFORE UPDATE ON transaksi_tarik BEGIN SELECT RAISE(ABORT, 'Riwayat tidak dapat diubah'); END");
            statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS tarik_no_delete BEFORE DELETE ON transaksi_tarik BEGIN SELECT RAISE(ABORT, 'Riwayat tidak dapat dihapus'); END");
            try (PreparedStatement seed = conn.prepareStatement("INSERT OR IGNORE INTO admin(id_admin, username, nama_admin, password_hash) VALUES(1, 'admin', 'Administrator Utama', ?)")) {
                seed.setString(1, sha256("admin123".getBytes(StandardCharsets.UTF_8)));
                seed.executeUpdate();
            }
            statement.executeUpdate("INSERT OR IGNORE INTO master_kategori VALUES('PLASTIK', 'Sampah Plastik', 300), ('KERTAS', 'Sampah Kertas', 150), ('LOGAM', 'Sampah Logam', 500)");
        } catch (SQLException e) {
            throw new IllegalStateException("Gagal menginisialisasi database: " + e.getMessage(), e);
        }
    }

    Connection open() throws SQLException {
        Connection conn = DriverManager.getConnection(url);
        try (Statement statement = conn.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        } catch (SQLException e) {
            conn.close();
            throw e;
        }
        return conn;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 tidak tersedia", e);
        }
    }
}
