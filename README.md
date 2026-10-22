# SSO 单点登录（CAS）

组织内部的 CAS 统一认证中心。各业务条线系统通过 CAS 完成用户认证，每个接入系统在 CAS 中分配独立的 `channelCode`，用于登录入口绑定校验、条线准入和审计。

> 逐文件代码地图、类/方法解析、请求链路与改动指南见 [代码地图与解析.md](代码地图与解析.md)。本文侧重构建、运行、接口契约与联调验收。

## 已实现并验证

- Apereo CAS 7.3.8.3 + Maven WAR overlay
- MySQL 用户认证（BCrypt 密码）
- 登录入口 `channelCode` 与 `service` 绑定校验
- 自定义 `RegisteredServiceAccessStrategy` 实时查询用户-条线授权
- 条线准入拒绝审计（写入 `t_login_audit`）
- 外置 JSON 服务注册表（`etc/cas/services/`）与文件监听热加载
- 管理台三服务：CAS 控制面 + admin-server 数据面 + admin-web 前端（条线、用户授权、接入系统、在线会话），统一 `X-Admin-Token` 鉴权
- 单点登出（SLO）：CAS 注销 TGT 后同步回调业务系统，注销业务本地会话
- 强制踢下线：管理台按用户枚举 TGT，触发 SLO 并删除会话，业务本地会话同步失效
- 两个条线示例服务（RETAIL / CREDIT）和 Node 演示客户端
- ERP 演示系统：`erp-server`（Spring Boot 3 + Druid + tk-mapper + MySQL）与 `erp-web`（Vue3 + Vite + Element Plus）接入 CAS
- 管理台三服务拆分（P1）：新增 `/cas/admin/control/**` 控制面（会话、踢下线、服务删除），仅回环地址可访问
- admin-server（P2）：Spring Boot fat jar（`127.0.0.1:8084`）承接管理数据面，并以控制令牌代理 CAS 控制面
- admin-web（P3）：独立 Vue3 + Vite + Element Plus 管理界面（5174），经同源反代调用 admin-server，令牌只存 `sessionStorage`
- 管理台收敛（P4）：移除 CAS 旧 `/cas/admin/api/**` 与内置静态管理台，CAS 只保留回环控制面；同步更新构建与配置说明
- Windows `bin/` 脚本：CAS、admin-server、admin-web 的构建、启动、停止与批量执行，含端口检查、PID 管理与日志
- 实测结果：
  - 正确 `channelCode` 返回 200；错配、缺失、未注册服务返回 403
  - alice 只能进入 RETAIL，进入 CREDIT 返回 401 并写入拒绝审计
  - bob 可从 RETAIL 单点登录进入 CREDIT
  - 停用用户的条线授权后，已有 CAS 会话再次访问也会被拒
  - 管理台新增条线与服务后无需重启即可生效；撤销授权后已有 CAS 会话返回 401；删除服务后入口立即返回 403
  - 主动 SLO 与管理员踢下线后，两个演示系统的业务本地会话均立即失效（再次访问返回 302 到登录页）
  - ERP 前端未登录自动跳 CAS；登录后按菜单渲染主页；登出与踢下线均使 ERP 服务端会话立即失效（详见第 11 节）

## 本阶段改进记录（2026-10-22）

| 改进 | 落地位置 | 说明 |
|---|---|---|
| 构建方式改为 Maven | `pom.xml`、`.mvn/wrapper/` | 移除 Gradle 模板，改为 CAS 7.3.8.3 Maven WAR overlay，Maven Wrapper 3.9.11 |
| 项目结构精简 | 项目根目录 | 删除 Docker、Helm、Puppeteer、OpenRewrite、占位测试等基础阶段不需要的内容 |
| MySQL 认证 | `application.yml`、`pom.xml` | JDBC 查询认证、BCrypt 密码、关闭 CAS 自带 accept 演示认证 |
| channelCode 入口校验 | `ChannelCodeValidationFilter` | 校验 `service` 与 `channelCode` 的注册绑定，错配/缺失/未注册服务返回 403 |
| 条线准入 | `ChannelAccessStrategy` | 实时查询 `t_user_channel`，校验授权状态、有效期和条线状态 |
| 拒绝审计 | `ChannelAuthorizationService` | 拒绝时写入 `t_login_audit` |
| 示例与联调 | `etc/cas/services/`、`demo/cas-client.mjs` | RETAIL/CREDIT 两个条线与无依赖 Node 客户端 |
| 文档 | `README.md` | 补充构建、运行、接入、验证用例、完整测试过程与 SQL DDL |

## 管理台阶段改进记录（2026-10-22）

> 下表记录管理台演进过程；其中内置单页与 `/admin/api` 已在 P4 移除，现行形态见“管理台三服务拆分”。

| 改进 | 落地位置 | 说明 |
|---|---|---|
| 服务注册表外置 | `etc/cas/services/`、`application.yml` | `sso.service-dir` 指向外置目录，CAS JSON 注册表开启 watcher，新增/修改文件无需重启 |
| 管理 REST API | `com.example.sso.admin` | 条线 upsert、用户查询、授权授予/撤销、服务定义增删 |
| 管理令牌鉴权 | `AdminTokenFilter` | 校验 `X-Admin-Token`；令牌为空时接口返回 503，令牌错误或缺失返回 401 |
| 管理路径安全豁免 | `CasWebSecurityConfigurer` | `/admin` 前缀不参与 CAS 自带安全链；CAS 会自动追加 `/**`，因此只能返回路径前缀 |
| 管理单页 UI | `src/main/resources/static/admin/index.html` | 无前端依赖，令牌存 `sessionStorage`，含条线、用户授权、接入系统三个页签 |
| 运行时删除 | `ServiceDefinitionService` | 文件监听器不处理删除事件，删除时必须同时调用 `ServicesManager.delete(id)` |
| 中文初始化 | `README.md` | MySQL 客户端导入需加 `--default-character-set=utf8mb4`，否则中文条线名会被写成乱码 |
| SLO 同步回调 | `application.yml`、`etc/cas/services/` | `cas.slo.asynchronous=false`，服务声明 `logoutType=BACK_CHANNEL`，CAS 等待业务注销完成 |
| 业务本地会话 | `demo/cas-client.mjs` | 改为服务端 session store，新增 SLO 回调端点，按 `SessionIndex` 注销本地会话 |
| 强制踢下线 | `AdminSessionController` | 按用户名枚举 TGT，触发 `LogoutManager` 发送 SLO，再删除 TGT 并写审计 |
| 管理台会话面板 | `static/admin/index.html` | 展示在线 TGT 和已接入服务，提供“强制踢下线”按钮 |

## ERP 演示阶段改进记录（2026-10-22）

| 改进 | 落地位置 | 说明 |
|---|---|---|
| ERP 后端 | `erp-server/` | Spring Boot 3.3 + Druid + tk-mapper + MySQL，登录登出、用户查询、菜单查询接口；channelCode 固定 ERP |
| ERP 前端 | `erp-web/` | Vue3 声明式（`<script setup>`）+ Vite + Element Plus + Pinia；默认走 CAS 登录页，回调后进主页按菜单渲染 |
| CAS 服务注册 | `etc/cas/services/service-3001.json` | `serviceId=^http://localhost:8081/erp/.*$`，BACK_CHANNEL，`logoutUrl=/erp/logout/slo` |
| 菜单条线隔离 | `erp_menu.channel_code` | 公共菜单（NULL）+ 本条线专属（ERP），树形下发 |
| 登出回跳 | `application.yml`、`AuthController` | 回跳地址用已注册的 `/erp/`，再由后端 302 回前端；CAS 开启 `cas.logout.follow-service-redirects` |
| 深链接保留 | `erp-web/src/views/LoginView.vue` | 目标路径暂存 sessionStorage，CAS 回调后还原 |
| 联调记录 | `README.md` | 第 11 节：链路、失败修复表、完整 DDL/DML |

## 技术栈

| 组件 | 版本 |
|---|---|
| Apereo CAS | 7.3.8.3 |
| Java | 21 |
| Maven | 3.9+（项目自带 Wrapper 3.9.11） |
| Spring Boot | 3.5.6（CAS 内置） |
| MySQL | 8.4 |
| Node.js | 用于演示客户端，无 npm 依赖 |

## 目录结构

```text
.
├── pom.xml
├── mvnw / mvnw.cmd
├── .mvn/wrapper/                               # Maven Wrapper
├── db/
│   ├── schema.sql                             # 用户、条线、授权、审计表
│   └── seed.sql                               # 演示用户与授权
├── demo/
│   └── cas-client.mjs                         # 两个条线的演示客户端
├── erp-server/                                 # ERP 后端演示（Spring Boot + Druid + tk-mapper + MySQL）
├── erp-web/                                    # ERP 前端演示（Vue3 + Vite + Element Plus）
├── admin-server/                               # 管理台数据面（Spring Boot fat jar，127.0.0.1:8084）
├── sso-admin-web/                              # 管理台前端（Vue3 + Vite + Element Plus，5174，/api 反代 8084）
├── etc/cas/services/                           # 外置 CAS 服务注册表（可热加载）
│   ├── service-1001.json                      # RETAIL
│   └── service-1002.json                      # CREDIT
├── src/main/java/
│   ├── com/example/sso/cas/
│   │   ├── ChannelCodeValidationFilter.java   # 登录入口 channelCode 校验
│   │   ├── ChannelAccessStrategy.java         # 条线准入策略
│   │   └── ChannelAuthorizationService.java   # 授权查询与拒绝审计
│   ├── com/example/sso/admin/
│   │   ├── AdminServiceController.java        # 控制面：删除服务定义并卸载运行时服务
│   │   ├── AdminSessionController.java        # 控制面：在线会话与强制踢下线
│   │   ├── ServiceDefinitionService.java      # 服务 JSON 查找与运行时删除
│   │   ├── AdminTokenFilter.java              # 控制面令牌与回环校验
│   │   └── AdminApiExceptionHandler.java      # 统一错误响应
│   └── org/apereo/cas/config/
│       └── CasOverlayOverrideConfiguration.java
├── src/main/resources/
│   ├── application.yml
│   └── META-INF/spring/                       # Spring Boot 自动配置注册
├── bin/
│   ├── README.md                              # bin 脚本、日志、排错与实测记录
│   ├── env.example.cmd                        # 环境变量模板（复制为 env.cmd）
│   ├── build-*.cmd / start-*.cmd / stop-*.cmd # 三个服务的构建、启动、停止
│   └── build-all.cmd / start-all.cmd / stop-all.cmd
├── 代码地图与解析.md                           # 逐文件职责、链路解析与改动指南
├── LICENSE.txt
└── README.md
```

## 代码地图与解析

逐文件职责、关键类与方法、认证/准入/SLO/管理台链路、数据模型与扩展指南统一维护在 [代码地图与解析.md](代码地图与解析.md)；`bin/` 脚本的使用、日志与排错见 [bin/README.md](bin/README.md)。本文后续章节只保留运行方式、接口契约与联调记录，避免与代码地图重复。

## 数据库初始化

确保 MySQL 8.4 已启动，然后执行：

```bash
mysql --default-character-set=utf8mb4 -uroot -p < db/schema.sql
mysql --default-character-set=utf8mb4 -uroot -p < db/seed.sql
```

Windows 下必须带 `--default-character-set=utf8mb4`，否则客户端可能按本地代码页解释脚本，导致中文条线名写成乱码。若已写入乱码，可执行：

```sql
UPDATE cas_sso.t_channel SET channel_name = '零售条线' WHERE channel_code = 'RETAIL';
UPDATE cas_sso.t_channel SET channel_name = '信贷条线' WHERE channel_code = 'CREDIT';
```

演示账号（密码均为 `Passw0rd!`）：

| 用户 | 条线授权 |
|---|---|
| alice | RETAIL |
| bob | RETAIL、CREDIT |

生产环境请修改演示密码、删除演示数据，并为 CAS 创建最小权限数据库账号。

## 脚本化构建与运行（推荐）

Windows 下建议使用 `bin/` 脚本；环境变量集中在本地 `bin/env.cmd`（由模板复制，已加入 `.gitignore`）：

```bat
copy bin\env.example.cmd bin\env.cmd
rem 编辑 bin\env.cmd，填写 JAVA_HOME、数据库密码与三个令牌

bin\build-all.cmd     rem 构建 CAS、admin-server、admin-web
bin\start-all.cmd     rem 启动并等待三个服务就绪
bin\stop-all.cmd      rem 反向停止三个服务
```

| 脚本 | 作用 |
|---|---|
| `bin\build-cas.cmd` / `build-admin-server.cmd` / `build-admin-web.cmd` | 单服务构建；构建前会停止对应运行实例 |
| `bin\start-cas.cmd` / `start-admin-server.cmd` / `start-admin-web.cmd` | 单服务后台启动，PID 写入 `.run\`，日志写入 `logs\` |
| `bin\stop-cas.cmd` / `stop-admin-server.cmd` / `stop-admin-web.cmd` | 按 PID 文件与命令行标记停止，拒绝误杀 |
| `bin\build-all.cmd` / `start-all.cmd` / `stop-all.cmd` | 批量构建、启动、停止 |

脚本会检查 JDK 21、端口占用、构建产物与必需环境变量；由脚本启动的服务可用 `bin\stop-*.cmd` 停止。完整说明、日志位置与排错表见 [bin/README.md](bin/README.md)。

## 构建

JDK 21 必须生效。如果本机默认 `java -version` 不是 21，先设置 `JAVA_HOME`：

```bash
# Git Bash
export JAVA_HOME=/c/path/to/jdk-21

