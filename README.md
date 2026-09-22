# golem-data-sync

基于 Apache SeaTunnel Zeta 2.3.13 的本地 MySQL 批量同步平台。

## 功能

- MySQL 数据源管理、连接测试和表结构浏览
- 单表整表批量同步
- 目标表自动创建
- 追加写入或清空后重写
- 运行状态、指标、事件时间线和失败诊断
- SeaTunnel Zeta 独立运行，平台通过 REST API 提交和跟踪任务

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

第一阶段仅支持 MySQL → MySQL、手工触发、单表整表同步。不支持 CDC、Cron、多表任务、自定义 SQL、字段改名或 Upsert。追加模式重复运行可能产生重复数据。
