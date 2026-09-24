# 本机真实实验记录 · 2026-09-24

环境：本机 SeaTunnel 2.3.13 + Zeta、JDBC connector 2.3.13、MySQL Connector/J 8.4.0、MySQL 9.3.0。所有下列数据写入仅限 `golem_lab_source/golem_lab_target`，原演示数据和任务保留。

每个运行可在 `/lab/runs/<ID>` 查看不可变配置、预检、实际 jobId、指标和事件；平台文件数据库与本机引擎日志属于运行数据，不入 Git。本文件保存关键证据与复现步骤，不保存密码。

## 1. 参数与配置

- 当前安装包重新生成参数目录，与仓库 `catalog-2.3.13.json` 的 `cmp` 完全一致：57 个组、376 项（含复用组），20 个 Transform Factory。
- 目录来自 Options/Factory.optionRule 和嵌套类；参数来源追溯到 2.3.13 固定源码，默认值不是实际运行生效值。
- 浏览器创建“浏览器验收 · 分支与 JSON 往返”，加入未知 `env.future_test={nested:[1,{keep:true}]}`，JSON→表单将 parallelism 1 改 2→JSON 后嵌套值保留，预检提示未验证；凭据显示引用或掩码。
- 浏览器提交运行 `d14c4ba7-1618-4675-bc2c-35bc2cb08bf3`，external jobId `2239938454168422694`，成功 150 条；三个独立分支目标 branch_customers/products/orders 为 20/10/120。
- 对实验、历史运行、日志、指标、集群及目录响应扫描，未出现实验账号密码。加密快照、秘密复制、敏感字面量拒绝、未知字段保留由测试覆盖。

## 2. Transform 与多表

| 实验 | 平台运行 ID | 实际结果 |
|---|---|---|
| 字段选择→改名→Sql 过滤→派生金额 | `c39e4581-b7c0-4f4c-b8a3-d41e74a832a1` | 120 读、80 写/提交，目标 orders_transformed 80 |
| JDBC table_list + `${table_name}` 路由 | `db979860-6364-429a-a9c8-b584c7404a39` | 一次真实作业同步 customers/products/orders 20/10/120 |
| 错误字段表达式 | `9a3b63af-8ffe-4fb8-bf4f-ccfde7800bff` | 引擎 HTTP 400 明确拒绝，平台 FAILED |
| JOIN 表别名语法 | `1e74f2a8-23e0-4ec3-9725-ad5e8c2892cb` | 引擎 HTTP 500 + status:fail，Unsupported table alias name syntax，平台 FAILED |
| 多表部分失败：partial_products 缺少 stock | `9dfa7999-3406-4a08-9cc0-64a162f59a1d` | 作业 FAILED；partial_customers 20、partial_products 0、partial_orders 0 |

Transform 使用 `FieldRename.specific=[{fieldName:"amount",targetName:"order_amount"}]`；最初使用错误 from/to 键的失败记录 `90565687-46fa-4dc5-b925-c8c47a72a0aa` 保留。Factory 名称为 `Sql`，SQL 从上一节点名称读取，不是直接发给 MySQL 的源查询。

金额实测：自动建出的 order_amount 与 taxed_amount 均为 DECIMAL(18,2)。`7.25*1.10` 落库为 `7.98`；80 条与 MySQL `ROUND(amount*1.10,2)` 逐条核对为 0 差异。这记录的是当前转换和目标精度行为，不宣称保留四位小数。

多表失败时平台保留引擎真实累计指标（本例重试后读取/写入 600，提交 0），这不代表 600 条唯一数据，更不保证其他表全部回滚。预检只做只读元数据/静态检查，转换后的字段兼容性与 SQL 支持由实际引擎验证。

## 3. Checkpoint / Savepoint / 恢复

复现：使用 checkpoint 或 XA 模板，源 perf_orders 100000，显式 checkpoint.interval=3000、checkpoint.timeout=60000、read_limit.rows_per_second=1000；Sink 使用主键 id、APPEND_DATA + Upsert。等至少一次 Checkpoint COMPLETED 后点击“保存点停止”，等 SAVED 后恢复。

| 配置 | 保存点运行 | 恢复运行 | 外部 jobId | 结果 |
|---|---|---|---|---|
| 普通 JDBC | `b73b37c2-46af-4087-94b3-c438f30c34b3` | `73539c9d-49c9-4a85-87b4-a01640271015` | `5295478521781568823` | Checkpoint 1 / Savepoint 3 COMPLETED；恢复成功 |
| XA、尚缺 XA_RECOVER_ADMIN | `85bd5f8e-ec34-4077-afc3-54c780e768f2` | `339bcb58-5fef-487b-82f5-3b7f95ca2ce6` | `4801859663298446329` | 保存点成功，恢复 XAER_RMERR 失败 |
| XA、用户授权补齐权限 | `f01fdebd-21eb-4f31-9103-3e924a6aedd2` | `17d9838a-a159-4b2b-b61d-4163c7080ab3` | `4331834112104219552` | Checkpoint / Savepoint 3 COMPLETED；恢复成功 |

- 成功恢复的 perf_checkpoint 和 perf_xa_verified 均为 **100000 行**。以 id 连接，检查 customer_id、amount、status、payload、created_at，缺失/字段差异为 **0**；目标行数同时等于源行数，无额外行。
- 恢复新建平台运行记录，复用原 external jobId 与加密快照；原记录继续显示“保存点暂停”，未改成“正常完成”。
- 未把普通 JDBC/一次 XA 实验宣称为完整 exactly-once 保证。普通 JDBC 可能重放，当前主键 Upsert使重放不增加重复主键。
- 用户明确授权后仅对本次专用账号授予持久全局 XA_RECOVER_ADMIN；失败历史保留，便于比较。
- 自动测试覆盖缺失 Checkpoint、物理保存点不存在、配置被修改、状态不可确认、停止请求失败、旧 SAVEPOINT_DONE 延迟响应；这些场景不自动全量重跑。

