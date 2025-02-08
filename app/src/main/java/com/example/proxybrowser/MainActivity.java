package com.example.proxybrowser;

import android.os.Bundle;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private EditText urlInput;
    private Button goButton, backButton, forwardButton;
    private ExecutorService executor;
    private String currentSessionId;
    private String userAgentString;

    private volatile OkHttpClient proxyClient; // Single client instance
    private final Object proxyClientLock = new Object();

    // Proxy configuration
    private static final String PROXY_HOST = "rotating.proxyempire.io";
    private static final int PROXY_PORT = 9000;
    private static final String PROXY_USERNAME = "AcpVQtYstcLT4Q7d";
    private static final String PROXY_PASSWORD = "wifi;fr;;;";

    //Content type mappings
    private static final Map<String, String> CONTENT_TYPE_MAP = new HashMap<>();
    static {
        CONTENT_TYPE_MAP.put("text/html", "text/html");
        CONTENT_TYPE_MAP.put("application/javascript", "application/javascript");
        CONTENT_TYPE_MAP.put("text/javascript", "application/javascript");
        CONTENT_TYPE_MAP.put("text/css", "text/css");
        CONTENT_TYPE_MAP.put("image/jpeg", "image/jpeg");
        CONTENT_TYPE_MAP.put("image/png", "image/png");
        CONTENT_TYPE_MAP.put("image/gif", "image/gif");
        CONTENT_TYPE_MAP.put("application/json", "application/json");
        CONTENT_TYPE_MAP.put("text/plain", "text/plain");
        CONTENT_TYPE_MAP.put("application/x-www-form-urlencoded", "application/x-www-form-urlencoded");
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        executor = Executors.newFixedThreadPool(4);
        setupProxyAuthentication();
        // Initialize proxy client asynchronously
        executor.execute(() -> {
            synchronized (proxyClientLock) {
                proxyClient = createProxyClient();
                proxyClientLock.notifyAll();
            }
        });
        initializeViews();
        configureWebView();
        setupClickListeners();
        generateNewSession();
    }
    private OkHttpClient getProxyClient() throws InterruptedException {
        synchronized (proxyClientLock) {
            while (proxyClient == null) {
                proxyClientLock.wait();
            }
            return proxyClient;
        }
    }
    private void generateNewSession() {
        currentSessionId = UUID.randomUUID().toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }

    private void setupProxyAuthentication() {
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                        PROXY_USERNAME,
                        PROXY_PASSWORD.toCharArray()
                );
            }
        });
    }

    private OkHttpClient createProxyClient() {
        Proxy proxy = new Proxy(
                Proxy.Type.SOCKS,
                new InetSocketAddress(PROXY_HOST, PROXY_PORT)
        );

        return new OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private void initializeViews() {
        webView = findViewById(R.id.webView);
        urlInput = findViewById(R.id.urlInput);
        goButton = findViewById(R.id.goButton);
        backButton = findViewById(R.id.backButton);
        forwardButton = findViewById(R.id.forwardButton);
    }

    private void configureWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Store the user agent string once
        userAgentString = webSettings.getUserAgentString();

        webView.setWebViewClient(new WebViewClient() {
            private String lastDomain = "";

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                try {
                    String domain = getDomainFromUrl(url);
                    if (!domain.equals(lastDomain)) {
                        generateNewSession();
                        lastDomain = domain;
                    }

                    // Create final copy of the current session ID to use in lambda
                    final String sessionId = currentSessionId;

                    Future<WebResourceResponse> future = executor.submit(() -> {
                        try {

                            // Get the proxy client safely
                            OkHttpClient client = getProxyClient();
                            // Use stored user agent string instead of accessing WebView
                            String proxyAuth = PROXY_USERNAME + "-session-" + sessionId + ":" + PROXY_PASSWORD;
                            String credentials = android.util.Base64.encodeToString(
                                    proxyAuth.getBytes(),
                                    android.util.Base64.NO_WRAP
                            );

                            Request request = new Request.Builder()
                                    .url(url)
                                    .header("User-Agent", userAgentString)
                                    .header("Proxy-Authorization", "Basic " + credentials)
                                    .header("X-Session-ID", sessionId)
                                    .header("X-Rotate", "true")
                                    .build();

                            Response response = client.newCall(request).execute();
                            String proxyIp = response.header("X-Proxy-IP");
                            if (proxyIp != null) {
                                Log.d("ProxyBrowser", "Using IP: " + proxyIp);
                            }
                            // Get content type from response
                            String contentType = response.header("Content-Type", "text/plain");
                            String mimeType = extractMimeType(contentType);
                            String encoding = extractCharset(contentType);
                            // Log for debugging
                            Log.d("ProxyBrowser", "URL: " + url);
                            Log.d("ProxyBrowser", "Original Content-Type: " + contentType);
                            Log.d("ProxyBrowser", "Mapped MIME Type: " + mimeType);
                            Log.d("ProxyBrowser", "Encoding: " + encoding);
                            return new WebResourceResponse(
                                    mimeType,
                                    encoding,
                                    response.body().byteStream()
                            );
                        } catch (IOException e) {
                            e.printStackTrace();
                            return null;
                        }
                    });

                    return future.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }
        });
    }

    private String getDomainFromUrl(String url) {
        try {
            java.net.URL urlObj = new java.net.URL(url);
            return urlObj.getHost();
        } catch (Exception e) {
            return url;
        }
    }
    private String extractMimeType(String contentType) {
        if (contentType == null) {
            return "text/plain";
        }

        // Split content type and get the MIME type part
        String mimeType = contentType.split(";")[0].trim().toLowerCase();

        // Check if we have a mapping for this MIME type
        String mappedType = CONTENT_TYPE_MAP.get(mimeType);
        if (mappedType != null) {
            return mappedType;
        }

        // Handle special cases
        if (mimeType.startsWith("text/")) {
            return mimeType;
        }
        if (mimeType.startsWith("image/")) {
            return mimeType;
        }
        if (mimeType.startsWith("application/")) {
            return mimeType;
        }

        // Default fallback
        return "text/plain";
    }

    private String extractCharset(String contentType) {
        if (contentType == null) {
            return "UTF-8";
        }

        // Try to find charset in content type
        for (String param : contentType.split(";")) {
            param = param.trim();
            if (param.toLowerCase().startsWith("charset=")) {
                return param.substring(8).trim().toUpperCase();
            }
        }

        // Default to UTF-8 if no charset is specified
        return "UTF-8";
    }
    private void setupClickListeners() {
        goButton.setOnClickListener(v -> {
            String url = urlInput.getText().toString();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            generateNewSession();
            webView.loadUrl(url);
        });

        backButton.setOnClickListener(v -> {
            if (webView.canGoBack()) {
                generateNewSession();
                webView.goBack();
            }
        });

        forwardButton.setOnClickListener(v -> {
            if (webView.canGoForward()) {
                generateNewSession();
                webView.goForward();
            }
        });
    }

    private String getMimeType(String url) {
        if (url.endsWith(".html") || url.endsWith(".htm")) return "text/html";
        if (url.endsWith(".js")) return "application/javascript";
        if (url.endsWith(".css")) return "text/css";
        if (url.endsWith(".jpg") || url.endsWith(".jpeg")) return "image/jpeg";
        if (url.endsWith(".png")) return "image/png";
        if (url.endsWith(".gif")) return "image/gif";
        return "text/plain";
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            generateNewSession();
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}