# Bank Sampah Digital

Aplikasi desktop petugas dengan Java 17+, Swing/FlatLaf, dan SQLite. Implementasi mengikuti `SPESIFIKASI_PROYEK.md`.

## Menjalankan

Pasang JDK 17+. Ada dua cara mudah untuk menjalankan aplikasi:

### Cara 1: Tinggal Klik (Windows)
Double-click file **`MULAI.bat`** di direktori proyek.

### Cara 2: Pakai File JAR
Atau jalankan perintah ini langsung:
```sh
java -jar target/bank-sampah-1.0.0.jar
```

Jika ingin kompilasi ulang dan menjalankan tes:
```sh
mvn test
mvn package
```

Saat pertama dijalankan, database `banksampah.db` dibuat di direktori kerja. Lokasi bisa diubah dengan properti JVM `-Dbanksampah.db.path=lokasi/database.db` (misalnya saat menjalankan Java secara langsung). Akun awal: **admin / admin123**. Ganti kata sandi default sebelum memakai aplikasi dengan data nyata; hash SHA-256 tanpa salt sesuai spesifikasi, bukan metode penyimpanan kata sandi yang direkomendasikan untuk produksi.

Di tab Setor sampah, pilih kategori pada tabel tarif lalu tekan **Ubah tarif** untuk mengatur tarif poin/kg ketiga kategori bawaan. Tarif yang baru hanya berlaku untuk setoran berikutnya; tarif pada transaksi lama tetap tersimpan sebagai snapshot.

Riwayat setor/tarik tidak bisa diedit atau dihapus. Setoran menambahkan poin bulat dan menyimpan receh dua desimal. Penarikan hanya berlaku untuk poin bulat (minimum 1.000, kelipatan 100). Ekspor PDF menggunakan filter riwayat yang sedang dipilih. File database serta PDF keluaran diabaikan oleh `.gitignore`.

SQLite memakai kolom `TEXT` untuk berat dan pecahan poin agar digit desimal tidak berubah menjadi `REAL` akibat *numeric affinity* SQLite. Semua nilai dibaca dan dihitung sebagai `BigDecimal`; saldo poin bulat dan Rupiah tetap `INTEGER`. Perubahan ini disengaja untuk menjaga presisi di luar janji `DECIMAL` SQLite.
