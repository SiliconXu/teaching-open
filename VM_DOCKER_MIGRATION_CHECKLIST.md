# Teaching Open 从宝塔 + Jar 迁移到 Linux VM + Docker Compose 实操清单

这份清单适用于你当前的场景：

- 旧环境：宝塔 + Nginx + Jar
- 旧数据：已经有真实用户、课程、作品、系统配置
- 文件存储：七牛
- 新环境目标：Linux 虚拟机 + Docker Compose

原则只有三条：

1. 先迁移到新环境验证，再切正式流量
2. 保住旧数据库和旧七牛配置，避免历史数据失联
3. 不要把初始化 SQL 重新导入到生产数据上

---

## 0. 迁移前确认

在开始前先确认这几个前提：

- [ ] 已确定一个维护时间窗口
- [ ] 已准备一台新的 Linux VM
- [ ] 已准备一个临时测试域名，先不要直接切正式域名
- [ ] 旧环境暂时不下线，作为回滚保障

建议 VM 最低配置：

- 2 核 CPU
- 4 GB 内存
- 40 GB 以上磁盘
- Ubuntu 22.04 LTS 或 Debian 12

---

## 1. 记录旧环境关键信息

在旧服务器上整理出以下信息，保存到你自己的密码管理器或安全文档里：

- [ ] 旧服务器 IP
- [ ] 旧站点域名
- [ ] 旧 Nginx 配置
- [ ] 旧 Jar 所在目录
- [ ] 旧 `application-prod.yml`
- [ ] MySQL 地址、端口、库名、用户名、密码
- [ ] Redis 地址、端口、库号、密码
- [ ] 七牛 `AccessKey`
- [ ] 七牛 `SecretKey`
- [ ] 七牛 `bucketName`
- [ ] 七牛访问域名 `staticDomain`
- [ ] 七牛存储区域 `area`

重点提醒：

- 如果新环境继续使用原来的七牛 `bucket + 域名`，历史作品文件通常不用迁移
- 如果你准备换七牛 bucket 或域名，必须先同步对象并保持文件 key 不变

---

## 2. 备份旧环境

### 2.1 备份数据库

在旧服务器执行：

```bash
mysqldump -h 127.0.0.1 -P 3306 -u teachingopen -p \
  --single-transaction --routines --triggers --events \
  teachingopen > /root/teachingopen_backup_$(date +%F_%H%M%S).sql
```

如果你的数据库连接信息不是上面的值，请替换为真实值。

完成后确认：

- [ ] `.sql` 文件已生成
- [ ] 文件大小明显不是 0
- [ ] 可以额外复制到本地电脑或对象存储做第二份备份

### 2.2 备份配置文件

把旧环境这些文件拷出来：

- [ ] `application-prod.yml`
- [ ] Nginx 站点配置
- [ ] 启动脚本
- [ ] 任何你手工改过的 `.sh`、`.conf`、`.service`

参考命令：

```bash
mkdir -p /root/teachingopen_backup_config
cp /path/to/application-prod.yml /root/teachingopen_backup_config/
cp /www/server/panel/vhost/nginx/your-site.conf /root/teachingopen_backup_config/
```

### 2.3 备份七牛信息

至少确认下面这些值已经留档：

- [ ] `QINIU_ACCESS_KEY`
- [ ] `QINIU_SECRET_KEY`
- [ ] `QINIU_BUCKET`
- [ ] `QINIU_STATICDOMAIN`
- [ ] `QINIU_AREA`

如果你不打算更换七牛桶，这一步通常不需要导出对象文件。

### 2.4 Redis 备份

Redis 通常不是必须恢复，但建议保留一份：

- [ ] 如果 Redis 是单独安装的，备份 `dump.rdb` 或 `appendonly.aof`
- [ ] 如果只是缓存，不恢复通常也没关系

---

## 3. 在新 Linux VM 上准备环境

### 3.1 安装 Docker 和 Compose

推荐先走 Docker 官方仓库；如果 VM 所在网络无法稳定访问 `download.docker.com`，直接改走 Ubuntu 自带仓库。

