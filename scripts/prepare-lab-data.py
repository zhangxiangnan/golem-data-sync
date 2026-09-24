#!/usr/bin/env python3
"""Prepare only golem_lab_* databases. Uses MYSQL_PWD or prompts, never stores credentials.
python3 scripts/prepare-lab-data.py --rows 100000 --reset
Use --rows 1000000 for a larger manual performance experiment.
"""
import argparse
import getpass
import os
import shutil
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--rows', type=int, choices=[100000, 1000000], default=100000)
parser.add_argument('--host', default='127.0.0.1')
parser.add_argument('--port', type=int, default=3306)
parser.add_argument('--user', default='root')
parser.add_argument('--reset', action='store_true', help='Reset ONLY the lab source fixture tables; target data remains untouched')
args = parser.parse_args()
env = os.environ.copy()
if 'MYSQL_PWD' not in env:
    env['MYSQL_PWD'] = getpass.getpass('MySQL password: ')
mysql = shutil.which('mysql') or '/opt/homebrew/bin/mysql'
sql = '''
CREATE DATABASE IF NOT EXISTS golem_lab_source CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS golem_lab_target CHARACTER SET utf8mb4;
CREATE TABLE IF NOT EXISTS golem_lab_source.customers (id BIGINT PRIMARY KEY,name VARCHAR(80) NOT NULL,email VARCHAR(120),created_at DATETIME NOT NULL);
CREATE TABLE IF NOT EXISTS golem_lab_source.products (id BIGINT PRIMARY KEY,name VARCHAR(80) NOT NULL,price DECIMAL(18,2) NOT NULL,stock INT NOT NULL);
CREATE TABLE IF NOT EXISTS golem_lab_source.orders (id BIGINT PRIMARY KEY,customer_id BIGINT NOT NULL,product_id BIGINT NOT NULL,amount DECIMAL(18,2) NOT NULL,status VARCHAR(20) NOT NULL,note TEXT,created_at DATETIME NOT NULL);
CREATE TABLE IF NOT EXISTS golem_lab_source.perf_orders (id BIGINT PRIMARY KEY,customer_id BIGINT NOT NULL,amount DECIMAL(18,2) NOT NULL,status VARCHAR(20) NOT NULL,payload VARCHAR(240),created_at DATETIME NOT NULL);
CREATE TABLE IF NOT EXISTS golem_lab_source.schema_probe (id BIGINT PRIMARY KEY,name VARCHAR(80),amount DECIMAL(18,2));
'''
if args.reset:
    sql += 'DROP TABLE golem_lab_source.schema_probe; CREATE TABLE golem_lab_source.schema_probe (id BIGINT PRIMARY KEY,name VARCHAR(80),amount DECIMAL(18,2));\n'
    for table in ['orders', 'products', 'customers', 'perf_orders']:
        sql += f'TRUNCATE TABLE golem_lab_source.{table};\n'
# Deterministic fixture values; INSERT IGNORE makes preparation repeatable without touching existing rows.
digits = '(SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)'
sequence = '(SELECT 1+a.n+10*b.n+100*c.n+1000*d.n+10000*e.n+100000*f.n AS n FROM ' + ' CROSS JOIN '.join(digits+' '+alias for alias in 'abcdef') + ') seq'
sql += f"INSERT IGNORE INTO golem_lab_source.customers SELECT n,CONCAT('Customer ',n),IF(MOD(n,5)=0,NULL,CONCAT('customer',n,'@example.test')),'2026-01-01 10:00:00' FROM {sequence} WHERE n<=20;\n"
sql += f"INSERT IGNORE INTO golem_lab_source.products SELECT n,CONCAT('Product ',n),CAST(n*12.34 AS DECIMAL(18,2)),n*10 FROM {sequence} WHERE n<=10;\n"
sql += f"INSERT IGNORE INTO golem_lab_source.orders SELECT n,MOD(n-1,20)+1,MOD(n-1,10)+1,CAST(n*7.25 AS DECIMAL(18,2)),IF(MOD(n,3)=0,'NEW','PAID'),IF(MOD(n,7)=0,NULL,IF(MOD(n,11)=0,'',CONCAT('Order ',n))),'2026-01-02 12:00:00' FROM {sequence} WHERE n<=120;\n"
sql += f"INSERT IGNORE INTO golem_lab_source.perf_orders SELECT n,MOD(n-1,20)+1,CAST(n*0.37 AS DECIMAL(18,2)),IF(MOD(n,3)=0,'NEW','PAID'),RPAD(CONCAT('payload-',n),200,'x'),'2026-01-03 12:00:00' FROM {sequence} WHERE n<={args.rows};\n"
sql += "INSERT IGNORE INTO golem_lab_source.schema_probe (id,name,amount) VALUES (1,'Alpha',12.34),(2,'Beta',56.78);\n"
sql += 'SELECT COUNT(*) AS prepared_performance_rows FROM golem_lab_source.perf_orders;'
result = subprocess.run([mysql,'--no-defaults',f'--host={args.host}',f'--port={args.port}',f'--user={args.user}','--batch'],input=sql,text=True,env=env,capture_output=True)
if result.returncode:
    # MySQL messages may quote SQL. Never echo connection credentials or full SQL.
    raise SystemExit('Fixture preparation failed; verify the account privileges and lab schema. Exit '+str(result.returncode))
print(result.stdout.strip())
