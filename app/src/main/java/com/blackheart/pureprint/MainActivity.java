package com.blackheart.pureprint;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.*;
import java.io.*;
import java.net.*;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private EditText webAppUrlInput, printerIpInput, portInput;
    private TextView statusText, countText, logText;
    private Button startBtn, stopBtn, testBtn;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    private boolean working = false;
    private int printedCount = 0;
    private SharedPreferences prefs;

    private final Runnable poller = new Runnable() {
        @Override public void run() {
            if (running) {
                pollOnce();
                handler.postDelayed(this, 2000);
            }
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        buildUi();
        loadSettings();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(Color.rgb(17, 17, 17));
        scroll.addView(root);

        root.addView(tv("🏷️ 黑心純列印 V3.9 GZPL", 28, Color.WHITE, true));

        statusText = tv("尚未啟動", 20, Color.rgb(255, 209, 102), true);
        root.addView(statusText);

        countText = tv("已列印：0", 32, Color.WHITE, true);
        root.addView(countText);

        root.addView(label("Apps Script Web App 網址"));
        webAppUrlInput = input("https://script.google.com/macros/s/XXXX/exec");
        root.addView(webAppUrlInput);

        root.addView(label("GoDEX IP"));
        printerIpInput = input("192.168.31.189");
        root.addView(printerIpInput);

        root.addView(label("Port"));
        portInput = input("9100");
        root.addView(portInput);

        startBtn = btn("▶️ 開始監聽", Color.rgb(6, 214, 160));
        stopBtn = btn("⏸️ 停止監聽", Color.rgb(239, 71, 111));
        testBtn = btn("🧪 測試列印", Color.rgb(30, 144, 255));

        root.addView(startBtn);
        root.addView(stopBtn);
        root.addView(testBtn);

        logText = tv("Log", 14, Color.WHITE, false);
        logText.setBackgroundColor(Color.rgb(43, 43, 43));
        logText.setPadding(16, 16, 16, 16);
        root.addView(logText);

        startBtn.setOnClickListener(v -> start());
        stopBtn.setOnClickListener(v -> stop());
        testBtn.setOnClickListener(v -> testPrint());

        setContentView(scroll);
    }

    private TextView tv(String text, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setPadding(0, 10, 0, 10);
        if (bold) v.setTypeface(null, Typeface.BOLD);
        return v;
    }

    private TextView label(String text) {
        return tv(text, 18, Color.rgb(255, 209, 102), true);
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(false);
        e.setMinLines(1);
        e.setTextColor(Color.BLACK);
        e.setTextSize(18);
        e.setBackgroundColor(Color.WHITE);
        e.setPadding(16, 12, 16, 12);
        return e;
    }

    private Button btn(String text, int bg) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(20);
        b.setTextColor(Color.BLACK);
        b.setBackgroundColor(bg);
        return b;
    }

    private void loadSettings() {
        webAppUrlInput.setText(prefs.getString("webAppUrl", ""));
        printerIpInput.setText(prefs.getString("printerIp", "192.168.31.189"));
        portInput.setText(prefs.getString("port", "9100"));
    }

    private void saveSettings() {
        prefs.edit()
                .putString("webAppUrl", webAppUrlInput.getText().toString().trim())
                .putString("printerIp", printerIpInput.getText().toString().trim())
                .putString("port", portInput.getText().toString().trim())
                .apply();
    }

    private void start() {
        saveSettings();
        running = true;
        status("監聽中...");
        handler.removeCallbacks(poller);
        handler.post(poller);
    }

    private void stop() {
        running = false;
        handler.removeCallbacks(poller);
        status("已停止");
    }

    private void pollOnce() {
        if (working) return;
        working = true;

        new Thread(() -> {
            try {
                String base = cleanUrl(webAppUrlInput.getText().toString().trim());
                if (base.length() == 0) throw new Exception("請輸入 Web App 網址");

                String json = httpGet(base + "?api=pending");
                JSONObject job = new JSONObject(json);

                if (!job.optBoolean("ok", false)) {
                    ui(() -> {
                        statusText.setText("監聽中｜沒有待列印貼紙");
                        working = false;
                    });
                    return;
                }

                String row = job.optString("row", "");
                String id = job.optString("id", "");
                String orderNo = job.optString("orderNo", "");
                String labelNo = job.optString("labelNo", "");
                String labelText = job.optString("labelText", "");

                // 相容舊版 API：如果後端只回 tspl/ezpl，就直接轉成文字印出；若有 labelText，優先用 labelText。
                if (labelText.length() == 0) {
                    labelText = job.optString("text", "");
                }
                if (labelText.length() == 0) {
                    labelText = job.optString("tspl", "TEST");
                }

                final String finalLabelText = labelText;
                final String zpl = buildZpl(finalLabelText);

                ui(() -> {
                    status("收到 #" + orderNo + " 第 " + labelNo + " 張，正在列印...");
                    log("ZPL:\n" + zpl);
                });

                sendSocket(zpl);

                httpGet(base + "?api=done&row=" + enc(row) + "&id=" + enc(id));

                printedCount++;
                ui(() -> {
                    countText.setText("已列印：" + printedCount);
                    status("列印完成 #" + orderNo + " 第 " + labelNo + " 張");
                    working = false;
                });

            } catch (Exception ex) {
                ui(() -> {
                    status("錯誤：" + ex.getMessage());
                    log(ex.toString());
                    working = false;
                });
            }
        }).start();
    }

    private void testPrint() {
        saveSettings();
        printText("TEST\nBLACKHEART");
    }

    private void printText(String text) {
        new Thread(() -> {
            try {
                String content = text == null ? "" : text.trim();
                if (content.length() == 0) content = "TEST";

                final String zpl = buildZpl(content);

                ui(() -> {
                    status("測試列印已送出");
                    log("送出內容:\n" + zpl);
                });

                sendSocket(zpl);

            } catch (Exception ex) {
                ui(() -> {
                    status("測試失敗：" + ex.getMessage());
                    log(ex.toString());
                });
            }
        }).start();
    }

    private String buildZpl(String text) {
        String[] rawLines = text.replace("\r", "").split("\n");
        StringBuilder zpl = new StringBuilder();

        zpl.append("^XA\n");
        zpl.append("^PW320\n");
        zpl.append("^LL240\n");

        int y = 30;
        int printed = 0;

        for (String line : rawLines) {
            String safe = sanitizeZplText(line);
            if (safe.length() == 0) continue;
            if (printed >= 10) break;

            zpl.append("^FO30,").append(y)
                    .append("^A0N,30,30^FD")
                    .append(safe)
                    .append("^FS\n");

            y += 40;
            printed++;
        }

        if (printed == 0) {
            zpl.append("^FO30,30^A0N,30,30^FDTEST^FS\n");
        }

        zpl.append("^XZ\n");
        return zpl.toString();
    }

    private String sanitizeZplText(String s) {
        if (s == null) return "";
        return s.replace("\"", "")
                .replace("'", "")
                .replace("^", "")
                .replace("~", "")
                .replace("\r", "")
                .replace("\n", "")
                .trim();
    }

    private void sendSocket(String data) throws Exception {
        String ip = printerIpInput.getText().toString().trim();
        int port = Integer.parseInt(portInput.getText().toString().trim());

        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(ip, port), 3000);

        OutputStream out = socket.getOutputStream();
        out.write(data.getBytes("US-ASCII"));
        out.flush();

        Thread.sleep(500);
        out.close();
        socket.close();
    }

    private String httpGet(String urlText) throws Exception {
        URL url = new URL(urlText);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestMethod("GET");
        c.setInstanceFollowRedirects(true);

        InputStream in = c.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);

        br.close();
        return sb.toString();
    }

    private String cleanUrl(String s) {
        if (s.endsWith("?")) return s.substring(0, s.length() - 1);
        if (s.endsWith("&")) return s.substring(0, s.length() - 1);
        return s;
    }

    private String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private void ui(Runnable r) {
        handler.post(r);
    }

    private void status(String s) {
        statusText.setText(s);
        log(s);
    }

    private void log(String s) {
        logText.setText(s + "\n\n" + logText.getText().toString());
    }
}
