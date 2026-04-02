# Teaching Open 本地隔离部署说明

本文档记录在 Windows + Docker Desktop 环境下，使用尽量隔离的方式本地运行 `Teaching Open` 的完整步骤。

目标：

- 本机只安装 `Docker Desktop`
- 不安装 `Java`、`Maven`、`Node.js`、`MySQL`、`Redis`、`IDEA`
- 数据库、缓存、前端、后端、构建过程都在容器中完成
- 修改代码后可以重新构建并测试

适用目录：

```powershell
D:\ext-dev\teaching-open
```

仓库内已提供一键脚本：

- [dev-docker.ps1](/D:/ext-dev/teaching-open/dev-docker.ps1)

如果你想直接用脚本而不是手动执行命令，可以优先看本文档的“快速方式”。

## 0. 快速方式

打开 `PowerShell`，进入项目根目录：

```powershell
cd D:\ext-dev\teaching-open
```

第一次完整构建并启动：

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action full
```

只启动数据库和 Redis：

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action infra
```

只重建后端并重启后端容器：

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action backend
```

只重建前端并重启前端容器：

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action frontend
```

只启动应用容器：

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action up
```

停止所有服务：

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-docker.ps1 -Action down
```

脚本动作说明：

- `full`：启动 `db/redis`，构建后端，构建前端，启动 `api/web`
- `infra`：只启动 `db/redis`
- `backend`：只重建后端并重启 `api`
- `frontend`：只重建前端并重启 `web`
- `up`：只启动 `api/web`
- `down`：停止所有服务

## 1. 前置条件

- 已安装 `Docker Desktop`
- 能正常执行 `docker` 和 `docker compose`
- 当前仓库目录为 `D:\ext-dev\teaching-open`

说明：

- 本项目已经调整为支持通过 `BASE_IMAGE` 指定基础镜像
- 这样可以优先使用你本地已经 `pull` 下来的镜像，减少构建时再次访问远端仓库的概率

## 2. 启动数据库和 Redis

进入项目根目录：

```powershell
cd D:\ext-dev\teaching-open
```

启动 `db` 和 `redis`：

```powershell
docker compose -f .\deploy\docker-compose.yml up -d db redis
```

说明：

- 第一次启动时，MySQL 会自动初始化数据库
- 数据会保存在 `deploy\data\mysql` 和 `deploy\data\redis`

## 3. 构建后端编译镜像

### 3.1 拉取 Maven + JDK 8 基础镜像

```powershell
docker pull maven:3.8-openjdk-8-slim
```

### 3.2 给基础镜像打本地别名

```powershell
docker tag maven:3.8-openjdk-8-slim teaching-open-local-maven8:latest
```

### 3.3 构建后端编译环境镜像

```powershell
docker build `
  --build-arg BASE_IMAGE=teaching-open-local-maven8:latest `
  -t teaching-open-api-builder `
  -f .\api\Dockerfile.builder .\api
```

## 4. 编译后端源码

```powershell
docker run --rm -v "D:\ext-dev\teaching-open\api:/workspace" -w /workspace teaching-open-api-builder bash -lc "mvn clean package"
```

编译成功后会生成：

```text
api\jeecg-boot-module-system\target\teaching-open-2.8.0.jar
```

## 5. 构建后端运行镜像

### 5.1 拉取 Java 8 运行时镜像

`openjdk:8` 已不可用，改用 `eclipse-temurin:8-jre-jammy`。

```powershell
docker pull eclipse-temurin:8-jre-jammy
```

### 5.2 给基础镜像打本地别名

```powershell
docker tag eclipse-temurin:8-jre-jammy teaching-open-local-openjdk8:latest
```

### 5.3 构建后端运行镜像

```powershell
docker build `
  --build-arg BASE_IMAGE=teaching-open-local-openjdk8:latest `
  -t registry.cn-shanghai.aliyuncs.com/goodat/teaching-open-api:latest `
  -f .\api\Dockerfile .\api
```

说明：

- 构建时如果看到 `JSONArgsRecommended` 警告，可以暂时忽略
- 只要最终显示构建成功即可

## 6. 构建前端编译镜像

### 6.1 拉取 Node 16 基础镜像

```powershell
docker pull node:16
```

### 6.2 给基础镜像打本地别名

```powershell
docker tag node:16 teaching-open-local-node16:latest
```

### 6.3 构建前端编译环境镜像

```powershell
docker build `
  --build-arg BASE_IMAGE=teaching-open-local-node16:latest `
  -t teaching-open-web-builder `
  -f .\web\Dockerfile.builder .\web
