# Spesifikasi Teknis: Sistem Bank Sampah Digital & Daur Ulang (Admin-Centric)

## 1. Ringkasan Arsitektur & Teknologi
- **Bahasa & Platform:** Java Desktop, Java Swing dengan library tema UI `FlatLaf`.
- **Database:** SQLite / MySQL via JDBC dengan manajemen koneksi Singleton pattern.
  - **Catatan SQLite:** Wajib menjalankan query `PRAGMA foreign_keys = ON;` segera setelah koneksi dibuka untuk mengaktifkan validasi *Foreign Key* yang secara default nonaktif di SQLite.
- **Penerapan PBO:** Memenuhi 4 pilar utama secara eksplisit (Encapsulation, Inheritance, Polymorphism, Abstraction).
- **Security Hashing:** SHA-256 via `java.security.MessageDigest` bawaan JDK (tanpa dependency luar).

---

## 2. Penerapan 4 Pilar PBO & Standarisasi Tipe Data
- **Abstraction:** 
  - `abstract class KategoriSampah` dengan atribut `protected BigDecimal beratKg` dan `protected int tarifPoinPerKg`. Memiliki abstract method `public abstract String getNamaKategori();` serta method konkrit `public int getTarifPoinPerKg()` dan kalkulasi poin.
- **Inheritance & Polymorphism:** 
  - `SampahPlastik extends KategoriSampah` -> Tarif default 300 Poin/kg.
  - `SampahKertas extends KategoriSampah` -> Tarif default 150 Poin/kg.
  - `SampahLogam extends KategoriSampah` -> Tarif default 500 Poin/kg.
  - Constructor tiap subclass mendukung parameter tarif dinamis dari database (`master_kategori`) dengan nilai konstanta default sebagai *fallback*:
    `public SampahPlastik(BigDecimal beratKg, Integer tarifDb) { super(beratKg, tarifDb != null ? tarifDb : DEFAULT_TARIF); }`
- **Encapsulation & Presisi (`BigDecimal`):** 
  - Seluruh atribut model bersifat `private` dan diakses melalui getter/setter ber-validasi.
  - Aturan mutlak: Dilarang memakai `float`/`double` untuk nilai uang, berat, maupun poin.
  - Berat kg: `BigDecimal` (skala 3 desimal / gram presisi, RoundingMode.HALF_UP).
  - Poin & Receh: `BigDecimal` (skala 2 desimal).
  - Inisialisasi wajib via String: `new BigDecimal(inputStr.trim())` atau `BigDecimal.valueOf(longVal)`.
  - **Mapping SQLite Persistence:** Karena SQLite menyimpan kolom bertipe `DECIMAL` sebagai `REAL` di balik layar, simpan nilai `BigDecimal` ke database menggunakan `ps.setString(index, value.toPlainString())` dan baca kembali menggunakan `new BigDecimal(rs.getString("kolom"))` guna menjaga presisi mutlak tanpa floating-point error.

---

## 3. Logika Moneter, Konversi, & Solusi "Celengan Receh"
- **Definisi Satuan:**
  - `tarifPoinPerKg`: Satuan murni **POIN** (bukan Rupiah).
  - Fixed Pegging Kurs Kasir: **`1 Poin = Rp 10`** (dihitung hanya saat penarikan dana).
- **Struktur Akun Warga:**
  - `saldoPoin` (`int`): Saldo poin bulat yang siap dicairkan.
  - `poinReceh` (`BigDecimal(10,2)`): Penampung sisa poin pecahan `< 1.00`.
- **Mekanisme Akumulasi Setoran:**
  1. `poinDidapat = beratKg.multiply(BigDecimal.valueOf(tarifPoinPerKg)).setScale(2, RoundingMode.HALF_UP)` (wajib dikunci 2 desimal sejak perkalian pertama agar konsisten dengan tipe `DECIMAL(10,2)` di DB dan `poinReceh`).
  2. `totalReceh = poinRecehLama.add(poinDidapat)`
  3. `poinBulat = totalReceh.setScale(0, RoundingMode.DOWN)`
  4. `sisaReceh = totalReceh.subtract(poinBulat)`
  5. Saldo ter-update: `saldoPoin += poinBulat.intValueExact()`, `poinReceh = sisaReceh`.
