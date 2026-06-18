package com.blackheart.printbridge;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends Activity {

    private EditText webAppUrlInput, printerIpInput, portInput;
    private TextView statusText, countText, logText;
    private Button startBtn, stopBtn, testBtn;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private boolean running = false;
    private boolean working = false;
    private int printedCount = 0;

    private final Runnable poller = new Runnable() {
        @Override public void run() {
            if (running) {
                pollOnce();
                handler.postDelayed(this, 2000);
            }
        }
    };

    @Override
    public void onCreate(Bundle b) {
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
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(tv("🏷️ 黑心純列印 V4.0 ZPL Big5 VF版", 28, Color.WHITE, true));

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
        testBtn = btn("🧪 測試列印", Color.rgb(0, 150, 255));

        root.addView(startBtn);
        root.addView(stopBtn);
        root.addView(testBtn);

        root.addView(label("Log"));
        logText = tv("", 14, Color.WHITE, false);
        logText.setBackgroundColor(Color.rgb(43, 43, 43));
        logText.setPadding(16, 16, 16, 16);
        root.addView(logText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

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
        v.setPadding(0, 8, 0, 8);
        if (bold) v.setTypeface(null, Typeface.BOLD);
        return v;
    }

    private TextView label(String text) {
        return tv(text, 18, Color.rgb(255, 209, 102), true);
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
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

    private void testPrint() {
        printText("TEST\nBLACKHEART\n梅粉\n煉乳+可可粉\n$60");
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
                        status("監聽中｜沒有待列印貼紙");
                        working = false;
                    });
                    return;
                }

                String row = job.optString("row", "");
                String id = job.optString("id", "");
                String orderNo = job.optString("orderNo", "");
                String labelNo = job.optString("labelNo", "");

                String labelText = job.optString("labelText", "");
                if (labelText.length() == 0) labelText = job.optString("text", "");
                if (labelText.length() == 0) labelText = job.optString("tspl", "");
                if (labelText.length() == 0) labelText = "#" + orderNo + "-" + labelNo;

                final String finalText = stripOldCommands(labelText);
                final String zpl = buildZpl(finalText);

                ui(() -> {
                    status("收到 #" + orderNo + " 第 " + labelNo + " 張，正在列印...");
                    log("送出內容:\n" + zpl);
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

    private void printText(String text) {
        saveSettings();
        new Thread(() -> {
            try {
                String content = text == null ? "" : text.trim();
                if (content.length() == 0) content = "TEST";

                final String finalContent = stripOldCommands(content);
                final String zpl = buildZpl(finalContent);

                ui(() -> {
                    status("列印中...");
                    log("送出內容:\n" + zpl);
                });

                sendSocket(zpl);

                ui(() -> {
                    status("測試列印已送出");
                    log("已送到印表機");
                });

            } catch (Exception ex) {
                ui(() -> {
                    status("列印失敗：" + ex.getMessage());
                    log(ex.toString());
                });
            }
        }).start();
    }

    private String buildZpl(String text) {
        String[] rawLines = text.replace("\r", "").split("\n");
        StringBuilder zpl = new StringBuilder();

        // GoDEX DX2 EZPL：使用 T 指令 + E 結尾，較適合 DX 系列
        zpl.append("^Q50,3\n");
        zpl.append("^W100\n");
        zpl.append("^H10\n");
        zpl.append("^P1\n");
        zpl.append("^S2\n");

        int y = 20;
        int printed = 0;

        for (String line : rawLines) {
            String safe = sanitizeZplText(line);
            if (safe.length() == 0) continue;
            if (printed >= 9) break;

            zpl.append("T 20,")
                    .append(y)
                    .append(",0,3,1,1,\"")
                    .append(safe)
                    .append("\"\n");

            y += 40;
            printed++;
        }

        zpl.append("E\n");
        return zpl.toString();
    }

    private String sanitizeZplText(String text) {
        if (text == null) return "";
        return text.replace("^", "")
                .replace("~", "")
                .replace("\\", "")
                .replace("\"", "")
                .replace("\r", "")
                .replace("\n", "")
                .trim();
    }

    private String stripOldCommands(String text) {
        if (text == null) return "";
        String[] lines = text.replace("\r", "").split("\n");
        StringBuilder clean = new StringBuilder();

        for (String line : lines) {
            String s = line.trim();
            if (s.length() == 0) continue;
            if (s.startsWith("^")) continue;
            if (s.startsWith("~")) continue;
            if (s.startsWith("AA,")) continue;
            if (s.equalsIgnoreCase("E")) continue;
            if (s.equalsIgnoreCase("PRINT")) continue;
            if (s.equalsIgnoreCase("FORM")) continue;
            if (s.toUpperCase().contains("PURE PRINT")) continue;
            if (s.toUpperCase().contains("NO ARCORE")) continue;
            clean.append(s).append("\n");
        }

        return clean.toString().trim();
    }

    private void sendSocket(String data) throws Exception {
        String ip = printerIpInput.getText().toString().trim();
        int port = Integer.parseInt(portInput.getText().toString().trim());

        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(ip, port), 3000);

        OutputStream os = socket.getOutputStream();
        os.write(data.getBytes("Big5"));
        os.flush();

        Thread.sleep(300);
        os.close();
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
        if (s == null) return "";
        if (s.endsWith("?")) return s.substring(0, s.length() - 1);
        if (s.endsWith("&")) return s.substring(0, s.length() - 1);
        return s;
    }

    private String enc(String s) throws Exception {
        return URLEncoder.encode(s == null ? "" : s, "UTF-8");
    }

    private void ui(Runnable r) { handler.post(r); }

    private void status(String s) {
        statusText.setText(s);
        log(s);
    }

    private void log(String s) {
        logText.setText(s + "\n\n" + logText.getText().toString());
    }
}