#### 方案 A：Docker 官方仓库

```bash
sudo apt update
sudo apt install -y ca-certificates curl gnupg git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
sudo tee /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
```

注意：

- 执行完 `sudo usermod -aG docker $USER` 后，需要退出当前 SSH 会话并重新登录一次
- 否则当前 shell 不会立即拿到新的 `docker` 组权限，后面执行 `docker build`、`docker compose` 时可能会报 `permission denied while trying to connect to the docker API at unix:///var/run/docker.sock`

如果这里出现类似下面的错误：

```text
curl: (35) OpenSSL SSL_connect: Connection reset by peer in connection to download.docker.com:443
gpg: no valid OpenPGP data found.
```

通常不是命令写错，而是当前 VM 到 Docker 官方源的 HTTPS 连接被重置或不可达。此时不要继续重试这组命令，直接改用下面的 Ubuntu 仓库方案。

#### 方案 B：Ubuntu 22.04 自带仓库

这个方案不依赖 Docker 官方 apt 源，通常在国内网络环境下更容易装通。缺点是包版本跟随 Ubuntu 仓库节奏，而不是 Docker 官方发布节奏。

```bash
sudo apt update
sudo apt install -y git docker.io docker-buildx docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
```

注意：

- 执行完 `sudo usermod -aG docker $USER` 后，需要退出当前 SSH 会话并重新登录一次
- 否则当前 shell 不会立即拿到新的 `docker` 组权限，后面执行 `docker build`、`docker compose` 时可能会报 `permission denied while trying to connect to the docker API at unix:///var/run/docker.sock`

### 3.2 配置 Docker 镜像源

如果你的 VM 访问 Docker Hub 较慢，建议先配置镜像加速源，再继续后面的 `docker pull` 和 `docker build`。

这里以 `DaoCloud` 为例：

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json > /dev/null <<'EOF'
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io"
  ]
}
EOF
sudo systemctl daemon-reload
sudo systemctl restart docker
```

验证是否生效：

```bash
docker info
```

确认输出中包含类似：

```text
Registry Mirrors:
 https://docker.m.daocloud.io/
```

说明：

- 这一步是给 Docker daemon 配置镜像加速源
- 配好之后，后面的 `docker build` 和 `docker pull` 会优先通过这个镜像源去获取基础镜像
- 如果你的 `/etc/docker/daemon.json` 已经有其他配置，不要直接覆盖，要把 `registry-mirrors` 合并进去

### 3.3 检查 Docker 是否可用

执行完后重新登录服务器，再检查：

```bash
docker --version
docker compose version
```

说明：

- 这套迁移方案里，MySQL 和 Redis 都是通过 Docker 容器运行的
- 因此在 VM 宿主机上**不需要再单独安装 Linux 系统级的 MySQL 软件包**
- 但如果你要运行 `db` 服务，仍然需要准备一个可用的 `MySQL 8` Docker 镜像
- 后面你看到的 `db` 服务，本质上就是一个 MySQL 8 容器

确认：

- [ ] Docker 可用
- [ ] `docker compose` 可用

### 3.4 给 GitHub 仓库添加 Deploy Key

如果你的代码仓库是私有仓库，推荐在这台 VM 上生成一把专门用于部署的 SSH key，然后把公钥加到 GitHub 仓库的 `Deploy keys`。

Deploy key 的特点：

- 只绑定单个仓库
- 默认只读，适合部署拉代码
- 如果你需要在服务器上反向 push 代码，再勾选写权限

在 VM 上执行：

```bash
mkdir -p ~/.ssh
chmod 700 ~/.ssh
ssh-keygen -t ed25519 -C "teaching-open deploy key" -f ~/.ssh/teaching_open_deploy -N ""
chmod 600 ~/.ssh/teaching_open_deploy
chmod 644 ~/.ssh/teaching_open_deploy.pub
cat ~/.ssh/teaching_open_deploy.pub
```

把输出的整行公钥复制出来，然后到 GitHub 仓库页面操作：

1. 打开你的仓库主页
2. 进入 `Settings`
3. 点击左侧 `Deploy keys`
4. 点击 `Add deploy key`
5. `Title` 填一个容易识别的名字，例如 `teaching-open-vm`
6. `Key` 粘贴刚才复制的公钥
7. 如果服务器只需要 `git clone` / `git pull`，不要勾选 `Allow write access`
8. 点击 `Add key`

然后在 VM 上写 SSH 配置：

```bash
cat > ~/.ssh/config <<'EOF'
Host github.com
  HostName github.com
  User git
  IdentityFile ~/.ssh/teaching_open_deploy
  IdentitiesOnly yes
