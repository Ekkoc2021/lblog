# LBlog 部署文档

## 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 后端框架 | Spring Boot | 3.5.7 |
| JDK | OpenJDK | 21 |
| 数据库 | MySQL 8.0（iblog，utf8mb4） | — |
| ORM | MyBatis + PageHelper | 3.0.3 / 2.1.0 |
| 连接池 | Druid | 1.2.20 |
| 认证 | Spring Security + JWT | — |
| AI | Spring AI + DeepSeek | 1.1.5 / v4-flash |
| 实时通信 | SSE（Server-Sent Events） | — |
| 缓存 | Caffeine（进程内） | — |
| 前端 | React 19 + Vite 8 + Ant Design 6 | — |
| 反向代理 | Nginx | 1.24 |

## 服务器要求

- Ubuntu 24.04，2c2g4m 起步
- 开放端口：80（Nginx）、8099（后端，可选仅本地监听）

## 1. 环境安装

```bash
# JDK 21
sudo apt update && sudo apt install openjdk-21-jdk -y

# MySQL 8
sudo apt install mysql-server -y

# Nginx
sudo apt install nginx -y
```

## 2. 数据库初始化

```bash
# 安全配置（可选）
sudo mysql_secure_installation

# 创建数据库和用户
sudo mysql
```

```sql
CREATE DATABASE iblog DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'lblog'@'localhost' IDENTIFIED BY '你的密码';
GRANT ALL PRIVILEGES ON iblog.* TO 'lblog'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

```bash
# 导入表结构和初始数据
mysql -u lblog -p iblog < v1.0.sql
```

`v1.0.sql` 包含全部表结构和初始数据（角色、管理员账号、站点配置等），具体内容见脚本末尾的 `-- 初始化数据` 部分。

## 3. 目录结构

```
/home/ubuntu/proj/
├── lblog-server-0.0.1-SNAPSHOT.jar   # 后端 JAR
├── lblog-web/dist/                    # 前端静态文件
├── uploads/                           # 图片上传目录
├── logs/                              # 应用日志
└── start.sh                           # 启动脚本
```

## 4. Nginx 配置

```bash
sudo nano /etc/nginx/sites-available/lblog
```

```nginx
server {
    listen 80;
    server_name _;

    root /home/ubuntu/proj/lblog-web/dist;
    index index.html;

    # SPA 路由回退
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 代理（注意后端 context-path 是 /iblogserver）
    location /api/ {
        proxy_pass http://127.0.0.1:8099/iblogserver/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # 上传文件代理（必须带 /iblogserver 前缀）
    location /uploads/ {
        proxy_pass http://127.0.0.1:8099/iblogserver/uploads/;
        proxy_set_header Host $host;
    }
}
```

> 注意：`proxy_pass` 必须带上后端的 context-path `/iblogserver`，否则请求进不了 Spring MVC 会直接 404。

```bash
sudo ln -sf /etc/nginx/sites-available/lblog /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default

# 修复 home 目录权限（Ubuntu 默认禁止 nginx 访问）
sudo chmod o+x /home/ubuntu

sudo nginx -t && sudo systemctl restart nginx
```

## 5. 构建

### 前端

```bash
cd lblog-web
npm install
npm run build
# 产物：dist/
```

上传：`scp -r dist/* ubuntu@<IP>:/home/ubuntu/proj/lblog-web/`

### 后端

```bash
cd lblog-server
# 必须使用 JDK 21（Spring Boot 3.5 + Maven Plugin 要求 Java 17+，项目指定 java.version=21）
export JAVA_HOME=/path/to/jdk-21
export PATH=$JAVA_HOME/bin:$PATH
mvn package -DskipTests -q
# 产物：target/lblog-server-0.0.1-SNAPSHOT.jar（约 59MB fat JAR）
```

上传：`scp target/lblog-server-0.0.1-SNAPSHOT.jar ubuntu@<IP>:/home/ubuntu/proj/`

> 如果本地 Maven 使用 JDK 8，需先设置 `JAVA_HOME` 指向 JDK 21，否则 Spring Boot Maven Plugin 3.5.7 会报 `UnsupportedClassVersionError`。

## 6. 启动脚本

```bash
#!/bin/bash
# /home/ubuntu/proj/start.sh

mkdir -p /home/ubuntu/proj/logs /home/ubuntu/proj/uploads

nohup env DB_PASSWORD=你的密码 java \
  -Xms256m -Xmx512m \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/home/ubuntu/proj/logs \
  -jar /home/ubuntu/proj/lblog-server-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --spring.ai.deepseek.api-key=你的AIKey \
  --lblog.upload-dir=/home/ubuntu/proj/uploads \
  > /home/ubuntu/proj/logs/app.log 2>&1 &
echo "PID: $!"
```

```bash
chmod +x /home/ubuntu/proj/start.sh
./start.sh
```

> **AI Key 配置**：命令行 `--spring.ai.deepseek.api-key=xxx` 会直接覆盖 `application-ai.yml` 中的值，无需改配置文件重新打包。

## 7. 常用命令

```bash
# 查看日志
tail -f /home/ubuntu/proj/logs/app.log

# 停止服务
pkill -f 'lblog-server.*SNAPSHOT'

# 重启 Nginx
sudo systemctl restart nginx

# 重启后端
pkill -f 'lblog-server.*SNAPSHOT' && ./start.sh
```

## 8. JVM 参数说明

| 参数 | 说明 |
|---|---|
| `-Xms256m -Xmx512m` | 堆内存 256M 起步，最大 512M（适配 2G 服务器） |
| `-XX:+HeapDumpOnOutOfMemoryError` | OOM 时自动 dump 堆快照 |
| `-XX:HeapDumpPath` | 堆快照存放路径 |

如需 GC 调优可追加：`-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Xlog:gc*:file=/home/ubuntu/proj/logs/gc.log:time,level,tags:filecount=5,filesize=10M`

> 2c2g 低并发博客场景默认 GC 足够，非必要不加。

## 9. 图片迁移

图片存储在 `lblog.upload-dir` 目录，与 JAR 独立。换服务器需拷贝：

```bash
# 同步图片文件
scp -r /home/ubuntu/proj/uploads/* user@new-host:/home/ubuntu/proj/uploads/

# 导出 images 表（MySQL）
mysqldump -u lblog -p iblog images image_usages | mysql -h new-host -u lblog -p iblog
```

## 10. 带宽优化

```nginx
# 在 nginx server 块中添加
gzip on;
gzip_types text/plain application/json text/css application/javascript;
gzip_min_length 256;

# 静态资源强缓存（文件名带 hash）
location /assets/ {
    expires 1y;
    add_header Cache-Control "public, immutable";
}
```
