# 洪兴洗车电商服务平台

一个面向「汽车用品 + 到店养车服务」的微服务电商演示项目：商品商城与门店服务共用一套订单、支付、评价体系，覆盖登录、检索、下单、支付、订单进度、评价与点赞的完整闭环。

> 开发环境密码、Token、密钥均为演示用固定值，仅用于本地学习，不要用于生产。

---

## 一、技术栈

| 层次 | 选型 |
| --- | --- |
| 运行时 | JDK 17 |
| 基础框架 | Spring Boot 3.2.4、Spring Cloud 2023.0.1、Spring Cloud Alibaba 2023.0.1.0 |
| 注册/配置中心 | Nacos 2.x（服务注册 + 配置中心；`@RefreshScope` 热更新目前仅覆盖门店缓存参数 `hc.shop.cache.*`） |
| 服务调用 | OpenFeign + LoadBalancer + Sentinel 降级工厂 |
| 流量治理 | Sentinel 1.8.6（QPS 流控、RT 熔断、`blockHandler`、`FallbackFactory`） |
| 持久层 | MyBatis-Plus 3.5.7（分页插件、逻辑删除） |
| 数据库 | MySQL 8（容器映射 **3307**，默认绑定 `127.0.0.1`） |
| 缓存 | Redis 7（容器映射 **6380**，密码由 `REDIS_PASSWORD` 注入） |
| 消息队列 | RocketMQ 5.x（下单削峰、延迟关单、索引同步、点赞同步） |
| 搜索 | Elasticsearch 8.10.4 + IK 分词 |
| 实时通信 | WebSocket（支付结果、订单进度）+ 心跳保活 + 指数退避重连 |
| AI 导购 | OpenAI 兼容 `/chat/completions` + Tool Calling（ReAct ≤3 轮）+ SSE 流式；JDK HttpClient 直连，无额外 SDK |
| 前端 | Vue 3 + Vite 5 + Element Plus + Pinia + Axios |
| 压测 | JMeter 5.6.3 |

---

## 二、整体架构

```text
浏览器 (Vue3 / Vite :5173)
        │  /api/**  /ws/**
        ▼
   gateway :8080  ── JWT 校验、白名单、可选鉴权（/api/ai）、WebSocket 路由
        │
        ├── user-service    :8081  hc_user     登录注册 / 用户 / 点赞 / 关注 / 用户管理 / 视角切换
        ├── product-service :8082  hc_product  分类 / 商品 / 库存扣减
        ├── shop-service    :8083  hc_shop     门店 / 服务项目 / Redis 三防 + 故障降级 / ES 检索
        ├── order-service   :8084  hc_order    下单削峰 / 状态机 / 延迟关单 / 订单 WS / 状态暂存降级
        ├── pay-service     :8085  hc_pay      Mock 支付 / 回调幂等 / 支付 WS
        ├── review-service  :8086  hc_review   评价 / 评分聚合 / 点赞数同步
        └── ai-service      :8087  (Redis DB7) 养车顾问 / Tool Calling ReAct / SSE / 降级兜底

中间件：Nacos 8848 · RocketMQ 9876/10911 · Elasticsearch 9200 · MySQL 3307 · Redis 6380
```

外部请求只允许经过网关；各服务的 `/internal/**` 不对外路由，服务间内部调用需携带 `X-Internal-Token`。`/api/ai/**` 为可选鉴权：匿名即可对话，携带有效登录态时网关透传 `X-User-Id`（始终先清除客户端伪造的身份头）。

---

## 三、目录结构

```text
DianShang/
├── pom.xml                     # 聚合工程，统一依赖版本
├── docker-compose.yml          # MySQL / Redis / Nacos / RocketMQ / ES
├── docker/                     # 中间件初始化脚本与配置
├── common/                     # Result、异常、枚举、JWT、常量、跨服务 DTO
├── gateway/                    # Spring Cloud Gateway + JWT 全局过滤器
├── user-service/
├── product-service/
├── shop-service/
├── order-service/
├── pay-service/
├── review-service/
├── ai-service/                 # AI 养车顾问（Tool Calling ReAct + SSE + Redis DB7 记忆 + 降级）
├── frontend/                   # Vue3 前端（Vite 代理到 8080）
├── perf/                       # JMeter .jmx 与压测报告
└── logs/                       # 各服务本地运行日志
```

---

## 四、启动中间件

前置条件：Docker Desktop 已启动。

