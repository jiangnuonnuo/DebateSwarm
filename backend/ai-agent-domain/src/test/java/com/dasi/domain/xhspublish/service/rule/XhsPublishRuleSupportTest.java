package com.dasi.domain.xhspublish.service.rule;

import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.model.entity.XhsPublishContentAssetEntity;
import com.dasi.types.exception.WorkException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XhsPublishRuleSupportTest {

    @Test
    void shouldPreferSelectedAssetIdsOverImagesAndFallbackAssets() {
        JSONObject context = new JSONObject();
        context.put("selectedAssetIds", List.of("asset-2", "asset-1"));
        context.put("images", List.of("http://example.com/image-a.jpg"));

        List<XhsPublishContentAssetEntity> assets = List.of(
                asset("asset-1", "C:/tmp/1.jpg", null),
                asset("asset-2", "C:/tmp/2.jpg", null)
        );

        List<String> images = XhsPublishRuleSupport.resolveImages(context, assets);
        assertEquals(List.of("C:/tmp/2.jpg", "C:/tmp/1.jpg"), images);
    }

    @Test
    void shouldThrowWhenSelectedAssetIdsContainsInvalidAsset() {
        JSONObject context = new JSONObject();
        context.put("selectedAssetIds", List.of("asset-not-exist"));
        List<XhsPublishContentAssetEntity> assets = List.of(asset("asset-1", "C:/tmp/1.jpg", null));

        assertThrows(WorkException.class, () -> XhsPublishRuleSupport.resolveImages(context, assets));
    }

    @Test
    void shouldFallbackToImagesThenTaskAssets() {
        JSONObject withImages = new JSONObject();
        withImages.put("images", List.of("http://img/a.jpg", "http://img/b.jpg"));
        assertEquals(List.of("http://img/a.jpg", "http://img/b.jpg"),
                XhsPublishRuleSupport.resolveImages(withImages, List.of()));

        JSONObject emptyContext = new JSONObject();
        List<XhsPublishContentAssetEntity> assets = List.of(
                asset("asset-1", "C:/tmp/1.jpg", null),
                asset("asset-2", null, "https://origin/2.jpg")
        );
        assertEquals(List.of("C:/tmp/1.jpg", "https://origin/2.jpg"),
                XhsPublishRuleSupport.resolveImages(emptyContext, assets));
    }

    private XhsPublishContentAssetEntity asset(String assetId, String storageRef, String originUrl) {
        return XhsPublishContentAssetEntity.builder()
                .assetId(assetId)
                .assetStatus("ready")
                .storageRef(storageRef)
                .originUrl(originUrl)
                .build();
    }

}

