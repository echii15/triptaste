package com.hmdp.ai.risk.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TableMetadata {
    private String tableName;
    private Long tableRows;
    private Long dataLength;
    private Integer slowSqlCount;
    private Double writeQps;
    private List<String> indexes = new ArrayList<>();
}