```bash
# 进入项目根目录（按实际克隆位置调整）
cd /path/to/DianShang
docker compose up -d
docker compose ps
```

首次启动（`hc-mysql-data` 卷为空）会自动执行 `docker/mysql/init/` 下的建库建表与种子 SQL。全部容器默认通过 `${BIND_HOST:-127.0.0.1}` 仅绑定本机回环地址，防止公网直接扫描中间件端口；上线生产务必提前复制 `.env.example` 为 `.env` 替换默认弱口令与密钥。

| 中间件 | 地址 | 账号 / 默认说明 |
| --- | --- | --- |
| MySQL | `127.0.0.1:3307` | `root` / 见 `.env`（本地开发兜底 `123456`） |
| Redis | `127.0.0.1:6380` | 见 `.env`（本地开发兜底 `123456`），DB 0–7 分服务使用（AI 顾问用 DB 7） |
| Nacos 控制台 | http://127.0.0.1:8848/nacos | 本地开发默认关闭鉴权（生产设 `NACOS_AUTH_ENABLE=true`） |
| RocketMQ NameServer | `127.0.0.1:9876` | — |
| Elasticsearch | http://127.0.0.1:9200 | 本地开发默认关闭认证（仅限内网/本机回环访问） |

> 本机若已有 3306 / 6379 的 MySQL、Redis，不会被本项目占用或修改。

常用检查命令：

```bash
docker exec hc-mysql mysql -uroot -p123456 -e "show databases;"
docker exec hc-redis redis-cli -a 123456 ping
curl http://127.0.0.1:9200/_cat/indices?v
```

---

## 五、构建与启动后端

### 0. 一键启停（Windows 推荐）

根目录提供两个脚本，双击或在终端运行均可：

- `start-all.bat`：检查 Docker 中间件（缺失时自动 `docker compose up -d` 并等待健康），使用 `JAVA17` / `JAVA_HOME` / `PATH` 中的 JDK 17 以 `-Xms128m -Xmx256m -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8` 后台启动 8 个服务，日志写入 `logs/<service>.log`，端口就绪后打印各服务状态，最后询问是否顺便启动前端 Vite（已在运行则跳过）。如本地需指定特定 JDK 目录，可在脚本顶部设置 `JAVA17` 变量；端口已被占用的服务会自动跳过。**注意**：结尾的「是否启动前端 [Y,N]」是交互问句，在无人值守/后台方式运行脚本时无法作答、前端不会启动；此时请交互运行脚本并回答 Y，或另行执行第六节的 `npm run dev`。
- `stop-all.bat`：仅按命令行精确匹配 `target/<service>-1.0.0.jar` 的 8 个 Java 进程并停止，不会误杀宿主机其他 Java 程序，也不停止前端 Vite 与 Docker 中间件；停止后逐端口（8080–8087）确认释放。

### 0.1 Linux 生产 / 云服务器部署（`deploy/`）

1. 复制环境变量模板并替换为强密码与随机密钥（`.env` 已被 `.gitignore` 忽略，禁止提交仓库）：

   ```bash
   cp .env.example .env
   # 生产关键项：JWT_SECRET / INTERNAL_TOKEN / MYSQL_PASSWORD / REDIS_PASSWORD
   # 生产务必关闭演示开关：HC_DEMO_ENABLED=false、HC_PERF_ENABLED=false
   # 业务服务默认绑定 SERVER_ADDRESS=127.0.0.1，仅由 Nginx 暴露 80/443 -> Gateway 8080
   ```

2. 启动中间件与后端 8 个服务（自动读取 `.env` 并注入 `-Duser.timezone=Asia/Shanghai`）：

   ```bash
   docker compose --env-file .env up -d
   mvn clean package -DskipTests
   bash deploy/start-all.sh          # 停止使用 bash deploy/stop-all.sh
   ```

3. 构建前端并配置 Nginx 反代（含 SPA `try_files`、`/api/ai/chat/stream` SSE 无缓冲与 `/ws/` WebSocket 升级头）：

   ```bash
   cd frontend && npm ci && npm run build
   # 参考 deploy/nginx.conf 挂载到 /etc/nginx/conf.d/hongcheng.conf 后重载 Nginx
   ```

首次使用需先完成下面的构建（脚本依赖各服务 `target/*.jar`）。

### 1. 构建

```bash
cd /path/to/DianShang   # 项目根目录，按实际克隆位置调整
export JAVA_HOME=<本机 JDK 17 安装目录>   # 示例：/usr/lib/jvm/java-17-openjdk
mvn clean package -DskipTests   # 需 Maven 3.9+ 已加入 PATH
```

