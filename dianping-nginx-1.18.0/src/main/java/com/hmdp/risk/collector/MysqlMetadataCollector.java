package com.hmdp.risk.collector;

import com.hmdp.risk.domain.model.RiskEventContext;
import com.hmdp.risk.domain.model.TableMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MysqlMetadataCollector implements RiskDataCollector {

    private static final List<String> DEFAULT_TABLES = Arrays.asList(
            "tb_shop", "tb_blog", "tb_voucher_order", "tb_seckill_voucher", "tb_voucher", "tb_user"
    );

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public String supportType() {
        return "MYSQL";
    }

    @Override
    public RiskCollectedData collect(RiskEventContext context) {
        RiskCollectedData data = new RiskCollectedData();
        for (String table : DEFAULT_TABLES) {
            try {
                TableMetadata metadata = queryTableMetadata(table);
                if (metadata != null) {
                    data.getTableMetadataList().add(metadata);
                }
            } catch (Exception e) {
                log.debug("collect mysql metadata failed, table={}, err={}", table, e.getMessage());
            }
        }
        return data;
    }

    private TableMetadata queryTableMetadata(String tableName) {
        List<Map<String, Object>> statusRows = jdbcTemplate.queryForList("SHOW TABLE STATUS LIKE ?", tableName);
        if (statusRows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = statusRows.get(0);
        TableMetadata metadata = new TableMetadata();
        metadata.setTableName(tableName);
        metadata.setTableRows(toLong(row.get("Rows")));
        metadata.setDataLength(toLong(row.get("Data_length")));
        metadata.setSlowSqlCount(0);
        metadata.setWriteQps(0D);

        List<Map<String, Object>> indexes = jdbcTemplate.queryForList("SHOW INDEX FROM " + tableName);
        for (Map<String, Object> index : indexes) {
            Object keyName = index.get("Key_name");
            Object columnName = index.get("Column_name");
            if (keyName != null && columnName != null) {
                metadata.getIndexes().add(keyName + "(" + columnName + ")");
            }
        }
        return metadata;
    }

    private Long toLong(Object val) {
        if (val == null) {
            return 0L;
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        try {
            return Long.valueOf(String.valueOf(val));
        } catch (Exception e) {
            return 0L;
        }
    }
}
