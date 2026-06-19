package com.blackheart.pureprint;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.ViewGroup;
import android.widget.*;

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        root.addView(tv("🏷️ 黑心純列印 V4.0 ZPL 中文字型版", 28, Color.WHITE, true));

        statusText = tv("尚未啟動", 20, Color.rgb(255, 209, 102), true);
        root.addView(statusText);

        countText = tv("已列印：0", 34, Color.rgb(190, 220, 255), true);
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
        testBtn = btn("🧪 測試列印", Color.rgb(0, 140, 255));

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
        testBtn.setOnClickListener(v -> printText("TEST\nBLACKHEART"));

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

                String labelText = firstNonEmpty(
                        job.optString("labelText", ""),
                        job.optString("text", ""),
                        job.optString("content", ""),
                        job.optString("plain", "")
                );

                if (labelText.length() == 0) {
                    String old = firstNonEmpty(job.optString("zpl", ""), job.optString("tspl", ""), job.optString("ezpl", ""));
                    labelText = extractReadableText(old);
                }

                final String finalText = labelText.length() == 0 ? "EMPTY" : labelText;
                final String zpl = buildEzplImage(finalText);

                ui(() -> {
                    status("收到 #" + orderNo + " 第 " + labelNo + " 張，正在列印...");
                    log("送出內容:\n" + zpl);
                });

                sendSocket(zpl);

                if (row.length() > 0 || id.length() > 0) {
                    httpGet(base + "?api=done&row=" + enc(row) + "&id=" + enc(id));
                }

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

                final String finalContent = content;
                final String zpl = buildEzplImage(finalContent);

                ui(() -> {
                    status("列印中...");
                    log("送出內容:\n" + zpl);
                });

                sendSocket(zpl);

                ui(() -> status("測試列印已送出"));

            } catch (Exception ex) {
                ui(() -> {
                    status("測試失敗：" + ex.getMessage());
                    log(ex.toString());
                });
            }
        }).start();
    }

    // DX2 最穩定方案：把中文先畫成 Bitmap，再用 EZPL 圖像指令列印。
    // 這樣不需要 Big5 / UTF-8 / AH / VF / AZ1，也不依賴印表機中文字型。
    private String buildEzplImage(String text) {
        String content = text == null ? "TEST" : text
                .replace("\r", "")
                .replace("\n", " ")
                .trim();
        if (content.length() == 0) content = "TEST";

        Bitmap bmp = textToBitmap(content);
        String hex = bitmapToHex(bmp);

        int widthBytes = (bmp.getWidth() + 7) / 8;
        int height = bmp.getHeight();

        StringBuilder ezpl = new StringBuilder();
        ezpl.append("^L\r\n");
        ezpl.append("^H12\r\n");
        ezpl.append("^Q40,3\r\n");
        ezpl.append("^W60\r\n");
        ezpl.append("^1\r\n");
        ezpl.append("GW,0,0,")
                .append(widthBytes).append(",")
                .append(height).append(",")
                .append(hex)
                .append("\r\n");
        ezpl.append("E\r\n");
        return ezpl.toString();
    }

    private Bitmap textToBitmap(String text) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(34);
        paint.setTypeface(Typeface.DEFAULT_BOLD);

        int maxWidth = 320;
        int padding = 16;
        int lineHeight = 44;

        java.util.ArrayList<String> lines = wrapText(text, paint, maxWidth - padding * 2);
        if (lines.size() == 0) lines.add("TEST");
        if (lines.size() > 5) {
            while (lines.size() > 5) lines.remove(lines.size() - 1);
        }

        int height = padding * 2 + lineHeight * lines.size();
        Bitmap bitmap = Bitmap.createBitmap(maxWidth, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        int y = padding + 34;
        for (String line : lines) {
            canvas.drawText(line, padding, y, paint);
            y += lineHeight;
        }
        return bitmap;
    }

    private java.util.ArrayList<String> wrapText(String text, Paint paint, int maxWidth) {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            String next = current.toString() + ch;
            if (paint.measureText(next) > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
            }
            current.append(ch);
        }

        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    private String bitmapToHex(Bitmap bmp) {
        StringBuilder hex = new StringBuilder();
        int width = bmp.getWidth();
        int height = bmp.getHeight();

        for (int y = 0; y < height; y++) {
            int bit = 0;
            int value = 0;

            for (int x = 0; x < width; x++) {
                int pixel = bmp.getPixel(x, y);
                int gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;

                value <<= 1;
                if (gray < 160) value |= 1;
                bit++;

                if (bit == 8) {
                    hex.append(String.format("%02X", value & 0xFF));
                    bit = 0;
                    value = 0;
                }
            }

            if (bit > 0) {
                value <<= (8 - bit);
                hex.append(String.format("%02X", value & 0xFF));
            }
        }
        return hex.toString();
    }

    private String sanitizeZplText(String s) {
        if (s == null) return "";
        return s
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
        socket.connect(new InetSocketAddress(ip, port), 5000);
        socket.setSoTimeout(5000);

        OutputStream os = socket.getOutputStream();
        os.write(data.getBytes("US-ASCII"));
        os.flush();

        Thread.sleep(1000);
        os.close();
        socket.close();
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && v.trim().length() > 0) return v.trim();
        }
        return "";
    }

    // 如果後端還回傳舊 EZPL，先盡量把可讀文字抽出來。
    private String extractReadableText(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = raw.replace("\r", "").split("\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.length() == 0) continue;
            if (t.startsWith("^") || t.startsWith("~") || t.equals("E")) continue;
            int idx = t.lastIndexOf(",");
            if (idx >= 0 && idx < t.length() - 1) {
                String tail = t.substring(idx + 1).replace("\"", "").trim();
                if (tail.length() > 0) sb.append(tail).append("\n");
            }
        }
        return sb.toString().trim();
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
        s = s.trim();
        if (s.endsWith("?")) return s.substring(0, s.length() - 1);
        if (s.endsWith("&")) return s.substring(0, s.length() - 1);
        return s;
    }

    private String enc(String s) throws Exception {
        return URLEncoder.encode(s == null ? "" : s, "UTF-8");
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
