package com.example.proxybrowser;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

public class ProxySettingsActivity extends AppCompatActivity {
    private EditText proxyHostEditText, proxyPortEditText, proxyUsernameEditText, proxyPasswordEditText;
    private Button saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_proxy_settings);

        proxyHostEditText = findViewById(R.id.proxyHostEditText);
        proxyPortEditText = findViewById(R.id.proxyPortEditText);
        proxyUsernameEditText = findViewById(R.id.proxyUsernameEditText);
        proxyPasswordEditText = findViewById(R.id.proxyPasswordEditText);
        saveButton = findViewById(R.id.saveButton);

        // Load current settings
        SharedPreferences prefs = getSharedPreferences("proxyPrefs", MODE_PRIVATE);
        proxyHostEditText.setText(prefs.getString("proxy_host", ""));
        proxyPortEditText.setText(String.valueOf(prefs.getInt("proxy_port", 0)));
        proxyUsernameEditText.setText(prefs.getString("proxy_username", ""));
        proxyPasswordEditText.setText(prefs.getString("proxy_password", ""));

        saveButton.setOnClickListener(v -> {
            String proxyHost = proxyHostEditText.getText().toString().trim();
            int proxyPort = 0;
            try {
                proxyPort = Integer.parseInt(proxyPortEditText.getText().toString().trim());
            } catch (NumberFormatException e) {
                // Handle error (e.g., show a message to the user)
            }
            String proxyUsername = proxyUsernameEditText.getText().toString().trim();
            String proxyPassword = proxyPasswordEditText.getText().toString().trim();

            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("proxy_host", proxyHost);
            editor.putInt("proxy_port", proxyPort);
            editor.putString("proxy_username", proxyUsername);
            editor.putString("proxy_password", proxyPassword);
            editor.apply();

            // Optionally, reconfigure the proxy settings immediately by calling setupProxyAuthentication()
            // or instructing the user to restart the activity/app.
            finish();
        });
    }
}
