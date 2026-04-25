import type { Post, Category, Tag, Series } from '../types';

export const mockCategories: Category[] = [
  { id: 1, name: '后端', slug: 'backend', parentId: null, description: '后端开发', sortOrder: 0, postCount: 42 },
  { id: 2, name: '前端', slug: 'frontend', parentId: null, description: '前端开发', sortOrder: 1, postCount: 35 },
  { id: 3, name: 'Android', slug: 'android', parentId: null, description: 'Android开发', sortOrder: 2, postCount: 18 },
  { id: 4, name: 'iOS', slug: 'ios', parentId: null, description: 'iOS开发', sortOrder: 3, postCount: 12 },
  { id: 5, name: '人工智能', slug: 'ai', parentId: null, description: 'AI相关', sortOrder: 4, postCount: 28 },
  { id: 6, name: '开发工具', slug: 'tools', parentId: null, description: '开发工具', sortOrder: 5, postCount: 15 },
  { id: 7, name: '代码人生', slug: 'life', parentId: null, description: '程序员生活', sortOrder: 6, postCount: 8 },
  { id: 8, name: '阅读', slug: 'reading', parentId: null, description: '读书笔记', sortOrder: 7, postCount: 6 },
];

export const mockTags: Tag[] = [
  { id: 1, name: 'Java', slug: 'java', postCount: 25 },
  { id: 2, name: 'Spring Boot', slug: 'spring-boot', postCount: 18 },
  { id: 3, name: 'React', slug: 'react', postCount: 15 },
  { id: 4, name: 'TypeScript', slug: 'typescript', postCount: 12 },
  { id: 5, name: 'MySQL', slug: 'mysql', postCount: 10 },
  { id: 6, name: 'Redis', slug: 'redis', postCount: 8 },
  { id: 7, name: 'Docker', slug: 'docker', postCount: 7 },
  { id: 8, name: '算法', slug: 'algorithm', postCount: 9 },
  { id: 9, name: '微服务', slug: 'microservice', postCount: 6 },
  { id: 11, name: '数据结构与算法分析实战', slug: 'data-structure', postCount: 5 },
];

export const mockSeries: Series[] = [
  { id: 1, title: 'Spring Boot 实战', slug: 'spring-boot-in-action', description: '从零开始学习Spring Boot', coverImageUrl: null, categoryId: 1, isCompleted: 0, sortOrder: 0, postCount: 12 },
  { id: 2, title: 'React 进阶指南', slug: 'react-advanced', description: 'React高级用法与最佳实践', coverImageUrl: null, categoryId: 2, isCompleted: 1, sortOrder: 1, postCount: 8 },
  { id: 3, title: 'MySQL 调优实战', slug: 'mysql-tuning', description: 'MySQL性能调优系列', coverImageUrl: null, categoryId: 1, isCompleted: 0, sortOrder: 2, postCount: 5 },
];

