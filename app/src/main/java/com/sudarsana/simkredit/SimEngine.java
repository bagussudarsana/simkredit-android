package com.sudarsana.simkredit;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

// Port mesin hitung loan.sudarsana.my.id/index.php (ARREAR/ADVANCE + refund confins + cap OJK).
// Boring over clever: tanpa library luar, hanya org.json bawaan Android.
public class SimEngine {
  public JSONObject rates;
  public Map<String,String> consts = new HashMap<>();

  public SimEngine(JSONObject rates) {
    this.rates = rates != null ? rates : new JSONObject();
    try {
      JSONObject c = this.rates.optJSONObject("loan_constants");
      if (c != null) { for (java.util.Iterator<String> it = c.keys(); it.hasNext();) { String k = it.next(); consts.put(k, c.optString(k, "")); } }
    } catch (Exception ignored) {}
  }

  double C(String k, double def) {
    try { String v = consts.get(k); if (v == null) return def; v = v.replace(',', '.').trim(); return Double.parseDouble(v); }
    catch (Exception e) { return def; }
  }

  public static long roundUpToUnit(double v, long u) { if (u < 1) u = 100; return (long) (Math.ceil(v / u) * u); }

  // katFromType: ringkas dari PHP (DB-link + keyword legacy).
  public String catFromType(String type) {
    if (type == null) return "minibus";
    String low = type.trim().toLowerCase();
    try {
      JSONObject link = rates.optJSONObject("loan_ph_kat_link");
      // loan_ph_kat_link dari API berbentuk array; dukung array & object
      JSONArray arr = rates.optJSONArray("loan_ph_kat_link");
      if (arr != null) {
        String up = type.trim().toUpperCase();
        for (int i = 0; i < arr.length(); i++) {
          JSONObject o = arr.optJSONObject(i); if (o == null) continue;
          String pk = o.optString("ph_kategori", "").toUpperCase();
          if (!pk.isEmpty() && (up.equals(pk) || up.contains(pk))) {
            String r = o.optString("rate_kategori", "").toLowerCase();
            if (!r.isEmpty()) return r;
          }
        }
      }
      if (link != null) { /* fallback object */ }
    } catch (Exception ignored) {}
    if (low.contains("motor")) return "motor";
    if (low.contains("passanger") || low.contains("passenger")) return "minibus";
    if (low.contains("pick up") || low.contains("pickup") || low.contains("truck")) return "truck";
    if (low.contains("minibus") || low.contains("micro") || low.contains("sedan") || low.contains("jeep")) return "minibus";
    return "minibus";
  }

  String tahunRange(String kat, int th) {
    JSONArray arr = rates.optJSONArray("loan_year_ranges");
    if (arr != null) for (int i = 0; i < arr.length(); i++) {
      JSONObject w = arr.optJSONObject(i); if (w == null) continue;
      if (!w.optString("kategori", "").equalsIgnoreCase(kat)) continue;
      if (th >= w.optInt("tahun_min") && th <= w.optInt("tahun_max")) return w.optString("kode", "");
    }
    return "";
  }

  Double dealerRate(String kat, String tr, int tenor) {
    JSONArray arr = rates.optJSONArray("loan_dealer_rates");
    if (arr != null) for (int i = 0; i < arr.length(); i++) {
      JSONObject w = arr.optJSONObject(i); if (w == null) continue;
      if (!w.optString("kategori", "").equalsIgnoreCase(kat)) continue;
      if (!w.optString("tahun_range", "").equals(tr)) continue;
      if (w.optInt("tenor") != tenor) continue;
      if (w.optInt("aktif", 1) != 1) continue;
      return w.optDouble("rate");
    }
    return null;
  }

  double roleMod(String role) {
    JSONArray arr = rates.optJSONArray("loan_role_modifiers");
    if (arr != null) for (int i = 0; i < arr.length(); i++) {
      JSONObject w = arr.optJSONObject(i); if (w == null) continue;
      if (w.optString("role", "").equalsIgnoreCase(role)) return w.optDouble("modifier");
    }
    return 0;
  }

  JSONObject roleRefund(String role) {
    JSONArray arr = rates.optJSONArray("loan_role_modifiers");
    if (arr != null) for (int i = 0; i < arr.length(); i++) {
      JSONObject w = arr.optJSONObject(i); if (w == null) continue;
      if (w.optString("role", "").equalsIgnoreCase(role)) return w;
    }
    return new JSONObject();
  }

