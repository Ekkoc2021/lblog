# LBlog

个人博客系统，前后端分离架构。

## 项目结构

```
lblog/
├── lblog-server/   Spring Boot 3.5 + Java 17 + MyBatis + MySQL 8
└── lblog-web/      React 19 + TypeScript + Vite + Ant Design 6
```

### 技术栈

| 层 | 后端 | 前端 |
|---|------|------|
| 框架 | Spring Boot 3.5 | React 19 |
| 语言 | Java 17 | TypeScript 6 |
| 数据 | MyBatis 3 + MySQL 8 | — |
| 连接池 | Druid | — |
| 安全 | Spring Security | JWT Bearer Token |
| UI | — | Ant Design 6 |
| 构建 | Maven | Vite 8 |
| 文档 | springdoc-openapi (Swagger) | — |

## 快速启动

### 环境要求

- JDK 17+
- Node.js 18+
- MySQL 8
- Maven 3.9+

### 1. 数据库

在 MySQL 中创建数据库并执行建表语句（见 `lblog-server/src/main/resources/sql/` 或联系项目维护者获取最新 DDL）。

默认连接：`192.168.1.5:3306/iblog`（可在 `application.yml` 中修改）。

### 2. 启动后端

```bash
cd lblog-server
mvn spring-boot:run
```

或通过 IntelliJ IDEA 运行 `LblogServerApplication`。

后端启动后访问：
- API 基础路径：`http://localhost:8099/iblogserver/api/v1/`
- Swagger UI：`http://localhost:8099/iblogserver/swagger-ui.html`

### 3. 启动前端

```bash
cd lblog-web
npm install
npm run dev
```

前端 dev server 默认 `http://localhost:5173`，API 请求自动代理到后端 `localhost:8099`。

## 构建打包

### 后端

```bash
cd lblog-server
mvn clean package -DskipTests
```

产物：`target/lblog-server-*.jar`

### 前端

```bash
cd lblog-web
npm run build
```

产物：`dist/` 目录，可直接由 Nginx 托管或放入后端 `src/main/resources/static/`。

## 部署

### 后端部署

```bash
java -jar lblog-server-*.jar --spring.profiles.active=prod
```

可通过环境变量或外部配置文件覆盖数据库连接等参数：

```bash
java -jar lblog-server.jar \
  --spring.datasource.url=jdbc:mysql://your-host:3306/iblog \
  --spring.datasource.username=your-user \
  --spring.datasource.password=your-password
```

### 前端部署

将 `dist/` 目录内容部署到 Nginx 或其他静态服务器，配置反向代理：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /path/to/lblog-web/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /iblogserver/ {
        proxy_pass http://127.0.0.1:8099;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### 一体化部署

也可将前端构建产物放入后端 `src/main/resources/static/` 目录后再打包，通过 Spring Boot 直接托管前端静态资源 + API。

```bash
cd lblog-web && npm run build
cp -r dist/* ../lblog-server/src/main/resources/static/
cd ../lblog-server && mvn clean package -DskipTests
```

## 项目端口

| 服务 | 端口 | 说明 |
|------|------|------|
| 后端 API | 8099 | Spring Boot，context-path: `/iblogserver` |
| 前端 Dev | 5173 | Vite HMR，代理 `/api` → 后端 |

## 开发约定

- API 响应统一包装：`ApiResponse<T>`，code=0 成功，非零错误
- 分页使用 PageHelper + `PageResult<T>`
- 前端 TypeScript 严格模式，`verbatimModuleSyntax` 启用
- 后端分层：controller → service/impl → mapper → domain
- 数据库表使用软删除（`deleted_at` + `is_delelte`）
