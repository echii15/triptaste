package com.hmdp.risk.collector;

import com.hmdp.risk.domain.model.TableMetadata;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RiskCollectedData {
    private List<TableMetadata> tableMetadataList = new ArrayList<>();
    private List<String> slowSqlList = new ArrayList<>();
    private List<String> redisSignals = new ArrayList<>();
    private List<String> mqSignals = new ArrayList<>();
}
