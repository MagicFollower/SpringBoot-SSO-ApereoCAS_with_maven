# bin 构建与运行（三个服务）

> 本文件是 `bin/` 目录的 README。除特别说明外，命令均在仓库根目录执行。
>
> 适用版本：2026-10-23。本文对应 `bin/` 下的 Windows cmd 脚本，覆盖 CAS、admin-server、admin-web 三个服务；ERP 演示系统仍按 [README](../README.md) 手工启动。

## 1. 前置条件

| 项 | 要求 |
|---|---|
| 操作系统 | Windows 10/11，脚本为 `.cmd` |
| JDK | 21（CAS 与 admin-server 都要求） |
| Node.js | 18+，自带 npm |
| MySQL | 8.x，已执行 `db/schema.sql` 与 `db/seed.sql` |
| 端口 | 8080（CAS）、8084（admin-server，仅回环）、5174（admin-web） |
| 目录 | 在仓库根目录或任意位置执行 `bin\*.cmd` 均可，脚本自动解析仓库路径 |

## 2. 一次性准备

复制环境变量模板：

```bat
copy bin\env.example.cmd bin\env.cmd
```

编辑 `bin\env.cmd`，填入本机真实值：

```bat
set "JAVA_HOME=C:\path\to\jdk-21"
set "CAS_DB_PASSWORD=..."
set "ADMIN_DB_PASSWORD=..."
set "CAS_ADMIN_TOKEN=..."
set "ADMIN_UI_TOKEN=..."
set "ADMIN_CONTROL_TOKEN=..."
```

规则：

- `ADMIN_DB_PASSWORD` 与 `CAS_DB_PASSWORD` 可以相同，取决于数据库账号。
- `ADMIN_CONTROL_TOKEN` 必须与 `CAS_ADMIN_TOKEN` 一致，否则 admin-server 调控制面会 401。
- `bin\env.cmd` 已加入 `.gitignore`，**不要提交真实密码或令牌**。
- 未创建 `env.cmd` 时，脚本仍会尝试使用当前命令行已有的环境变量；缺少必需变量会给出提示并退出。
- 若本机默认 `java -version` 不是 21，必须通过 `JAVA_HOME` 指向 JDK 21；脚本会拒绝在 JDK 8 下构建/启动。

## 3. 脚本清单

| 脚本 | 作用 |
|---|---|
| `bin\env.example.cmd` | 环境变量模板 |
| `bin\build-all.cmd` | 依次构建三个服务 |
| `bin\build-cas.cmd` | 停止已运行的 CAS，执行 `mvnw clean package -DskipTests` |
| `bin\build-admin-server.cmd` | 停止已运行的 admin-server，执行 `mvnw clean package -DskipTests` |
| `bin\build-admin-web.cmd` | 需要时执行 `npm ci`，再执行 `npm run build` |
| `bin\start-all.cmd` | 依次启动 CAS → admin-server → admin-web，并等待就绪 |
| `bin\start-cas.cmd` | 后台启动 `target\cas.war`，等待 `/cas/login` 返回 200 |
| `bin\start-admin-server.cmd` | 后台启动 admin-server fat jar，等待 8084 可连接 |
| `bin\start-admin-web.cmd` | 用 Vite preview 托管 `dist`，等待 5174 返回 200 |
| `bin\stop-all.cmd` | 反向停止 admin-web → admin-server → CAS |
| `bin\stop-*.cmd` | 按 `.run\<service>.pid` 停止对应服务 |

脚本产生的目录：

| 目录 | 内容 | 是否入库 |
|---|---|---|
| `target/` | CAS 可执行 war | 否 |
| `admin-server/target/` | admin-server fat jar | 否 |
| `sso-admin-web/dist/` | 管理前端静态产物 | 否 |
| `sso-admin-web/node_modules/` | 前端依赖 | 否 |
| `build/` | CAS 运行时 Tomcat 工作目录 | 否 |
| `logs/` | 三个服务的 stdout/stderr | 否 |
| `.run/` | 三个服务的 PID 文件 | 否 |
| `bin/env.cmd` | 本机环境变量 | 否 |

