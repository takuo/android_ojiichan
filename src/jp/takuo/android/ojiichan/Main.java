package jp.takuo.android.ojiichan;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.http.AccessToken;
import twitter4j.http.RequestToken;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.Toast;

public class Main extends Activity implements
    DialogInterface.OnClickListener, DialogInterface.OnCancelListener {

    static final private int REQUEST_CODE = 0;
    static final private String PREF_ACCESS_TOKEN = "access_token";
    static final private String PREF_ACCESS_TOKEN_SECRET = "access_token_secret";
    static final private String CONSUMER_KEY = "rviVIoBF91o4eaJC5jssA";
    static final private String CONSUMER_SEC = "fBm4tbRozD9r6TZWH1M4MDgQsbSxjmr5AchOWyULGks";
    static final private String LOG_TAG = "OJIICHAN";
    static final public String CALLBACK_URL = "http://ojiichan.localnet/";
    static private int mActionType = 0; // { 1: gabari, 2: batari, 3: furoha, 4: furoa }
    static private final int ACTION_NONE   = 0;
    static private final int ACTION_GABARI = 1;
    static private final int ACTION_BATARI = 2;
    static private final int ACTION_FUROHA = 3;
    static private final int ACTION_FUROA  = 4;
    static private boolean mAuthed = false;
    static private Twitter mTwitter;
    static private RequestToken mRequestToken;
    static private AccessToken mAccessToken;
    static private ProgressDialog mProgressDialog;
    private Context mContext;
    private MenuItem mItem;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getApplicationContext();
        setContentView(R.layout.main);
        ImageView image = (ImageView)findViewById(R.id.mainimage);
        image.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // FIXME: make better ImageMap implementation.
                float x;
                float y;
                int w = v.getWidth();
                int h = v.getHeight();
                int curAction = ACTION_NONE;
                float xscale = (float)w / (float)438;
                float yscale = (float)h / (float)600;

                x = event.getX() / xscale;
                y = event.getY() / yscale;
                // Log.d(LOG_TAG, "x: " + x + ", y:" + y);
                if (x >= 90 && x <= 210 &&
                        y >= 300 && y <= 425) curAction = ACTION_BATARI;
                else if (x >= 110 && x <= 200 &&
                        y >= 450 && y <= 540) curAction = ACTION_GABARI;
                else if (x >= 240 && x <= 350 &&
                        y >= 300 && y <= 430) curAction = ACTION_FUROHA;
                else if (x >= 245 && x <= 370 &&
                        y >= 440 && y <= 550) curAction = ACTION_FUROA;
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    mActionType = curAction;
                    Log.d(LOG_TAG, "DOWN ACTION: " + curAction);
                } else if (event.getAction() == MotionEvent.ACTION_UP ) {
                    Log.d(LOG_TAG, "UP ACTION: " + curAction);
                    if (mActionType == curAction) doAction(mActionType);
                }
                return true;
            }
        });
        isAuthed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean ret = super.onCreateOptionsMenu(menu);
        if (mAuthed)
            mItem = menu.add(0, Menu.FIRST, Menu.NONE, R.string.logout);
        else
            mItem = menu.add(0, Menu.FIRST, Menu.NONE, R.string.login);
        return ret;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean ret = super.onOptionsItemSelected(item);
        if (mAuthed) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
            SharedPreferences.Editor editor = pref.edit();
            editor.putString(PREF_ACCESS_TOKEN, "");
            editor.putString(PREF_ACCESS_TOKEN_SECRET, "");
            editor.commit();
            mAuthed = false;
            Toast.makeText(mContext, R.string.logout_message, Toast.LENGTH_LONG).show();
            if (mItem != null) mItem.setTitle(R.string.login);
        } else {
            AsyncRequest req = new AsyncRequest();
            req.execute ();
        }
        return ret;
    }

    class AsyncUpdate extends AsyncTask<String, String, Void> {
        String mText;

        public AsyncUpdate() {
            mProgressDialog = new ProgressDialog(Main.this);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        }

        protected void onProgressUpdate(String... progress) {
            mProgressDialog.setMessage(progress[0]);
        }

        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setTitle(R.string.sending_title);
            mProgressDialog.show();
        }

        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            if(mProgressDialog != null &&
                mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
            Toast.makeText(mContext, mText, Toast.LENGTH_LONG).show();
        }

        @Override
        protected Void doInBackground(String... params) {
            String text = params[0];
            int retry = 0;
            publishProgress(String.format(getString(R.string.sending_message), text));
            while(retry <= 5) {
                try {
                    mTwitter.updateStatus(text);
                    mText = getString(R.string.success);
                    break;
                } catch (TwitterException e) {
                    if (e.getStatusCode() == 403) {
                        text = String.format("%s %d", params[0], System.currentTimeMillis());
                        retry ++;
                        if (retry > 5) {
                            mText = e.getMessage();
                        }
                        publishProgress(getString(R.string.avoid_limit));
                    } else {
                        mText = e.getMessage();
                        break;
                    }
                }
            }
            return null;
        }
    }

    class AsyncRequest extends AsyncTask<Void, String, Void> {
        public AsyncRequest() {
            mProgressDialog = new ProgressDialog(Main.this);
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
            Intent intent = new Intent(Main.this, TwitterLogin.class);
            intent.putExtra("auth_url", mRequestToken.getAuthorizationURL());
            Main.this.startActivityForResult(intent, REQUEST_CODE);
        }

        @Override
        protected Void doInBackground(Void... params) {
            publishProgress(getString(R.string.dialog_message));
            TwitterFactory factory = new TwitterFactory();
            mTwitter = factory.getInstance();
            mTwitter.setOAuthConsumer(CONSUMER_KEY, CONSUMER_SEC);
            try {
                mRequestToken = mTwitter.getOAuthRequestToken(CALLBACK_URL);
            } catch (TwitterException e) {
                Log.d(LOG_TAG, "Cannot get request token: " + e.getMessage());
            }
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resutCode, Intent data) {
        if (requestCode != REQUEST_CODE) return;
        try {
            mAccessToken =
                mTwitter.getOAuthAccessToken(mRequestToken, data.getExtras().getString("oauth_verifier"));
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
            SharedPreferences.Editor editor = pref.edit();
            editor.putString(PREF_ACCESS_TOKEN, mAccessToken.getToken());
            editor.putString(PREF_ACCESS_TOKEN_SECRET, mAccessToken.getTokenSecret());
            editor.commit();
            mAuthed = true;
            if (mItem != null) mItem.setTitle(R.string.logout);
        } catch (TwitterException e) {
            Log.d(LOG_TAG, "Cannot get AccessToken: " + e.getMessage());
            mAuthed = false;
            if (mItem != null) mItem.setTitle(R.string.login);
        } catch (Exception e) {
            Log.d(LOG_TAG, "Cannot get AccessToken: " + e.getMessage());
        }
    }

    private void isAuthed() {
        String token = PreferenceManager
        .getDefaultSharedPreferences(mContext)
        .getString(PREF_ACCESS_TOKEN, null);
        String secret = PreferenceManager
        .getDefaultSharedPreferences(mContext)
        .getString(PREF_ACCESS_TOKEN_SECRET, null);
        if (!TextUtils.isEmpty(token) && !TextUtils.isEmpty(secret)) {
            TwitterFactory factory = new TwitterFactory();
            mAccessToken = new AccessToken(token, secret);
            mTwitter = factory.getInstance();
            mTwitter.setOAuthConsumer(CONSUMER_KEY, CONSUMER_SEC);
            mTwitter.setOAuthAccessToken(mAccessToken);
            mAuthed = true;
            if (mItem != null) mItem.setTitle(R.string.logout);
        }
    }

    private void doAction(int action) {
        if (!mAuthed) {
            AlertDialog.Builder adb = new AlertDialog.Builder(this);
            adb.setTitle(R.string.app_name);
            adb.setMessage(R.string.require_login);
            adb.setNegativeButton(R.string.cancel, this);
            adb.setPositiveButton(R.string.login, this);
            adb.create().show();
            return;
        }

        String text;
        switch (action) {
        case ACTION_GABARI:
            text = getString(R.string.action_gabari);
            break;
        case ACTION_BATARI:
            text = getString(R.string.action_batari);
            break;
        case ACTION_FUROHA:
            text = getString(R.string.action_furoha);
            break;
        case ACTION_FUROA:
            text = getString(R.string.action_furoa);
            break;
        default:
            return;
        }
        Log.d(LOG_TAG, "TEXT: " + text);
        AsyncUpdate au = new AsyncUpdate();
        au.execute(text);
    }

    @Override
    public void onCancel(DialogInterface dialog) {
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            AsyncRequest req = new AsyncRequest();
            req.execute ();
        }
    }
}
