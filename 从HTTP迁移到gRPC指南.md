# 从HTTP REST API迁移到gRPC的完整指南

## 📋 迁移概述

将 **hmdp-ai-service** 从HTTP REST API迁移到gRPC需要修改以下组件：

1. **AI服务端** (hmdp-ai-service)
2. **主业务端** (hm-dianping)
3. **通信协议** (HTTP → gRPC)
4. **序列化方式** (JSON → Protocol Buffers)

---

## 🔧 1. AI服务端改动 (hmdp-ai-service)

### **1.1 添加gRPC依赖**

```xml
<!-- pom.xml 添加 -->
<properties>
    <grpc.version>1.60.0</grpc.version>
    <protobuf.version>3.25.1</protobuf.version>
    <protoc.version>3.25.1</protoc.version>
</properties>

<dependencies>
    <!-- gRPC核心 -->
    <dependency>
        <groupId>net.devh</groupId>
        <artifactId>grpc-server-spring-boot-starter</artifactId>
        <version>3.1.0.RELEASE</version>
    </dependency>
    
    <!-- Protocol Buffers -->
    <dependency>
        <groupId>com.google.protobuf</groupId>
        <artifactId>protobuf-java</artifactId>
        <version>${protobuf.version}</version>
    </dependency>
    
    <!-- gRPC工具 -->
    <dependency>
        <groupId>io.grpc</groupId>
        <artifactId>grpc-protobuf</artifactId>
        <version>${grpc.version}</version>
    </dependency>
    <dependency>
        <groupId>io.grpc</groupId>
        <artifactId>grpc-stub</artifactId>
        <version>${grpc.version}</version>
    </dependency>
</dependencies>

<build>
    <extensions>
        <!-- Protocol Buffers编译器 -->
        <extension>
            <groupId>kr.motd.maven</groupId>
            <artifactId>os-maven-plugin</artifactId>
            <version>1.7.1</version>
        </extension>
    </extensions>
    
    <plugins>
        <!-- Protocol Buffers插件 -->
        <plugin>
            <groupId>org.xolstice.maven.plugins</groupId>
            <artifactId>protobuf-maven-plugin</artifactId>
            <version>0.6.1</version>
            <configuration>
                <protocArtifact>com.google.protobuf:protoc:${protoc.version}:exe:${os.detected.classifier}</protocArtifact>
                <pluginId>grpc-java</pluginId>
                <pluginArtifact>io.grpc:protoc-gen-grpc-java:${grpc.version}:exe:${os.detected.classifier}</pluginArtifact>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>compile</goal>
                        <goal>compile-custom</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### **1.2 定义Protocol Buffers文件**

创建 `src/main/proto/ai_service.proto`:

```protobuf
syntax = "proto3";

package com.hmdp.ai;

option java_multiple_files = true;
option java_package = "com.hmdp.ai.proto";
option java_outer_classname = "AiServiceProto";

// 基础消息类型
message ReviewSnippet {
    string title = 1;
    string content = 2;
    string short_summary = 3;
}

// 请求消息
message ChunkSummaryRequest {
    int64 shop_id = 1;
    string shop_name = 2;
    int32 chunk_index = 3;
    int32 total_chunks = 4;
    repeated ReviewSnippet reviews = 5;
}

message FinalSummaryRequest {
    string shop_name = 1;
    int32 review_count = 2;
    repeated ChunkSummaryResponse chunk_summaries = 3;
}

message IntentParseRequest {
    string query = 1;
    repeated string available_types = 2;
}

message RecommendReasonRequest {
    string query = 1;
    repeated RecommendReasonShop shops = 2;
}

message RecommendReasonShop {
    int64 id = 1;
    string name = 2;
    double distance = 3;
    double score = 4;
    string shop_desc = 5;
}

message RecommendRerankRequest {
    string query = 1;
    int32 top_n = 2;
    repeated string include_keywords = 3;
    repeated string exclude_keywords = 4;
    repeated RecommendRerankShop shops = 5;
}

message RecommendRerankShop {
    int64 id = 1;
    string name = 2;
    string type_name = 3;
    string address = 4;
    double distance = 5;
    double score = 6;
    double avg_price = 7;
    double base_rank_score = 8;
    string shop_desc = 9;
}

