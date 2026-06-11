package com.hmdp.risk.domain.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RiskEventContext {
    private String eventName;
    private String eventDesc;
    private String eventTime;
    private String eventType;
    private String trafficLevel;
    private List<String> bizModules = new ArrayList<>();
    private List<String> coreApis = new ArrayList<>();
    private List<String> highRiskResources = new ArrayList<>();
    private List<TableMetadata> tableMetadataList = new ArrayList<>();
    private List<String> slowSqlList = new ArrayList<>();
    private List<String> redisSignals = new ArrayList<>();
    private List<String> mqSignals = new ArrayList<>();
    private List<String> knowledgeSnippets = new ArrayList<>();
    private String ddl;
}
