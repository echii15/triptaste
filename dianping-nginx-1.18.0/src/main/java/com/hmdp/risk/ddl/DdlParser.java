package com.hmdp.risk.ddl;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DdlParser {

    private static final Pattern ALTER_TABLE_PATTERN = Pattern.compile("(?i)alter\\s+table\\s+`?([a-zA-Z0-9_]+)`?.*");

    public String extractTableName(String ddl) {
        if (ddl == null) {
            return null;
        }
        Matcher matcher = ALTER_TABLE_PATTERN.matcher(ddl.trim());
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    public boolean addNotNullColumn(String ddl) {
        if (ddl == null) {
            return false;
        }
        String upper = ddl.toUpperCase();
        return upper.contains("ADD COLUMN") && upper.contains("NOT NULL");
    }

    public boolean modifyColumnType(String ddl) {
        if (ddl == null) {
            return false;
        }
        String upper = ddl.toUpperCase();
        return upper.contains(" MODIFY ") || upper.contains(" CHANGE ");
    }
}