### 2. 启动 8 个服务

每个服务一个终端（Git Bash / Shell 示例），或用 `nohup ... &` 放后台：

```bash
cd /path/to/DianShang   # 项目根目录，按实际克隆位置调整
JAVA="$JAVA_HOME/bin/java.exe"   # JAVA_HOME 指向本机 JDK 17
$JAVA -Xms128m -Xmx256m -Dfile.encoding=UTF-8 -jar gateway/target/gateway-1.0.0.jar
$JAVA -Xms128m -Xmx256m -Dfile.encoding=UTF-8 -jar user-service/target/user-service-1.0.0.jar
$JAVA -Xms128m -Xmx256m -Dfile.encoding=UTF-8 -jar product-service/target/product-service-1.0.0.jar
$JAVA -Xms128m -Xmx256m -Dfile.encoding=UTF-8 -jar shop-service/target/shop-service-1.0.0.jar
$JAVA -Xms128m -Xmx256m -Dfile.encoding=UTF-8 -jar order-service/target/order-service-1.0.0.jar
$JAVA -Xms128m -Xmx256m -Dfile.encoding=UTF-8 -jar pay-service/target/pay-service-1.0.0.jar
$JAVA -Xms128m -Xmx256m -Dfile.encoding=UTF-8 -jar review-service/target/review-service-1.0.0.jar
$JAVA -Xms128m -Xmx256m -Dfile.encoding=UTF-8 -jar ai-service/target/ai-service-1.0.0.jar
```

启动后在 Nacos「服务列表」应看到 8 个服务全部健康；网关入口 `http://127.0.0.1:8080`。

单个服务重启示例（只重启店铺服务）：

```bash
# 先停掉旧进程（端口 8083），再执行上面的 shop-service 启动命令
```

日志默认输出到控制台，也可重定向到 `logs/<service>.log`。

### 3. 端口一览

| 服务 | 端口 | Redis DB |
| --- | --- | --- |
| gateway | 8080 | 0 |
| user-service | 8081 | 1 |
| product-service | 8082 | 2 |
| shop-service | 8083 | 3 |
| order-service | 8084 | 4 |
| pay-service | 8085 | 5 |
| review-service | 8086 | 6 |
| ai-service | 8087 | 7 |

### 4. AI 养车顾问配置（仅环境变量，严禁写死密钥）

`ai-service` 的大模型接入项全部由环境变量注入，`application.yml` 只保留占位与默认值，代码与配置文件中不出现任何真实密钥：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_BASE_URL` | `https://api.deepseek.com/v1` | OpenAI 兼容服务地址 |
| `AI_MODEL` | `deepseek-chat` | 模型名 |
| `AI_API_KEY` | 空 | 大模型 API Key；**留空即触发本地检索降级** |

```bash
# 可选：配置真实 Key 后启动（PowerShell 示例，密钥不要提交到仓库）
$env:AI_API_KEY="<你的 Key>"
$env:AI_MODEL="deepseek-chat"
# 然后运行 start-all.bat 或上面的 ai-service 启动命令
```

对话入口 `POST /api/ai/chat/stream`（SSE，匿名可用）：

- 事件流：`meta`（回传 sessionId）→ `status`（正在检索商品/门店/订单…）→ `delta`（文本增量，前端打字机渲染）→ `cards`（结构化推荐卡片 JSON：products/shops/services/orders）→ `done`（含 degraded 标记）；异常时 `error`。
- 工具集（全部经 OpenFeign + `X-Internal-Token` 调真实微服务，不造假数据）：`search_products(keyword)` 调 product-service；`search_shops_and_services(keyword)` 调 shop-service 的 ES 分词检索；`query_my_orders()` 仅当网关透传 `X-User-Id` 时调 order-service，未登录直接提示需登录、绝不越权。
- ReAct 最多 3 轮工具调用，防死循环；多轮记忆存 Redis DB7，按 sessionId 保留最近 10 轮、TTL 2 小时。
- 降级兜底：未配 Key / 余额不足 / 大模型超时 / Sentinel 限流熔断时，自动提取关键词调用 `search_products` 与 `search_shops_and_services` 返回真实推荐卡片与友好提示，接口永不 500。

```bash
# 匿名对话（未配 Key 时走本地检索降级，仍返回真实商品/门店卡片）
curl -N -X POST http://127.0.0.1:8080/api/ai/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message":"附近哪里可以洗车？"}'
```

