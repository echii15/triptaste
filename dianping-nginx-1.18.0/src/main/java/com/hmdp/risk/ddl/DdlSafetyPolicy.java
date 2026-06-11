package com.hmdp.risk.ddl;

import java.util.Arrays;
import java.util.List;

public class DdlSafetyPolicy {

    private static final List<String> FORBIDDEN_EXECUTE_WORDS = Arrays.asList(
            "DROP ", "DELETE ", "UPDATE ", "INSERT ", "TRUNCATE ", "RENAME ", "CREATE "
    );

    private DdlSafetyPolicy() {
    }

    public static boolean containsForbiddenSql(String ddl) {
        if (ddl == null) {
            return false;
        }
        String upper = ddl.trim().toUpperCase();
        for (String word : FORBIDDEN_EXECUTE_WORDS) {
            if (upper.startsWith(word) || upper.contains(";" + word.trim())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAlterTable(String ddl) {
        return ddl != null && ddl.trim().toUpperCase().startsWith("ALTER TABLE");
    }
}
