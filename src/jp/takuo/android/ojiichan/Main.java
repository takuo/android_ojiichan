package jp.takuo.android.ojiichan;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.http.AccessToken;
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

    private static final int REQUEST_CODE = 0;
    public static final String PREF_ACCOUNT = "account_name";
    public static final String PREF_ACCESS_TOKEN = "access_token";
    public static final String PREF_ACCESS_TOKEN_SECRET = "access_token_secret";
    public static final String CONSUMER_KEY = "rviVIoBF91o4eaJC5jssA";
    public static final String CONSUMER_SEC = "fBm4tbRozD9r6TZWH1M4MDgQsbSxjmr5AchOWyULGks";
    private static final String LOG_TAG = "OJIICHAN";
    private static int mActionType = 0; // { 1: gabari, 2: batari, 3: furoha, 4: furoa }
    private static final int ACTION_NONE   = 0;
    private static final int ACTION_GABARI = 1;
    private static final int ACTION_BATARI = 2;
    private static final int ACTION_FUROHA = 3;
    private static final int ACTION_FUROA  = 4;
    private static boolean mAuthed = false;
    public static Twitter mTwitter;
    public static AccessToken mAccessToken;
    public static String mScreenName;
    private ProgressDialog mProgressDialog;
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
        mTwitter = new TwitterFactory().getInstance();
        mTwitter.setOAuthConsumer(CONSUMER_KEY, CONSUMER_SEC);
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
            mTwitter.shutdown();
            mTwitter = new TwitterFactory().getInstance();
            mTwitter.setOAuthConsumer(CONSUMER_KEY, CONSUMER_SEC);
            mAuthed = false;
            Toast.makeText(mContext, R.string.logout_message, Toast.LENGTH_LONG).show();
            if (mItem != null) mItem.setTitle(R.string.login);
            this.setTitle(String.format("%s - %s", getString(R.string.app_name),
                    getString(R.string.not_loggedin)));
        } else {
            Intent intent = new Intent(Main.this, TwitterLogin.class);
            Main.this.startActivityForResult(intent, REQUEST_CODE);
        }
        return ret;
    }

    class AsyncUpdate extends AsyncTask<String, String, Void> {
        private String mText;

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
                    } else if (e.getStatusCode() == 401) {
                        mText = getString(R.string.oauth_fail);
                        break;
                    } else {
                        mText = e.getMessage();
                        break;
                    }
                }
            }
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resutCode, Intent data) {
        if(requestCode == REQUEST_CODE) isAuthed();
    }

    private void isAuthed() {
        mScreenName = PreferenceManager
        .getDefaultSharedPreferences(mContext)
        .getString(PREF_ACCOUNT, null);
        String token = PreferenceManager
        .getDefaultSharedPreferences(mContext)
        .getString(PREF_ACCESS_TOKEN, null);
        String secret = PreferenceManager
        .getDefaultSharedPreferences(mContext)
        .getString(PREF_ACCESS_TOKEN_SECRET, null);

        if (!TextUtils.isEmpty(token) && !TextUtils.isEmpty(secret)) {
            mAccessToken = new AccessToken(token, secret);
            mTwitter.setOAuthAccessToken(mAccessToken);
            mAuthed = true;
            if (mScreenName == null) {
                try {
                    mScreenName = mTwitter.getScreenName();
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putString(PREF_ACCOUNT, mScreenName);
                    editor.commit();
                } catch (Exception e) {
                    Log.d(LOG_TAG, "Error: " + e.getMessage());
                }
            }
            this.setTitle(String.format("%s - %s", getString(R.string.app_name), mScreenName));
            if (mItem != null) mItem.setTitle(R.string.logout);
        } else {
            if (mItem != null) mItem.setTitle(R.string.login);
            this.setTitle(String.format("%s - %s", getString(R.string.app_name), getString(R.string.not_loggedin)));
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
        new AsyncUpdate().execute(text);
    }

    @Override
    public void onCancel(DialogInterface dialog) {
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            Intent intent = new Intent(Main.this, TwitterLogin.class);
            Main.this.startActivityForResult(intent, REQUEST_CODE);
        }
    }
}
