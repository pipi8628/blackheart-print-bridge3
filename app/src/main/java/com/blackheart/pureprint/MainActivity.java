package com.blackheart.pureprint;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.ViewGroup;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class MainActivity extends Activity {

    private EditText webAppUrlInput, printerIpInput, portInput;
    private TextView statusText, logText;
    private Button loadBtn, reloadBtn, testBtn;
    private WebView webView;
    private LinearLayout setupPanel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        buildUi();
        loadSettings();
        setupWebView();
        String url = webAppUrlInput.getText().toString().trim();
        if (url.length() > 0) loadPosUrl();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(17, 17, 17));

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        Button menuBtn = btn("☰ 設定", Color.rgb(255, 209, 102));
        menuBtn.setTextSize(18);
        FrameLayout.LayoutParams menuLp = new FrameLayout.LayoutParams(150, 72);
        menuLp.leftMargin = 14;
        menuLp.topMargin = 14;
        root.addView(menuBtn, menuLp);

        setupPanel = new LinearLayout(this);
        setupPanel.setOrientation(LinearLayout.VERTICAL);
        setupPanel.setPadding(18, 18, 18, 18);
        setupPanel.setBackgroundColor(Color.rgb(245, 245, 245));
        setupPanel.setVisibility(View.VISIBLE);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView title = tv("設定面板 V49.2", 20, Color.BLACK, true);
        Button closeBtn = btn("×", Color.rgb(255, 209, 102));
        closeBtn.setTextSize(24);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        titleRow.addView(closeBtn, new LinearLayout.LayoutParams(70, 70));
        setupPanel.addView(titleRow);

        statusText = tv("尚未載入 POS", 15, Color.rgb(40, 40, 40), true);
        setupPanel.addView(statusText);

        setupPanel.addView(labelDark("POS Web App 網址"));
        webAppUrlInput = input("https://script.google.com/macros/s/XXXX/exec");
        setupPanel.addView(webAppUrlInput);

        setupPanel.addView(labelDark("GoDEX IP"));
        printerIpInput = input("192.168.31.189");
        setupPanel.addView(printerIpInput);

        setupPanel.addView(labelDark("Port"));
        portInput = input("9100");
        setupPanel.addView(portInput);

        loadBtn = btn("儲存 / 載入 POS", Color.rgb(6, 214, 160));
        reloadBtn = btn("重新整理 POS", Color.rgb(142, 202, 230));
        testBtn = btn("測試列印", Color.rgb(255, 209, 102));

        setupPanel.addView(loadBtn);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.addView(reloadBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        btnRow.addView(testBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        setupPanel.addView(btnRow);

        setupPanel.addView(labelDark("LOG"));
        logText = tv("", 12, Color.rgb(20, 20, 20), false);
        logText.setBackgroundColor(Color.rgb(235, 235, 235));
        logText.setPadding(12, 8, 12, 8);
        setupPanel.addView(logText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                120
        ));

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                dp(360),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        panelLp.leftMargin = 24;
        panelLp.topMargin = 96;
        root.addView(setupPanel, panelLp);

        menuBtn.setOnClickListener(v -> toggleSetupPanel());
        closeBtn.setOnClickListener(v -> hideSetupPanel());
        loadBtn.setOnClickListener(v -> loadPosUrl());
        reloadBtn.setOnClickListener(v -> {
            if (webView != null) webView.reload();
        });
        testBtn.setOnClickListener(v -> printText("黑心地瓜球\n測試列印\n$60"));

        setContentView(root);
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView labelDark(String text) {
        return tv(text, 14, Color.rgb(20, 20, 20), true);
    }

    private void toggleSetupPanel() {
        if (setupPanel == null) return;
        if (setupPanel.getVisibility() == View.VISIBLE) hideSetupPanel();
        else showSetupPanel();
    }

    private void hideSetupPanel() {
        if (setupPanel != null) setupPanel.setVisibility(View.GONE);
    }

    private void showSetupPanel() {
        if (setupPanel != null) setupPanel.setVisibility(View.VISIBLE);
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.addJavascriptInterface(new PrinterBridge(), "AndroidPrinter");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                status("POS 已載入｜AndroidPrinter 已注入");
                // 讓前端偵錯更明顯：頁面載入後在 console/全域標記橋接存在
                view.evaluateJavascript("window.__ANDROID_PRINTER_READY__ = !!window.AndroidPrinter; console.log('AndroidPrinter ready:', !!window.AndroidPrinter);", null);
                hideSetupPanel();
            }
        });
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

    private void loadPosUrl() {
        saveSettings();
        String url = webAppUrlInput.getText().toString().trim();
        if (url.length() == 0) {
            status("請先輸入 POS Web App 網址");
            return;
        }
        status("載入 POS 中...");
        webView.loadUrl(url);
    }

    public class PrinterBridge {
        @JavascriptInterface
        public void printText(String text) {
            MainActivity.this.printText(text);
        }

        @JavascriptInterface
        public boolean isReady() {
            return true;
        }
    }

    private TextView tv(String text, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setPadding(0, 5, 0, 5);
        if (bold) v.setTypeface(null, Typeface.BOLD);
        return v;
    }

    private TextView label(String text) {
        return tv(text, 14, Color.rgb(255, 209, 102), true);
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextColor(Color.BLACK);
        e.setTextSize(18);
        e.setBackgroundColor(Color.WHITE);
        e.setPadding(12, 8, 12, 8);
        return e;
    }

    private Button btn(String text, int bg) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(16);
        b.setTextColor(Color.BLACK);
        b.setBackgroundColor(bg);
        return b;
    }

    private void printText(String text) {
        saveSettings();
        new Thread(() -> {
            try {
                String content = text == null ? "" : text.replace("\r", "").trim();
                if (content.length() == 0) content = "TEST";

                // V1.1 修正：POS 一次送整筆訂單時，會用 --- 分隔多張標籤。
                // 這裡由 APK 端拆成多張逐張送 GoDEX，避免全部擠在同一張標籤。
                String[] labels = content.split("(?m)^\\s*---\\s*$");
                int total = 0;
                for (String label : labels) {
                    if (label != null && label.trim().length() > 0) total++;
                }
                if (total <= 0) {
                    labels = new String[]{"TEST"};
                    total = 1;
                }

                final int finalTotal = total;
                ui(() -> {
                    status("列印中...共 " + finalTotal + " 張");
                    log("收到 POS 列印內容，共 " + finalTotal + " 張");
                });

                int index = 0;
                for (String label : labels) {
                    String one = label == null ? "" : label.trim();
                    if (one.length() == 0) continue;
                    index++;

                    final String finalOne = one;
                    final int finalIndex = index;
                    final byte[] printData = buildEzplImage(finalOne);

                    ui(() -> {
                        status("列印中..." + finalIndex + "/" + finalTotal);
                        log("第 " + finalIndex + "/" + finalTotal + " 張\n" + finalOne + "\n\nEZPL IMAGE bytes=" + printData.length);
                    });

                    sendSocket(printData);
                    Thread.sleep(350);
                }

                ui(() -> status("列印已送出｜共 " + finalTotal + " 張"));

            } catch (Exception ex) {
                ui(() -> {
                    status("列印失敗：" + ex.getMessage());
                    log(ex.toString());
                });
            }
        }).start();
    }

    private byte[] buildEzplImage(String text) throws Exception {
        String content = text == null ? "TEST" : text.replace("\r", "").trim();
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
        out.write(("Q0,10," + widthBytes + "," + height + "\r\n").getBytes("US-ASCII"));
        out.write(img);
        out.write(("\r\nE\r\n").getBytes("US-ASCII"));
        return out.toByteArray();
    }

    private Bitmap textToBitmap(String text) {
        String content = text == null ? "TEST" : text.replace("\r", "").trim();
        if (content.length() == 0) content = "TEST";

        String[] rawLines = content.split("\n");
        String first = "";
        for (String s : rawLines) {
            if (s != null && s.trim().length() > 0 && !s.trim().equals("---")) {
                first = s.trim();
                break;
            }
        }

        if (first.startsWith("D.")) return drinkTextToBitmap(content);
        if (first.startsWith("TEL:")) return telTextToBitmap(content);
        return normalTextToBitmap(content);
    }

    private Bitmap normalTextToBitmap(String text) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setFakeBoldText(true);

        int width = 320;
        int height = 240;
        int margin = 22;
        int y = 46;
        int lineHeight = 42;

        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        String[] rawLines = text == null ? new String[]{"TEST"} : text.replace("\r", "").split("\n");

        for (String rawLine : rawLines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.length() == 0 || line.equals("---")) continue;

            if (line.contains("$") || line.matches("\\d{2}/\\d{2}.*")) {
                lines.add(line);
            } else {
                paint.setTextSize(34);
                lines.addAll(wrapText(line, paint, width - (margin * 3)));
            }
        }

        if (lines.size() == 0) lines.add("TEST");
        while (lines.size() > 6) lines.remove(lines.size() - 1);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        for (String line : lines) {
            boolean isHeader = line.contains("$");
            boolean isTime = line.matches("\\d{2}/\\d{2}.*");

            if (isHeader) {
                String left = line;
                String price = "";
                int p = line.lastIndexOf("$");
                if (p >= 0) {
                    left = line.substring(0, p).trim();
                    price = line.substring(p).trim();
                }
                paint.setTextSize(34);
                paint.setFakeBoldText(true);
                canvas.drawText(left, margin, y, paint);
                if (price.length() > 0) {
                    float priceWidth = paint.measureText(price);
                    canvas.drawText(price, width - margin - priceWidth, y, paint);
                }
                y += 48;
                continue;
            }

            if (isTime) {
                paint.setTextSize(18);
            } else if (line.contains("・") || line.startsWith("+")) {
                paint.setTextSize(26);
            } else {
                paint.setTextSize(34);
            }
            paint.setFakeBoldText(true);

            float textWidth = paint.measureText(line);
            float x = (width - textWidth) / 2f;
            if (x < margin) x = margin;
            if (x + textWidth > width - margin) x = width - margin - textWidth;
            if (x < margin) x = margin;
            if (isTime && y < height - 22) y = Math.max(y, height - 24);
            canvas.drawText(line, x, y, paint);
            y += isTime ? 24 : lineHeight;
        }
        return bitmap;
    }

    private Bitmap telTextToBitmap(String text) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setFakeBoldText(true);

        int width = 320;
        int height = 240;
        int margin = 22;

        java.util.ArrayList<String> raw = new java.util.ArrayList<>();
        String[] rawLines = text == null ? new String[]{"TEL"} : text.replace("\r", "").split("\n");
        for (String s : rawLines) {
            String line = s == null ? "" : s.trim();
            if (line.length() == 0 || line.equals("---")) continue;
            raw.add(line);
        }
        if (raw.size() == 0) raw.add("TEL:");

        String header = raw.get(0);
        String price = "";
        int p = header.lastIndexOf("$");
        if (p >= 0) {
            price = header.substring(p).trim();
            header = header.substring(0, p).trim();
        }

        String time = "";
        java.util.ArrayList<String> itemLines = new java.util.ArrayList<>();
        for (int i = 1; i < raw.size(); i++) {
            String line = raw.get(i);
            if (line.matches("\\d{2}/\\d{2}.*")) time = line;
            else if (line.startsWith("$")) price = line;
            else if (line.contains("$")) {
                int pp = line.lastIndexOf("$");
                price = line.substring(pp).trim();
                String left = line.substring(0, pp).trim();
                if (left.length() > 0) itemLines.add(left);
            } else itemLines.add(line);
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        float telSize = 34;
        paint.setTextSize(telSize);
        while (paint.measureText(header) > width - (margin * 2) && telSize > 24) {
            telSize -= 1;
            paint.setTextSize(telSize);
        }
        float hw = paint.measureText(header);
        canvas.drawText(header, Math.max(margin, (width - hw) / 2f), 48, paint);

        int y = 98;
        for (int i = 0; i < itemLines.size() && i < 3; i++) {
            String line = itemLines.get(i);
            paint.setTextSize(i == 0 ? 34 : 30);
            java.util.ArrayList<String> wrapped = wrapText(line, paint, width - (margin * 2));
            for (String w : wrapped) {
                if (y > 150) break;
                float tw = paint.measureText(w);
                float x = (width - tw) / 2f;
                if (x < margin) x = margin;
                if (x + tw > width - margin) x = width - margin - tw;
                canvas.drawText(w, x, y, paint);
                y += 38;
            }
        }

        if (price.length() > 0) {
            paint.setTextSize(30);
            float pw = paint.measureText(price);
            canvas.drawText(price, (width - pw) / 2f, height - 52, paint);
        }
        if (time.length() > 0) {
            paint.setTextSize(19);
            float tw = paint.measureText(time);
            canvas.drawText(time, (width - tw) / 2f, height - 20, paint);
        }
        return bitmap;
    }

    private Bitmap drinkTextToBitmap(String text) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setFakeBoldText(true);

        int width = 320;
        int height = 240;
        int margin = 22;

        java.util.ArrayList<String> raw = new java.util.ArrayList<>();
        String[] rawLines = text == null ? new String[]{"TEST"} : text.replace("\r", "").split("\n");
        for (String s : rawLines) {
            String line = s == null ? "" : s.trim();
            if (line.length() == 0 || line.equals("---")) continue;
            raw.add(line);
        }
        if (raw.size() == 0) raw.add("D.");

        String header = raw.get(0);
        String price = "";
        int p = header.lastIndexOf("$");
        if (p >= 0) {
            price = header.substring(p).trim();
            header = header.substring(0, p).trim();
        }
        header = normalizeDrinkHeader(header);

        String time = "";
        java.util.ArrayList<String> itemLines = new java.util.ArrayList<>();
        for (int i = 1; i < raw.size(); i++) {
            String line = raw.get(i);
            if (line.matches("\\d{2}/\\d{2}.*")) time = line;
            else if (line.startsWith("$")) price = line;
            else itemLines.add(line);
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        paint.setTextSize(34);
        float headWidth = paint.measureText(header);
        float headX = (width - headWidth) / 2f;
        if (headX < margin) headX = margin;
        if (headX + headWidth > width - margin) headX = width - margin - headWidth;
        canvas.drawText(header, headX, 48, paint);

        int y = 96;
        for (int i = 0; i < itemLines.size() && i < 4; i++) {
            String line = itemLines.get(i);
            if (line.contains("・")) line = line.replace("・", " / ");
            if (line.startsWith("+")) paint.setTextSize(28);
            else if (i == 0) paint.setTextSize(34);
            else paint.setTextSize(30);

            java.util.ArrayList<String> wrapped = wrapText(line, paint, width - (margin * 2));
            for (String w : wrapped) {
                if (y > 175) break;
                float tw = paint.measureText(w);
                float x = (width - tw) / 2f;
                if (x < margin) x = margin;
                if (x + tw > width - margin) x = width - margin - tw;
                canvas.drawText(w, x, y, paint);
                y += 36;
            }
        }

        if (time.length() > 0) {
            paint.setTextSize(19);
            canvas.drawText(time, margin, height - 20, paint);
        }
        if (price.length() > 0) {
            paint.setTextSize(30);
            float pw = paint.measureText(price);
            canvas.drawText(price, width - margin - pw, height - 20, paint);
        }
        return bitmap;
    }

    private String normalizeDrinkHeader(String header) {
        if (header == null) return "";
        String h = header.trim();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\((\\d+)\\)");
        java.util.regex.Matcher m = p.matcher(h);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, "(" + m.group(1) + "號)");
        }
        m.appendTail(sb);
        return sb.toString();
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
                        if (gray < 230) value |= 1;
                    }
                }
                out[idx++] = (byte)(value & 0xFF);
            }
        }
        return out;
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

        Thread.sleep(250);
        os.close();
        socket.close();
    }

    private void ui(Runnable r) {
        handler.post(r);
    }

    private void status(String s) {
        statusText.setText(s);
        log(s);
    }

    private void log(String s) {
        if (logText != null) logText.setText(s == null ? "" : s);
    }
}