EOF
chmod 600 ~/.ssh/config
ssh-keyscan github.com >> ~/.ssh/known_hosts
chmod 644 ~/.ssh/known_hosts
```

测试连接：

```bash
ssh -T git@github.com
```

如果看到类似 `Hi <你的 GitHub 用户名>!` 或成功认证的提示，就说明 deploy key 已经生效。

注意：

- 一把 deploy key 只能绑定一个 GitHub 仓库，多个仓库不能复用同一把 key
- 如果同一台 VM 需要访问多个私有仓库，建议每个仓库生成一把独立 key，再用 `~/.ssh/config` 配不同的 `Host` 别名
- 如果你不想用 deploy key，也可以改用 HTTPS + Personal Access Token，但长期部署通常还是 SSH 更省事

### 3.5 拉取代码

```bash
cd /opt
sudo mkdir -p /opt/teaching-open
sudo chown -R $USER:$USER /opt/teaching-open
cd /opt/teaching-open
git clone <你的仓库地址> .
```

如果你已经把二次开发代码放在自己的 Git 仓库里，这里直接 clone 你自己的仓库。

确认：

- [ ] 代码已经落到 `/opt/teaching-open`

---

## 4. 配置新环境变量

项目已有变量文件：

- [deploy/.env](/D:/ext-dev/teaching-open/deploy/.env)

先备份一份再编辑：

```bash
cd /opt/teaching-open
cp deploy/.env deploy/.env.backup
nano deploy/.env
```

至少改这些值：

```dotenv
JEECG_DOMAIN=http://scratch2.vulcan-code.top:4080

MYSQL_HOST=db
MYSQL_PORT=3306
MYSQL_DATABASE=teachingopen
MYSQL_USER=teachingopen
MYSQL_PASSWORD=Dragon#1

REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DATABASE=0

UPLOAD_TYPE=qiniu

QINIU_ACCESS_KEY=hx8dX89C13lJb26AjEj_fFHqcJHOqPiHpSezrxWY
QINIU_SECRET_KEY=v1QAU_zDjESEwQKL0tKiyQdjBHFVEv6Y6rLQxzBX
QINIU_BUCKET=vulcan-scratch
QINIU_STATICDOMAIN=//qiniu.vucoding.top
QINIU_AREA=z0
```

确认：

- [ ] `UPLOAD_TYPE=qiniu`
- [ ] 七牛配置已经与旧环境保持一致
- [ ] 域名先填测试域名，不要急着填正式域名

---

## 5. 改用“本地构建，VM 只封装运行镜像”

从这一节开始，推荐你不要在 VM 上做重量级编译，而是在本地开发机先把构建产物准备好，再上传到 VM。

这样做的好处：

- VM 不需要跑 `mvn clean package`
- VM 不需要跑 `npm ci` 和 `npm run build`
- 更省 VM 的 CPU、内存、磁盘 IO
- 本地失败更容易调试，部署时更稳定

首次执行这套流程时，可以按下面的顺序理解：

- 本地负责生成后端 Jar 和前端 `dist`
- VM 负责保存代码、环境变量、运行容器
- VM 上只构建运行镜像，不再承担 Maven / npm 的完整编译过程

这里的“本地构建”默认是指：

- 本机安装 `Docker Desktop`
- 通过 Docker 容器完成 Maven 和 npm 构建
- 本机不强制要求安装 `Java`、`Maven`、`Node.js`

本节中的本地示例命令，默认对应仓库现有的本地开发方式：

- 本地系统：`Windows`
- 本地终端：`PowerShell`
- 项目目录：`D:\ext-dev\teaching-open`

如果你本地就是按仓库里的隔离开发方式在跑，可以直接参考：

- [LOCAL_DOCKER_SETUP.md](/D:/ext-dev/teaching-open/LOCAL_DOCKER_SETUP.md)
- [dev-docker.ps1](/D:/ext-dev/teaching-open/dev-docker.ps1)

补充说明：

- 首次本地构建时，Docker 可能需要拉取 `maven`、`node`、`nginx`、`mysql` 这类基础镜像
- 这些基础镜像第一次拉取成功后，后续通常会被本地 Docker 直接复用
- 之前配置好的 `teaching-open-m2-cache` 和 `teaching-open-npm-cache` 也会继续复用
- 所以后面更新 app 时，通常只需要重新编译产物并重建你自己的运行镜像，不需要每次重新拉取全部基础镜像

---

## 6. 本地构建产物，并在 VM 上封装运行镜像

本节分成两部分：

1. 在本地开发机构建产物
2. 把产物传到 VM，然后只在 VM 上构建运行镜像

### 6.1 在本地开发机准备后端编译容器

以下命令在本地开发机执行。

如果你本地是 Windows + Docker Desktop，直接在仓库根目录的 `PowerShell` 中执行：

```powershell
cd D:\ext-dev\teaching-open
docker pull maven:3.8-openjdk-8-slim
docker tag maven:3.8-openjdk-8-slim teaching-open-local-maven8:latest
docker build `
  --build-arg BASE_IMAGE=teaching-open-local-maven8:latest `
  -t teaching-open-api-builder `
  -f .\api\Dockerfile.builder .\api
```

