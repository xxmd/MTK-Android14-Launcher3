package com.android.launcher3.pm;

import static com.android.launcher3.proxy.SearchCenterProxy.PATCH_VER;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.bm.aiappfolder.IAppInterface;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class AppProxy {
    private static final String TAG = "AppProxy";
    private static final String PACKAGE_NAME = "com.doogee.aiappfolder";
    private static final String ACTION_SERVICE = "com.hotapp.aiappfolder.service";

    public interface IInstance {
        public AppProxy get(Context context);

    }

    public static final IInstance INSTANCE = new IInstance() {
        @Override
        public AppProxy get(Context context) {
            return AppProxy.getInstance(context);
        }
    };

    private final Context mContext;
    private IAppInterface appInterface;
    private final List<Task> taskList = new ArrayList<>();
    private final ServiceConnection searchConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.e(TAG, "bindRemoteSearchService onServiceConnected +++++++++++");
            try {
                appInterface = IAppInterface.Stub.asInterface(service);
                if (appInterface != null) {
                    taskList.add(new Task(Task.TASK_TYPE_PATCH_VERSION));
                    for (Task task : taskList) {
                        switch (task.taskType) {
                            case Task.TASK_TYPE_INSTALL_START:
                                appInterface.onInstallStart(task.packageName);
                                Log.d(TAG, "bindRemoteSearchService onInstallStart:" + task.packageName);
                                break;
                            case Task.TASK_TYPE_INSTALL_FINISHED:
                                appInterface.onInstallFinished(task.packageName);
                                Log.d(TAG, "bindRemoteSearchService onInstallFinished:" + task.packageName);
                                break;
                            case Task.TASK_TYPE_INSTALL_PROGRESS:
                                appInterface.onProgressChanged(task.packageName, task.progress);
                                Log.d(TAG, "bindRemoteSearchService onProgressChanged:" + task.packageName + ", progress:" + task.progress);
                                break;
                            case Task.TASK_TYPE_PATCH_VERSION:
                                appInterface.setPatchVer(PATCH_VER);
                                Log.d(TAG, "bindRemoteSearchService setPatchVer:" + PATCH_VER);
                            case Task.TASK_TYPE_ON_COLORS_CHANGED:
                                appInterface.onColorsChanged();
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
                Log.e(TAG, "remote service disconnected package name: " + name.getPackageName());
            }
            appInterface = null;
            bindSearchServiceWithHandler();
        }
    };
    private final ProxyHandler mServiceHandler = new ProxyHandler(this);

    private static AppProxy mInstance;

    private static AppProxy getInstance(Context context) {
        if (mInstance == null) {
            synchronized (AppProxy.class) {
                mInstance = new AppProxy(context);
            }
        }
        return mInstance;
    }

    private AppProxy(Context context) {
        this.mContext = context;
        bindSearchServiceWithHandler();
        Log.e(TAG, "construct");
    }

    public void onColorsChanged() {
        Log.d(TAG, "onColorsChanged");
        try {
            if (appInterface != null) {
                appInterface.onColorsChanged();
            } else {
                Task task = new Task(Task.TASK_TYPE_ON_COLORS_CHANGED);
                taskList.add(task);
                bindSearchServiceWithHandler();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void onInstallStart(String packageName) {
        Log.d(TAG, "onInstallStart packageName:" + packageName);
        try {
            if (appInterface != null) {
                appInterface.onInstallStart(packageName);
            } else {
                Task task = new Task(Task.TASK_TYPE_INSTALL_START);
                task.packageName = packageName;
                taskList.add(task);
                bindSearchServiceWithHandler();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void onInstallFinished(String packageName) {
        Log.d(TAG, "onInstallFinished packageName:" + packageName);
        try {
            if (appInterface != null) {
                appInterface.onInstallFinished(packageName);
            } else {
                Task task = new Task(Task.TASK_TYPE_INSTALL_FINISHED);
                task.packageName = packageName;
                taskList.add(task);
                bindSearchServiceWithHandler();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void onProgressChanged(String packageName, float progress) {
        Log.d(TAG, "onProgressChanged packageName:" + packageName + ", progress:" + progress);
        try {
            if (appInterface != null) {
                appInterface.onProgressChanged(packageName, progress);
            } else {
                Task task = new Task(Task.TASK_TYPE_INSTALL_PROGRESS);
                task.packageName = packageName;
                task.progress = progress;
                taskList.add(task);
                bindSearchServiceWithHandler();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void setPatchVer() {
        Log.d(TAG, "setPatchVer");
        try {
            if (appInterface != null) {
                appInterface.setPatchVer(PATCH_VER);
            } else {
                Task task = new Task(Task.TASK_TYPE_PATCH_VERSION);
                taskList.add(task);
                bindSearchServiceWithHandler();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void bindSearchServiceWithHandler() {
        Log.d(TAG, "bindSearchServiceWithHandler");
        this.mServiceHandler.sendEmptyMessage(0);
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

    private static class ProxyHandler extends Handler {
        private final WeakReference<AppProxy> mProxyRef;

        ProxyHandler(AppProxy service) {
            this.mProxyRef = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            AppProxy proxy = this.mProxyRef.get();
            if (proxy != null) {
                Log.d(TAG, "ProxyHandler handleMessage bindSearchService");
                proxy.bindSearchService();
            }
        }
    }

    private static class Task {
        private static final int TASK_TYPE_INSTALL_START = 1;
        private static final int TASK_TYPE_INSTALL_PROGRESS = 2;
        private static final int TASK_TYPE_INSTALL_FINISHED = 3;
        private static final int TASK_TYPE_PATCH_VERSION = 4;
        private static final int TASK_TYPE_ON_COLORS_CHANGED = 5;

        private int taskType = TASK_TYPE_INSTALL_START;
        private String packageName;
        private float progress;

        public Task(int type) {
            taskType = type;
        }
    }
}
