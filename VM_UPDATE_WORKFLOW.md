# Teaching Open 当前 VM 服务器通用更新流程

这份文档不是讲“第一次迁移”，而是讲：

- 你的新服务器已经是 `Linux VM + Docker Compose`
- 项目代码已经在服务器上
- 后续每次功能更新、bug 修复、页面调整，应该怎么更新

适用当前服务器信息：

- SSH 地址：`siliconxu.asuscomm.com`
- SSH 端口：`8922`
- SSH 用户：`siliconxu`
- 服务器项目目录：`/opt/teaching-open`

原则只有四条：

1. 本地构建，服务器只封装运行镜像
2. 生产库只执行增量 SQL，不重新导入初始化 SQL
3. 先判断“有没有数据库变更”，再决定更新步骤
4. 更新完成后，至少做一次登录和核心页面验证

---

## 1. 先判断这次属于哪一种更新

每次更新前，先判断属于下面哪一种。

### 情况 A：只改前端

例如：

- 页面样式调整
- 表格显示修复
- 表单交互修复
- Markdown 编辑器修复

这类更新通常只需要：

- 本地重建前端
- 上传 `web/dist`
- 服务器重建 `web` 镜像
- 重启 `web`

### 情况 B：只改后端

例如：

- 接口逻辑修复
- 权限逻辑修复
- 默认值修复
- 自动判题逻辑调整

这类更新通常只需要：

- 本地重建后端
- 上传 Jar
- 服务器重建 `api` 镜像
- 重启 `api`

### 情况 C：前后端都改了

例如：

- 新功能同时改了页面和接口
- 页面字段和接口返回一起调整

这类更新通常需要：

- 本地重建前端
- 本地重建后端
- 上传 `dist` 和 Jar
- 服务器重建 `api`、`web`
- 重启 `api`、`web`

### 情况 D：包含数据库变更

例如：

- 新增表
- 新增字段
- 新增字典
- 新增菜单/权限初始化
- 修改数据库默认数据

这类更新必须额外做两件事：

1. 先备份生产数据库
2. 再执行增量 SQL

重要提醒：

- 生产环境绝对不要重新导入 `teachingopen2.8.sql`
- 生产环境绝对不要用初始化 SQL 覆盖现有库
- 只执行你本次需要的 `update*.sql`

---

## 2. 每次更新前固定先做的事

### 第 1 步：本地确认代码是最新的

在本地开发机进入项目目录：

```powershell
cd D:\ext-dev\teaching-open
```

如果你的服务器代码是通过 Git 同步的，先把本地代码提交并推送到服务器要拉取的分支。

### 第 2 步：判断是否改了下面这些文件

如果改了这些文件，服务器上除了上传构建产物，最好也同步源码：

- `api/Dockerfile`
- `web/Dockerfile`
- `deploy/docker-compose.yml`
- `deploy/.env`
- `api/db/update*.sql`
- 任何后端源码
- 任何前端源码

最稳妥的方式是：

1. 本地把源码 push 到仓库
2. 服务器上执行 `git pull`
3. 再上传本地产物

---

## 3. 本地构建流程

以下命令都在本地 Windows 开发机执行。

项目目录：

```powershell
cd D:\ext-dev\teaching-open
```

### 3.1 只改前端

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action frontend
```

构建完成后，确认：

- `web/dist` 已生成

### 3.2 只改后端

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action backend
```

构建完成后，确认：

- `api/jeecg-boot-module-system/target/teaching-open-2.8.0.jar` 已生成

### 3.3 前后端都改了

最简单直接执行：

```bash
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action full
```

或者分别执行：

```bash
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action backend
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action frontend
```

---

## 4. 上传文件到当前 VM 服务器

以下命令在本地执行。

当前服务器：

- 主机：`siliconxu.asuscomm.com`
- 端口：`8922`
- 用户：`siliconxu`

### 4.1 上传后端 Jar

```bash
scp -P 8922 api/jeecg-boot-module-system/target/teaching-open-2.8.0.jar siliconxu@siliconxu.asuscomm.com:/opt/teaching-open/api/jeecg-boot-module-system/target/
```

### 4.2 上传前端 dist 压缩包

```bash
tar -czf web-dist.tar.gz -C web dist
scp -P 8922 web-dist.tar.gz siliconxu@siliconxu.asuscomm.com:/opt/teaching-open/web/
```

### 4.3 如果这次改了 SQL 文件

如果你改了 `api/db/update*.sql`，建议把服务器源码也同步到最新：

