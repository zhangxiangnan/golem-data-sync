# SeaTunnel 实验台

入口：<http://127.0.0.1:3200/lab>。仅使用本机 **SeaTunnel 2.3.13 + Zeta** 真实执行；旧单表向导、Spark Mock 和只读浏览保持独立。

## 使用

1. 在数据源中配置专用 `golem_lab_source` 和 `golem_lab_target`，准备数据：

   ```bash
   python3 scripts/prepare-lab-data.py --user YOUR_LAB_USER --rows 100000
   # 需要恢复源库固定测试数据和 schema_probe 初始结构时显式加 --reset
   # 可选 --rows 1000000；不会清空目标库，也不会访问 golem_sync_* 演示库
   ```

   密码交互输入，或从进程环境 `MYSQL_PWD` 提供。重复执行默认 INSERT IGNORE；已有源数据与已演进结构保留。`--reset` 只清空专用源库固定测试表并重建 schema_probe。
2. 新建实验，选源库、目标库和模板。模板包含基础同步、字段选择→改名→SQL 过滤→派生字段、原生 table_list、多分支、Checkpoint、XA、批量结构变化与性能读取。
3. 表单直接修改同一 JSON 树；完整节点参数和原生 JSON 保留未识别参数、嵌套对象、数组。语法错误时禁止保存或切回表单。参数目录可搜索原始键名、类型和说明。
4. 点击“保存并预检”，核对源表、目标表和写入策略及脱敏配置，再提交真实运行。预检只做静态校验、连接和源元数据读取；SQL 表达式及转换后目标兼容性由真实引擎确定，不套用旧单表字段同名规则。
5. 在运行页查看配置 DAG、引擎返回的实际 DAG、总量/分表指标、吞吐、进程资源、Checkpoint、事件和脱敏日志。运行对比手动选择 2～4 次记录。

`DROP_DATA` 会清理配置的目标表；`RECREATE_SCHEMA` 会重建目标表。这些策略在模板、JSON 和提交预览中均可见。建议只绑定专用实验库。

## 配置、凭据与参数依据

- 唯一配置源为 `env / source[] / transform[] / sink[]` 原生 JSON。绑定键为 `source/0`、`sink/0` 等节点位置；表单移动/删除节点同时调整绑定。
- JDBC url/user/password/driver 以及 Sink database 由数据源注入，编辑器使用 `${datasource:source/0:password}` 等引用。附加敏感参数使用 `${secret:name}`；秘密只写入加密存储，复制实验保留秘密，导出只含引用。导出文件不包含秘密值，跨环境导入后需重新绑定及填写秘密。
- 每次运行保存不可变的加密执行快照、脱敏配置、SHA-256、绑定、源结构预检快照、引擎版本和外部 jobId。编辑实验不改历史。删除实验采用归档，历史运行继续可查；数据源删除检查实验和历史绑定。
- 参数目录由本机 Options、Factory.optionRule、嵌套配置类反射生成，当前 **57 个分组、376 项条目（含分组复用项）**，覆盖 JDBC Source/Sink、20 个已安装 Transform、公共节点与 EnvCommonOptions。包含原始类型、默认值、必填/互斥条件、枚举、版本和固定版本源码链接。
- 元数据中的字段布局由具体插件解析，独立 `metadata.*` 组避免误当作节点根参数。未识别项保留并提示未验证，不能以此保证该键被引擎使用。
- “定义默认值”“用户显式配置”“引擎实际 DAG/响应”分开显示。平台已验证标识来自真实成功运行，只证明对应运行里的组合；LLM/Embedding 等仍需要额外服务、密钥及网络条件。

重新核对安装包并生成目录（隔离 JVM，不给后端增加依赖）：

```bash
ST_HOME=.runtime/apache-seatunnel-2.3.13
java -cp "$ST_HOME/starter/seatunnel-starter.jar:$ST_HOME/connectors/*:$ST_HOME/lib/*" \
  scripts/ExtractLabCatalog.java "$ST_HOME" /tmp/catalog-2.3.13.json
cmp src/main/resources/lab/catalog-2.3.13.json /tmp/catalog-2.3.13.json
```