- **Persamaan Akuntansi & Audit:**
  - `poin_didapat` pada tabel setoran wajib bertipe `DECIMAL(10,2)`.
  - Formula audit:
    $$\text{Saldo Berjalan} = \text{saldo\_poin} + \text{poin\_receh}$$
    $$\sum (\text{poin\_didapat}) - \sum (\text{poin\_ditarik WHERE status='SUCCESS'}) = \text{saldo\_poin} + \text{poin\_receh}$$
  - **Filter Audit:** Saat menjalankan pengecekan persamaan audit, bagian `SUM(poin_ditarik)` hanya menghitung baris yang berstatus 'SUCCESS' agar transaksi gagal tidak ikut mengurangi saldo audit.
- **Aturan Penarikan Saldo:**
  - Hanya `saldoPoin` bulat yang dapat ditarik. `poinReceh` tidak dapat ditarik sampai terakumulasi menjadi integer `>= 1`.
  - **Threshold Penarikan Pasti:**
    - Minimal penarikan: **1.000 Poin** (= Rp 10.000).
    - Kelipatan penarikan: **100 Poin** (= Rp 1.000).

---

## 4. Keamanan, Alur Sesi, & Integritas Transaksi
- **Autentikasi Admin:**
  - Dialog Form Login di awal aplikasi (Username & Password).
  - Verifikasi password mencocokkan hash SHA-256 di database.
  - Sesi aktif disimpan dalam singleton `AdminSession` (`id_admin`, `username`, `nama_admin`).
- **Audit Trail & Immutable History (Read-Only Records):**
  - Setiap mutasi setor dan tarik wajib mencatat `id_admin` petugas yang login.
  - Tabel transaksi menyimpan snapshot nilai `tarif_per_kg` pada detik transaksi terjadi.
  - **Prinsip Read-Only:** Tabel `transaksi_setor` dan `transaksi_tarik` bersifat *append-only* (hanya tambah). Operasi **Update & Delete di GUI hanya diizinkan untuk Data Master** (Warga dan Kategori Sampah). Riwayat transaksi tidak boleh diedit/dihapus agar integritas saldo tetap terjaga. Jika ada kesalahan input, harus diselesaikan melalui mekanisme pembatalan/koreksi manual yang mencatat record transaksi baru (bukan hapus record lama).
- **Pencegahan Double-Click & Race Condition:**
  - Tombol aksi GUI (`Setor`, `Tukar`) dinonaktifkan (`setEnabled(false)`) sesaat setelah diklik dan diaktifkan kembali di blok `finally`.
- **ACID Database Transaction:**
  - Seluruh update saldo dan penulisan log transaksi dieksekusi dalam transaction block:
    `conn.setAutoCommit(false)` -> `conn.commit()` / `conn.rollback()`.

---

## 5. Skema Relasional Database (Dialek SQLite)

