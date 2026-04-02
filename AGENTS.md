# Teaching Open 工程说明

本文档面向准备维护、二次开发、排查问题的开发者，说明这个仓库的技术栈、目录结构、运行方式、调试方式，以及新增功能时的推荐切入点。

相关文档：

- 本地 Docker 隔离部署：[`LOCAL_DOCKER_SETUP.md`](./LOCAL_DOCKER_SETUP.md)
- 一键构建脚本：[`dev-docker.ps1`](./dev-docker.ps1)

## 1. 项目定位

`Teaching Open` 是一个基于 `Jeecg Boot` 改造的在线教学平台，包含：

- 前台首页/课程/社区/资讯
- 学生作品创作与展示
- 班级作业提交与批改
- 课程与课程单元管理
- 系统管理、菜单、字典、权限、部门、用户

从代码来看，它不是单纯的内容站，而是一个“前台 + 后台 + 教学业务”的完整系统。

## 2. 技术栈

### 2.1 后端

核心栈：

- `Java 8`
- `Spring Boot 2.1.3.RELEASE`
- `MyBatis-Plus 3.1.2`
- `Apache Shiro`
- `JWT`
- `Redis`
- `Quartz`
- `Swagger 2`
- `Lombok`

主要入口与配置：

- 启动类：[`api/jeecg-boot-module-system/src/main/java/org/jeecg/JeecgApplication.java`](./api/jeecg-boot-module-system/src/main/java/org/jeecg/JeecgApplication.java)
- 父 POM：[`api/pom.xml`](./api/pom.xml)
- 生产配置：[`api/jeecg-boot-module-system/src/main/resources/application-prod.yml`](./api/jeecg-boot-module-system/src/main/resources/application-prod.yml)
- 开发配置：[`api/jeecg-boot-module-system/src/main/resources/application-dev.yml`](./api/jeecg-boot-module-system/src/main/resources/application-dev.yml)

### 2.2 前端

核心栈：

- `Vue 2.6`
- `Vue Router 3`
- `Vuex 3`
- `ant-design-vue 1.x`
- `axios`
- `vue-cli 3`
- `Jeecg` 前端组件与 mixin

主要入口与配置：

- 前端入口：[`web/src/main.js`](./web/src/main.js)
- 路由入口：[`web/src/router/index.js`](./web/src/router/index.js)
- 静态基础路由：[`web/src/config/router.config.js`](./web/src/config/router.config.js)
- 权限守卫：[`web/src/permission.js`](./web/src/permission.js)
- 请求封装：[`web/src/utils/request.js`](./web/src/utils/request.js)
- API 工具：[`web/src/api/manage.js`](./web/src/api/manage.js)
- 构建配置：[`web/vue.config.js`](./web/vue.config.js)

### 2.3 基础设施

- `MySQL 8`
- `Redis 6`
- `Nginx`
- `Docker / Docker Compose`

数据库初始化脚本：

- [`api/db/teachingopen2.8.sql`](./api/db/teachingopen2.8.sql)
- [`api/db/qrtzUpcase.sql`](./api/db/qrtzUpcase.sql)

## 3. 仓库结构

### 3.1 根目录

- `api/`：后端源码
- `web/`：前端源码
- `deploy/`：Docker 编排、环境变量、启动脚本
- `资料/`：部署参考图、模板文件、Nginx 示例等

### 3.2 后端模块

后端是典型的 Jeecg/Boot 结构：

- `jeecg-boot-base-common/`
  - 通用工具、基础配置、通用返回值、切面、文件工具等
- `jeecg-boot-module-system/`
  - 业务主模块
  - 系统管理功能
  - 教学相关业务功能

教学业务主要在：

- `api/jeecg-boot-module-system/src/main/java/org/jeecg/modules/teaching/`

常见分层：

- `controller/`：接口入口
- `service/`：业务接口
- `service/impl/`：业务实现
- `entity/`：数据库实体
- `mapper/`：Mapper 接口
- `mapper/xml/`：复杂 SQL
- `model/`、`vo/`：页面聚合对象、返回模型

### 3.3 前端模块

前端主要在：

- `web/src/views/home/`：前台页面
- `web/src/views/account/`：学生中心
- `web/src/views/teaching/`：教学后台业务页面
- `web/src/views/system/`：系统管理页面
- `web/src/components/jeecg/`：Jeecg 组件
- `web/src/mixins/`：列表页与编辑页的公共行为

## 4. 请求与路由机制

### 4.1 前端请求

