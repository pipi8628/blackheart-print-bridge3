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
import java.io.ByteArrayOutputStream;
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
    private Button startBtn, stopBtn, testBtn, skipBtn, cancelAllBtn;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private boolean running = false;
    private boolean working = false;
    private int printedCount = 0;

    private String lastBaseUrl = "";
    private String lastRow = "";
    private String lastId = "";
    private String lastOrderNo = "";
    private String lastLabelNo = "";

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

        root.addView(tv("🏷️ BlackHeart PurePrint｜DX2 圖片列印版八", 26, Color.WHITE, true));

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
        skipBtn = btn("🗑 略過目前待印", Color.rgb(255, 183, 3));
        cancelAllBtn = btn("🧹 取消全部待印", Color.rgb(255, 120, 120));

        root.addView(startBtn);
        root.addView(stopBtn);
        root.addView(testBtn);
        root.addView(skipBtn);
        root.addView(cancelAllBtn);

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
        testBtn.setOnClickListener(v -> printText("黑心地瓜球\n珍珠奶茶\n少冰半糖"));
        skipBtn.setOnClickListener(v -> skipCurrentPending());
        cancelAllBtn.setOnClickListener(v -> cancelAllPending());

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

                lastBaseUrl = base;
                lastRow = row;
                lastId = id;
                lastOrderNo = orderNo;
                lastLabelNo = labelNo;

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
                final byte[] printData = buildEzplImage(finalText);
                final String zpl = "EZPL Q IMAGE bytes=" + printData.length;

                ui(() -> {
                    status("收到 #" + orderNo + " 第 " + labelNo + " 張，正在列印...");
                    log("送出內容:\n" + zpl);
                });

                sendSocket(printData);

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

    private void skipCurrentPending() {
        saveSettings();
        new Thread(() -> {
            try {
                String base = cleanUrl(webAppUrlInput.getText().toString().trim());
                if (base.length() == 0) throw new Exception("請輸入 Web App 網址");

                String row = lastRow;
                String id = lastId;
                String orderNo = lastOrderNo;
                String labelNo = lastLabelNo;

                // 如果目前畫面沒有已讀取的訂單，就主動抓一次 pending 再略過。
                if ((row == null || row.length() == 0) && (id == null || id.length() == 0)) {
                    String json = httpGet(base + "?api=pending");
                    JSONObject job = new JSONObject(json);
                    if (!job.optBoolean("ok", false)) {
                        ui(() -> status("沒有待略過訂單"));
                        return;
                    }
                    row = job.optString("row", "");
                    id = job.optString("id", "");
                    orderNo = job.optString("orderNo", "");
                    labelNo = job.optString("labelNo", "");
                }

                if (row.length() == 0 && id.length() == 0) {
                    throw new Exception("找不到 row/id，無法標記完成");
                }

                httpGet(base + "?api=done&row=" + enc(row) + "&id=" + enc(id));

                lastBaseUrl = base;
                lastRow = "";
                lastId = "";
                lastOrderNo = "";
                lastLabelNo = "";

                final String msg = "已略過待印訂單 #" + orderNo + " 第 " + labelNo + " 張";
                ui(() -> status(msg));

            } catch (Exception ex) {
                ui(() -> {
                    status("略過失敗：" + ex.getMessage());
                    log(ex.toString());
                });
            }
        }).start();
    }


    private void cancelAllPending() {
        saveSettings();
        stop();
        new Thread(() -> {
            try {
                String base = cleanUrl(webAppUrlInput.getText().toString().trim());
                if (base.length() == 0) throw new Exception("請輸入 Web App 網址");

                ui(() -> status("正在批次取消全部待印..."));

                // 優先使用 Apps Script 的批次清除 API：?api=clearAll
                // 這會比一筆一筆 done 快很多。如果後端尚未支援，會自動退回舊方式。
                boolean clearAllOk = false;
                String clearJson = "";
                try {
                    clearJson = httpGet(base + "?api=clearAll");
                    JSONObject clearResult = new JSONObject(clearJson);
                    clearAllOk = clearResult.optBoolean("ok", false);

                    if (clearAllOk) {
                        int count = clearResult.optInt("count", clearResult.optInt("cleared", -1));

                        lastBaseUrl = base;
                        lastRow = "";
                        lastId = "";
                        lastOrderNo = "";
                        lastLabelNo = "";

                        final String msg = count >= 0
                                ? "已批次取消全部待印，共 " + count + " 筆"
                                : "已批次取消全部待印";
                        ui(() -> status(msg));
                        return;
                    }
                } catch (Exception ignored) {
                    // 後端還沒有 clearAll 時，會自動改用逐筆取消。
                }

                ui(() -> status("後端未支援 clearAll，改用逐筆取消..."));

                int skipped = 0;
                java.util.HashSet<String> seen = new java.util.HashSet<>();

                for (int i = 0; i < 100; i++) {
                    String json = httpGet(base + "?api=pending");
                    JSONObject job = new JSONObject(json);

                    if (!job.optBoolean("ok", false)) {
                        break;
                    }

                    String row = job.optString("row", "");
                    String id = job.optString("id", "");
                    String orderNo = job.optString("orderNo", "");
                    String labelNo = job.optString("labelNo", "");
                    String key = row + "|" + id;

                    if (row.length() == 0 && id.length() == 0) {
                        throw new Exception("pending 回傳沒有 row/id，無法取消全部");
                    }

                    if (seen.contains(key)) {
                        throw new Exception("同一筆待印無法清除，請檢查 Apps Script 的 api=done 是否有成功寫入");
                    }
                    seen.add(key);

                    httpGet(base + "?api=done&row=" + enc(row) + "&id=" + enc(id));
                    skipped++;

                    final int current = skipped;
                    final String msg = "取消中：已取消 " + current + " 筆｜#" + orderNo + " 第 " + labelNo + " 張";
                    ui(() -> status(msg));
                }

                lastBaseUrl = base;
                lastRow = "";
                lastId = "";
                lastOrderNo = "";
                lastLabelNo = "";

                final int total = skipped;
                ui(() -> status("已取消全部待印，共 " + total + " 筆"));

            } catch (Exception ex) {
                ui(() -> {
                    status("取消全部失敗：" + ex.getMessage());
                    log(ex.toString());
                });
            }
        }).start();
    }

    private void printText(String text) {
        saveSettings();
        new Thread(() -> {
            try {
                String content = text == null ? "" : text.replace("\r", "").trim();
                if (content.length() == 0) content = "TEST";

                final String finalContent = content;
                final byte[] printData = buildEzplImage(finalContent);
                final String zpl = "EZPL Q IMAGE bytes=" + printData.length;

                ui(() -> {
                    status("列印中...");
                    log("送出內容:\n" + zpl);
                });

                sendSocket(printData);

                ui(() -> status("測試列印已送出"));

            } catch (Exception ex) {
                ui(() -> {
                    status("測試失敗：" + ex.getMessage());
                    log(ex.toString());
                });
            }
        }).start();
    }

    // DX2 圖片列印方案：GoDEX EZPL 圖像不是 GW+HEX，而是 Q 指令 + 原始 bitmap bytes。
    // 官方 EZPL Pattern command: Qx,y,width,height，後面資料長度 = width(byte) * height(dot)。
    private byte[] buildEzplImage(String text) throws Exception {
        String content = text == null ? "TEST" : text
                .replace("\r", "")
                .trim();
        if (content.length() == 0) content = "TEST";

        Bitmap bmp = textToBitmap(content);
        byte[] img = bitmapToBytes(bmp);

        int widthBytes = (bmp.getWidth() + 7) / 8;
        int height = bmp.getHeight();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("^Q30,3\r\n").getBytes("US-ASCII"));
        out.write(("^W40\r\n").getBytes("US-ASCII"));
        out.write(("^H12\r\n").getBytes("US-ASCII"));
        out.write(("^S2\r\n").getBytes("US-ASCII"));
        out.write(("^L\r\n").getBytes("US-ASCII"));
        out.write(("Q40,10," + widthBytes + "," + height + "\r\n").getBytes("US-ASCII"));
        out.write(img);
        out.write(("\r\nE\r\n").getBytes("US-ASCII"));
        return out.toByteArray();
    }

    private Bitmap textToBitmap(String text) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(42);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setFakeBoldText(true);

        int width = 320;       // 40mm 標籤安全寬度
        int height = 110;      // 30mm 標籤安全高度
        int lineHeight = 38;   // 商用版行距

        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        String[] rawLines = text == null ? new String[]{"TEST"} : text.replace("\r", "").split("\n");
        for (String raw : rawLines) {
            String line = raw == null ? "" : raw.trim();
            if (line.length() == 0) continue;
            lines.addAll(wrapText(line, paint, width - 52));
        }

        if (lines.size() == 0) lines.add("TEST");
        while (lines.size() > 3) lines.remove(lines.size() - 1);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        int y = 55;
        for (String line : lines) {
            canvas.drawText(line, 55, y, paint);
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

    private byte[] bitmapToBytes(Bitmap bmp) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        int widthBytes = (width + 7) / 8;
        byte[] out = new byte[widthBytes * height];

        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int bx = 0; bx < widthBytes; bx++) {
                int value = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int x = bx * 8 + bit;
                    value <<= 1;
                    if (x < width) {
                        int pixel = bmp.getPixel(x, y);
                        int gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
                        // GoDEX Q pattern: bit=1 印黑點。
                        if (gray < 230) value |= 1;
                    }
                }
                out[idx++] = (byte)(value & 0xFF);
            }
        }
        return out;
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

    private void sendSocket(byte[] data) throws Exception {
        String ip = printerIpInput.getText().toString().trim();
        int port = Integer.parseInt(portInput.getText().toString().trim());

        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(ip, port), 5000);
        socket.setSoTimeout(5000);

        OutputStream os = socket.getOutputStream();
        os.write(data);
        os.flush();

        Thread.sleep(1200);
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