message ReviewRiskCheckRequest {
    string scene = 1;
    string title = 2;
    string content = 3;
    string shop_name = 4;
    string shop_desc = 5;
}

// 响应消息
message ChunkSummaryResponse {
    string engine = 1;
    string summary = 2;
    repeated string high_frequency_points = 3;
    repeated string unique_points = 4;
    repeated string keywords = 5;
}

message FinalSummaryResponse {
    string engine = 1;
    string summary = 2;
    string advice = 3;
    repeated string high_frequency_points = 4;
    repeated string unique_points = 5;
}

message IntentParseResponse {
    string intent_summary = 1;
    repeated string type_keywords = 2;
    repeated string include_keywords = 3;
    repeated string exclude_keywords = 4;
}

message RecommendReasonResponse {
    map<int64, string> reason_by_shop_id = 1;
}

message RecommendRerankResponse {
    string engine = 1;
    repeated int64 ranked_shop_ids = 2;
    map<int64, string> reason_by_shop_id = 3;
    map<int64, int32> score_by_shop_id = 4;
}

message ReviewRiskCheckResponse {
    bool pass = 1;
    string risk_level = 2;
    int32 risk_score = 3;
    repeated string risk_tags = 4;
    repeated string reasons = 5;
    string suggestion = 6;
    string engine = 7;
}

// 服务定义
service AiService {
    rpc SummarizeChunk(ChunkSummaryRequest) returns (ChunkSummaryResponse);
    rpc SummarizeFinal(FinalSummaryRequest) returns (FinalSummaryResponse);
    rpc ParseIntent(IntentParseRequest) returns (IntentParseResponse);
    rpc RecommendReason(RecommendReasonRequest) returns (RecommendReasonResponse);
    rpc RecommendRerank(RecommendRerankRequest) returns (RecommendRerankResponse);
    rpc ReviewRiskCheck(ReviewRiskCheckRequest) returns (ReviewRiskCheckResponse);
}
```

### **1.3 替换REST控制器为gRPC服务**

删除 `InternalAiController.java`，创建新的gRPC服务实现：

```java
package com.hmdp.ai.grpc;

import com.hmdp.ai.proto.*;
import com.hmdp.ai.service.AiOrchestrationService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class AiServiceGrpcImpl extends AiServiceGrpc.AiServiceImplBase {
    
    private final AiOrchestrationService aiOrchestrationService;
    
    @Override
    public void summarizeChunk(ChunkSummaryRequest request, 
                              StreamObserver<ChunkSummaryResponse> responseObserver) {
        try {
            // 转换请求
            com.hmdp.ai.dto.ChunkSummaryRequest dtoRequest = convertToDto(request);
            
            // 调用业务逻辑
            com.hmdp.ai.dto.ChunkSummaryResponse dtoResponse = 
                aiOrchestrationService.summarizeChunk(dtoRequest);
            
            // 转换响应
            ChunkSummaryResponse response = convertFromDto(dtoResponse);
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("AI服务处理失败: " + e.getMessage())
                .asRuntimeException());
        }
    }
    
    // 其他5个方法的实现...
    
    private com.hmdp.ai.dto.ChunkSummaryRequest convertToDto(ChunkSummaryRequest proto) {
        // 转换逻辑：Protocol Buffers → DTO
        // ... 实现转换逻辑
    }
    
    private ChunkSummaryResponse convertFromDto(com.hmdp.ai.dto.ChunkSummaryResponse dto) {
        // 转换逻辑：DTO → Protocol Buffers
        // ... 实现转换逻辑
    }
}
```

### **1.4 更新应用配置**

```yaml
# application.yaml
server:
  port: 8090

grpc:
  server:
    port: 9090  # gRPC端口
```

---

## 🔧 2. 主业务端改动 (hm-dianping)

### **2.1 添加gRPC依赖**

```xml
<!-- pom.xml 添加 -->
<properties>
    <grpc.version>1.60.0</grpc.version>
    <protobuf.version>3.25.1</protobuf.version>
</properties>

