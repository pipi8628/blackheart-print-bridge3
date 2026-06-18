package com.blackheart.printbridge;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;

public class MainActivity extends Activity {

    private EditText webAppUrlInput, printerIpInput, portInput;
    private TextView statusText, logText;
    private Button loadBtn, testBtn;
    private WebView webView;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        buildUi();
        setupWebView();
        loadSettings();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 18, 18, 18);
        root.setBackgroundColor(Color.rgb(17, 17, 17));

        TextView title = tv("黑心地瓜球 POS WebView 自動列印版", 24, Color.WHITE, true);
        root.addView(title);

        statusText = tv("等待操作", 18, Color.rgb(255, 209, 102), true);
        root.addView(statusText);

        root.addView(label("Web App / POS 網址"));
        webAppUrlInput = input("https://script.google.com/.../exec");
        root.addView(webAppUrlInput);

        root.addView(label("GoDEX / Gprinter IP"));
        printerIpInput = input("192.168.31.189");
        root.addView(printerIpInput);

        root.addView(label("Port"));
        portInput = input("9100");
        root.addView(portInput);

        loadBtn = btn("載入 POS 網頁", Color.rgb(6, 214, 160));
        testBtn = btn("測試列印", Color.rgb(142, 202, 230));
        root.addView(loadBtn);
        root.addView(testBtn);

        logText = tv("Log", 13, Color.WHITE, false);
        logText.setBackgroundColor(Color.rgb(43, 43, 43));
        logText.setPadding(12, 12, 12, 12);
        root.addView(logText);

        webView = new WebView(this);
        LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        );
        root.addView(webView, webParams);

        setContentView(root);

        loadBtn.setOnClickListener(v -> loadPosPage());
        testBtn.setOnClickListener(v -> {
            saveSettings();
            printText("測試列印\nBLACKHEART");
        });
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AndroidPrinter(), "AndroidPrinter");
    }

    private void loadSettings() {
        webAppUrlInput.setText(prefs.getString("webAppUrl", ""));
        printerIpInput.setText(prefs.getString("printerIp", "192.168.31.189"));
        portInput.setText(prefs.getString("port", "9100"));

        String url = webAppUrlInput.getText().toString().trim();
        if (url.startsWith("http")) {
            webView.loadUrl(url);
            status("已自動載入 POS 網頁");
        }
    }

    private void saveSettings() {
        prefs.edit()
                .putString("webAppUrl", webAppUrlInput.getText().toString().trim())
                .putString("printerIp", printerIpInput.getText().toString().trim())
                .putString("port", portInput.getText().toString().trim())
                .apply();
    }

    private void loadPosPage() {
        saveSettings();
        String url = webAppUrlInput.getText().toString().trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            status("網址要以 http:// 或 https:// 開頭");
            return;
        }
        webView.loadUrl(url);
        status("已載入 POS 網頁");
    }

    public class AndroidPrinter {
        @JavascriptInterface
        public void printText(String text) {
            MainActivity.this.printText(text);
        }

        @JavascriptInterface
        public void printOrder(String text) {
            MainActivity.this.printText(text);
        }
    }

    private void printText(String text) {
        saveSettings();
        new Thread(() -> {
            try {
                String content = text == null ? "" : text.trim();
                if (content.length() == 0) content = "EMPTY";

                final String finalContent = content;
                final String ezpl = buildEzpl(finalContent);

                sendSocket(ezpl);

                ui(() -> status("已送出列印"));
                ui(() -> log("送出內容:\n" + finalContent + "\n\nEZPL:\n" + ezpl));

            } catch (Exception ex) {
                ui(() -> {
                    status("列印失敗：" + ex.getMessage());
                    log(ex.toString());
                });
            }
        }).start();
    }

    private String buildEzpl(String text) {
        String[] rawLines = text.replace("\r", "").split("\n");
        StringBuilder body = new StringBuilder();

        int y = 20;
        int printed = 0;

        for (String line : rawLines) {
            String safe = sanitizeEzplText(line);
            if (safe.length() == 0) continue;

            body.append("AA,20,").append(y).append(",1,1,0,0E,\"").append(safe).append("\"\r\n");
y += 42;
printed++;
if (printed >= 9) break;
            }

            if (printed >= 9) break;

            body.append("AA,20,").append(y).append(",1,1,0,0E,\"").append(safe).append("\"\r\n");
            y += 42;
            printed++;
            if (printed >= 9) break;
        }

        if (printed == 0) {
            body.append("AA,20,20,1,1,0,0E,\"EMPTY\"\r\n");
        }

        return "^Q30,2\r\n" +
                "^W40\r\n" +
                "^H10\r\n" +
                "^P1\r\n" +
                "^S2\r\n" +
                "^AD\r\n" +
                "^C1\r\n" +
                "^R0\r\n" +
                "~Q+0\r\n" +
                "^O0\r\n" +
                "^D0\r\n" +
                "^E12\r\n" +
                body.toString() +
                "E\r\n";
    }

    private String sanitizeEzplText(String s) {
        if (s == null) return "";
        return s.replace("\"", "'")
                .replace("\\", "/")
                .replace("\t", " ")
                .trim();
    }

    private void sendSocket(String data) throws Exception {
        String ip = printerIpInput.getText().toString().trim();
        int port = Integer.parseInt(portInput.getText().toString().trim());

        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(ip, port), 3000);

        OutputStream out = socket.getOutputStream();
        out.write(data.getBytes(Charset.forName("Big5")));
        out.flush();

        Thread.sleep(500);
        out.close();
        socket.close();
    }

    private TextView tv(String text, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setPadding(0, 8, 0, 8);
        if (bold) v.setTypeface(null, android.graphics.Typeface.BOLD);
        return v;
    }

    private TextView label(String text) {
        return tv(text, 16, Color.rgb(255, 209, 102), true);
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextColor(Color.BLACK);
        e.setTextSize(16);
        e.setBackgroundColor(Color.WHITE);
        e.setPadding(12, 8, 12, 8);
        return e;
    }

    private Button btn(String text, int bg) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(18);
        b.setTextColor(Color.BLACK);
        b.setBackgroundColor(bg);
        return b;
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
