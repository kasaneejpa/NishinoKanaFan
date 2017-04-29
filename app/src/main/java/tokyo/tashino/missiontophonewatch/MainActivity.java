package tokyo.tashino.missiontophonewatch;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends ActionBarActivity {
    public static final int NOTIFICATION_ID = 1;
    //コンテンツ再読み込み時間
    public static final long RELOAD_TIME_BY_HOUR =3;
    //WEBブラウザ自動スクロールの時間と速度指定
    public static final long SCROLL_DELAY_TIME =1000*2;
    public static final long SCROLL_SPAN_TIME =100;
    public static final long SCROLL_STEP=19;
    //検索エンジンのURL
    public static final String SEARCH_ENGINE_BING = "http://www.bing.com/search?q=";
    public static final String SEARCH_ENGINE_GOOGLE = "http://www.google.co.jp/search?q=";
    public static final String SEARCH_ENGINE_EXCITE = "http://websearch.excite.co.jp/?q=";
    //セーフサーチ　URLパラメータ
    public static final String SAFE_SEARCH_GOOGLE = "&safe=active";
    public static final String SAFE_SEARCH_BING = "&adlt=strict";
    public static final String SAFE_SEARCH_EXCITE = "&safe=active";
    //コンテンツデータ
    private SharedDataClass sharedDataClass = null;
    //現在選択中のチャンネル
    private String selectedChannelName;
    //配信タイマー
    private Timer mTimer   = null;
    //コンテンツ読み込みタイマー
    private Timer contentsRefreshTimer = null;
    //WEBブラウザスクロールタイマー
    private Timer scrollTimer = null;
    //タイマーからUIクラスへのPOST用ハンドラ
    private Handler mHandler = new Handler();   //UI Threadへのpost用ハンド
    private Handler mHandler2 = new Handler();   //UI Threadへのpost用ハンド
    private Handler mHandler3 = new Handler();   //UI Threadへのpost用ハンド
    //ブラウザスクロール位置
    private int scrollY;
    //スクロールするかどうか　trueかfalse
    private String isScroll;
    //チャンネ内データ選択用ランダムクラス
    private Random mRandom;
    //チャンネルセレクトダイアログ
    private AlertDialog selectDlg = null;
    private String currentUrl;
    //現在選択中のサーチエンジン
    private String currentEngine = SEARCH_ENGINE_GOOGLE;
    private String currentSafeSearch = SAFE_SEARCH_GOOGLE;
    //モバイル通信環境でユーザーが１度でもOKしたかどうか
    private boolean isMobileOk = false;
    //WEBローディングのペンディング用フラグ
    private boolean isLoadPending = false;
    //通信状態取得用のブロードキャストレシーバ
    private BroadcastReceiver myReceiver;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //コンテンツ読み込み処理
        if(contentsLoad() == false) return;

        //サーチエンジンランダム選択処理
        Random random = new Random();
        int dice = random.nextInt(3);
        switch (dice){
            case 0:
                currentEngine = SEARCH_ENGINE_GOOGLE;
                currentSafeSearch = SAFE_SEARCH_GOOGLE;
                break;
            case 1:
                currentEngine = SEARCH_ENGINE_BING;
                currentSafeSearch = SAFE_SEARCH_BING;
                break;
            case 2:
                currentEngine = SEARCH_ENGINE_EXCITE;
                currentSafeSearch = SAFE_SEARCH_EXCITE;
                break;
        }

        //配信間隔設定　WIFI接続時は１５秒、MOBILE接続時は１０分に設定
        final RadioGroup rg = (RadioGroup) findViewById(R.id.radioGroup);
        final RadioButton rb5min = (RadioButton) findViewById(R.id.radioButton5min);
        final RadioButton rb10min = (RadioButton) findViewById(R.id.radioButton10min);
        final RadioButton rb15sec = (RadioButton) findViewById(R.id.radioButton15sec);
        final RadioButton rb30sec = (RadioButton) findViewById(R.id.radioButton30sec);
        final RadioButton rb1min = (RadioButton) findViewById(R.id.radioButton1min);
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo nInfo = cm.getActiveNetworkInfo();
        if(nInfo==null){
            new AlertDialog.Builder(this).setTitle("通信環境がありません。").
                    setNegativeButton("終了します。", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    }).
                    setMessage("このアプリは通信環境が前提です。終了します。").
                    setCancelable(false).
                    create().show();
            rg.check(rb10min.getId());
        }
       else {
            if (nInfo.isConnected()) {
            /* NetWork接続可 */
                if (nInfo.getTypeName().equals("WIFI")) {
                    rg.check(rb1min.getId());
                } else if (nInfo.getTypeName().equals("MOBILE")) {
                    rg.check(rb1min.getId());
                } else{
                    rg.check(rb1min.getId());
                }
            }else{
                rg.check(rb10min.getId());
            }
        }
        //配信間隔変更時に配信開始処理
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                notificationStart(false);
            }
        });

        //WEBブラウザ初期設定、LOADエラーじにエラー画面表示処理を仕込む
        WebView webView = (WebView) findViewById(R.id.webView);
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
        settings.setDefaultTextEncodingName("UTF-8");

        //WEBブラウザがタッチされたら配信間隔を5分以上にしスクロールを止める。
        webView.setOnTouchListener(new WebView.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                stopScroll();
                rg.setOnCheckedChangeListener(null);
                int i = rg.getCheckedRadioButtonId();
                if (i == rb15sec.getId() || i == rb30sec.getId() || i== rb1min.getId()) {
                    rg.check(rb5min.getId());
                }
                rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(RadioGroup group, int checkedId) {
                        notificationStart(false);
                    }
                });
                notificationStart(true);
                return false;
            }
        });
        //チャンネルないデータ選択用RANDAMクラス初期化
        mRandom = new Random();

        //NWの状態変化を受け取るレシーバ作成
        myReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                checkNWStatus();
            }
        };
        final IntentFilter filter=new IntentFilter();
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        registerReceiver(myReceiver, filter);


        //コンテンツリフレッシュタイマー
        if (contentsRefreshTimer != null) {
            contentsRefreshTimer.cancel();
            contentsRefreshTimer.purge();
        }
        contentsRefreshTimer = new Timer();
        contentsRefreshTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // mHandler2を通じてUI Threadへ処理をキューイング
                mHandler2.post(new Runnable() {
                    public void run() {
                        notificationStop();
                        if(contentsLoad() == false) return;;
                        isLoadPending = true;
                        new AlertDialog.Builder(MainActivity.this).setTitle("続けますか？").
                                setPositiveButton("続けます。", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        isLoadPending = false;
                                        notificationStart(false);
                                    }
                                }).
                                setNegativeButton("終了します。", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        myFinalize();
                                        finish();
                                    }
                                }).
                                setMessage("チャンネル表を再読み込みしました。またアプリケーションが起動して" + RELOAD_TIME_BY_HOUR + "時間たちます。通信代が高額にならないように一旦配信を止めています。続けますか？").
                                setCancelable(false).
                                create().show();
                        notificationStart(false);
                    }
                });
            }
        }, 1000*60*60*RELOAD_TIME_BY_HOUR, 1000*60*60*RELOAD_TIME_BY_HOUR);
    }

    //停止時にWEBブラウザで同的処理が走り続けないように、静的コンテンツを表示しておく
    //停止中は通信しないようにする。
    @Override
    protected void onStop() {
        super.onStop();
        notificationStop();
        WebView webView = (WebView) findViewById(R.id.webView);
        showWebView(webView,"file:///android_asset/stop.html");
    }

    //コンテンツロード処理
    private  boolean contentsLoad(){
        sharedDataClass = new SharedDataClass(this);
        //非同期処理開始
        sharedDataClass.execute();
        int i=0;
        //LOAD処理終了まで、進捗を表示
        while(sharedDataClass.getIsLoadDone()==false){
            i++;
            Toast.makeText(this,"Loading...phase "+i,Toast.LENGTH_SHORT).show();
            if (i == 20) {
                new AlertDialog.Builder(this).setTitle("コンテンツをダウンロードできませんでした。").
                        setPositiveButton("閉じる", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .setMessage("サーバにアクセスが集中しているなどの原因が考えられます。終了します。").
                        create().show();
                return false;
            }
            try{
                Thread.sleep(1000);
            }catch(InterruptedException e){
                e.printStackTrace();
            }
        }
        //先頭チャンネルをUIに表示、メンバに設定
        ArrayList<String> channelNameArray = sharedDataClass.getChannelNameArray();
        TextView tv = (TextView) findViewById(R.id.selectChannelTxt);
        tv.setText(channelNameArray.get(0));
        selectedChannelName = channelNameArray.get(0);
        return true;
    }
    //コンテンツ読み込みタイマーをキャンセル
    private void stopContentsReload(){
        if (contentsRefreshTimer != null) {
            contentsRefreshTimer.cancel();
            contentsRefreshTimer.purge();
        }
        contentsRefreshTimer = null;
    }
    //WEB　表示とスクロールスタート
    public void showWebView(WebView webView,String url){
        if(isLoadPending == false) {
            webView.loadUrl(url);
            if(isScroll.equals("true")) {
                startScroll(webView);
            }
        }
    }
    //WEBスクロール停止
    private void stopScroll(){
        if (scrollTimer != null) {
            scrollTimer.cancel();
            scrollTimer.purge();
        }
        scrollTimer = null;
    }
    //WEBスクロール開始
    private void startScroll(final WebView v){
        scrollY = 0;
        if (scrollTimer != null) {
            scrollTimer.cancel();
            scrollTimer.purge();
        }
        scrollTimer = new Timer();
        scrollTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // mHandler2を通じてUI Threadへ処理をキューイング
                mHandler3.post(new Runnable() {
                    public void run() {
                        if( v.getContentHeight() * v.getScale() - v.getHeight() > scrollY) {
                            scrollY += SCROLL_STEP;
                            v.scrollTo(0, scrollY);
                        }
                    }
                });
            }
        }, SCROLL_DELAY_TIME, SCROLL_SPAN_TIME);
    }
    //ネットワーク状態をチェック、初めてモバイル接続になるときには継続の意思確認をする。
    private void checkNWStatus(){
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo nInfo = cm.getActiveNetworkInfo();

        if (nInfo == null) {
            return;
        }

        if (nInfo.isConnected()) {
            /* NetWork接続可 */
            if (nInfo.getTypeName().equals("WIFI")) {

            } else if (nInfo.getTypeName().equals("MOBILE")) {
                if(isMobileOk == false) {
                    notificationStop();
                    isLoadPending = true;
                    new AlertDialog.Builder(this).setTitle("モバイル接続です。").
                            setPositiveButton("了解しました。", new DialogInterface.OnClickListener(){
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    isMobileOk = true;
                                    isLoadPending = false;
                                    notificationStart(false);
                                }
                            }).
                            setNegativeButton("終了します。", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    myFinalize();
                                    finish();
                                }
                            }).
                            setMessage("モバイル接続では通信代が高額になる恐れがあります。").
                            setCancelable(false).
                            create().show();
                }
            }

        } else {
        }
    }
    //終了処理、ブロードキャストレシーバの解除、各種タイマーの停止
    private void myFinalize(){
        unregisterReceiver(myReceiver);
        stopScroll();
        notificationStop();
        stopContentsReload();
    }
    //大画面が押されたときに配信とWEBスクロールを停止して別画面表示
    public void onFullViewClick(View v){
        notificationStop();
        stopScroll();

        WebView webView = (WebView) findViewById(R.id.webView);
        String url = webView.getUrl();
        webView.loadUrl("file:///android_asset/stop.html");

        Intent intent = new Intent(this,WebActivity.class);
        intent.putExtra("URL", url);
        startActivity(intent);

    }
    //画面が再表示されたとき、配信を自動開始
    @Override
    protected void onResume() {
        super.onResume();
        notificationStart(false);
    }
    //チャンネル選択処理
    public void onChannelClick(View v){

        final ArrayList<String> rows = sharedDataClass.getChannelNameArray();

        ListView lv = new ListView(this);
        lv.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, rows));
        lv.setScrollingCacheEnabled(false);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> items, View view, int position, long id) {
                selectedChannelName = rows.get(position).toString();
                TextView tv = (TextView) findViewById(R.id.selectChannelTxt);
                tv.setText(selectedChannelName);
                notificationStart(false);
                selectDlg.dismiss();
            }
        });
        selectDlg = new AlertDialog.Builder(this)
                .setTitle("チャンネルを選択")
                .setPositiveButton("キャンセル", null)
                .setView(lv)
                .create();

        selectDlg.show();
    }
    //配信停止ボタンが押されたときの処理
    public void onNotificationStop(View v){
        RadioGroup rg = (RadioGroup)findViewById(R.id.radioGroup);
        rg.clearCheck();
        notificationStop();
    }
    //配信停止処理
    private void notificationStop(){
        if(mTimer != null) {
            mTimer.cancel();
            mTimer.purge();
        }
        mTimer = null;
    }
    //配信開始ボタン、次ボタンが押された時、通知間隔をチェックして配信開始処理
    public void onNotificationFire(View v){
        RadioGroup rg = (RadioGroup)findViewById(R.id.radioGroup);
        if(rg.getCheckedRadioButtonId() <0){
            new AlertDialog.Builder(this).setTitle("通知間隔を指定してください。").
                    setPositiveButton("閉じる", null).
                    create().show();
            return;
        }
        notificationStart(false);
    }
    //配信開始処理本体
    private void notificationStart(boolean delayed){
        RadioGroup rg = (RadioGroup)findViewById(R.id.radioGroup);
        RadioButton rb15sec= (RadioButton)findViewById(R.id.radioButton15sec);
        RadioButton rb30sec= (RadioButton)findViewById(R.id.radioButton30sec);
        RadioButton rb1min= (RadioButton)findViewById(R.id.radioButton1min);
        RadioButton rb5min= (RadioButton)findViewById(R.id.radioButton5min);
        RadioButton rb10min= (RadioButton)findViewById(R.id.radioButton10min);
        int spanInt=1000*5;
        int delayInt=0;
        int i=rg.getCheckedRadioButtonId();
        if(i == rb15sec.getId()){
            spanInt = 1000*15;
        }else if(i==rb30sec.getId()){
            spanInt = 1000*30;
        }else if(i==rb1min.getId()){
            spanInt = 1000*60;
        }else if(i==rb5min.getId()){
            spanInt = 1000*60*5;
        }else if(i==rb10min.getId()){
            spanInt = 1000*60*10;
        }else{
            return;
        }
        if (delayed == true){
            delayInt = spanInt;
        }
        notificationStop();

        mTimer = new Timer();

        mTimer.schedule( new TimerTask(){
            @Override
            public void run() {
                // mHandlerを通じてUI Threadへ処理をキューイング
                mHandler.post( new Runnable() {
                    public void run() {
                        TextView missionTv = (TextView)findViewById(R.id.missionTxt);
                        ArrayList<Map<String,String>> channel = sharedDataClass.getChannnelByName(selectedChannelName);
                        if(channel ==null){
                            Toast.makeText(MainActivity.this,"Hello",Toast.LENGTH_LONG).show();
                        }
                        int channelSize = channel.size();
                        int i = mRandom.nextInt(channelSize);
                        String missionTxt = channel.get(i).get("title");
                        String missionUrl = channel.get(i).get("description");
                        String link = channel.get(i).get("link");
                        isScroll = channel.get(i).get("scroll");

                        missionTv.setText(missionTxt);
                        WebView webView = (WebView)findViewById(R.id.webView);

                        if(link.length()>0){
                            try {
                                currentUrl = link;
                                showWebView(webView, currentUrl);
                            }
                            catch(Exception e){
                                e.printStackTrace();
                            }
                        }else {
                            try {
                                currentUrl = currentEngine + URLEncoder.encode(missionUrl, "UTF-8"); /*+currentSafeSearch*/;
                                showWebView(webView,currentUrl);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        displayNotification(channel.get(i).get("title"));
                    }
                });
            }
        }, delayInt, spanInt);
    }

    //おすすめ文字列を時計や通知領域に表示
    private void displayNotification(String mission) {

        NotificationManagerCompat.from(this).cancelAll();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

        builder.setSmallIcon(R.drawable.smallicon);
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.kana);
        builder.setLargeIcon(bitmap);
        builder.setDefaults(0);
        builder.setOnlyAlertOnce(false);

        builder.setContentTitle("Kana Info!");
        builder.setContentText(mission);
        builder.setVisibility(Notification.VISIBILITY_PUBLIC);

        NotificationCompat.BigTextStyle bigStyle = new
                NotificationCompat.BigTextStyle();
        bigStyle.bigText(mission);
        builder.setStyle(bigStyle);

        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
    //メニュー作成
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    //メニュー操作のコールバック
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_about) {
            new AlertDialog.Builder(this).setTitle("このアプリケーションについて").
                    setPositiveButton("閉じる", null).
                    setMessage("Nishino Kana Fanは西野カナさんのYoutube,Facebook,Blogなどをランダムに配信する純粋な応援アプリです。またカナやんに関係のあるキーワードで自動検索して情報を表示するチャンネルもあります。半月でチャンネル更新します。無料です。自動検索機能等は特許取得済みです。特許第5885115号").
                    create().show();
            return true;
        }
        if (id == R.id.action_mail) {
            new AlertDialog.Builder(this).setTitle("検索キーワード募集").
                    setPositiveButton("閉じる", null).
                    setNeutralButton("作者にメールを送る", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // インテントのインスタンス生成
                            Intent intent = new Intent();
                            intent.setAction(Intent.ACTION_SENDTO);
                            //宛先をセット
                            intent.setData(Uri.parse("mailto:missionnotify@apost.plala.or.jp"));
                            //標題をセット
                            intent.putExtra(Intent.EXTRA_SUBJECT, "西野カナさんを応援する意見や検索して欲しいキーワードを送ります。");
                            startActivity(intent);
                        }
                    }).
                    setMessage("西野カナさんに関係する検索キーワードの組み合わせを募集しています。こんな検索面白いよというのがあったらアプリ作者にメールください。お勧めのサイトも教えて,サイト集チャンネルも企画中！よければ採用します。みんなでカナやんを応援しよう！ missionnotify@apost.plala.or.jp まで").
                    create().show();
            return true;
        }
        if (id == R.id.action_settings) {
            new AlertDialog.Builder(this).setTitle("検索エンジンの設定").
                    setPositiveButton("Google", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            currentEngine = SEARCH_ENGINE_GOOGLE;
                            currentSafeSearch = SAFE_SEARCH_GOOGLE;
                        }
                    }).
                    setNegativeButton("Bing", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            currentEngine = SEARCH_ENGINE_BING;
                            currentSafeSearch = SAFE_SEARCH_BING;
                        }
                    }).
                    setNeutralButton("excite", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            currentEngine = SEARCH_ENGINE_EXCITE;
                            currentSafeSearch = SAFE_SEARCH_EXCITE;
                        }
                    }).
                    create().show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