---

## 六、启动前端

```bash
cd /path/to/DianShang/frontend   # 按实际克隆位置调整
npm install
npm run dev
```

访问 http://127.0.0.1:5173 。Vite 已将 `/api` 与 `/ws` 代理到 `http://127.0.0.1:8080`，前端不直连任何微服务。

生产构建：

```bash
npm run build
npm run preview
```

构建产物在 `frontend/dist`，第三方库已通过 `manualChunks` 拆分，避免主包体积告警。

---

## 七、演示账号与完整演示路径

| 账号 | 密码 | 说明 |
| --- | --- | --- |
| zhangsan | 123456 | 普通用户 |
| lisi | 123456 | 普通用户 |
| wangwu | 123456 | 普通用户 |
| admin | 不入库明文（仓库仅存 BCrypt 哈希） | 管理员 `ADMIN`，登录后导航出现「商家工作台」 |

推荐演示流程：

1. 登录 `zhangsan`，在首页或「养车商城 / 门店服务」检索商品、门店与服务项目；Header 搜索框会聚合商品、门店与服务结果。
2. 商品加入购物车可勾选多件合并结算；门店服务可选择项目、到店时间并提交预约订单。
3. 下单接口先返回 `QUEUED`，前端轮询 `/api/order/state/{orderNo}` 展示削峰结果。
4. 收银台按 `payDeadline` 展示同步倒计时与本地生成的演示二维码；打开扫码窗口点击「完成支付」，页面通过 WebSocket 收到 `PAY_SUCCESS`，断开时自动轮询兜底。默认支付时限为 30 分钟（`ORDER_PAY_TIMEOUT_DELAY_LEVEL=16`、`ORDER_PAY_TIMEOUT_SECONDS=1800`，本地短时验收可改为 `4` / `30`），超时未支付由延迟消息自动关单并回补库存；生产可通过 `HC_DEMO_ENABLED=false` 一键关闭模拟支付与买家演示发货入口。
5. 订单详情右上角「演示工具」可模拟商家发货 / 门店接单；订单进入「服务中」后点击「确认完成」。
6. 已完成订单可发布评价，支持本地选择图片上传（JPG/PNG/WebP/GIF，≤5MB，最多 5 张）；门店评价区实时展示聚合评分、星级分布与服务订单评价。
7. 「我的评价」可跳回对应门店评价区；「个人中心」可修改昵称、手机号、性别，查看关注门店与点赞内容。
8. 已完成 / 已取消订单提供「再次购买 / 再次预约」；支付超时页面会引导重新下单。
9. 全站右下角「养车顾问」悬浮按钮可展开侧边对话抽屉：快捷提问（机油 / 洗车 / 查订单）、SSE 流式打字机回复、检索状态提示，并在回复下方渲染可点击跳转的商品 / 门店 / 服务 / 订单卡片。未配 `AI_API_KEY` 时自动降级为本地真实检索；查订单需登录。
10. 管理员 `admin` 登录后自动进入「商家工作台」（`/admin`，普通用户登录进首页、访问 `/admin` 自动跳回首页）：门店与服务管理（新增/编辑/上下架，改动经 RocketMQ 同步 ES）、订单处理（门店接单 / 商家发货，C 端 WebSocket 实时收到进度）、评价回复、用户管理四个 Tab。订单处理默认只看「待处理」（已支付待接单/发货），切「进行中 / 全部」可看其余状态。管理端订单/评价列表展示真实用户名与昵称（预约手机号/联系人部分脱敏），C 端前台仍为匿名脱敏展示。
11. 用户管理 Tab 可对**启用的普通用户**点「切换视角」：确认后签发 15 分钟短效模拟 Token，前台顶部出现常驻横幅「当前正在以 xxx 视角操作」，期间下单/评价等操作归属该用户；点横幅「返回管理端 / 退出模拟」原样恢复管理员会话（不重新登录）。禁止模拟管理员与自身；开始/退出均写 `IMPERSONATION_START/EXIT` 审计日志，网关对模拟 Token 额外透传 `X-Impersonator-Id`。后台顶栏右上角「进入前台」则是以管理员自身账号查看前台，不切换身份。

评价图片接口：

