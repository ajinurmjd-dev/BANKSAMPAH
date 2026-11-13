package id.banksampah.core;

import java.math.BigDecimal;

public final class SampahLogam extends KategoriSampah {
    public static final int DEFAULT_TARIF = 500;
    public SampahLogam(BigDecimal beratKg, Integer tarifDb) {
        super(beratKg, tarifDb != null ? tarifDb : DEFAULT_TARIF);
    }
    @Override public String getNamaKategori() { return "Sampah Logam"; }
}
