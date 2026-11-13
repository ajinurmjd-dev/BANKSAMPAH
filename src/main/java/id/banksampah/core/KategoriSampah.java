package id.banksampah.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

public abstract class KategoriSampah {
    private final BigDecimal beratKg;
    private final int tarifPoinPerKg;

    protected KategoriSampah(BigDecimal beratKg, int tarifPoinPerKg) {
        if (beratKg == null || beratKg.signum() <= 0 || tarifPoinPerKg <= 0) {
            throw new IllegalArgumentException("Berat dan tarif harus lebih dari nol");
        }
        this.beratKg = beratKg.setScale(3, RoundingMode.HALF_UP);
        if (this.beratKg.signum() == 0) {
            throw new IllegalArgumentException("Berat minimal 0,001 kg");
        }
        this.tarifPoinPerKg = tarifPoinPerKg;
    }

    public BigDecimal getBeratKg() { return beratKg; }
    public int getTarifPoinPerKg() { return tarifPoinPerKg; }
    public abstract String getNamaKategori();

    public BigDecimal hitungPoin() {
        return beratKg.multiply(BigDecimal.valueOf(tarifPoinPerKg)).setScale(2, RoundingMode.HALF_UP);
    }
}
