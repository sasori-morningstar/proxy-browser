package com.example.proxybrowser;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

public class ProxyUtils {

    /**
     * Reads all proxy settings from SharedPreferences and configures the default Authenticator
     * using the stored username and password.
     */
    public static void setupProxyAuthentication(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("proxyPrefs", Context.MODE_PRIVATE);
        final String proxyUsername = prefs.getString("proxy_username", "");
        final String proxyPassword = prefs.getString("proxy_password", "");

        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                        proxyUsername,
                        proxyPassword.toCharArray()
                );
            }
        });
    }

    /**
     * Creates a new OkHttpClient that is configured to use the proxy settings (host and port)
     * read from SharedPreferences.
     */
    public static OkHttpClient createProxyClient(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("proxyPrefs", Context.MODE_PRIVATE);
        String proxyHost = prefs.getString("proxy_host", "");
        int proxyPort = prefs.getInt("proxy_port", 0);

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);

        // If the proxy host or port are not configured, return a client without a proxy.
        if (!proxyHost.isEmpty() && proxyPort != 0) {
            Proxy proxy = new Proxy(
                    Proxy.Type.SOCKS, // Change to Proxy.Type.HTTP if needed.
                    new InetSocketAddress(proxyHost, proxyPort)
            );
            builder.proxy(proxy);
        }

        return builder.build();
    }
}
