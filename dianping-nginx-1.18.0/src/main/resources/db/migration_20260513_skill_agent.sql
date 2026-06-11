CREATE TABLE IF NOT EXISTS ai_skill_registry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  skill_name VARCHAR(64) NOT NULL UNIQUE,
  skill_type VARCHAR(64) NOT NULL,
  description TEXT,
  input_schema TEXT,
  output_schema TEXT,
  permission_level VARCHAR(32) DEFAULT 'LOW',
  enabled TINYINT DEFAULT 1,
  version VARCHAR(32) DEFAULT '1.0',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_skill_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL UNIQUE,
  profile_json TEXT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO ai_skill_registry(skill_name, skill_type, description, input_schema, output_schema, permission_level, enabled, version)
SELECT 'shop_summary_skill', 'SHOP', 'Summarize shop reputation, highlights and advice by shop id',
       '{"shopId":"long","refresh":"boolean"}',
       '{"shopId":"long","finalSummary":"string","highFrequencyHighlights":"array","uniqueHighlights":"array","advice":"string"}',
       'LOW', 1, '1.0'
WHERE NOT EXISTS (SELECT 1 FROM ai_skill_registry WHERE skill_name = 'shop_summary_skill');

INSERT INTO ai_skill_registry(skill_name, skill_type, description, input_schema, output_schema, permission_level, enabled, version)
SELECT 'shop_recommend_skill', 'SHOP', 'Recommend nearby shops by natural language demand, location and category preference',
       '{"query":"string","x":"double","y":"double","currentTypeId":"long"}',
       '{"query":"string","intentSummary":"string","keywords":"array","recommendShops":"array"}',
       'LOW', 1, '1.0'
WHERE NOT EXISTS (SELECT 1 FROM ai_skill_registry WHERE skill_name = 'shop_recommend_skill');

INSERT INTO ai_skill_registry(skill_name, skill_type, description, input_schema, output_schema, permission_level, enabled, version)
SELECT 'review_risk_check_skill', 'CONTENT', 'Check review draft risks including ads, privacy, illegal content and abuse',
       '{"scene":"string","title":"string","content":"string","shopId":"long"}',
       '{"pass":"boolean","riskLevel":"string","riskScore":"int","riskTags":"array","reasons":"array","suggestion":"string"}',
       'LOW', 1, '1.0'
WHERE NOT EXISTS (SELECT 1 FROM ai_skill_registry WHERE skill_name = 'review_risk_check_skill');

INSERT INTO ai_skill_registry(skill_name, skill_type, description, input_schema, output_schema, permission_level, enabled, version)
SELECT 'order_draft_skill', 'ORDER', 'Generate order draft only. It cannot auto pay or confirm an order',
       '{"shopId":"long","voucherId":"long","items":"array","confirmed":"boolean"}',
       '{"draftOnly":"boolean","shopId":"long","voucherId":"long","needUserConfirm":"boolean","safetyNotice":"string"}',
       'HIGH', 1, '1.0'
WHERE NOT EXISTS (SELECT 1 FROM ai_skill_registry WHERE skill_name = 'order_draft_skill');