## 4. 构建

### 全量构建

```bat
bin\build-all.cmd
```

执行顺序：CAS → admin-server → admin-web。任一步失败即停止，返回非 0。

### 单独构建

```bat
bin\build-cas.cmd
bin\build-admin-server.cmd
bin\build-admin-web.cmd
```

说明：

- 构建 CAS 前会先停止由脚本启动的 CAS，避免 Windows 锁定 `target\cas.war`。
- admin-web 首次构建且没有 `node_modules\vite` 时，会自动执行 `npm ci`；之后直接 `npm run build`。
- CAS 与 admin-server 都使用 JDK 21 编译；脚本会检查 `java.version = 21`。
- CAS 构建成功后产物为 `target\cas.war`；admin-server 为 `admin-server\target\admin-server-1.0.0-SNAPSHOT.jar`。

## 5. 启动

```bat
bin\start-all.cmd
```

或单独启动：

```bat
bin\start-cas.cmd
bin\start-admin-server.cmd
bin\start-admin-web.cmd
```

行为：

- 启动前检查对应端口是否已监听；已占用则直接退出，避免重复启动。
- 后台运行，不弹出新窗口；标准输出/错误写入 `logs\<service>.out.log` 与 `logs\<service>.err.log`。
- 进程 PID 写入 `.run\<service>.pid`。
- CAS 最多等待 60 秒直到 `/cas/login` 返回 200；admin-server 等待 8084 可连接；admin-web 等待 5174 返回 200。
- 超时会自动调用对应停止脚本并返回非 0。

依赖顺序：

```text
CAS (8080) ──> admin-server (8084) ──> admin-web (5174)
```

admin-server 启动时会读取 `ADMIN_SERVICE_DIR`，脚本默认设为 `<仓库>\etc\cas\services`；CAS 默认读取同一目录。

## 6. 停止

```bat
bin\stop-all.cmd
```

或单独停止：

```bat
bin\stop-admin-web.cmd
bin\stop-admin-server.cmd
bin\stop-cas.cmd
```

行为：

- 只停止 `.run\<service>.pid` 中记录且命令行匹配对应标记的进程，不会按端口或进程名批量杀进程。
- 进程已退出或 PID 文件不存在时返回成功，并清理过期 PID 文件。
- 若 PID 被其他进程复用、命令行不匹配，脚本拒绝结束该进程并返回非 0。
- 手工启动的服务没有 PID 文件，脚本不会尝试停止。

## 7. 验证

启动完成后执行：

```bat
curl -s -o NUL -w "cas_login=%{http_code}\n" http://localhost:8080/cas/login
curl -s -o NUL -w "control=%{http_code}\n" -H "X-Admin-Token: <CAS_ADMIN_TOKEN>" http://localhost:8080/cas/admin/control/users/bob/sessions
curl -s -o NUL -w "admin_api=%{http_code}\n" -H "X-Admin-Token: <ADMIN_UI_TOKEN>" http://127.0.0.1:8084/api/channels
curl -s -o NUL -w "admin_web=%{http_code}\n" http://localhost:5174/
curl -s -o NUL -w "admin_web_proxy=%{http_code}\n" -H "X-Admin-Token: <ADMIN_UI_TOKEN>" http://localhost:5174/api/channels
```

期望：

```text
cas_login=200
control=200
admin_api=200
admin_web=200
admin_web_proxy=200
```

浏览器打开 `http://localhost:5174/gate`，输入 `ADMIN_UI_TOKEN`，应能进入条线管理并看到三条演示条线。

## 8. 日志与排错