<dependencies>
    <!-- gRPC客户端 -->
    <dependency>
        <groupId>net.devh</groupId>
        <artifactId>grpc-client-spring-boot-starter</artifactId>
        <version>3.1.0.RELEASE</version>
    </dependency>
    
    <!-- Protocol Buffers -->
    <dependency>
        <groupId>com.google.protobuf</groupId>
        <artifactId>protobuf-java</artifactId>
        <version>${protobuf.version}</version>
    </dependency>
    
    <!-- gRPC工具 -->
    <dependency>
        <groupId>io.grpc</groupId>
        <artifactId>grpc-protobuf</artifactId>
        <version>${grpc.version}</version>
    </dependency>
    <dependency>
        <groupId>io.grpc</groupId>
        <artifactId>grpc-stub</artifactId>
        <version>${grpc.version}</version>
    </dependency>
</dependencies>

<build>
    <extensions>
        <extension>
            <groupId>kr.motd.maven</groupId>
            <artifactId>os-maven-plugin</artifactId>
            <version>1.7.1</version>
        </extension>
    </extensions>
    
    <plugins>
        <plugin>
            <groupId>org.xolstice.maven.plugins</groupId>
            <artifactId>protobuf-maven-plugin</artifactId>
            <version>0.6.1</version>
            <configuration>
                <protocArtifact>com.google.protobuf:protoc:3.25.1:exe:${os.detected.classifier}</protocArtifact>
                <pluginId>grpc-java</pluginId>
                <pluginArtifact>io.grpc:protoc-gen-grpc-java:1.60.0:exe:${os.detected.classifier}</pluginArtifact>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>compile</goal>
                        <goal>compile-custom</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### **2.2 更新配置类**

```java
// AiProperties.java
@ConfigurationProperties(prefix = "hmdp.ai")
public class AiProperties {
    private String grpcHost = "localhost";
    private int grpcPort = 9090;  // gRPC端口
    private long deadlineMs = 8000;  // 超时时间
    
    // 移除HTTP相关配置
    // private String baseUrl;
    // private int connectTimeoutMs;
    // private int readTimeoutMs;
}
```

### **2.3 替换REST客户端为gRPC客户端**

```java
// AiRemoteClientImpl.java
@Component
public class AiRemoteClientImpl implements AiRemoteClient {
    
    private final AiServiceGrpc.AiServiceBlockingStub aiServiceStub;
    
    public AiRemoteClientImpl(AiProperties aiProperties) {
        ManagedChannel channel = ManagedChannelBuilder
            .forAddress(aiProperties.getGrpcHost(), aiProperties.getGrpcPort())
            .usePlaintext()  // 本地开发用明文
            .build();
        
        this.aiServiceStub = AiServiceGrpc.newBlockingStub(channel);
    }
    
    @Override
    public ChunkSummaryResponse summarizeChunk(ChunkSummaryRequest request) {
        try {
            // 转换DTO到Protocol Buffers
            com.hmdp.ai.proto.ChunkSummaryRequest protoRequest = convertToProto(request);
            
            // 设置超时
            Context.CancellableContext context = Context.current()
                .withDeadline(Deadline.after(aiProperties.getDeadlineMs(), TimeUnit.MILLISECONDS), 
                            Executors.newSingleThreadScheduledExecutor());
            
            // 调用gRPC服务
            com.hmdp.ai.proto.ChunkSummaryResponse protoResponse = 
                context.call(() -> aiServiceStub.summarizeChunk(protoRequest));
            
            // 转换回DTO
            return convertFromProto(protoResponse);
            
        } catch (Exception e) {
            log.warn("gRPC call failed, method=summarizeChunk, error={}", e.getMessage());
            return null;
        }
    }
    
    // 其他方法的实现...
    
    private com.hmdp.ai.proto.ChunkSummaryRequest convertToProto(ChunkSummaryRequest dto) {
        // DTO → Protocol Buffers转换
        // ... 实现转换逻辑
    }
    
    private ChunkSummaryResponse convertFromProto(com.hmdp.ai.proto.ChunkSummaryResponse proto) {
        // Protocol Buffers → DTO转换
        // ... 实现转换逻辑
    }
}
```

### **2.4 更新配置**

```yaml
# application.yaml
hmdp:
  ai:
    grpc-host: localhost
    grpc-port: 9090      # gRPC端口
    deadline-ms: 8000    # 超时时间
    
    # 移除HTTP配置
    # base-url: http://127.0.0.1:8090
    # connect-timeout-ms: 1500
    # read-timeout-ms: 8000
```