// 文章详情 - 正文内容
export const mockPostBodies: Record<number, string> = {
  1: `## 概述

Spring Boot 3.0 是 Spring Boot 历史上的一个重要里程碑，它在 Java 17 的基础上引入了许多令人兴奋的新特性。

## 最低要求：Java 17

从 Spring Boot 3.0 开始，**Java 17 成为最低版本要求**。

### Java 17 新特性

Java 17 带来了密封类、记录类型、模式匹配等新特性。

\`\`\`java
public record User(String name, String email) {
  // record 类型
}

public sealed class Shape permits Circle, Rectangle { }
public final class Circle extends Shape { }
public final class Rectangle extends Shape { }
\`\`\`

#### 为什么升级到 Java 17

LTS 版本、长期支持、性能提升显著。

## GraalVM 原生镜像支持

| 特性 | 说明 |
|------|------|
| AOT 编译 | 提前编译为机器码，启动速度提升 10 倍 |
| 内存优化 | 大幅减少运行时内存占用 |
| 无需 JVM | 生成独立的可执行文件 |

\`\`\`xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.0.0</version>
</dependency>
\`\`\`

> 原生镜像并不适合所有场景，它更适合微服务和 Serverless 架构。

## 可观测性增强

引入 Micrometer Observation 作为统一的观测门面：

\`\`\`java
@Observed(name = "user.service")
public class UserService {
    @Observed(name = "getUser")
    public User getUser(Long id) {
        return userRepository.findById(id);
    }
}
\`\`\`

## 总结

Spring Boot 3.0 是一个现代化的版本，它为未来的 Java 开发奠定了基础。`,
  2: `## React 19 新 Hook 详解

React 19 带来了多个革命性的新 Hook。

## useActionState

\`\`\`tsx
function CommentForm() {
  const [state, submitAction, isPending] = useActionState(
    async (prevState, formData) => {
      const res = await fetch('/api/comments', { method: 'POST', body: formData });
      return await res.json();
    },
    null
  );
  return (
    <form action={submitAction}>
      <textarea name="content" rows={4} />
      <button type="submit" disabled={isPending}>
        {isPending ? '提交中...' : '发表评论'}
      </button>
    </form>
  );
}
\`\`\`

## useOptimistic

\`\`\`tsx
function MessageList({ messages, sendMessage }) {
  const [optimisticMessages, addOptimisticMessage] = useOptimistic(
    messages,
    (state, newMessage) => [...state, { ...newMessage, sending: true }]
  );
  async function handleSend(formData) {
    addOptimisticMessage({ text: formData.get('message'), id: Date.now() });
    await sendMessage(formData);
  }
}
\`\`\`

## 总结

React 19 的这些新 Hook 让代码更简洁、更直观。`,
  3: `## 慢查询定位

\`\`\`sql
SET slow_query_log = 1;
SET long_query_time = 1;
\`\`\`

## 使用 EXPLAIN 分析

\`\`\`sql
EXPLAIN SELECT * FROM posts WHERE title LIKE '%Spring%';
\`\`\`

| 列名 | 说明 | 优化目标 |
|------|------|----------|
| type | 访问类型 | 至少达到 range |
| rows | 扫描行数 | 越小越好 |
| Extra | 附加信息 | 出现 Using filesort 需要优化 |

## 索引优化

> 最左前缀原则是复合索引的核心。`,
  4: `## Docker Compose 编排微服务

### 基本配置

\`\`\`yaml
version: '3.8'
services:
  nacos:
    image: nacos/nacos-server:2.2.0
    ports:
      - "8848:8848"
    environment:
      MODE: standalone

  gateway:
    build: ./gateway
    ports:
      - "8080:8080"
    depends_on:
      - nacos
\`\`\`

## 总结

Docker Compose 大大简化了本地开发环境的搭建。`,
  5: `## 类型体操入门

TypeScript 的类型系统是图灵完备的。

### 基础类型

\`\`\`typescript
type User = {
  name: string;
  age: number;
};

type Admin = User & { role: 'admin' };
\`\`\`

### 条件类型

\`\`\`typescript
type IsString<T> = T extends string ? true : false;
type Result = IsString<'hello'>; // true
\`\`\`

## 总结

掌握类型体操需要大量练习。`,
  6: `## Redis 分布式锁

### SETNX 实现

\`\`\`java
Boolean locked = redisTemplate.opsForValue()
    .setIfAbsent("lock:key", "value", 30, TimeUnit.SECONDS);
if (Boolean.TRUE.equals(locked)) {
    try {
        // 执行业务逻辑
    } finally {
        redisTemplate.delete("lock:key");
    }
}
\`\`\`

> 注意：需要确保解锁操作的原子性。`,
  7: `## Python 爬虫基础

### requests 库

\`\`\`python
import requests

response = requests.get('https://api.example.com/data')
data = response.json()
print(data)
\`\`\`

### BeautifulSoup 解析

\`\`\`python
from bs4 import BeautifulSoup

soup = BeautifulSoup(html, 'html.parser')
titles = soup.find_all('h2', class_='title')
\`\`\`

## 总结

爬虫开发需遵守网站的 robots.txt 协议。`,
  8: `## 动态规划入门

### 斐波那契数列

\`\`\`java
public int fib(int n) {
    if (n <= 1) return n;
    int[] dp = new int[n + 1];
    dp[0] = 0; dp[1] = 1;
    for (int i = 2; i <= n; i++) {
        dp[i] = dp[i - 1] + dp[i - 2];
    }
    return dp[n];
}
\`\`\`

### 背包问题

\`\`\`java
public int knapsack(int[] weights, int[] values, int capacity) {
    int n = weights.length;
    int[][] dp = new int[n + 1][capacity + 1];
    for (int i = 1; i <= n; i++) {
        for (int w = 0; w <= capacity; w++) {
            if (weights[i - 1] <= w) {
                dp[i][w] = Math.max(
                    dp[i - 1][w],
                    dp[i - 1][w - weights[i - 1]] + values[i - 1]
                );
            } else {
                dp[i][w] = dp[i - 1][w];
            }
        }
    }
    return dp[n][capacity];
}
\`\`\`

> 动态规划的核心是找到状态转移方程。`,
};


