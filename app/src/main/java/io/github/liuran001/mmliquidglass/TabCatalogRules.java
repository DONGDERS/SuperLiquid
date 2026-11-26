package io.github.liuran001.mmliquidglass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Shared identity and layout rules. No Android dependencies. */
public final class TabCatalogRules {
    public static final int MAX_TABS = 12;
    private static final Map<String, String> PACKAGES = new LinkedHashMap<>();
    static {
        PACKAGES.put("mm", "com.tencent.mm");
        PACKAGES.put("qq", "com.tencent.mobileqq");
        PACKAGES.put("bili", "tv.danmaku.bili");
        PACKAGES.put("heybox", "com.max.xiaoheihe");
        PACKAGES.put("kugou", "com.kugou.android");
        PACKAGES.put("pinduoduo", "com.xunmeng.pinduoduo");
        PACKAGES.put("taobao", "com.taobao.taobao");
        PACKAGES.put("alipay", "com.eg.android.AlipayGphone");
    }
    private TabCatalogRules() {}
    public static String packageFor(String key) { return PACKAGES.get(key); }
    public static String keyFor(String pkg) {
        for (Map.Entry<String, String> entry : PACKAGES.entrySet()) {
            if (entry.getValue().equals(pkg)) return entry.getKey();
        }
        return null;
    }
    public static boolean validText(String text) {
        return text != null && !text.isBlank() && text.length() <= 100
                && text.codePoints().noneMatch(Character::isISOControl);
    }
    public static String normalize(String appKey, String title) {
        if (title == null) return "";
        title = title.trim();
        if ("kugou".equals(appKey)) {
            if ("金币".equals(title)) return "赚钱";
            if ("我的".equals(title)) return "我";
        }
        return title;
    }
    /** Bind a layout to the exact installed app and the complete native key order. */
    public static String catalogToken(long versionCode, long updatedAt, List<String> keys) {
        if (versionCode < 0 || updatedAt <= 0 || keys.size() < 2 || keys.size() > MAX_TABS
                || new LinkedHashSet<>(keys).size() != keys.size()) return "";
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            StringBuilder input = new StringBuilder(versionCode + ":" + updatedAt + ":");
            for (String key : keys) {
                if (!validText(key)) return "";
                input.append(key.length()).append(':').append(key);
            }
            byte[] hash = digest.digest(input.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder token = new StringBuilder();
            for (byte b : hash) token.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            return token.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
    public static boolean matchesCatalog(String saved, long versionCode, long updatedAt, List<String> keys) {
        return saved != null && !saved.isEmpty() && saved.equals(catalogToken(versionCode, updatedAt, keys));
    }
    public static boolean locked(String appKey, String title) {
        return switch (appKey) {
            case "mm" -> "微信".equals(title) || "我".equals(title);
            case "qq" -> "消息".equals(title) || "联系人".equals(title);
            case "bili", "alipay" -> "首页".equals(title) || "我的".equals(title);
            case "heybox", "kugou" -> "首页".equals(title) || "主页".equals(title)
                    || "我".equals(title) || "我的".equals(title);
            case "pinduoduo" -> "首页".equals(title) || "个人中心".equals(title);
            case "taobao" -> "首页".equals(title) || "我的淘宝".equals(title);
            default -> false;
        };
    }
    /** Only current native items survive; new items default visible in native order. */
    public static List<String> candidateOrder(List<String> nativeKeys, List<String> saved) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (nativeKeys.isEmpty()) return new ArrayList<>();
        result.add(nativeKeys.get(0));
        for (String key : saved) if (nativeKeys.contains(key)) result.add(key);
        result.addAll(nativeKeys);
        return new ArrayList<>(result);
    }
}