## 4. 批量结构变化

源 schema_probe 初始为 id BIGINT、name VARCHAR(80)、amount DECIMAL(18,2)，2 行。步骤：基线重建 → 新增 note VARCHAR → 保留/重建目标各跑一次 → 删除 note、amount 改 VARCHAR(40) → 保留/重建各跑一次。

| 步骤 / schema_save_mode | 运行 ID | 结果 |
|---|---|---|
| 基线 RECREATE_SCHEMA | `aacbd37f-c1de-4127-9677-7ecf05eb4896` | 成功 2 |
| 新增字段 CREATE_SCHEMA_WHEN_NOT_EXIST | `83a50965-4b47-496c-a3fe-67281ff47af0` | 失败：已有目标缺字段 |
| 新增字段 RECREATE_SCHEMA | `ca5f6641-f69e-4907-b66c-1975823c9114` | 成功 2 |
| 删字段、改类型 CREATE_SCHEMA_WHEN_NOT_EXIST | `0a4569b4-fe8e-4496-8e8c-223694fbab90` | 成功 2，兼容数据可隐式转换；不代表目标结构自动变化 |
| 删字段、改类型 RECREATE_SCHEMA | `6dbe4523-52cb-4c8c-9758-ae87e62a3f1f` | 成功 2，目标跟随新结构 |

浏览器比较基线与末次运行，amount 显示 DECIMAL/18/2 → VARCHAR/40/null。当前源/目标 schema_probe 保留最后实验状态；准备脚本 `--reset` 可恢复源初始结构。所有 DDL 在两次批量运行之间执行，未测试/宣称运行中自动 DDL 演进。

## 5. 并行与性能

固定源 perf_orders 100000 行、200 字符 payload，目标 perf_performance 每次 DROP_DATA。以下是人工选定配置的单次测量，不是自动矩阵或统计基准；平台耗时含提交与最多约 2 秒轮询延迟，QPS 是引擎最后返回值。

| parallelism | batch_size | split.size | 平台耗时 | 最后 SourceReceivedQPS | 运行 ID |
|---:|---:|---:|---:|---:|---|
| 1 | 1000 | 10000 | 3.8 s | 33875 | `47fcb22f-f4ce-4740-9393-374542f644a9` |
| 2 | 1000 | 10000 | 3.6 s | 39517 | `4a334150-50b4-4f25-896c-14f5516b7f62` |
| 4 | 1000 | 10000 | 3.5 s | 50058 | `a0e6361c-4b35-4048-8ad3-8ddc1e01603d` |
| 4 | 100 | 10000 | 3.4 s | 43519 | `26496d5c-736b-40bd-b609-c08e03114097` |
| 4 | 1000 | 25000 | 3.2 s | 50487 | `6f3d3d5d-c89f-4429-bc26-36bf04c790b4` |

实际引擎 DAG envOptions 返回 parallelism=1/2/4。引擎日志显示 table_path 模式使用 dynamic chunk splitter，split.size=10000 对应 10 splits；25000 对应 4 splits。因为基于近似表统计，实际动态 chunk size 可能与显式 split.size 不相等（本次 10000 推导 10859），不能将配置值直接当成精确分片行数。

另跑 query 模式：`cfaa7213-51db-41be-ab6b-6cae3235639b`，jobId `5282618363307183895`。`SELECT * FROM golem_lab_source.perf_orders`，partition_column=id、partition_lower_bound="1"、partition_upper_bound="100000"、partition_num=4、parallelism=4；成功 100000 条，日志切换 fixed chunk splitter，明确返回 4 splits，并注册 reader 0/1/2/3。边界类型必须是字符串，最初整数配置被平台拒绝。目标 perf_query_partition 逐字段差异 0。

perf_performance 最后一次目标 100000 条、逐字段差异 0。不同 batch_size 被保存为显式参数；本机接口没有返回其内部最终批大小，未将其标成已验证的实际生效值。100 万条准备能力已提供，本次未进行百万规模性能测量。

## 6. 重启、取消与回归

- 活动作业中重启 API：`b493dd88-db53-4448-a59a-f862016acf5b`。RECONCILING 事件存在，外部 jobId 未变且未重复提交，最终成功 100000。perf_restart 逐字段差异 0。
- 取消：`3f905fd9-49e5-4d5c-975c-3f0d9a79de1f` 最终 CANCELED；与 SAVED 独立。
- 后端 `./mvnw test` **53 项通过**；前端 `pnpm exec tsc --noEmit`、`pnpm test` **28 项通过**、`pnpm build` 通过。
- 浏览器验收：模板创建、JSON/表单往返、未知字段保留、提交预览、真实多分支提交、DAG/分表指标/资源、选择三次性能对比与两次结构对比、嵌套参数搜索和本机集群；轮询短暂断开后提示清除由回归测试覆盖。
- 原演示源库 customers/products/orders 仍为 **20/10/120**，目标 **0/0/120**；原单表任务配置未修改。历史真正 Zeta orders 同步运行 `3160ff03-2a17-46d5-94d4-86066fe57761` 保留，最新 Spark Mock 指标不代表 MySQL 数据量。
