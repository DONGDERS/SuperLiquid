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
    public static final int SCHEMA = 2;
    public static final int MAX_CANDIDATES = 32;

    /** Measurements are taken in the native row, before the module moves its cells. */
    static final class LabelCandidate {
        private final String text; private final double baseline, vertical, centering, size;
        private final boolean direct, description, badge;
        LabelCandidate(String text, double baseline, double vertical, double centering,
                double size, boolean direct, boolean description, boolean badge) {
            this.text=text; this.baseline=baseline; this.vertical=vertical; this.centering=centering;
            this.size=size; this.direct=direct; this.description=description; this.badge=badge;
        }
        String text(){return text;} double baseline(){return baseline;} double vertical(){return vertical;}
        double centering(){return centering;} double size(){return size;} boolean direct(){return direct;}
        boolean description(){return description;} boolean badge(){return badge;}
    }

    public static String accessibilityTitle(String text) {
        if (text == null) return "";
        String[] parts = text.trim().split("[，,\\n]");
        if (parts.length > 1) {
            boolean annotation = false;
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.matches(".*(?:标签|选中|按钮|之[0-9０-９]+|[0-9０-９]+项|[0-9０-９]+个未读).*")) annotation = true;
            }
            if (annotation) return parts[0].trim();
        }
        return text.trim();
    }
    static boolean decoration(String text) {
        return text == null || text.isBlank() || text.matches("[0-9０-９+＋.· %％]+")
                || text.matches("[0-9０-９.]+折.*")
                || text.matches("[0-9０-９]+(?:条|个|条未读|个未读|元|天|秒|%|％).*未读.*");
    }
    static boolean homeTitle(String appKey, String text) {
        return switch (appKey) {
            case "mm" -> "微信".equals(text);
            case "qq" -> "消息".equals(text);
            default -> "首页".equals(text) || "主页".equals(text);
        };
    }
    static boolean accountTitle(String appKey, String text) {
        return switch (appKey) {
            case "qq" -> "联系人".equals(text);
            case "pinduoduo" -> "个人中心".equals(text);
            case "taobao" -> "我的淘宝".equals(text);
            default -> "我的".equals(text) || "我".equals(text);
        };
    }
    /** Whole-row agreement wins over the first text found inside a cell. */
    static List<String> chooseLabels(String appKey, List<List<LabelCandidate>> cells) {
        List<String> labels = new ArrayList<>();
        for (int index = 0; index < cells.size(); index++) {
            LabelCandidate best = null;
            double bestScore = -Double.MAX_VALUE;
            for (LabelCandidate candidate : cells.get(index)) {
                if (!validText(candidate.text()) || decoration(candidate.text()) || candidate.badge()) continue;
                double score = candidate.vertical() * 2 + candidate.centering();
                if (candidate.description()) score -= 2;
                if (candidate.direct()) score += 8;
                if ((index == 0 && homeTitle(appKey, candidate.text()))
                        || accountTitle(appKey, candidate.text())) score += 100;
                if (!candidate.description() && candidate.vertical() >= 0.4 && candidate.centering() >= 0.35) {
                    for (int other = 0; other < cells.size(); other++) {
                        if (other == index) continue;
                        for (LabelCandidate peer : cells.get(other)) {
                            if (peer.description() || peer.badge() || decoration(peer.text())
                                    || peer.vertical() < 0.4 || peer.centering() < 0.35) continue;
                            if (Math.abs(candidate.baseline() - peer.baseline()) <= 3
                                    && Math.abs(candidate.size() - peer.size()) <= Math.max(2, candidate.size() * 0.25)) {
                                score += 8;
                                if ((other == 0 && homeTitle(appKey, peer.text()))
                                        || accountTitle(appKey, peer.text())) score += 4;
                                break; // One vote per native cell, never one vote per duplicate TextView.
                            }
                        }
                    }
                }
                if (score > bestScore) { bestScore = score; best = candidate; }
            }
            labels.add(best == null ? "项目 " + (index + 1) : best.text());
        }
        return labels;
    }
    /** Identity does not depend on the chosen name, geometry or unread/promotion text. */
    static String cellKey(int nativeIndex, String identity, List<String> observed) {
        java.util.TreeSet<String> evidence = new java.util.TreeSet<>();
        for (String text : observed) {
            String title = accessibilityTitle(text);
            if (validText(title) && !decoration(title)) evidence.add(title);
        }
        StringBuilder input = new StringBuilder(identity);
        for (String value : evidence) input.append(':').append(value.length()).append(':').append(value);
        return "cell2:" + nativeIndex + ":" + digest(input.toString()).substring(0, 24);
    }
    private static String digest(String input) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
    static boolean isBiliPublishActionLabel(String label) {
        String value = accessibilityTitle(label);
        return "发布".equals(value) || "投稿".equals(value) || "+".equals(value)
                || "＋".equals(value) || "发布内容".equals(value) || "发布视频".equals(value)
                || "发布作品".equals(value);
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
    static int accountIndex(String appKey, List<String> labels) {
        for (int i = 1; i < labels.size(); i++) {
            if (accountTitle(appKey, labels.get(i))) return i;
        }
        return labels.size() - 1;
    }
}
