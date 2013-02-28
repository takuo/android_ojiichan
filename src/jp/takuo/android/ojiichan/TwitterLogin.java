package jp.takuo.android.ojiichan;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class TwitterLogin extends Activity {
    private static final String CALLBACK_URL = "oob"; // "ojiichan://oauthcallback/";
    private static final String LOG_TAG = "OjiichanLogin";
    private ProgressDialog mProgressDialog;
    private Twitter mTwitter;
    private RequestToken mRequestToken;
    private AccessToken mAccessToken;
    private WebView mWebView;
    private Button mButtonOK;
    private EditText mEditPIN;
    private Context mContext;
    private Intent mIntent;
    private Handler mHandler;

    class AsyncRequest extends AsyncTask<Void, String, Void> {
        public AsyncRequest() {
            mProgressDialog = new ProgressDialog(TwitterLogin.this);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        }

        protected void onProgressUpdate(String... progress) {
            mProgressDialog.setMessage(progress[0]);
        }

        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setTitle(R.string.dialog_title);
            mProgressDialog.show();
        }

        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            if(mProgressDialog != null &&
                mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
            if (mRequestToken != null) {
                mWebView.loadUrl(mRequestToken.getAuthorizationURL());
            } else {
                Toast.makeText(mContext, R.string.fail, Toast.LENGTH_LONG).show();
                setResult(Activity.RESULT_OK, mIntent);
                finish();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            publishProgress(getString(R.string.dialog_message));
            try {
                mRequestToken = mTwitter.getOAuthRequestToken(CALLBACK_URL);
            } catch (TwitterException e) {
                Log.d(LOG_TAG, "Cannot get request token: " + e.getMessage());
            }
            return null;
        }
    }

    class HTMLParser {
        public void log(String str) {
            Pattern p = Pattern.compile("<code>(\\d+)</code>");
            Matcher m = p.matcher(str);
            if (m.find()) {
                final String pin = m.group(1);
                Log.d("HTMLParser", "PIN CODE: "+ pin);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mEditPIN.setText(pin);
                        mEditPIN.setEnabled(true);
                        mButtonOK.setEnabled(true);
//                        verifyCode(pin);
                    }
                });
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled") // Be careful!
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getApplicationContext();
        mIntent = getIntent();
        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.login);
        mTwitter = new TwitterFactory().getInstance();
        mTwitter.setOAuthConsumer(Main.CONSUMER_KEY, Main.CONSUMER_SEC);

        mHandler = new Handler();

        mButtonOK = (Button)findViewById(R.id.buttonPIN);
        mButtonOK.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                verifyCode(mEditPIN.getText().toString());
            }
        });
        mEditPIN  = (EditText)findViewById(R.id.edit_code);
        mWebView = (WebView)findViewById(R.id.webview);
        mWebView.addJavascriptInterface(new HTMLParser(), "htmlParser");
        mWebView.setWebViewClient(new WebViewClient(){
            /*
            @Override
            public boolean shouldOverrideUrlLoading (WebView view, String url) {
                if (url.startsWith(CALLBACK_URL)) {
                    Uri uri = Uri.parse(url);
                    verify(uri);
                    setResult(Activity.RESULT_OK, mIntent);
                    finish();
                    return true;
                }
                return false;
            }*/
            @Override
            public void onPageFinished (WebView view, String url) {
                view.loadUrl("javascript:window.htmlParser.log(document.documentElement.outerHTML);");
            }
        });
        mWebView.getSettings().setAppCacheEnabled(false);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.clearCache(true);
        mWebView.clearFormData();
        getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_VISIBILITY_ON);
        final Activity activity = this;
        mWebView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView w, int p) {
                activity.setProgress(p * 100);
            }
        });
        AsyncRequest req = new AsyncRequest();
        req.execute();
    }

    /*
    private void verify(Uri uri) {
        if (uri != null) {
            String verifier = uri.getQueryParameter("oauth_verifier");
            try {
                mAccessToken = mTwitter.getOAuthAccessToken(mRequestToken, verifier);
                mTwitter.setOAuthAccessToken(mAccessToken);
                String name = mTwitter.getScreenName();
                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
                SharedPreferences.Editor editor = pref.edit();
                editor.putString(Main.PREF_ACCESS_TOKEN, mAccessToken.getToken());
                editor.putString(Main.PREF_ACCESS_TOKEN_SECRET, mAccessToken.getTokenSecret());
                editor.putString(Main.PREF_ACCOUNT, name);
                editor.commit();
                Toast.makeText(mContext, R.string.success, Toast.LENGTH_LONG).show();
            } catch (TwitterException e) {
                Log.d(LOG_TAG, "Cannot get AccessToken: " + e.getMessage());
            } catch (Exception e) {
                Log.d(LOG_TAG, "Cannot get AccessToken: " + e.getMessage());
            }
        }
    }
    */

    private void verifyCode(String pin) {
        try {
            mAccessToken = mTwitter.getOAuthAccessToken(mRequestToken, pin);
            mTwitter.setOAuthAccessToken(mAccessToken);
            String name = mTwitter.getScreenName();
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
            SharedPreferences.Editor editor = pref.edit();
            editor.putString(Main.PREF_ACCESS_TOKEN, mAccessToken.getToken());
            editor.putString(Main.PREF_ACCESS_TOKEN_SECRET, mAccessToken.getTokenSecret());
            editor.putString(Main.PREF_ACCOUNT, name);
            editor.commit();
            Toast.makeText(mContext, R.string.success, Toast.LENGTH_LONG).show();
        } catch (TwitterException e) {
            Log.d(LOG_TAG, "Cannot get AccessToken: " + e.getMessage());
            Toast.makeText(mContext, R.string.fail, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.d(LOG_TAG, "Cannot get AccessToken: " + e.getMessage());
            Toast.makeText(mContext, R.string.fail, Toast.LENGTH_LONG).show();
        }
        setResult(Activity.RESULT_OK, mIntent);
        finish();
    }
}
