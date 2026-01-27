// IAppInterface.aidl
package com.bm.aiappfolder;

interface IAppInterface {
 void onInstallStart(String packageName);
 void onInstallFinished(String packageName);
 void onProgressChanged(String packageName, float progress);
 void setPatchVer(int versionCode);
 void onColorsChanged();
}
