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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private EditText webAppUrlInput, printerIpInput, portInput;
    private TextView statusText, countText, logText;
    private Button startBtn, stopBtn, testBtn, skipBtn, cancelAllBtn;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private boolean running = false;
    private boolean working = false;
    private int printedCount = 0;
    private volatile int ignoreBeforeOrderNo = 0;
    private volatile int ignoreBeforeRowNo = 0;
    private volatile boolean backgroundClearing = false;

    private String lastBaseUrl = "";
    private String lastRow = "";
    private String lastId = "";
    private String lastOrderNo = "";
    private String lastLabelNo = "";

    private final Runnable poller = new Runnable() {
        @Override public void run() {
            if (running) {
                pollOnce();
                handler.postDelayed(this, 800);
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

        root.addView(tv("🏷️ BlackHeart PurePrint｜DX2 終極2圖片列印版", 26, Color.WHITE, true));

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
        e.setTextSize(24);
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
        // 只印當天版：不再載入舊的 ignore row/order 設定，避免新單被誤擋。
        ignoreBeforeOrderNo = 0;
        ignoreBeforeRowNo = 0;
        prefs.edit().remove("ignoreBeforeOrderNo").remove("ignoreBeforeRowNo").apply();
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

                String orderDateText = getOrderDateText(job);
                if (!isTodayOrder(orderDateText)) {
                    if (row.length() > 0 || id.length() > 0) {
                        httpGet(base + "?api=done&row=" + enc(row) + "&id=" + enc(id));
                    }
                    final String skippedMsg = "非今日訂單自動略過 #" + orderNo + "｜日期=" + orderDateText;
                    ui(() -> {
                        status(skippedMsg);
                        working = false;
                    });
                    return;
                }

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
        new Thread(() -> {
            try {
                String base = cleanUrl(webAppUrlInput.getText().toString().trim());
                if (base.length() == 0) throw new Exception("請輸入 Web App 網址");

                int cutoff = parseOrderNo(lastOrderNo);
                int cutoffRow = parseOrderNo(lastRow);

                // 先抓目前第一筆待印，當作本次要清掉的舊單界線。
                try {
                    String json = httpGet(base + "?api=pending");
                    JSONObject job = new JSONObject(json);
                    if (job.optBoolean("ok", false)) {
                        cutoff = Math.max(cutoff, parseOrderNo(job.optString("orderNo", "")));
                        cutoffRow = Math.max(cutoffRow, parseOrderNo(job.optString("row", "")));
                    }
                } catch (Exception ignored) {}

                if (cutoff <= 0 && cutoffRow <= 0) {
                    ui(() -> status("目前沒有可判斷單號/列號的待印訂單"));
                    return;
                }

                ignoreBeforeOrderNo = Math.max(ignoreBeforeOrderNo, cutoff);
                // 嚴格清除模式：清除期間先暫時擋掉所有目前 pending，避免舊單被 poller 撿回去印。
                // 背景清完後會把 ignoreBeforeRowNo 改回實際清到的最大 row。
                int originalIgnoreBeforeRowNo = ignoreBeforeRowNo;
                ignoreBeforeRowNo = 999999999;
                prefs.edit()
                        .putInt("ignoreBeforeOrderNo", ignoreBeforeOrderNo)
                        .putInt("ignoreBeforeRowNo", ignoreBeforeRowNo)
                        .apply();
                backgroundClearing = true;

                lastBaseUrl = base;
                lastRow = "";
                lastId = "";
                lastOrderNo = "";
                lastLabelNo = "";

                final int finalCutoff = ignoreBeforeOrderNo;
                final int finalCutoffRow = ignoreBeforeRowNo;
                ui(() -> status("已清空本地待印｜單號#" + finalCutoff + " / 列" + finalCutoffRow + "以前背景取消，新單照常印"));

                // 後端如果支援 clearAllBefore，優先用真正批次。
                try {
                    String clearJson = httpGet(base + "?api=clearAllBefore&orderNo=" + enc(String.valueOf(finalCutoff)) + "&row=" + enc(String.valueOf(finalCutoffRow)));
                    JSONObject clearResult = new JSONObject(clearJson);
                    if (clearResult.optBoolean("ok", false)) {
                        int count = clearResult.optInt("count", clearResult.optInt("cleared", -1));
                        ignoreBeforeRowNo = Math.max(originalIgnoreBeforeRowNo, finalCutoffRow);
                        prefs.edit().putInt("ignoreBeforeRowNo", ignoreBeforeRowNo).apply();
                        backgroundClearing = false;
                        final String msg = count >= 0
                                ? "背景已批次取消舊單，共 " + count + " 筆｜新單可正常列印"
                                : "背景已批次取消舊單｜新單可正常列印";
                        ui(() -> status(msg));
                        return;
                    }
                } catch (Exception ignored) {
                    // 後端尚未支援 clearAllBefore，改用背景逐筆 done，但不阻塞新單列印。
                }

                int skipped = 0;
                int maxClearedRow = originalIgnoreBeforeRowNo;
                java.util.HashSet<String> seen = new java.util.HashSet<>();

                for (int i = 0; i < 200; i++) {
                    String json = httpGet(base + "?api=pending");
                    JSONObject job = new JSONObject(json);

                    if (!job.optBoolean("ok", false)) break;

                    String row = job.optString("row", "");
                    String id = job.optString("id", "");
                    String orderNo = job.optString("orderNo", "");
                    String labelNo = job.optString("labelNo", "");
                    int n = parseOrderNo(orderNo);
                    int r = parseOrderNo(row);
                    if (r > maxClearedRow) maxClearedRow = r;

                    // APK 單機嚴格模式：因 pending API 只吐一筆，無法可靠分辨「清除期間的新單」。
                    // 所以這裡會把當下持續吐出的 pending 全部 done 到清空為止，避免舊單回印。

                    if (row.length() == 0 && id.length() == 0) break;

                    String key = row + "|" + id;
                    if (seen.contains(key)) break;
                    seen.add(key);

                    httpGet(base + "?api=done&row=" + enc(row) + "&id=" + enc(id));
                    skipped++;

                    final int current = skipped;
                    final String msg = "背景取消舊單中：已取消 " + current + " 筆｜#" + orderNo + " 第 " + labelNo + " 張";
                    ui(() -> status(msg));
                }

                ignoreBeforeRowNo = maxClearedRow;
                prefs.edit().putInt("ignoreBeforeRowNo", ignoreBeforeRowNo).apply();
                backgroundClearing = false;
                final int total = skipped;
                final int finalMaxClearedRow = maxClearedRow;
                ui(() -> status("背景取消完成，共 " + total + " 筆｜已略過到 row " + finalMaxClearedRow));

            } catch (Exception ex) {
                backgroundClearing = false;
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
        paint.setTextSize(28);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setFakeBoldText(true);

        int width = 320;       // 40mm 標籤安全寬度
        int height = 240;      // 30mm 標籤安全高度
        int lineHeight = 34;   // 三行商用版行距

        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        String[] rawLines = text == null
                ? new String[]{"TEST"}
                : text.replace("\r", "").split("\n");

        for (String raw : rawLines) {
            String line = raw == null ? "" : raw.trim();
            if (line.length() == 0) continue;
            int margin = 30;
            lines.addAll(wrapText(line, paint, width - (margin * 2)));
        }

        if (lines.size() == 0) lines.add("TEST");
        while (lines.size() > 6) lines.remove(lines.size() - 1);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        int y = 28;
        int margin = 24;

        for (String line : lines) {
            if (line.startsWith("NO.")) {
                paint.setTextSize(40);   // 號碼放大
                paint.setFakeBoldText(true);
            } else if (line.matches("\\d{2}/\\d{2}.*")) {
                paint.setTextSize(18);   // 時間縮小，例如 06/21 03:21
                paint.setFakeBoldText(false);
            } else if (line.contains("・")) {
                paint.setTextSize(26);   // 無糖・少冰 同一行
                paint.setFakeBoldText(true);
            } else if (line.startsWith("$")) {
                paint.setTextSize(34);   // 價格放大
                paint.setFakeBoldText(true);
            } else {
                paint.setTextSize(30);   // 品名正常
                paint.setFakeBoldText(true);
            }

            float textWidth = paint.measureText(line);
            float x = (width - textWidth) / 2f - 30;
            if (x < margin) x = margin;

            canvas.drawText(line, x, y, paint);
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
        socket.connect(new InetSocketAddress(ip, port), 8000);
        socket.setSoTimeout(8000);

        OutputStream os = socket.getOutputStream();
        os.write(data);
        os.flush();

        Thread.sleep(300);
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

    private String getOrderDateText(JSONObject job) {
        return firstNonEmpty(
                job.optString("date", ""),
                job.optString("orderDate", ""),
                job.optString("createdAt", ""),
                job.optString("created_at", ""),
                job.optString("timestamp", ""),
                job.optString("time", ""),
                job.optString("datetime", ""),
                job.optString("createdTime", "")
        );
    }

    private boolean isTodayOrder(String dateText) {
        if (dateText == null || dateText.trim().length() == 0) {
            // 後端若尚未回傳日期，先放行，避免新單被誤擋。
            return true;
        }

        String s = dateText.trim();
        String todayDash = new SimpleDateFormat("yyyy-MM-dd", Locale.TAIWAN).format(new Date());
        String todaySlash = new SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(new Date());
        String todaySlashNoZero = new SimpleDateFormat("yyyy/M/d", Locale.TAIWAN).format(new Date());
        String todayMd = new SimpleDateFormat("M/d", Locale.TAIWAN).format(new Date());
        String todayMdZero = new SimpleDateFormat("MM/dd", Locale.TAIWAN).format(new Date());
        String todayChinese = new SimpleDateFormat("yyyy年M月d日", Locale.TAIWAN).format(new Date());

        if (s.contains(todayDash)) return true;
        if (s.contains(todaySlash)) return true;
        if (s.contains(todaySlashNoZero)) return true;
        if (s.contains(todayChinese)) return true;

        // 避免 6/2 誤判 6/20：只有字串開頭就是月/日時才使用 M/d 判斷。
        if (s.startsWith(todayMd + " ") || s.startsWith(todayMd + "　") || s.equals(todayMd)) return true;
        if (s.startsWith(todayMdZero + " ") || s.startsWith(todayMdZero + "　") || s.equals(todayMdZero)) return true;

        return false;
    }

    private int parseOrderNo(String s) {
        if (s == null) return 0;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') digits.append(c);
        }
        if (digits.length() == 0) return 0;
        try {
            return Integer.parseInt(digits.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private String httpGet(String urlText) throws Exception {
        URL url = new URL(urlText);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(1000);
        c.setReadTimeout(1500);
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
