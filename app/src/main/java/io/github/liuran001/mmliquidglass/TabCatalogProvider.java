package io.github.liuran001.mmliquidglass;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;

/** Host-to-manager channel. A host may only report its own supported bottom bar. */
public final class TabCatalogProvider extends ContentProvider {
    public static final String REPORT = "report_tabs";
    public static final Uri URI = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".tab_catalog");
    public static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("runtime_tab_catalog", Context.MODE_PRIVATE);
    }
    @Override public boolean onCreate() { return true; }
    @Override public synchronized Bundle call(String method, String appKey, Bundle extras) {
        if (!REPORT.equals(method) || extras == null) return null;
        String pkg = TabCatalogRules.packageFor(appKey);
        if (pkg == null || !pkg.equals(getCallingPackage())) return null;
        Context ctx = getContext();
        try {
            if (ctx.getPackageManager().getPackageUid(pkg, 0) != Binder.getCallingUid()) return null;
            String[] keys = extras.getStringArray("keys");
            String[] labels = extras.getStringArray("labels");
            if (keys == null || labels == null || keys.length < 2
                    || keys.length > TabCatalogRules.MAX_TABS || keys.length != labels.length) return null;
            JSONArray items = new JSONArray();
            HashSet<String> seen = new HashSet<>();
            for (int i = 0; i < keys.length; i++) {
                if (!TabCatalogRules.validText(keys[i]) || !TabCatalogRules.validText(labels[i])
                        || !seen.add(keys[i])) return null;
                items.put(new JSONObject().put("key", keys[i]).put("label", labels[i])
                        .put("locked", i == 0 || TabCatalogRules.locked(appKey, keys[i])
                                || (("taobao".equals(appKey) || "alipay".equals(appKey)) && i == keys.length - 1)));
            }
            // Take the version from PackageManager, never from a host-supplied value.
            PackageInfo info = ctx.getPackageManager().getPackageInfo(pkg, 0);
            if (extras.getLong("version_code", -1) != info.getLongVersionCode()
                    || extras.getLong("updated_at", -1) != info.lastUpdateTime) return null;
            JSONObject report = new JSONObject().put("schema", 1).put("package", pkg)
                    .put("version_code", info.getLongVersionCode()).put("version_name", info.versionName)
                    .put("updated_at", info.lastUpdateTime).put("items", items);
            SharedPreferences prefs = preferences(ctx);
            String fingerprint = report.toString();
            if (!fingerprint.equals(prefs.getString(appKey + "_fingerprint", null))
                    || prefs.getString(appKey, null) == null) {
                report.put("seen_at", System.currentTimeMillis());
                prefs.edit().putString(appKey, report.toString())
                        .putString(appKey + "_fingerprint", fingerprint).apply();
            }
            Bundle result = new Bundle();
            result.putBoolean("accepted", true);
            return result;
        } catch (Exception failure) {
            android.util.Log.w("LiquidGlass", "tab catalog report rejected", failure);
            return null;
        }
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
