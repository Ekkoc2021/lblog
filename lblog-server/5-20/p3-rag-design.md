# P3 — 检索增强 RAG

> 阶段目标：当 AI 不知道答案时，能主动从外部知识库检索相关资料
> 前置依赖：P0（基础存储），可与 P1/P2 并行
> 设计原则：**渐进增强** — 先上线基础检索链，再逐步加高级特性（混合搜索、查询改写、重排序、知识图谱）

---

## 目录

1. [设计决策](#1-设计决策)
2. [RAG 术语与命名规范](#2-rag-术语与命名规范)
3. [数据库设计](#3-数据库设计)
4. [包结构与类设计](#4-包结构与类设计)
5. [核心流程设计](#5-核心流程设计)
6. [层次检索](#6-层次检索hierarchical--multi-level-retrieval)
7. [知识图谱增强（Future）](#7-知识图谱增强-future)
8. [实施步骤](#8-实施步骤)
9. [测试策略](#9-测试策略)
10. [验收标准](#10-验收标准)
11. [相关文档](#11-相关文档)

---

## 1. 设计决策

### 1.1 RAG 整体架构模式

采用 **Advisor 链注入 + 独立 RAG Service** 模式：

```
用户提问
    │
    ▼ Advisor 链（新增 RAG Advisor）
    ├─ ChatHistoryAdvisor（加载历史）
    ├─ RAGAdvisor（★ 新增：改写 → 检索 → 注入）
    ├─ DeepSeekToolCallAdvisor（工具调用）
    ├─ CompressionAdvisor（压缩）
    └─ ChatClient → LLM
```

RAGAdvisor 在每次用户提问时触发，不与 `ChatHistoryAdvisor` 耦合。

### 1.2 为什么新建知识库表而不是复用现有数据

| 方案 | 问题 |
|------|------|
| 直接对现有 `posts` / `comments` 建索引 | 数据结构差异大，通用检索引擎难统一处理 |
| 将全量文档实时喂给 LLM | Token 不够，成本高 |
| 新建 `ai_knowledge_base` + `ai_doc_chunks` 统一知识库 | ✅ 所有知识源统一为"文档-分块-嵌入"模型，检索层与业务解耦 |

### 1.3 存储方案选择

| 方案 | 优点 | 缺点 | 选择 |
|------|------|------|------|
| MySQL TEXT + JSON embedding | 零新增组件，事务一致 | 全表扫描，十万级以下可用 | ✅ P3 核心 |
| MySQL VECTOR 类型（≥8.4/9.0） | 原生向量索引，性能好 | 需要升级 MySQL | ⏳ 未来升级后迁入 |
| pgvector | 成熟向量方案 | 要切 PostgreSQL | ❌ 不纳入 |
| Elasticsearch | 全文检索强 | 运维重，与现有技术栈不匹配 | ❌ 不纳入 |
| 专用向量数据库（Milvus/Qdrant） | 大规模高性能 | 新基建 | ❌ 不纳入 |

### 1.4 RAG Advisor 职责边界

```
RAGAdvisor.before(request, chain):
  1. 判断是否需要检索（规则 + LLM 路由）
     ├─ 系统指令类问题（"你的名字是什么"）→ 跳过检索
     ├─ 通用闲聊（"今天天气"）→ 跳过检索
     └─ 需知识的问题 → 执行检索流程
  2. 改写查询（可选）
     └─ 调用 LLM 将模糊问题转为精确检索查询
  3. 执行检索
     ├─ 向量检索（cosine similarity）
     ├─ BM25 检索（MySQL FULLTEXT）
     └─ 融合排序 → 重排序
  4. 注入上下文
     └─ 将检索结果以 [1] [2] [3] 引用格式拼入 system prompt
```

### 1.5 分块策略：Small-to-Chunk（从小到大）

```
原始文档
    │
    ├─ 大块（parent chunk, ~2000 token）
    │   按章节/标题边界分割，保留完整语境
    │
    └─ 小块（child chunk, ~200 token）
        在大块内按段落/句子边界再分割，用于索引
        每个小块引用其所属大块

检索时：
  用户查询 → 匹配小块（高精度） → 返回大块（完整语境）
```

---

## 2. RAG 术语与命名规范

### 2.1 统一术语表

| 中文 | 英文（代码/数据库） | 定义 | 使用位置 |
|------|---------------------|------|---------|
| 知识库 | `KnowledgeBase` | 知识来源的顶层分类，如"项目文档"、"API 规范" | 领域模型、表名 |
| 文档 | `DocDocument` | 一篇完整的知识条目，标题 + 正文 | 领域模型 |
| 文档分块 | `DocChunk` | 文档按策略切分后的片段，含 embedding | 领域模型、检索核心 |
| 分块策略 | `ChunkStrategy` | 定义如何分割文档（段落/标题/固定长度/语义） | 策略接口 |
| 向量 | `Vector` / `Embedding` | 文本的浮点数向量表示 | 存储、相似度计算 |
| 向量维度 | `VectorDimension` | DeepSeek embedding 输出维度（1024） | 配置常量 |
| 余弦相似度 | `CosineSimilarity` | 向量间相似度计算函数 | 检索打分 |
| BM25 检索 | `Bm25Retrieval` | 关键词精确匹配 + TF-IDF 加权 | 混合检索 |
| 混合搜索 | `HybridSearch` / `HybridRetrieval` | BM25 + 向量加权融合 | 检索融合层 |
| 融合排序 | `FusionRank` / `ReciprocalRankFusion` | 多路结果加权合并 | 排序 |
| 重排序 | `ReRanker` / `CrossEncoderReranker` | 初检结果用交叉编码器重排 | 排序优化 |
| 查询改写 | `QueryRewrite` | 用 LLM 将模糊问题转为精确查询 | 检索前处理 |
| 检索增强 | `RetrievalAugmented` / `RAG` | 从外部知识库检索结果注入 LLM 上下文 | 整体架构 |
| 引用 | `Citation` | 检索结果附带的来源标记 `[1]` `[2]` | 注入格式 |
| 命名实体 | `Entity` | 文档中提取的关键概念（类名、API、术语） | 知识图谱增强 |
| 实体链接 | `EntityLinking` | 将查询中的实体与知识库实体关联 | 知识图谱增强 |
| 知识图谱 | `KnowledgeGraph` | 实体 + 关系的图结构知识表示 | 增强检索（Future） |
| 元数据过滤 | `MetadataFilter` | 按时间/来源/标签精确过滤检索结果 | 检索后处理 |
| 检索路由 | `RetrievalRouter` | 判断当前问题是否需要/适合检索 | 前处理决策 |
| 上下文注入 | `ContextInjection` | 将检索结果拼入 LLM 上下文的格式策略 | Advisor 核心 |

### 2.2 代码命名约定

```
# 包路径
ai/rag/                              # RAG 模块根包
ai/rag/advisor/RAGAdvisor.java       # Advisor 入口
ai/rag/retrieval/                    # 检索相关
ai/rag/chunking/                     # 分块策略
ai/rag/embedding/                    # 向量化
ai/rag/rerank/                       # 重排序
ai/rag/rewrite/                      # 查询改写
ai/rag/fusion/                       # 融合排序
ai/rag/knowledgegraph/               # 知识图谱增强（Future）
ai/rag/domain/                       # 领域模型
ai/rag/mapper/                       # MyBatis mapper
ai/rag/service/                      # 业务服务

# 接口命名
ChunkStrategy          # 分块策略接口
ParagraphChunker       # 段落分块实现
SemanticChunker        # 语义分块实现
EmbeddingService       # 向量化接口
VectorStore            # 向量存储接口
MySQLVectorStore       # MySQL 向量存储实现
HybridRetriever        # 混合检索器
ReciprocalRankFusion   # 倒数排序融合
QueryRewriter          # 查询改写接口
LLMQueryRewriter       # LLM 查询改写实现
CrossEncoderReranker   # 交叉编码重排序

# 配置前缀
ai.rag.enabled             # 全局开关
ai.rag.chunk-size          # 分块大小
ai.rag.chunk-overlap       # 分块重叠
ai.rag.top-k               # 检索 top K
ai.rag.bm25-weight         # BM25 权重
ai.rag.vector-weight       # 向量权重
```

### 2.3 数据库命名

```
# 核心表
ai_knowledge_base          # 知识库（多数据源统一入口）
ai_doc_documents           # 文档（原始内容）
ai_doc_chunks              # 文档分块（含 embedding）
ai_doc_entities            # 文档实体（知识图谱用，预留）

# 核心字段命名
chunk_index                # 分块序号
chunk_strategy             # 分块策略标识
parent_chunk_id            # 所属大块 ID（small-to-chunk）
embedding_vector            # 向量（JSON 数组字符串）
embedding_model            # 向量化模型名
relevance_score            # 相关性得分
citation_index             # 引用序号 [1][2][3]
```

---

## 3. 数据库设计

### 3.1 DDL

```sql
-- ============================================================
-- Table: ai_knowledge_base
-- 说明：知识库分类表，每种数据源是一个独立的知识库
-- ============================================================
CREATE TABLE IF NOT EXISTS `ai_knowledge_base` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `name`            VARCHAR(64)     NOT NULL                 COMMENT '知识库标识，如 project-docs / api-spec',
    `display_name`    VARCHAR(128)    DEFAULT NULL             COMMENT '展示名',
    `description`     TEXT            DEFAULT NULL             COMMENT '知识库描述',
    `source_type`     VARCHAR(32)     NOT NULL                 COMMENT '数据源类型：manual / scraping / import / api',
    `doc_count`       INT             NOT NULL DEFAULT 0       COMMENT '文档总数',
    `chunk_count`     INT             NOT NULL DEFAULT 0       COMMENT '分块总数',
    `is_active`       TINYINT         NOT NULL DEFAULT 1       COMMENT '启用状态',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_name` (`name`),
    KEY `idx_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 知识库';


-- ============================================================
-- Table: ai_doc_documents
-- 说明：文档表，存储完整的原始文档内容
-- ============================================================
CREATE TABLE IF NOT EXISTS `ai_doc_documents` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `kb_id`           BIGINT          NOT NULL                 COMMENT '所属知识库',
    `title`           VARCHAR(255)    NOT NULL                 COMMENT '文档标题',
    `content`         LONGTEXT        NOT NULL                 COMMENT '文档原始内容（Markdown/Text）',
    `content_type`    VARCHAR(32)     DEFAULT 'text'           COMMENT '内容格式：markdown / plain-text / html',
    `source`          VARCHAR(512)    DEFAULT NULL             COMMENT '来源路径（如文件路径、URL）',
    `checksum`        VARCHAR(64)     DEFAULT NULL             COMMENT '内容校验和，用于去重更新',
    `token_count`     INT             DEFAULT 0                COMMENT '文档 token 数',
    `chunk_count`     INT             DEFAULT 0                COMMENT '分块数',
    `metadata`        JSON            DEFAULT NULL             COMMENT '扩展元数据：作者、标签、版本等',
    `is_active`       TINYINT         NOT NULL DEFAULT 1       COMMENT '启用状态，0=下架不计入检索',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_kb` (`kb_id`),
    KEY `idx_source` (`source`),
    CONSTRAINT `fk_doc_kb` FOREIGN KEY (`kb_id`) REFERENCES `ai_knowledge_base` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 知识文档';


-- ============================================================
-- Table: ai_doc_chunks
-- 说明：文档分块表，存储分割后的文本片段及其向量
-- ============================================================
CREATE TABLE IF NOT EXISTS `ai_doc_chunks` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `doc_id`          BIGINT          NOT NULL                 COMMENT '所属文档',
    `kb_id`           BIGINT          NOT NULL                 COMMENT '所属知识库（冗余，加速过滤）',
    `chunk_index`     INT             NOT NULL                 COMMENT '分块序号（文档内从 0 递增）',
    `content`         TEXT            NOT NULL                 COMMENT '分块文本',
    `token_count`     INT             DEFAULT 0                COMMENT '本块 token 数估算',
    `embedding_vector` JSON          DEFAULT NULL              COMMENT '向量：[0.001, -0.023, ...]，JSON 浮点数组',
    `embedding_model` VARCHAR(64)    DEFAULT NULL              COMMENT '生成向量的模型名',
    `parent_chunk_id` BIGINT         DEFAULT NULL              COMMENT '所属大块 ID（small-to-chunk），NULL=自身是大块',
    `chunk_strategy`  VARCHAR(32)    DEFAULT 'paragraph'       COMMENT '分块策略：paragraph / semantic / heading / fixed',
    `section_title`   VARCHAR(255)   DEFAULT NULL              COMMENT '所属章节标题（用于上下文和引用）',
    `metadata`        JSON           DEFAULT NULL              COMMENT '扩展元数据',
    `is_active`       TINYINT        NOT NULL DEFAULT 1        COMMENT '启用状态',
    `created_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_doc` (`doc_id`),
    KEY `idx_kb` (`kb_id`),
    KEY `idx_parent` (`parent_chunk_id`),
    FULLTEXT INDEX `ft_content` (`content`)                   COMMENT 'BM25 全文检索'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 文档分块';


-- ============================================================
-- Table: ai_doc_entities（预留，知识图谱增强用）
-- 说明：从文档中提取的命名实体，用于实体增强检索
-- ============================================================
CREATE TABLE IF NOT EXISTS `ai_doc_entities` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `kb_id`           BIGINT          NOT NULL                 COMMENT '所属知识库',
    `entity_name`     VARCHAR(255)    NOT NULL                 COMMENT '实体名（如 UserService、@PostMapping）',
    `entity_type`     VARCHAR(32)     NOT NULL                 COMMENT '实体类型：class / api / config / term',
    `aliases`         JSON            DEFAULT NULL             COMMENT '别名列表：["UserServiceImpl", "IUserService"]',
    `description`     TEXT            DEFAULT NULL             COMMENT '实体简要描述',
    `chunk_ids`       JSON            NOT NULL                 COMMENT '关联的分块 ID 列表：[101, 105, 203]',
    `relation_ids`    JSON            DEFAULT NULL             COMMENT '关联关系 ID 列表，指向 ai_doc_relations',
    `first_seen_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_kb_type` (`kb_id`, `entity_type`),
    KEY `idx_name` (`entity_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档实体（知识图谱）';


-- ============================================================
-- Table: ai_doc_relations（预留，知识图谱增强用）
-- 说明：实体关系表，存储实体间的关系三元组
-- ============================================================
CREATE TABLE IF NOT EXISTS `ai_doc_relations` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `kb_id`           BIGINT          NOT NULL                 COMMENT '所属知识库',
    `subject_id`      BIGINT          NOT NULL                 COMMENT '主体实体 ID',
    `predicate`       VARCHAR(64)     NOT NULL                 COMMENT '关系谓词：depends_on / extends / implements / contains / references / configured_by',
    `object_id`       BIGINT          NOT NULL                 COMMENT '客体实体 ID',
    `confidence`      DECIMAL(3,2)    DEFAULT 1.00             COMMENT '抽取置信度（0.00-1.00）',
    `source_chunk_id` BIGINT          DEFAULT NULL             COMMENT '来源分块 ID，可追溯原文',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_subject` (`subject_id`),
    KEY `idx_object` (`object_id`),
    KEY `idx_predicate` (`predicate`, `subject_id`, `object_id`),
    CONSTRAINT `fk_rel_subject` FOREIGN KEY (`subject_id`) REFERENCES `ai_doc_entities` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_rel_object` FOREIGN KEY (`object_id`) REFERENCES `ai_doc_entities` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体关系（知识图谱）';
```

### 3.2 索引说明

| 索引 | 用途 | 说明 |
|------|------|------|
| `ft_content`（FULLTEXT） | BM25 关键词检索 | MySQL 5.6+ InnoDB 自然语言搜索 |
| `idx_kb` (chunks) | 按知识库过滤 | 避免跨库干扰 |
| `idx_parent` (chunks) | small-to-chunk 关联 | 小块 → 大块回溯 |
| `idx_name` (entities) | 实体精确匹配 | 查询实体链接入口 |
| `idx_subject/object` (relations) | 关系遍历 | 邻接表索引 |

### 3.3 embedding_vector 存取格式

```json
// MySQL JSON 列中存储格式
[0.001234, -0.005678, 0.009012, ...]  // 1024 维浮点数组

// Java 映射
String embeddingJson = "[0.001234, -0.005678, ...]";  // 数据库存取
List<Float> embedding = objectMapper.readValue(embeddingJson, List.class);  // 内存计算
```

---

## 4. 包结构与类设计

### 4.1 包结构

```
ai/rag/
├── advisor/
│   └── RAGAdvisor.java                    # ★ 核心：RAG 上下文注入 Advisor
│
├── retrieval/                              # 检索层
│   ├── Retriever.java                     # 检索器接口
│   ├── VectorRetriever.java               # 向量检索
│   ├── Bm25Retriever.java                 # BM25 全文检索
│   └── HybridRetriever.java              # 混合检索（向量 + BM25 融合）
│
├── chunking/                               # 分块策略
│   ├── ChunkStrategy.java                 # 分块策略接口
│   ├── ParagraphChunker.java              # 段落分块
│   ├── HeadingChunker.java                # 标题边界分块
│   ├── FixedSizeChunker.java              # 固定长度分块
│   └── SemanticChunker.java              # 语义分块（Future）
│
├── embedding/                              # 向量化
│   ├── EmbeddingService.java              # 向量化接口
│   └── DeepSeekEmbeddingService.java      # DeepSeek embedding API 实现
│
├── rerank/                                 # 重排序
│   └── ReRanker.java                      # 重排序接口
│   └── LLMReranker.java                   # 用 LLM 做交叉编码重排
│
├── rewrite/                                # 查询改写
│   ├── QueryRewriter.java                 # 查询改写接口
│   └── LLMQueryRewriter.java             # LLM 改写实现
│
├── fusion/                                 # 融合排序
│   └── ReciprocalRankFusion.java          # 倒数排序融合（RRF）
│
├── knowledgegraph/                         # 知识图谱（P3.5 Future）
│   ├── EntityExtractor.java               # 实体抽取接口
│   ├── EntityLinker.java                  # 实体链接
│   ├── KnowledgeGraphRetriever.java       # 实体增强检索
│   └── RelationExtractor.java            # 关系抽取
│
├── domain/                                 # 领域模型
│   ├── KnowledgeBase.java                 # 知识库实体
│   ├── DocDocument.java                   # 文档实体
│   ├── DocChunk.java                      # 分块实体
│   ├── DocEntity.java                     # 文档实体（知识图谱）
│   ├── DocRelation.java                   # 实体关系（知识图谱）
│   ├── RetrievalResult.java               # 检索结果包装
│   └── Citation.java                      # 引用信息
│
├── mapper/                                 # MyBatis 映射
│   ├── KnowledgeBaseMapper.java
│   ├── DocDocumentMapper.java
│   ├── DocChunkMapper.java
│   ├── DocEntityMapper.java              # 知识图谱预留
│   └── DocRelationMapper.java            # 知识图谱预留
│
├── service/
│   ├── KnowledgeBaseService.java          # 知识库管理
│   ├── DocumentService.java               # 文档 CRUD + 重新索引
│   ├── RAGService.java                    # ★ 核心：检索编排
│   └── knowledgegraph/
│       ├── EntityService.java             # 实体管理（Future）
│       └── RelationService.java           # 关系管理（Future）
│
└── config/
    └── RAGConfig.java                     # RAG 配置属性 + Bean 注册
```

### 4.2 核心类设计

#### RAGAdvisor.java — RAG 上下文注入 Advisor

```java
/**
 * RAG Advisor：在每次用户提问前检索相关知识注入上下文。
 *
 * Advisor 链中位置：ChatHistoryAdvisor 之后，DeepSeekToolCallAdvisor 之前
 *
 * 流程：
 *   1. 从用户最新消息中提取查询文本
 *   2. 可选：路由判断是否需要检索
 *   3. 可选：查询改写（LLM 将模糊问题转为精确查询）
 *   4. 混合检索（BM25 + 向量）→ 融合排序 → 重排序
 *   5. 检索结果以引用格式注入 system prompt 尾部
 */
public class RAGAdvisor implements BaseAdvisor {

    private final RetrievalRouter router;         // 检索路由判断
    private final QueryRewriter queryRewriter;    // 查询改写
    private final HybridRetriever hybridRetriever; // 混合检索器
    private final ReRanker reRanker;              // 重排序
    private final RAGConfig ragConfig;            // 配置
    private final int order;

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        // 1. 提取用户最新消息
        String userQuery = extractUserMessage(request);
        if (userQuery == null || userQuery.isBlank()) {
            return chain.next(request);
        }

        // 2. 路由判断：此问题是否需要检索
        if (!router.shouldRetrieve(userQuery)) {
            return chain.next(request);
        }

        // 3. 查询改写（可选）
        List<String> queries = queryRewriter.rewrite(userQuery);
        if (queries == null || queries.isEmpty()) {
            queries = List.of(userQuery);
        }

        // 4. 执行混合检索 → 融合排序 → 重排序
        List<RetrievalResult> results = hybridRetriever.retrieve(
            queries, ragConfig.getTopK(), ragConfig.getKbFilter());

        List<RetrievalResult> reranked = reRanker.rerank(userQuery, results);

        // 5. 构造引用上下文，注入到 system prompt
        String citationBlock = buildCitationBlock(reranked);

        return request.mutate()
            .system(systemText -> systemText
                .text("\n\n---\n以下是从知识库中检索到的参考资料：\n")
                .text(citationBlock)
                .text("\n---\n请使用 [序号] 标注引用来源。"))
            .build();
    }
}
```

#### RAGService.java — 检索编排核心

```java
/**
 * RAG 检索编排服务。
 * 被 RAGAdvisor 调用，封装完整的检索流程：
 * 改写 → 检索 → 融合 → 重排序 → 返回
 */
public interface RAGService {

    /**
     * 完整检索流程（改写 + 检索 + 融合 + 重排序）。
     *
     * @param query    用户原始查询
     * @param topK     返回 top K 条结果
     * @param kbId     知识库 ID（null=全库检索）
     * @return 排序后的检索结果列表（含引用序号）
     */
    List<RetrievalResult> retrieve(String query, int topK, Long kbId);

    /**
     * 仅检索（不改写），用于改写后执行。
     */
    List<RetrievalResult> search(List<String> queries, int topK, Long kbId);

    /**
     * 向量检索（by cosine similarity）。
     */
    List<RetrievalResult> vectorSearch(List<Float> queryEmbedding, int topK, Long kbId);

    /**
     * BM25 全文检索。
     */
    List<RetrievalResult> bm25Search(String query, int topK, Long kbId);

    /**
     * 构建引用文本块。
     */
    String buildCitations(List<RetrievalResult> results);
}
```

#### Retriever.java — 检索器接口

```java
/**
 * 检索器接口。
 * 每种检索方式独立实现，由 HybridRetriever 调用。
 */
public interface Retriever {

    /**
     * 执行检索。
     *
     * @param query 检索查询
     * @param topK  返回 top K
     * @param filter 元数据过滤条件（可选）
     * @return 检索结果列表（未排序，带原始得分）
     */
    List<RetrievalResult> retrieve(String query, int topK, RetrievalFilter filter);

    /** 检索器类型标识：vector / bm25 / hybrid / entity */
    String type();
}
```

#### HybridRetriever.java — 混合检索器

```java
/**
 * 混合检索器：向量 + BM25 + 可选的实体增强。
 *
 * 融合策略：Reciprocal Rank Fusion (RRF)
 *   score = Σ 1 / (k + rank_i)
 *   其中 k=60（RRF 常数，控制融合平滑度）
 */
public class HybridRetriever {

    private final VectorRetriever vectorRetriever;
    private final Bm25Retriever bm25Retriever;
    private final ReciprocalRankFusion fusion;

    public List<RetrievalResult> retrieve(List<String> queries, int topK, Long kbId) {
        RetrievalFilter filter = kbId != null
            ? RetrievalFilter.of("kb_id", kbId)
            : null;

        // 每路检索各自取 topK*2 给融合留余量
        List<List<RetrievalResult>> allResults = new ArrayList<>();

        // BM25 检索（对每个改写查询）
        for (String q : queries) {
            allResults.add(bm25Retriever.retrieve(q, topK * 2, filter));
        }

        // 向量检索（只对原始查询）
        if (vectorRetriever.isAvailable()) {
            allResults.add(vectorRetriever.retrieve(queries.get(0), topK * 2, filter));
        }

        // RRF 融合 → 取 topK
        return fusion.merge(allResults, topK);
    }
}
```

#### EmbeddingService.java — 向量化接口

```java
/**
 * 向量化服务接口。
 * 将文本转为浮点数向量，供向量检索使用。
 */
public interface EmbeddingService {

    /**
     * 将单段文本转为向量。
     */
    List<Float> embed(String text);

    /**
     * 批量向量化（减少 API 调用）。
     */
    List<List<Float>> embedBatch(List<String> texts);

    /**
     * 获取向量维度（如 DeepSeek = 1024）。
     */
    int getDimension();

    /**
     * 获取当前使用的 embedding 模型名。
     */
    String getModelName();

    /**
     * 判断服务是否可用（API Key 配置等）。
     */
    boolean isAvailable();
}
```

#### QueryRewriter.java — 查询改写接口

```java
/**
 * 查询改写器。
 * 将用户自然语言问题转为 1-3 个精确检索查询，提高召回率。
 *
 * 示例：
 *   "用户表有哪些字段" → ["users table columns schema", "用户表 字段 定义 数据库"]
 */
public interface QueryRewriter {

    /**
     * 改写查询。
     *
     * @param userQuery 用户原始自然语言问题
     * @return 改写后的检索查询列表（1-3 个），返回 null/空列表表示不改写
     */
    List<String> rewrite(String userQuery);

    /**
     * 是否启用改写（可由配置控制）。
     */
    boolean isEnabled();
}
```

#### ReRanker.java — 重排序接口

```java
/**
 * 重排序器。
 * 对初检结果进行细粒度重新排序，最相关的结果排最前。
 */
public interface ReRanker {

    /**
     * 对检索结果重排序。
     *
     * @param query   用户原始查询
     * @param results 初检结果列表
     * @return 重排序后的结果（最相关在前）
     */
    List<RetrievalResult> rerank(String query, List<RetrievalResult> results);
}
```

#### ChunkStrategy.java — 分块策略接口

```java
/**
 * 分块策略接口。
 * 定义如何将一篇完整文档分割为若干片段。
 *
 * 扩展新策略只需实现此接口，在 DocumentService 中注册。
 */
public interface ChunkStrategy {

    /**
     * 对文档执行分块。
     *
     * @param document  原始文档
     * @param chunkSize  目标块大小（token 数）
     * @param overlap    块间重叠（token 数）
     * @return 分块列表
     */
    List<DocChunk> chunk(DocDocument document, int chunkSize, int overlap);

    /** 策略名称标识 */
    String name();
}
```

#### RetrievalResult.java — 检索结果

```java
/**
 * 检索结果包装。
 * 包含命中的分块及其相关性信息。
 */
@Data
@Builder
public class RetrievalResult {
    private DocChunk chunk;              // 命中的分块
    private DocDocument document;        // 所属文档（含标题、来源）
    private double score;                // 相关性得分
    private int rank;                    // 当前排序中的名次
    private int citationIndex;           // 引用序号 [1][2][3]...
    private String matchType;            // 匹配类型：vector / bm25 / hybrid / entity
    private List<String> matchedTerms;   // BM25 命中的关键词（可选）
}
```

#### RetrievalRouter.java — 检索路由

```java
/**
 * 检索路由：判断当前问题是否需要执行检索。
 * 避免对闲聊/系统指令类问题做无意义检索。
 */
public interface RetrievalRouter {

    /**
     * 判断是否需要检索。
     *
     * @param query 用户最新消息
     * @return true=需要检索，false=跳过检索直接交给 LLM
     */
    boolean shouldRetrieve(String query);
}
```

#### RAGConfig.java — RAG 配置属性

```java
@ConfigurationProperties(prefix = "ai.rag")
@Data
public class RAGConfig {
    /** 全局开关 */
    private boolean enabled = true;

    /** 分块大小（token 数） */
    private int chunkSize = 500;

    /** 分块重叠（token 数） */
    private int chunkOverlap = 50;

    /** 大块大小（small-to-chunk 用） */
    private int parentChunkSize = 2000;

    /** 检索返回条数 */
    private int topK = 5;

    /** 重排序后保留条数 */
    private int rerankTopK = 3;

    /** BM25 权重（混合搜索） */
    private double bm25Weight = 0.3;

    /** 向量权重（混合搜索） */
    private double vectorWeight = 0.7;

    /** RRF 融合常数 k */
    private int rrfConstant = 60;

    /** 查询改写启用 */
    private boolean queryRewriteEnabled = true;

    /** 知识库过滤（null=全库） */
    private String defaultKbName;

    /** 向量维度 */
    private int vectorDimension = 1024;

    /** embedding 模型名 */
    private String embeddingModel = "deepseek-embedding-v2";

    /** 检索路由最小长度（小于此长度不检索） */
    private int minQueryLength = 3;

    // ===== 知识图谱增强配置（Future） =====
    /** 实体增强启用 */
    private boolean entityAugmentationEnabled = false;

    /** 实体抽取的最大实体数 */
    private int maxEntitiesPerDoc = 50;

    /** 关系抽取置信度阈值 */
    private double relationConfidenceThreshold = 0.7;
}
```

---

## 5. 核心流程设计

### 5.1 完整检索流程

```
用户提问: "数据库的用户表有哪些字段"
    │
    │  [路由判断]
    ▼
RetrievalRouter.shouldRetrieve()
  ├─ 问题长度 < 3 token → false（太短不检）
  ├─ 匹配系统指令关键词（"你是谁"等）→ false
  └─ 否则 → true
    │
    │  [改写]
    ▼
LLMQueryRewriter.rewrite("数据库的用户表有哪些字段")
  → ["users table columns schema", "用户表 字段 定义", "CREATE TABLE users 字段列表"]
    │
    │  [混合检索] ← 对每个改写查询
    ▼
  ┌─────────────────────────────────────┐
  │ BM25Retriever                       │
  │   MATCH content AGAINST ('users')   │
  │   → top 10 结果（含 BM25 score）     │
  └─────────────────────────────────────┘
  ┌─────────────────────────────────────┐
  │ VectorRetriever                     │
  │   计算查询 embedding                │
  │   全表 cosine similarity 排序       │
  │   → top 10 结果（含 cosine score）   │
  └─────────────────────────────────────┘
    │
    │  [融合]
    ▼
ReciprocalRankFusion.merge(bm25结果, 向量结果)
  → 合并去重 → RRF 评分 → top 5
    │
    │  [重排序]
    ▼
ReRanker.rerank(query, top5)
  → LLM 逐一打分 → 按相关性重排 → top 3
    │
    │  [small-to-chunk 回溯]
    ▼
  → 如果命中的是小块 → 找到所属大块
  → 用大块内容替换小块（保留完整语境）
    │
    │  [注入上下文]
    ▼
[1] users 表: CREATE TABLE users (id BIGINT, username VARCHAR(50), email VARCHAR(100), password VARCHAR(255), created_at DATETIME)
[2] users 表包含 10 个字段，主键为 id...
[3] 用户模块: UserService 通过 userMapper 操作 users 表

--- 注入到 system prompt 末尾 ---
"以下是从知识库中检索到的参考资料：
[1] 《数据库表结构》- users 表包含...
[2] 《用户模块说明》- UserService...
[3] 《JPA 实体映射》- User 实体...
请使用 [序号] 标注引用来源。"
    │
    ▼
LLM 回答: "用户表有以下字段：id（主键）、username（用户名）、email（邮箱）、password（密码）、created_at（创建时间）[1]。在 Java 代码中通过 UserService 操作该表 [2][3]。"
```

### 5.2 查询改写 Prompt 设计

```java
/**
 * 查询改写 Prompt。
 * 将用户自然语言问题转为 3 个不同角度的检索查询。
 * 
 * 约束：改写后的查询应覆盖不同粒度/角度，提高检索召回。
 */
public static final String QUERY_REWRITE_PROMPT = """
你是一个搜索查询改写专家。给定用户的原始问题，生成 3 个不同的检索查询。

要求：
1. 保持与原始问题相同的语义
2. 从不同角度/粒度表达
3. 中文和英文关键词可混用
4. 每个查询不超过 50 个字
5. 如果原始问题已经是非常精确的检索查询，只返回原始问题本身
6. 按 JSON 数组格式返回：["query1", "query2", "query3"]

原始问题：{query}

检索查询：
""";
```

### 5.3 检索路由 Rule 设计

```java
/**
 * 检索路由规则。
 * 基于关键词和启发式规则，低开销地过滤不需要检索的问题。
 */
public class RuleBasedRetrievalRouter implements RetrievalRouter {

    /** 系统指令类问题（不需要检索） */
    private static final List<Pattern> SYSTEM_QUERY_PATTERNS = List.of(
        Pattern.compile("你是谁"),
        Pattern.compile("你能做什么"),
        Pattern.compile("你的名字"),
        Pattern.compile("你好"),
        Pattern.compile("hi|hello|hey", Pattern.CASE_INSENSITIVE),
        Pattern.compile("谢谢|感谢"),
        Pattern.compile("repeat|重复.*说|再说一遍")
    );

    /** 明确要求检索的问题（必须检） */
    private static final List<Pattern> FORCE_RETRIEVE_PATTERNS = List.of(
        Pattern.compile("查(一下|找|看|询)"),
        Pattern.compile("搜索"),
        Pattern.compile("有没有.*(文档|资料|说明)"),
        Pattern.compile(".*表.*字段|.*接口.*参数|.*配置.*说明")
    );

    @Override
    public boolean shouldRetrieve(String query) {
        if (query == null || query.trim().length() < 3) {
            return false;
        }

        // 必须检索匹配
        for (Pattern p : FORCE_RETRIEVE_PATTERNS) {
            if (p.matcher(query).find()) {
                return true;
            }
        }

        // 系统指令匹配 → 不检索
        for (Pattern p : SYSTEM_QUERY_PATTERNS) {
            if (p.matcher(query).find()) {
                return false;
            }
        }

        // 默认：长查询建议检索，短查询不检
        return query.length() > 6;
    }
}
```

### 5.4 余弦相似度计算

```java
/**
 * 向量相似度计算工具类。
 * 用于 JDK 中直接计算，不引入额外依赖。
 */
public class VectorSimilarity {

    /**
     * 计算余弦相似度（范围 -1 ~ 1）。
     */
    public static double cosineSimilarity(List<Float> vecA, List<Float> vecB) {
        if (vecA.size() != vecB.size()) {
            throw new IllegalArgumentException("向量维度不匹配");
        }
        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < vecA.size(); i++) {
            double a = vecA.get(i);
            double b = vecB.get(i);
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

### 5.5 Small-to-Chunk 回溯

```java
/**
 * small-to-chunk 检索策略：
 * 小块（~200 token）建索引 → 检索命中小块 → 回溯到大块（~2000 token）取完整内容
 *
 * 目的：小块保证高精度检索，大块保证完整语境。
 */
public class SmallToChunkResolver {

    private final DocChunkMapper chunkMapper;

    /**
     * 将检索命中的小块替换为其所属大块（去重）。
     */
    public List<RetrievalResult> resolve(List<RetrievalResult> hits) {
        Map<Long, RetrievalResult> parentMap = new LinkedHashMap<>();

        for (RetrievalResult hit : hits) {
            Long parentId = hit.getChunk().getParentChunkId();
            if (parentId != null) {
                // 命中小块 → 用大块替换（只保留第一个命中该大块的结果）
                parentMap.putIfAbsent(parentId, convertToParentResult(hit, parentId));
            } else {
                // 命中的本身就是大块
                parentMap.putIfAbsent(hit.getChunk().getId(), hit);
            }
        }

        List<RetrievalResult> resolved = new ArrayList<>(parentMap.values());
        // 按原排名重排
        resolved.sort(Comparator.comparingInt(RetrievalResult::getRank));
        return resolved;
    }

    private RetrievalResult convertToParentResult(RetrievalResult hit, Long parentId) {
        DocChunk parentChunk = chunkMapper.selectById(parentId);
        return RetrievalResult.builder()
            .chunk(parentChunk)
            .document(hit.getDocument())
            .score(hit.getScore())
            .matchType(hit.getMatchType() + "(small-to-chunk)")
            .build();
    }
}
```

### 5.6 引用注入格式

```
--- 注入到 system prompt 尾部的格式 ---

以下是从知识库中检索到的参考资料：

[1] 《数据库表结构》
    来源：docs/database-schema.md
    users 表包含以下字段：
    - id BIGINT AUTO_INCREMENT PRIMARY KEY
    - username VARCHAR(50) NOT NULL UNIQUE
    - email VARCHAR(100) NOT NULL
    - ...

[2] 《用户模块设计说明》
    来源：docs/user-module.md
    UserService 提供用户注册、登录、信息查询功能。
    底层通过 UserMapper 操作 users 表。

[3] 《Spring Security 配置》
    来源：config/SecurityConfig.java
    SecurityConfig 配置了 JWT 认证过滤器和 CORS 策略。

请使用 [序号] 标注引用来源。
```

---

## 6. 层次检索（Hierarchical / Multi-level Retrieval）

### 6.1 什么是层次检索

层次检索是一种 **逐层缩小搜索范围** 的检索策略。核心思想是：不直接在全量分块中做一次检索，而是先定位到粗粒度区域，再在区域内精细查找。

```
Flat 检索（当前 P3 默认）：
  用户提问 → 全库 N 个分块（一次检索）→ 取 TOP K

Hierarchical 检索：
  用户提问 → Level 1: 知识库/文档摘要（粗）→ 定位 TOP M
          → Level 2: 文档内章节摘要（中）→ 定位 TOP N
          → Level 3: 章节内分块原文（细）→ 取 TOP K
```

搜索范围逐层递减：`全库 → 文档子集 → 章节子集`，每一步的检索范围比上一步小 1-2 个数量级。

### 6.2 核心概念

| 术语 | 英文 | 定义 |
|------|------|------|
| 文档摘要 | Document Summary | 文档标题 + 一行摘要的向量索引，用于 Level 1 粗检 |
| 章节摘要 | Section Summary | 文档内每章的标题 + 摘要向量，用于 Level 2 中检 |
| 分块索引 | Chunk Index | 段落原文 embedding，用于 Level 3 细检 |
| 逐层过滤 | Layer-Wise Filter | Level N 的检索范围 = Level N-1 命中结果的子集 |
| 垂直检索 | Top-Down Retrieval | 从顶层逐级向下，同一路径内串行 |
| 水平检索 | Horizontal Retrieval | 同层多路并行检索后融合（Flat 检索本质是单层水平检索） |
| 级联漏斗 | Cascade Funnel | 逐层缩放的漏斗形状，越往下候选越少精度越高 |
| 跨层跳跃 | Cross-Level Skip | 某些场景可跳过中间层，直接从文档跳到段落 |
| 摘要粒度 | Summary Granularity | 每层摘要的信息密度控制，越粗的层信息损失越大 |
| 检索深度 | Retrieval Depth | 从顶层到底层经过的层次数 |

### 6.3 三层索引结构

```
┌─────────────────────────────────────────────────────────────┐
│  Level 1: Document Summary Index（文档摘要索引）             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ [Doc-1] 标题: 数据库表结构设计       向量: [0.012...] │  │
│  │         摘要: users/posts/categories 表字段定义和关系   │  │
│  │ [Doc-2] 标题: API 接口规范          向量: [0.034...] │  │
│  │         摘要: 所有 REST 端点、请求/响应格式             │  │
│  │ [Doc-3] 标题: 部署与配置说明        向量: [0.056...] │  │
│  │         摘要: Docker 部署、环境变量、CI/CD 流程         │  │
│  │ ...                                                     │  │
│  └───────────────────────────────────────────────────────┘  │
│  检索方式：向量检索（摘要 embedding）                        │
│  返回 TOP: 3-5 篇文档                                       │
├─────────────────────────────────────────────────────────────┤
│  Level 2: Section Summary Index（章节摘要索引）              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ [Doc-1/Sec-1] 标题: users 表   摘要: 用户表字段...  │  │
│  │ [Doc-1/Sec-2] 标题: posts 表   摘要: 文章表字段...  │  │
│  │ [Doc-3/Sec-1] 标题: Docker 部署 摘要: 容器化部署... │  │
│  │ ...                                                     │  │
│  └───────────────────────────────────────────────────────┘  │
│  检索方式：向量检索（摘要 embedding），限定在 Level 1 命中  │
│  返回 TOP: 3-5 个章节                                       │
├─────────────────────────────────────────────────────────────┤
│  Level 3: Chunk Content Index（段落原文索引）                │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ [Doc-1/Sec-1/Chunk-1] users 表建表 DDL 原文           │  │
│  │ [Doc-1/Sec-1/Chunk-2] users 表字段说明                 │  │
│  │ ...                                                     │  │
│  └───────────────────────────────────────────────────────┘  │
│  检索方式：向量检索 + BM25，限定在 Level 2 命中章节内      │
│  返回 TOP: K 个分块（最终结果）                             │
└─────────────────────────────────────────────────────────────┘
```

### 6.4 完整检索流程

```
用户提问: "users 表有哪些字段约束"

                ▼
  Level 1: Document Summary Index（全库 50 篇文档摘要）
    向量检索: "users table constraints schema"
    → 命中: Doc-1 (数据库表结构, score=0.92)
           Doc-5 (用户模块设计, score=0.78)
           Doc-12 (数据校验规范, score=0.65)
    → TOP 3 篇文档，进入下一层

                ▼
  Level 2: Section Summary Index（仅 Level 1 命中的 3 篇文档）
    限定范围: Doc-1(5章) + Doc-5(3章) + Doc-12(4章) = 12 个章节
    向量检索: "users table constraints"
    → 命中: Doc-1/Sec-1 (score=0.95, 标题"users 表")
           Doc-5/Sec-2 (score=0.71, 标题"用户实体")
           Doc-12/Sec-3 (score=0.55, 标题"字段校验")
    → TOP 3 个章节，进入下一层

                ▼
  Level 3: Chunk Content Index（仅 Level 2 命中的 3 个章节）
    限定范围: 3 个章节共计 15 个分块
    向量检索 + BM25 混合检索
    → 命中: Chunk-42 (users DDL, score=0.96)
           Chunk-44 (外键约束, score=0.88)
           Chunk-103 (@NotNull 注解, score=0.72)

                ▼
  最终 3 个分块 → 注入 LLM 上下文
```

### 6.5 Flat vs Small-to-Chunk vs Hierarchical 对比

| 维度 | Flat 检索 | Small-to-Chunk | Hierarchical 检索 |
|------|-----------|---------------|-------------------|
| **检索次数** | 1 | 1 + 回溯 | 3（串行） |
| **每次范围** | 全库全量分块 | 全库小块 | 逐层缩小 |
| **索引总数** | N | N（小块）+ M（大块） | N + M 摘要（增量约 10%） |
| **检索精度** | 依赖 embedding 质量 | 小块召回高 + 大块语境完整 | **最高**，每层过滤噪声 |
| **检索延迟** | 低 | 中（+大块加载） | **较高**（3 次串行） |
| **实现复杂度** | 低 | 中 | **高** |
| **扩展性** | 分块数 >10 万时性能下降 | 同左 | **好**，每层独立优化 |
| **适合场景** | 千级分块 | 万级分块 | **十万级以上**或检索精度要求极高 |
| **冷启动成本** | 低 | 低（多存 parent_id） | **较高**（需额外构建摘要索引） |

**在本项目中的定位：**

```
当前文档规模（几十篇，千级分块）：
  Hierarchical 精度提升 ≈ 不明显（Flat 已经够准）
  Hierarchical 延迟增加 ≈ 3 倍（3 次串行检索）
  结论：不必要

未来文档规模（> 100 篇，万级以上分块）：
  Flat 开始出现噪声
  Hierarchical 的逐层过滤效果显著
  结论：可开启
```

### 6.6 与 RAG 和知识图谱的区别

| 维度 | RAG（检索增强生成） | 层次检索（Hierarchical Retrieval） | 知识图谱（Knowledge Graph） |
|------|-------------------|----------------------------------|---------------------------|
| **本质** | **检索+生成**的架构模式 | **检索策略**（如何检索） | **知识组织方式**（知识如何关联） |
| **解决的问题** | AI 不知道答案时，从外部查 | 检索结果噪声多/精度不够时，逐层过滤 | 知识间的多跳关系推理 |
| **核心手段** | 向量检索 + BM25 + 注入上下文 | 分层摘要索引 + 逐层范围限定 | 实体 + 关系 + 图遍历 |
| **数据组织** | 分块 + embedding | 多层摘要（Doc→Section→Chunk） | 三元组（实体-关系-实体） |
| **检索次数** | 1 次混合检索 | N 次串行，逐层缩小 | 1 次实体命中 + 可选关系扩展 |
| **与检索精度的关系** | 基础检索手段 | 检索精度的 **优化策略** | 检索精度的 **增强手段** |
| **与数据量的关系** | 适用任何规模 | 数据量大时收益才显著 | 实体关系丰富时收益才显著 |
| **实现成本** | 中（embedding + 检索） | 中高（多层摘要需额外存储） | 高（实体/关系抽取流水线） |
| **可独立运行** | ✅ 是 | ❌ 依赖 RAG 基础检索 | ✅ 可作为 RAG 的增强插件 |

**三者关系图解：**

```
┌──────────────────────────────────────────────────────────┐
│                     RAG 架构（整体模式）                    │
│                                                          │
│  用户 → [检索策略] → [知识组织] → 注入 → LLM → 回答      │
│              │              │                             │
│              ▼              ▼                             │
│      Hierarchical     Knowledge Graph                     │
│      （检索策略）      （知识增强）                         │
│       优化"怎么找"      补充"找什么"                      │
└──────────────────────────────────────────────────────────┘

实际组合方式：
  ┌────────┐  ┌────────────┐  ┌──────────┐
  │ 用户   │→ │ RAG 路由   │→ │ 检索执行  │→ LLM
  │ 提问   │  │ 判断是否检索│  │          │
  └────────┘  └────────────┘  └──────────┘
                                  │
                  ┌───────────────┴───────────────┐
                  ▼                               ▼
          ┌──────────────┐               ┌──────────────┐
          │ 检索策略选择  │               │ 知识增强（可选）│
          ├──────────────┤               ├──────────────┤
          │ Flat（默认）  │               │ 无增强        │
          │ Hierarchical  │               │ Entity 增强   │
          │ (数据量大时)   │               │ KG 增强       │
          └──────────────┘               └──────────────┘
```

**一句话说清区别：**

> **RAG 是"要不要查"**（架构决策），**层次检索是"怎么查效率最高"**（策略优化），**知识图谱是"查出来的东西还能关联到什么"**（知识扩展）。三者不在同一层面，可以叠加使用。

### 6.7 本项目的策略选择

```
当前 P3（千级分块）:    Flat + Small-to-Chunk ✅ 已设计
文档量增长后           Hierarchical（配置切换）
知识需求变复杂后       叠加 Entity/KG 增强（P3.5）
```

**代码预留方式** — `RAGConfig` 中已包含模式配置入口：

```java
@ConfigurationProperties(prefix = "ai.rag")
@Data
public class RAGConfig {
    // 检索模式：flat / hybrid / hierarchical
    private String retrieverMode = "hybrid";

    // 层次检索配置
    private HierarchicalConfig hierarchical = new HierarchicalConfig();

    @Data
    public static class HierarchicalConfig {
        private int docLevelTopK = 3;      // Level 1 返回文档数
        private int sectionLevelTopK = 3;  // Level 2 返回章节数
        private int chunkLevelTopK = 5;    // Level 3 返回分块数
        private boolean sectionLevelEnabled = true;  // 是否启用章节层（可跳过）
    }
}
```

`RAGAdvisor` 根据 `retrieverMode` 选择检索器：

```java
// RAGAdvisor.before() 中:
Retriever retriever = switch (ragConfig.getRetrieverMode()) {
    case "flat"        -> flatRetriever;
    case "hybrid"      -> hybridRetriever;
    case "hierarchical" -> hierarchicalRetriever;
    default            -> hybridRetriever;
};
```

新增 `HierarchicalRetriever` 实现：

```java
/**
 * 层次检索器。
 * 逐层缩小检索范围：文档摘要 → 章节摘要 → 分块原文。
 *
 * 每层独立 embedding 索引，下层检索范围限定在上层命中的结果内。
 * 适合文档量大（>100 篇）或检索精度要求高的场景。
 */
public class HierarchicalRetriever implements Retriever {

    private final DocChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final RAGConfig.HierarchicalConfig config;

    @Override
    public List<RetrievalResult> retrieve(String query, int topK,
                                           RetrievalFilter filter) {
        List<Float> queryVec = embeddingService.embed(query);

        // Level 1: 文档摘要检索
        List<DocSummary> docs = searchDocSummaries(queryVec, config.getDocLevelTopK(), filter);

        // Level 2: 章节摘要检索（限定在 Level 1 命中的文档内）
        List<Long> docIds = docs.stream().map(DocSummary::getDocId).toList();
        List<SectionSummary> sections = searchSectionSummaries(
            queryVec, config.getSectionLevelTopK(), docIds, filter);

        // Level 3: 分块原文检索（限定在 Level 2 命中的章节内）
        List<Long> sectionIds = sections.stream().map(SectionSummary::getSectionId).toList();
        List<DocChunk> chunks = searchChunks(query, sectionIds, config.getChunkLevelTopK());

        // small-to-chunk 回溯 → 返回最终结果
        return resolveParentChunks(chunks);
    }

    @Override
    public String type() {
        return "hierarchical";
    }
}
```

### 6.8 何时切换到层次检索

**切换到 Hierarchical 的触发条件：**

| 条件 | 指标 | 阈值 |
|------|------|------|
| 文档总量 | 知识库文档数 | > 100 篇 |
| 分块总量 | ai_doc_chunks 记录数 | > 10,000 条 |
| Flat 检索精度下降 | Precision@5 | < 50% |
| 检索结果噪声 | 不相关结果占比 | > 30% |

**切换成本：**
- 单次增量：构建文档摘要表和章节摘要表（约增 10% 存储）
- 代码改动：`application.yml` 中改一行 `ai.rag.retriever-mode=hierarchical`
- 性能影响：检索延迟从 1 次变为 3 次串行，但每次范围显著缩小

---

## 7. 知识图谱增强（Future）

### 7.1 什么是知识图谱

#### 7.1.1 定义

知识图谱（Knowledge Graph）是一种用 **图结构** 组织知识的方式。它描述真实世界中的 **实体**（Entity）以及实体之间的 **关系**（Relation），以 **三元组**（Triple）为基本单位：

```
(主体, 谓词, 客体)
(UserService, implements, UserServiceInterface)
(UserController, depends_on, UserService)
(login, is_method_of, UserController)
```

**经典例子：**

```
Google 知识图谱（2012 年推出）：
  (Albert Einstein, born_in, Germany)
  (Albert Einstein, discovered, Theory of Relativity)
  (Theory of Relativity, field, Physics)

本项目可能的 KG：
  (UserService, implements, UserServiceInterface)
  (UserController, depends_on, UserService)
  (login, is_method_of, UserController)
  (/api/v1/auth/login, maps_to, AuthController.login)
```

#### 7.1.2 与关系数据库的对比

| 维度 | 关系数据库（MySQL） | 知识图谱 |
|------|-------------------|---------|
| 基本单位 | 行（Row） | 三元组（Subject-Predicate-Object） |
| 数据模型 | 表 + 外键 | 图（节点 + 边） |
| 关联查询 | JOIN（表间硬关联） | 图遍历（沿边走，天然可递归） |
| Schema | 严格（先定义表结构） | 灵活（边可动态新增） |
| 深层关系 | SQL 递归 CTE 性能差，深度 >3 难 | 深度遍历天然高效 |
| 典型查询 | "查询 UserService 类" | "UserService 依赖的所有类中，哪些被 @RestController 标注" |

**关键差异：** 关系数据库擅长"查某条记录"，知识图谱擅长"找关联路径"。

```
关系数据库思维：
  SELECT * FROM users WHERE id = ?
  → 找到用户（单点查询）

知识图谱思维：
  UserService → depends_on → UserMapper
  UserMapper → implements → UserMapperInterface
  UserMapperInterface → used_by → JdbcTemplate
  → 路径：UserService → UserMapper → UserMapperInterface → JdbcTemplate
  → 问题：UserService 间接依赖了 JdbcTemplate
```

#### 7.1.3 与向量检索的对比

| 维度 | 向量检索 | 知识图谱 |
|------|---------|---------|
| 匹配方式 | 语义相似度（"approximate"） | 精确匹配（"exact"） |
| 查询表达 | "users 表有哪些字段"（自然语言） | "users 表的 schema"（实体名） |
| 结果质量 | 依 embedding 质量，可能跑偏 | 精确命中或完全错过 |
| 关系推理 | 不能（"UserService 用什么实现的？"需全文理解） | 能（沿 implemented_by 边直达） |
| 覆盖范围 | 全量文本（不需要结构化） | 只有抽取出的实体和关系 |
| 维护成本 | 低：全文灌入即可 | 高：需持续抽取、审核、更新 |

**核心差异：** 向量检索是模糊匹配（"大概相关"），KG 是精确命中（"就是这个"）。

---

### 7.2 知识图谱核心概念

| 概念 | 英文 | 定义 | 本项目的例子 |
|------|------|------|-------------|
| **实体** | Entity / Node | 客观世界中的事物，图中的一个节点 | `UserService`、`login()`、`/api/v1/auth/login`、`users 表` |
| **关系** | Relation / Edge | 两个实体之间的语义联系，图中的一条边 | `depends_on`、`implements`、`maps_to`、`contains` |
| **三元组** | Triple / Statement | (主体, 谓词, 客体) 是最小知识单元 | `(UserService, depends_on, UserMapper)` |
| **谓词** | Predicate | 关系类型的名称 | `implements`、`extends`、`references`、`configured_by` |
| **属性** | Property | 实体的特征描述（不是独立实体） | `UserService.name = "UserService"`、`login.is_public = false` |
| **本体** | Ontology | 知识图谱的 Schema，定义实体类型和关系类型 | `类 implements 接口`、`控制器 depends_on 服务` |
| **命名空间** | Namespace | 实体唯一标识的前缀，避免重名冲突 | `class:UserService`、`api:/api/v1/auth/login`、`db:users` |
| **入度/出度** | In-degree / Out-degree | 指向/出自某实体的边数 | `UserService` 入度=5（5 处引用），出度=3（依赖 3 个类） |
| **子图** | Subgraph | 大图中的一部分，围绕某实体的一跳/多跳邻居 | `UserService` 的子图包括它依赖的和依赖它的所有实体 |
| **邻接表** | Adjacency List | 图在关系数据库中的存储方式，每个节点存其邻居列表 | MySQL `ai_doc_relations` 表 |
| **图遍历** | Graph Traversal | 从起点沿边/关系逐级访问相邻节点 | `UserService → implements → UserServiceInterface` |
| **最短路径** | Shortest Path | 两实体间经过最少边数的路径 | `UserService → login()：UserService → UserController → login()` |
| **上下位关系** | Hypernym / Hyponym | 泛化-特化关系 | `BaseService（上位）→ UserService（下位）` |
| **知识推理** | Knowledge Reasoning | 基于已有三元组推导新知识 | `UserService implements UserServiceInterface` 且 `UserController depends_on UserService` → 推理出 `UserController depends_on UserServiceInterface` |
| **实体链接** | Entity Linking | 将文本中的实体指称链接到 KG 中的实体 | 查询中的 "login 方法" → 链接到 KG 中的 `method:login` |
| **关系抽取** | Relation Extraction | 从非结构化文本中识别实体间的关系 | 从 Javadoc "UserService 依赖于 UserMapper" 中抽取 `(UserService, depends_on, UserMapper)` |

**实体 vs 属性的区分：**

```
代码中:
  UserService 是实体（有独立身份，可被引用）
  UserService 的创建时间不是实体（只是属性）

文档中:
  "users 表" 是实体（可在多处被引用）
  "users 表有 10 个字段" 中的 "10" 是属性

判断标准：有没有独立标识（ID/名称）？能不能被其他实体引用？
→ 能 → 实体
→ 不能 → 属性
```

**本项目中会用到的实体类型：**

| 实体类型 | 来源 | 命名空间 | 示例 |
|---------|------|---------|------|
| `class` | Java 类 | `class:` | `class:UserService` |
| `interface` | Java 接口 | `interface:` | `interface:UserServiceInterface` |
| `method` | Java 方法 | `method:` | `method:UserService.login` |
| `api` | REST 端点 | `api:` | `api:POST /api/v1/auth/login` |
| `config` | 配置键 | `config:` | `config:spring.ai.deepseek.api-key` |
| `table` | 数据库表 | `db:` | `db:users` |
| `column` | 数据库字段 | `db.col:` | `db.col:users.username` |
| `term` | 业务术语 | `term:` | `term:JWT token` |

---

### 7.3 为什么知识图谱可以做检索增强

#### 7.3.1 核心逻辑：向量检索和 KG 是互补的

```
向量检索强项：语义模糊匹配
  "用户登录流程" → 能召回"login 方法说明"、"认证逻辑"、
                   "JWT token 校验"（虽然后者没有"登录"二字）

向量检索弱项：精确关系
  "UserService 依赖了哪些类" → 能搜到含 UserService 的文档，
    但不一定搜到"UserMapper"（如果不在同一段）

KG 的强项正是向量的弱项：
  (UserService, depends_on, UserMapper) → 精确、确定、可遍历
```

**两者在检索中的定位：**

```
用户查询
    │
    ├── 语义匹配 → 向量检索（模糊、全量召回）
    │
    ├── 精确命中 → BM25 + 实体链接（精确、定点命中）
    │
    └── 关系扩展 → KG 图遍历（沿关系扩展到关联实体）
```

#### 7.3.2 KG 增强 RAG 的三种方式

**方式一：实体直连（Entity Direct Hit）— 最基础**

```
查询: "UserService 怎么用"
    │
    ├─ 向量检索 → 模糊匹配到含"UserService"的分块
    │
    └─ 实体链接 → query→"UserService" → KG→entity_id=42
         → 直接返回 entity_id=42 关联的所有分块（精确命中）
         → 向量可能漏的分块，实体命中补回来
```

**方式二：关系扩展（Relation Expansion）— KG 独有能力**

```
查询: "UserService 登录流程"

实体链接命中 UserService (entity_id=42)

在 KG 中沿关系扩展（只需 2 次 SQL 查询）：
  entity_id=42
    ├── has_method → login (entity_id=55)
    │     └── calls → UserMapper (entity_id=60)
    ├── extends → BaseService (entity_id=45)
    └── referenced_by → UserController (entity_id=50)
          └── has_method → login (entity_id=55)

扩展召回：UserMapper + BaseService + UserController 的文档分块
         这些是纯向量检索很可能漏掉的相关知识
```

**方式三：结构化查询（Structured QA）— 不需要 LLM 读全文**

```
查询: "UserService 实现了哪个接口？"

向量检索方式：
  → 召回 UserService 的类定义文档
  → LLM 阅读全文找到 "implements UserServiceInterface"
  → 耗时、耗 token、可能读错位置

KG 方式：
  → 实体链接到 UserService
  → 查 KG：(UserService, implements, ?)
  → 直接返回：UserServiceInterface
  → 不需要 LLM 阅读，毫秒级返回
```

#### 7.3.3 在 RAG 流程中的位置

```
用户查询
    │
    ▼
┌─────────────────────────────────────────────────────┐
│                    RAG 检索层                         │
│                                                     │
│  ┌──────────────┐  ┌─────────────┐  ┌─────────────┐ │
│  │  向量检索     │  │  BM25 检索   │  │  KG 增强    │ │
│  │（语义相似度） │  │（关键词匹配） │  │（精确+扩展） │ │
│  └──────┬───────┘  └──────┬──────┘  └──────┬──────┘ │
│         │                │                │         │
│         ▼                ▼                ▼         │
│  ┌─────────────────────────────────────────────┐    │
│  │          RRF 融合排序（三路融合）             │    │
│  └─────────────────────────────────────────────┘    │
│                         │                           │
│                         ▼                           │
│           重排序 → TOP K → 注入上下文                 │
└─────────────────────────────────────────────────────┘
```

#### 7.3.4 KG 做 RAG 的局限（必须诚实面对）

| 局限 | 原因 | 缓解 |
|------|------|------|
| **覆盖率受限** | KG 只能回答"已抽取的实体和关系"，未抽取的一概不知 | 结合向量检索兜底，KG 作为增强而非替代 |
| **抽取精度** | 自动抽取准确率 60-80%，错误关系比没抽取更糟糕 | 只保留高置信度关系（confidence > 0.7） |
| **时效性** | 代码变更后 KG 需同步更新，滞后期间可能给出过时信息 | 文档变更时触发局部重新抽取 |
| **成本** | 实体/关系抽取需 LLM 调用，批量抽取成本不可忽视 | 异步、增量抽取，只在文档变更时触发 |
| **规模瓶颈** | MySQL 邻接表遍历深度 >3 性能骤降 | 限制遍历深度 ≤2，大图考虑 Neo4j |

#### 7.3.5 总结：KG 在 RAG 中的角色

> **知识图谱不是 RAG 的替代品，而是向量检索的精确补充。**
>
> 向量检索负责"找相关内容"（语义、模糊、全量），
> KG 负责"找明确关联"（精确、关系、确定）。
>
> 两者结合在混排层融合，比任何单一方式都更健壮。

---

### 7.4 为什么现在不做

知识图谱在这个项目现阶段属于 **过度设计**，理由如下：

| 评估维度 | 说明 |
|---------|------|
| **核心矛盾** | P3 要解决的是"AI 能查到资料"→ 向量+BM25 已够用 |
| **多跳推理场景** | 当前项目知识是扁平的（API 文档、配置、代码结构），命中即用 |
| **落地成本** | 需要实体识别 NLP 流水线 + 关系抽取 LLM 调用 + 图遍历逻辑 |
| **维护负担** | 实体更新策略、关系一致性、置信度校准都需持续投入 |
| **技术栈** | MySQL 不支持图遍历，纯代码层实现效率低 |

**核心判断：知识图谱解决"多跳关系推理问题"，但当前 RAG 场景是"单跳检索+阅读理解"。**

### 7.5 如果要做：轻量方案设计

不引入图数据库，在 MySQL 实体/关系表基础上做 **Entity-Augmented Retrieval**（实体增强检索）。

```
用户查询: "UserService 的 login 方法怎么配置权限？"
    │
    ▼ 实体提取
  EntityExtractor.extract(query)
  → [UserService, login, 权限]
    │
    ▼ 实体链接
  EntityLinker.link(entities)
  → UserService → id=101 (class)
  → login → id=205 (method)
  → 权限 → id=310 (term)
    │
    ├── 精确匹配检索
    │   通过 entity→chunk_ids 直接命中相关分块
    │   → Chunk#42 (UserService 类说明)
    │   → Chunk#58 (login 方法文档)
    │
    ├── 关系扩展检索
    │    查 ai_doc_relations 找到关联实体
    │    UserService extends BaseService → 扩展 BaseService 相关 chunk
    │    UserService references UserMapper → 扩展 UserMapper 相关 chunk
    │
    └── 结果与向量/BM25 结果融合 → 注入上下文
```

### 7.6 知识图谱模块详细类设计

#### EntityExtractor.java — 实体抽取接口

```java
/**
 * 实体抽取器。
 * 从文本中识别命名实体（类名、方法名、API 路径、配置键等）。
 *
 * 实现方式：
 *   - Phase 1（规则）：正则 + 关键词匹配（类名大写开头、@注解、/api/路径）
 *   - Phase 2（LLM）：调用 LLM 从文档中提取实体（更准确但更贵）
 */
public interface EntityExtractor {

    /**
     * 从文本中抽取实体。
     *
     * @param text   源文本
     * @param kbId   所属知识库（用于类型过滤）
     * @return 抽取的实体列表
     */
    List<ExtractedEntity> extract(String text, Long kbId);

    /**
     * 从文档批量抽取。
     */
    List<ExtractedEntity> extractFromDocument(DocDocument document);

    @Data
    @Builder
    class ExtractedEntity {
        private String name;
        private String type;       // class / method / api / config / term
        private List<String> aliases;
        private String description;
        private double confidence;
    }
}
```

#### EntityLinker.java — 实体链接接口

```java
/**
 * 实体链接器。
 * 将查询中识别出的实体引用与知识库中的实体记录关联。
 *
 * 匹配策略：
 *   1. 精确匹配（entity_name 完全一致）
 *   2. 别名匹配（aliases 包含）
 *   3. 模糊匹配（Levenshtein distance < 3）
 *   4. 低于阈值则新建临时实体
 */
public interface EntityLinker {

    /**
     * 将抽取的实体链接到知识库中的已有实体。
     *
     * @param extracted 从查询中抽取的实体
     * @param kbId      知识库 ID
     * @return 链接结果，含匹配到的实体 ID 和置信度
     */
    List<LinkResult> link(List<ExtractedEntity> extracted, Long kbId);

    @Data
    @Builder
    class LinkResult {
        private ExtractedEntity extracted;
        private DocEntity matched;       // null = 未匹配
        private boolean isNew;
        private double confidence;
        private String matchMethod;       // exact / alias / fuzzy
    }
}
```

#### KnowledgeGraphRetriever.java — 知识图谱增强检索

```java
/**
 * 知识图谱增强检索器。
 * 在向量+BM25 检索之外，通过实体关联扩展搜索结果。
 *
 * 增强方式：
 *   1. 实体命中 → 直接返回该实体关联的所有 chunk（精确）
 *   2. 关系扩展 → 沿着关系找到相邻实体，连带返回其 chunk（扩展）
 *   3. 结果与混合检索结果 RRF 融合
 */
public class KnowledgeGraphRetriever implements Retriever {

    private final EntityExtractor entityExtractor;
    private final EntityLinker entityLinker;
    private final DocChunkMapper chunkMapper;
    private final DocRelationMapper relationMapper;

    @Override
    public List<RetrievalResult> retrieve(String query, int topK,
                                           RetrievalFilter filter) {
        // 1. 抽取查询中的实体
        List<ExtractedEntity> entities = entityExtractor.extract(query, filter.getKbId());
        if (entities.isEmpty()) {
            return List.of();
        }

        // 2. 链接到知识库实体
        List<LinkResult> linked = entityLinker.link(entities, filter.getKbId());

        // 3. 收集关联的 chunk
        Set<Long> chunkIds = new LinkedHashSet<>();
        for (LinkResult link : linked) {
            if (link.getMatched() != null) {
                // 实体直连的 chunk
                chunkIds.addAll(link.getMatched().getChunkIds());

                // 关系扩展：沿着关系找到相邻实体的 chunk
                List<DocRelation> relations = relationMapper
                    .selectBySubjectId(link.getMatched().getId());
                for (DocRelation rel : relations) {
                    DocEntity obj = entityMapper.selectById(rel.getObjectId());
                    if (obj != null) {
                        chunkIds.addAll(obj.getChunkIds());
                    }
                }
            }
        }

        // 4. 加载 chunk 并包装为 RetrievalResult
        List<DocChunk> chunks = chunkMapper.selectByIds(new ArrayList<>(chunkIds));
        return chunks.stream()
            .map(chunk -> RetrievalResult.builder()
                .chunk(chunk)
                .score(1.0)
                .matchType("entity")
                .build())
            .limit(topK)
            .toList();
    }

    @Override
    public String type() {
        return "entity";
    }
}
```

#### RelationExtractor.java — 关系抽取接口

```java
/**
 * 关系抽取器。
 * 从文档的上下文中提取实体间的关系。
 *
 * 关系类型：
 *   - class: extends / implements / depends_on
 *   - api: references / configured_by
 *   - config: belongs_to / overrides
 *   - 通用: contains / related_to
 */
public interface RelationExtractor {

    /**
     * 从文档中抽取实体关系。
     * 调用 LLM 分析文本中实体间的语义关系。
     *
     * @param document 文档
     * @param entities 文档中已识别的实体
     * @return 抽取的关系列表
     */
    List<ExtractedRelation> extract(DocDocument document, List<DocEntity> entities);

    @Data
    @Builder
    class ExtractedRelation {
        private String subjectName;
        private String predicate;       // extends / implements / ...
        private String objectName;
        private double confidence;
        private Long sourceChunkId;
    }
}
```

### 7.7 知识图谱增强的 P3.5 实施计划

| 小阶段 | 任务 | 前置 | 预估工期 |
|--------|------|------|---------|
| **P3.5a** | 实体抽取（规则版）：正则匹配类名/方法名/API 路径/配置键 | P3 核心检索上线 | 2d |
| **P3.5b** | 实体表入库 + 实体-分块关联建立 | P3.5a | 1d |
| **P3.5c** | JDBC 连接实体链接器 + 精确/别名匹配 | P3.5b | 1d |
| **P3.5d** | Entity-augmented retrieval：实体命中 → 扩展召回 | P3.5c | 1d |
| **P3.5e** | 关系抽取（LLM 版）：异步分析文档，提取关系三元组入库 | P3.5a | 2d |
| **P3.5f** | 关系图遍历检索：沿 relation 扩展召回 | P3.5e | 1d |

> **预计总工期：~8d**，建议在 P3 核心检索稳定运行 1-2 周后启动，根据实际检索效果决定优先级。

### 7.8 不做完整知识图谱的理由（详细版）

| 否决项 | 解释 |
|--------|------|
| **场景不匹配** | KG 擅长多跳推理（"A 依赖 B，B 依赖 C → 升级 A 会影响 C"），当前项目无此类需求 |
| **抽取精度** | 实体/关系自动抽取准确率通常 60-80%，需要人工审核修正 |
| **时效性** | 代码变更后实体关系需同步更新，维护链条长 |
| **图数据库缺失** | MySQL 存三元组效率低，关系深度 >2 时性能急剧下降 |
| **团队 ROI** | 8d 工期可以优化检索质量（调 embedding、改分块策略），比做 KG 回报更高 |

---

## 8. 实施步骤

### Priority 1：核心检索链（MVP，~5d）

**目标：一条用户问题 → 能找到最相关的文档段 → 注入 LLM 上下文。**

| Step | 内容 | 工时 | 产出 |
|------|------|------|------|
| 1 | 建表：`ai_knowledge_base` + `ai_doc_documents` + `ai_doc_chunks` | 0.5d | DDL |
| 2 | 领域模型 + Mapper：KnowledgeBase/DocDocument/DocChunk 实体 + CRUD XML | 1d | Mapper 层 |
| 3 | DocumentService：文档导入、分块、去重更新 | 1d | Service 层 |
| 4 | EmbeddingService（DeepSeek API 向量化）+ 入库 | 1d | 向量化 |
| 5 | VectorRetriever（全表 cosine） + Bm25Retriever（FULLTEXT） | 0.5d | 检索层 |
| 6 | RAGAdvisor：检索 → 注入上下文（无改写/重排序） | 1d | Advisor 接入 |

**MVP 检索流程（简化版）：**
```
用户提问 → RAGAdvisor.before()
  → BM25 全文检索（FULLTEXT MATCH）
  → 取 top 5 → 拼引用 → 注入 system prompt
  → 返回给 LLM
```

### Priority 2：检索质量提升（~4d）

**目标：提高检索精度和召回，支持混合搜索和查询改写。**

| Step | 内容 | 工时 | 产出 |
|------|------|------|------|
| 7 | QueryRewriter + LLMQueryRewriter（改写 prompt） | 1d | 查询改写 |
| 8 | EmbeddingService 接入向量检索 → VectorRetriever | 1d | 向量检索 |
| 9 | HybridRetriever（BM25 + 向量 RRF 融合） | 1d | 混合检索 |
| 10 | ReciprocalRankFusion 融合排序 | 0.5d | 融合排序 |
| 11 | Small-to-Chunk 分块 + 回溯解析 | 0.5d | 分块优化 |

### Priority 3：高级特性（~3d）

**目标：重排序、管理 API、数据接入。**

| Step | 内容 | 工时 | 产出 |
|------|------|------|------|
| 12 | ReRanker + LLMReranker（重排序 prompt） | 1d | 重排序 |
| 13 | 知识库管理 API（CRUD + 重新索引 + 重新向量化） | 1d | 管理接口 |
| 14 | 检索路由 RetrievalRouter | 0.5d | 路由 |
| 15 | 文档导入工具（支持 Markdown/Text 批量导入 + 自动分块） | 0.5d | 导入工具 |

### 数据接入计划

| 数据源 | 内容 | 接入方式 | 优先级 |
|--------|------|---------|--------|
| 项目文档 | CLAUDE.md、设计文档（5-*/） | 手动导入或定时扫描 | P1 |
| API 文档 | Swagger/springdoc 自动生成的 API 说明 | 启动时自动同步 | P2 |
| 配置说明 | application.yml 注释 + site_config 含义 | 手动导入 | P2 |
| 代码注释 | 关键类的 Javadoc | 扫描导入（Future） | P3 |

---

## 9. 测试策略

### 9.1 单元测试

| 测试目标 | 内容 | 方式 |
|---------|------|------|
| ChunkStrategy | 段落/标题/固定长度分块是否正确 | 输入 Mock 文档，验证分块边界 |
| EmbeddingService | 向量化调用 + 缓存 | Mock DeepSeek API |
| CosineSimilarity | 已知向量对验证计算结果 | 纯数学验证 |
| BM25Retriever | FULLTEXT MATCH SQL + 结果排序 | H2 内存 + SQL |
| HybridRetriever | RRF 融合逻辑 | Mock 两路检索结果 |
| RAGAdvisor | before() 注入逻辑 | Mock RAGService |

### 9.2 集成测试

| 测试场景 | 验证点 |
|---------|--------|
| 文档入库 → 分块 → 向量化 → 检索 | 全链路数据一致性 |
| 10 篇文档导入 → 多轮检索 | 检索结果不重复、排序合理 |
| 文档更新（内容变更）→ 重新分块 | 旧数据清除，新数据生效 |
| 知识库隔离 | A 库检索不返回 B 库内容 |

### 9.3 检索质量验证

| 指标 | 说明 | 目标 |
|------|------|------|
| Recall@K | Top K 中包含相关文档的比例 | > 80% |
| Precision@K | Top K 中真正相关的比例 | > 60% |
| MRR | 第一个相关结果的排名倒数均值 | > 0.7 |
| 端到端满意度 | 人工判断 LLM 回答是否准确引用 | > 90% |

---

## 10. 验收标准

1. ✅ 导入一篇项目文档后，AI 能回答文档中的具体问题并标注引用
2. ✅ 不相关的问题（闲聊）不触发检索
3. ✅ 多篇文档入库后，检索结果按相关性排序
4. ✅ 更新文档内容后重新索引，检索结果反映最新内容
5. ✅ 不同知识库内容不串库
6. ✅ 全链路延迟 < 500ms（不含 LLM 生成时间）
7. ✅ 引用格式规范 [1][2][3]，LLM 回答时正确标注来源

---

## 11. 相关文档

- [上下文工程路线图](./context-engineering-roadmap.md)
- [P0 详细设计：对话持久化](./p0-foundation.md)
- [P1 Skill 设计](./p1-skill-design.md)
- [P2 窗口管理设计](../5-19/p2-sliding-window.md)
