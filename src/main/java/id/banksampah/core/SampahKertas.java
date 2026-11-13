package id.banksampah.core;

import java.math.BigDecimal;

public final class SampahKertas extends KategoriSampah {
    public static final int DEFAULT_TARIF = 150;
    public SampahKertas(BigDecimal beratKg, Integer tarifDb) {
        super(beratKg, tarifDb != null ? tarifDb : DEFAULT_TARIF);
    }
    @Override public String getNamaKategori() { return "Sampah Kertas"; }
}