```bash
ssh -p 8922 siliconxu@siliconxu.asuscomm.com
cd /opt/teaching-open
git pull
```

如果服务器不是通过 Git 拉代码维护，那就把对应 SQL 文件单独传上去。

---

## 5. 服务器更新流程

以下命令都在服务器上执行。

先登录服务器：

```bash
ssh -p 8922 siliconxu@siliconxu.asuscomm.com
```

进入项目目录：

```bash
cd /opt/teaching-open
```

### 5.1 如果这次包含源码更新，先同步源码

如果你服务器上的项目目录是 Git 仓库，建议先同步：

```bash
git pull
```

如果这次只上传了构建产物，且 Dockerfile、Compose、SQL 都没有变化，这一步可以跳过。

补充说明：

- 服务器上的 Docker 容器只使用本地上传后的 `jar` 和 `dist` 来构建本地镜像
- 不依赖任何业务远程镜像仓库里的 `teaching-open-api` 或 `teaching-open-web`
- 因此 `docker build` 的镜像名、`docker-compose.yml` 里的镜像名，必须保持一致

如果这次上传了前端压缩包，服务器上先解压：

```bash
cd /opt/teaching-open/web
rm -rf dist
tar -xzf web-dist.tar.gz
cd /opt/teaching-open
```

---

## 6. 如果这次有数据库变更

只有在这次真的改了数据库时，才执行这一节。

### 第 1 步：先备份当前生产数据库

示例：

```bash
docker exec teachingopen_db sh -c "mysqldump -uroot -pteachingopen --single-transaction --routines --triggers --events teachingopen > /tmp/teachingopen_backup_$(date +%F_%H%M%S).sql"
docker cp teachingopen_db:/tmp/. /opt/teaching-open/db-backup-temp
```

如果你生产库的 root 密码不是 `teachingopen`，改成真实密码。

如果你平时已经有固定备份方案，也可以用你现有的备份方式。

### 第 2 步：执行增量 SQL

示例：

```bash
docker cp /opt/teaching-open/api/db/update3.0.sql teachingopen_db:/tmp/update3.0.sql
docker exec teachingopen_db sh -c "mysql --default-character-set=utf8mb4 -uroot -pteachingopen teachingopen < /tmp/update3.0.sql"
```

说明：

- 执行前先确认这个 `update*.sql` 还没有在生产执行过
- 如果只是某个更高版本，例如 `update3.1.sql`，就替换成对应文件名

---

## 7. 构建运行镜像

### 7.1 重建后端运行镜像

当你本次包含后端改动时执行：

```bash
cd /opt/teaching-open
docker build -t teaching-open-api:latest -f api/Dockerfile api
```

### 7.2 重建前端运行镜像

当你本次包含前端改动时执行：

```bash
cd /opt/teaching-open
docker build -t teaching-open-web:latest -f web/Dockerfile web
```

### 7.3 如果你改了数据库镜像相关内容

只有你真的改了数据库初始化镜像时，才执行：

```bash
cd /opt/teaching-open
docker build -t registry.cn-shanghai.aliyuncs.com/goodat/teaching-open-db:latest -f api/Dockerfile.db api
```

一般日常更新不用这一步。

---

## 8. 重启服务

进入 `deploy` 目录：

```bash
cd /opt/teaching-open/deploy
```

### 8.1 只改前端

```bash
docker compose up -d --force-recreate --no-deps web
```

### 8.2 只改后端

```bash
docker compose up -d --force-recreate --no-deps api
```

### 8.3 前后端都改了

```bash
docker compose up -d --force-recreate api web
```

### 8.4 改了数据库结构，但只是执行了增量 SQL

通常还是只需要：

```bash
docker compose up -d --force-recreate api web
```

一般不需要重建 `db` 容器。

---

## 9. 更新后检查

每次更新完，至少检查下面这些。

### 9.1 看容器状态

```bash
cd /opt/teaching-open/deploy
docker compose ps
```

确认：

- `api` 在运行
- `web` 在运行
- `db` 在运行
- `redis` 在运行

### 9.2 看后端日志

```bash
docker compose logs -f api
```

重点看有没有：

- 数据库连接报错
- Redis 连接报错
- 七牛配置报错
- Bean 初始化报错
- SQL 执行相关报错

### 9.3 看前端日志

```bash
docker compose logs -f web
```

### 9.4 浏览器验收

至少检查：

1. 首页能打开
2. 管理员能登录
3. 本次改动对应页面是否正常
4. 图片和七牛文件是否正常
5. 后台核心菜单能打开

