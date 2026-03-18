# 电商订单服务 + 支付服务（Java）

基于 **Java 17 + Spring Boot 3** 的示例实现，覆盖以下能力：

1. 用户在结算页下单  
2. 用户支付  
3. 防重复下单  
4. 下单时扣库存、使用优惠券  
5. 库存/优惠券交互采用 **TCC（Try-Confirm-Cancel）** 模式  
6. 订单号与支付号采用 **Leaf Segment** 号段模式  
7. 全链路幂等（下单与支付均支持 `Idempotency-Key`）  
8. 面向高并发（无锁/低锁数据结构、分段发号、按订单细粒度锁）

---

## 项目结构

```text
src/main/java/com/ecommerce/orderpay
├── api                 # REST接口
├── common              # 通用模型、异常、幂等、防重、锁
├── domain              # 订单/支付领域对象
├── leaf                # Leaf Segment 发号器
├── repository          # 仓储（示例为内存实现）
├── service             # 订单服务、支付服务
└── tcc                 # 库存/优惠券TCC服务与协调器
```

---

## 核心业务流程

### 1) 下单流程

`POST /api/v1/orders`

- 使用 `Idempotency-Key` 做幂等门禁：
    - 同 key + 同请求体：直接返回历史响应
    - 同 key + 不同请求体：拒绝
- 业务防重：`userId + checkoutToken` 组成业务唯一键，避免重复提交创建多个订单
- 订单号生成：Leaf Segment（预加载号段、并发安全）
- 执行 TCC（下单阶段只做 Try）：
    1. Inventory Try：冻结库存
    2. Coupon Try：冻结优惠券
    3. 任一步失败则 Cancel 回滚
- TCC 行为会落入事务表（记录每个分支的 Try/Confirm/Cancel 及结果）
- 订单状态机流转：`INIT -> PENDING_PAYMENT`

### 2) 支付流程

`POST /api/v1/payments`

- 使用 `Idempotency-Key` 做支付请求幂等
- 按 `orderId` 加细粒度锁，防止并发重复支付
- 支付单唯一性：
    - `orderId` 唯一
    - `externalNo`（三方流水）唯一
- 支付成功后再执行 TCC Confirm（库存最终扣减、优惠券最终核销）
- 订单状态机流转：`PENDING_PAYMENT -> PAYING -> PAID`

---

## 快速启动

```bash
mvn spring-boot:run
```

---

## 接口示例

### 下单

```bash
curl -X POST 'http://localhost:8080/api/v1/orders' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: order-req-001' \
  -d '{
    "userId":"U1001",
    "checkoutToken":"CHK-001",
    "items":[{"skuId":"SKU-1","quantity":2}],
    "couponId":"COUPON-1",
    "amountCents":1200
  }'
```

### 支付

```bash
curl -X POST 'http://localhost:8080/api/v1/payments' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: pay-req-001' \
  -d '{
    "userId":"U1001",
    "orderId":1,
    "channel":"ALIPAY",
    "externalNo":"EXT-001"
  }'
```

---

## 高并发/高性能/高可用/高稳定设计点

### 高并发 & 高性能

- Leaf Segment 号段发号，减少中心存储访问频率
- 号段低水位异步预取，降低发号阻塞概率
- 幂等、防重、仓储使用 `ConcurrentHashMap` + CAS 风格更新
- 支付使用按订单维度锁，避免全局锁争用

### 高可用 & 高稳定

- 幂等状态机（PROCESSING/DONE）避免重复执行破坏数据
- TCC 行为事务表，便于追踪 Try/Confirm/Cancel 与补偿
- TCC 补偿（Cancel）在异常路径回滚冻结资源
- 订单状态机约束流转路径，避免非法状态跳转
- 明确错误码与统一异常处理
- Tomcat 线程池和队列参数可调（`application.yml`）

> 说明：本示例为单体演示实现，生产环境建议把幂等、防重、仓储落到 Redis/MySQL，并加入 MQ、重试任务、熔断限流、监控告警与多副本部署。