  double convRate(String scheme, String effR, int tenor) {
    JSONArray arr = rates.optJSONArray("loan_conversion");
    if (arr != null) for (int i = 0; i < arr.length(); i++) {
      JSONObject w = arr.optJSONObject(i); if (w == null) continue;
      if (!w.optString("scheme", "").equalsIgnoreCase(scheme)) continue;
      if (w.optInt("tenor") != tenor) continue;
      double e = w.optDouble("eff_rate");
      String key = String.format(java.util.Locale.US, "%.1f", e);
      if (key.equals(effR)) return w.optDouble("flat_rate");
    }
    return 0;
  }

  long admFee(String role, long ntf, int tenor, String area) {
    JSONArray arr = rates.optJSONArray("loan_admin_fees");
    long best = 0; int bestScore = -1;
    if (arr != null) for (int i = 0; i < arr.length(); i++) {
      JSONObject w = arr.optJSONObject(i); if (w == null) continue;
      if (!w.optString("role", "").equalsIgnoreCase(role)) continue;
      if (ntf < w.optLong("ntf_min") || ntf > w.optLong("ntf_max")) continue;
      int wt = w.optInt("tenor");
      if (wt != 0 && wt != tenor) continue;
      String wa = w.optString("area", "semua").toLowerCase();
      if (!wa.equals(area.toLowerCase()) && !wa.equals("semua")) continue;
      int s = 0; if (wa.equals(area.toLowerCase())) s += 2; if (wt == tenor) s += 1;
      if (s > bestScore) { bestScore = s; best = w.optLong("biaya"); }
    }
    return best;
  }

  int katIns(String type, double harga) {
    if (catFromType(type).equals("truck")) return 6;
    if (harga <= 125000000) return 1; if (harga <= 200000000) return 2;
    if (harga <= 400000000) return 3; if (harga <= 800000000) return 4; return 5;
  }

  double insRate(String jenis, String guna, int kat, int wil) {
    JSONArray arr = rates.optJSONArray("loan_insurance");
    if (arr != null) for (int i = 0; i < arr.length(); i++) {
      JSONObject w = arr.optJSONObject(i); if (w == null) continue;
      if (!w.optString("jenis", "").equals(jenis)) continue;
      if (!w.optString("guna", "").equals(guna)) continue;
      if (w.optInt("kategori") != kat) continue;
      if (w.optInt("wilayah") != wil) continue;
      return w.optDouble("rate");
    }
    return 0;
  }

