package com.android.launcher3.proxy;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.pm.AppProxy;
import com.android.launcher3.util.SafeCloseable;
import com.doogee.searchcenter.ISearchCenterInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SearchCenterProxy implements ISearchCenterInterface, SafeCloseable {
    public static final int PATCH_VER = 13;
    private static final int DELAY_MILLIS = 3000;
    private final static String SR_NAME = "search_center.prefs";
    private final static String USAGE_STAT_INFOS = "usage_stat_infos";
    private static final String PACKAGE_NAME = "com.doogee.searchcenter";
    private static final String ACTION_ACTIVITY = PACKAGE_NAME + ".intent.action.negative";
    private static final String ACTION_SERVICE = "com.doogee.search.service";
    private static final String CLASS_NAME = "com.horse.mobi.negative.NegativeActivity";

    private static final int ACTION_LAUNCH_MAIN_ACTIVITY = 10;
    private static final int ACTION_UPDATE_SLIDE_POSITION = 11;
    private static final int ACTION_DO_NOTHING = 12;
    private static final int ACTION_REMOVE_WINDOW = 13;
    private static final int ACTION_ADD_WINDOW = 14;

    public interface IInstance {
        public SearchCenterProxy get(Context context);
    }

    public static final IInstance INSTANCE = new IInstance() {
        @Override
        public SearchCenterProxy get(Context context) {
            return SearchCenterProxy.getInstance(context);
        }
    };


    private static final String TAG = "SearchCenterProxy";
    private final Context mContext;
    private ISearchCenterInterface searchCenterInterface;
    private boolean needStart = false;
    private final List<Task> taskList = new ArrayList<>();

    private static class Task {
        private static final int TASK_TYPE_IS_SWITCH_OPEN = 1;
        private static final int TASK_TYPE_ON_COLORS_CHANGED = 2;
        private static final int TASK_TYPE_ON_TOUCH_MOTION = 3;
        private static final int TASK_TYPE_PATCH_VERSION = 4;
        private int taskType = TASK_TYPE_IS_SWITCH_OPEN;
        private int mAction;
        private float mY;

        public Task(int type) {
            taskType = type;
        }
    }

    private final ServiceConnection searchConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.e(TAG, "bindRemoteSearchService onServiceConnected +++++++++++");
            try {
                searchCenterInterface = ISearchCenterInterface.Stub.asInterface(service);
                if (searchCenterInterface != null) {
                    taskList.add(new Task(Task.TASK_TYPE_PATCH_VERSION));
                    for (Task task : taskList) {
                        switch (task.taskType) {
                            case Task.TASK_TYPE_IS_SWITCH_OPEN:
                                isSwitchOpen = searchCenterInterface.isSwitchOpen();
                                Log.d(TAG, "bindRemoteSearchService isSwitchOpen:" + isSwitchOpen);
                                break;
                            case Task.TASK_TYPE_ON_COLORS_CHANGED:
                                searchCenterInterface.onColorsChanged();
                                Log.d(TAG, "bindRemoteSearchService onColorsChanged");
                                break;
                            case Task.TASK_TYPE_ON_TOUCH_MOTION:
                                searchCenterInterface.onTouchMotionEvent(task.mAction, task.mY);
                                Log.d(TAG, "bindRemoteSearchService onTouchMotionEvent:" + task.mAction + ", mY:" + task.mY);
                                break;
                            case Task.TASK_TYPE_PATCH_VERSION:
                                searchCenterInterface.setPatchVer(PATCH_VER);
                                Log.d(TAG, "bindRemoteSearchService setPatchVer:" + PATCH_VER);
                                break;
                        }
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (name != null) {
                Log.e(SearchCenterProxy.TAG, "remote service disconnected package name: " + name.getPackageName());
            }
            searchCenterInterface = null;
            bindSearchService(false);
        }
    };

    private static SearchCenterProxy mInstance;

    private static SearchCenterProxy getInstance(Context context) {
        if (mInstance == null) {
            synchronized (SearchCenterProxy.class) {
                mInstance = new SearchCenterProxy(context);
            }
        }
        return mInstance;
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private SearchCenterProxy(Context context) {
        this.mContext = context;
        loadWallpaperFile();
        loadUsageStats();
        bindSearchService(true);
        addColorsChangedListener();
        Log.e(TAG, "SearchCenterProxy construct");
    }

    private void addColorsChangedListener() {
        Log.e(SearchCenterProxy.TAG, "addColorsChangedListener");
        WallpaperManager wm = WallpaperManager.getInstance(this.mContext);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            wm.addOnColorsChangedListener((colors, which) -> {
                Log.i(TAG, "addColorsChangedListener#onColorsChanged");
                updateWallpaper();
                onColorsChanged();
                AppProxy.INSTANCE.get(this.mContext).onColorsChanged();
            }, mHandler);
        }
    }

    private Map<String, Long> mUsageStatMap = new HashMap<>();
    private String mUsageStatJson;

    public String getUsageStatJson() {
        Log.i(TAG, "getUsageStatJson mUsageStatJson: " + mUsageStatJson);
        return mUsageStatJson;
    }

    private void loadUsageStats() {
        try {
            SharedPreferences sf = this.mContext.getSharedPreferences(SR_NAME, Context.MODE_PRIVATE);
            mUsageStatJson = sf.getString(USAGE_STAT_INFOS, "");
            Log.i(TAG, "loadUsageStats mUsageStatJson: " + mUsageStatJson);
            if (!TextUtils.isEmpty(mUsageStatJson)) {
                JSONArray jArr = new JSONArray(mUsageStatJson);
                for (int i = 0; i < jArr.length(); i++) {
                    JSONObject optObj = jArr.optJSONObject(i);
                    String packageName = optObj.optString("PackageName");
                    long lastTimeUsed = optObj.optLong("LastTimeUsed");
                    if (!TextUtils.isEmpty(packageName)) {
                        mUsageStatMap.put(packageName, lastTimeUsed);
                    }
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "loadUsageStats exception: " + e.getLocalizedMessage());
        }
    }

    public synchronized void saveUsageStats(Intent intent) {
        String packageName = intent.getPackage();
        if (TextUtils.isEmpty(packageName)) {
            ComponentName component = intent.getComponent();
            if (component != null) {
                packageName = component.getPackageName();
            }
        }
        Log.i(TAG, "saveUsageStats packageName: " + packageName);
        if (TextUtils.isEmpty(packageName)) {
            Log.i(TAG, "saveUsageStats packageName is null, ignore");
            return;
        }
        long beforeSevenDays = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
        mUsageStatMap.put(packageName, System.currentTimeMillis());
        try {
            mUsageStatMap.entrySet().removeIf(entry -> entry.getValue() < beforeSevenDays);
            Map<String, Long> sortUsageStatMap = mUsageStatMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey,
                    Map.Entry::getValue,
                    (e1, e2) -> e1,
                    LinkedHashMap::new));
            JSONArray jsonArray = new JSONArray();
            Iterator<Map.Entry<String, Long>> iter = sortUsageStatMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, Long> entry = iter.next();
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("PackageName", entry.getKey());
                jsonObject.put("LastTimeUsed", entry.getValue());
                jsonArray.put(jsonObject);
            }
            mUsageStatJson = jsonArray.toString();
            Log.i(TAG, "saveUsageStats mUsageStatJson: " + mUsageStatJson);
            SharedPreferences sf = this.mContext.getSharedPreferences(SR_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sf.edit();
            editor.putString(USAGE_STAT_INFOS, mUsageStatJson);
            editor.apply();
        } catch (Throwable e) {
            Log.e(TAG, "saveUsageStats exception: " + e.getLocalizedMessage());
        }
    }

    boolean isSwitchOpen = false;

    @Override
    public boolean isSwitchOpen() {
        try {
            if (searchCenterInterface != null) {
                isSwitchOpen = searchCenterInterface.isSwitchOpen();
            } else {
                Task task = new Task(Task.TASK_TYPE_IS_SWITCH_OPEN);
                taskList.add(task);
                bindSearchService(true);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return isSwitchOpen;
    }

    @Override
    public void setPatchVer(int versionCode) throws RemoteException {

    }

    @Override
    public int getPatchVer() throws RemoteException {
        return PATCH_VER;
    }

    @Override
    public String getUsageStats(int intervalType, long beginTime, long endTime, int limit) throws RemoteException {
        return "";
    }

    @Override
    public void onColorsChanged() {
        Log.i(TAG, "onColorsChanged");
        try {
            if (searchCenterInterface != null) {
                searchCenterInterface.onColorsChanged();
            } else {
                Task task = new Task(Task.TASK_TYPE_ON_COLORS_CHANGED);
                taskList.add(task);
                bindSearchService(true);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAttachedToWindow() throws RemoteException {
    }

    @Override
    public void onDetachedFromWindow() throws RemoteException {
    }

    @Override
    public void onActivityResume() throws RemoteException {
    }

    @Override
    public void onActivityPause() throws RemoteException {
    }

    @Override
    public void onNavKeyEvent(int keyCode) throws RemoteException {
    }

    @Override
    public int onTouchMotionEvent(int action, float y) {
        int result = 0;
        try {
            if (searchCenterInterface != null) {
                searchCenterInterface.setPatchVer(PATCH_VER);
                result = searchCenterInterface.onTouchMotionEvent(action, y);
                if (result == ACTION_LAUNCH_MAIN_ACTIVITY) {
                    try {
                        Intent intent = new Intent();
//                    intent.setClassName(PACKAGE_NAME, CLASS_NAME);
//                        intent.setPackage(PACKAGE_NAME);
                        intent.setAction(ACTION_ACTIVITY);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        mContext.startActivity(intent);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            } else {
                Task task = new Task(Task.TASK_TYPE_PATCH_VERSION);
                taskList.add(task);
                task = new Task(Task.TASK_TYPE_ON_TOUCH_MOTION);
                task.mAction = action;
                task.mY = y;
                taskList.add(task);
                bindSearchService(true);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public float getDeltaY() throws RemoteException {
        return 0;
    }

    @Override
    public IBinder asBinder() {
        return null;
    }

    @Override
    public void close() {
    }

    public void bindSearchService(boolean startSearch) {
        this.needStart = startSearch;
        Log.e(SearchCenterProxy.TAG, "bindSearchService needStart: " + needStart);
        bindSearchService();
    }

    private void bindSearchService() {
        Intent intent = new Intent();
        intent.setAction(ACTION_SERVICE);
        intent.setPackage(PACKAGE_NAME);
        try {
            this.mContext.bindService(intent, this.searchConnection, Context.BIND_AUTO_CREATE);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private File mWallpaperFile;
    private String mWallpaperFileMD5;
    private boolean isUpdateWallpapering;

    public File getWallpaperFile() {
        return mWallpaperFile;
    }

    public String getWallpaperFileMD5() {
        return mWallpaperFileMD5;
    }

    private void loadWallpaperFile() {
        updateWallpaper();
    }

    public boolean isUpdateWallpapering() {
        return isUpdateWallpapering;
    }

    public synchronized void updateWallpaper() {
        isUpdateWallpapering = true;
        try {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(this.mContext);
            Drawable wallpaperDrawable = wallpaperManager.getDrawable();
            Log.d(TAG, "openFile wallpaperDrawable: " + wallpaperDrawable);
            if (wallpaperDrawable != null) {
                Bitmap bitmap = drawableToBitmap(wallpaperDrawable);
                mWallpaperFile = saveWallpaperCache(this.mContext, bitmap);
            }
        } catch (Throwable e) {
            Log.d(TAG, "openFile exception: " + e.getLocalizedMessage());
            e.printStackTrace();
        }
        isUpdateWallpapering = false;
    }

    private static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            Bitmap bmp = ((BitmapDrawable) drawable).getBitmap();
            if (bmp != null) return bmp;
        }

        int width = drawable.getIntrinsicWidth() > 0
            ? drawable.getIntrinsicWidth() : 1;
        int height = drawable.getIntrinsicHeight() > 0
            ? drawable.getIntrinsicHeight() : 1;

        Bitmap bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    private File saveWallpaperCache(Context context, Bitmap bitmap) {
        File file = null;

        try {
            File dir = new File(context.getCacheDir(), "wallpaper");
            if (!dir.exists()) {
                boolean mkdirSuccess = dir.mkdirs();
                Log.d(TAG, "saveWallpaperCache dir is not exists, mkdir result: " + mkdirSuccess + ", dir: " + dir);
            } else {
                Log.d(TAG, "saveWallpaperCache dir is exists, dir: " + dir);
            }

            file = new File(dir, "wallpaper.png");
            if (file.exists()) {
                boolean deleteSuccess = file.delete();
                Log.d(TAG, "saveWallpaperCache file is exists, delete result: " + deleteSuccess + ", file: " + file);
            } else {
                Log.d(TAG, "saveWallpaperCache file is not exists, file: " + file);
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
            }
            mWallpaperFileMD5 = getFileMD5(file);
            Log.d(TAG, "saveWallpaperCache success, file: " + mWallpaperFileMD5);
        } catch (Throwable e) {
            Log.d(TAG, "saveWallpaperCache exception: " + e.getLocalizedMessage());
            e.printStackTrace();
        }

        return file;
    }

    public static String getFileMD5(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192]; // 大缓冲区提升性能
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            // 转换为 32 位十六进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
