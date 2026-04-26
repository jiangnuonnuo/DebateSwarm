package com.dasi.domain.xhspublish.service.rule;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.model.entity.XhsPublishContentAssetEntity;
import com.dasi.types.exception.WorkException;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public final class XhsPublishRuleSupport {

    private XhsPublishRuleSupport() {
    }

    public static String readString(JSONObject source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            String value = source.getString(key);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    public static boolean readBoolean(JSONObject source, String key, boolean defaultValue) {
        if (source == null || !source.containsKey(key)) {
            return defaultValue;
        }
        Object value = source.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    public static List<String> readStringList(JSONObject source, String key) {
        if (source == null || !source.containsKey(key)) {
            return List.of();
        }
        Object raw = source.get(key);
        if (raw instanceof JSONArray jsonArray) {
            return jsonArray.toJavaList(String.class).stream().filter(StringUtils::hasText).map(String::trim).toList();
        }
        if (raw instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).filter(StringUtils::hasText).map(String::trim).toList();
        }
        if (raw instanceof String text && StringUtils.hasText(text)) {
            return List.of(text.trim());
        }
        return List.of();
    }

    public static String normalizeVisibility(String visibility) {
        if (!StringUtils.hasText(visibility)) {
            return "公开可见";
        }
        String trimmed = visibility.trim();
        switch (trimmed) {
            case "公开可见", "仅自己可见", "仅互关好友可见" -> {
                return trimmed;
            }
            default -> {
                String normalized = trimmed.toLowerCase().replace(" ", "").replace("-", "").replace("_", "");
                return switch (normalized) {
                    case "public", "open", "everyone", "all", "publicvisible" -> "公开可见";
                    case "private", "self", "selfonly", "onlyme", "me" -> "仅自己可见";
                    case "friends", "friendsonly", "mutual", "mutualfollow", "mutualfollows" -> "仅互关好友可见";
                    default -> throw new WorkException("可见性不支持，请使用 public/self-only/friends-only 或中文枚举");
                };
            }
        }
    }

    public static String normalizeSchedule(String scheduleAt) {
        if (!StringUtils.hasText(scheduleAt)) {
            return null;
        }
        try {
            OffsetDateTime target = OffsetDateTime.parse(scheduleAt.trim());
            OffsetDateTime now = OffsetDateTime.now();
            if (target.isBefore(now.plusHours(1))) {
                throw new WorkException("schedule_at 至少需要晚于当前时间 1 小时");
            }
            if (target.isAfter(now.plusDays(14))) {
                throw new WorkException("schedule_at 不能超过未来 14 天");
            }
            return target.toString();
        } catch (DateTimeParseException e) {
            throw new WorkException("schedule_at 必须是 RFC3339 格式");
        }
    }

    public static List<String> resolveImages(JSONObject sourceContext, List<XhsPublishContentAssetEntity> assetList) {
        List<String> imageList = new ArrayList<>(readStringList(sourceContext, "images"));
        if (!imageList.isEmpty()) {
            return imageList;
        }
        if (assetList == null || assetList.isEmpty()) {
            return List.of();
        }
        return assetList.stream()
                .filter(asset -> asset != null && StringUtils.hasText(asset.getAssetStatus()) && !"deleted".equalsIgnoreCase(asset.getAssetStatus()))
                .map(asset -> StringUtils.hasText(asset.getStorageRef()) ? asset.getStorageRef() : asset.getOriginUrl())
                .filter(StringUtils::hasText)
                .toList();
    }

    public static LocalDateTime nowPlusHours(Integer hours) {
        return LocalDateTime.now().plusHours(hours == null ? 24 : hours);
    }

}
