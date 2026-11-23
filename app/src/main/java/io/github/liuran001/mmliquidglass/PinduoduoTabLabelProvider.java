package io.github.liuran001.mmliquidglass;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Stores the last visible campaign label for the module's tab editor. */
public final class PinduoduoTabLabelProvider extends ContentProvider {
    public static final String CAMPAIGN_KEY = "pdd_campaign";
    public static final String LEGACY_CAMPAIGN_KEY = "周一大领券";
    public static final String LABEL_KEY = "campaign_label";
    private static final String PINDUODUO = "com.xunmeng.pinduoduo";
    private static final String UPDATE = "update_campaign_label";
    private static final Uri URI = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".pdd_tab_label");

    public static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("pinduoduo_tab_labels", Context.MODE_PRIVATE);
    }

    static void publish(Context context, String label) {
        if (label.isEmpty() || label.length() > 40) return;
        context.getContentResolver().call(URI, UPDATE, label, null);
    }

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String label, Bundle extras) {
        if (!UPDATE.equals(method) || !PINDUODUO.equals(getCallingPackage())) return null;
        if (label == null || label.isEmpty() || label.length() > 40
                || label.codePoints().anyMatch(Character::isISOControl)) return null;
        SharedPreferences prefs = preferences(getContext());
        if (!label.equals(prefs.getString(LABEL_KEY, null))) {
            prefs.edit().putString(LABEL_KEY, label).apply();
        }
        return Bundle.EMPTY;
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
