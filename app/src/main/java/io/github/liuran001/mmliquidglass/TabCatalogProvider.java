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
            if (extras.getInt("schema") != TabCatalogRules.SCHEMA) return null;
            String payload = extras.getString("items");
            if (payload == null || payload.length() > 80000) return null;
            JSONArray incoming = new JSONArray(payload);
            if (incoming.length() < 2 || incoming.length() > TabCatalogRules.MAX_TABS) return null;
            JSONArray items = new JSONArray();
            HashSet<String> seen = new HashSet<>();
            java.util.ArrayList<String> labels = new java.util.ArrayList<>();
            for (int i = 0; i < incoming.length(); i++) labels.add(incoming.getJSONObject(i).getString("label"));
            int account = TabCatalogRules.accountIndex(appKey, labels);
            int previousIndex = -1;
            for (int i = 0; i < incoming.length(); i++) {
                JSONObject entry = incoming.getJSONObject(i);
                String key = entry.getString("key"), label = entry.getString("label");
                int nativeIndex = entry.getInt("native_index");
                JSONArray choices = entry.getJSONArray("candidates");
                if (!TabCatalogRules.validText(key) || !key.startsWith("cell" + TabCatalogRules.SCHEMA + ":" + nativeIndex + ":")
                        || !TabCatalogRules.validText(label) || !seen.add(key)
                        || nativeIndex <= previousIndex || nativeIndex >= TabCatalogRules.MAX_TABS
                        || choices.length() == 0 || choices.length() > TabCatalogRules.MAX_CANDIDATES) return null;
                previousIndex = nativeIndex;
                JSONArray valid = new JSONArray();
                HashSet<String> unique = new HashSet<>();
                boolean includesLabel = false;
                boolean locked = i == 0 || i == account;
                for (int j = 0; j < choices.length(); j++) {
                    String choice = choices.getString(j);
                    if (!TabCatalogRules.validText(choice) || !unique.add(choice)) return null;
                    includesLabel |= label.equals(choice);
                    valid.put(choice);
                }
                if (!includesLabel) return null;
                items.put(new JSONObject().put("key", key).put("label", label)
                        .put("native_index", nativeIndex).put("candidates", valid).put("locked", locked));
            }
            // Take the version from PackageManager, never from a host-supplied value.
            PackageInfo info = ctx.getPackageManager().getPackageInfo(pkg, 0);
            if (extras.getLong("version_code", -1) != info.getLongVersionCode()
                    || extras.getLong("updated_at", -1) != info.lastUpdateTime) return null;
            JSONObject report = new JSONObject().put("schema", TabCatalogRules.SCHEMA).put("package", pkg)
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
