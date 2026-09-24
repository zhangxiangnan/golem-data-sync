# golem-data-sync

基于 Apache SeaTunnel 2.3.13 的 MySQL 批量同步平台，任务定义与执行引擎分离。

## 功能

- MySQL 数据源管理、连接测试、只读数据浏览和表结构查看
- 单表整表批量同步
- 目标表自动创建
- 追加写入或清空后重写
- 运行状态、指标、事件时间线和失败诊断
- 同一任务在每次运行时选择执行配置，不把引擎固化在任务定义中
- 本机 Zeta 通过 REST API 真实提交、查询、停止和恢复运行
- Spark 本地模拟执行器覆盖提交、运行、失败、停止和指标生命周期
- Flink 已进入能力矩阵与扩展模型，但尚未配置可运行适配器

## 本地启动

要求：Java 17+、Node 20+、pnpm 11+、可访问的 MySQL。后端使用仓库内 Maven Wrapper，无需单独安装 Maven。

```bash
./scripts/bootstrap-seatunnel.sh
./scripts/dev-up.sh
open http://127.0.0.1:3200
./scripts/dev-down.sh
```

如果 Apache 默认下载端点在当前网络不可达，可指定一个可信的 Apache 镜像后重试：

```bash
SEATUNNEL_DOWNLOAD_URL=https://你的镜像/apache-seatunnel-2.3.13-bin.tar.gz ./scripts/bootstrap-seatunnel.sh
```

默认端口：Web `3200`、API `8090`、SeaTunnel REST `8081`。

## 查看 MySQL 数据

在「数据源」点击「查看数据」，或从任务详情点击「查看源表 / 查看目标表」。浏览页支持切换数据源和表、分页（20/50/100 条）、刷新及字段结构查看；NULL、空字符串和长文本分别显示，长文本可点击展开。

只读接口：`GET /api/data-sources/{id}/tables/{table}/rows?page=1&pageSize=50`。单元格返回字符串或 null，以保留大整数、金额和时间文本；二进制显示为十六进制。有主键时按主键升序，没有主键时顺序可能变化。翻页不提供跨页快照，不支持编辑数据或自定义 SQL。

## 执行配置

| Profile | 引擎 | 当前状态 | 用途 |
|---|---|---|---|
| `zeta-local` | Zeta | 真实 | 本机开发和 MySQL → MySQL 真实同步 |
| `spark-local-mock` | Spark 3 | 模拟 | 验证多引擎提交、状态、指标和停止流程，不访问公司集群 |
| `flink-unconfigured` | Flink | 未配置 | 仅展示能力和限制，不可选择运行 |

运行任务时可选择启用且在线的 profile；不传 `engineProfileId` 时兼容默认 `zeta-local`：

```http
POST /api/sync-jobs/{jobId}/runs
Content-Type: application/json

{"engineProfileId":"spark-local-mock"}
```

可通过 `GET /api/engine-profiles` 查看执行配置健康状态，通过
`GET /api/engine-capabilities` 查看 Zeta、Spark、Flink 对当前链路的能力和限制。
配置预览支持 `?engineProfileId=zeta-local` 或 `spark-local-mock`。

也可以分终端启动：

```bash
./scripts/start-seatunnel.sh
./mvnw spring-boot:run
cd apps/web && pnpm install && pnpm dev
```

如需演示数据，先在 `.env` 中配置本地 MySQL 管理账号，再执行：

```bash
cp .env.example .env
./scripts/seed-demo-mysql.sh
```

## 验证

```bash
./mvnw clean test
cd apps/web
pnpm typecheck
pnpm test
pnpm build
```

## 当前边界

第一阶段仅支持 MySQL → MySQL、手工触发、单表整表同步。Zeta 已接真实执行，
Spark 仅为本地 Mock，Flink 尚无执行适配器。不支持 CDC、Cron、多表任务、自定义
SQL、字段改名、Upsert、Schema Evolution 或任意 Connector 跨引擎兼容。追加模式
重复运行可能产生重复数据。