---

## 🔧 3. 部署配置改动

### **3.1 Kubernetes配置**

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: hmdp-app
spec:
  template:
    spec:
      containers:
      - name: hm-dianping
        # 主容器配置...
        
      - name: hmdp-ai-service
        ports:
        - containerPort: 9090  # gRPC端口
          name: grpc
        env:
        - name: GRPC_SERVER_PORT
          value: "9090"
```

### **3.2 服务发现**

```yaml
# service.yaml
apiVersion: v1
kind: Service
metadata:
  name: hmdp-ai-service
spec:
  selector:
    app: hmdp-ai-service
  ports:
  - name: grpc
    port: 9090
    targetPort: 9090
    protocol: TCP
```

---

## 📊 4. 性能对比

| 指标 | HTTP REST | gRPC |
|------|-----------|------|
| **延迟** | ~1-2ms (同Pod) | ~0.5-1ms (同Pod) |
| **吞吐量** | 中等 | 高 (二进制协议) |
| **CPU使用** | 中等 | 低 (更高效序列化) |
| **内存使用** | 中等 | 低 (Protobuf压缩) |
| **网络带宽** | 中等 (JSON) | 低 (二进制) |
| **开发复杂度** | 低 | 中等 |
| **调试难度** | 低 | 中等 |

---

## ⚠️ 5. 迁移风险与注意事项

### **5.1 兼容性问题**
- **版本管理**: 需要确保客户端和服务端使用相同版本的.proto文件
- **字段变更**: Protobuf字段一旦定义不能随意删除，只能标记为deprecated

### **5.2 错误处理**
- **超时控制**: gRPC有更精细的超时控制
- **重试机制**: 需要实现客户端重试逻辑
- **熔断降级**: 考虑添加熔断器

### **5.3 调试困难**
- **工具依赖**: 需要专门的gRPC调试工具
- **日志记录**: 二进制协议难以直接查看内容

### **5.4 学习曲线**
- **团队培训**: 开发团队需要学习Protobuf语法
- **代码生成**: 需要熟悉gRPC代码生成流程

---

## 🚀 6. 迁移步骤建议

### **阶段1: 准备工作 (1-2天)**
1. ✅ 评估当前HTTP接口的使用情况
2. ✅ 设计Protobuf消息结构
3. ✅ 准备开发环境

### **阶段2: 并行开发 (3-5天)**
1. ✅ AI服务端添加gRPC支持 (保持HTTP接口)
2. ✅ 主业务端添加gRPC客户端 (保持REST调用)
3. ✅ 编写转换器 (DTO ↔ Protobuf)
4. ✅ 单元测试和集成测试

### **阶段3: 切换上线 (1天)**
1. ✅ 部署新版本 (支持双协议)
2. ✅ 灰度切换部分流量到gRPC
3. ✅ 监控性能指标和错误率
4. ✅ 全量切换并移除HTTP接口

### **阶段4: 优化收尾 (2-3天)**
1. ✅ 性能调优
2. ✅ 清理废弃代码
3. ✅ 更新文档

---

## 💡 7. 替代方案考虑

如果不想完全迁移到gRPC，可以考虑：

### **7.1 HTTP/2 + JSON**
- 保持REST API
- 升级到HTTP/2协议
- 获得部分性能提升

### **7.2 混合模式**
- 核心接口用gRPC
- 非核心接口保持HTTP
- 渐进式迁移

### **7.3 服务网格**
- 使用Istio等服务网格
- 透明升级通信协议
- 无需修改应用代码

---

## 📈 8. 预期收益

迁移到gRPC的预期收益：

- **性能提升**: 延迟降低20-50%，带宽减少30-60%
- **资源节省**: CPU使用降低15-25%，内存使用降低10-20%
- **可维护性**: 强类型接口，减少序列化错误
- **扩展性**: 支持流式调用，为未来功能预留空间

---

## 🎯 总结

**迁移复杂度**: 中等偏高  
**预计时间**: 1-2周  
**主要工作**: 
- 定义Protobuf schema
- 实现类型转换
- 更新客户端和服务端代码
- 配置和部署调整

**建议**: 如果当前HTTP性能满足需求，可考虑延迟迁移；如果对性能有更高要求，gRPC是值得的投资。