---

## 10. 三种最常见更新场景

### 场景 A：只改前端

本地：

```powershell
cd D:\ext-dev\teaching-open
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action frontend
tar -czf web-dist.tar.gz -C web dist
scp -P 8922 web-dist.tar.gz siliconxu@siliconxu.asuscomm.com:/opt/teaching-open/web/
```

服务器：

```bash
ssh -p 8922 siliconxu@siliconxu.asuscomm.com
cd /opt/teaching-open
git pull
cd /opt/teaching-open/web
rm -rf dist
tar -xzf web-dist.tar.gz
cd /opt/teaching-open
docker build -t teaching-open-web:latest -f web/Dockerfile web
cd /opt/teaching-open/deploy
docker compose up -d --force-recreate --no-deps web
docker compose ps
```

### 场景 B：只改后端

本地：

```powershell
cd D:\ext-dev\teaching-open
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action backend
scp -P 8922 api/jeecg-boot-module-system/target/teaching-open-2.8.0.jar siliconxu@siliconxu.asuscomm.com:/opt/teaching-open/api/jeecg-boot-module-system/target/
```

服务器：

```bash
ssh -p 8922 siliconxu@siliconxu.asuscomm.com
cd /opt/teaching-open
git pull
docker build -t teaching-open-api:latest -f api/Dockerfile api
cd /opt/teaching-open/deploy
docker compose up -d --force-recreate --no-deps api
docker compose ps
```

### 场景 C：前后端都改了

本地：

```powershell
cd D:\ext-dev\teaching-open
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action full
scp -P 8922 api/jeecg-boot-module-system/target/teaching-open-2.8.0.jar siliconxu@siliconxu.asuscomm.com:/opt/teaching-open/api/jeecg-boot-module-system/target/
tar -czf web-dist.tar.gz -C web dist
scp -P 8922 web-dist.tar.gz siliconxu@siliconxu.asuscomm.com:/opt/teaching-open/web/
```

服务器：

```bash
ssh -p 8922 siliconxu@siliconxu.asuscomm.com
cd /opt/teaching-open
git pull
cd /opt/teaching-open/web
rm -rf dist
tar -xzf web-dist.tar.gz
cd /opt/teaching-open
docker build -t teaching-open-api:latest -f api/Dockerfile api
docker build -t teaching-open-web:latest -f web/Dockerfile web
cd /opt/teaching-open/deploy
docker compose up -d --force-recreate api web
docker compose ps
```

### 场景 D：包含数据库变更

本地：

1. 先本地构建
2. 把本次需要执行的 `update*.sql` 同步到服务器

服务器：

```bash
ssh -p 8922 siliconxu@siliconxu.asuscomm.com
cd /opt/teaching-open
git pull
docker cp /opt/teaching-open/api/db/update3.0.sql teachingopen_db:/tmp/update3.0.sql
docker exec teachingopen_db sh -c "mysql --default-character-set=utf8mb4 -uroot -pDragon#1 teachingopen < /tmp/update3.0.sql"
docker build -t teaching-open-api:latest -f api/Dockerfile api
docker build -t teaching-open-web:latest -f web/Dockerfile web
cd /opt/teaching-open/deploy
docker compose up -d --force-recreate api web
docker compose ps
```

注意：

- 这里的 `update3.0.sql` 只是示例
- 后续请替换成你本次实际要执行的升级脚本

---

## 11. 不要这样更新

下面这些是后续更新时不要做的：

- 不要往生产库重新导入 `teachingopen2.8.sql`
- 不要把整个生产库删了再导初始化 SQL
- 不要没备份就直接执行升级 SQL
- 不要在服务器上直接跑重型 `mvn clean package`
- 不要把 `jar`、`dist` 当源码提交进 Git
- 不要改了 `deploy/.env` 却忘记备份旧文件
- 不要让 `docker build` 的镜像名和 `docker-compose.yml` 里的 `image` 不一致

---

## 12. 一句话记忆版

以后更新，按这个记就够了：

- 只改前端：本地 `frontend` -> 上传 `web/dist` -> 服务器重建 `web` -> 重启 `web`
- 只改后端：本地 `backend` -> 上传 Jar -> 服务器重建 `api` -> 重启 `api`
- 前后端都改：本地 `full` -> 上传 Jar 和 `dist` -> 服务器重建 `api`、`web` -> 重启
- 改了数据库：先备份生产库 -> 执行增量 SQL -> 再重建并重启 `api`、`web`