export const mockPosts: Post[] = [
  {
    id: 1, title: 'Spring Boot 3.0 新特性全面解析', slug: 'spring-boot-3-features', excerpt: '本文全面介绍Spring Boot 3.0带来的新特性，包括对Java 17的最低要求、GraalVM原生镜像支持、可观测性增强等重要变更。', featuredImage: null, status: 1, authorId: 1, categoryId: 1, publishedAt: '2026-04-24T10:00:00Z', createdAt: '2026-04-24T10:00:00Z', updatedAt: '2026-04-24T10:00:00Z',
    author: { id: 1, username: 'ekko', nickname: 'Ekko', email: 'ekko@lblog.com', avatar: null, role: 'admin' },
    category: { id: 1, name: '后端', slug: 'backend', parentId: null, description: null, sortOrder: 0 },
    tags: [{ id: 1, name: 'Java', slug: 'java' }, { id: 2, name: 'Spring Boot', slug: 'spring-boot' }],
    viewCount: 1256, likeCount: 89, commentCount: 23,
  },
  {
    id: 2, title: 'React 19 Hooks 最佳实践', slug: 'react-19-hooks', excerpt: '深入探讨React 19中Hooks的使用技巧和最佳实践，包括新引入的useActionState、useOptimistic等Hook的详细用法。', featuredImage: null, status: 1, authorId: 1, categoryId: 2, publishedAt: '2026-04-23T14:30:00Z', createdAt: '2026-04-23T14:30:00Z', updatedAt: '2026-04-23T14:30:00Z',
    author: { id: 1, username: 'ekko', nickname: 'Ekko', email: 'ekko@lblog.com', avatar: null, role: 'admin' },
    category: { id: 2, name: '前端', slug: 'frontend', parentId: null, description: null, sortOrder: 1 },
    tags: [{ id: 3, name: 'React', slug: 'react' }, { id: 4, name: 'TypeScript', slug: 'typescript' }, { id: 99, name: '前端工程化与性能优化最佳实践指南', slug: 'frontend-best-practice' }],
    viewCount: 983, likeCount: 67, commentCount: 15,
  },
  {
    id: 3, title: 'MySQL 索引优化实战：从慢查询到毫秒级响应', slug: 'mysql-index-optimization', excerpt: '通过真实案例讲解MySQL索引优化的全过程，从慢查询定位、执行计划分析到索引设计，帮助你彻底掌握索引优化技巧。', featuredImage: null, status: 1, authorId: 1, categoryId: 1, publishedAt: '2026-04-22T09:15:00Z', createdAt: '2026-04-22T09:15:00Z', updatedAt: '2026-04-22T09:15:00Z',
    author: { id: 1, username: 'ekko', nickname: 'Ekko', email: 'ekko@lblog.com', avatar: null, role: 'admin' },
    category: { id: 1, name: '后端', slug: 'backend', parentId: null, description: null, sortOrder: 0 },
    tags: [{ id: 5, name: 'MySQL', slug: 'mysql' }],
    viewCount: 2341, likeCount: 156, commentCount: 41,
  },
  {
    id: 4, title: 'Docker Compose 编排微服务架构实践', slug: 'docker-compose-microservice', excerpt: '使用Docker Compose搭建完整的微服务开发环境，包含服务注册发现、配置中心、网关等基础组件的容器化部署。', featuredImage: null, status: 1, authorId: 1, categoryId: 1, publishedAt: '2026-04-21T16:45:00Z', createdAt: '2026-04-21T16:45:00Z', updatedAt: '2026-04-21T16:45:00Z',
    author: { id: 1, username: 'ekko', nickname: 'Ekko', email: 'ekko@lblog.com', avatar: null, role: 'admin' },
    category: { id: 1, name: '后端', slug: 'backend', parentId: null, description: null, sortOrder: 0 },
    tags: [{ id: 7, name: 'Docker', slug: 'docker' }, { id: 9, name: '微服务', slug: 'microservice' }],
    viewCount: 756, likeCount: 45, commentCount: 12,
  },
  {
    id: 5, title: 'TypeScript 5.x 类型体操进阶', slug: 'typescript-type-gymnastics', excerpt: '从基础类型到高级类型编程，全面掌握TypeScript类型系统的强大能力，包括条件类型、映射类型、模板字面量类型等。', featuredImage: null, status: 1, authorId: 1, categoryId: 2, publishedAt: '2026-04-20T11:20:00Z', createdAt: '2026-04-20T11:20:00Z', updatedAt: '2026-04-20T11:20:00Z',
    author: { id: 1, username: 'ekko', nickname: 'Ekko', email: 'ekko@lblog.com', avatar: null, role: 'admin' },
    category: { id: 2, name: '前端', slug: 'frontend', parentId: null, description: null, sortOrder: 1 },
    tags: [{ id: 4, name: 'TypeScript', slug: 'typescript' }],
    viewCount: 612, likeCount: 38, commentCount: 8,
  },
  {
    id: 6, title: 'Redis 分布式锁实现与踩坑记录', slug: 'redis-distributed-lock', excerpt: '详细讲解Redis分布式锁的多种实现方式，包括SETNX、RedLock算法，以及生产环境中遇到的常见问题和解决方案。', featuredImage: null, status: 1, authorId: 1, categoryId: 1, publishedAt: '2026-04-19T08:30:00Z', createdAt: '2026-04-19T08:30:00Z', updatedAt: '2026-04-19T08:30:00Z',
    author: { id: 1, username: 'ekko', nickname: 'Ekko', email: 'ekko@lblog.com', avatar: null, role: 'admin' },
    category: { id: 1, name: '后端', slug: 'backend', parentId: null, description: null, sortOrder: 0 },
    tags: [{ id: 6, name: 'Redis', slug: 'redis' }, { id: 1, name: 'Java', slug: 'java' }],
    viewCount: 1893, likeCount: 112, commentCount: 34,
  },
  {
    id: 7, title: 'Python 爬虫实战：从入门到进阶', slug: 'python-spider', excerpt: '从requests到Scrapy，系统学习Python网络爬虫开发，包含反爬策略应对、数据清洗和存储等实用技巧。', featuredImage: null, status: 1, authorId: 1, categoryId: 5, publishedAt: '2026-04-18T15:00:00Z', createdAt: '2026-04-18T15:00:00Z', updatedAt: '2026-04-18T15:00:00Z',
    author: { id: 1, username: 'ekko', nickname: 'Ekko', email: 'ekko@lblog.com', avatar: null, role: 'admin' },
    category: { id: 5, name: '人工智能', slug: 'ai', parentId: null, description: null, sortOrder: 4 },
    tags: [{ id: 10, name: 'Python', slug: 'python' }],
    viewCount: 445, likeCount: 29, commentCount: 7,
  },
  {
    id: 8, title: '算法刷题笔记：动态规划从零到一', slug: 'dp-from-zero-to-one', excerpt: '系统整理动态规划的学习路径，从斐波那契数列到背包问题，配合LeetCode经典题目逐层深入。', featuredImage: null, status: 1, authorId: 1, categoryId: 1, publishedAt: '2026-04-17T13:20:00Z', createdAt: '2026-04-17T13:20:00Z', updatedAt: '2026-04-17T13:20:00Z',
    author: { id: 1, username: 'ekko', nickname: 'Ekko', email: 'ekko@lblog.com', avatar: null, role: 'admin' },
    category: { id: 1, name: '后端', slug: 'backend', parentId: null, description: null, sortOrder: 0 },
    tags: [{ id: 8, name: '算法', slug: 'algorithm' }, { id: 1, name: 'Java', slug: 'java' }],
    viewCount: 567, likeCount: 41, commentCount: 18,
  },
];
