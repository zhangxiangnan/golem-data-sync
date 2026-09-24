import type { LabConfig, Json, NodeConfig } from "./lab-api";
export const ACTIVE=new Set(["SUBMITTING","PENDING","RUNNING","SAVING","CANCELING","UNKNOWN"]);
export function parseConfig(text:string):LabConfig {
  const value=JSON.parse(text);
  if(!value||typeof value!=="object"||Array.isArray(value)||!value.env||typeof value.env!=="object"||Array.isArray(value.env)||!["source","transform","sink"].every(k=>Array.isArray(value[k])&&value[k].every((n:unknown)=>n&&typeof n==="object"&&!Array.isArray(n))))throw new Error("需要 env 对象和 source / transform / sink 节点数组");
  return value;
}
export function patchNode(config:LabConfig,section:"source"|"transform"|"sink",index:number,patch:Record<string,Json>):LabConfig {
  return {...config,[section]:config[section].map((node,i)=>i===index?{...node,...patch}:node)};
}
export function reindexBindings(config:LabConfig,bindings:Record<string,string>):LabConfig {
  const result=structuredClone(config);
  for(const section of ["source","sink"] as const)result[section].forEach((node,i)=>{
    const key=`${section}/${i}`;
    for(const field of ["url","username","password","driver",...(section==="sink"?["database"]:[])]) {
      if(bindings[key])node[field]=`\${datasource:${key}:${field}}`;
      else if(typeof node[field]==="string"&&String(node[field]).startsWith("${datasource:"))delete node[field];
    }
  });return result;
}
export function moveNode(config:LabConfig,bindings:Record<string,string>,section:"source"|"transform"|"sink",from:number,to:number|null) {
  const order=config[section].map((_,i)=>i);const [removed]=order.splice(from,1);if(to!==null)order.splice(to,0,removed);
  const result={...config,[section]:order.map(i=>config[section][i])};const next={...bindings};
  Object.keys(next).filter(k=>k.startsWith(`${section}/`)).forEach(k=>delete next[k]);
  order.forEach((old,i)=>{if(bindings[`${section}/${old}`])next[`${section}/${i}`]=bindings[`${section}/${old}`];});
  return {config:reindexBindings(result,next),bindings:next};
}
export function secretProblem(value:Json):boolean {
  if(Array.isArray(value))return value.some(secretProblem);
  if(value&&typeof value==="object")return Object.entries(value).some(([key,v])=>/(password|passwd|pwd|authorization|private[_-]?key|secret|api[_-]?key|access[_-]?key|token|credential)/i.test(key)&&v!==null&&!(typeof v==="string"&&/^\$\{(secret:[\w-]+|datasource:[^}]+)\}$/.test(v))||secretProblem(v));
  return typeof value==="string"&&/(password|passwd|pwd|secret|api_key|token)\s*[=:]\s*[^\s]+/i.test(value)&&!/^\$\{secret:[\w-]+\}$/.test(value);
}
export function flatten(value:Json,prefix=""):Record<string,string> {
  if(value!==null&&typeof value==="object")return Object.fromEntries(Object.entries(value).flatMap(([k,v])=>Object.entries(flatten(v,`${prefix}/${k}`))));
  return {[prefix]:JSON.stringify(value)};
}
export const templateNames={basic:"单表 / 基线",transform:"多步转换：选择、改名、过滤、派生",multi:"原生 table_list 多表",branches:"多条独立分支",checkpoint:"限速 Checkpoint / 保存点",xa:"XA Checkpoint / 保存点",schema:"批量结构变化",performance:"10 万行性能测试"};
export type Template=keyof typeof templateNames;
export function template(kind:Template,sourceDatabase="golem_lab_source",targetDatabase="golem_lab_target"):LabConfig {
  const source:NodeConfig={plugin_name:"Jdbc",plugin_output:"orders",table_path:`${sourceDatabase}.orders`};
  const sink:NodeConfig={plugin_name:"Jdbc",plugin_input:["orders"],table:"orders",generate_sink_sql:true,schema_save_mode:"CREATE_SCHEMA_WHEN_NOT_EXIST",data_save_mode:"DROP_DATA",batch_size:1000,enable_upsert:true,primary_keys:["id"]};
  const config:LabConfig={env:{"job.mode":"BATCH",parallelism:1},source:[source],transform:[],sink:[sink]};
  if(kind==="transform"){
    config.transform=[
      {plugin_name:"Filter",plugin_input:["orders"],plugin_output:"selected",include_fields:["id","customer_id","amount","status"]},
      {plugin_name:"FieldRename",plugin_input:["selected"],plugin_output:"renamed",specific:[{fieldName:"amount",targetName:"order_amount"}]},
      {plugin_name:"Sql",plugin_input:["renamed"],plugin_output:"filtered",query:"select id, customer_id, order_amount, status from renamed where status = 'PAID'"},
      {plugin_name:"Sql",plugin_input:["filtered"],plugin_output:"derived",query:"select id, customer_id, order_amount, order_amount * 1.10 as taxed_amount, status from filtered"},
    ];sink.plugin_input=["derived"];sink.table="orders_transformed";
  }
  if(kind==="multi") {delete source.table_path;source.table_list=["customers","products","orders"].map(t=>({table_path:`${sourceDatabase}.${t}`}));sink.table="${table_name}";delete sink.primary_keys;}
  if(kind==="branches") {config.source=["customers","products","orders"].map(t=>({...source,table_path:`${sourceDatabase}.${t}`,plugin_output:t}));config.sink=["customers","products","orders"].map(t=>({...sink,plugin_input:[t],table:`branch_${t}`}));}
  if(["checkpoint","xa","performance"].includes(kind)){source.table_path=`${sourceDatabase}.perf_orders`;source["split.size"]=10000;sink.table=`perf_${kind}`;config.env["checkpoint.interval"]=3000;config.env["checkpoint.timeout"]=60000;if(kind!=="performance"){config.env["read_limit.rows_per_second"]=1000;sink.data_save_mode="APPEND_DATA";}}
  if(kind==="xa"){sink.is_exactly_once=true;sink.xa_data_source_class_name="com.mysql.cj.jdbc.MysqlXADataSource";sink.max_commit_attempts=3;sink.transaction_timeout_sec=300;sink.data_save_mode="APPEND_DATA";}
  if(kind==="schema"){source.table_path=`${sourceDatabase}.schema_probe`;sink.table="schema_probe";sink.schema_save_mode="RECREATE_SCHEMA";}
  // database is supplied only by the binding; this argument documents the selected target in the caller.
  void targetDatabase;return config;
}

export function schemaMap(schemas:Json[]):Record<string,string> {
  const result:Record<string,string>={};
  for(const table of schemas)if(table&&typeof table==="object"&&!Array.isArray(table)&&Array.isArray(table.columns)){
    for(const column of table.columns)if(column&&typeof column==="object"&&!Array.isArray(column)){
      result[`${table.node}/${table.table}/${column.name}`]=JSON.stringify({type:column.typeName,size:column.size,scale:column.scale,nullable:column.nullable,primaryKey:column.primaryKey});
    }
  }return result;
}