前端统一通过 [`web/src/utils/request.js`](./web/src/utils/request.js) 创建 axios 实例：

- 默认 `baseURL` 来源于 `window._CONFIG['domianURL']`
- 本地开发时一般走 `/api`
- 请求头自动带 `X-Access-Token`

常用接口方法在 [`web/src/api/manage.js`](./web/src/api/manage.js)：

- `getAction`
- `postAction`
- `putAction`
- `deleteAction`
- `uploadAction`

### 4.2 前端路由

前端路由分两部分：

- 静态公共路由：[`web/src/config/router.config.js`](./web/src/config/router.config.js)
- 登录后动态菜单路由：[`web/src/permission.js`](./web/src/permission.js)

也就是说：

- 首页、登录页、作品详情页等是写死在前端代码里的
- 后台管理菜单是登录后从服务端拉取，再动态生成路由

### 4.3 后端接口风格

大多数接口遵循 Jeecg 风格：

- 列表：`/list`
- 查询单条：`/queryById`
- 新增：`/add`
- 编辑：`/edit`
- 删除：`/delete`
- 批量删除：`/deleteBatch`
- 导入导出：`/importExcel`、`/exportXls`

例如课程模块：

- 控制器：[`TeachingCourseController.java`](./api/jeecg-boot-module-system/src/main/java/org/jeecg/modules/teaching/controller/TeachingCourseController.java)
- 页面：[`TeachingCourseList.vue`](./web/src/views/teaching/TeachingCourseList.vue)

## 5. 业务模型速览

从数据库和代码可以看出，核心业务表包括：

- `teaching_course`：课程
- `teaching_course_unit`：课程单元
- `teaching_course_dept`：课程与班级/部门的关联
- `teaching_work`：学生作品/作业
- `teaching_work_comment`：作品评论
- `teaching_work_correct`：作业批改
- `teaching_additional_work`：附加作业
- `teaching_news`：资讯
- `teaching_menu`：前台菜单
- `teaching_scratch_assets`：Scratch 素材库

系统侧常用表包括：

- `sys_user`
- `sys_role`
- `sys_permission`
- `sys_depart`
- `sys_dict`
- `sys_config`
- `sys_file`

## 6. 二次开发推荐方式

### 6.1 新增简单后台模块

适合场景：

- 新增一个管理列表页
- 新增配置项
- 新增字典项
- 新增导入导出页面

推荐路径：

1. 新建数据库表
2. 新增实体 `entity`
3. 新增 `mapper` 与 `mapper/xml`
4. 新增 `service` 和 `service/impl`
5. 新增 `controller`
6. 在前端新增 `views/xxx/List.vue` 和 `modules/xxxModal.vue`
7. 在菜单/权限表中增加页面权限

这类开发成本通常最低，因为项目已经有大量可复用模式。

### 6.2 沿着现有教学业务扩展

适合场景：

- 给课程增加字段
- 给作品增加状态、标签、审核逻辑
- 给学生中心增加新入口
- 增加新的教师端操作

推荐先阅读已有模块：

- 课程：[`TeachingCourseController.java`](./api/jeecg-boot-module-system/src/main/java/org/jeecg/modules/teaching/controller/TeachingCourseController.java)
- 作业/作品：[`TeachingWorkController.java`](./api/jeecg-boot-module-system/src/main/java/org/jeecg/modules/teaching/controller/TeachingWorkController.java)
- 学生课程地图：[`CourseUnitMap.vue`](./web/src/views/account/course/CourseUnitMap.vue)
- 作品详情：[`WorkDetail.vue`](./web/src/views/home/WorkDetail.vue)

### 6.3 涉及复杂查询时

这个项目大量使用：

- MyBatis-Plus 的基础 CRUD
- Mapper XML 中的自定义 SQL

如果是多表联查、分页聚合、关联统计，优先去：

- `mapper/`
- `mapper/xml/`

不要强行把复杂 SQL 全塞进 `QueryWrapper`。

## 7. 新增功能时建议遵循的套路

### 7.1 后端

建议顺序：

1. 先确定数据库表和字段
2. 先加 `entity`
3. 先做最小可用接口
4. 再补 service 层逻辑
5. 最后再接前端页面

如果只是单表增删改查，尽量沿用 Jeecg 风格，避免引入过多新架构。

### 7.2 前端

建议顺序：

1. 先找最像的现有页面复制思路
2. 优先复用 `JeecgListMixin`
3. API 调用统一放到 `manage.js` 风格的方法里
4. 后台页面优先沿用现有表格、弹窗、字典、上传组件