```bash
# 上传（需登录）：返回 /api/review/uploads/yyyyMMdd/{uuid}.{ext}
curl -X POST http://127.0.0.1:8080/api/review/upload \
  -H "Authorization: Bearer <JWT>" \
  -F "file=@/path/to/photo.jpg"

# 图片公开访问（网关白名单，无需登录）
curl -I http://127.0.0.1:8080/api/review/uploads/20261116/example.jpg

# 门店评价聚合
curl http://127.0.0.1:8080/api/review/shop/1/stats
```

管理端接口（统一校验管理员 `ADMIN` 角色 JWT）：

```bash
# 商家回复评价（仅 ADMIN 角色 JWT 可调用）
curl -X PUT http://127.0.0.1:8080/api/review/1/reply \
  -H "Authorization: Bearer <ADMIN_JWT>" \
  -H "Content-Type: application/json" \
  -d '{"reply":"感谢您的评价，欢迎再次到店"}'

# 门店/服务管理（仅 ADMIN 角色 JWT 可调用）
curl http://127.0.0.1:8080/api/manage/shop/page \
  -H "Authorization: Bearer <ADMIN_JWT>"

# 管理员订单列表 / 接单 / 发货：仅认 ADMIN 角色 JWT
curl http://127.0.0.1:8080/api/order/manage/page?status=10 \
  -H "Authorization: Bearer <ADMIN_JWT>"
curl -X POST http://127.0.0.1:8080/api/order/manage/HCxxxx/deliver \
  -H "Authorization: Bearer <ADMIN_JWT>"

# 管理员评价列表（含真实用户名/昵称）：仅认 ADMIN 角色 JWT
curl http://127.0.0.1:8080/api/review/manage/page?shopId=1 \
  -H "Authorization: Bearer <ADMIN_JWT>"

# 视角切换：仅 ADMIN JWT 可发起；目标须为启用普通用户（禁止管理员/自身）
curl -X POST http://127.0.0.1:8080/api/user/manage/1/impersonate \
  -H "Authorization: Bearer <ADMIN_JWT>"
# 返回 { token, expiresIn: 900, user, impersonatorId, impersonatedBy }；
# 持模拟 Token 退出（仅写审计，身份恢复由前端还原原管理员 Token 完成）
curl -X POST http://127.0.0.1:8080/api/user/impersonation/exit \
  -H "Authorization: Bearer <IMPERSONATION_TOKEN>"
```

---

## 八、技术亮点与代码位置

