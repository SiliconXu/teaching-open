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

执行完后重新登录服务器，再检查：

```bash
docker --version
docker compose version
```

确认：

- [ ] Docker 可用
- [ ] `docker compose` 可用

### 3.2 给 GitHub 仓库添加 Deploy Key

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

### 3.3 拉取代码

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
JEECG_DOMAIN=https://你的测试域名

MYSQL_HOST=db
MYSQL_PORT=3306
MYSQL_DATABASE=teachingopen
MYSQL_USER=teachingopen
MYSQL_PASSWORD=改成强密码

REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DATABASE=0

UPLOAD_TYPE=qiniu

QINIU_ACCESS_KEY=你的七牛AK
QINIU_SECRET_KEY=你的七牛SK
QINIU_BUCKET=你的七牛bucket
QINIU_STATICDOMAIN=https://你的七牛访问域名
QINIU_AREA=z0
```

确认：

- [ ] `UPLOAD_TYPE=qiniu`
- [ ] 七牛配置已经与旧环境保持一致
- [ ] 域名先填测试域名，不要急着填正式域名

---

## 5. 决定镜像来源

你有两种方式：

### 方式 A：直接使用已有镜像

适合你暂时不需要编译自己的修改版本。

优点：

- 快
- 适合先验证迁移链路

缺点：

- 如果你有二次开发代码，未必包含在这些镜像里

### 方式 B：在新 VM 上构建你自己的镜像

适合你已经改了代码，准备部署自己的版本。

我更推荐你用这个方式。

---

## 6. 构建自己的 Docker 镜像

以下命令在新 VM 上执行：

```bash
cd /opt/teaching-open
```

### 6.1 构建数据库初始化镜像

```bash
docker build -t teaching-open-db:latest -f api/Dockerfile.db api
```

### 6.2 构建后端编译镜像

```bash
docker build -t teaching-open-api-builder:latest -f api/Dockerfile.builder api
```

### 6.3 编译后端 Jar

```bash
docker run --rm \
  -v "$PWD/api:/workspace" \
  -w /workspace \
  teaching-open-api-builder:latest \
  bash -lc "mvn clean package"
```

确认：

- [ ] 已生成 `api/jeecg-boot-module-system/target/teaching-open-2.8.0.jar`

### 6.4 构建后端运行镜像

```bash
docker build -t teaching-open-api:latest -f api/Dockerfile api
```

### 6.5 构建前端编译镜像

```bash
docker build -t teaching-open-web-builder:latest -f web/Dockerfile.builder web
```

### 6.6 编译前端

这个老项目建议用 `npm`，不要用 `yarn`。

```bash
docker run --rm \
  -v "$PWD/web:/workspace" \
  -w /workspace \
  teaching-open-web-builder:latest \
  bash -lc "npm ci --legacy-peer-deps && npm run build"
```

确认：

- [ ] 已生成 `web/dist`

### 6.7 构建前端运行镜像

```bash
docker build -t teaching-open-web:latest -f web/Dockerfile web
```

---

## 7. 让 Compose 使用你自己构建的镜像

项目默认的 Compose 文件是：

- [deploy/docker-compose.yml](/D:/ext-dev/teaching-open/deploy/docker-compose.yml)

建议在新 VM 上先备份，再把镜像名改成你本地刚构建的名字：

```bash
cp deploy/docker-compose.yml deploy/docker-compose.yml.backup
nano deploy/docker-compose.yml
```

把这些镜像名改掉：

```yaml
services:
  db:
    image: teaching-open-db:latest

  api:
    image: teaching-open-api:latest

  web:
    image: teaching-open-web:latest
```

Redis 可以继续用原文件里的镜像。

确认：

- [ ] `db` 使用 `teaching-open-db:latest`
- [ ] `api` 使用 `teaching-open-api:latest`
- [ ] `web` 使用 `teaching-open-web:latest`

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