# PowerShell
$env:JAVA_HOME = "C:\path\to\jdk-21"
```

构建：

```bash
./mvnw clean package -DskipTests
```

Windows：

```bat
mvnw.cmd clean package -DskipTests
```

产物：`target/cas.war`。项目默认使用阿里云 Maven 镜像；如需改用官方源，删除 `pom.xml` 中的 `aliyun-public` 仓库，并修改 `.mvn/wrapper/maven-wrapper.properties` 的 `distributionUrl`。

> Windows 上重新打包前必须先停止正在运行的 CAS，否则 `spring-boot:repackage` 无法重命名被占用的 `target/cas.war`。

## 运行

> Windows 推荐直接执行 `bin\start-all.cmd`（先按上一节准备 `bin\env.cmd`）。以下为手工启动方式。

CAS 通过环境变量读取数据库连接：

```bash
# Git Bash
export CAS_DB_URL='jdbc:mysql://localhost:3306/cas_sso?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8'
export CAS_DB_USER='root'
export CAS_DB_PASSWORD='你的数据库密码'
export CAS_ADMIN_TOKEN='请替换为足够长的随机令牌'
java -jar target/cas.war
```

```powershell
# PowerShell
$env:CAS_DB_URL = 'jdbc:mysql://localhost:3306/cas_sso?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8'
$env:CAS_DB_USER = 'root'
$env:CAS_DB_PASSWORD = '你的数据库密码'
$env:CAS_ADMIN_TOKEN = '请替换为足够长的随机令牌'
java -jar target/cas.war
```

启动后访问：

- 登录页：`http://localhost:8080/cas/login`
- 管理台：先启动 admin-server，再启动 `sso-admin-web`，打开 `http://localhost:5174/gate`

- `CAS_ACCEPT_AUTH` 默认 `false`。临时排查时可设为 `true` 使用 CAS 自带演示认证，联调结束后务必关闭。
- `CAS_DB_PASSWORD` 没有默认值；数据库账号有密码时必须设置。
- `CAS_ADMIN_TOKEN` 没有默认值；未设置时控制面返回 503。
- 控制面与 admin-server 都只接受请求头 `X-Admin-Token`；生产环境必须走 HTTPS，并在反向代理层限制来源。
- 也可以把 `target/cas.war` 部署到外部 Tomcat 10.1。

## 演示客户端

开两个终端分别启动两个条线：

```bash
# 终端 1：零售条线
PORT=9001 CHANNEL_CODE=RETAIL node demo/cas-client.mjs

# 终端 2：信贷条线
PORT=9002 CHANNEL_CODE=CREDIT node demo/cas-client.mjs
```

PowerShell：

```powershell
$env:PORT=9001; $env:CHANNEL_CODE='RETAIL'; node demo/cas-client.mjs
$env:PORT=9002; $env:CHANNEL_CODE='CREDIT'; node demo/cas-client.mjs
```

访问 `http://localhost:9001/` 或 `http://localhost:9002/`：

1. 未登录时跳转 CAS，并携带 `service` 与 `channelCode`。
2. 登录成功后回调，客户端调用 CAS `/p3/serviceValidate` 校验票据。
3. 校验成功后创建服务端 session，浏览器只保存随机会话 ID；页面显示当前用户和本地会话 ID。
4. 页面提供“另一个条线”的链接，用于验证 SSO。
5. `/logout` 会销毁当前本地 session，再跳转 CAS 全局登出。
6. `/slo` 和 `POST /logout` 是 CAS 后端 SLO 回调入口，按 `SessionIndex` 注销对应本地 session。
7. `/bad-channel` 会故意携带错误的 `channelCode`，用于验证入口拦截。

## 验证用例

```bash
# 正确 channelCode
curl -o /dev/null -w '%{http_code}\n' \
  'http://localhost:8080/cas/login?service=http%3A%2F%2Flocalhost%3A9001%2Fcallback&channelCode=RETAIL'
# 200

# channelCode 错配
curl -o /dev/null -w '%{http_code}\n' \
  'http://localhost:8080/cas/login?service=http%3A%2F%2Flocalhost%3A9001%2Fcallback&channelCode=CREDIT'
# 403
```

## 管理台

管理台由独立前端 `sso-admin-web` 提供，CAS 不再内置静态页面：

- 开发态：启动 admin-server 后执行 `cd sso-admin-web && npm run dev`，打开 `http://localhost:5174/gate`；
- 发布态：`npm run build` 的 `dist/` 交给 Nginx 等静态服务，`/api` 反代到 `127.0.0.1:8084`。

打开后输入 `ADMIN_UI_TOKEN`，令牌只保存在当前标签页的 `sessionStorage`，不会写入服务端。

| 页签 | 能力 |
|---|---|
| 条线 | 新增或更新 `t_channel`；停用后该条线下所有授权立即失效 |
| 用户授权 | 按用户名/姓名查询；授予或重新启用授权；设置生效/失效时间；撤销授权；查看在线 CAS 会话并强制踢下线 |
| 接入系统 | 新增服务定义 JSON、查看当前服务、删除服务（立即从运行时注册表移除） |

接口分两层：

| 层 | 路径 | 调用方与鉴权 |
|---|---|---|
| 数据面 | `http://127.0.0.1:8084/api/**` | 管理前端；`X-Admin-Token` = `ADMIN_UI_TOKEN` |
| 控制面 | `/cas/admin/control/**` | 仅 admin-server 本机调用；回环地址 + `X-Admin-Token` = `CAS_ADMIN_TOKEN` |

数据面清单见“admin-server 接口（P2）”，控制面清单见“控制面接口（P1）”。旧 `/cas/admin/api/**` 与 `/cas/admin/index.html` 已在 P4 移除，直接访问返回 404。

调用示例：

```bash
TOKEN='请替换为 ADMIN_UI_TOKEN'

curl -s -H "X-Admin-Token: $TOKEN" \
  http://127.0.0.1:8084/api/channels

curl -s -H "X-Admin-Token: $TOKEN" \
  http://127.0.0.1:8084/api/services
```

新增服务时 `serviceId` 必须满足：

- 以 `^` 开头、`$` 结尾；
- 形如 `^http://host:port/路径$` 或 `^https://host/路径$`；
- 不接受 `.*` 这类全匹配（路径中的 `/.*` 可以，但必须锚定主机和端口）。
- `id` 不能与现有服务重复，`channelCode` 必须已在 `t_channel` 中存在。

服务 JSON 写入 `etc/cas/services/service-<id>.json` 后由 CAS watcher 自动加载。**删除文件不会被 watcher 处理**，因此管理台删除时会同时调用 `ServicesManager.delete(id)`；手工直接删除文件仍需重启 CAS 才能卸载。

## 管理台三服务拆分（P1–P4 已落地）

管理台原为 CAS 内置的单页（`/cas/admin/index.html`）+ 内置 REST 接口。为支持"管理功能独立发布、CAS 保持低频迭代"，规划为同机三服务：

| 服务 | 形态 | 端口 | 职责 | 状态 |
|---|---|---|---|---|
| CAS | 官方可执行 war（`java -jar`，内嵌 Tomcat） | 8080 | 认证、准入、控制面 `/cas/admin/control/**`（仅回环） | P1 已完成；旧数据面已于 P4 移除 |
| admin-server | Spring Boot fat jar | `127.0.0.1:8084` | 数据面：条线、用户、授权、服务定义；代理控制面 | P2 已完成 |
| admin-web | Vue3 + Vite + Element Plus | 5174 | 管理界面，同源反代调用 admin-server | P3 已完成 |

拆分边界（按能力，已核实依赖）：

> 代码级实现与逐文件说明见 [代码地图与解析.md](代码地图与解析.md)。

- **可移出**：条线/用户/授权 CRUD（纯 MySQL）；服务定义的新增与修改（写 JSON 文件，watcher 热加载）。
- **必须留在 CAS 进程内**：在线会话查询与踢下线（依赖 `TicketRegistry` + `LogoutManager`）；服务定义删除后的运行时卸载（依赖 `ServicesManager.delete`，文件 watcher 不处理删除）。
- 因此 admin-server 是"数据面 + 聚合代理"，控制面通过 `127.0.0.1` 调用 CAS，不对外暴露。

### 控制面接口（P1）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/cas/admin/control/users/{username}/sessions` | 在线 TGT 与已接入服务 |
| POST | `/cas/admin/control/users/{username}/kickout` | SLO + 删除全部 TGT |
| DELETE | `/cas/admin/control/services/{id}` | 删除服务定义文件并调用 `ServicesManager.delete(id)` |

安全约束：

- `/admin/control/**` 仅允许回环地址（`127.0.0.1`、`::1`、IPv4-mapped 回环），非回环一律 **403**；
- 仍需 `X-Admin-Token`：未配置令牌 **503**，缺失/错误 **401**；
- 旧 `/cas/admin/api/**` 与内置静态管理台已在 P4 移除，访问返回 404；管理操作全部改走 admin-server / admin-web。

> 同机部署下，admin-server 直连 `127.0.0.1:8080` 即可；若将来 CAS 前置反向代理，控制面调用应继续直连本机，避免 RemoteAddr 变成代理地址。

### admin-server 接口（P2）

服务仅绑定 `127.0.0.1:8084`，所有接口需 `X-Admin-Token`（UI 令牌，未配置 `ADMIN_UI_TOKEN` 时返回 503，令牌错误/缺失返回 401）。响应统一为 `{"code":0,"message":"ok","data":...}`。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET / POST | `/api/channels` | 条线列表 / 新增或更新（数据库 `t_channel`） |
| GET | `/api/users?q=` | 用户查询（最多 200 条） |
| GET | `/api/users/{username}/grants` | 授权列表（含条线状态、有效期） |
| POST | `/api/users/{username}/grants` | 授予或重新启用（支持 `validFrom` / `validTo`） |
| DELETE | `/api/users/{username}/grants/{channelCode}` | 撤销授权（软删除，`status=0`） |
| GET / POST | `/api/services` | 服务定义列表 / 新增（写 `etc/cas/services/*.json`，CAS watcher 热加载） |
| DELETE | `/api/services/{id}` | 代理 CAS 控制面删除（文件 + 运行时卸载） |
| GET | `/api/users/{username}/sessions` | 代理 CAS 控制面查询在线 TGT |
| POST | `/api/users/{username}/kickout` | 代理 CAS 控制面踢下线（SLO + 删除票据） |

环境变量与启动：

> Windows 可用 `bin\build-admin-server.cmd` 与 `bin\start-admin-server.cmd`，环境变量由 `bin\env.cmd` 注入。

```bash
cd admin-server
export ADMIN_DB_PASSWORD='...'          # 必填，不写入仓库
export ADMIN_UI_TOKEN='...'             # 前端访问令牌
export ADMIN_CONTROL_TOKEN='...'        # 与 CAS 的 CAS_ADMIN_TOKEN 相同
export ADMIN_SERVICE_DIR='<仓库根>/etc/cas/services'   # 建议绝对路径
./mvnw -DskipTests package
java -jar target/admin-server-1.0.0-SNAPSHOT.jar
```

| 变量 | 默认值 |
|---|---|
| `ADMIN_DB_URL` | `jdbc:mysql://localhost:3306/cas_sso?...` |
| `ADMIN_DB_USER` / `ADMIN_DB_PASSWORD` | `root` / 空（必须注入） |
| `ADMIN_CONTROL_BASE` | `http://127.0.0.1:8080/cas/admin/control` |
| `ADMIN_CONTROL_TOKEN` | 空 |
| `ADMIN_UI_TOKEN` | 空（为空时接口 503） |
| `ADMIN_SERVICE_DIR` | `../etc/cas/services` |

### admin-web 前端（P3）

独立 Vue3 工程 `sso-admin-web/`，开发态监听 5174，`/api` 反向代理到 `127.0.0.1:8084`，浏览器与后端同源，无 CORS。

> Windows 可用 `bin\build-admin-web.cmd` 构建，`bin\start-admin-web.cmd` 以 Vite preview 托管 `dist` 并监听 5174。

```bash
cd sso-admin-web
npm install
npm run dev        # http://localhost:5174
npm run build      # 产物在 dist/
```

| 路由 | 功能 |
|---|---|
| `/gate` | 令牌入口：输入 `ADMIN_UI_TOKEN`，先调用 `/api/channels` 校验，失败不保存 |
| `/channels` | 条线列表与新增/编辑（`channelCode`、名称、状态、主题、MFA） |
| `/users` | 用户查询、授权列表、授予/重新启用、撤销；选中用户后查询在线会话并踢下线 |
| `/services` | 服务定义列表、新增（id、名称、`serviceId`、条线、`logoutUrl`、`evaluationOrder`）、删除 |

令牌与错误处理：

- 令牌存 `sessionStorage`（键 `sso_admin_token`），仅当前标签页有效，关闭标签页即失效；
- 请求自动携带 `X-Admin-Token` 与 `Cache-Control: no-store`；
- 401 清除令牌并跳回 `/gate`；顶部“切换令牌”同样清除后回入口；
- 后端未配置 `ADMIN_UI_TOKEN` 时返回 503，入口页直接展示后端错误信息。

> 生产发布：`npm run build` 后把 `dist/` 交给 Nginx 或任意静态服务托管，仍把 `/api` 反代到 `127.0.0.1:8084`；整个管理台必须放在内网或反向代理白名单之后。

## 接入新系统

1. 接入方提交系统名称、回调地址、所属条线和联系人。
2. CAS 负责人在管理台“条线”页签创建或启用条线，分配 `channelCode`。
3. 在管理台“接入系统”页签新增服务定义：填写系统名称、所属条线、严格限定的 `serviceId` 正则，以及业务系统的 SLO 注销地址 `logoutUrl`。
4. 接入方实现 `logoutUrl`：接收 CAS 后端 POST，按 `SessionIndex` 删除本地会话；详见“单点登出（SLO）”章节。
5. 在管理台“用户授权”页签为用户授予该条线；已有账号可在 `t_user` 中创建。
6. 无需重新构建或重启，CAS watcher 会自动加载新的服务定义。
7. 业务系统跳转到：

   ```text
   http://cas.example.com/cas/login?service=<回调地址>&channelCode=<条线代码>
   ```

8. 回调收到 `ticket` 后调用 `/p3/serviceValidate` 校验，建立本地会话。

也可以直接调用管理 API 完成第 2～5 步。示例服务定义见 `etc/cas/services/service-1001.json` 和 `service-1002.json`。

## 关键设计约定

- `channelCode` 由接入系统传入，但只作为声明；真正的授权依据是 CAS 服务注册表中 `service` 绑定的 `channelCode`。
- `serviceId` 必须精确锚定域名或路径，禁止使用 `.*` 这类全匹配。
- `channelCode` 是系统标识，不是密钥；不要把它当作接口鉴权凭证。
- CAS 只负责认证与条线准入，不负责业务系统内部的数据权限。
- 服务注册表是外置 JSON（`etc/cas/services/`），新增和修改由 watcher 热加载；删除必须通过管理台，以便同步清理运行时注册表。
- 管理台使用单一共享令牌，不是完整的管理员账号体系；生产环境应放在内网或反向代理白名单之后。
- TGT 是 CAS 全局登录状态的权威来源；业务本地会话必须保存为服务端可吊销对象，并通过 `logoutUrl` 接受 SLO。
- SLO 使用同步后端回调（`cas.slo.asynchronous=false`），回调完成后登出才返回；接入系统必须实现幂等的注销端点。
- 强制踢下线先遍历用户 TGT 并触发 SLO，再删除 TGT；只删 TGT 不会让业务本地会话失效。

