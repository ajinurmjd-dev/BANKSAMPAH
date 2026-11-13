package id.banksampah.core;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BankService {
    public record Admin(int id, String username, String nama) { }
    public record Warga(int id, String nik, String nama, String hp, int saldoPoin, BigDecimal poinReceh) { }
    public record Kategori(String id, String nama, int tarif) { }
    public record Preview(BigDecimal berat, BigDecimal poinDidapat, int tambahanPoin, BigDecimal recehAkhir) { }
    public record History(String tanggal, String jenis, String nik, String nama, String kategori, String detail, String poin, String rupiah, String status) { }

    private final Database db;

    public BankService(String dbPath) {
        db = new Database(dbPath);
    }

    private interface DbWork<T> { T run(Connection conn) throws SQLException; }

    private <T> T read(DbWork<T> work) {
        try (Connection conn = db.open()) {
            return work.run(conn);
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    private void transact(DbWork<Void> work) {
        try (Connection conn = db.open()) {
            conn.setAutoCommit(false);
            try {
                work.run(conn);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                try { conn.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof SQLException sql) throw databaseError(sql);
                throw e;
            }
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    private static IllegalStateException databaseError(SQLException e) {
        return new IllegalStateException("Operasi database gagal: " + e.getMessage(), e);
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(name + " wajib diisi (maksimal " + max + " karakter)");
        }
        return value.trim();
    }

    private static void validateWarga(String nik, String nama, String hp) {
        required(nik, "NIK", 20);
        required(nama, "Nama", 100);
        if (!required(hp, "Nomor HP", 15).matches("\\+?[0-9]{8,15}")) {
            throw new IllegalArgumentException("Nomor HP harus berisi 8-15 digit (boleh diawali +)");
        }
    }

    public Admin login(String username, char[] password) {
        String name = required(username, "Username", 50);
        if (password == null) throw new IllegalArgumentException("Password salah");
        return read(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT id_admin, username, nama_admin, password_hash FROM admin WHERE username = ?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    byte[] actual = Database.sha256(new String(password).getBytes(StandardCharsets.UTF_8)).getBytes(StandardCharsets.US_ASCII);
                    if (!rs.next() || !MessageDigest.isEqual(actual, rs.getString("password_hash").getBytes(StandardCharsets.US_ASCII))) {
                        throw new IllegalArgumentException("Username atau password salah");
                    }
                    Admin admin = new Admin(rs.getInt("id_admin"), rs.getString("username"), rs.getString("nama_admin"));
                    AdminSession.getInstance().login(admin);
                    return admin;
                }
            }
        });
    }

    private static Warga mapWarga(ResultSet rs) throws SQLException {
        return new Warga(rs.getInt("id_warga"), rs.getString("nik"), rs.getString("nama"), rs.getString("no_hp"), rs.getInt("saldo_poin"), new BigDecimal(rs.getString("poin_receh")).setScale(2));
    }

    private static Warga findWarga(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM warga WHERE id_warga = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Warga tidak ditemukan");
                return mapWarga(rs);
            }
        }
    }

    public List<Warga> listWarga(String query) {
        return read(conn -> {
            List<Warga> result = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM warga WHERE nik LIKE ? ESCAPE '\\' OR nama LIKE ? ESCAPE '\\' ORDER BY nama, id_warga")) {
                String q = query == null ? "" : query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                ps.setString(1, "%" + q + "%");
                ps.setString(2, "%" + q + "%");
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(mapWarga(rs)); }
            }
            return result;
        });
    }

    public Warga addWarga(String nik, String nama, String hp) {
        validateWarga(nik, nama, hp);
        return read(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO warga(nik, nama, no_hp, poin_receh) VALUES(?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, nik.trim()); ps.setString(2, nama.trim()); ps.setString(3, hp.trim()); ps.setString(4, "0.00");
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("ID warga tidak tersedia");
                    return findWarga(conn, keys.getInt(1));
                }
            }
        });
    }

    public void updateWarga(int id, String nik, String nama, String hp) {
        validateWarga(nik, nama, hp);
        read(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE warga SET nik = ?, nama = ?, no_hp = ? WHERE id_warga = ?")) {
                ps.setString(1, nik.trim()); ps.setString(2, nama.trim()); ps.setString(3, hp.trim()); ps.setInt(4, id);
                if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Warga tidak ditemukan");
            }
            return null;
        });
    }

    public void deleteWarga(int id) {
        try (Connection conn = db.open(); PreparedStatement ps = conn.prepareStatement("DELETE FROM warga WHERE id_warga = ?")) {
            ps.setInt(1, id);
            if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Warga tidak ditemukan");
        } catch (SQLException e) {
            if (e.getErrorCode() == 19 && e.getMessage().toLowerCase(Locale.ROOT).contains("foreign key")) {
                throw new IllegalStateException("Warga tidak dapat dihapus karena memiliki riwayat transaksi", e);
            }
            throw databaseError(e);
        }
    }

    public List<Kategori> listKategori() {
        return read(conn -> {
            List<Kategori> result = new ArrayList<>();
            try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM master_kategori ORDER BY id_kategori")) {
                while (rs.next()) result.add(new Kategori(rs.getString("id_kategori"), rs.getString("nama_kategori"), rs.getInt("tarif_poin_per_kg")));
            }
            return result;
        });
    }

    public void updateTarifKategori(int adminId, String kategoriId, int tarif) {
        if (tarif <= 0) throw new IllegalArgumentException("Tarif harus lebih dari nol");
        if (!"PLASTIK".equals(kategoriId) && !"KERTAS".equals(kategoriId) && !"LOGAM".equals(kategoriId)) {
            throw new IllegalArgumentException("Kategori tidak didukung");
        }
        read(conn -> {
            checkAdmin(conn, adminId);
            try (PreparedStatement ps = conn.prepareStatement("UPDATE master_kategori SET tarif_poin_per_kg = ? WHERE id_kategori = ?")) {
                ps.setInt(1, tarif);
                ps.setString(2, kategoriId);
                if (ps.executeUpdate() != 1) throw new IllegalArgumentException("Kategori tidak ditemukan");
            }
            return null;
        });
    }

    private static KategoriSampah category(Connection conn, String id, BigDecimal berat) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT tarif_poin_per_kg FROM master_kategori WHERE id_kategori = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Kategori tidak ditemukan");
                int tarif = rs.getInt(1);
                return switch (id) {
                    case "PLASTIK" -> new SampahPlastik(berat, tarif);
                    case "KERTAS" -> new SampahKertas(berat, tarif);
                    case "LOGAM" -> new SampahLogam(berat, tarif);
                    default -> throw new IllegalArgumentException("Kategori tidak didukung");
                };
            }
        }
    }

    private static BigDecimal weight(String berat) {
        try {
            return new BigDecimal(required(berat, "Berat", 30)).setScale(3, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Berat harus berupa angka desimal", e);
        }
    }

    private static Preview calculate(Warga warga, KategoriSampah category) {
        BigDecimal earned = category.hitungPoin();
        BigDecimal total = warga.poinReceh().add(earned);
        int whole = total.setScale(0, RoundingMode.DOWN).intValueExact();
        Math.addExact(warga.saldoPoin(), whole);
        return new Preview(category.getBeratKg(), earned, whole, total.subtract(BigDecimal.valueOf(whole)).setScale(2));
    }

    public Preview previewSetor(int wargaId, String kategoriId, String berat) {
        BigDecimal kg = weight(berat);
        return read(conn -> calculate(findWarga(conn, wargaId), category(conn, kategoriId, kg)));
    }

    private static void checkAdmin(Connection conn, int adminId) throws SQLException {
        Admin active = AdminSession.getInstance().getAdmin();
        if (active == null || active.id() != adminId) throw new IllegalArgumentException("Admin harus login sebelum transaksi");
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM admin WHERE id_admin = ? AND username = ?")) {
            ps.setInt(1, adminId); ps.setString(2, active.username());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Admin tidak ditemukan");
            }
        }
    }

    public void setor(int adminId, int wargaId, String kategoriId, String berat) {
        BigDecimal kg = weight(berat);
        transact(conn -> {
            checkAdmin(conn, adminId);
            Warga warga = findWarga(conn, wargaId);
            KategoriSampah kategori = category(conn, kategoriId, kg);
            Preview preview = calculate(warga, kategori);
            try (PreparedStatement ps = conn.prepareStatement("UPDATE warga SET saldo_poin = ?, poin_receh = ? WHERE id_warga = ? AND saldo_poin = ? AND poin_receh = ?")) {
                ps.setInt(1, Math.addExact(warga.saldoPoin(), preview.tambahanPoin()));
                ps.setString(2, preview.recehAkhir().toPlainString());
                ps.setInt(3, wargaId); ps.setInt(4, warga.saldoPoin()); ps.setString(5, warga.poinReceh().toPlainString());
                if (ps.executeUpdate() != 1) throw new IllegalStateException("Saldo berubah, silakan ulangi transaksi");
            }
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO transaksi_setor(id_warga, id_admin, kategori, berat_kg, tarif_per_kg, poin_didapat) VALUES(?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, wargaId); ps.setInt(2, adminId); ps.setString(3, kategori.getNamaKategori());
                ps.setString(4, preview.berat().toPlainString()); ps.setInt(5, kategori.getTarifPoinPerKg());
                ps.setString(6, preview.poinDidapat().toPlainString()); ps.executeUpdate();
            }
            return null;
        });
    }

    public void tarik(int adminId, int wargaId, int poin, String metode) {
        if (poin < 1000 || poin % 100 != 0) throw new IllegalArgumentException("Penarikan minimal 1.000 poin dan kelipatan 100");
        if (!"CASH".equals(metode) && !"E-WALLET".equals(metode)) throw new IllegalArgumentException("Metode harus CASH atau E-WALLET");
        int rupiah = Math.multiplyExact(poin, 10);
        transact(conn -> {
            checkAdmin(conn, adminId);
            Warga warga = findWarga(conn, wargaId);
            if (warga.saldoPoin() < poin) throw new IllegalArgumentException("Saldo poin tidak mencukupi");
            try (PreparedStatement ps = conn.prepareStatement("UPDATE warga SET saldo_poin = saldo_poin - ? WHERE id_warga = ? AND saldo_poin >= ?")) {
                ps.setInt(1, poin); ps.setInt(2, wargaId); ps.setInt(3, poin);
                if (ps.executeUpdate() != 1) throw new IllegalStateException("Saldo berubah, silakan ulangi transaksi");
            }
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO transaksi_tarik(id_warga, id_admin, poin_ditarik, nominal_rupiah, jenis_penukaran, status) VALUES(?, ?, ?, ?, ?, 'SUCCESS')")) {
                ps.setInt(1, wargaId); ps.setInt(2, adminId); ps.setInt(3, poin); ps.setInt(4, rupiah); ps.setString(5, metode);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public boolean auditSaldo(int wargaId) {
        return read(conn -> {
            Warga warga = findWarga(conn, wargaId);
            BigDecimal transaksi = BigDecimal.ZERO;
            try (PreparedStatement ps = conn.prepareStatement("SELECT poin_didapat FROM transaksi_setor WHERE id_warga = ?")) {
                ps.setInt(1, wargaId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) transaksi = transaksi.add(new BigDecimal(rs.getString(1)));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT poin_ditarik FROM transaksi_tarik WHERE id_warga = ? AND status = 'SUCCESS'")) {
                ps.setInt(1, wargaId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) transaksi = transaksi.subtract(BigDecimal.valueOf(rs.getLong(1)));
                }
            }
            return transaksi.compareTo(BigDecimal.valueOf(warga.saldoPoin()).add(warga.poinReceh())) == 0;
        });
    }

    private static String historyType(String tipe) {
        if (tipe == null || (!tipe.equals("SEMUA") && !tipe.equals("SETOR") && !tipe.equals("TARIK"))) {
            throw new IllegalArgumentException("Tipe harus SEMUA, SETOR, atau TARIK");
        }
        return tipe;
    }

    public List<History> history(String nik, String tipe) {
        String type = historyType(tipe);
        return read(conn -> {
            List<History> result = new ArrayList<>();
            String sql = "SELECT s.tanggal, 'SETOR' jenis, w.nik, w.nama, s.kategori, s.berat_kg || ' kg x ' || s.tarif_per_kg || ' poin/kg' detail, s.poin_didapat poin, '' rupiah, 'SUCCESS' status, s.id_setor nomor FROM transaksi_setor s JOIN warga w ON w.id_warga = s.id_warga WHERE (? = '' OR w.nik = ?) AND (? = 'SEMUA' OR ? = 'SETOR') UNION ALL "
                    + "SELECT t.tanggal, 'TARIK', w.nik, w.nama, '', t.jenis_penukaran, CAST(t.poin_ditarik AS TEXT), CAST(t.nominal_rupiah AS TEXT), t.status, t.id_tarik FROM transaksi_tarik t JOIN warga w ON w.id_warga = t.id_warga WHERE (? = '' OR w.nik = ?) AND (? = 'SEMUA' OR ? = 'TARIK') ORDER BY tanggal DESC, jenis, nomor DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                String search = nik == null ? "" : nik.trim();
                ps.setString(1, search); ps.setString(2, search); ps.setString(3, type); ps.setString(4, type);
                ps.setString(5, search); ps.setString(6, search); ps.setString(7, type); ps.setString(8, type);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(new History(rs.getString("tanggal"), rs.getString("jenis"), rs.getString("nik"), rs.getString("nama"), rs.getString("kategori"), rs.getString("detail"), rs.getString("poin"), rs.getString("rupiah"), rs.getString("status")));
                }
            }
            return result;
        });
    }

    public void exportPdf(Path file, String nik, String tipe) {
        if (file == null) throw new IllegalArgumentException("Lokasi PDF wajib diisi");
        String search = nik == null ? "" : nik.trim();
        List<History> rows = history(search, tipe);
        List<Warga> balances = read(conn -> {
            List<Warga> result = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM warga WHERE (? = '' OR nik = ?) ORDER BY nama, id_warga")) {
                ps.setString(1, search); ps.setString(2, search);
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(mapWarga(rs)); }
            }
            return result;
        });
        Document document = new Document();
        try (OutputStream out = Files.newOutputStream(file)) {
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(new Paragraph("Laporan Bank Sampah"));
            document.add(new Paragraph("Filter NIK: " + (search.isEmpty() ? "Semua" : search) + " | Transaksi: " + tipe));
            document.add(new Paragraph("Saldo warga (poin bulat + receh):"));
            PdfPTable saldoTable = new PdfPTable(4);
            for (String title : new String[] {"NIK", "Nama", "Poin", "Receh"}) saldoTable.addCell(new Phrase(title));
            for (Warga w : balances) {
                saldoTable.addCell(w.nik()); saldoTable.addCell(w.nama());
                saldoTable.addCell(Integer.toString(w.saldoPoin())); saldoTable.addCell(w.poinReceh().toPlainString());
            }
            document.add(saldoTable);
            document.add(new Paragraph("Riwayat transaksi:"));
            PdfPTable table = new PdfPTable(9);
            table.setWidthPercentage(100);
            for (String title : new String[] {"Tanggal", "Jenis", "NIK", "Nama", "Kategori", "Detail", "Poin", "Rupiah", "Status"}) table.addCell(new Phrase(title));
            for (History row : rows) {
                for (String cell : new String[] {row.tanggal(), row.jenis(), row.nik(), row.nama(), row.kategori(), row.detail(), row.poin(), row.rupiah(), row.status()}) table.addCell(cell);
            }
            document.add(table);
            document.close();
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("Gagal mengekspor PDF: " + e.getMessage(), e);
        } finally {
            document.close();
        }
    }
}