说明：

- 这个 builder 镜像里已经预置了 Maven `settings.xml`，会优先使用阿里云公共 Maven 仓库镜像来加速访问 `central`
- 如果你刚更新过代码，记得先重新构建这个后端 builder 镜像，再执行下一步后端打包

### 6.2 在本地开发机通过 Docker 编译后端 Jar

```powershell
cd D:\ext-dev\teaching-open
docker volume create teaching-open-m2-cache
docker run --rm `
  -v "D:\ext-dev\teaching-open\api:/workspace" `
  -v teaching-open-m2-cache:/root/.m2 `
  -w /workspace `
  teaching-open-api-builder `
  bash -lc "mvn clean package"
```

说明：

- `teaching-open-m2-cache` 会缓存 Maven 依赖
- 以后重复构建时，可以直接复用这个 Docker volume，避免每次重新下载全部依赖
- 这里生成的必须是 Spring Boot 可执行 jar；如果后面启动 `api` 容器时报 `no main manifest attribute, in app.jar`，说明当前 jar 不是可执行包，需要同步最新代码并重新构建后端 jar

确认本地已生成：

- [ ] `api/jeecg-boot-module-system/target/teaching-open-2.8.0.jar`

### 6.3 在本地开发机准备前端编译容器

这个老项目建议用 `npm`，不要用 `yarn`。

```powershell
cd D:\ext-dev\teaching-open
docker pull node:16
docker tag node:16 teaching-open-local-node16:latest
docker build `
  --build-arg BASE_IMAGE=teaching-open-local-node16:latest `
  -t teaching-open-web-builder `
  -f .\web\Dockerfile.builder .\web
```

说明：

- 这个 builder 镜像里已经把 `npm` 和 `yarn` 的 registry 配到了 `https://registry.npmmirror.com`
- 如果你刚更新过代码，记得先重新构建这个前端 builder 镜像，再执行下一步前端编译

### 6.4 在本地开发机通过 Docker 编译前端静态文件

```powershell
cd D:\ext-dev\teaching-open
docker volume create teaching-open-npm-cache
docker run --rm `
  -v "D:\ext-dev\teaching-open\web:/workspace" `
  -v teaching-open-npm-cache:/root/.npm `
  -w /workspace `
  teaching-open-web-builder `
  bash -lc "npm ci --cache /root/.npm --legacy-peer-deps && npm run build"