```sql
-- 1. Tabel Admin (Petugas)
CREATE TABLE admin (
    id_admin INTEGER PRIMARY KEY AUTOINCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    nama_admin VARCHAR(100) NOT NULL,
    password_hash VARCHAR(64) NOT NULL -- SHA-256 Hex string
);

-- 2. Tabel Master Referensi Kategori Sampah
CREATE TABLE master_kategori (
    id_kategori VARCHAR(20) PRIMARY KEY,
    nama_kategori VARCHAR(50) NOT NULL,
    tarif_poin_per_kg INT NOT NULL
);

-- 3. Tabel Data Warga
CREATE TABLE warga (
    id_warga INTEGER PRIMARY KEY AUTOINCREMENT,
    nik VARCHAR(20) NOT NULL UNIQUE,
    nama VARCHAR(100) NOT NULL,
    no_hp VARCHAR(15) NOT NULL,
    saldo_poin INT NOT NULL DEFAULT 0,
    poin_receh DECIMAL(10,2) NOT NULL DEFAULT 0.00
);

-- 4. Tabel Transaksi Setor Sampah
CREATE TABLE transaksi_setor (
    id_setor INTEGER PRIMARY KEY AUTOINCREMENT,
    id_warga INT NOT NULL,
    id_admin INT NOT NULL,
    kategori VARCHAR(50) NOT NULL,
    berat_kg DECIMAL(10,3) NOT NULL,
    tarif_per_kg INT NOT NULL, -- Immutable snapshot rate
    poin_didapat DECIMAL(10,2) NOT NULL,
    tanggal DATETIME NOT NULL DEFAULT (DATETIME('now', 'localtime')),
    FOREIGN KEY (id_warga) REFERENCES warga(id_warga),
    FOREIGN KEY (id_admin) REFERENCES admin(id_admin)
);

-- 5. Tabel Transaksi Tarik / Tukar Poin
CREATE TABLE transaksi_tarik (
    id_tarik INTEGER PRIMARY KEY AUTOINCREMENT,
    id_warga INT NOT NULL,
    id_admin INT NOT NULL,
    poin_ditarik INT NOT NULL,
    nominal_rupiah INT NOT NULL,
    jenis_penukaran VARCHAR(20) NOT NULL, -- 'CASH' atau 'E-WALLET'
    status VARCHAR(15) NOT NULL DEFAULT 'SUCCESS', -- 'SUCCESS' atau 'FAILED'
    catatan VARCHAR(255),
    tanggal DATETIME NOT NULL DEFAULT (DATETIME('now', 'localtime')),
    FOREIGN KEY (id_warga) REFERENCES warga(id_warga),
    FOREIGN KEY (id_admin) REFERENCES admin(id_admin)
);

-- 6. Auto-Seeding Data Awal (Wajib dijalankan saat inisialisasi DB)
INSERT OR IGNORE INTO admin (id_admin, username, nama_admin, password_hash) 
VALUES (1, 'admin', 'Administrator Utama', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9'); SHA-256 hash dari 'admin123'

INSERT OR IGNORE INTO master_kategori (id_kategori, nama_kategori, tarif_poin_per_kg) VALUES 
('PLASTIK', 'Sampah Plastik', 300),
('KERTAS', 'Sampah Kertas', 150),
('LOGAM', 'Sampah Logam', 500);
```

---

## 6. Rancangan Antarmuka Pengguna (`JTabbedPane` + FlatLaf)
- **Modal Login Dialog:** Input username & password sebelum masuk ke form utama.
- **Header Info:** Menampilkan nama admin yang sedang bertugas dan tombol Logout.
- **Tab 1: Manajemen Data Warga (CRUD)**
  - Panel kiri: Form tambah/edit (NIK, Nama, No HP) dengan validasi regex format nomor.
  - Panel kanan: `JTable` warga (NIK, Nama, No HP, Saldo Poin, Poin Receh), search box real-time, tombol Edit, tombol Hapus.
  - **Handling Hapus Warga:** Method `deleteWarga()` wajib menangkap `SQLException`. Jika terjadi error akibat pelanggaran *Foreign Key* (karena warga sudah memiliki riwayat transaksi), sistem harus menampilkan dialog informasi yang rapi: "Warga tidak dapat dihapus karena memiliki riwayat transaksi".
- **Tab 2: Penimbangan & Setor Sampah**
  - Pemilih Warga: Dropdown / form autocomplete NIK/Nama warga.
  - Dropdown Kategori: Plastik (300 Poin/kg), Kertas (150 Poin/kg), Logam (500 Poin/kg).
  - Form Input Berat: Input desimal kg.
  - Pratinjau Realtime: Kalkulasi poin bulat dan receh yang akan diterima.
  - Tombol Setor Sampah (dilengkapi proteksi disable button saat proses DB).
- **Tab 3: Penukaran Poin & Riwayat Transaksi**
  - Form Tarik Saldo:
    - Input poin (validasi: kelipatan 100, min. 1.000, max <= `saldoPoin`).
    - Pilihan metode: Tunai (Cash) / Saldo E-Wallet.
    - Preview konversi Rupiah instan (`poin * 10`).
  - Tabel Riwayat:
    - Log transaksi setor dan tarik terpadu.
    - Filter kategori & pencarian per NIK warga.
  - Tombol **Export Laporan PDF**: Rekapitulasi mutasi dan saldo warga untuk laporan RT/RW.
