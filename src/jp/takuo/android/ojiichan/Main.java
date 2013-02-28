package jp.takuo.android.ojiichan;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import android.R.integer;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Display;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.util.DisplayMetrics;

import java.util.Timer;
import java.util.TimerTask;

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
    private static final int MAGIC_HEIGHT = -19; // なんかy座標ずれるので調整してたらこの値だったけど詳細不明
    private static boolean mAuthed = false;
    private Twitter mTwitter;
    private AccessToken mAccessToken;
    private String mScreenName;
    private Context mContext;
    private MenuItem mItem;
    private ImageView mMainImage;
    private ImageView mScreenImage;
    private ImageView mBatariButton;
    private ImageView mGabariButton;
    private ImageView mFurohaButton;
    private ImageView mFuroaButton;
    private ImageView mParticle;
    private float mScaleX;
    private float mScaleY;
    private float mResizeX;
    private float mResizeY;

    class ButtonInfo {
        private int x, y;
        private int width, height;
        private int offResourceId, onResourceId;
        private ImageView imageView;
        
        ButtonInfo(int offResourceId, int onResourceId, int x, int y, int width, int height, ImageView imageView) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.offResourceId = offResourceId;
            this.onResourceId = onResourceId;
            this.imageView = imageView;
        }

        int getX() { return x; }
        int getY() { return y; }
        int getWidth() { return width; }
        int getHeight() { return height; }
        int getOffResourceId() { return offResourceId; }
        int getOnResourceId() { return onResourceId; }
        ImageView getImageView() { return imageView; }

        void buttonOn() {
            setScaledImage(imageView, onResourceId);
        }

        void buttonOff() {
            setScaledImage(imageView, offResourceId);
        }
    }

    class AnimationTerminator extends TimerTask {
        private static final int DEFAULT_IMAGE_ID = 0;
        
        private AnimationDrawable animation;
        private ImageView imageView;
        private int imageId;
        private boolean disableOnTerminate;
        
        AnimationTerminator(AnimationDrawable animation, ImageView imageView, int imageId) {
            this.animation = animation;
            this.imageView = imageView;
            this.imageId = imageId;
            this.disableOnTerminate = false;
        }

        // for particle
        AnimationTerminator(AnimationDrawable animation, ImageView imageView, boolean disableOnTerminate) {
            this.animation = animation;
            this.imageView = imageView;
            this.imageId = DEFAULT_IMAGE_ID;
            this.disableOnTerminate = disableOnTerminate;
        }

        public void terminate() {
            if (animation != null) animation.stop();
            if (imageId != DEFAULT_IMAGE_ID) {
                setScaledImage(imageView, imageId);
            }
            if (disableOnTerminate) imageView.setVisibility(View.GONE);
        }

        @Override
        public void run() {
            mHandler.post(new Runnable() {
                public void run() {
                    terminate();
                }
            });
        }
    }

    private ButtonInfo mLastActionButton;
    private Handler mHandler;
    AnimationDrawable mAnimation;
    AnimationDrawable mParticleAnimation;
    private SparseArray<ButtonInfo> mButtonInfo;
    private Resources mResources;

    private void setScaledImage(ImageView imageView, int resource_id) {
        Bitmap bitmap = BitmapFactory.decodeResource(mResources, resource_id);
        Bitmap bitmap2 = Bitmap.createScaledBitmap(bitmap, (int)(bitmap.getWidth()* mResizeX), (int)(bitmap.getHeight() * mResizeY), false);
        imageView.setImageBitmap(bitmap2);
    }

    protected AnimationDrawable createScaledAnimation(ImageView imageView, int resource_id) {
        AnimationDrawable animationDrawable = (AnimationDrawable)(mResources.getDrawable(resource_id));
        AnimationDrawable retval = new AnimationDrawable();
        for (int i = 0; i < animationDrawable.getNumberOfFrames(); i++) {
            BitmapDrawable drawable = (BitmapDrawable)animationDrawable.getFrame(i);
            Bitmap bitmap = drawable.getBitmap();
            Bitmap bitmap2 = Bitmap.createScaledBitmap(bitmap, (int)(bitmap.getWidth()*mResizeX), (int)(bitmap.getHeight()*mResizeY), false);
            BitmapDrawable bd = new BitmapDrawable(getResources(), bitmap2);
            bd.setTargetDensity(bitmap.getDensity());
            retval.addFrame(bd, animationDrawable.getDuration(i));
        }
        imageView.setImageDrawable(retval);
        return retval;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getApplicationContext();
        setContentView(R.layout.main);
        mHandler = new Handler();
        mResources = getResources();
        mMainImage = (ImageView)findViewById(R.id.mainimage);
        mScreenImage = (ImageView)findViewById(R.id.screenimage);
        mBatariButton = (ImageView)findViewById(R.id.button_batari);
        mGabariButton = (ImageView)findViewById(R.id.button_gabari);
        mFurohaButton = (ImageView)findViewById(R.id.button_furoha);
        mFuroaButton = (ImageView)findViewById(R.id.button_furoa);
        mParticle = (ImageView)findViewById(R.id.particle);
        
        WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        Display disp = wm.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        disp.getMetrics(metrics);

        int titleBarHeight = 0;
        switch (metrics.densityDpi) {
        case 480: // DisplayMetrics.DENSITY_XXHIGH: (API Level 16)
            titleBarHeight = 96;
        case 320: // DisplayMetrics.DENSITY_XHIGH: (API Level 9)
            titleBarHeight = 64;
        case DisplayMetrics.DENSITY_HIGH:
            titleBarHeight = 48;
            break;
        case 213: //DisplayMetrics.DENSITY_TV: (API Level 13)
            titleBarHeight = 42;
            break;
        case DisplayMetrics.DENSITY_MEDIUM:
            titleBarHeight = 32;
            break;
        case DisplayMetrics.DENSITY_LOW:
            titleBarHeight = 24;
            break;
        }
        Log.d(LOG_TAG, "titleBarHeight = " + titleBarHeight);
        int w, h;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR2) {
            w = disp.getWidth();
            h = disp.getHeight() - titleBarHeight;
        } else {
            Point dispSize = new Point();
            disp.getSize(dispSize);
            w = dispSize.x;
            h = dispSize.y - titleBarHeight;
        }
        Log.d(LOG_TAG, "w: " + w + ", h: " + h);
        mScaleX = (float)w / (float)438;
        mScaleY = (float)h / (float)600;
        Log.d(LOG_TAG, "mScaleX: " + mScaleX + ", mScaleY:" + mScaleY);

        BitmapDrawable d = (BitmapDrawable)mMainImage.getDrawable();
        mResizeX = (float)w / (float)d.getBitmap().getWidth() ;
        mResizeY = (float)h / (float)d.getBitmap().getHeight();
        Log.d(LOG_TAG, "mResizeX: " + mResizeX + ", mResizeY:" + mResizeY);

        setScaledImage(mScreenImage, R.drawable.screen_blank);
        setScaledImage(mBatariButton, R.drawable.button_batari_off);
        setScaledImage(mGabariButton, R.drawable.button_gabari_off);
        setScaledImage(mFuroaButton, R.drawable.button_furoa_off);
        setScaledImage(mFurohaButton, R.drawable.button_furoha_off);

        mMainImage.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // TODO: make better ImageMap implementation.
                float x;
                float y;
                int curAction = ACTION_NONE;

                x = event.getX() / mScaleX;
                y = event.getY() / mScaleY;
                Log.d(LOG_TAG, "x: " + x + ", y:" + y);
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
                    if (mActionType != ACTION_NONE) {
                        ButtonInfo buttonInfo = mButtonInfo.get(mActionType);
                        buttonInfo.buttonOn();
                        mLastActionButton = buttonInfo;
                    }

                    Matrix matrix = new Matrix();
                    matrix.reset();
                    matrix.postScale(mScaleX, mScaleY);
                    mParticle.setImageMatrix(matrix);
                    mParticle.setPadding((int)(-75 * mScaleX + event.getX()), (int)(-75 + mScaleY +event.getY()), 0, 0);
                    mParticle.setVisibility(View.VISIBLE);
                    mParticle.setImageResource(R.drawable.animation_touch);
                    //if (mParticleAnimation != null) mParticleAnimation.terminate();
                    mParticleAnimation = (AnimationDrawable)mParticle.getDrawable();
                    mParticleAnimation.start();
                    new Timer().schedule(new AnimationTerminator(mParticleAnimation, mParticle, true), 4 * 100);
                } else if (event.getAction() == MotionEvent.ACTION_UP ) {
                    Log.d(LOG_TAG, "UP ACTION: " + curAction);
                    if (mLastActionButton != null) {
                        mLastActionButton.buttonOff();
                    }
                    if (mActionType == curAction) {
                        doAction(mActionType);
                        if (mLastActionButton != null) {
                            mLastActionButton.buttonOff();
                            mLastActionButton = null;
                        }
                    }
                }
                return true;
            }
        });

        mButtonInfo = new SparseArray<ButtonInfo>(5);
        mButtonInfo.put(ACTION_NONE, null);
        mButtonInfo.put(ACTION_BATARI, new ButtonInfo(R.drawable.button_batari_off, R.drawable.button_batari_on, 81, 292, 147, 152, mBatariButton));
        mButtonInfo.put(ACTION_GABARI, new ButtonInfo(R.drawable.button_gabari_off, R.drawable.button_gabari_on, 98, 443, 106, 111, mGabariButton));
        mButtonInfo.put(ACTION_FUROHA, new ButtonInfo(R.drawable.button_furoha_off, R.drawable.button_furoha_on, 228, 292, 133, 143, mFurohaButton));
        mButtonInfo.put(ACTION_FUROA, new ButtonInfo(R.drawable.button_furoa_off, R.drawable.button_furoa_on, 238, 438, 156, 129, mFuroaButton));

        Matrix matrix = new Matrix();
        matrix.reset();
        matrix.postScale(mScaleX, mScaleY);
        mScreenImage.setImageMatrix(matrix);
        mScreenImage.setPadding((int)(42 * mScaleX), (int)((50 + MAGIC_HEIGHT) * mScaleY), 0, 0);

        for (int i = 1; i < mButtonInfo.size(); i++) {
            ButtonInfo bi = mButtonInfo.get(i);
            matrix.reset();
            matrix.postScale(mScaleX, mScaleY);
            ImageView iv = bi.getImageView();
            iv.setImageMatrix(matrix);
            iv.setPadding((int)(bi.getX() * mScaleX), (int)((bi.getY() + MAGIC_HEIGHT) * mScaleY), 0, 0);
        }

        mParticle.setVisibility(View.GONE);
        
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
        }

        protected void onProgressUpdate(String... progress) {
        }

        protected void onPreExecute() {
            super.onPreExecute();
            mAnimation = createScaledAnimation(mScreenImage, R.drawable.animation_screen_post);
            mAnimation.setOneShot(false);
            mAnimation.start();
        }

        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            if (mText.equals(getString(R.string.success))) {
                mHandler.post(new Runnable() {
                    public void run() {
                        mAnimation.stop();
                        mAnimation = createScaledAnimation(mScreenImage, R.drawable.animation_screen_ok);
                        mAnimation.setOneShot(false);
                        mAnimation.start();
                        new Timer().schedule(new AnimationTerminator(mAnimation, mScreenImage, R.drawable.screen_blank), 3 * 1000);
                    }
                });
            } else {
                mHandler.post(new Runnable() {
                    public void run() {
                        setScaledImage(mScreenImage, R.drawable.screen_ng);
                        new Timer().schedule(new AnimationTerminator(null, mScreenImage, R.drawable.screen_blank), 3 * 1000);
                    }
                });
                Toast.makeText(mContext, mText, Toast.LENGTH_LONG).show();
            }
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_CODE && resultCode == RESULT_OK) isAuthed();
        else if (resultCode == RESULT_CANCELED) {
            mTwitter.shutdown();
            mTwitter = new TwitterFactory().getInstance();
            mTwitter.setOAuthConsumer(CONSUMER_KEY, CONSUMER_SEC);
        }
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
