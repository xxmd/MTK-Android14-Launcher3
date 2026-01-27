package com.android.launcher3.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.proxy.SearchCenterProxy;

import java.io.File;
import java.io.FileNotFoundException;

public class LauncherWallpaperProvider extends ContentProvider {
    private static final String TAG = "LauncherWallpaperProvider";
    public static Uri WALLPAPER_URI;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    @Override
    public boolean onCreate() {
        String authority = "content://" + getContext().getPackageName() + ".wallpaper";
        MATCHER.addURI(authority, "current", 1);
        MATCHER.addURI(authority, "blur", 2);
        WALLPAPER_URI = Uri.parse(authority);
        Log.d(TAG, "onCreate WALLPAPER_URI: " + WALLPAPER_URI);
        return true;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        Log.d(TAG, "openFile uri: " + uri);
        String queryMD5 = null;
        String packageName = null;
        try {
            queryMD5 = uri.getQueryParameter("md5");
            packageName = uri.getQueryParameter("packageName");
            Log.d(TAG, "openFile packageName: " + packageName + ", queryMD5: " + queryMD5);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            SearchCenterProxy searchCenterProxy = SearchCenterProxy.INSTANCE.get(getContext());
            while (searchCenterProxy.isUpdateWallpapering()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                }
            }
            String wallpaperFileMD5 = searchCenterProxy.getWallpaperFileMD5();
            Log.d(TAG, "openFile packageName: " + packageName + ", queryMD5: " + queryMD5 + ", wallpaperFileMD5: " + wallpaperFileMD5);
            if (!TextUtils.equals(queryMD5, wallpaperFileMD5)) {
                File wallpaperFile = searchCenterProxy.getWallpaperFile();
                Log.d(TAG, "openFile packageName: " + packageName + ", wallpaperFile: " + wallpaperFile);
                if (wallpaperFile != null) {
                    return ParcelFileDescriptor.open(
                        wallpaperFile,
                        ParcelFileDescriptor.MODE_READ_ONLY
                    );
                }
            }
        } catch (Throwable e) {
            Log.d(TAG, "openFile exception: " + e.getLocalizedMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