```

说明：

- `teaching-open-npm-cache` 会缓存 npm 下载包
- `npm ci` 仍然会重建 `node_modules`，但不会每次都从网络重新拉取全部依赖

确认本地已生成：

- [ ] `web/dist`

### 6.5 在 VM 上为构建产物准备目录

如果你前面已经在 VM 上 `git clone` 了仓库源码，那么大部分源码目录都已经存在。

通常只需要确保后端 `target` 目录存在，方便上传 Jar：

```bash
cd /opt/teaching-open
mkdir -p api/jeecg-boot-module-system/target
```

说明：

- `web/` 目录通常会随着 `git clone` 一起带下来，不需要额外创建
- 这里的 `mkdir -p` 只是兜底操作，即使目录已存在也不会报错

### 6.6 把本地构建产物上传到 VM

下面给一个最直接的 `scp` 示例。以下命令在你的本地开发机执行：

```bash
scp -P <ssh-port> api/jeecg-boot-module-system/target/teaching-open-2.8.0.jar <vm-user>@<vm-host>:/opt/teaching-open/api/jeecg-boot-module-system/target/
scp -P <ssh-port> -r web/dist <vm-user>@<vm-host>:/opt/teaching-open/web/
```
按照当前服务器配置：
```bash
scp -P 8922 api/jeecg-boot-module-system/target/teaching-open-2.8.0.jar siliconxu@siliconxu.asuscomm.com:/opt/teaching-open/api/jeecg-boot-module-system/target/
scp -P 8922 -r web/dist siliconxu@siliconxu.asuscomm.com:/opt/teaching-open/web/
```

说明：

- `-P` 后面填写的是 SSH 端口号，例如 `-P 8922`
- `-P` 必须是大写，小写 `-p` 不是指定端口的意思

如果你不方便用 `scp`，也可以用：

- `rsync`
- `WinSCP`
- 先打包成 zip 再上传解压

不建议把这些构建产物直接提交到 GitHub 再让 VM `git pull`，原因是：

- `api/jeecg-boot-module-system/target` 本来就在 [.gitignore](/D:/ext-dev/teaching-open/.gitignore) 中
- `web/dist` 现在也已经加入了 [.gitignore](/D:/ext-dev/teaching-open/.gitignore)
- `jar` 和 `dist` 都属于构建产物，不适合作为日常源码提交的一部分
- 把二进制产物提交进仓库会让仓库历史变大，也容易让代码提交记录变得混乱

更推荐的做法是：

- 源码继续正常 `git push` / `git pull`
- `jar` 和 `web/dist` 作为构建产物，单独上传到 VM
- 如果后面你想把流程再规范一些，可以再演进成“本地构建镜像后推送到镜像仓库，VM 直接拉镜像”

可以把这条原则理解成：

- Git 仓库保存“源码、配置、脚本、Dockerfile”
- VM 部署时额外接收“本地刚构建出来的 `jar` 和 `dist`”
- 不把生成结果混进日常源码提交

上传完成后，在 VM 上确认：

- [ ] `/opt/teaching-open/api/jeecg-boot-module-system/target/teaching-open-2.8.0.jar` 存在
- [ ] `/opt/teaching-open/web/dist` 存在

### 6.7 在 VM 上构建数据库初始化镜像

这一步的作用，是把仓库里的初始化 SQL 打进一个 MySQL 8 镜像里。

```bash
cd /opt/teaching-open
docker build -t teaching-open-db:latest -f api/Dockerfile.db api
```

说明：

- 这一步**不是在 VM 宿主机安装 MySQL**
- 它只是基于 `mysql:8.0` 基础镜像，封装一个带初始化 SQL 的数据库镜像
- 如果 VM 本地还没有 `mysql:8.0` 基础镜像，`docker build` 会自动尝试拉取
- 如果这里报 `failed to resolve source metadata for docker.io/library/mysql:8.0` 或 `i/o timeout`，本质上是 VM 到镜像仓库的网络超时，不是 SQL 文件本身有问题

如果你**没有改过数据库初始化 SQL**，更推荐的做法是：

- 直接跳过这一步
- 继续构建 `api` 和 `web` 运行镜像
- 在 `docker-compose.yml` 里让 `db` 继续使用原来的预构建镜像

只有在下面这些情况，才建议坚持自己构建 `teaching-open-db:latest`：

- 你修改了 `api/db/` 里的初始化 SQL
- 你不想依赖原来的预构建数据库镜像

### 6.8 在 VM 上构建后端运行镜像

`api/Dockerfile` 只会把已经生成好的 Jar 打进镜像，不会在 VM 上重新编译 Java 代码。

说明：

- `api/Dockerfile` 默认基础镜像已经改为 `eclipse-temurin:8-jre-jammy`
- 不再使用容易失败的 `openjdk:8`
- 因此这里直接执行普通的 `docker build` 即可

```bash
cd /opt/teaching-open
docker build -t teaching-open-api:latest -f api/Dockerfile api
```

### 6.9 在 VM 上构建前端运行镜像

`web/Dockerfile` 只会把已经生成好的 `dist` 打进 Nginx 镜像，不会在 VM 上重新执行 `npm build`。

```bash
cd /opt/teaching-open
docker build -t teaching-open-web:latest -f web/Dockerfile web
```

---

## 7. 让 Compose 使用你自己构建的镜像

项目默认的 Compose 文件是：

- [deploy/docker-compose.yml](/D:/ext-dev/teaching-open/deploy/docker-compose.yml)

建议在新 VM 上先备份，再按你的实际情况调整镜像名：

```bash
cp deploy/docker-compose.yml deploy/docker-compose.yml.backup
nano deploy/docker-compose.yml
```

默认更推荐只改 `api` 和 `web`，`db` 继续沿用原文件里的预构建镜像。

推荐写法：

```yaml
services:
  api:
    image: teaching-open-api:latest

  web:
    image: teaching-open-web:latest
