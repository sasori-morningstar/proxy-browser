package com.example.proxybrowser;

import android.os.Bundle;
import android.webkit.MimeTypeMap;
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

import okhttp3.ConnectionPool;
import okhttp3.Interceptor;
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
        // Text formats
        CONTENT_TYPE_MAP.put("text/html", "text/html");
        CONTENT_TYPE_MAP.put("text/plain", "text/plain");
        CONTENT_TYPE_MAP.put("text/css", "text/css");

        // JavaScript
        CONTENT_TYPE_MAP.put("application/javascript", "application/javascript");
        CONTENT_TYPE_MAP.put("text/javascript", "application/javascript");
        CONTENT_TYPE_MAP.put("application/x-javascript", "application/javascript");

        // Images
        CONTENT_TYPE_MAP.put("image/jpeg", "image/jpeg");
        CONTENT_TYPE_MAP.put("image/jpg", "image/jpeg");
        CONTENT_TYPE_MAP.put("image/png", "image/png");
        CONTENT_TYPE_MAP.put("image/gif", "image/gif");
        CONTENT_TYPE_MAP.put("image/webp", "image/webp");
        CONTENT_TYPE_MAP.put("image/svg+xml", "image/svg+xml");
        CONTENT_TYPE_MAP.put("image/x-icon", "image/x-icon");

        // Video
        CONTENT_TYPE_MAP.put("video/mp4", "video/mp4");
        CONTENT_TYPE_MAP.put("video/webm", "video/webm");
        CONTENT_TYPE_MAP.put("video/ogg", "video/ogg");

        // Audio
        CONTENT_TYPE_MAP.put("audio/mpeg", "audio/mpeg");
        CONTENT_TYPE_MAP.put("audio/ogg", "audio/ogg");
        CONTENT_TYPE_MAP.put("audio/wav", "audio/wav");

        // Application types
        CONTENT_TYPE_MAP.put("application/json", "application/json");
        CONTENT_TYPE_MAP.put("application/xml", "application/xml");
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
        // Generate new session only if not already generated for the current domain
        if (currentSessionId == null) {
            currentSessionId = UUID.randomUUID().toString();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdownNow(); // Force shutdown of all tasks
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
        if (proxyClient != null) {
            proxyClient.dispatcher().executorService().shutdown();
            proxyClient.connectionPool().evictAll();
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
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(5, 1, TimeUnit.MINUTES))
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();
                        Response response = null;
                        try {
                            response = chain.proceed(request);
                            return response;
                        } catch (IOException e) {
                            if (response != null) {
                                response.close();
                            }
                            throw e;
                        }
                    }
                })
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


        webSettings.setAllowFileAccess(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        // Enable additional settings
        webSettings.setBlockNetworkLoads(false);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setSupportMultipleWindows(true);

        // Store the user agent string once
        userAgentString = webSettings.getUserAgentString();

        webView.setWebViewClient(new WebViewClient() {
            private String lastDomain = "";

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (shouldSkipInterception(url)) {
                    return null;
                }

                try {
                    String domain = getDomainFromUrl(url);
                    if (!domain.equals(lastDomain)) {
                        generateNewSession();
                        lastDomain = domain;
                    }

                    final String sessionId = currentSessionId;

                    Future<WebResourceResponse> future = executor.submit(() -> {
                        Response response = null;
                        try {
                            OkHttpClient client = getProxyClient();
                            Request request = new Request.Builder()
                                    .url(url)
                                    .header("User-Agent", userAgentString)
                                    .header("X-Session-ID", sessionId)
                                    .header("X-Rotate", "true")
                                    .build();

                            response = client.newCall(request).execute();

                            // Immediately read the entire response body into memory
                            byte[] responseBytes = response.body().bytes();
                            String contentType = response.header("Content-Type", "");
                            if (contentType.isEmpty()) {
                                contentType = guessMimeType(url);
                            }

                            // Create response before closing
                            WebResourceResponse webResponse = new WebResourceResponse(
                                    extractMimeType(contentType),
                                    extractCharset(contentType),
                                    new java.io.ByteArrayInputStream(responseBytes)
                            );

                            // Add CORS headers
                            Map<String, String> headers = new HashMap<>();
                            headers.put("Access-Control-Allow-Origin", "*");
                            headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                            headers.put("Access-Control-Allow-Headers", "Origin, Content-Type, Accept");
                            webResponse.setResponseHeaders(headers);

                            return webResponse;

                        } catch (IOException e) {
                            Log.e("ProxyBrowser", "Request failed for URL: " + url, e);
                            return null;
                        } finally {
                            if (response != null && response.body() != null) {
                                response.close();
                            }
                        }
                    });

                    return future.get(30, TimeUnit.SECONDS);

                } catch (Exception e) {
                    Log.e("ProxyBrowser", "Interception failed for URL: " + url, e);
                    return null;
                }
            }
        });
    }
    private boolean shouldSkipInterception(String url) {
        // Skip data URLs
        if (url.startsWith("data:")) {
            return true;
        }

        // Skip blob URLs
        if (url.startsWith("blob:")) {
            return true;
        }

        return false;
    }

    private String guessMimeType(String url) {
        // Guess MIME type based on file extension
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            if (mimeType != null) {
                return mimeType;
            }
        }

        // Fallback to checking common extensions
        if (url.endsWith(".js")) return "application/javascript";
        if (url.endsWith(".css")) return "text/css";
        if (url.endsWith(".jpg") || url.endsWith(".jpeg")) return "image/jpeg";
        if (url.endsWith(".png")) return "image/png";
        if (url.endsWith(".gif")) return "image/gif";
        if (url.endsWith(".webp")) return "image/webp";
        if (url.endsWith(".svg")) return "image/svg+xml";
        if (url.endsWith(".mp4")) return "video/mp4";
        if (url.endsWith(".webm")) return "video/webm";
        if (url.endsWith(".mp3")) return "audio/mpeg";

        // Additional check for URLs with query parameters
        String baseUrl = url.split("\\?")[0];
        if (baseUrl.endsWith(".php") || baseUrl.endsWith(".aspx")) {
            // Check if URL appears to be serving images
            if (url.contains("/images/")) {
                return "image/jpeg";
            }
        }
        return "text/plain";
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