# Teaching Open 本地 Docker 运行说明

这份文档只保留本地开发最需要的内容。

目标：

- 本机只安装 `Docker Desktop`
- 前端、后端、MySQL、Redis 都跑在容器里
- 改完代码后可以快速重建
- 改了数据库 `*.sql` 后，知道应该怎么更新本地库

项目目录：

```powershell
D:\ext-dev\teaching-open
```

一键脚本：

- [dev-docker.ps1](/D:/ext-dev/teaching-open/dev-docker.ps1)

## 1. 前置条件

只需要确认两件事：

- 已安装 `Docker Desktop`
- 在 PowerShell 里可以执行 `docker` 和 `docker compose`

进入项目目录：

```powershell
cd D:\ext-dev\teaching-open
```

这套脚本现在会按下面顺序获取基础镜像：

- 先用本地已经存在的镜像
- 再尝试国内镜像地址
- 最后再回退到 Docker Hub 官方地址

如果你本机网络有特殊要求，也可以提前手动指定镜像源：

```powershell
$env:TEACHING_OPEN_API_BASE_SOURCE = 'docker.m.daocloud.io/library/maven:3.8-openjdk-8-slim'
$env:TEACHING_OPEN_API_RUNTIME_BASE_SOURCE = 'docker.m.daocloud.io/library/eclipse-temurin:8-jre-jammy'
$env:TEACHING_OPEN_WEB_BASE_SOURCE = 'docker.m.daocloud.io/library/node:16'
$env:TEACHING_OPEN_WEB_RUNTIME_BASE_SOURCE = 'docker.m.daocloud.io/library/nginx:latest'
```

## 2. 第一次完整启动

第一次建议严格按下面顺序执行。

### 第 1 步：启动数据库和 Redis

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action infra
```

### 第 2 步：等待数据库启动

```powershell
Start-Sleep -Seconds 20
```

### 第 3 步：导入数据库 SQL

先设置 MySQL `root` 密码变量：

```powershell
$DbRootPassword = '你的MySQL root密码'
```

先导入基础库：

```powershell
docker cp .\api\db\teachingopen2.8.sql teachingopen_db:/tmp/teachingopen2.8.sql
docker cp .\api\db\qrtzUpcase.sql teachingopen_db:/tmp/qrtzUpcase.sql
docker exec teachingopen_db sh -c "mysql --default-character-set=utf8mb4 -uroot -p$DbRootPassword teachingopen < /tmp/teachingopen2.8.sql"
docker exec teachingopen_db sh -c "mysql --default-character-set=utf8mb4 -uroot -p$DbRootPassword teachingopen < /tmp/qrtzUpcase.sql"
```

再按顺序导入所有升级脚本：

```powershell
$updates = @(
  '.\api\db\update2.2.sql',
  '.\api\db\update2.3.sql',
  '.\api\db\update2.4.sql',
  '.\api\db\update2.5.sql',
  '.\api\db\update2.6.sql',
  '.\api\db\update2.7.sql',
  '.\api\db\update2.8.sql',
  '.\api\db\update2.9.sql',
  '.\api\db\update3.0.sql'
)

foreach ($file in $updates) {
  $name = Split-Path $file -Leaf
  docker cp $file "teachingopen_db:/tmp/$name"
  docker exec teachingopen_db sh -c "mysql --default-character-set=utf8mb4 -uroot -p$DbRootPassword teachingopen < /tmp/$name"
}
```

### 第 4 步：构建并启动前后端

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action full
```

## 3. 访问地址

启动完成后：

- 前端首页：`http://localhost`
- 后端接口前缀：`http://localhost/api`

默认账号：

- `admin`
- `teacher`
- `student`

默认密码：

- `123456`

## 4. 日常更新怎么做

### 4.1 只改后端

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action backend
```

### 4.2 只改前端

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action frontend
```

### 4.3 前后端都改了

