# 项目状态

更新时间：2026-09-22

## 第一阶段已完成

- MySQL 数据源的新增、编辑、删除、测试连接、表与字段元数据读取。
- MySQL 到 MySQL 的单表整表同步任务，支持追加与清空重写。
- 任务定义与执行引擎解耦，每次运行保存实际 engine type、profile、外部任务 ID 和追踪地址。
- Zeta 确定性生成 SeaTunnel JSON；Spark 确定性生成 SeaTunnel Spark 3 HOCON；预览和持久化快照均隐藏密码。
- `zeta-local` 通过 SeaTunnel REST 提交、轮询、停止；`spark-local-mock` 模拟完整生命周期。
- Engine Registry 按运行记录保存的 profile 完成轮询、停止和服务重启后的活动任务恢复。
- Zeta、Spark、Flink 能力矩阵，以及执行前链路能力校验；Flink 当前未配置执行 profile。
- 同一任务单活动运行保护、状态事件时间线、错误脱敏和引擎离线陈旧标记。
- Overview、数据源、任务列表、四步创建向导、任务详情和运行详情页面。
- 运行弹窗可选择本机 Zeta 或 Spark 本地模拟；历史与详情展示引擎、profile、外部任务 ID、Mock 标识和指标可用性。
- SeaTunnel、API、Web 的本地启动/停止脚本，以及可选 MySQL 演示数据脚本。

## 已验证

- 后端单元和集成测试通过，覆盖 Engine Registry、能力校验、Zeta/Spark 状态、
  Spark Mock 成功/失败/停止、双格式配置生成、历史 Zeta 数据迁移回填及按原 profile 恢复轮询。
- API 使用默认文件型 H2 成功启动，Flyway 初始化成功。
- 系统状态、执行配置、能力矩阵、概览、数据源列表、任务列表接口完成本机 HTTP 冒烟。
- Spark Mock 可用于本地验证多引擎页面和生命周期，但不会产生真实数据同步。
- 前端类型检查、单元测试与 Next 生产构建通过。
- 使用本机浏览器检查了概览、数据源空状态和新建任务向导。

## 尚未完成真实链路验收

本机已安装 SeaTunnel 2.3.13，并通过新版多引擎 API 验证 Zeta REST 健康检查可返回版本信息。
以下涉及真实数据写入和长任务的项目仍待专项验收：

- MySQL 源表到目标表的真实 APPEND / REPLACE 同步。
- 长任务停止、真实运行指标和服务重启后的 jobId 重关联。
- 公司 Spark 平台 API、鉴权、状态和指标映射；当前只定义 `SparkPlatformClient` 契约。

## 第一阶段边界

暂不支持 Cron、CDC、多表任务、自定义 SQL、字段改名、Upsert、增量水位、断点恢复、
Schema Evolution、任意 Connector 跨引擎兼容、登录和多租户。Flink 仅进入类型和能力展示，
不出现在可运行选项中。
