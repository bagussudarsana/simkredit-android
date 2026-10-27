package com.sudarsana.simkredit;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;

public class MainActivity extends Activity {
  SimEngine engine;
  TextView hasil, status;
  EditText eHarga, eCair, eAdm, eType, eTahun, eTenor, eSkema, eRole, eArea;
  CheckBox cIns;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    loadEngine();
    ScrollView sv = new ScrollView(this);
    LinearLayout L = new LinearLayout(this);
    L.setOrientation(LinearLayout.VERTICAL);
    int p = dp(16); L.setPadding(p, p, p, p);
    sv.addView(L, new ViewGroup.LayoutParams(-1, -2));
    TextView t = new TextView(this);
    t.setText("SimKredit — Simulasi Kredit (100% offline)");
    t.setTextSize(18); t.setPadding(0, 0, 0, dp(4));
    L.addView(t);
    status = new TextView(this); L.addView(status);
    eHarga = add(L, "OTR / Harga", "200000000", true);
    eCair = add(L, "Pencairan", "160000000", true);
    eAdm = add(L, "ADM Global", "0", true);
    eType = add(L, "Type (cth: Minibus/Sedan/Jeep, motor)", "Minibus/Sedan/Jeep", false);
    eTahun = add(L, "Tahun unit", "2018", true);
    eTenor = add(L, "Tenor (bln)", "36", true);
    eSkema = add(L, "Skema (ARREAR/ADVANCE)", "ARREAR", false);
    eRole = add(L, "Customer (cth: Dealer)", "Dealer", false);
    eArea = add(L, "Area (nasional/bali_mataram)", "bali_mataram", false);
    cIns = new CheckBox(this); cIns.setText("Pakai asuransi"); cIns.setChecked(true); L.addView(cIns);
    Button hit = new Button(this); hit.setText("HITUNG (offline)"); L.addView(hit);
    Button sync = new Button(this); sync.setText("SYNC RATE DARI SERVER"); L.addView(sync);
    hasil = new TextView(this); hasil.setPadding(0, dp(12), 0, 0); hasil.setTextSize(15);
    L.addView(hasil);
    setContentView(sv);
    hit.setOnClickListener(v -> doHitung());
    sync.setOnClickListener(v -> doSync());
    // Auto-sync tiap dibuka bila ada internet (jadwal ringan: saat dibuka + tombol manual)
    doSync();
  }

  int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

  EditText add(LinearLayout L, String label, String def, boolean num) {
    TextView l = new TextView(this); l.setText(label); L.addView(l);
    EditText e = new EditText(this); e.setText(def);
    if (num) e.setInputType(InputType.TYPE_CLASS_NUMBER);
    L.addView(e);
    return e;
  }

  void loadEngine() {
    try {
      JSONObject j = null;
      File f = new File(getFilesDir(), "rates.json");
      if (f.exists()) {
        FileInputStream in = new FileInputStream(f);
        byte[] by = new byte[(int) f.length()]; in.read(by); in.close();
        j = new JSONObject(new String(by, "UTF-8"));
      } else {
        InputStream in = getAssets().open("rates.json");
        byte[] by = new byte[in.available()]; in.read(by); in.close();
        j = new JSONObject(new String(by, "UTF-8"));
      }
      engine = new SimEngine(j);
      setStatus("Rate lokal siap.");
    } catch (Exception e) { engine = new SimEngine(new JSONObject()); setStatus("Rate gagal dimuat."); }
  }

  void setStatus(String s) { if (status != null) status.setText(s); }

  double num(EditText e, double d) {
    try { String s = e.getText().toString().replace(".", "").replace(" ", "").replace(",", "."); return Double.parseDouble(s); }
    catch (Exception ex) { return d; }
  }

  void doHitung() {
    if (engine == null) return;
    double harga = num(eHarga, 200000000), cair = num(eCair, 160000000), adm = num(eAdm, 0);
    String type = eType.getText().toString(), skema = eSkema.getText().toString().toUpperCase();
    if (!skema.equals("ADVANCE")) skema = "ARREAR";
    int tahun = (int) num(eTahun, 2018), tenor = (int) num(eTenor, 36);
    String role = eRole.getText().toString().trim(); if (role.isEmpty()) role = "Dealer";
    String area = eArea.getText().toString().trim(); if (area.isEmpty()) area = "bali_mataram";
    JSONObject r = engine.hitung(harga, cair, adm, type, tahun, tenor, skema, role, area, cIns.isChecked());
    NumberFormat nf = NumberFormat.getInstance(new Locale("id", "ID"));
    if (r.optBoolean("ok", false)) {
      hasil.setText("Angsuran: Rp " + nf.format(r.optLong("angsuran")) + " × " + tenor + " bln\n"
        + "Pokok efektif: Rp " + nf.format(r.optLong("pokokEff")) + "\n"
        + "Total bayar: Rp " + nf.format(r.optLong("totBayar")) + "\n"
        + "DP: Rp " + nf.format(r.optLong("dp")) + " | Premi: Rp " + nf.format(r.optLong("premi")) + "\n"
        + "Eff " + r.optString("eff") + "% | Flat " + String.format("%.2f", r.optDouble("flat")) + "%\n"
        + "Refund bunga: Rp " + nf.format(r.optLong("refundBunga")) + " | Asuransi: Rp " + nf.format(r.optLong("refundAsuransi")) + " | Admin: Rp " + nf.format(r.optLong("refundAdmin")));
    } else hasil.setText("Gagal: " + r.optString("err"));
  }

  void doSync() {
    setStatus("Sync rate…");
    new Thread(() -> {
      try {
        URL u = new URL("https://loan.sudarsana.my.id/api_rates.php");
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(15000);
        InputStream in = c.getInputStream();
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        String s = bo.toString("UTF-8");
        JSONObject j = new JSONObject(s);
        if (j.optBoolean("ok", false)) {
          FileOutputStream fo = openFileOutput("rates.json", MODE_PRIVATE);
          fo.write(s.getBytes("UTF-8")); fo.close();
          engine = new SimEngine(j);
          runOnUiThread(() -> setStatus("Sync OK " + j.optString("ts", "")));
        } else runOnUiThread(() -> setStatus("Sync gagal (server). Pakai rate lokal."));
      } catch (Exception e) {
        runOnUiThread(() -> setStatus("Offline — pakai rate lokal."));
      }
    }).start();
  }
}
