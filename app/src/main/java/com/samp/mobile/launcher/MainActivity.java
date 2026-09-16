package com.samp.mobile.launcher;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.tabs.TabLayout;
import com.joom.paranoid.Obfuscate;
import com.samp.mobile.R;
import com.samp.mobile.launcher.adapters.FavouriteServerAdapter;
import com.samp.mobile.launcher.adapters.ServerAdapter;
import com.samp.mobile.launcher.config.Config;
import com.samp.mobile.launcher.data.FavoritesInfo;
import com.samp.mobile.launcher.fragments.HomeFragment;
import com.samp.mobile.launcher.fragments.ServerPagesItemFragment;
import com.samp.mobile.launcher.fragments.ServersFragment;
import com.samp.mobile.launcher.fragments.SettingsFragment;
import com.samp.mobile.launcher.util.ConfigValidator;
import com.samp.mobile.launcher.util.GpuDataManager;
import com.samp.mobile.launcher.util.SAMPServerInfo;
import com.samp.mobile.launcher.util.SampQueryAPI;
import com.samp.mobile.launcher.util.SharedPreferenceCore;
import com.samp.mobile.launcher.util.SignatureChecker;
import com.samp.mobile.launcher.util.ViewPagerWithoutSwipe;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
@Obfuscate
public class MainActivity extends AppCompatActivity {

    private static final int FIRST_LAUNCH_PERMISSIONS = 2406;

    public String[] tabTitles = { "Home", "Play", "Settings" };
    public int[] tabImages = { R.drawable.ic_mainmenu, R.drawable.ic_server, R.drawable.ic_settingsmenu};
    public int[] tabSelectedImages = { R.drawable.ic_mainmenu_on, R.drawable.ic_serveron, R.drawable.ic_settingsmenu_on};

    public static ArrayList<SAMPServerInfo> mServersList = new ArrayList<>();
    public static ArrayList<SAMPServerInfo> mFavoriteServersList = new ArrayList<>();

    boolean bAdsInitialized = false;

    private int iCountQueue = 0;

    private int retryAttempt;

    int i1 = 0;

    private final Handler serverRefreshHandler = new Handler();
    private boolean serverQueryRunning = false;
    private final Runnable serverRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            queryInfinityServer();
            serverRefreshHandler.postDelayed(this, 15000);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Config.currentContext = this;

        mServersList = new ArrayList<>();
        mFavoriteServersList = new ArrayList<>();

        ConfigValidator.validateConfigFiles(this);
        requestFirstLaunchPermissions();

        //if(!SignatureChecker.isSignatureValid(this, getPackageName()))
        //{
        //Toast.makeText(this, "Use original launcher! No remake", Toast.LENGTH_LONG).show();
        //return;
        //}


        File file = new File(getExternalFilesDir(null) + "/download/update.apk");
        if (file.exists()) {
            file.delete();
        }

        FragmentManager fm = getSupportFragmentManager();
        ViewPagerAdapter sa = new ViewPagerAdapter(fm);
        ViewPagerWithoutSwipe pa = findViewById(R.id.fragment_place);
        pa.setAdapter(sa);

        TabLayout tabLayout = findViewById(R.id.constraintLayout);

        tabLayout.setupWithViewPager(pa);

