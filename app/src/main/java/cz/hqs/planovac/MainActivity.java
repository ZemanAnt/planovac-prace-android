package cz.hqs.planovac;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int PICK = 101, CAMERA = 102;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest cameraRequest;
    private SharedPreferences prefs;
    private String baseUrl;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        prefs = getSharedPreferences("hqs_android", MODE_PRIVATE);
        baseUrl = prefs.getString("server_url", "");
        if (baseUrl.isEmpty()) setup(); else launch();
    }
    private void setup() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(24 * getResources().getDisplayMetrics().density);
        view.setPadding(pad, pad, pad, pad);
        TextView label = new TextView(this);
        label.setText("Zadejte adresu Plánovače práce (HTTPS), například https://server.example.cz. Nezabezpečené HTTP funguje pouze v místní síti.");
        view.addView(label);
        EditText input = new EditText(this);
        input.setSingleLine();
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://adresa-serveru");
        input.setText(baseUrl);
        view.addView(input);
        Button connect = new Button(this);
        connect.setText("Připojit");
        view.addView(connect);
        setContentView(view);
        connect.setOnClickListener(v -> {
            String value = input.getText().toString().trim().replaceAll("/+$", "");
            Uri uri = Uri.parse(value);
            String host = uri.getHost(), scheme = uri.getScheme();
            boolean local = host != null && (host.equalsIgnoreCase("localhost") || host.endsWith(".local") || host.startsWith("192.168.") || host.startsWith("10.") || host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..+"));
            if (host == null || uri.getUserInfo() != null || uri.getPort() > 65535 || !("https".equalsIgnoreCase(scheme) || ("http".equalsIgnoreCase(scheme) && local))) {
                input.setError("Zadejte platnou HTTPS adresu nebo místní HTTP adresu.");
                return;
            }
            baseUrl = value;
            prefs.edit().putString("server_url", value).apply();
            launch();
        });
    }
    private void launch() {
        web = new WebView(this);
        setContentView(web);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri link = request.getUrl();
                Uri configured = Uri.parse(baseUrl);
                if (link.getHost() != null && link.getHost().equalsIgnoreCase(configured.getHost()) && link.getScheme().equalsIgnoreCase(configured.getScheme()) && link.getPort() == configured.getPort()) return false;
                if ("https".equalsIgnoreCase(link.getScheme()) || "mailto".equalsIgnoreCase(link.getScheme()) || "tel".equalsIgnoreCase(link.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, link)); } catch (Exception ignored) {}
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try { startActivityForResult(params.createIntent(), PICK); }
                catch (Exception e) { fileCallback.onReceiveValue(null); fileCallback = null; return false; }
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    Uri configured = Uri.parse(baseUrl);
                    if (request.getOrigin().getHost() == null || !request.getOrigin().getHost().equalsIgnoreCase(configured.getHost()) || !request.getOrigin().getScheme().equalsIgnoreCase(configured.getScheme())) { request.deny(); return; }
                    boolean asksCamera = false;
                    for (String resource : request.getResources()) if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) asksCamera = true;
                    if (!asksCamera) { request.deny(); return; }
                    cameraRequest = request;
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) grantCamera();
                    else requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA);
                });
            }
        });
        web.loadUrl(baseUrl + "/");
    }
    private void grantCamera() {
        if (cameraRequest != null) {
            cameraRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            cameraRequest = null;
        }
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == CAMERA && cameraRequest != null) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) grantCamera();
            else { cameraRequest.deny(); cameraRequest = null; }
        }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }
    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else if (web != null) new AlertDialog.Builder(this)
            .setTitle("Plánovač práce")
            .setItems(new String[]{"Změnit server", "Zavřít aplikaci"}, (dialog, index) -> {
                if (index == 0) { web.destroy(); web = null; setup(); }
                else finish();
            }).show();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
