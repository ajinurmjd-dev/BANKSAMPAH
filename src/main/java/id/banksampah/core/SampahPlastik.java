package id.banksampah.core;

import java.math.BigDecimal;

public final class SampahPlastik extends KategoriSampah {
    public static final int DEFAULT_TARIF = 300;
    public SampahPlastik(BigDecimal beratKg, Integer tarifDb) {
        super(beratKg, tarifDb != null ? tarifDb : DEFAULT_TARIF);
    }
    @Override public String getNamaKategori() { return "Sampah Plastik"; }
}