```

## 7. 编译前端源码

使用 `npm`，不要使用 `yarn`。

原因：

- 当前仓库有 `package-lock.json`
- 没有 `yarn.lock`
- 使用 `yarn` 时容易拉到过新的依赖，导致 `Node 16` 不兼容
- 使用 `npm ci --legacy-peer-deps` 对这个老 Vue 2 项目最稳

执行：

```powershell
docker run --rm -v "D:\ext-dev\teaching-open\web:/workspace" -w /workspace teaching-open-web-builder bash -lc "npm ci --legacy-peer-deps && npm run build"
```

编译成功后会生成：

```text
web\dist
```

## 8. 构建前端运行镜像

### 8.1 拉取 Nginx 基础镜像

```powershell
docker pull nginx:latest
```

### 8.2 给基础镜像打本地别名

```powershell
docker tag nginx:latest teaching-open-local-nginx:latest
```

### 8.3 构建前端运行镜像

```powershell
docker build `
  --build-arg BASE_IMAGE=teaching-open-local-nginx:latest `
  -t registry.cn-shanghai.aliyuncs.com/goodat/teaching-open-web:latest `
  -f .\web\Dockerfile .\web
```

## 9. 启动前后端服务

```powershell
docker compose -f .\deploy\docker-compose.yml up -d api web
```

说明：

- `docker-compose.yml` 中 `api` 和 `web` 使用的镜像名，和上面构建时使用的镜像名保持一致
- 因此会优先使用你本地刚构建的镜像，而不是重新拉取远端镜像

## 10. 访问系统

浏览器打开：

- 前端首页：`http://localhost`
- 后端接口前缀：`http://localhost/api`

默认测试账号：

- `admin`
- `teacher`
- `student`

默认密码：

- `123456`

## 11. 修改代码后的重建方式

### 11.1 只改后端

重新编译后端：

```powershell
docker run --rm -v "D:\ext-dev\teaching-open\api:/workspace" -w /workspace teaching-open-api-builder bash -lc "mvn clean package"
```

重新构建后端运行镜像：

```powershell
docker build `
  --build-arg BASE_IMAGE=teaching-open-local-openjdk8:latest `
  -t registry.cn-shanghai.aliyuncs.com/goodat/teaching-open-api:latest `
  -f .\api\Dockerfile .\api
```

重启后端容器：

```powershell
docker compose -f .\deploy\docker-compose.yml up -d --force-recreate api
```

### 11.2 只改前端

重新编译前端：

```powershell
docker run --rm -v "D:\ext-dev\teaching-open\web:/workspace" -w /workspace teaching-open-web-builder bash -lc "npm ci --legacy-peer-deps && npm run build"
```

重新构建前端运行镜像：

```powershell
docker build `
  --build-arg BASE_IMAGE=teaching-open-local-nginx:latest `
  -t registry.cn-shanghai.aliyuncs.com/goodat/teaching-open-web:latest `
  -f .\web\Dockerfile .\web
```

重启前端容器：

```powershell
docker compose -f .\deploy\docker-compose.yml up -d --force-recreate web
```

## 12. 停止服务

停止所有容器：

```powershell
docker compose -f .\deploy\docker-compose.yml down
```

## 13. 清理数据

如果你想彻底删除本地测试数据：

1. 先停止容器
2. 再删除以下目录

```text
deploy\data\mysql
deploy\data\redis
deploy\data\upload
```

## 14. 常见坑总结

### 14.1 `maven:3.8-openjdk-8-slim` 拉取失败

现象：

- `failed to fetch anonymous token`
- `401 Unauthorized`

建议：

- 先手动 `docker pull`
- 再 `docker tag` 成本地别名
- 构建时通过 `--build-arg BASE_IMAGE=本地别名` 使用

### 14.2 `openjdk:8 not found`

原因：

- `openjdk` 官方镜像已废弃

处理：

- 改用 `eclipse-temurin:8-jre-jammy`

### 14.3 前端 `yarn` 安装失败

现象：

- `minimatch` 要求更高版本 Node

原因：

- 没有 `yarn.lock`
- `yarn` 会解析到过新的间接依赖

处理：

- 不用 `yarn`
- 改用 `npm ci --legacy-peer-deps`

### 14.4 前端 `npm ci` 出现 `ERESOLVE`

原因：

- 老 Vue 2 项目依赖存在 `peerDependencies` 冲突

处理：

- 使用 `npm ci --legacy-peer-deps`

## 15. 建议

如果只是本地验证功能，这套方式已经足够。

如果后续进入高频开发阶段，可以再考虑：

- 保持 `db`、`redis` 走 Docker
- 前后端改为本机源码热启动

这样开发效率会更高，但本机需要额外安装 `JDK 8`、`Maven`、`Node 16`。
