// ISearchCenterInterface.aidl
package com.doogee.searchcenter;

// Declare any non-default types here with import statements

interface ISearchCenterInterface {
 //void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat,
 //           double aDouble, String aString);
 void onActivityPause();
 void onActivityResume();
 void onAttachedToWindow();
 void onDetachedFromWindow();
 void onNavKeyEvent(int keyCode);
 int onTouchMotionEvent(int action, float y);
 float getDeltaY();
 boolean isSwitchOpen();
 void setPatchVer(int versionCode);
 int getPatchVer();
 String getUsageStats(int intervalType, long beginTime, long endTime, int limit);
 void onColorsChanged();
}