这个仓库不是全新架构项目，二开时“贴着现有模式做”通常比“重设计一套前端结构”更省成本。

## 8. 调试建议

### 8.1 后端调试

如果你走本机源码方式：

- 用 IDEA 运行 `JeecgApplication`
- 断点位置优先放在 `controller` 和 `service/impl`

如果你走 Docker 隔离方式：

- 先看容器日志
- 再进入对应模块排查

常用命令：

```powershell
docker compose -f .\deploy\docker-compose.yml logs -f api
docker compose -f .\deploy\docker-compose.yml logs -f web
```

开发配置中，Mapper 日志可以开到 `debug`：

- [`application-dev.yml`](./api/jeecg-boot-module-system/src/main/resources/application-dev.yml)

### 8.2 前端调试

优先看：

- 浏览器 `Network`
- 浏览器 `Console`
- axios 请求路径是否正确
- 登录 token 是否存在

重点排查文件：

- [`web/src/utils/request.js`](./web/src/utils/request.js)
- [`web/src/permission.js`](./web/src/permission.js)
- [`web/vue.config.js`](./web/vue.config.js)

### 8.3 数据问题调试

优先确认三件事：

1. 表结构是否已经同步
2. SQL 初始化脚本是否已执行
3. 当前登录用户是否具备菜单/部门/角色权限

很多“页面没显示”“接口没数据”的问题，本质上不是代码异常，而是：

- 菜单没配置
- 字典没配置
- 部门权限过滤
- 数据没初始化

## 9. 菜单、权限、字典、配置

这个项目高度依赖后台基础数据。

### 9.1 菜单

后台动态菜单由服务端返回，前端再生成路由。

如果你新增后台页面，通常不仅要写前端页面，还要补：

- `sys_permission`
- 角色与权限关系
- 可能还有菜单图标、排序、路径

### 9.2 字典

很多页面字段依赖字典：

- `course_type`
- `course_category`

如果页面显示的是 `_dictText` 字段，通常说明它背后依赖 `sys_dict` / `sys_dict_item`。

### 9.3 系统配置

前端启动时会拉取系统配置和菜单：

- [`web/src/main.js`](./web/src/main.js)

因此：

- 站点标题
- 自定义 JS/CSS
- 文件访问域名
- 上传方式

这类都可能由数据库配置控制，而不只是写死在代码里。

## 10. 文件上传与静态资源

上传逻辑既支持本地，也支持七牛云。

关键点：

- 前端通过 `getFileAccessHttpUrl` 拼接文件访问地址
- 后端通过配置决定 `uploadType`
- Scratch/作品相关能力对静态资源路径比较敏感

建议本地开发优先使用：

- `uploadType: local`

## 11. 当前项目的现实限制

需要知道的几个事实：

- 技术栈偏旧：`Spring Boot 2.1`、`Vue 2`
- 依赖生态偏老，前端安装时对 `npm/yarn/node` 版本敏感
- Jeecg 体系有助于快速做 CRUD，但也带来一定框架耦合
- 很多业务行为依赖数据库初始化数据，不是纯代码驱动

因此二次开发时建议：

- 小步修改
- 尽量保持与现有代码风格一致
- 每次变更后优先验证数据库、权限、菜单、字典

## 12. 推荐阅读顺序

第一次接手这个仓库，建议按下面顺序阅读：

1. [`README.md`](./README.md)
2. [`LOCAL_DOCKER_SETUP.md`](./LOCAL_DOCKER_SETUP.md)
3. [`api/pom.xml`](./api/pom.xml)
4. [`api/jeecg-boot-module-system/src/main/resources/application-dev.yml`](./api/jeecg-boot-module-system/src/main/resources/application-dev.yml)
5. [`web/package.json`](./web/package.json)
6. [`web/src/main.js`](./web/src/main.js)
7. [`web/src/permission.js`](./web/src/permission.js)
8. 一个后端业务模块，例如课程或作业
9. 对应的前端页面
10. 对应的数据库表

## 13. 维护建议

如果后续要长期维护，建议优先补这些能力：

- 补更完整的开发文档
- 补数据库变更说明
- 补一套最小可运行测试数据说明
- 固化前端依赖安装方式
- 固化 Docker 构建与重建脚本

如果要做大升级，再考虑：

- 升级到较新的 Java 运行时
- 升级到较新的 Spring Boot
- 评估 Vue 3 重构成本

但这类升级属于中大型工程，不建议和业务二开并行推进。
