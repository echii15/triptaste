package com.hmdp.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.ai.client.AiRemoteClient;
import com.hmdp.ai.client.dto.IntentParseRequest;
import com.hmdp.ai.client.dto.IntentParseResponse;
import com.hmdp.ai.client.dto.RecommendReasonRequest;
import com.hmdp.ai.client.dto.RecommendReasonResponse;
import com.hmdp.ai.client.dto.RecommendReasonShop;
import com.hmdp.ai.client.dto.ReviewRiskCheckRequest;
import com.hmdp.ai.client.dto.ReviewRiskCheckResponse;
import com.hmdp.config.properties.AiProperties;
import com.hmdp.dto.Result;
import com.hmdp.dto.ai.AiAssistantRequestDTO;
import com.hmdp.dto.ai.AiAssistantResponseDTO;
import com.hmdp.dto.ai.AiRecommendShopDTO;
import com.hmdp.dto.ai.AiReviewRiskCheckRequestDTO;
import com.hmdp.dto.ai.AiReviewRiskCheckResponseDTO;
import com.hmdp.dto.ai.AiShopSummaryDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IAiService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiServiceImpl implements IAiService {

    private static final ExecutorService WARMUP_EXECUTOR = Executors.newFixedThreadPool(2);
    private static final String ENGINE_FALLBACK = "FALLBACK";

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IShopService shopService;
    @Resource
    private IBlogService blogService;
    @Resource
    private IShopTypeService shopTypeService;
    @Resource
    private AiRemoteClient aiRemoteClient;
    @Resource
    private AiProperties aiProperties;

    @Override
    public Result getShopSummary(Long shopId, Boolean refresh) {
        if (shopId == null) {
            return Result.fail("shopId cannot be blank");
        }
        Shop shop = shopService.getById(shopId);
        if (shop == null) {
            return Result.fail("shop not found");
        }
        String cacheKey = RedisConstants.AI_SHOP_SUMMARY_KEY + shopId;
        if (!Boolean.TRUE.equals(refresh)) {
            AiShopSummaryDTO cached = readShopSummary(cacheKey);
            if (cached != null) {
                cached.setFromCache(true);
                return Result.ok(cached);
            }
        }

        List<Blog> blogs = blogService.query()
                .eq("shop_id", shopId)
                .orderByDesc("liked")
                .orderByDesc("create_time")
                .last("limit " + Math.max(aiProperties.getSummaryMaxBlogs(), 20))
                .list();
        AiShopSummaryDTO summary = buildLocalSummary(shop, blogs);
        safeCacheSet(cacheKey, JSONUtil.toJsonStr(summary), aiProperties.getSummaryTtlMinutes());
        return Result.ok(summary);
    }

    @Override
    public Result warmupShopSummary(Long shopId) {
        if (shopId == null) {
            return Result.fail("shopId cannot be blank");
        }
        WARMUP_EXECUTOR.submit(() -> {
            try {
                getShopSummary(shopId, true);
            } catch (Exception e) {
                log.warn("warmup shop summary failed, shopId={}, err={}", shopId, e.getMessage());
            }
        });
        return Result.ok("warmup submitted");
    }

    @Override
    public Result assistantRecommend(AiAssistantRequestDTO requestDTO) {
        try {
            if (requestDTO == null || StrUtil.isBlank(requestDTO.getQuery())) {
                return Result.fail("query cannot be blank");
            }
            String cacheKey = RedisConstants.AI_ASSISTANT_CACHE_KEY + buildAssistantCacheId(requestDTO);
            AiAssistantResponseDTO cached = readAssistantCache(cacheKey);
            if (cached != null) {
                cached.setFromCache(true);
                return Result.ok(cached);
            }

            List<ShopType> allTypes = shopTypeService.query().orderByAsc("sort").list();
            Long pickedTypeId = requestDTO.getCurrentTypeId();
            IntentParseResponse intent = null;
            if (CollUtil.isNotEmpty(allTypes)) {
                try {
                    IntentParseRequest req = new IntentParseRequest();
                    req.setQuery(requestDTO.getQuery());
                    req.setAvailableTypes(allTypes.stream().map(ShopType::getName).collect(Collectors.toList()));
                    intent = aiRemoteClient.parseIntent(req);
                } catch (Exception ignore) {
                    // fallback below
                }
                if (pickedTypeId == null && intent != null && CollUtil.isNotEmpty(intent.getTypeKeywords())) {
                    String kw = intent.getTypeKeywords().get(0);
                    for (ShopType type : allTypes) {
                        if (StrUtil.containsIgnoreCase(type.getName(), kw) || StrUtil.containsIgnoreCase(kw, type.getName())) {
                            pickedTypeId = type.getId();
                            break;
                        }
                    }
                }
            }

            QueryWrapper<Shop> wrapper = new QueryWrapper<>();
            if (pickedTypeId != null && pickedTypeId > 0) {
                wrapper.eq("type_id", pickedTypeId);
            }
            int limit = Math.max(aiProperties.getAssistantCandidateLimit(), 10);
            wrapper.orderByDesc("score").orderByDesc("comments").last("limit " + limit);
            List<Shop> shops = shopService.list(wrapper);
            if (CollUtil.isEmpty(shops)) {
                return Result.ok(emptyAssistantResponse(requestDTO.getQuery(), "No matched shops"));
            }

            List<AiRecommendShopDTO> recommendShops = shops.stream()
                    .limit(Math.max(1, aiProperties.getAssistantTopN()))
                    .map(this::toRecommendShopDTO)
                    .collect(Collectors.toList());
            fillReason(requestDTO.getQuery(), recommendShops);

            AiAssistantResponseDTO response = new AiAssistantResponseDTO();
            response.setQuery(requestDTO.getQuery());
            response.setIntentSummary(intent != null && StrUtil.isNotBlank(intent.getIntentSummary()) ? intent.getIntentSummary() : "Nearby recommendations");
            response.setKeywords(intent != null ? intent.getIncludeKeywords() : Collections.emptyList());
            response.setRecommendShops(recommendShops);
            response.setGeneratedAt(LocalDateTime.now().toString());
            response.setFromCache(false);

            safeCacheSet(cacheKey, JSONUtil.toJsonStr(response), aiProperties.getAssistantTtlMinutes());
            return Result.ok(response);
        } catch (Exception e) {
            log.error("assistantRecommend failed, req={}", JSONUtil.toJsonStr(requestDTO), e);
            return Result.ok(emptyAssistantResponse(requestDTO == null ? "" : requestDTO.getQuery(), "Service degraded, fallback response"));
        }
    }

    @Override
    public Result checkReviewRisk(AiReviewRiskCheckRequestDTO requestDTO) {
        try {
            if (requestDTO == null || StrUtil.isBlank(requestDTO.getContent())) {
                return Result.fail("content cannot be blank");
            }
            String cacheKey = RedisConstants.AI_REVIEW_RISK_CACHE_KEY + buildReviewRiskCacheId(requestDTO);
            AiReviewRiskCheckResponseDTO cached = readReviewRiskCache(cacheKey);
            if (cached != null) {
                cached.setFromCache(true);
                return Result.ok(cached);
            }

            ReviewRiskCheckRequest req = new ReviewRiskCheckRequest();
            req.setScene(StrUtil.blankToDefault(requestDTO.getScene(), "BLOG_NOTE"));
            req.setTitle(StrUtil.blankToDefault(requestDTO.getTitle(), ""));
            req.setContent(requestDTO.getContent());
            if (requestDTO.getShopId() != null) {
                Shop shop = shopService.getById(requestDTO.getShopId());
                if (shop != null) {
                    req.setShopName(shop.getName());
                    req.setShopDesc(shop.getShopDesc());
                }
            }

            ReviewRiskCheckResponse remote = aiRemoteClient.reviewRiskCheck(req);
            AiReviewRiskCheckResponseDTO response = new AiReviewRiskCheckResponseDTO();
            if (remote != null) {
                response.setEngine(StrUtil.blankToDefault(remote.getEngine(), ENGINE_FALLBACK));
                response.setPass(remote.getPass());
                response.setRiskLevel(StrUtil.blankToDefault(remote.getRiskLevel(), "REVIEW"));
                response.setRiskScore(remote.getRiskScore() == null ? 30 : remote.getRiskScore());
                response.setRiskTags(remote.getRiskTags() == null ? Collections.emptyList() : remote.getRiskTags());
                response.setReasons(remote.getReasons() == null ? Collections.emptyList() : remote.getReasons());
                response.setSuggestion(StrUtil.blankToDefault(remote.getSuggestion(), "Please review manually before publish"));
            } else {
                response = localRiskFallback();
            }
            response.setFromCache(false);
            response.setGeneratedAt(LocalDateTime.now().toString());
            safeCacheSet(cacheKey, JSONUtil.toJsonStr(response), aiProperties.getReviewRiskTtlMinutes());
            return Result.ok(response);
        } catch (Exception e) {
            log.error("checkReviewRisk failed, req={}", JSONUtil.toJsonStr(requestDTO), e);
            AiReviewRiskCheckResponseDTO fallback = localRiskFallback();
            fallback.setGeneratedAt(LocalDateTime.now().toString());
            fallback.setFromCache(false);
            return Result.ok(fallback);
        }
    }

    private AiShopSummaryDTO buildLocalSummary(Shop shop, List<Blog> blogs) {
        AiShopSummaryDTO dto = new AiShopSummaryDTO();
        dto.setShopId(shop.getId());
        dto.setShopName(shop.getName());
        dto.setEngine(ENGINE_FALLBACK);
        dto.setReviewCount(blogs == null ? 0 : blogs.size());
        dto.setChunkCount(1);
        dto.setHighFrequencyHighlights(Collections.singletonList("Score " + safeInt(shop.getScore()) + ", comments " + safeInt(shop.getComments())));
        dto.setUniqueHighlights(Collections.singletonList("Avg price " + safeLong(shop.getAvgPrice())));
        dto.setFinalSummary("Local summary generated from shop profile and review count.");
        dto.setAdvice("Check score, comments, distance and recent reviews before decision.");
        dto.setGeneratedAt(LocalDateTime.now().toString());
        dto.setFingerprint(SecureUtil.md5(shop.getId() + ":" + dto.getReviewCount()));
        dto.setFromCache(false);
        return dto;
    }

    private AiAssistantResponseDTO emptyAssistantResponse(String query, String summary) {
        AiAssistantResponseDTO dto = new AiAssistantResponseDTO();
        dto.setQuery(query);
        dto.setIntentSummary(summary);
        dto.setKeywords(Collections.emptyList());
        dto.setRecommendShops(Collections.emptyList());
        dto.setGeneratedAt(LocalDateTime.now().toString());
        dto.setFromCache(false);
        return dto;
    }

    private AiRecommendShopDTO toRecommendShopDTO(Shop shop) {
        AiRecommendShopDTO dto = new AiRecommendShopDTO();
        dto.setId(shop.getId());
        dto.setTypeId(shop.getTypeId());
        dto.setName(shop.getName());
        dto.setAddress(shop.getAddress());
        dto.setShopDesc(shop.getShopDesc());
        dto.setAvgPrice(shop.getAvgPrice());
        dto.setScore(shop.getScore());
        dto.setComments(shop.getComments());
        dto.setSold(shop.getSold());
        dto.setDistance(null);
        return dto;
    }

    private void fillReason(String query, List<AiRecommendShopDTO> recommendShops) {
        if (CollUtil.isEmpty(recommendShops)) {
            return;
        }
        try {
            RecommendReasonRequest req = new RecommendReasonRequest();
            req.setQuery(query);
            List<RecommendReasonShop> shops = new ArrayList<>(recommendShops.size());
            for (AiRecommendShopDTO shop : recommendShops) {
                RecommendReasonShop s = new RecommendReasonShop();
                s.setId(shop.getId());
                s.setName(shop.getName());
                s.setAddress(shop.getAddress());
                s.setShopDesc(shop.getShopDesc());
                s.setAvgPrice(shop.getAvgPrice());
                s.setScore(shop.getScore());
                s.setDistance(shop.getDistance());
                shops.add(s);
            }
            req.setShops(shops);
            RecommendReasonResponse resp = aiRemoteClient.recommendReason(req);
            Map<String, String> reasonById = resp == null ? null : resp.getReasonByShopId();
            for (AiRecommendShopDTO shop : recommendShops) {
                String reason = reasonById == null ? null : reasonById.get(String.valueOf(shop.getId()));
                if (StrUtil.isBlank(reason)) {
                    reason = buildLocalReason(query, shop);
                }
                shop.setReason(reason);
            }
        } catch (Exception e) {
            for (AiRecommendShopDTO shop : recommendShops) {
                shop.setReason(buildLocalReason(query, shop));
            }
        }
    }

    private String buildLocalReason(String query, AiRecommendShopDTO shop) {
        return "Score " + safeInt(shop.getScore())
                + ", comments " + safeInt(shop.getComments())
                + ", avg price " + safeLong(shop.getAvgPrice())
                + ". Matched query: " + query;
    }

    private AiShopSummaryDTO readShopSummary(String cacheKey) {
        try {
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StrUtil.isBlank(json)) {
                return null;
            }
            return JSONUtil.toBean(json, AiShopSummaryDTO.class);
        } catch (Exception ignore) {
            return null;
        }
    }

    private AiAssistantResponseDTO readAssistantCache(String cacheKey) {
        try {
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StrUtil.isBlank(json)) {
                return null;
            }
            return JSONUtil.toBean(json, AiAssistantResponseDTO.class);
        } catch (Exception ignore) {
            return null;
        }
    }

    private AiReviewRiskCheckResponseDTO readReviewRiskCache(String cacheKey) {
        try {
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StrUtil.isBlank(json)) {
                return null;
            }
            return JSONUtil.toBean(json, AiReviewRiskCheckResponseDTO.class);
        } catch (Exception ignore) {
            return null;
        }
    }

    private AiReviewRiskCheckResponseDTO localRiskFallback() {
        AiReviewRiskCheckResponseDTO dto = new AiReviewRiskCheckResponseDTO();
        dto.setEngine(ENGINE_FALLBACK);
        dto.setPass(false);
        dto.setRiskLevel("REVIEW");
        dto.setRiskScore(35);
        dto.setRiskTags(Collections.singletonList("MANUAL_REVIEW"));
        dto.setReasons(Collections.singletonList("Remote risk service unavailable"));
        dto.setSuggestion("Please review manually before publish");
        return dto;
    }

    private String buildAssistantCacheId(AiAssistantRequestDTO requestDTO) {
        String raw = "v1|" + requestDTO.getQuery() + "|" + requestDTO.getX() + "|" + requestDTO.getY() + "|" + requestDTO.getCurrentTypeId();
        return SecureUtil.md5(raw);
    }

    private String buildReviewRiskCacheId(AiReviewRiskCheckRequestDTO requestDTO) {
        String raw = "v1|" + StrUtil.blankToDefault(requestDTO.getScene(), "BLOG_NOTE")
                + "|" + requestDTO.getShopId()
                + "|" + StrUtil.blankToDefault(requestDTO.getTitle(), "")
                + "|" + StrUtil.blankToDefault(requestDTO.getContent(), "");
        return SecureUtil.md5(raw);
    }

    private int safeInt(Integer v) {
        return v == null ? 0 : v;
    }

    private long safeLong(Long v) {
        return v == null ? 0L : v;
    }

    private void safeCacheSet(String cacheKey, String value, Long ttlMinutes) {
        try {
            long ttl = ttlMinutes == null || ttlMinutes <= 0 ? 10L : ttlMinutes;
            stringRedisTemplate.opsForValue().set(cacheKey, value, ttl, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("cache write skipped, key={}, err={}", cacheKey, e.getMessage());
        }
    }
}