依据：[SQL Transform](https://seatunnel.apache.org/docs/2.3.13/transforms/sql/)、[JDBC Source](https://seatunnel.apache.org/docs/2.3.13/connectors/source/Jdbc/)、[JDBC Sink](https://seatunnel.apache.org/docs/2.3.13/connectors/sink/Jdbc/)、[2.3.13 源码](https://github.com/apache/seatunnel/tree/2.3.13)、[Schema Evolution](https://seatunnel.apache.org/docs/2.3.13/introduction/configuration/schema-evolution/)。

## 保存点与恢复

- 同一实验同时只允许一个活动运行。提交超时/结果不确定保留 UNKNOWN 和预分配外部 jobId，持续核对，不重试提交。引擎明确拒绝（包括 HTTP 500 + status:fail）记录失败。
- “取消”不创建保存点；“保存点停止”要求显式配置 checkpoint.interval。SAVING 是创建中，SAVED 对应 SAVEPOINT_DONE，**不是全量成功**。
- 恢复必须原运行已暂停、实验无活动运行、配置/绑定/秘密版本未变、引擎仍确认 SAVEPOINT_DONE、成功保存点元数据和本机存储文件均存在。
- 恢复新增平台运行，关联原记录，复用原加密执行快照与 SeaTunnel 2.3.13 要求的原 external jobId。不修改拓扑或参数，不在保存点缺失时全量重跑。旧记录被冻结；恢复初始化时忽略引擎短暂返回的旧 SAVEPOINT_DONE。
- 当前文件存在性检查针对本机 `file:` Checkpoint 存储；其他存储位置不可确认时明确拒绝恢复。平台重启只按原 jobId 接回轮询并记录 RECONCILING，不重新提交。
- 普通 JDBC 的 Checkpoint 不等于 exactly-once。本机普通 JDBC 使用主键 Upsert，恢复可能重放。XA 已验证保存点停止和恢复，但不把一次实验提升为所有故障场景的 exactly-once 保证。
- 2026-09-24 经用户明确授权，为专用账号 `golem_lab_20260924@localhost` 授予持久的全局 `XA_RECOVER_ADMIN`，用于 XA 恢复。该账号的数据读写授权限定两个实验库；XA 恢复查看权限属于实例级。

## 观察与边界

- 采样周期 2 秒。未返回的指标显示不可用，不补零；重试后的累计读取/写入量可能超过唯一数据行数。资源来自 Zeta 进程，多作业共享，不属于某次运行独占用量。
- 曲线横轴按首次采样后的统一秒数对齐。平台耗时包含提交/轮询延迟；短作业通常仅 1～2 个采样点，不能当成严谨性能基准。
- 日志读取当前引擎日志文件最后 2 MiB 中匹配 jobId 的最多 500 行，做凭据脱敏；不是全量日志归档。恢复复用 jobId，日志可能包含同一外部作业较早执行的行。事件、指标与配置快照独立持久保存。
- 本机集群页展示磁盘配置、安装 JAR、Checkpoint 存储、Slot/JVM 配置和可用运行响应；磁盘配置不能证明进程已加载，页面标注重启要求。
- SQL Transform 不是 JDBC 源 SQL。复杂 JOIN/聚合不能假定支持。`table_path/table_list` 的动态分片使用 `split.*`，`query` 模式使用 `partition_*`；不要仅按 JSON 已填写就声称生效。
- 批量结构变化是两次运行之间的 DDL 实验；不支持运行中 CDC DDL 演进。目标表存在时 CREATE_SCHEMA_WHEN_NOT_EXIST 不会自动按新增字段修表。
- 本期没有 CDC、真实 Spark/Flink、高级 SQL 编辑执行台或自动参数矩阵。原单表向导和数据浏览继续使用原有存储及规则。

## 接口与存储

`/api/lab` 提供 catalog、cluster、experiments CRUD/copy/validate/runs、runs 列表/detail/metrics/logs/cancel/savepoint/restore、compare（ids 2～4）。校验响应包含脱敏提交预览；所有运行仅走现有 Zeta 客户端，错误遵循既有 API 格式。

Flyway V3 新增实验、实验绑定、加密秘密、运行、历史绑定、事件和指标表；不改变旧单表任务存储。后端依赖、前端依赖未增加。实测与复现链接见 [实验记录](LAB-EXPERIMENTS.md)。