| 现象 | 原因 | 处理 |
|---|---|---|
| `JDK 21 is required` | `JAVA_HOME` 指向 JDK 8 或未设置 | 修改 `bin\env.cmd`，指向 JDK 21 |
| `target\cas.war not found` | 未构建 | 执行 `bin\build-cas.cmd` |
| `port 8080/8084/5174 is already in use` | 已有实例或别的程序占用 | 执行对应 `stop-*.cmd`，或手工确认占用进程 |
| `ADMIN_UI_TOKEN is not set` | `env.cmd` 缺变量 | 补全后重开 cmd 窗口 |
| admin-server 控制面调用 401 | `ADMIN_CONTROL_TOKEN` 与 `CAS_ADMIN_TOKEN` 不一致 | 改成同一令牌并重启两边 |
| admin-web 打开 404 / 空白 | `dist` 未构建或未更新 | 执行 `bin\build-admin-web.cmd` 后再启动 |
| admin-web 的 `/api` 502 | admin-server 未启动 | 先启动 admin-server，检查 8084 |
| 构建 CAS 报文件重命名失败 | 运行中的 CAS 锁住 war | 先执行 `bin\stop-cas.cmd` |
| `npm ci` 失败 | 网络或 npm 源不可用 | 配置 npm 镜像后重试，或改用 `npm install` |

日志位置：

```text
logs\cas.out.log
logs\cas.err.log
logs\admin-server.out.log
logs\admin-server.err.log
logs\admin-web.out.log
logs\admin-web.err.log
```

## 9. 本次脚本实测记录（2026-10-23）

环境：Windows 11，JDK 21.0.12，Node 24.18.0，MySQL 8.4；CAS/admin-server 使用同一测试令牌。

| 步骤 | 命令 | 结果 |
|---|---|---|
| CAS 构建 | `bin\build-cas.cmd` | BUILD SUCCESS，生成 `target\cas.war` |
| admin-server 构建 | `bin\build-admin-server.cmd` | BUILD SUCCESS，生成 fat jar |
| admin-web 构建 | `bin\build-admin-web.cmd` | `npm ci` + `vite build` 成功，生成 `dist` |
| 全量构建 | `bin\build-all.cmd` | 三个服务全部构建成功 |
| 全量启动 | `bin\start-all.cmd` | 8080/8084/5174 均就绪，返回 0 |
| 重复启动 CAS | `bin\start-cas.cmd` | 检测到 8080 占用，返回 1，不重复启动 |
| 功能检查 | 第 7 节五条 curl | 全部 200 |
| 前端预览 | 浏览器访问 `http://localhost:5174/gate` | 令牌入口正常，条线列表 3 条 |
| 全量停止 | `bin\stop-all.cmd` | 三个 PID 均停止，端口释放，返回 0 |
| 重复停止 | `bin\stop-cas.cmd` | 提示 no pid file，返回 0 |

> 测试中曾发现 `timeout /t` 在 PATH 含 Unix 工具时会被错误解析，脚本已改为 Windows `ping -n 2` 做等待；同时 `timeout` 在 stdin 被重定向时会报错，使用 `ping` 可同时兼容交互与自动化执行。

## 10. 与手工方式的关系

`bin` 脚本只是对现有命令的封装，不改变服务配置：

- CAS 仍通过 `CAS_DB_PASSWORD`、`CAS_ADMIN_TOKEN` 等环境变量启动。
- admin-server 仍只监听 `127.0.0.1:8084`。
- admin-web 生产预览使用 `vite preview`，`/api` 代理配置在 `sso-admin-web/vite.config.js` 的 `preview.proxy`。
- 如需 Nginx 托管，仍按 README 的发布说明将 `dist` 交给静态服务器并反代 `/api`。

## 11. 清理

测试或构建后可删除：

```text
target\
admin-server\target\
sso-admin-web\dist\
sso-admin-web\node_modules\
build\
logs\
.run\
bin\env.cmd
```

源码、`package-lock.json`、服务定义与数据库脚本不要删除。
