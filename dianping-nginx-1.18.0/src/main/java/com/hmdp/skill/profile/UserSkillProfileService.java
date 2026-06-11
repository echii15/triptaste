package com.hmdp.skill.profile;

import cn.hutool.json.JSONUtil;
import com.hmdp.skill.dto.SkillFeedbackRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class UserSkillProfileService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> loadProfile(Long userId) {
        if (userId == null) {
            return new HashMap<>();
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT profile_json FROM user_skill_profile WHERE user_id = ?",
                    userId
            );
            if (rows.isEmpty() || rows.get(0).get("profile_json") == null) {
                return new HashMap<>();
            }
            return JSONUtil.toBean(String.valueOf(rows.get(0).get("profile_json")), Map.class);
        } catch (Exception e) {
            log.debug("load user skill profile failed, userId={}, err={}", userId, e.getMessage());
            return new HashMap<>();
        }
    }

    public void updateByFeedback(SkillFeedbackRequest request) {
        if (request == null || request.getUserId() == null) {
            return;
        }
        Map<String, Object> profile = loadProfile(request.getUserId());
        String feedbackType = request.getFeedbackType() == null ? "" : request.getFeedbackType();
        if ("COUPON_CLICK".equalsIgnoreCase(feedbackType) || "COUPON_RECEIVED".equalsIgnoreCase(feedbackType)) {
            profile.put("couponSensitivity", "high");
        } else if ("RECOMMEND_IGNORED".equalsIgnoreCase(feedbackType)) {
            profile.put("recommendAggressiveness", "low");
        } else if ("ORDER_CREATED".equalsIgnoreCase(feedbackType)) {
            profile.put("orderIntent", "strong");
        } else if ("COMMENT_EDITED".equalsIgnoreCase(feedbackType)) {
            profile.put("commentStyle", "user-edited");
        }
        profile.put("lastFeedbackSkill", request.getSkillName());
        upsertProfile(request.getUserId(), profile);
    }

    private void upsertProfile(Long userId, Map<String, Object> profile) {
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE user_skill_profile SET profile_json = ?, update_time = NOW() WHERE user_id = ?",
                    JSONUtil.toJsonStr(profile),
                    userId
            );
            if (updated == 0) {
                jdbcTemplate.update(
                        "INSERT INTO user_skill_profile(user_id, profile_json) VALUES(?, ?)",
                        userId,
                        JSONUtil.toJsonStr(profile)
                );
            }
        } catch (Exception e) {
            log.debug("upsert user skill profile failed, userId={}, err={}", userId, e.getMessage());
        }
    }
}