  // Hitung penuh. Kunci input sama seperti POST front.
  public JSONObject hitung(double harga, double pencairan, double admGlobal,
                           String type, int tahun, int tenor, String skema,
                           String role, String area, boolean insOn) {
    JSONObject out = new JSONObject();
    try {
      boolean isMotor = catFromType(type).equals("motor");
      if (isMotor) insOn = false;
      double dp = Math.max(0, harga - pencairan);
      // Premi awal
      double totPrem = 0;
      if (insOn) {
        int kat = katIns(type, harga);
        double base = insRate("All Risk", "Komersil", kat, 3);
        int yrs = Math.max(1, Math.min(4, (int) Math.ceil(tenor / 12.0)));
        double[] f = {100, 85, 75, 70};
        for (int i = 0; i < yrs; i++) totPrem += harga * (f[i] / 100.0) * base;
      }
      double pokok = pencairan + Math.max(0, admGlobal) + totPrem;
      String kat = catFromType(type);
      String tr = tahunRange(kat, tahun);
      Double dr = dealerRate(kat, tr, tenor);
      double fallback = C("FALLBACK_DEALER_RATE", 25);
      double dealer = dr != null ? dr : fallback;
      // Modifier tahun: aturan tabel bila ada, else umur efektif
      double mod = 0; String modSrc = "standar";
      JSONArray rules = rates.optJSONArray("loan_year_rules");
      boolean used = false;
      if (rules != null && C("YEAR_RULES_ENABLE", 1) == 1) {
        for (int i = 0; i < rules.length(); i++) {
          JSONObject w = rules.optJSONObject(i); if (w == null || w.optInt("aktif") != 1) continue;
          String rk = w.optString("kategori", "semua");
          if (!rk.equals("semua") && !rk.equalsIgnoreCase(kat)) continue;
          if (tahun < w.optInt("tahun_min") || tahun > w.optInt("tahun_max")) continue;
          mod = w.optDouble("modifier"); modSrc = w.optString("label", "aturan tahun"); used = true; break;
        }
      }
      if (isMotor) { mod = 0; modSrc = "motor (30% sudah termasuk non-asuransi)"; }
      else if (!used) {
        int umurEff = (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - tahun) + (int) Math.ceil(tenor / 12.0);
        int minAge = (int) C("OLD_YEAR_MIN_AGE", 20);
        boolean old = umurEff >= minAge;
        mod = old ? C("OLD_YEAR_MOD", 2.0) : (!insOn ? C("NO_INS_MOD", 1.5) : 0);
        modSrc = old ? ("tua: umur efektif " + umurEff) : (!insOn ? "tanpa asuransi" : "standar");
      }
      double effAuto = dealer + mod;
      double step = C("EFF_ROUND_STEP", 0.5); if (step <= 0) step = 0.5;
      String effR = String.format(java.util.Locale.US, "%.1f", Math.round(effAuto / step) * step);
      double flatAdder = C("FLAT_ADDER", 0.01);
      double rMod = roleMod(role);
      double flat = convRate(skema, effR, tenor) + flatAdder + rMod;
      long rounding = (long) C("ROUNDING_UNIT", 100);
      // Iterasi angsuran konvergen (salin PHP)
      double tY = tenor / 12.0;
      double feesPokok = 0, feesTunai = 0, ang = 0;
      for (int it = 0; it < 12; it++) {
        double peff = pokok + feesPokok;
        if (skema.equals("ADVANCE") && tenor > 1) peff += ang;
        double angLama = ang;
        if (skema.equals("ARREAR")) ang = roundUpToUnit((peff + peff * tY * flat / 100.0) / tenor, rounding);
        else { int n = Math.max(1, tenor - 1); ang = roundUpToUnit((peff + peff * flat / 100.0 / 12 * tenor) / n, rounding); }
        long adm = admFee(role, Math.round(pokok), tenor, area);
        double np = 0, nt = 0;
        nt += adm; // alur pencairan: ADM tunai utk kelebihan, pokok sudah include admGlobal
        boolean conv = Math.abs(np - feesPokok) < 0.5 && Math.abs(nt - feesTunai) < 0.5 && Math.abs(ang - angLama) < 0.5 && it > 0;
        feesPokok = np; feesTunai = nt;
        if (conv) break;
      }
      double peffFinal = pokok + feesPokok + ((skema.equals("ADVANCE") && tenor > 1) ? ang : 0);
      double totBayar = skema.equals("ADVANCE") ? ang * (tenor - 1) : ang * tenor;
      // Refund confins
      JSONObject rf = roleRefund(role);
      double upRef = rMod;
      double cairPct = rf.optDouble("npv_cair_pct", upRef);
      if (cairPct <= 0) cairPct = upRef;
      double bGross = ang > 0 ? (cairPct / 100.0) * peffFinal * peffFinal / (12 * ang) : 0;
      double aGross = totPrem * rf.optDouble("refund_asuransi_pct", 15.0) / 100.0;
      double admGross = rf.optDouble("refund_admin", 0);
      double capMax = peffFinal * C("REFUND_CAP_PCT", 17.5) / 100.0;
      double tot = bGross + aGross + admGross;
      if (tot > capMax && tot > 0) { double k = capMax / tot; bGross *= k; aGross *= k; admGross *= k; }
      double tax = rf.optDouble("pajak_pct", 0) / 100.0;
      out.put("ok", true).put("angsuran", (long) ang).put("pokokEff", Math.round(peffFinal))
         .put("totBayar", Math.round(totBayar)).put("dp", Math.round(dp))
         .put("eff", effR).put("flat", flat).put("premi", Math.round(totPrem))
         .put("refundBunga", Math.round(bGross * (1 - tax)))
         .put("refundAsuransi", Math.round(aGross * (1 - tax)))
         .put("refundAdmin", Math.round(admGross * (1 - tax)))
         .put("modSrc", modSrc);
    } catch (Exception e) {
      try { out.put("ok", false).put("err", e.getMessage()); } catch (Exception ignored) {}
    }
    return out;
  }
}