```

如果你前面已经成功执行了 `6.7`，并且确实构建出了本地数据库镜像，再把 `db` 改成：

```yaml
services:
  db:
    image: teaching-open-db:latest
```

Redis 可以继续用原文件里的镜像。

确认：

- [ ] `api` 使用 `teaching-open-api:latest`
- [ ] `web` 使用 `teaching-open-web:latest`
- [ ] 如果你跳过了 `6.7`，`db` 继续使用原文件里的镜像
- [ ] 如果你执行了 `6.7`，`db` 使用 `teaching-open-db:latest`

---

## 8. 先启动新环境基础设施

先启动数据库和 Redis：

```bash
cd /opt/teaching-open/deploy
docker compose up -d db redis
```

检查容器：

```bash
docker compose ps
```

确认：

- [ ] `db` 正常运行
- [ ] `redis` 正常运行

---

## 9. 恢复旧数据库到新环境

把旧服务器导出的 SQL 文件传到新 VM，例如：

```bash
scp root@旧服务器IP:/root/teachingopen_backup_2026-xx-xx_xxxxxx.sql /opt/teaching-open/
```

进入新 VM 后导入数据库：

```bash
cd /opt/teaching-open
cat teachingopen_backup_*.sql | docker exec -i teachingopen_db mysql -uroot -pteachingopen teachingopen
```

如果你改过 `MYSQL_ROOT_PASSWORD`，把命令里的密码替换掉。

重要说明：

- 当前 `db` 镜像在“全新数据目录首次启动”时，可能会自动执行镜像内自带的初始化 SQL
- 所以如果你是要恢复“旧环境完整备份”，不能直接往这个已经初始化过的库里继续导入
- 更稳的做法是：先把目标库删掉并重建成空库，再导入你的旧备份

参考命令：

```bash
docker exec -i teachingopen_db mysql -uroot -pteachingopen -e "DROP DATABASE IF EXISTS teachingopen; CREATE DATABASE teachingopen CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
```

如果导入时出现类似下面的错误：

```text
ERROR 1118 (42000): Row size too large (> 8126)
```

通常说明旧库导出的建表语句里带有较老的 InnoDB 行格式定义，导入到新的 MySQL 8 环境时触发了行大小限制。

推荐处理方式：

1. 先备份原始导出文件
2. 在导入前把 SQL 文件里的 `ROW_FORMAT=COMPACT` 或 `ROW_FORMAT=REDUNDANT` 替换成 `ROW_FORMAT=DYNAMIC`
3. 清空新环境里这次失败导入产生的残留数据后，再重新导入

参考命令：

```bash
cd /opt/teaching-open
cp teachingopen_backup_*.sql teachingopen_backup_fixed.sql
sed -i -E 's/ROW_FORMAT=(COMPACT|REDUNDANT)/ROW_FORMAT=DYNAMIC/g' teachingopen_backup_fixed.sql
```

如果这是一个全新的迁移环境、当前库里还没有任何你需要保留的数据，可以先重置数据库数据目录再重新导入：

```bash
cd /opt/teaching-open/deploy
docker compose down
sudo rm -rf data/mysql/*
docker compose up -d db
```

然后重新导入修正后的 SQL：

```bash
cd /opt/teaching-open
docker exec -i teachingopen_db mysql -uroot -pteachingopen -e "DROP DATABASE IF EXISTS teachingopen; CREATE DATABASE teachingopen CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
cat teachingopen_backup_fixed.sql | docker exec -i teachingopen_db mysql -uroot -pteachingopen teachingopen
```

注意：

- `rm -rf data/mysql/*` 只适用于“新的迁移环境、导入失败后准备重来”的场景
- 如果这台新 VM 上已经有你要保留的数据，不要直接清空数据目录
- 如果替换后仍然报错，再检查导出文件对应报错位置附近是否还有其他显式的表选项需要调整
- 如果出现 `Duplicate entry ... for key ... PRIMARY`，通常说明你把旧备份导入到了一个已经初始化过、或已经部分导入过的数据里，需要先删库重建空库再导入

导入完成后检查：

```bash
docker exec -it teachingopen_db mysql -uroot -pteachingopen -e "use teachingopen; show tables;"
```

确认：

- [ ] 数据库导入成功
- [ ] 能看到 `sys_user`
- [ ] 能看到 `sys_config`
- [ ] 能看到 `sys_file`
- [ ] 能看到各种 `teaching_*` 表

重要提醒：

- 不要再导入 [teachingopen2.8.sql](/D:/ext-dev/teaching-open/api/db/teachingopen2.8.sql) 到这份恢复后的库里
- 如果你的代码升级涉及数据库变更，只能执行你需要的升级 SQL，不能重置整库

---

## 10. 启动 API 和 Web

```bash
cd /opt/teaching-open/deploy
docker compose up -d api web
```

查看运行状态：

```bash
docker compose ps
docker compose logs -f api
```

确认：

- [ ] `api` 正常启动
- [ ] `web` 正常启动
- [ ] 没有明显数据库连接报错
- [ ] 没有明显七牛配置报错

---

## 11. 配置测试域名

先把测试域名指向新 VM。

如果你暂时不做 HTTPS，可以先用 `80` 端口测试。

项目里的 `web` 容器已经暴露了 `80:80`，所以只需要：

- [ ] 测试域名 A 记录指向新 VM
- [ ] 云服务器安全组放行 80 端口
- [ ] VM 本机防火墙放行 80 端口

浏览器验证：

- [ ] 测试域名首页能打开
- [ ] `/api` 接口能正常响应

---

## 12. 业务验收清单

用测试域名逐项检查：

- [ ] 管理员能登录
- [ ] 普通用户能登录
- [ ] 用户列表正常
- [ ] 头像正常显示
- [ ] 首页 logo 正常显示
- [ ] 课程列表正常
- [ ] 作品列表正常
- [ ] 作品封面正常显示
- [ ] Scratch 作品详情页正常
- [ ] 图片、PPT、文档等历史上传文件可访问
- [ ] 后台“网站配置”里的 logo、banner、首页文案都还在
- [ ] 资讯正文里的历史图片正常显示

如果这里有文件显示异常，优先排查：

1. `deploy/.env` 中的七牛配置是否与旧环境一致
2. 旧数据库里的文件 key 是否还存在于原七牛 bucket
3. 是否有历史富文本把旧域名写死了

---

## 13. 如果你后续还要升级数据库结构

只在测试域名验证通过后再做。

原则：

- [ ] 先备份当前新环境数据库
- [ ] 再执行你自己的增量 SQL
- [ ] 每次变更都先在测试环境验证

如果你需要执行项目自带升级脚本：

- [api/db/update2.2.sql](/D:/ext-dev/teaching-open/api/db/update2.2.sql)
- [api/db/update2.3.sql](/D:/ext-dev/teaching-open/api/db/update2.3.sql)
- [api/db/update2.4.sql](/D:/ext-dev/teaching-open/api/db/update2.4.sql)
- [api/db/update2.5.sql](/D:/ext-dev/teaching-open/api/db/update2.5.sql)
- [api/db/update2.6.sql](/D:/ext-dev/teaching-open/api/db/update2.6.sql)
- [api/db/update2.7.sql](/D:/ext-dev/teaching-open/api/db/update2.7.sql)
- [api/db/update2.8.sql](/D:/ext-dev/teaching-open/api/db/update2.8.sql)

注意：

- 只执行你确实缺少的升级 SQL
- 不要把这些脚本当成“初始化整库脚本”

---

## 14. 正式切换步骤

只有测试验收全部通过后，才做正式切换。

推荐顺序：

1. [ ] 冻结旧线上数据写入窗口
2. [ ] 再做一次旧线上数据库最终增量备份
3. [ ] 把最终备份导入到新环境
4. [ ] 在新环境做最后一次冒烟验证
5. [ ] 正式域名切到新 VM
6. [ ] 观察 30 到 60 分钟
7. [ ] 没问题后再下线旧环境

如果旧环境在切换窗口内还有新增数据，记得以最后一次备份为准。

---

## 15. 回滚方案

如果新环境异常，不要硬扛，直接回滚：

1. [ ] 把正式域名重新指回旧服务器
2. [ ] 恢复旧 Nginx 配置
3. [ ] 启动旧 Jar 服务
4. [ ] 检查登录、作品、文件访问是否恢复

只要旧环境在切换前没有被破坏，DNS 或代理切回去就能快速恢复。

---

## 16. 迁移完成后建议

迁移完成后建议你再做三件事：

1. [ ] 给新 VM 做定期 MySQL 备份任务
2. [ ] 保存一份 `deploy/.env` 的脱敏模板
3. [ ] 把生产镜像构建流程固定下来，不再手工临时修改

建议长期备份内容：

- MySQL 全量备份
- `deploy/.env`
- `deploy/docker-compose.yml`
- 七牛配置参数
- 你的二次开发代码仓库

---

## 17. 这次迁移中不要做的事

- [ ] 不要在生产库上重新导入初始化 SQL
- [ ] 不要先删表再导数据
- [ ] 不要先切正式域名再验证测试域名
- [ ] 不要更换七牛 bucket 后又不迁移旧对象
- [ ] 不要只迁 Jar，不迁数据库

---

## 18. 本项目里你会用到的文件

- Compose 文件：[deploy/docker-compose.yml](/D:/ext-dev/teaching-open/deploy/docker-compose.yml)
- 环境变量：[deploy/.env](/D:/ext-dev/teaching-open/deploy/.env)
- 后端生产配置：[application-prod.yml](/D:/ext-dev/teaching-open/api/jeecg-boot-module-system/src/main/resources/application-prod.yml)
- 初始化数据库脚本：[teachingopen2.8.sql](/D:/ext-dev/teaching-open/api/db/teachingopen2.8.sql)
- 本地 Docker 构建说明：[LOCAL_DOCKER_SETUP.md](/D:/ext-dev/teaching-open/LOCAL_DOCKER_SETUP.md)

---

## 19. 迁移完成判定标准

满足以下条件，就可以认为迁移完成：

- [ ] 正式域名已切到新 VM
- [ ] 管理员和普通用户都能登录
- [ ] 历史作品可查看
- [ ] 七牛文件访问正常
- [ ] 后台配置还在
- [ ] 24 小时内没有明显错误日志