## 已知边界

- SLO 依赖业务系统的 `logoutUrl` 可达且实现正确；回调失败时 CAS 仍会注销 TGT，但业务本地会话可能残留，当前没有重试队列。
- 无状态 JWT/纯客户端 Cookie 无法被远程吊销；业务系统必须使用服务端 session、吊销列表或短 TTL。
- 当前使用内存票据注册表，`getSessionsFor` 为全量扫描；多节点部署必须换成共享票据注册表，并为按用户踢下线维护索引。
- 本地配置关闭了 TGC 加密并允许 HTTP；生产环境必须启用 HTTPS、开启 TGC 加密/签名并配置固定密钥。
- 当前是单条线单 `channelCode` 的绑定模型；如果同一系统需要多个条线，需要扩展为多对多绑定。
- 文件 watcher 只处理新增和修改；直接删除 `etc/cas/services/*.json` 不会卸载已加载服务，必须走管理台删除或重启 CAS。
- 管理台只有共享令牌，没有操作人身份、角色和操作审计；不要直接暴露到公网。

## 后续工作

1. 管理台管理员账号、角色与操作审计。
2. SLO 失败重试队列、告警与死信处理。
3. 成功登录审计、登录失败限流与账号锁定。
4. 属性下发（用户名、姓名、部门、条线）。
5. 高可用：共享票据注册表、用户 TGT 索引、数据库连接池与 CAS 多节点。

## 单点登出（SLO）

> 代码级实现（CAS `LogoutManager`、ERP `ErpSessionRegistry` 与 SLO 解析）见 [代码地图与解析.md](代码地图与解析.md)。

### 目标

用户从任意一个接入系统登出，或直接访问 CAS `/logout` 后，所有共享同一 TGT 的接入系统都必须立即销毁自己的本地会话；不能只清除浏览器里某一个业务的 Cookie。

### 方案

CAS 的 TGT 是全局登录状态的唯一权威来源，业务系统本地会话是各自会话状态的权威来源。两者通过标准 CAS 后端 SLO 联动：

1. 用户在业务系统点击“单点登出”，业务系统销毁自己的本地 session，然后跳转 CAS `/logout`；用户也可以直接访问 CAS `/logout`。
2. CAS 注销 TGT，并由 `LogoutManager` 向该 TGT 关联的每个已注册服务发送后端 SLO 请求。
3. SLO 请求为 `POST logoutUrl`，表单参数 `logoutRequest` 中包含 SAML LogoutRequest XML，`SessionIndex` 是当初签发给该业务的 ST。
4. 业务系统按 `SessionIndex` 删除对应本地 session，返回 HTTP 200。
5. CAS 等待所有回调完成后才结束登出流程，因此业务本地会话在登出响应返回前已经失效。

关键配置：

| 配置/字段 | 值 | 作用 |
|---|---|---|
| `cas.slo.disabled` | `false` | 启用 SLO |
| `cas.slo.asynchronous` | `false` | 同步等待业务回调；避免“CAS 已登出、业务会话还在”的窗口 |
| 服务 JSON `logoutType` | `BACK_CHANNEL` | 使用服务端 POST 回调，不依赖用户浏览器 |
| 服务 JSON `logoutUrl` | 业务注销端点 | 必填；不可达时无法保证本地会话失效 |

为什么业务本地会话必须改为服务端 session：

- 如果 Cookie 里直接保存 `username`，CAS 和业务都无法远程吊销这个 Cookie；用户下次带旧 Cookie 访问仍然会被当成已登录。
- 正确做法是浏览器只保存随机 `sessionId`，服务端保存 `sessionId → {user, ST}`；SLO 按 ST 删除服务端记录，旧 Cookie 再访问时查不到 session，立即跳转登录。
- 无状态 JWT 会话必须在服务端维护吊销列表/版本号，或在每次请求时校验；否则只能依赖短 TTL，无法做到“立即失效”。

### 方案选择

- 选择后端 SLO 而不是前端 iframe/AJAX：后端回调不依赖用户浏览器是否还开着，能真正销毁服务端会话。
- 选择同步回调（`asynchronous=false`）：用少量登出延迟换取“登出返回时业务会话已失效”；异步模式只能保证最终通知。
- 没有把 Redis 作为前提：单节点下 CAS 的 TGT 注册表就是登录状态权威来源；Redis 只在多节点共享票据时才是可选项之一（JDBC、Hazelcast 同样可行）。即使上了 Redis，业务本地会话仍必须靠 SLO 或短 TTL 失效，Redis 本身不能替业务系统删 session。
- 没有采用“只删 TGT”的简化方案：那只让 CAS 不能再签发新票据，业务本地会话仍会继续有效。

### 实现

| 位置 | 内容 |
|---|---|
| `application.yml` | `cas.slo.asynchronous: false`，`cas.slo.disabled: false` |
| `etc/cas/services/service-*.json` | `logoutType: BACK_CHANNEL`，`logoutUrl` 指向业务 SLO 端点 |
| `demo/cas-client.mjs` | 服务端 `sessions` Map；`/slo` 与 `POST /logout` 解析 `logoutRequest`，按 `SessionIndex` 删除本地会话；`GET /logout` 本地登出后跳 CAS 全局登出 |
| `AdminSessionController` | 踢下线时调用 `LogoutManager.performLogout`，复用同一套 SLO 通知机制 |

业务系统接入契约：

1. 提供可被 CAS 访问的 `logoutUrl`，接受 `POST application/x-www-form-urlencoded`。
2. 读取 `logoutRequest` 参数，解析 `<samlp:SessionIndex>`；它对应签发给本系统的 ST。
3. 删除该 ST 对应的本地会话；重复请求必须幂等，仍返回 200。
4. 在 2 秒内返回，不重定向；CAS 会同步等待。
5. 业务系统必须是服务端可吊销的会话模型；纯客户端 Cookie/JWT 无法被远程销毁。
6. `logoutUrl` 应只在内网暴露，或用反向代理限制来源为 CAS 节点；SLO 请求只能注销本地已存在的会话，不能凭请求创建会话或授予权限。

### 失败语义

- 任意业务回调失败或超时，CAS 仍会继续注销 TGT，但该业务的本地会话可能残留；管理台踢下线接口会把失败写入 `errors` 并返回 `kickedOut=false`。
- 当前没有 SLO 重试队列；生产环境应为失败回调增加重试/死信队列，或用短业务 session TTL 兜底。
- SLO 是服务端到服务端调用，`logoutUrl` 必须是 CAS 节点可达的内网地址；多节点部署时每个节点都要能访问。

## 强制踢下线

> 控制面与 ERP 本地会话失效的代码路径见 [代码地图与解析.md](代码地图与解析.md)。

### 目标

管理员在管理台选中用户并执行“强制踢下线”后：

1. 该用户所有 CAS TGT 立即删除，不能再用旧 TGC 换新 ST；
2. 该用户在所有接入系统里的本地会话立即失效；
3. 操作留下审计记录。

### 与普通 SLO 的区别

普通 SLO 由用户主动发起，通常只涉及当前 TGT；强制踢下线由管理员发起，需要先按用户找到全部在线 TGT，再对每个 TGT 触发 SLO。仅删除 TGT 不会通知业务系统，所以必须调用 `LogoutManager` 而不是直接 `deleteTicket`。

### 流程

1. 管理员在管理台“用户授权”页点击“强制踢下线”；管理前端调用 admin-server `POST /api/users/{username}/kickout`，admin-server 再以控制令牌调用 CAS 控制面 `POST /admin/control/users/{username}/kickout`。
2. `TicketRegistry.getSessionsFor(username)` 返回该用户所有未过期 TGT。
3. 对每个 TGT 调用 `LogoutManager.performLogout`，同步向 TGT 关联的每个服务发送 SLO 请求。
4. 删除该 TGT；旧 TGC 再访问 CAS 时只能得到登录页。
5. 写入 `t_login_audit`：`result=KICKOUT`，`reason=ADMIN_KICKOUT sessions=N slo=M errors=E`。
6. 返回会话数、SLO 请求数和错误列表。

### 接口与管理台

```bash
TOKEN='请替换为 ADMIN_UI_TOKEN'

# 查看在线会话（TGT 与已接入服务）
curl -s -H "X-Admin-Token: $TOKEN" \
  http://127.0.0.1:8084/api/users/bob/sessions

# 强制踢下线
curl -s -X POST -H "X-Admin-Token: $TOKEN" \
  http://127.0.0.1:8084/api/users/bob/kickout
```

响应示例：

```json
{
  "userId": "bob",
  "sessions": 1,
  "logoutRequests": 2,
  "errors": [],
  "kickedOut": true
}
```

管理台“用户授权”页选中用户后，会展示该用户的在线 TGT、创建时间和已接入服务；点击“强制踢下线”并确认后执行上述接口。

### 部署与扩展

- 当前使用 CAS 默认内存票据注册表，`getSessionsFor` 需要扫描票据，适合单节点和中小规模用户。
- 多节点部署必须使用共享票据注册表（Redis、JDBC 或 Hazelcast 均可；Redis 不是唯一选择），否则一个节点只能看到自己的 TGT。
- 使用 Redis 票据注册表时，建议额外维护 `user → TGT` 索引，避免按用户踢下线时全量扫描；索引应由 TGT 创建/删除事件维护，并设置过期时间。
- 管理接口必须走 HTTPS，并限制来源；共享令牌只适合内网管理台，不是完整的 RBAC。
- SLO 回调失败不会自动重试；生产环境应增加重试队列、告警，并让业务 session 具备较短的最大生命周期。

## ERP 演示系统（erp-server + erp-web）

> 两个工程的逐文件地图与调用链见 [代码地图与解析.md](代码地图与解析.md)。

除 9001/9002 两个极简客户端外，仓库内还有一套完整的前后端演示系统，用来验证「CAS 单点登录 + 条线准入 + 完整业务系统」的整体链路：

- `erp-server/`：Spring Boot 3.3 + Druid + tk.mybatis(mapper) + MySQL，提供登录登出、用户查询、菜单查询等基础 ERP 接口；
- `erp-web/`：Vue3（`<script setup>` 声明式语法）+ Vite + Element Plus + Pinia + vue-router 的企业级界面。

条线恒为 `channelCode = ERP`；CAS 服务定义见 `etc/cas/services/service-3001.json`（`serviceId = ^http://localhost:8081/erp/.*$`，`logoutType = BACK_CHANNEL`，`logoutUrl = http://localhost:8081/erp/logout/slo`）。

### 技术栈与目录

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.3.2、Druid 1.2.28（stat/wall 过滤器）、tk.mybatis mapper-spring-boot-starter 5.0.2、MySQL Connector/J、Lombok、JDK 21 |
| 前端 | Vue 3.5、Vite 6、vue-router 4、Pinia 2、Element Plus 2.9（中文 locale）、Axios |
| 会话 | 服务端 HttpSession（cookie `ERPSESSION`，HttpOnly + SameSite=Lax，30 分钟），维护 ST → session 索引供 SLO 注销 |

```text
erp-server/
├── db/erp.sql                                  # 条线、授权、菜单表与种子数据
├── src/main/java/com/example/erp/
│   ├── config/ErpProperties.java               # erp.* 集成配置（CAS 地址、回调、前端地址）
│   ├── controller/AuthController.java          # 登录/回调/会话/登出/SLO 接收
│   ├── controller/UserController.java          # 用户查询（分页 + 关键字）
│   ├── controller/MenuController.java          # 菜单查询（按条线过滤 + 树）
│   ├── service/CasAuthService.java             # /p3/serviceValidate 票据校验（禁用外部实体）
│   ├── service/ErpSessionRegistry.java         # ST → HttpSession，按 SessionIndex 注销
│   ├── service/ErpMenuService.java             # 扁平菜单 → 树
│   ├── web/ErpSessionFilter.java               # /api/** 会话校验，失败 401 JSON
│   └── util/SloXmlParser.java                  # SAML LogoutRequest 解析（SessionIndex）
erp-web/
├── vite.config.js                              # 5173 端口，/erp 代理到 8081，避免跨域
└── src/
    ├── api/index.js                            # Axios 实例与接口封装
    ├── router/index.js                         # 路由守卫：未登录统一跳 CAS；深链接 redirect 暂存
    ├── stores/user.js                          # 会话、菜单、登出（Pinia）
    ├── layout/MainLayout.vue                   # 深色侧边菜单 + 顶栏用户区
    └── views/                                  # 登录页/首页/用户管理/菜单管理/订单示例/报表/看板
```

### 数据库初始化

```bash
mysql --default-character-set=utf8mb4 -uroot -p < erp-server/db/erp.sql
```

脚本幂等（`INSERT IGNORE` + 幂等 `UPDATE`），可重复执行；DDL 与种子数据全文见「联调测试记录 → 11.9」。

### 启动

```bash
# 1) erp-server（8081）
cd erp-server
export ERP_DB_PASSWORD='<数据库密码>'    # 不写入仓库；PowerShell: $env:ERP_DB_PASSWORD='...'
./mvnw -DskipTests package
java -jar target/erp-server-1.0.0-SNAPSHOT.jar

# 2) erp-web（5173）
cd erp-web
npm install
npm run dev
```

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `ERP_DB_URL` | `jdbc:mysql://localhost:3306/cas_sso?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8` | 可指向独立库 |
| `ERP_DB_USER` | `root` | 生产应使用最小权限账号 |
| `ERP_DB_PASSWORD` | 空（必须注入） | 不在仓库中保存 |

CAS、Druid、前端与回调地址还可通过 `erp.cas.base-url`、`erp.service-base-url`、`erp.frontend-base-url`、`erp.channel-code` 覆盖。

### 登录链路

1. 浏览器打开 `http://localhost:5173/`，路由守卫发现未登录，跳 `/erp/login/cas`；
2. erp-server 302 到 CAS 登录页（携带 `service=http://localhost:8081/erp/login/callback` 与 `channelCode=ERP`）；
3. 登录成功后 CAS 回调 `/erp/login/callback?ticket=ST-...`，erp-server 调 `/p3/serviceValidate` 校验票据；
4. 校验通过则建立服务端会话（记录 ST → session），302 到 `http://localhost:5173/login?cas=ok`；
5. 前端拉取 `/erp/api/session` 与 `/erp/api/menus`，进入主页按菜单渲染示例页面。

