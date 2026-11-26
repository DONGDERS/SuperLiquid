package io.github.liuran001.mmliquidglass;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class TabCatalogRulesTest {
    private static TabCatalogRules.LabelCandidate label(String text, double baseline) {
        return new TabCatalogRules.LabelCandidate(text, baseline, 0.7, 1, 11, false, false, false);
    }
    @Test public void sharedBaselineAndAccountBeatPromotionalText() {
        assertEquals(List.of("首页", "直播", "我的"), TabCatalogRules.chooseLabels("kugou", List.of(
                List.of(label("首页", 34)), List.of(label("限时活动", 22), label("直播", 33)),
                List.of(label("5.5折", 34), label("我的", 35)))));
        assertEquals(1, TabCatalogRules.accountIndex("qq", List.of("消息", "联系人", "动态")));
        assertEquals(2, TabCatalogRules.accountIndex("qq", List.of("消息", "频道", "其他")));
    }
    @Test public void publishSemanticsRecognizeAccessibilitySuffix() {
        assertTrue(TabCatalogRules.isBiliPublishActionLabel("发布内容，5之3，标签"));
        assertFalse(TabCatalogRules.isBiliPublishActionLabel("新发布动态"));
    }
    @Test public void discountChangesDoNotChangeCellIdentity() {
        assertEquals(TabCatalogRules.cellKey(4, "mine", List.of("我的", "5.5折")),
                TabCatalogRules.cellKey(4, "mine", List.of("我的", "8折")));
        assertNotEquals(TabCatalogRules.cellKey(4, "mine", List.of("我的")),
                TabCatalogRules.cellKey(3, "mine", List.of("我的")));
    }
    @Test public void removalDropsOldTabAndAppendsNewVisibleTab() {
        assertEquals(List.of("首页", "消息", "我的", "新功能"),
                TabCatalogRules.candidateOrder(List.of("首页", "新功能", "消息", "我的"),
                        List.of("首页", "已下线", "消息", "我的")));
    }
    @Test public void renamedTabDoesNotInheritOldTitleConfiguration() {
        assertEquals(List.of("首页", "我的", "新名称"),
                TabCatalogRules.candidateOrder(List.of("首页", "新名称", "我的"),
                        List.of("首页", "旧名称", "我的")));
    }
    @Test public void firstNativeItemAlwaysPinnedAndSavedDuplicatesRemoved() {
        assertEquals(List.of("新首页", "我的", "消息"),
                TabCatalogRules.candidateOrder(List.of("新首页", "消息", "我的"),
                        List.of("我的", "我的", "消息")));
    }
    @Test public void noSavedConfigFollowsNativeOrder() {
        List<String> nativeTabs = List.of("首页", "频道", "联系人", "动态");
        assertEquals(nativeTabs, TabCatalogRules.candidateOrder(nativeTabs, List.of()));
        assertEquals(List.of(), TabCatalogRules.candidateOrder(List.of(), List.of("旧项")));
    }
    @Test public void nativeCampaignTitlesAndKugouAliasesKeepTheirIdentity() {
        assertEquals("周一大领券", TabCatalogRules.normalize("pinduoduo", "周一大领券"));
        assertEquals("赚钱", TabCatalogRules.normalize("kugou", "金币"));
        assertEquals("我", TabCatalogRules.normalize("kugou", "我的"));
        assertEquals("我的", TabCatalogRules.normalize("alipay", "我的"));
    }
    @Test public void onlySupportedHostsCanReport() {
        assertEquals("qq", TabCatalogRules.keyFor("com.tencent.mobileqq"));
        assertEquals("com.eg.android.AlipayGphone", TabCatalogRules.packageFor("alipay"));
        assertNull(TabCatalogRules.packageFor("play"));
        assertNull(TabCatalogRules.keyFor("com.android.vending"));
    }
    @Test public void unsafeLabelsAndOversizedLabelsAreRejected() {
        assertFalse(TabCatalogRules.validText(""));
        assertFalse(TabCatalogRules.validText("  "));
        assertFalse(TabCatalogRules.validText("首页\n已选中"));
        assertFalse(TabCatalogRules.validText("a".repeat(101)));
        assertTrue(TabCatalogRules.validText("游戏库"));
        assertTrue(TabCatalogRules.locked("qq", "联系人"));
        assertFalse(TabCatalogRules.locked("qq", "新项目"));
    }
    @Test public void savedLayoutRequiresExactVersionInstallAndNativeOrder() {
        List<String> keys = List.of("首页", "消息", "我的");
        String token = TabCatalogRules.catalogToken(10, 1000, keys);
        assertEquals(64, token.length());
        assertTrue(TabCatalogRules.matchesCatalog(token, 10, 1000, keys));
        assertFalse(TabCatalogRules.matchesCatalog(token, 11, 1000, keys));
        assertFalse(TabCatalogRules.matchesCatalog(token, 10, 2000, keys));
        assertFalse(TabCatalogRules.matchesCatalog(token, 10, 1000, List.of("首页", "我的", "消息")));
        assertFalse(TabCatalogRules.matchesCatalog(token, 10, 1000, List.of("首页", "新项目", "我的")));
        assertFalse(TabCatalogRules.matchesCatalog("", 10, 1000, keys));
    }
    @Test public void invalidOrPartialCatalogCannotAuthorizeLayout() {
        assertEquals("", TabCatalogRules.catalogToken(-1, 1000, List.of("首页", "我的")));
        assertEquals("", TabCatalogRules.catalogToken(1, 0, List.of("首页", "我的")));
        assertEquals("", TabCatalogRules.catalogToken(1, 1000, List.of("首页")));
        assertEquals("", TabCatalogRules.catalogToken(1, 1000, List.of("首页", "首页")));
        assertEquals("", TabCatalogRules.catalogToken(1, 1000, List.of("首页", "")));
    }

}