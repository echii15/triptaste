package com.hmdp.risk.controller;

import com.hmdp.dto.Result;
import com.hmdp.risk.application.RiskAnalysisService;
import com.hmdp.risk.dto.DdlCheckRequest;
import com.hmdp.risk.dto.RiskAnalyzeRequest;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/risk")
public class RiskController {

    @Resource
    private RiskAnalysisService riskAnalysisService;

    @PostMapping("/analyze")
    public Result analyze(@RequestBody RiskAnalyzeRequest request) {
        if (request == null || request.getEventDesc() == null || request.getEventDesc().trim().isEmpty()) {
            return Result.fail("eventDesc 不能为空");
        }
        return Result.ok(riskAnalysisService.analyze(request.getEventDesc()));
    }

    @PostMapping("/ddl-check")
    public Result ddlCheck(@RequestBody DdlCheckRequest request) {
        if (request == null || request.getDdl() == null || request.getDdl().trim().isEmpty()) {
            return Result.fail("ddl 不能为空");
        }
        return Result.ok(riskAnalysisService.checkDdl(request.getDdl()));
    }
}