        for(int  i = 0; i < tabLayout.getTabCount(); i++)
        {
            View inflate = LayoutInflater.from(this).inflate(R.layout.tablayout_item, (ViewGroup) tabLayout, false);

            ImageView image = inflate.findViewById(R.id.imageView2);
            image.setBackgroundResource(tabImages[i]);

            Objects.requireNonNull(tabLayout.getTabAt(i)).setCustomView(inflate);

            tabLayout.clearOnTabSelectedListeners();
            tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                    pa.setCurrentItem(tab.getPosition(), true);
                    ((ImageView)tab.getCustomView().findViewById(R.id.imageView2)).setBackgroundResource(tabSelectedImages[tab.getPosition()]);
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {
                    ((ImageView)tab.getCustomView().findViewById(R.id.imageView2)).setBackgroundResource(tabImages[tab.getPosition()]);
                }

                @Override
                public void onTabReselected(TabLayout.Tab tab) {

                }
            });

        }
        ((ImageView)tabLayout.getTabAt(0).getCustomView().findViewById(R.id.imageView2)).setBackgroundResource(tabSelectedImages[0]);

        getServersInfo();
        getFavoriteServersInfo();
        serverRefreshHandler.post(serverRefreshRunnable);
        animateLauncherEntrance();
    }

    private void requestFirstLaunchPermissions() {
        ArrayList<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissions.toArray(new String[0]), FIRST_LAUNCH_PERMISSIONS);
        }
    }

    private void animateLauncherEntrance() {
        View root = findViewById(R.id.main_layout);
        root.setAlpha(0.0f);
        root.setScaleX(0.97f);
        root.setScaleY(0.97f);

        ObjectAnimator fade = ObjectAnimator.ofFloat(root, View.ALPHA, 0.0f, 1.0f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(root, View.SCALE_X, 0.97f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(root, View.SCALE_Y, 0.97f, 1.0f);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(fade, scaleX, scaleY);
        animatorSet.setDuration(550);
        animatorSet.start();
    }

    /**
     * Converts a manually copied data pack only when the user explicitly turns
     * on "Modified data" in Settings. Nothing is renamed on launcher startup or
     * when Connect is pressed.
     */
    public void prepareModifiedData() {
        GLSurfaceView gpuView = new GLSurfaceView(this);
        gpuView.setEGLContextClientVersion(2);
        gpuView.setAlpha(0.0f);
        gpuView.setRenderer(new GLSurfaceView.Renderer() {
            @Override
            public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
                String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
                String suffix = GpuDataManager.detectSuffix(renderer, extensions);
                int renamed = GpuDataManager.prepare(MainActivity.this, suffix);
                Log.i("InfinityGPU", "GPU=" + renderer + ", format=" + suffix +
                        ", renamed=" + renamed);
                runOnUiThread(() -> {
                    ViewGroup parent = (ViewGroup) gpuView.getParent();
                    if (parent != null) parent.removeView(gpuView);
                    Toast.makeText(MainActivity.this,
                            "Modified data ready (" + suffix.toUpperCase() + ", " +
                                    renamed + " files)", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onSurfaceChanged(GL10 gl, int width, int height) { }

            @Override
            public void onDrawFrame(GL10 gl) { }
        });
        gpuView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        ViewGroup root = findViewById(R.id.main_layout);
        root.addView(gpuView, new ViewGroup.LayoutParams(1, 1));
        gpuView.requestRender();
    }

    public final ArrayList<SAMPServerInfo> getServerList() {
        return mServersList;
    }

    public final ArrayList<SAMPServerInfo> getFavoriteServerList() {
        return mFavoriteServersList;
    }


    public boolean getServersInfo()
    {
        getServerList().clear();

        SAMPServerInfo infinity = new SAMPServerInfo();
        infinity.setId(1);
        infinity.setProjId(2);
        infinity.setServerName("Infinity Role Play");
        infinity.setAddress("148.113.8.119");
        infinity.setPort(26000);
        infinity.setCurrentPlayerCount(0);
        infinity.setMaxPlayerCount(1000);
        infinity.setHasPassword(false);
        infinity.setServerMode("Role Play");
        infinity.setLanguage("Sinhala / English");
        infinity.setServerStatus(SAMPServerInfo.Status.ONLINE);
        infinity.setPing(0);
        infinity.setQueried(true);
        getServerList().add(infinity);

        return true;
    }

    private void queryInfinityServer() {
        if (serverQueryRunning || getServerList().isEmpty()) return;
        serverQueryRunning = true;

        new AsyncTask<Void, Void, String[]>() {
            @Override
            protected String[] doInBackground(Void... ignored) {
                SampQueryAPI query = new SampQueryAPI("148.113.8.119", 26000);
                try {
                    if (!query.mo7166d()) return null;
                    return query.mo7164b();
                } finally {
                    if (query.f7277a != null) query.f7277a.close();
                }
            }

            @Override
            protected void onPostExecute(String[] info) {
                serverQueryRunning = false;
                if (info != null && info.length >= 6 && !getServerList().isEmpty()) {
                    try {
                        SAMPServerInfo server = getServerList().get(0);
                        server.setHasPassword("1".equals(info[0]));
                        server.setCurrentPlayerCount(Integer.parseInt(info[1]));
                        server.setMaxPlayerCount(Integer.parseInt(info[2]));
                        server.setServerName(info[3]);
                        server.setServerMode(info[4]);
                        server.setLanguage(info[5]);
                        server.setServerStatus(SAMPServerInfo.Status.ONLINE);
                    } catch (Exception e) {
                        Log.e("InfinityQuery", "Invalid server response", e);
                    }
                }
                refreshHostedServers();
            }
        }.execute();
    }

    private void refreshHostedServers() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof ServersFragment && fragment.isAdded()) {
                for (Fragment child : fragment.getChildFragmentManager().getFragments()) {
                    if (child instanceof ServerPagesItemFragment
                            && ((ServerPagesItemFragment) child).getPage() == 1
                            && child.getView() != null) {
                        RecyclerView list = child.requireView().findViewById(R.id.server_recycler);
                        if (list != null && list.getAdapter() != null) {
                            list.getAdapter().notifyDataSetChanged();
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        serverRefreshHandler.removeCallbacks(serverRefreshRunnable);
        super.onDestroy();
    }

    public void getFavoriteServersInfo()
    {
        for(int i = 0; i< FavoritesInfo.getServerList(this).size(); i++)
        {
            SAMPServerInfo serverInfo = new SAMPServerInfo(i, i, "Loading...", FavoritesInfo.getServerList(this).get(i).ip, FavoritesInfo.getServerList(this).get(i).port, 0, 0, 0, 0, 0, "English");
            serverInfo.setFavorite(true);
            serverInfo.setQueried(false);
            getFavoriteServerList().add(serverInfo);
            new ProcessInfo().execute(i);
        }

        refreshFavoriteServers();
    }

    public class ProcessInfo extends AsyncTask<Integer, Void, Void> {
        public Void doInBackground(Integer... numArr) {
            int intValue = numArr[0].intValue();
            if(getFavoriteServerList().size() > intValue) {
                if(!getFavoriteServerList().get(intValue).getQueried()) {
                    SampQueryAPI sampQuery = new SampQueryAPI(getFavoriteServerList().get(intValue).getAddress(), getFavoriteServerList().get(intValue).getPort());
                    try {
                        if (!sampQuery.mo7166d()) return null;
                        String[] info = sampQuery.mo7164b();
                        if (info == null || info.length < 6) return null;

                        SAMPServerInfo queriedServer = new SAMPServerInfo(intValue, intValue,
                                info[3], getFavoriteServerList().get(intValue).getAddress(),
                                getFavoriteServerList().get(intValue).getPort(),
                                Integer.parseInt(info[1]), Integer.parseInt(info[2]),
                                Integer.parseInt(info[0]), 1, 1, info[5]);
                        queriedServer.setServerMode(info[4]);
                        queriedServer.setLanguage(info[5]);
                        queriedServer.setFavorite(true);
                        queriedServer.setServerStatus(SAMPServerInfo.Status.ONLINE);
                        getFavoriteServerList().set(intValue, queriedServer);
                    } catch (Exception error) {
                        Log.e("FavoriteQuery", "Could not query favorite server", error);
                        return null;
                    } finally {
                        DatagramSocket datagramSocket = sampQuery.f7277a;
                        if (datagramSocket != null) datagramSocket.close();
                    }
                    getFavoriteServerList().get(intValue).setQueried(true);

                    refreshFavoriteServers();
                }
                else
                    return null;
            }
            return null;
        }

        public void onPostExecute(Void r1) {
            super.onPostExecute(r1);
        }
    }

    public void refreshFavoriteServers()
    {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof ServersFragment) {
                while (!fragment.isAdded()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if(fragment.isAdded()) {
                    for (Fragment fragment2 : fragment.getChildFragmentManager().getFragments()) {
                        if ((fragment2 instanceof ServerPagesItemFragment) && ((ServerPagesItemFragment) fragment2).getPage() == 0 && fragment2 != null) {
                            RecyclerView view = Objects.requireNonNull( fragment2.requireView().findViewById(R.id.server_recycler));
                            if(view != null) {
                                RecyclerView.Adapter adapter = view.getAdapter();
                                if(adapter != null) {
                                    view.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            adapter.notifyDataSetChanged();
                                        }
                                    });
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void hideKeyboard(Activity activity) {
        InputMethodManager inputManager = (InputMethodManager) activity
                .getSystemService(Context.INPUT_METHOD_SERVICE);

        // check if no view has focus:
        View currentFocusedView = activity.getCurrentFocus();
        if (currentFocusedView != null) {
            inputManager.hideSoftInputFromWindow(currentFocusedView.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    public class ViewPagerAdapter extends FragmentPagerAdapter {

        public ViewPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            if(position == 1)
                return new ServersFragment();
            else if(position == 0)
                return new HomeFragment();
            else if(position == 2)
                return new SettingsFragment();
            return new HomeFragment();
        }

        @Override
        public int getCount() {
            return 3;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
