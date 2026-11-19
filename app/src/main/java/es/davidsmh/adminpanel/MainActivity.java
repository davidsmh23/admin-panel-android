package es.davidsmh.adminpanel;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Envoltorio nativo del panel. Muestra una pantalla de acceso propia y, al pulsar,
 * lanza el flujo OIDC (oauth2-proxy -> Pocket ID) dentro del WebView. La sesion vive
 * en la cookie de oauth2-proxy; sin cookie no se carga el panel.
 */
public class MainActivity extends Activity {
    private static final String PANEL_HOST = "admin.davidsmh.es";
    private static final String ID_HOST = "id.davidsmh.es";
    private static final String PANEL_URL = "https://" + PANEL_HOST + "/";
    private static final String SIGN_IN_URL = PANEL_URL + "oauth2/sign_in?rd=%2F";
    private static final String COOKIE_NAME = "_oauth2_proxy_admin";

    private WebView web;
    private LinearLayout loginView;
    private Button loginBtn;
    private TextView status;
    private ProgressBar spinner;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0F1115"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0F1115"));
        web.setVisibility(View.INVISIBLE);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        loginView = buildLogin();
        root.addView(loginView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        setupWebView();
        if (b == null) Updater.check(this);

        if (b != null) web.restoreState(b);
        else if (hasSession()) {
            showLoading("Entrando…");
            web.loadUrl(PANEL_URL);
        } else showLogin(null);
    }

    private LinearLayout buildLogin() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(dp(32), dp(32), dp(32), dp(32));
        l.setBackgroundColor(Color.parseColor("#0F1115"));
        l.setClickable(true);

        TextView title = new TextView(this);
        title.setText("Admin Panel");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        l.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Inicia sesión con tu cuenta de Pocket ID para continuar");
        sub.setTextColor(Color.parseColor("#9AA3B2"));
        sub.setTextSize(15);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(8), 0, dp(32));
        l.addView(sub);

        loginBtn = new Button(this);
        loginBtn.setText("Entrar con Pocket ID");
        loginBtn.setAllCaps(false);
        loginBtn.setTextColor(Color.WHITE);
        loginBtn.setTextSize(16);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor("#4C8DFF"));
        g.setCornerRadius(dp(12));
        loginBtn.setBackground(g);
        loginBtn.setPadding(dp(24), dp(14), dp(24), dp(14));
        loginBtn.setOnClickListener(v -> {
            showLoading("Redirigiendo a Pocket ID…");
            web.loadUrl(SIGN_IN_URL);
        });
        l.addView(loginBtn, new LinearLayout.LayoutParams(-1, dp(52)));

        spinner = new ProgressBar(this);
        spinner.setVisibility(View.GONE);
        l.addView(spinner, new LinearLayout.LayoutParams(dp(40), dp(40)));

        status = new TextView(this);
        status.setTextColor(Color.parseColor("#9AA3B2"));
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(16), 0, 0);
        l.addView(status);
        return l;
    }

    private void setupWebView() {
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, false);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportMultipleWindows(false);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("https".equals(u.getScheme()) && (PANEL_HOST.equals(u.getHost()) || ID_HOST.equals(u.getHost())))
                    return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap f) {
                // Pantalla de Pocket ID: mostrar el WebView para que el usuario se autentique.
                if (ID_HOST.equals(Uri.parse(url).getHost())) revealWeb();
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                CookieManager.getInstance().flush();
                Uri u = Uri.parse(url);
                String p = u.getPath() == null ? "" : u.getPath();
                if (PANEL_HOST.equals(u.getHost()) && !p.startsWith("/oauth2") && hasSession()) revealWeb();
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    showLogin("No se pudo conectar. Comprueba tu conexión e inténtalo de nuevo.");
                }
            }
        });
    }

    private boolean hasSession() {
        String c = CookieManager.getInstance().getCookie(PANEL_URL);
        return c != null && c.contains(COOKIE_NAME);
    }

    private void revealWeb() {
        web.setVisibility(View.VISIBLE);
        loginView.setVisibility(View.GONE);
    }

    private void showLoading(String msg) {
        loginView.setVisibility(View.VISIBLE);
        loginBtn.setVisibility(View.GONE);
        spinner.setVisibility(View.VISIBLE);
        status.setText(msg);
    }

    private void showLogin(String msg) {
        web.setVisibility(View.INVISIBLE);
        loginView.setVisibility(View.VISIBLE);
        loginBtn.setVisibility(View.VISIBLE);
        spinner.setVisibility(View.GONE);
        status.setText(msg == null ? "" : msg);
    }

    @Override
    public void onBackPressed() {
        if (web.getVisibility() == View.VISIBLE && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
