package jp.takuo.android.ojiichan;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.http.AccessToken;
import twitter4j.http.RequestToken;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Toast;

public class TwitterLogin extends Activity {
    private static final String CALLBACK_URL = "ojiichan://oauthcallback/";
    private static final String LOG_TAG = "OjiichanLogin";
    private ProgressDialog mProgressDialog;
    private Twitter mTwitter = Main.mTwitter;
    private RequestToken mRequestToken;
    private AccessToken mAccessToken = Main.mAccessToken;
    private WebView mWebView;
    private Context mContext;
    private Intent mIntent;

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getApplicationContext();
        mIntent = getIntent();
        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.login);
        mWebView = (WebView)findViewById(R.id.webview);
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

    @Override
    protected void onNewIntent(Intent intent) {
        Uri uri = intent.getData();
        if (uri != null && uri.toString().startsWith(CALLBACK_URL)) {
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
            setResult(Activity.RESULT_OK, mIntent);
            finish();
        }
    }
}