| 亮点 | 实现位置 | 关键点 |
| --- | --- | --- |
| Nacos 注册 + 配置中心 | 各服务 `application.yml`、`shop-service/.../service/ShopCacheService.java` | 8 个服务全部注册；`@RefreshScope` 仅标注在 `ShopCacheService`，支持 `hc.shop.cache.*` 热更新；其余服务的 `@Value` 参数（如 Sentinel 阈值）修改后需重启生效 |
| OpenFeign 跨服务调用 | `order-service/.../feign/`、`pay-service/.../feign/`、`review-service/.../feign/` | 商品、库存、订单、评价上下文均通过 Feign；内部 Token 拦截器鉴权 |
| Sentinel 流控 + 熔断 | `order-service/.../config/OrderSentinelConfig.java`、`pay-service/.../config/PaySentinelConfig.java` | 下单/支付 QPS 流控 + RT 熔断，`@SentinelResource(blockHandler)` 返回 1308/1406 |
| Feign 降级 | `*ClientFallbackFactory.java`、各服务 `application.yml` | 开启 `feign.sentinel.enabled=true`（无 `spring.cloud` 前缀）后生效；降级工厂返回 `Result.fail(503)`，由调用方转业务异常并触发 MQ 重试 |
| 下单削峰 | `order-service/.../service/OrderSubmitService.java`、`mq/OrderCreateListener.java` | Redis 状态机 + RocketMQ 异步建单，前端轮询 `QUEUED` |
| 延迟关单 + 库存回滚 | `order-service/.../mq/OrderTimeoutListener.java`、`OrderService.timeoutCancel` | 延迟消息到期检查未支付订单，状态机置 40 并调用库存恢复 |
| 支付幂等 | `pay-service/.../service/PayCallbackService.java`、`t_pay_transaction.uk_out_trade_no` | 唯一索引 + 订单状态机双重校验，重复回调直接返回成功 |
| Redis 缓存三防 | `shop-service/.../service/ShopCacheService.java` | 空值缓存（穿透）、互斥锁重建（击穿）、基础 TTL + 随机抖动（雪崩）；脏缓存删除自愈 |
| Redis 故障降级（门店缓存） | `shop-service/.../service/ShopCacheService.java` | 连接失败熔断 10s：窗口内直连 DB、跳过锁与写缓存；降级期 evict 记入待补偿集合，恢复后首次访问补删，避免脏缓存残留 |
| Redis 故障降级（下单状态） | `order-service/.../service/OrderStateStore.java` | 同款 10s 熔断：写入跳过不阻断入队/建单；读取返回合成 `QUEUED`（而非 `NOT_FOUND`），轮询方继续等 DB 建单结果，不会误判终止 |
| 缓存对照接口 | `ShopController` | `/api/shop/{id}` 走缓存；`/api/shop/db/{id}` 直连 MySQL（默认关闭 `hc.perf.enabled=false`，压测需设 `HC_PERF_ENABLED=true`） |
| ES + IK 检索 | `shop-service/.../service/EsIndexService.java`、`SearchService.java` | 门店/服务标题与描述分词检索，Mapping 使用 `ik_max_word` |
| RocketMQ 同步 ES | `shop-service/.../mq/ShopSyncListener.java` | 新增/修改/下架发送 `HC_SHOP_SYNC`，异步更新索引 |
| 评价点赞同步 | `user-service/.../service/LikeService.java`、`review-service/.../mq/ReviewLikeListener.java` | 点赞成功发 `HC_REVIEW_LIKE`，消费端按 `eventId` 去重并原子增减 `like_count` |
| 支付结果 WebSocket | `pay-service/.../websocket/`、`frontend/src/views/PayView.vue` | 握手 `?token=` 校验 JWT，服务端推送 `PAY_SUCCESS` / `PAY_FAILED`；收银台 `payDeadline` + 本地二维码 + 轮询兜底 |
| 评价图片上传 | `review-service/.../service/ReviewImageStorageService.java`、`ReviewUploadController.java`、`WebMvcConfig.java` | 魔数校验 JPG/PNG/WebP/GIF，5MB 上限，UUID 文件名，日期目录，`/api/review/uploads/**` 静态映射 |
| 图片访问边界 | `gateway/.../filter/JwtAuthFilter.java` | 仅 `/api/review/uploads/` 公开，上传接口仍需登录 |
| 门店评价聚合 | `review-service/.../mapper/ReviewMapper.java`、`ReviewService.statsByShop` | 可见评价数、平均分、1–5 星分布实时统计，`GET /api/review/shop/{shopId}/stats` |
| 服务订单闭环 | `frontend/src/views/OrderDetailView.vue`、`ShopDetailView.vue`、`ReviewPanel.vue` | 按 `orderType` 切换门店/服务文案，预约信息展示，服务评价写入门店 ID，评价区支持 `#shop-reviews` 定位 |
| 体验降噪 | `frontend/src/views/HomeView.vue`、`ShopListView.vue`、`PayView.vue`、`LoginView.vue` | 技术指标收进「技术说明 / 开发者信息」折叠区，首屏聚焦商品、门店与服务；错误提示改为用户可读文案 |
| 订单进度 WebSocket | `order-service/.../websocket/`、`OrderProgressPublisher.java`、`frontend/src/views/OrderDetailView.vue` | 支付/发货/完成/取消/超时取消实时推送，详情页自动刷新 |
| 心跳与重连 | `frontend/src/utils/ws.js` | 15s 发送 `PING`，忽略 `PONG`；断线按 1/2/4/8s 指数退避，上限 15s |
| 权限边界（RBAC） | `common/.../util/JwtUtil.java`、`gateway/.../filter/JwtAuthFilter.java`、shop/review `AdminTokenInterceptor`、order `AdminRoleInterceptor`、`OrderController` | `t_user.role` 写入 JWT `role` 声明；网关入口剥离伪造 `X-User-*`/`X-Admin-Token`/`X-Internal-Token` 并拦截 `..` 路径穿越；所有管理端统一仅认 `ADMIN` 角色；`/order/state/{orderNo}` 校验订单归属（防 IDOR） |
| 商家工作台 | `frontend/src/views/AdminView.vue`、`router/index.js`、`stores/auth.js`、`components/AppHeader.vue`、`views/LoginView.vue` | `/admin` 四 Tab（门店与服务 / 订单处理 / 评价回复 / 用户管理）；`requiresAdmin` 守卫 + `isAdmin` getter，普通用户访问跳回首页；统一登录按 role 自动分流（ADMIN→/admin）；订单处理默认过滤待处理（status=10）；复用现有管理端接口，无新增假接口 |
| 管理员视角切换 | `user-service/.../service/UserManageService.java`、`config/ImpersonationProperties.java`、`frontend/src/components/ImpersonationBanner.vue`、`stores/auth.js` | 仅启用普通用户可被模拟（禁管理员/自身）；15min 短效 Token 含 impersonatorId 声明、无 Refresh Token；原管理员会话存 sessionStorage 快照，退出原样恢复不重签；常驻横幅不可关闭仅能退出；开始/退出写审计日志，网关透传 `X-Impersonator-Id` |
| 商业视觉层级 | `frontend/src/views/HomeView.vue`、`components/AppHeader.vue`、`ProductCard.vue`、`views/ShopListView.vue` | 深色导览条 + 导航 3px 激活指示；首页深色主视觉内嵌真实数据条（门店/商品总数）；卡片虚线底栏 + 大号等宽价格 + 含工时/门店直供标签；热门服务之下「精选养车用品 / 附近直营门店」两个真实数据楼层，无假数据装饰 |
| 管理端真实用户名 | `user-service/.../controller/UserInternalController.java`、order/review `UserBriefClient`、`OrderAdminVO`、`ReviewAdminVO` | `/internal/user/briefs` 仅 X-Internal-Token 可达（网关不路由 /internal/**）；管理端 VO 补 username/nickname，预约手机号/联系人部分脱敏；C 端 `maskUser` 匿名展示不变；briefs 降级 503 时管理端报错而非退化匿名 |
| JMeter 压测 | `perf/shop-detail-cache-vs-db.jmx`、`perf/results/` | 100 并发对比缓存与直连 MySQL |
| AI Tool Calling ReAct | `ai-service/.../service/AgentService.java`、`ToolExecutor.java`、`llm/HttpLlmClient.java` | OpenAI 兼容流式协议，最多 3 轮工具调用；3 个真实工具经 Feign + `X-Internal-Token` 调商品/门店/订单；`query_my_orders` 无 `X-User-Id` 直接拦截 |
| AI SSE 流式 + 记忆 | `ai-service/.../controller/AiChatController.java`、`service/ChatMemoryService.java`、`frontend/src/components/AiAdvisor.vue` | `SseEmitter` + 有界线程池下发 meta/status/delta/cards/done；Redis DB7 按 `ai:chat:u:{userId}:{sessionId}` / `ai:chat:anon:{sessionId}` 隔离存储最近 10 轮、TTL 2h；前端 fetch 流式打字机 + 可点击卡片 |
| AI 降级兜底 | `ai-service/.../service/RecommendFallbackService.java`、`config/AiSentinelConfig.java`、`gateway/.../filter/JwtAuthFilter.java` | 未配 Key/超时/余额不足/限流熔断时提取关键词走真实检索，永不 500；网关 `/api/ai/` 可选鉴权，匿名放行且清除伪造身份头 |

### 压测结果（100 并发，JMeter 5.6.3）

| 场景 | QPS | 平均响应时间 | 错误率 |
| --- | --- | --- | --- |
| Redis 缓存 `/api/shop/{id}` | 18,751.7 | 4.43 ms | 0% |
| 直连 MySQL `/api/shop/db/{id}` | 8,596.7 | 9.65 ms | 0% |

缓存路径 QPS 提升约 **2.18 倍**，平均延迟下降约 **54.1%**。复现方式：

```bash
# 压测目标默认 127.0.0.1:8083（shop-service 直连，隔离网关开销），
# 只需 middleware + shop-service（启动时设置 HC_PERF_ENABLED=true）在线；可用 -Jhost/-Jport 覆盖。
# 注意：JMeter 要求 -o 报告目录为空，重跑前先清空或改用新目录名。
jmeter -n -t perf/shop-detail-cache-vs-db.jmx -l perf/results/run2.log -e -o perf/results/html-run2
```

现存产物即上述命令的调优复测结果：`perf/results/run2.log`、`perf/results/run2-summary.json`、报告 `perf/results/html-run2/index.html`。

---

## 九、测试

```bash
cd /path/to/DianShang   # 项目根目录，按实际克隆位置调整
export JAVA_HOME=<本机 JDK 17 安装目录>
mvn test   # 需 Maven 3.9+ 已加入 PATH
```

当前共 9 个模块、218 个单元测试（common 20 / gateway 9 / user 45 / product 17 / shop 20 / order 50 / pay 16 / review 28 / ai 13），覆盖状态机、下单消费者、支付回调幂等、评价资格与聚合、评价图片上传校验、门店评分聚合、Redis 缓存三防、**Redis 连接失败降级与熔断窗口旁路、evict 待补偿恢复补删、下单状态合成 QUEUED**、点赞 MQ 事件、AI 顾问的未配 Key 降级、大模型超时降级、ReAct 工具调用、上游 SSE 流解析、未登录查订单拦截，以及 RBAC 的 JWT role 声明与登录返回体 role、网关伪造头剥离与透传、shop/review 双凭证拦截器、order 管理员接单/发货类型校验、内部 briefs 凭证拦截与管理端真实用户名/脱敏边界、**视角切换的自身/禁用/管理员禁模拟校验与短效 Token 声明**等。最近一次全量 `mvn test` 于 2026-11-19 执行：9 模块 BUILD SUCCESS，0 失败 0 错误 0 跳过。

---

## 十、常见问题

1. **服务启动报 Nacos 连接失败**：确认 `hc-nacos` 已健康，8848/9848 端口未被占用。
2. **前端请求 404 / 502**：确认网关 8080 已启动且 Nacos 中目标服务在线；前端只应访问 5173。
3. **MySQL 连不上**：本项目使用 **3307**，不是本机 3306；容器名 `hc-mysql`。
4. **Redis 连不上**：使用 **6380** 且需要密码 `123456`；不要连本机 6379。
5. **Windows 打包提示 jar 被占用**：先停对应服务再 `mvn package`，然后重启。
6. **ES 搜索无结果**：确认 `hc-elasticsearch` 健康，且索引已由 `HC_SHOP_SYNC` 初始化；可调用管理端同步接口或重启 shop-service 触发初始化。
7. **WebSocket 一直显示重连**：检查网关 `/ws/pay`、`/ws/order` 路由及登录 Token 是否过期。
8. **下游服务宕机但 FallbackFactory 不生效**：Sentinel 的 Feign 适配只认顶层 `feign.sentinel.enabled=true`，写到 `spring.cloud.openfeign.sentinel.enabled` 下不会织入代理；开启后降级工厂必须返回 `Result.fail(503)`，不能直接抛异常（会被包装成 `AssertionError`）。
9. **容器 healthy 但接口 500 / 连接即断**：Docker Desktop 端口转发偶发「半死」——容器内 `redis-cli ping` 正常、宿主机 TCP 能 connect 但流立即关闭。判别法：connect 后等 1s，有协议响应为活、立即 closed 为坏；修复 `docker restart hc-redis hc-nacos hc-rocketmq-namesrv hc-rocketmq-broker`（按受影响端口）。代码侧门店缓存与下单状态已有 10s 熔断降级，Redis 短暂不可用时前台直连 DB / 合成 QUEUED，不再抛 500。
10. **机器睡眠唤醒后日志出现 Nacos gRPC ERROR / Hikari clock leap WARN**：长连接断开与时钟跳变的自愈记录（重连成功即恢复），属环境现象，无需处理。

---

## 十一、范围说明

- 支付为 Mock 支付，用于演示回调幂等与状态机，不接入真实支付渠道。
- 评价图片保存到本地磁盘 `uploads/review/`（可通过 `hc.upload.review-dir` 调整），未接入对象存储。
- 管理端为轻量 RBAC：`t_user.role`（USER/ADMIN）+ JWT role 声明 + 网关透传；前端 `/admin` 工作台复用现有管理接口，未做独立商家入驻/多商户隔离体系。管理员种子密码仅以 BCrypt 哈希入库，明文不进入仓库。
- 视角切换为演示级 Impersonation：15 分钟短效 Token + sessionStorage 快照 + 日志审计（无独立审计表），关标签即失效；未做审计查询界面与审批流。
- Redis 故障降级目前覆盖 shop-service 缓存与 order-service 下单状态两处；pay/review 的 Redis 依赖尚未加同款熔断，Redis 长时间不可用时这两处仍可能报错（演示环境可接受，生产需补齐）。
- Sentinel 规则当前在应用启动时通过代码加载，单机内存规则；生产可迁移到 Nacos 持久化规则源。
- AI 养车顾问默认不内置任何真实密钥：`AI_API_KEY` 留空时走本地真实检索降级（仍返回真实商品/门店卡片）。接入真实大模型需自行设置环境变量，真实 DeepSeek 链路需用户自备 Key 验证。
- 中间件均为单机 Docker 容器，未配置集群与高可用。