### 接口清单

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/erp/login/cas` | 302 到 CAS 登录页（带 service 与 channelCode） |
| GET | `/erp/login/callback` | 票据校验、建立会话、302 回前端（`?cas=ok` / `?cas=fail`） |
| GET | `/erp/api/session` | 当前会话（未登录返回 `authenticated=false`，不报 401） |
| POST | `/erp/api/logout` | 销毁本地会话，返回 `casLogoutUrl` |
| GET | `/erp/logout` | 前通道登出：销毁本地会话并 302 到 CAS 登出 |
| GET/POST | `/erp/logout/slo` | CAS/管理台后端 SLO 回调入口（解析 `logoutRequest`，按 SessionIndex 注销） |
| GET | `/erp/api/users?q=&page=&size=` | 用户查询（tk-mapper，username/displayName 模糊） |
| GET | `/erp/api/menus` | 菜单查询：按条线过滤（公共 + 本条线专属）并组树 |

`/erp/api/**` 除 `session`、`logout` 外都要求已建立会话，否则返回 `401 {"code":401,"message":"未登录或会话已失效"}`。

### 登出与 SLO 集成

- 前端登出 → `POST /erp/api/logout`（本地会话立即销毁）→ 浏览器跳 CAS `/logout?service=http://localhost:8081/erp/`；
- CAS 登出销毁 TGT，并对 `BACK_CHANNEL` 服务同步回调 `/erp/logout/slo`（SAML LogoutRequest，`SessionIndex` 即 ST），erp-server 据此销毁本地会话；
- 管理台「强制踢下线」共用同一回调，在请求周期内使 ERP 本地会话失效；
- CAS 需开启 `cas.logout.follow-service-redirects: true`（本项目 `application.yml` 已配置），登出后才会自动跳回 `service`；且回跳地址必须命中已注册服务（这里用 `http://localhost:8081/erp/`，再由后端 302 回前端），否则 CAS 会以「应用程序未被授权使用CAS」拒绝；
- 前端主动登出时本地会话已先行销毁，随后到达的 SLO 回调输出 `removed=[false]`（幂等忽略），属预期行为。

### 已知边界

- 后端会话是单节点内存实现；多节点需换 Spring Session 等共享会话存储。
- 菜单按「公共（channel_code 为空）+ 本条线专属」过滤，一条菜单只能绑定一个条线。
- 前端为演示界面：订单示例、报表与看板使用静态示例数据，仅用户/菜单页面走真实接口。
- Element Plus 与图标全量引入（主包约 1.2MB / gzip 410KB），生产可改为按需引入。
- 登出后若浏览器回退到历史页面，页面缓存可能仍可见；受保护数据始终以接口 401 为准。

## 联调测试记录（2026-10-22）

> 测试数据库：`cas_sso`（MySQL 8.4.11，端口 3306）。数据库密码通过环境变量 `CAS_DB_PASSWORD` 注入，**不写入本文件**。
> 首轮在隔离实例（3307）验证，随后在 3306 正式实例复测；以下记录以 3306 复测结果为准。
> 第 1～14 节按时间顺序记录各阶段结果，其中出现的 `/cas/admin/api/**` 与内置管理台是 P4 之前的历史路径，现已被移除；现行入口见“管理台”与“管理台三服务拆分”。

### 测试环境

| 项 | 值 |
|---|---|
| CAS | 7.3.8.3 |
| JDK | Dragonwell 21.0.12 |
| Maven | Wrapper 3.9.11 |
| MySQL | 8.4.11 |
| Node.js | 24.18.0 |
| 操作系统 | Windows 11 |
| CAS 地址 | `http://localhost:8080/cas` |
| 演示客户端 | 9001（RETAIL）、9002（CREDIT） |
| ERP 后端 | erp-server，8081（`/erp`） |
| ERP 前端 | erp-web（Vite dev server），5173 |

### 1. 初始化数据库

```bash
mysql --default-character-set=utf8mb4 -h127.0.0.1 -P3306 -uroot -p < db/schema.sql
mysql --default-character-set=utf8mb4 -h127.0.0.1 -P3306 -uroot -p < db/seed.sql
```

必须带 `--default-character-set=utf8mb4`；否则 Windows 客户端可能把中文条线名写成乱码（详见 9.5 节）。

验证语句：

```sql
USE cas_sso;
SHOW TABLES;
SELECT username, enabled FROM t_user;
SELECT user_id, channel_code, status FROM t_user_channel;
```

实际结果：4 张表创建成功（`t_user`、`t_channel`、`t_user_channel`、`t_login_audit`）；alice 仅授权 RETAIL，bob 授权 RETAIL 和 CREDIT。

### 2. 构建与启动

```bash
export JAVA_HOME=/c/path/to/jdk-21
./mvnw clean package -DskipTests

export CAS_DB_URL='jdbc:mysql://127.0.0.1:3306/cas_sso?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8'
export CAS_DB_USER='root'
export CAS_DB_PASSWORD='<本地数据库密码>'
java -jar target/cas.war
```

启动日志实际输出：

```text
Started CasWebApplication in 11.465 seconds
Loaded [2] service(s) from cache [JsonServiceRegistry].
```

启动两个演示客户端：

```bash
PORT=9001 CHANNEL_CODE=RETAIL node demo/cas-client.mjs
PORT=9002 CHANNEL_CODE=CREDIT node demo/cas-client.mjs
```

### 3. 登录入口校验

```bash
BASE='http://localhost:8080/cas/login'
S1='http%3A%2F%2Flocalhost%3A9001%2Fcallback'
S2='http%3A%2F%2Flocalhost%3A9002%2Fcallback'

curl -s -o /dev/null -w '%{http_code}\n' "$BASE?service=$S1&channelCode=RETAIL"   # 200
curl -s -o /dev/null -w '%{http_code}\n' "$BASE?service=$S1&channelCode=CREDIT"   # 403
curl -s -o /dev/null -w '%{http_code}\n' "$BASE?service=$S1"                     # 403
curl -s -o /dev/null -w '%{http_code}\n' "$BASE?service=$S2&channelCode=CREDIT"   # 200
curl -s -o /dev/null -w '%{http_code}\n' "$BASE?service=http%3A%2F%2Flocalhost%3A9999%2Fcallback&channelCode=RETAIL" # 403
```

实际结果与预期一致：正确绑定 200；错配、缺失、未注册服务均为 403。

### 4. 登录、准入与 SSO

使用 alice / `Passw0rd!` 登录 RETAIL：CAS 返回 302，`Location` 中带 `ST-...`；回调后 9001 页面显示 `alice`，HTTP 200。

用 alice 的 CAS 会话访问 9002：9002 重定向到 `channelCode=CREDIT` 的 CAS 登录地址，该请求返回 **401**，准入拒绝。

使用 bob / `Passw0rd!` 登录 RETAIL，再访问 CREDIT：RETAIL 回调 200 且页面显示 `bob`；通过 CAS SSO 进入 CREDIT，回调 200 且页面显示 `bob`，未再次输入密码。

登录请求示例（`execution` 从登录页隐藏域提取）：

```bash
curl -s -c cookies.txt "$BASE?service=$S1&channelCode=RETAIL" -o login.html
EXEC=$(grep -oE 'name="execution" value="[^"]+"' login.html | sed -E 's/.*value="([^"]+)"/\1/' | head -1)
curl -s -D headers.txt -c cookies.txt -b cookies.txt -X POST "http://localhost:8080/cas/login" \
  --data-urlencode 'username=alice' \
  --data-urlencode 'password=Passw0rd!' \
  --data-urlencode "execution=$EXEC" \
  --data-urlencode '_eventId=submit' \
  --data-urlencode 'geolocation=' \
  --data-urlencode 'deviceFingerprint='
```

### 5. 实时撤权

```sql
UPDATE t_user_channel
SET status = 0
WHERE user_id = 'alice' AND channel_code = 'RETAIL';
```

用 alice 已有 TGT 再次访问 RETAIL，返回 **401**；随后恢复：

```sql
UPDATE t_user_channel
SET status = 1
WHERE user_id = 'alice' AND channel_code = 'RETAIL';
```

恢复后同一 TGT 再次访问返回 302 并带新 ST，说明准入是实时查询数据库的。

### 6. 审计验证

```sql
SELECT user_id, channel_code, result, reason, service_url
FROM t_login_audit
ORDER BY id DESC;
```

实际记录：

| user_id | channel_code | result | reason | service_url |
|---|---|---|---|---|
| alice | RETAIL | DENY | CHANNEL_NOT_AUTHORIZED | http://localhost:9001/callback |
| alice | CREDIT | DENY | CHANNEL_NOT_AUTHORIZED | http://localhost:9002/callback |

### 7. 完整 SQL DDL

以下为本次测试实际执行的 DDL，与 `db/schema.sql` 一致：

```sql
CREATE DATABASE IF NOT EXISTS cas_sso
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE cas_sso;

CREATE TABLE IF NOT EXISTS t_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(128) NULL,
    dept_id       VARCHAR(64)  NULL,
    enabled       TINYINT      NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS t_channel (
    channel_code VARCHAR(32)  NOT NULL,
    channel_name VARCHAR(128) NOT NULL,
    status       TINYINT      NOT NULL DEFAULT 1,
    theme        VARCHAR(64)  NULL,
    mfa_required TINYINT      NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (channel_code)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS t_user_channel (
    user_id      VARCHAR(64) NOT NULL,
    channel_code VARCHAR(32) NOT NULL,
    source       VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    valid_from   DATETIME    NULL,
    valid_to     DATETIME    NULL,
    status       TINYINT     NOT NULL DEFAULT 1,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, channel_code),
    KEY idx_user_channel_user_status (user_id, status),
    KEY idx_user_channel_channel_status (channel_code, status),
    CONSTRAINT fk_user_channel_user
        FOREIGN KEY (user_id) REFERENCES t_user (username)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_user_channel_channel
        FOREIGN KEY (channel_code) REFERENCES t_channel (channel_code)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS t_login_audit (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      VARCHAR(64)  NULL,
    channel_code VARCHAR(32)  NULL,
    service_url  VARCHAR(512) NULL,
    result       VARCHAR(16)  NOT NULL,
    reason       VARCHAR(256) NULL,
    ip           VARCHAR(64)  NULL,
    user_agent   VARCHAR(512) NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_login_audit_user_time (user_id, created_at),
    KEY idx_login_audit_channel_time (channel_code, created_at)
) ENGINE = InnoDB;
```

### 8. 测试数据（DML）

以下为本次测试实际执行的初始化数据，与 `db/seed.sql` 一致：

```sql
USE cas_sso;

INSERT IGNORE INTO t_channel (channel_code, channel_name, status)
VALUES ('RETAIL', '零售条线', 1),
       ('CREDIT', '信贷条线', 1);

INSERT IGNORE INTO t_user (username, password_hash, display_name, dept_id, enabled)
VALUES ('alice', '$2a$10$q2urDFIXedECAz3hgGdGbuV647K3J6fqgxQU9Kp11ucM5wSUu741K', 'Alice', 'D001', 1),
       ('bob',   '$2a$10$kO9JHjUMetT.a8uqfPN5FuXtefr0oJ6qS.mXwsVWwD39UO3/35g2S', 'Bob',   'D002', 1);

INSERT IGNORE INTO t_user_channel (user_id, channel_code, source, status)
VALUES ('alice', 'RETAIL', 'MANUAL', 1),
       ('bob',   'RETAIL', 'MANUAL', 1),
       ('bob',   'CREDIT', 'MANUAL', 1);
```

### 9. 管理台联调（2026-10-22）

本节记录外置服务注册表与管理台的完整验证过程。管理台测试会写入真实数据库和 `etc/cas/services/`，测试结束后已清理测试条线、授权与服务文件；拒绝审计保留。

测试环境补充：

| 项 | 值 |
|---|---|
| 管理令牌 | 通过 `CAS_ADMIN_TOKEN` 注入，本文不记录实际值 |
| 服务注册目录 | `./etc/cas/services` |
| 管理台地址 | `http://localhost:8080/cas/admin/index.html` |
| 测试条线 | `TEST`（测试后删除） |
| 测试服务 | id `2001`（测试后删除） |

#### 9.1 启动与令牌鉴权

```bash
export CAS_ADMIN_TOKEN='<测试令牌>'
java -jar target/cas.war
```

启动日志：

```text
Started CasWebApplication in 11.382 seconds
Loaded [2] service(s) from cache [JsonServiceRegistry].
```

起始目录只有 1001、1002 两个示例服务；测试服务 2001 在 9.2 节通过管理台创建。

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  http://localhost:8080/cas/admin/api/channels
# 401（缺少令牌）

curl -s -o /dev/null -w '%{http_code}\n' -H 'X-Admin-Token: wrong' \
  http://localhost:8080/cas/admin/api/channels
# 401（令牌错误）

curl -s -o /dev/null -w '%{http_code}\n' -H "X-Admin-Token: $CAS_ADMIN_TOKEN" \
  http://localhost:8080/cas/admin/api/channels
# 200

curl -s -o /dev/null -w '%{http_code}\n' \
  http://localhost:8080/cas/admin/index.html
# 200
```

带令牌的条线接口返回 `CREDIT`、`RETAIL`、`TEST` 三条记录；无令牌和错误令牌均返回 401，说明 `/admin/**` 已从 CAS 安全链豁免，但管理接口仍受令牌过滤器保护。

#### 9.2 创建测试条线、授权与服务

```bash
TOKEN='<测试令牌>'

curl -s -H "X-Admin-Token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"channelCode":"TEST","channelName":"Test Channel","status":1,"mfaRequired":0}' \
  http://localhost:8080/cas/admin/api/channels
# {"channelCode":"TEST","updated":true}

curl -s -H "X-Admin-Token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"channelCode":"TEST"}' \
  http://localhost:8080/cas/admin/api/users/alice/grants
# {"granted":true,"channelCode":"TEST","userId":"alice"}

curl -s -H "X-Admin-Token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"id":2001,"name":"test-demo","serviceId":"^http://localhost:9003/.*$","channelCode":"TEST","description":"admin console test","logoutUrl":"http://localhost:9003/logout","evaluationOrder":10}' \
  http://localhost:8080/cas/admin/api/services
# {"id":2001,"name":"test-demo","serviceId":"^http://localhost:9003/.*$","channelCode":"TEST",...}
```

服务定义实际写入 `etc/cas/services/service-2001.json`，结构与示例服务一致，`properties.channelCode` 为 `TEST`。

非法 `serviceId` 验证：

```bash
curl -s -H "X-Admin-Token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"id":2002,"name":"bad","serviceId":".*","channelCode":"TEST"}' \
  http://localhost:8080/cas/admin/api/services
# 400 {"error":"serviceId 必须形如 ^http://host:port/路径$，且禁止全匹配"}
```

新增后服务注册表从 2 个增加到 3 个，登录入口立即接受：

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  'http://localhost:8080/cas/login?service=http%3A%2F%2Flocalhost%3A9003%2Fcallback&channelCode=TEST'
# 200
```

#### 9.3 新接入系统登录与热删除

启动测试客户端：

```bash
PORT=9003 CHANNEL_CODE=TEST node demo/cas-client.mjs
```

使用 alice / `Passw0rd!` 完成登录：CAS 返回 302，`Location` 为 `http://localhost:9003/callback?ticket=ST-...`；回调后页面返回 200，页面中同时出现 `alice`、`TEST`。说明仅通过管理台创建条线、授权和服务，就能完成一个新系统的接入，无需重新构建。

为验证 watcher 的新增能力，再创建一个临时服务 2002：

```bash
curl -s -H "X-Admin-Token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"id":2002,"name":"hot-reload-demo","serviceId":"^http://localhost:9004/.*$","channelCode":"TEST","evaluationOrder":20}' \
  http://localhost:8080/cas/admin/api/services
# 200
```

CAS 日志随后输出 `Loaded [4] service(s) from cache [JsonServiceRegistry].`，对 9004 的登录入口返回 200。

通过管理台删除 2002：

```bash
curl -s -X DELETE -H "X-Admin-Token: $TOKEN" \
  http://localhost:8080/cas/admin/api/services/2002
# {"deleted":true,"id":2002}
```

删除后：

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  'http://localhost:8080/cas/login?service=http%3A%2F%2Flocalhost%3A9004%2Fcallback&channelCode=TEST'
# 403
```

文件 `service-2002.json` 已不存在。实测确认：仅删除 JSON 文件时 CAS watcher 不会卸载服务，必须由管理台调用 `ServicesManager.delete(id)` 才会立即生效。

#### 9.4 撤销授权与实时拦截

```bash
curl -s -X DELETE -H "X-Admin-Token: $TOKEN" \
  http://localhost:8080/cas/admin/api/users/alice/grants/TEST
# {"revoked":true,"userId":"alice","channelCode":"TEST"}
```

使用 alice 已有 TGC 再次请求 TEST 条线登录地址：

```bash
curl -s -b cookies.txt -o /dev/null -w '%{http_code}\n' \
  'http://localhost:8080/cas/login?service=http%3A%2F%2Flocalhost%3A9003%2Fcallback&channelCode=TEST'
# 401
```

审计表中新增：

| user_id | channel_code | result | reason | created_at |
|---|---|---|---|---|
| alice | TEST | DENY | CHANNEL_NOT_AUTHORIZED | 2026-10-22 21:01:46 |

重新授权后，同一 TGC 再次访问返回 302 并签发新 ST：

```bash
curl -s -H "X-Admin-Token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"channelCode":"TEST"}' \
  http://localhost:8080/cas/admin/api/users/alice/grants
# 200，随后登录入口返回 302
```

#### 9.5 中文条线名乱码修复

测试中发现：使用 `mysql -uroot -p < db/seed.sql` 导入时，Windows 客户端按本地代码页解释脚本，`t_channel.channel_name` 被写成乱码（如 `淇¤捶鏉＄嚎`）。管理台因此显示乱码。修复方式：

```bash
mysql --default-character-set=utf8mb4 -uroot -p < db/seed.sql
```

已写入的乱码数据执行：

```sql
UPDATE cas_sso.t_channel SET channel_name = '零售条线' WHERE channel_code = 'RETAIL';
UPDATE cas_sso.t_channel SET channel_name = '信贷条线' WHERE channel_code = 'CREDIT';
```

修复后管理台与接口均显示正确的“零售条线 / 信贷条线”。

#### 9.6 前端页面验证

在浏览器打开 `http://localhost:8080/cas/admin/index.html`：

1. 未填写令牌时页面提示“请先填写并保存 X-Admin-Token”。
2. 输入令牌并点击“保存令牌”后，条线表格加载出 CREDIT、RETAIL、TEST。
3. 管理接口响应已设置 `Cache-Control: no-store`；页面顶部“刷新数据”可手动重新拉取。
3. “用户授权”页签加载 alice、bob；点击 alice 的“管理授权”后展示 RETAIL、TEST 两条授权及撤销按钮。
4. “接入系统”页签展示 1001、1002、2001 三条服务定义及删除按钮。
5. 三个页签切换、下拉框联动、错误提示均正常。

#### 9.7 清理与最终状态

```bash
curl -s -X DELETE -H "X-Admin-Token: $TOKEN" \
  http://localhost:8080/cas/admin/api/services/2001
# {"deleted":true,"id":2001}
```

清理后 9003 登录入口返回 403。数据库清理：

```sql
DELETE FROM cas_sso.t_user_channel WHERE channel_code = 'TEST';
DELETE FROM cas_sso.t_channel WHERE channel_code = 'TEST';
```

最终状态：

```text
t_channel:       CREDIT（信贷条线）、RETAIL（零售条线）
t_user_channel:  alice/RETAIL、bob/RETAIL、bob/CREDIT，status 均为 1
etc/cas/services: service-1001.json、service-1002.json
t_login_audit:   alice/TEST 的拒绝记录保留
```

#### 9.8 本阶段 SQL DDL 与 DML

本阶段没有新增或修改表结构，DDL 与第 7 节完全一致。管理台操作产生的数据变更等价于以下 DML：

```sql
-- 管理台 POST /admin/api/channels
INSERT INTO t_channel (channel_code, channel_name, status, theme, mfa_required)
VALUES ('TEST', 'Test Channel', 1, NULL, 0) AS new
ON DUPLICATE KEY UPDATE
    channel_name = new.channel_name,
    status = new.status,
    theme = new.theme,
    mfa_required = new.mfa_required;

-- 管理台 POST /admin/api/users/alice/grants
INSERT INTO t_user_channel (user_id, channel_code, source, valid_from, valid_to, status)
VALUES ('alice', 'TEST', 'MANUAL', NULL, NULL, 1) AS new
ON DUPLICATE KEY UPDATE
    status = 1,
    source = 'MANUAL',
    valid_from = new.valid_from,
    valid_to = new.valid_to;

-- 管理台 DELETE /admin/api/users/alice/grants/TEST（软撤销）
UPDATE t_user_channel SET status = 0
WHERE user_id = 'alice' AND channel_code = 'TEST';

-- 测试清理（硬删除测试条线及其授权；审计表无外键，记录保留）
DELETE FROM t_user_channel WHERE channel_code = 'TEST';
DELETE FROM t_channel WHERE channel_code = 'TEST';

-- 中文乱码修复
UPDATE t_channel SET channel_name = '零售条线' WHERE channel_code = 'RETAIL';
UPDATE t_channel SET channel_name = '信贷条线' WHERE channel_code = 'CREDIT';
```

验证查询：

```sql
SELECT channel_code, channel_name, status FROM t_channel ORDER BY channel_code;
SELECT user_id, channel_code, status FROM t_user_channel ORDER BY user_id, channel_code;
SELECT user_id, channel_code, result, reason, created_at
FROM t_login_audit ORDER BY id DESC LIMIT 5;
```

实测结果：`t_channel` 仅剩 CREDIT、RETAIL；alice 仅 RETAIL；bob 保留 RETAIL、CREDIT；`t_login_audit` 最新一条为 `alice | TEST | DENY | CHANNEL_NOT_AUTHORIZED`。

### 10. SLO 与踢下线联调（2026-10-22）

测试拓扑：CAS 8080，RETAIL 9001，CREDIT 9002；使用 bob 登录两个条线，验证同一 TGT 在两个业务系统中的本地会话。

启动配置：

```yaml
cas:
  slo:
    disabled: false
    asynchronous: false
```

两个演示服务均声明：

```json
{
  "logoutType": "BACK_CHANNEL",
  "logoutUrl": "http://localhost:9001/logout"
}
```

#### 10.1 建立两个业务本地会话

bob 登录 RETAIL 后通过 SSO 进入 CREDIT，两个页面均返回 200，CAS 中只有一个 TGT：

```text
bob 在 RETAIL 的本地会话已建立
bob 在 CREDIT 的本地会话已建立
```

管理接口返回：

```json
[{
  "ticketId": "TGT-1-CBCIcZ9CwtMbtCokkPDe4WpLKj5hMA-EGN2XwA-ouBiBTsd11JHUQyxQCAg54ISYrYg-X",
  "createdAt": "2026-10-22T13:21:59.499784900Z",
  "services": [
    "http://localhost:9001/callback",
    "http://localhost:9002/callback"
  ]
}]
```

#### 10.2 主动单点登出

从 RETAIL 页面访问 `/logout`，本地 RETAIL session 先被删除，然后跳转 CAS `/logout`：

```bash
curl -b bob-cookies.txt -L http://localhost:9001/logout \
  -o /dev/null -w '%{http_code} %{url_effective}\n'
# 200 http://localhost:8080/cas/logout?service=http%3A%2F%2Flocalhost%3A9001%2F
```

CAS 日志：

```text
Performing logout operations for [TGT-1-...]
[2] logout requests were processed
```

RETAIL 日志：

```text
SLO received: sessionIndex=ST-1-NHk5VMm9aEoSEgWolMgV9VODVzE-X nameId=bob removed=0
```

CREDIT 日志：

```text
SLO received: sessionIndex=ST-2-D2T1L6yIelM6fbdKOzkbdpMSno0-X nameId=bob removed=1
```

RETAIL 的 `removed=0` 是因为 `/logout` 已先删除本地会话；CREDIT 的 `removed=1` 证明 CAS 后端 SLO 真正注销了另一个业务的本地会话。

登出后：

```text
CAS sessions: []
RETAIL /: 302 http://localhost:8080/cas/login?...channelCode=RETAIL
CREDIT /: 302 http://localhost:8080/cas/login?...channelCode=CREDIT
```

#### 10.3 管理员强制踢下线

重新登录 bob 到两个条线，确认两个本地页面均为 200，然后调用：

```bash
curl -s -X POST -H "X-Admin-Token: $TOKEN" \
  http://localhost:8080/cas/admin/api/users/bob/kickout
```

实际响应：

```json
{
  "userId": "bob",
  "sessions": 1,
  "logoutRequests": 2,
  "errors": [],
  "kickedOut": true
}
```

踢下线后：

```text
CAS sessions: []
RETAIL /: 302 http://localhost:8080/cas/login?...channelCode=RETAIL
CREDIT /: 302 http://localhost:8080/cas/login?...channelCode=CREDIT
```

两个演示客户端均记录到 `removed=1`：

```text
RETAIL: SLO received: sessionIndex=ST-3-... nameId=bob removed=1
CREDIT: SLO received: sessionIndex=ST-4-... nameId=bob removed=1
```

用踢下线前的旧 TGC 再访问 CAS 登录入口：

```text
old-tgc-login: 200
页面包含 name="execution"
```

返回 200 登录页而不是 302 + 新 ST，说明 TGT 已被删除，无法继续签发票据。

#### 10.4 同一用户多个独立会话

用两个独立 Cookie 容器分别让 bob 登录 RETAIL 和 CREDIT，形成两个 TGT。管理接口返回两条在线会话：

```json
[
  {"ticketId":"TGT-4-...","services":["http://localhost:9001/callback","http://localhost:9002/callback"]},
  {"ticketId":"TGT-3-...","services":["http://localhost:9002/callback","http://localhost:9001/callback"]}
]
```

踢下线响应：

```json
{"userId":"bob","sessions":2,"logoutRequests":4,"errors":[],"kickedOut":true}
```

两个浏览器容器的四个本地会话全部失效：

```text
before: A 200/200  B 200/200（RETAIL/CREDIT）
after:  A 302/302  B 302/302
```

说明踢下线按用户遍历全部 TGT，而不是只处理当前浏览器的一条会话。

审计记录：

```sql
SELECT user_id, result, reason, created_at
FROM t_login_audit
WHERE user_id = 'bob' AND result = 'KICKOUT'
ORDER BY id DESC LIMIT 1;
```

```text
bob | KICKOUT | ADMIN_KICKOUT sessions=1 slo=2 errors=0 | 2026-10-22 21:21:04
```

#### 10.5 管理台页面验证

在浏览器“用户授权”页签选择 bob：

- 在线会话表格显示 TGT、创建时间、`http://localhost:9001/callback, http://localhost:9002/callback`；
- “强制踢下线”按钮由禁用变为可用；
- 点击并确认后提示：`已踢下线：1 个 CAS 会话，2 个 SLO 请求`；
- 会话表格清空，按钮重新禁用。

#### 10.6 边界用例

未提供 SLO 端点的服务拒绝创建：

```bash
curl -s -H "X-Admin-Token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"id":2099,"name":"no-slo","serviceId":"^http://localhost:9009/.*$","channelCode":"RETAIL"}' \
  http://localhost:8080/cas/admin/api/services
# 400 {"error":"logoutUrl 必填：业务系统必须提供 SLO 注销端点"}
```

对没有在线会话的用户执行踢下线：

```json
{"userId":"alice","sessions":0,"logoutRequests":0,"errors":[],"kickedOut":false}
```

接口返回 200 且不报错，同时记录一条 `sessions=0` 的踢下线审计。

#### 10.7 本阶段 SQL DDL / DML

- 无新增 DDL；继续使用第 7 节的四张表。
- 强制踢下线只新增审计数据，等价 DML：

```sql
INSERT INTO t_login_audit (user_id, result, reason)
VALUES ('bob', 'KICKOUT', 'ADMIN_KICKOUT sessions=1 slo=2 errors=0');
```

- 主动 SLO 不写业务表（由 CAS 票据注册表和业务本地 session 完成）。
- SLO/踢下线测试没有修改演示账号、条线或授权数据。

### 11. ERP 前后端联调（2026-10-22）

> 目标：erp-server（8081）+ erp-web（5173）接入 CAS 后，登录登出、用户查询、菜单查询与整体调用链全部可用，含 SLO/踢下线对 ERP 本地会话的立即失效。

#### 11.1 初始化与启动

```bash
mysql --default-character-set=utf8mb4 -uroot -p < erp-server/db/erp.sql

cd erp-server
export ERP_DB_PASSWORD='...'   # 不写入仓库
./mvnw -DskipTests package
java -jar target/erp-server-1.0.0-SNAPSHOT.jar    # Started ErpApplication in 3.5s，8081 ready

cd erp-web
npm install && npm run dev                        # VITE ready，5173
```

`etc/cas/services/service-3001.json` 随 CAS 启动自动加载；本次复测重启 CAS 后运行时注册表内共 3 个服务（RETAIL、CREDIT、ERP），登录页正确显示服务名 `erp-demo` 与描述「ERP 演示系统」。

#### 11.2 CAS 登录链路（浏览器实测）

```text
打开 http://localhost:5173/home
→ 302 /erp/login/cas
→ 302 http://localhost:8080/cas/login?service=http%3A%2F%2Flocalhost%3A8081%2Ferp%2Flogin%2Fcallback&channelCode=ERP
→ 输入 bob / Passw0rd! 提交
→ 302 /erp/login/callback?ticket=ST-1-nkg0WhNjWiUmB-T-j4CtnmskAn0-X
→ 302 http://localhost:5173/login?cas=ok
→ 路由进入 /home：侧边菜单 5 个顶级项（首页/系统管理/业务示例/报表中心/ERP 专属看板），
  顶栏显示 Bob、channelCode=ERP、CAS 单点登录标签
```

后端日志对应：`ERP login success: user [bob], ST [ST-1-...]`；`/erp/api/session` 返回：

```json
{"code":0,"message":"ok","data":{"authenticated":true,"user":{"username":"bob","displayName":"Bob","deptId":"D002","channelCode":"ERP"}}}
```

#### 11.3 免密 SSO 重入

重启 erp-server（本地会话全部丢失）后再次访问 `/home`：前端检测到未认证 → 跳 CAS → 浏览器仍持有 TGT，未再要求输入密码即重新签发 ST 并回到主页，用户仍为 bob。说明 ERP 会话生命周期与 CAS 认证生命周期解耦，SSO 语义正确。

#### 11.4 用户查询

- `/erp/api/users?page=1&size=10` → `total=2`，表格展示 alice/Alice/D001、bob/Bob/D002（启用）；
- 关键字 `q=ali` → `total=1`，仅 alice 一行。

#### 11.5 菜单查询

- `/erp/api/menus` 返回 5 顶级 / 共 8 节点树，`parentId`、`sortOrder`、`channelCode` 均正确（`erp-board` 为 `"ERP"`，其余为公共）；
- 菜单管理页表格以树形展示 8 行，图标列正常渲染；
- 「仅看当前条线专属」过滤 → 仅剩「ERP 专属看板」；
- 关键字「用户」过滤 → 保留父目录「系统管理」+ 命中项「用户管理」。

#### 11.6 示例业务页面

订单示例（5 行 + 关键字/状态下拉过滤）、报表中心（4 组条形 + 4 张指标卡）、ERP 专属看板（3 指标卡 + 3 行库存预警）均正常渲染，站内导航（侧边菜单点击、面包屑、路由标题）工作正常。

#### 11.7 登出全链

```text
点击「退出登录」确认
→ POST /erp/api/logout 销毁 ERP 本地会话，返回 casLogoutUrl
→ 跳 http://localhost:8080/cas/logout?service=http%3A%2F%2Flocalhost%3A8081%2Ferp%2F
→ CAS 销毁 TGT 并向后端 SLO 回调（DefaultLogoutManager: [1] logout requests were processed）
→ CAS 自动跳回 http://localhost:8081/erp/ → 302 http://localhost:5173/
→ 前端已未登录 → 跳 CAS 登录页（TGT 已销毁，需重新输密码）
```

erp-server 侧日志：`ERP SLO request: sessionIndex=[ST-1-...], removed=[false]`——前端登出时本地会话已先行销毁，SLO 回调幂等忽略，属预期。登出后前端再访问 `/erp/api/users` 返回 `401 {"code":401,"message":"未登录或会话已失效"}`。

#### 11.8 踢下线使 ERP 本地会话立即失效

```bash
curl -s -H "X-Admin-Token: $CAS_ADMIN_TOKEN" \
  http://localhost:8080/cas/admin/api/users/bob/sessions
# [{"ticketId":"TGT-2-...","services":["http://localhost:8081/erp/login/callback"]}]

curl -s -X POST -H "X-Admin-Token: $CAS_ADMIN_TOKEN" \
  http://localhost:8080/cas/admin/api/users/bob/kickout
# {"userId":"bob","sessions":1,"logoutRequests":1,"errors":[],"kickedOut":true}
```

erp-server 侧日志（被动失效路径）：

```text
Invalidated ERP session by SLO SessionIndex [ST-2-aW2BjsNTFQ8PQunhdzsAZi-d-Z4-X]
ERP SLO request: sessionIndex=[ST-2-...], removed=[true]
```

随后浏览器访问 `/system/users`：路由守卫请求 `/erp/api/session` 得到未认证，直接跳 CAS 登录页（TGT 同时被踢，不再免密）。即管理员操作在同一个请求周期内使 ERP 服务端会话失效，前端下一次请求即被拒绝。

#### 11.9 ERP 数据库 DDL / DML（完整）

来自 `erp-server/db/erp.sql`，对 `cas_sso` 库执行：

```sql
USE cas_sso;

-- ERP 条线
INSERT IGNORE INTO t_channel (channel_code, channel_name, status)
VALUES ('ERP', '企业资源计划', 1);
UPDATE t_channel SET channel_name = '企业资源计划', status = 1 WHERE channel_code = 'ERP';

-- 演示用户授予 ERP 条线
INSERT IGNORE INTO t_user_channel (user_id, channel_code, source, status)
VALUES ('alice', 'ERP', 'MANUAL', 1),
       ('bob', 'ERP', 'MANUAL', 1);
UPDATE t_user_channel SET status = 1
WHERE channel_code = 'ERP' AND user_id IN ('alice', 'bob');

-- ERP 菜单表
CREATE TABLE IF NOT EXISTS erp_menu (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id    BIGINT       NOT NULL DEFAULT 0,
    menu_name    VARCHAR(64)  NOT NULL,
    menu_code    VARCHAR(64)  NOT NULL,
    path         VARCHAR(128) NULL,
    icon         VARCHAR(64)  NULL,
    sort_order   INT          NOT NULL DEFAULT 0,
    channel_code VARCHAR(32)  NULL,
    status       TINYINT      NOT NULL DEFAULT 1,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_erp_menu_code (menu_code)
) ENGINE = InnoDB;

-- 菜单种子数据：channel_code 为 NULL 表示所有条线可见
INSERT IGNORE INTO erp_menu (id, parent_id, menu_name, menu_code, path, icon, sort_order, channel_code, status)
VALUES (1, 0, '首页',         'dashboard',      '/home',            'HomeFilled',    1, NULL,  1),
       (2, 0, '系统管理',     'system',         NULL,               'Setting',       2, NULL,  1),
       (3, 2, '用户管理',     'system-users',   '/system/users',    'User',          1, NULL,  1),
       (4, 2, '菜单管理',     'system-menus',   '/system/menus',    'Menu',          2, NULL,  1),
       (5, 0, '业务示例',     'example',        NULL,               'Goods',         3, NULL,  1),
       (6, 5, '订单示例',     'example-orders', '/example/orders',  'List',          1, NULL,  1),
       (7, 0, '报表中心',     'reports',        '/reports',         'DataAnalysis',  4, NULL,  1),
       (8, 0, 'ERP 专属看板', 'erp-board',      '/erp/board',       'Monitor',       5, 'ERP', 1);
-- 另有 8 条按 menu_code 的幂等 UPDATE（对齐名称/排序/条线归属），见 erp-server/db/erp.sql。
```

CAS 侧无新增 DDL；新增服务定义文件 `etc/cas/services/service-3001.json`（见「ERP 演示系统」章节）。

#### 11.10 联调中修复的问题

| 问题 | 现象 | 修复 |
|---|---|---|
| 分页字段名不匹配 | 用户列表 total 正确但表格无行 | 前端按后端 `PageResult{total,records}` 读取 `records` |
| 菜单树丢字段 | 接口 JSON 无 `channelCode`，条线归属列恒显示"公共" | `MenuNode` 增加 `channelCode` 字段并透传 |
| 菜单管理页行重复 | 8 节点渲染 11 行（子树重复） | 改为树数据 + `tree-props`，过滤时保留祖先链 |
| 深链接丢失 | 未登录访问 `/system/menus`，登录后落到 `/home` | 跳 CAS 前把目标路径暂存 `sessionStorage`，回调后还原 |
| 登出被 CAS 拒绝 | `/cas/logout?service=http://localhost:5173/` 报「应用程序未被授权使用CAS」 | 回跳地址改为已注册的 `http://localhost:8081/erp/`，由后端 302 回前端；CAS 开启 `cas.logout.follow-service-redirects: true` |

#### ERP 测试结论

- ERP 前后端与 CAS 的整体调用链（入口跳转、票据校验、会话建立、菜单下发、登出、SLO、踢下线）全部按预期工作。
- 前端未登录一律走 CAS 登录页；ERP 本地会话与 CAS TGT 两级失效均生效，管理员踢下线后前端下一次请求即被拒。
- 菜单的条线隔离生效：`channelCode=ERP` 会话可见 8 节点（含 ERP 专属看板）；按同一过滤条件用 SQL 复算，RETAIL 渠道可见 7 节点且 `erp-board` 命中数为 0（`SELECT COUNT(*) ... WHERE channel_code IS NULL OR channel_code='RETAIL'`）。
- ERP 服务端会话为单节点内存实现，重启 erp-server 会丢失本地会话（用户凭 CAS TGT 免密重建）；多节点需共享会话存储。

### 12. 管理台控制面拆分（P1）联调（2026-10-22）

> 目标：为管理台三服务拆分做准备，把必须留在 CAS 进程内的能力收敛为"仅回环可访问的控制面"，同时不破坏现有管理台。

#### 12.1 改动范围

| 文件 | 改动 |
|---|---|
| `AdminTokenFilter` | 新增 `/admin/control/**` 回环地址校验；拦截范围覆盖新旧路径 |
| `AdminSessionController` | 会话查询、踢下线同时映射 `/admin/api/users/...` 与 `/admin/control/users/...` |
| `AdminServiceController` | 删除同时映射 `/admin/api/services/{id}` 与 `/admin/control/services/{id}` |
| `CasOverlayOverrideConfiguration` | 过滤器注册增加 `/admin/control/*` |

CAS 重建 war 后按标准方式重启（不带任何外置静态目录参数）；实验探针目录 `admin-ui/` 已清理。

#### 12.2 测试环境补充

| 项 | 值 |
|---|---|
| 管理令牌 | 环境变量 `CAS_ADMIN_TOKEN`（不记录值） |
| 回环地址 | `127.0.0.1:8080` |
| 非回环对照 | 本机局域网地址（`192.168.3.103:8080`） |

#### 12.3 用例与结果

| # | 用例 | 结果 |
|---|---|---|
| 1 | 回环 + 令牌：`GET /cas/admin/control/users/bob/sessions` | 200 `[]` |
| 2 | 回环 + 令牌：`POST .../kickout`（无在线会话） | 200 `{"userId":"bob","sessions":0,"logoutRequests":0,"errors":[],"kickedOut":false}` |
| 3 | 回环但无令牌 | 401 `{"error":"invalid admin token"}` |
| 4 | 局域网 IP 访问控制面 | 403 `{"error":"control api is only accessible from loopback"}` |
| 5 | 局域网 IP 访问旧 `/cas/admin/api/channels`（对照） | 200（限制只作用于控制面） |
| 6 | 旧接口新增探针服务 `id=3999` | 200，写入 `service-3999.json` |
| 7 | 探针服务登录入口（watcher 已加载） | 200 |
| 8 | 控制面删除探针服务 | 200 `{"id":3999,"deleted":true}`，文件已删除 |
| 9 | 删除后再访问该服务登录入口 | 403（运行时已卸载，无需重启） |
| 10 | 未配置 `CAS_ADMIN_TOKEN`：登录页 / 控制面 / 旧接口 | 200 / 503 / 503 |
| 11 | 恢复令牌后控制面 | 200 |
| 12 | 旧管理台页面 | 200（过渡期兼容） |

#### 12.4 SQL DDL / DML

- 无新增 DDL；继续使用第 7 节四张表。
- 用例 2 的"无会话踢下线"仍写入一条审计（`t_login_audit`）：

```sql
INSERT INTO t_login_audit (user_id, result, reason)
VALUES ('bob', 'KICKOUT', 'ADMIN_KICKOUT sessions=0 slo=0 errors=0');
```

实测记录：`id=9, user_id=bob, result=KICKOUT, reason=ADMIN_KICKOUT sessions=0 slo=0 errors=0, created_at=2026-10-22 23:17:03`。

- 探针服务只写 JSON 文件且已删除，不涉及数据库变更。

#### 12.5 结论

- 控制面与旧接口并存，过渡期无需修改现有管理台即可继续使用；
- 回环限制按路径生效，旧接口不受影响；
- 服务删除保持"文件删除 + 运行时卸载"语义，无需重启 CAS。

### 13. admin-server 数据面与代理（P2）联调（2026-10-22）

> 目标：新增独立 Spring Boot fat jar（`127.0.0.1:8084`）承接管理数据面，并代理 CAS 控制面；验证鉴权、CRUD、服务定义热加载/卸载与会话踢下线全链路。

#### 13.1 启动与环境

```bash
cd admin-server
export ADMIN_DB_PASSWORD='...'
export ADMIN_UI_TOKEN='...'
export ADMIN_CONTROL_TOKEN='...'      # 与 CAS 的 CAS_ADMIN_TOKEN 相同
export ADMIN_SERVICE_DIR='<仓库根>/etc/cas/services'
./mvnw -DskipTests package
java -jar target/admin-server-1.0.0-SNAPSHOT.jar
```

启动日志：`Tomcat started on port 8084`、`Started AdminServerApplication in 3.9s`。服务绑定 `127.0.0.1`，局域网地址访问连接被拒绝（HTTP 000）。

#### 13.2 用例与结果

| # | 用例 | 结果 |
|---|---|---|
| 1 | 无令牌访问 `/api/channels` | 401 `invalid admin token` |
| 2 | 局域网 IP 访问 8084（绑定校验） | 连接被拒绝（HTTP 000） |
| 3 | 令牌访问 `/api/channels` | 200，返回 CREDIT/ERP/RETAIL 三条（camelCase 字段） |
| 4 | `/api/users?q=bob` | 200，返回 Bob / D002 |
| 5 | `/api/users/bob/grants` | 200，CREDIT/ERP/RETAIL 三条授权 |
| 6 | 新增条线 `P2TEST` | 200 `{"channelCode":"P2TEST","updated":true}` |
| 7 | 授予 bob `P2TEST`（含有效期） | 200，授权列表显示 `validFrom`/`validTo` |
| 8 | 撤销 bob `P2TEST` | 200，授权状态变为 `status=0` |
| 9 | 经 admin-server 新增服务 `id=3998` | 200，写入 `service-3998.json` |
| 10 | 探针服务登录入口（watcher 已加载） | 200 |
| 11 | 经 admin-server 删除服务 3998（代理控制面） | 200，文件删除 + 运行时卸载，登录入口变 403 |
| 12 | 经 CAS 登录建立 bob 会话后查询 `/api/users/bob/sessions` | 200，返回 1 个 TGT 与 `http://localhost:8081/erp/login/callback` |
| 13 | 经 admin-server 踢下线 | 200 `sessions=1, logoutRequests=1, kickedOut=true`；再次查询会话为 `[]` |
| 14 | 踢下线后 ERP 本地会话（`/erp/api/session`） | `authenticated:false`（SLO 生效） |
| 15 | 未配置 `ADMIN_UI_TOKEN` | 503 `admin endpoint is disabled; set ADMIN_UI_TOKEN`；恢复令牌后 200 |

#### 13.3 SQL DDL / DML

- 无新增 DDL；复用 `t_channel`、`t_user`、`t_user_channel`、`t_login_audit`。
- 联调临时数据（已清理）：

```sql
INSERT INTO t_channel (channel_code, channel_name, status)
VALUES ('P2TEST', 'P2 Test Channel', 1);

INSERT INTO t_user_channel (user_id, channel_code, source, valid_from, valid_to, status)
VALUES ('bob', 'P2TEST', 'MANUAL', '2026-10-22 00:00:00', '2026-12-31 23:59:59', 1);

UPDATE t_user_channel SET status = 0
WHERE user_id = 'bob' AND channel_code = 'P2TEST';

-- 测试结束清理
DELETE FROM t_user_channel WHERE user_id = 'bob' AND channel_code = 'P2TEST';
DELETE FROM t_channel WHERE channel_code = 'P2TEST';
```

- 代理踢下线写入审计：`id=10, user_id=bob, result=KICKOUT, reason=ADMIN_KICKOUT sessions=1 slo=1 errors=0, created_at=2026-10-22 23:23:33`。

#### 13.4 联调中修复的问题

| 问题 | 现象 | 修复 |
|---|---|---|
| Druid wall 不识别 MySQL 别名语法 | 条线/授权 upsert 返回 500：`sql injection violation ... token AS` | 将 `INSERT ... VALUES (...) AS new ON DUPLICATE KEY UPDATE col = new.col` 改为重复占位符写法 |
| 测试负载编码 | curl 直接传中文 JSON 时服务器报 `Invalid UTF-8 start byte 0xb2` | 测试改用 ASCII 负载；浏览器/axios 均为 UTF-8，不影响产品 |

#### 13.5 结论

- 数据面 CRUD、令牌鉴权、回环绑定、服务定义热加载与运行时卸载、会话查询与踢下线代理全部按预期工作；
- 控制面仍由 CAS 进程完成，admin-server 只做代理，边界与设计一致；
- 测试数据已清理，管理台数据恢复到 CREDIT/ERP/RETAIL 三条。

### 14. admin-web 管理界面（P3）联调（2026-10-22）

> 目标：用独立 Vue3 工程替换 CAS 内置单页，经同源反代调用 admin-server，验证令牌入口、条线/用户/授权/服务/会话全流程与构建产物。

#### 14.1 启动与环境

```bash
cd sso-admin-web
npm install
npm run dev        # Vite 5174，/api → http://127.0.0.1:8084
```

依赖服务：CAS 8080、admin-server `127.0.0.1:8084`、MySQL 3306。admin-server 以 `ADMIN_UI_TOKEN` / `ADMIN_CONTROL_TOKEN` 启动；CAS 的控制面令牌与之相同。

#### 14.2 用例与结果

| # | 用例 | 结果 |
|---|---|---|
| 1 | 无令牌直接访问 `/users` | 路由守卫重定向到 `/gate` |
| 2 | 输入错误令牌 | 停留 `/gate`，页面显示“令牌校验失败：invalid admin token”，`sessionStorage` 未写入 |
| 3 | 输入正确令牌 | 跳转 `/channels`，表格 3 条（CREDIT/ERP/RETAIL） |
| 4 | 页面新增条线 `P3TEST` | 列表变为 4 条，数据库同步写入 |
| 5 | 用户页查询 `bob` | 返回 Bob / D002，授权列表 CREDIT/ERP/RETAIL 三条 |
| 6 | 授予 bob `P3TEST` | 授权行显示“有效”，弹窗提示“授权已保存” |
| 7 | 撤销 bob `P3TEST` | 授权行变“已撤销”，接口返回成功 |
| 8 | 选中 bob 查询在线会话（CAS 已登录） | 返回 1 个 TGT 及 ERP 回调地址 |
| 9 | 页面“强制踢下线” | 提示“已处理 1 个会话，发出 1 个 SLO 请求”，会话列表变空；ERP `/erp/api/session` 返回 `authenticated:false` |
| 10 | 接入系统页列表 | 3 条（1001/1002/3001） |
| 11 | 页面新增服务 `id=3997` | 生成 `etc/cas/services/service-3997.json`，登录入口 200 |
| 12 | 页面删除服务 3997 | 文件被删除，CAS 运行时卸载，登录入口变 403 |
| 13 | 顶部“切换令牌” | 清除 `sessionStorage` 并回到 `/gate` |
| 14 | `npm run build` | 构建成功（4.68s），产物写入 `dist/` |

#### 14.3 SQL DDL / DML

- 无新增 DDL；界面操作等价于第 13 节的条线/授权接口。
- 联调临时数据（已清理）：

```sql
-- 页面“新增条线”写入
INSERT INTO t_channel (channel_code, channel_name, status)
VALUES ('P3TEST', 'P3 Test Channel', 1);

-- 页面“授予授权”写入（有效期示例）
INSERT INTO t_user_channel (user_id, channel_code, source, valid_from, valid_to, status)
VALUES ('bob', 'P3TEST', 'MANUAL', '2026-10-22 00:00:00', '2026-12-31 23:59:59', 1);

-- 页面“撤销”软删除
UPDATE t_user_channel SET status = 0
WHERE user_id = 'bob' AND channel_code = 'P3TEST';

-- 测试结束清理
DELETE FROM t_user_channel WHERE user_id = 'bob' AND channel_code = 'P3TEST';
DELETE FROM t_channel WHERE channel_code = 'P3TEST';
```

- 页面踢下线写入审计：`id=11, user_id=bob, result=KICKOUT, reason=ADMIN_KICKOUT sessions=1 slo=1 errors=0, created_at=2026-10-22 23:31:03`。
- 清理后复核：条线仅 CREDIT/ERP/RETAIL，bob 授权仅 CREDIT/ERP/RETAIL，服务注册目录仅 1001/1002/3001。

#### 14.4 联调说明

| 事项 | 说明 |
|---|---|
| 无 CORS | 浏览器只访问 5174，`/api` 由 Vite/Nginx 反代到 8084；admin-server 只监听回环 |
| 令牌不落盘 | `sessionStorage` 键 `sso_admin_token`，关闭标签页即失效；401 自动清除并回入口 |
| 构建体积 | Element Plus 全量图标注册使主包约 1.26 MB（gzip 408 kB）；管理台仅内网使用，暂不拆分 |
| 验收脚本 | Element Plus 弹窗有动画，自动化脚本按固定短等待偶发超时；改为等待 DOM 状态后通过，非产品缺陷 |

#### 14.5 结论

- 管理界面已完全脱离 CAS war，条线、用户授权、接入系统、在线会话与踢下线全部经 admin-server 完成；
- UI 操作与 API 结果一致：新增/撤销授权、服务新增热加载、删除运行时卸载、踢下线触发 SLO；
- 旧内置管理台在过渡期仍可用，P4 再移除 CAS 旧数据面接口与静态页；
- 构建产物可直接由静态服务器托管，发布前端不再需要重建 CAS war。

### 15. 旧接口与内置管理台移除（P4）回归（2026-10-22）

> 目标：删除 CAS 进程内的旧数据面 `/admin/api/**` 与内置静态管理台，只保留回环控制面；确认 admin-server、admin-web、认证/SLO 全链路不受影响。

#### 15.1 变更范围

| 变更 | 说明 |
|---|---|
| 删除 `AdminChannelController`、`AdminUserController` | 条线与用户/授权数据面统一由 admin-server 提供 |
| 收敛 `AdminServiceController` | 只保留 `DELETE /admin/control/services/{id}` |
| 收敛 `AdminSessionController` | 只保留 `/admin/control/users/{username}/sessions、kickout` |
| 收敛 `ServiceDefinitionService` | 只保留删除与 JSON 查找，移除列表/新增逻辑 |
| 收敛 `AdminTokenFilter` | 过滤器只注册到 `/admin/control/*`，校验回环地址与令牌 |
| 删除 `src/main/resources/static/admin/index.html` | 管理界面由 `sso-admin-web` 提供 |
| 配置清理 | 移除已删除控制器的 Bean 与 import |

变更前已备份到仓库外目录 `C:\Users\webtu\Desktop\SSO-backup-p4-20261022`（源码、静态页、README、application.yml）。

#### 15.2 构建与启动

```bash
# 先停止正在运行的 CAS，否则 Windows 会锁住 target/cas.war
export JAVA_HOME='<JDK 21>'
./mvnw clean package -DskipTests
java -jar target/cas.war   # CAS_DB_PASSWORD / CAS_ADMIN_TOKEN 通过环境变量注入
```

结果：`BUILD SUCCESS`，产物 `target/cas.war` 244,499,111 字节；启动后加载 3 个服务，登录页 200。

#### 15.3 用例与结果

| # | 用例 | 结果 |
|---|---|---|
| 1 | 回环访问旧 `/cas/admin/api/channels` | 404 |
| 2 | 回环访问旧 `/cas/admin/api/users?q=bob` | 404 |
| 3 | 回环访问旧 `/cas/admin/api/services` | 404 |
| 4 | 访问内置 `/cas/admin/index.html` | 404 |
| 5 | 控制面不带令牌 | 401 |
| 6 | 控制面回环 + 令牌 | 200 `[]` |
| 7 | 控制面经局域网 IP `192.168.3.103` + 令牌 | 403 |
| 8 | 旧接口经局域网 IP | 404 |
| 9 | admin-server 不带令牌 / 带令牌查询条线 | 401 / 200，CREDIT/ERP/RETAIL 三条 |
| 10 | 经 admin-server 新增探针服务 3996 | 写入 `service-3996.json`，CAS 登录入口 200 |
| 11 | 经 admin-server 删除 3996 | 文件移除，登录入口立即 403 |
| 12 | 对旧路径 `DELETE /cas/admin/api/services/3996` | 404 |
| 13 | bob 登录 ERP（`channelCode=ERP`） | 302 携 ST；ERP 回调后 `/erp/api/session` 为 `authenticated:true` |
| 14 | 登录入口校验 | 注册服务 + 正确 `channelCode` 200；错配、缺失 `channelCode`、未注册服务均 403 |
| 15 | 经 admin-server 查询会话并踢下线 | `sessions=1, logoutRequests=1`；随后会话 `[]`，ERP `authenticated:false` |
| 16 | 审计 | `id=12 ... ADMIN_KICKOUT sessions=1 slo=1 errors=0` |
| 17 | admin-web 回归 | 条线 3 条、服务 3 条、bob 授权 3 条且“无在线会话” |
| 18 | war 内容检查 | 无 `static/admin`、`AdminChannelController`、`AdminUserController`；控制面 3 个类存在 |

#### 15.4 SQL DDL / DML

- 无新增 DDL；P4 未修改表结构。
- 探针服务只涉及 `etc/cas/services/service-3996.json`，测试后已删除。
- 踢下线审计为预期写入：`id=12, user_id=bob, result=KICKOUT, reason=ADMIN_KICKOUT sessions=1 slo=1 errors=0`。
- 清理后复核：服务目录仅 1001/1002/3001，无 P4 临时数据。

#### 15.5 联调说明

| 事项 | 说明 |
|---|---|
| 构建 JDK | 当前 shell 的 `JAVA_HOME` 可能仍是 JDK 8；必须显式切换到 JDK 21，否则文本块语法报错 |
| war 文件锁 | CAS 运行时 `target/cas.war` 被占用，`repackage` 无法重命名；先停进程再打包 |
| 历史文档 | 第 1～14 节保留了 P4 之前的旧路径记录，已加历史说明，不代表当前接口 |

#### 15.6 结论

- CAS 已不再暴露任何浏览器可访问的管理数据面，旧接口与内置页面均返回 404；
- 控制面仍只接受回环地址 + 令牌，局域网访问 403；
- admin-server 的数据面、服务热加载/卸载、会话查询与踢下线、admin-web 页面全部正常；
- 认证、条线准入、ERP 登录与 SLO 回归通过，P4 收敛完成。

### 测试结论

- 登录入口校验、MySQL 认证、条线准入、拒绝审计、跨条线 SSO、实时撤权均按预期工作。
- 管理台令牌鉴权、条线/授权/服务管理、外置注册表热加载与运行时删除均按预期工作；新系统接入不再需要重新构建。
- 主动 SLO 与管理员强制踢下线均能在同一个请求周期内使两个业务系统的服务端本地会话失效；旧 TGC 不能再换取新票据。
- ERP 演示系统（Spring Boot + Druid + tk-mapper / Vue3 + Vite）与 CAS 的登录、免密重入、用户/菜单查询、登出与踢下线全链路工作正常，详见第 11 节。
- 管理台控制面（P1）已拆分并验证：非回环访问 403、无令牌 401、未配置令牌 503；旧接口与旧管理台在过渡期不受影响，详见第 12 节。
- admin-server（P2）已落地并验证：数据面 CRUD、令牌鉴权、服务定义热加载/卸载、会话踢下线代理全部通过，详见第 13 节。
- admin-web（P3）已落地并验证：令牌入口、条线/用户/授权/服务/在线会话全流程经 UI 完成，前端构建独立于 CAS war，详见第 14 节。
- 管理台收敛（P4）已完成：旧 `/cas/admin/api/**` 与内置页面返回 404，控制面回环校验保持，admin-server/admin-web 与认证/SLO 全链路回归通过，详见第 15 节。
- CAS 登出回跳地址必须命中已注册服务，否则报「应用程序未被授权使用CAS」；`cas.logout.follow-service-redirects: true` 决定登出后是否自动跳回。
- 服务删除必须走管理台或重启 CAS；直接删除 JSON 文件不会卸载运行时服务。
- 数据库初始化必须显式使用 `utf8mb4`，否则中文条线名会乱码。
- 测试库 `cas_sso` 保留，便于复现；生产前请删除演示账号、修改演示密码，并使用最小权限数据库账号。
- 已知边界：SLO 依赖业务 `logoutUrl` 可达且实现正确；回调失败时本地 session 可能残留，需要重试队列或短 TTL 兜底。

## 术语与缩写

本章按“认证会话 → SLO → 本项目自定义概念 → 通用工程术语 → 易混淆关系”的顺序解释 README 中出现的专有名词。

### 1. 认证与会话

| 缩写/术语 | 全称 | 含义 | 在本项目中的分析 |
|---|---|---|---|
| SSO | Single Sign-On，单点登录 | 用户一次认证后可访问多个互信系统，无需重复输入密码 | 本项目由 CAS 集中认证；bob 登录 RETAIL 后可免密进入 CREDIT |
| CAS | Central Authentication Service，中央认证服务 | Apereo 基金会的开源统一认证协议与实现 | 本项目使用 Apereo CAS 7.3.8.3，CAS 是认证中心，不是业务系统 |
| TGC | Ticket Granting Cookie，票据授予 Cookie | 浏览器保存的 CAS 全局会话 Cookie，通常只保存 TGT 的引用 | 路径为 `/cas/`、HttpOnly；本地环境关闭了加密，生产必须启用 HTTPS 与 TGC 加密 |
| TGT | Ticket Granting Ticket，票据授予票据 | CAS 服务端的全局登录会话，代表“这个浏览器已登录” | 踢下线的核心就是按用户找到并删除 TGT；TGT 删除后旧 TGC 不能再换新票据 |
| ST | Service Ticket，服务票据 | CAS 针对某一个业务系统签发的一次性票据 | 业务回调 `/p3/serviceValidate` 校验 ST 后建立本地会话；SLO 的 `SessionIndex` 通常就是 ST |
| PGT | Proxy Granting Ticket，代理授予票据 | CAS 代理认证场景下，代表某个服务代替用户访问下游服务的凭证 | 本项目未使用代理认证，因此没有 PGT/PGTIOU 流程 |
| PGTIOU | Proxy Granting Ticket IOU | 代理票据的中间标识，用于回调时换取 PGT | 本项目未使用 |
| Apereo CAS | Apereo Central Authentication Service | CAS 项目的完整名称 | 与泛称“CAS 协议”区分：本项目既使用 CAS 协议，也使用 Apereo 的具体实现 |
| CAS 3.0 | CAS Protocol 3.0 | CAS 的票据校验协议版本 | 本项目回调使用 `/p3/serviceValidate`，其中 `p3` 表示协议 3.0 |
| SP | Service Provider，服务提供方 | SAML 语境中消费身份断言的一方 | 本项目业务系统相当于 SP，但认证协议是 CAS，不是完整 SAML |
| IdP | Identity Provider，身份提供方 | 负责认证并签发身份断言的一方 | CAS 在本项目中承担 IdP 角色 |
| 本地会话 | Local Session | 业务系统自己维护的登录状态 | 与 CAS 的 TGT 相互独立；SLO 必须显式通知业务系统，否则本地会话不会自动消失 |

### 2. 单点登出与相关协议

| 缩写/术语 | 全称 | 含义 | 在本项目中的分析 |
|---|---|---|---|
| SLO | Single Logout，单点登出 | 一个系统登出后，让所有参与 SSO 的系统一起登出 | 本项目使用 CAS 后端 SLO；SLO 不是“删除浏览器所有 Cookie”，而是通知各业务销毁服务端会话 |
| Back-channel SLO | Back-channel Single Logout，后端单点登出 | CAS 直接向业务 `logoutUrl` 发 HTTP POST，不经过用户浏览器 | 本项目采用此方式；不依赖浏览器标签页是否还开着 |
| Front-channel SLO | Front-channel Single Logout，前端单点登出 | 通过浏览器 iframe/AJAX 加载各业务的登出地址 | 依赖浏览器和第三方 Cookie 策略，不适合作为强一致注销的唯一手段；本项目未采用 |
| SAML | Security Assertion Markup Language，安全断言标记语言 | 一种 XML 身份断言标准 | 本项目只在 SLO 消息中使用 SAML LogoutRequest XML，并不是完整 SAML IdP |
| LogoutRequest | SAML Logout Request，登出请求 | CAS 发给业务的 XML 注销消息 | 通过表单参数 `logoutRequest` 传递 |
| SessionIndex | Session Index，会话索引 | LogoutRequest 中标识某次业务会话的字段 | 本项目按它匹配业务本地 session 中保存的 ST，只注销对应会话 |
| logoutUrl | Logout URL，登出回调地址 | 业务系统提供的 SLO 接收端点 | 在服务注册 JSON 中配置；必须能被 CAS 访问，且实现幂等 |
| logoutType | Logout Type，登出类型 | CAS 服务定义中的登出方式 | 本项目固定为 `BACK_CHANNEL`，确保服务端到服务端回调 |
| 踢下线 | Forced Logout / Admin Kick-out | 管理员强制终止指定用户的全部会话 | 比普通 SLO 多了“按用户枚举 TGT”的步骤；本项目通过 `TicketRegistry.getSessionsFor` 实现 |

### 3. 本项目自定义概念

| 术语 | 含义 | 分析 |
|---|---|---|
| channelCode | 条线代码，本项目的业务条线标识 | 例如 RETAIL、CREDIT；由接入系统传入，但只作为声明，真正授权依据是服务注册表中绑定的 channelCode；它不是密钥 |
| 条线 | 业务条线/业务线 | 组织内按业务划分的系统集合；一个用户可被授予多个条线 |
| serviceId | CAS 服务标识/匹配正则 | 必须锚定主机和路径，例如 `^http://localhost:9001/.*$`；不能使用 `^.*$` 这类全匹配 |
| RegisteredService | 已注册服务 | CAS 对某个接入系统的配置对象，包含 serviceId、channelCode、logoutUrl 等 |
| 服务注册表 | Service Registry | 保存“有哪些接入系统”的配置；本项目是 `etc/cas/services/*.json`，新增可热加载 |
| 票据注册表 | Ticket Registry | 保存运行时的 TGT/ST；本项目默认是内存实现，重启丢失，多节点需要共享实现 |
| AccessStrategy | 访问策略 | CAS 在签发票据前执行的准入判断；本项目用它实时查询用户-条线授权 |
| `X-Admin-Token` | 管理接口令牌 | 管理台请求头中的共享令牌；不是用户身份，也不是完整 RBAC |
| KICKOUT | 踢下线审计结果 | 写入 `t_login_audit.result`，原因列记录会话数、SLO 请求数和错误数 |

### 4. 通用工程术语

| 缩写/术语 | 全称 | 含义 | 在本项目中的分析 |
|---|---|---|---|
| API | Application Programming Interface，应用程序编程接口 | 系统间约定的调用接口 | 管理台通过 REST API 操作条线、授权、服务和会话 |
| REST | Representational State Transfer，表述性状态转移 | 一种以资源为中心的 HTTP 接口风格 | 管理接口使用 GET/POST/DELETE 操作资源 |
| JSON | JavaScript Object Notation，JavaScript 对象表示法 | 轻量级数据交换格式 | 服务注册文件、管理接口请求/响应均使用 JSON |
| XML | eXtensible Markup Language，可扩展标记语言 | 结构化文本格式 | SLO 的 LogoutRequest 是 XML；业务端解析 `SessionIndex` |
| HTTP | Hypertext Transfer Protocol，超文本传输协议 | Web 请求响应协议 | 本地联调用 HTTP；生产必须使用 HTTPS |
| HTTPS | Hypertext Transfer Protocol Secure，安全超文本传输协议 | HTTP over TLS | 保护 TGC、管理令牌和 SLO 回调内容 |
| TLS | Transport Layer Security，传输层安全协议 | 加密传输协议 | HTTPS 的底层安全机制；生产应启用有效证书 |
| JWT | JSON Web Token，JSON 网络令牌 | 自包含的签名令牌，常用于无状态会话 | 纯 JWT 无法被远程立即吊销；要做踢下线必须维护吊销列表/版本号或短 TTL |
| TTL | Time To Live，生存时间 | 数据或会话的有效期 | 业务 session 的短 TTL 可作为 SLO 失败时的兜底，但不是“立即失效”的替代 |
| MFA | Multi-Factor Authentication，多因素认证 | 密码之外再要求第二因素 | 表中有 `mfa_required` 字段，但当前未接入实际 MFA 流程，不能把该字段当作已启用 |
| RBAC | Role-Based Access Control，基于角色的访问控制 | 按角色分配权限 | 当前管理台只有共享令牌，没有角色和管理员身份 |
| UI | User Interface，用户界面 | 面向用户的交互界面 | 管理台是独立前端 `sso-admin-web` |
| ID | Identifier，标识符 | 唯一标识 | TGT ID、ST ID、服务 id、sessionId 都是标识符，不可混用 |
| BCrypt | bcrypt password hashing | 带盐的自适应密码哈希算法 | 数据库保存 BCrypt 哈希，不保存明文密码 |
| JDBC | Java Database Connectivity，Java 数据库连接 | Java 访问关系型数据库的标准 API | CAS JDBC 认证和本项目授权查询都通过 JDBC 访问 MySQL |
| SQL | Structured Query Language，结构化查询语言 | 关系型数据库查询语言 | 授权查询、审计写入使用 SQL |
| DDL | Data Definition Language，数据定义语言 | 创建/修改表结构的 SQL | `db/schema.sql` 中的 CREATE TABLE 属于 DDL |
| DML | Data Manipulation Language，数据操纵语言 | 增删改查数据的 SQL | INSERT/UPDATE/DELETE 属于 DML；测试记录中单独列出 |
| WAR | Web Application Archive，Web 应用归档 | Java Web 应用打包格式 | 本项目产物是 `target/cas.war`，可部署到外部 Tomcat |
| JDK | Java Development Kit，Java 开发工具包 | 编译和运行 Java 的工具集合 | 本项目要求 JDK 21；构建前必须设置正确的 `JAVA_HOME` |
| UTF-8 | Unicode Transformation Format 8-bit | Unicode 的一种变长编码 | 中文条线名和 JSON 使用 UTF-8；MySQL 客户端导入需指定 `utf8mb4` |
| AJAX | Asynchronous JavaScript and XML，异步 JavaScript 与 XML | 浏览器异步请求技术 | CAS 支持 AJAX 前端 SLO；本项目选择后端 SLO 以避免依赖浏览器 |

### 5. 存储与中间件

| 术语 | 含义 | 分析 |
|---|---|---|
| 内存票据注册表 | CAS 默认 Ticket Registry | 单节点简单、无外部依赖；重启丢失会话，多节点之间不共享 |
| Redis | Remote Dictionary Server，远程字典服务器 | 可作为共享票据注册表或缓存；不是 SLO 的必要条件 |
| Hazelcast | 内存数据网格 | CAS 支持的另一种共享票据方案，和 Redis 同类但部署模型不同 |
| JDBC Ticket Registry | 数据库票据注册表 | 用关系型数据库共享票据，适合已有数据库运维体系的场景 |
| user → TGT 索引 | 用户到票据的二级索引 | 多节点按用户踢下线时避免全量扫描；应由 TGT 创建/删除事件维护 |

### 6. 容易混淆的关系

```text
浏览器 ──TGC Cookie──> CAS 服务端 TGT ──ST──> 业务系统本地会话
                              │
                              └──SLO 回调──> 各业务 logoutUrl ──> 删除本地会话
```

- **TGC 不是 TGT**：TGC 是浏览器 Cookie，TGT 是 CAS 服务端对象；删 TGT 后旧 TGC 失效。
- **TGT 不是 ST**：TGT 代表全局 SSO 会话，ST 代表一次具体业务登录；ST 通常一次性使用。
- **CAS 会话不是业务本地会话**：CAS 注销 TGT 只让 CAS 不再签发新票据，业务本地 session 必须靠 SLO 显式删除。
- **SLO 不是踢下线**：SLO 是协议/机制，踢下线是管理员发起的强制操作；本项目踢下线复用 SLO 并额外枚举全部 TGT。
- **服务注册表不是票据注册表**：前者是“接入系统配置”，后者是“运行时登录票据”。
- **Redis 不是 SLO 的前提**：单节点内存注册表也能完成 SLO；Redis 解决多节点共享，不负责删除业务 session。
- **channelCode 不是密钥**：它只是条线声明；真正的准入依据是服务注册表绑定值 + 数据库实时授权。
- **`mfa_required` 不是已启用 MFA**：当前只是数据字段，CAS 登录流程尚未读取并强制执行。

### 7. 本项目未使用或未实现

- PGT/PGTIOU：未启用 CAS 代理认证。
- 完整 SAML IdP：只使用 SAML LogoutRequest 作为 SLO 消息格式。
- OAuth2/OIDC：本项目使用 CAS 协议，不是 OAuth2/OIDC。
- MFA 强制执行：表中有字段，未接入第二因素流程。
- SLO 失败重试队列：当前只记录错误，未自动重试。
- 共享票据注册表：单节点使用内存实现，多节点部署需要另行接入 Redis/JDBC/Hazelcast。

## 许可

CAS 采用 Apache License 2.0，见 `LICENSE.txt`。
