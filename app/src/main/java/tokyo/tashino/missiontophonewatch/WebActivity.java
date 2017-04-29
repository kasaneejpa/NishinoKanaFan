package tokyo.tashino.missiontophonewatch;

import android.app.AlertDialog;
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;


public class WebActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);

        Intent intent = getIntent();
        String url = intent.getStringExtra("URL");

        WebView webView = (WebView)findViewById(R.id.webFullView);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                view.loadUrl("file:///android_asset/error.html");
            }
        });
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAppCacheEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);

        try {
            webView.loadUrl(url);
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        WebView webView = (WebView)findViewById(R.id.webFullView);
        webView.loadUrl("file:///android_asset/stop.html");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_web, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            new AlertDialog.Builder(this).setTitle("このアプリケーションについて").
                    setPositiveButton("閉じる",null).
                    setMessage("Nishino Kana Fanは西野カナさんのYoutube,Facebook,Blogなどをランダムに配信する純粋な応援アプリです。またカナやんに関係のあるキーワードで自動検索して情報を表示するチャンネルもあります。半月でチャンネル更新します。無料、広告なしです。自動検索機能等は特許取得済みです。特許第5885115号。").
                    create().show();

            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
