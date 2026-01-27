package com.android.launcher3.searchcenter;

import static com.android.launcher3.proxy.SearchCenterProxy.PATCH_VER;

import android.app.AppOpsManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.launcher3.proxy.SearchCenterProxy;
import com.doogee.searchcenter.ISearchCenterInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SearchCenterService extends Service {
    private static final String TAG = "SearchCenterService";
    private ISearchCenterInterface.Stub mStub = new ISearchCenterInterface.Stub() {
        @Override
        public void onActivityPause() throws RemoteException {

        }

        @Override
        public void onActivityResume() throws RemoteException {

        }

        @Override
        public void onAttachedToWindow() throws RemoteException {

        }

        @Override
        public void onDetachedFromWindow() throws RemoteException {

        }

        @Override
        public void onNavKeyEvent(int keyCode) throws RemoteException {

        }

        @Override
        public int onTouchMotionEvent(int action, float y) throws RemoteException {
            return 0;
        }

        @Override
        public float getDeltaY() throws RemoteException {
            return 0;
        }

        @Override
        public boolean isSwitchOpen() throws RemoteException {
            return false;
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
            String usageStatsJson = SearchCenterProxy.INSTANCE.get(getApplicationContext()).getUsageStatJson();
            if (allowedGetUsageStats()) {
                usageStatsJson = SearchCenterService.this.getFromUsageStatsManager(beginTime, endTime, limit);
            }
            return usageStatsJson;
        }

        @Override
        public void onColorsChanged() throws RemoteException {
            
        }
    };

    private boolean allowedGetUsageStats() {
        boolean result = false;
        try {
            AppOpsManager appOps = (AppOpsManager) getApplicationContext().getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                getApplicationContext().getPackageName()
            );
            result = mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable e) {
            Log.e(TAG, "allowedGetUsageStats exception: " + e.getLocalizedMessage());
            e.printStackTrace();
        }
        Log.i(TAG, "allowedGetUsageStats result: " + result);
        return result;
    }

    private String getFromUsageStatsManager(long beginTime, long endTime, int limit) {
        String usageStatsJson = "";
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            List<UsageStats> usageStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, beginTime, endTime);
            List<UsageStats> tempStats = new ArrayList<>();
            for (UsageStats us : usageStats) {
                if (us.getTotalTimeInForeground() > 0) {
                    tempStats.add(us);
                }
            }
            tempStats.sort((o1, o2) -> {
                if (o1 != null && o2 != null) {
                    return o1.getLastTimeUsed() <= o2.getLastTimeUsed() ? 1 : -1;
                }
                return 0;
            });
            if (!tempStats.isEmpty()) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < tempStats.size(); i++) {
                    if (i >= limit) {
                        break;
                    }
                    UsageStats usageStat = tempStats.get(i);
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("PackageName", usageStat.getPackageName());
                    jsonObject.put("LastTimeUsed", usageStat.getLastTimeUsed());
                    jsonArray.put(jsonObject);
                }
                usageStatsJson = jsonArray.toString();
            }
        } catch (Throwable e) {
            Log.e(TAG, "getFromUsageStatsManager exception: " + e.getLocalizedMessage());
            e.printStackTrace();
        }
        Log.i(TAG, "getFromUsageStatsManager usageStatsJson: " + usageStatsJson);
        return usageStatsJson;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mStub;
    }
}