最简单，直接重跑完整构建：

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action full
```

## 5. 数据库 SQL 怎么维护

数据库脚本都在：

```text
api\db
```

当前这几个文件的角色要分清：

- `teachingopen2.8.sql`
  - 基础初始化脚本
  - 负责建基础表和基础数据
- `qrtzUpcase.sql`
  - Quartz 相关脚本
- `update2.2.sql` 到 `update3.0.sql`
  - 增量升级脚本
  - 按版本顺序执行

本地 Docker 的使用规则，统一按下面来：

### 规则 1：新增数据库变更时，写到最新的 `update*.sql`

比如这次功能要新增表、字段、字典、菜单、权限：

- 就写到当前最新升级文件里
- 现在就是 [update3.0.sql](/D:/ext-dev/teaching-open/api/db/update3.0.sql)

如果后面版本继续升级：

- 就新增 `update3.1.sql`、`update3.2.sql` 这类文件

### 规则 2：本地已有数据库时，只执行新增或改过的升级脚本

例如你刚改了：

- [update3.0.sql](/D:/ext-dev/teaching-open/api/db/update3.0.sql)

下面命令默认你已经先执行过：

```powershell
$DbRootPassword = '你的MySQL root密码'
```

那就在数据库容器里执行它：

```powershell
docker cp .\api\db\update3.0.sql teachingopen_db:/tmp/update3.0.sql
docker exec teachingopen_db sh -c "mysql --default-character-set=utf8mb4 -uroot -p$DbRootPassword teachingopen < /tmp/update3.0.sql"
```

执行完以后，再根据你改的是前端、后端还是两边都改，分别运行：

- 只改后端：`backend`
- 只改前端：`frontend`
- 前后端都改：`full`

### 规则 3：如果要重建一个全新的本地数据库，必须重新导入“基础脚本 + 全部升级脚本”

不能只导入 `teachingopen2.8.sql`。

正确做法是：

1. 删除旧数据库目录
2. 重新启动 `db` 和 `redis`
3. 重新导入：
   - `teachingopen2.8.sql`
   - `qrtzUpcase.sql`
   - 所有 `update*.sql`

## 6. 改了 SQL 以后，本地数据库怎么更新

分两种情况。

### 情况 A：保留当前本地数据

适合：

- 只是加字段
- 加表
- 加字典
- 加菜单权限

步骤：

1. 改对应的 `update*.sql`
2. 把这个 SQL 文件导入当前数据库
3. 重建前端、后端

示例默认你已经先执行过：

```powershell
$DbRootPassword = '你的MySQL root密码'
```

示例：

```powershell
docker cp .\api\db\update3.0.sql teachingopen_db:/tmp/update3.0.sql
docker exec teachingopen_db sh -c "mysql --default-character-set=utf8mb4 -uroot -p$DbRootPassword teachingopen < /tmp/update3.0.sql"
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action full
```

### 情况 B：不要当前本地数据，直接重置

适合：

- 表结构改动很多
- 想从头验证初始化流程
- 想确保本地库和仓库 SQL 完全一致

步骤：

1. 停掉容器
2. 删除 MySQL 数据目录
3. 重新启动基础设施
4. 重新导入全部 SQL
5. 重新构建前后端

下面命令默认你已经先执行过：

```powershell
$DbRootPassword = '你的MySQL root密码'
```

命令如下：

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action down
Remove-Item -Recurse -Force .\deploy\data\mysql
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action infra
Start-Sleep -Seconds 20
docker cp .\api\db\teachingopen2.8.sql teachingopen_db:/tmp/teachingopen2.8.sql
docker cp .\api\db\qrtzUpcase.sql teachingopen_db:/tmp/qrtzUpcase.sql
docker exec teachingopen_db sh -c "mysql --default-character-set=utf8mb4 -uroot -p$DbRootPassword teachingopen < /tmp/teachingopen2.8.sql"
docker exec teachingopen_db sh -c "mysql --default-character-set=utf8mb4 -uroot -p$DbRootPassword teachingopen < /tmp/qrtzUpcase.sql"
```

然后再执行：

```powershell
$updates = @(
  '.\api\db\update2.2.sql',
  '.\api\db\update2.3.sql',
  '.\api\db\update2.4.sql',
  '.\api\db\update2.5.sql',
  '.\api\db\update2.6.sql',
  '.\api\db\update2.7.sql',
  '.\api\db\update2.8.sql',
  '.\api\db\update2.9.sql',
  '.\api\db\update3.0.sql'
)

foreach ($file in $updates) {
  $name = Split-Path $file -Leaf
  docker cp $file "teachingopen_db:/tmp/$name"
  docker exec teachingopen_db sh -c "mysql --default-character-set=utf8mb4 -uroot -p$DbRootPassword teachingopen < /tmp/$name"
}

powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action full
```

## 7. 最常用的命令

完整重建：

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action full
```

只启动数据库和 Redis：

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action infra
```

只重建后端：

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action backend
```

只重建前端：

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action frontend
```

停止所有容器：

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action down
```

## 8. 一句话工作流

以后本地开发，按下面记就够了：

- 第一次跑：`infra` -> 导入全部 SQL -> `full`
- 只改后端：`backend`
- 只改前端：`frontend`
- 前后端都改：`full`
- 改了数据库：先执行对应 `update*.sql`，再跑 `backend` / `frontend` / `full`
- 想彻底重置数据库：删 `deploy\data\mysql`，然后重新导入全部 SQL

