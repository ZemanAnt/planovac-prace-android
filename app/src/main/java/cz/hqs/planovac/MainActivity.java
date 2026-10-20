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
import android.webkit.WebResourceError;
import android.graphics.Bitmap;
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
    private boolean initialLoadFailed;

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
        label.setText("Připojení k serveru Plánovače práce");
        label.setTextSize(20);
        view.addView(label);
        Uri previous = Uri.parse(baseUrl.isEmpty() ? "http://10.0.0.180:3000" : baseUrl);
        EditText ipInput = new EditText(this);
        ipInput.setSingleLine(true);
        ipInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        ipInput.setHint("IP adresa");
        ipInput.setText(previous.getHost() != null ? previous.getHost() : "10.0.0.180");
        view.addView(ipInput);
        EditText portInput = new EditText(this);
        portInput.setSingleLine(true);
        portInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        portInput.setHint("Port");
        portInput.setText(previous.getPort() > 0 ? String.valueOf(previous.getPort()) : "3000");
        view.addView(portInput);
        TextView note = new TextView(this);
        note.setText("V místní síti se automaticky použije HTTP. Telefon musí být ve stejné síti jako server.");
        view.addView(note);
        Button connect = new Button(this);
        connect.setText("Připojit");
        view.addView(connect);
        setContentView(view);
        connect.setOnClickListener(v -> {
            String host = ipInput.getText().toString().trim();
            String portText = portInput.getText().toString().trim();
            String[] octets = host.split("\\.", -1);
            boolean validIp = octets.length == 4;
            if (validIp) for (String octet : octets) {
                try {
                    int number = Integer.parseInt(octet);
                    if (number < 0 || number > 255 || octet.length() > 3 || octet.isEmpty()) validIp = false;
                } catch (NumberFormatException e) { validIp = false; }
            }
            if (!validIp) { ipInput.setError("Zadejte platnou IPv4 adresu, například 10.0.0.180"); return; }
            int port;
            try { port = Integer.parseInt(portText); }
            catch (NumberFormatException e) { portInput.setError("Zadejte číslo portu"); return; }
            if (port < 1 || port > 65535) { portInput.setError("Port musí být 1 až 65535"); return; }
            baseUrl = "http://" + host + ":" + port;
            prefs.edit().putString("server_url", baseUrl).apply();
            launch();
        });
    }
    private void launch() {
        initialLoadFailed = false;
        web = new WebView(this);
        setContentView(web);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame() && !initialLoadFailed) {
                    initialLoadFailed = true;
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Nepodařilo se otevřít server")
                        .setMessage("Adresa: " + request.getUrl() + "\\nChyba WebView (" + error.getErrorCode() + "): " + error.getDescription() + "\\n\\nZkontrolujte Wi-Fi a dostupnost stejné adresy v prohlížeči telefonu.")
                        .setPositiveButton("Zkusit znovu", (dialog, which) -> { initialLoadFailed = false; web.reload(); })
                        .setNegativeButton("Změnit adresu", (dialog, which) -> { web.destroy(); web = null; setup(); })
                        .show();
                }
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, android.webkit.WebResourceResponse response) {
                super.onReceivedHttpError(view, request, response);
                if (request.isForMainFrame() && !initialLoadFailed && response.getStatusCode() >= 400) {
                    initialLoadFailed = true;
                    new AlertDialog.Builder(MainActivity.this).setTitle("Server vrátil HTTP " + response.getStatusCode())
                        .setMessage("Adresa: " + request.getUrl() + "\\nZkontrolujte cílovou stránku nebo přihlášení.")
                        .setPositiveButton("Zkusit znovu", (dialog, which) -> { initialLoadFailed = false; web.reload(); })
                        .setNegativeButton("Změnit adresu", (dialog, which) -> { web.destroy(); web = null; setup(); }).show();
                }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri link = request.getUrl();
                Uri configured = Uri.parse(baseUrl);
                if (link.getHost() != null && link.getHost().equalsIgnoreCase(configured.getHost()) && link.getScheme().equalsIgnoreCase(configured.getScheme()) && link.getPort() == configured.getPort()) return false;
                if ("https".equalsIgnoreCase(link.getScheme()) || "http".equalsIgnoreCase(link.getScheme()) || "mailto".equalsIgnoreCase(link.getScheme()) || "tel".equalsIgnoreCase(link.getScheme())) {
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
