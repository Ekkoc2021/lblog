create table ai_chat_sessions
(
    id            bigint auto_increment comment '主键'
        primary key,
    user_id       bigint                             null comment '用户 ID（NULL 表示访客）',
    agent_type    varchar(32)                        not null comment 'Agent 类型：draw/chat/code',
    title         varchar(255)                       null comment '会话标题，自动生成或用户命名',
    model_name    varchar(64)                        null comment '使用的模型名',
    message_count int      default 0                 not null comment '消息总数',
    total_tokens  int      default 0                 not null comment '累计 tokens',
    status        tinyint  default 1                 not null comment '状态：1=活跃, 0=已归档, -1=已删除',
    created_at    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_at    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '最后活跃时间'
)
    comment 'AI 对话会话表' collate = utf8mb4_unicode_ci;

create table ai_chat_messages
(
    id                bigint auto_increment comment '主键'
        primary key,
    session_id        bigint                             not null comment '关联会话 ID',
    role              varchar(16)                        not null comment '角色：user/assistant/tool/system',
    content           longtext                           null comment '消息可见文本内容',
    reasoning_content longtext                           null comment '模型思考/推理过程',
    tool_calls        json                               null comment '工具调用列表',
    tool_call_id      varchar(64)                        null comment '工具调用 ID（仅 tool role 使用）',
    name              varchar(128)                       null comment '工具名（仅 tool role 使用）',
    msg_index         int      default 0                 not null comment '会话内消息序号，从 0 递增',
    tokens            int      default 0                 null comment '本条消息的 token 数',
    metadata          json                               null comment '扩展字段',
    created_at        datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    constraint fk_message_session
        foreign key (session_id) references ai_chat_sessions (id)
            on delete cascade
)
    comment 'AI 对话消息表' collate = utf8mb4_unicode_ci;

create index idx_session_order
    on ai_chat_messages (session_id, msg_index);

create index idx_status
    on ai_chat_sessions (status);

create index idx_user_agent
    on ai_chat_sessions (user_id, agent_type, updated_at);

create table ai_prompts
(
    id             bigint auto_increment
        primary key,
    module         varchar(50)                          not null comment 'AI module identifier',
    prompt_key     varchar(100)                         not null comment 'Prompt key',
    content        text                                 not null comment 'Prompt content',
    version        int        default 1                 not null comment 'Version number',
    sort_order     int        default 0                 not null comment 'Sort order',
    description    varchar(500)                         null,
    is_active      tinyint(1) default 1                 not null,
    effective_from datetime                             null,
    effective_to   datetime                             null,
    created_by     varchar(100)                         null,
    updated_by     varchar(100)                         null,
    created_at     datetime   default CURRENT_TIMESTAMP not null,
    updated_at     datetime   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint uk_module_key_version
        unique (module, prompt_key, version)
);

create index idx_active
    on ai_prompts (is_active);

create index idx_module_order
    on ai_prompts (module, sort_order);

create table ai_prompts_audit
(
    id          bigint auto_increment
        primary key,
    prompt_id   bigint                             not null,
    module      varchar(50)                        not null,
    prompt_key  varchar(100)                       not null,
    old_content text                               null,
    new_content text                               null,
    old_version int                                null,
    new_version int                                null,
    action      varchar(20)                        not null,
    operator    varchar(100)                       null,
    remark      varchar(500)                       null,
    created_at  datetime default CURRENT_TIMESTAMP not null
);

create index idx_action_time
    on ai_prompts_audit (created_at);

create index idx_module_key
    on ai_prompts_audit (module, prompt_key);

create index idx_prompt_id
    on ai_prompts_audit (prompt_id);

create table ai_skill_packages
(
    id           int auto_increment
        primary key,
    name         varchar(64)                        not null,
    agent_type   varchar(32)                        null comment '归属 Agent 类型（draw/chat/code），NULL 表示通用',
    display_name varchar(128)                       not null,
    description  varchar(512)                       null,
    keywords     varchar(512)                       not null,
    prompt       text                               not null comment '技能提示词',
    is_active    tinyint  default 1                 null,
    created_at   datetime default CURRENT_TIMESTAMP null,
    updated_at   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint name
        unique (name)
)
    collate = utf8mb4_unicode_ci;

create table categories
(
    id          bigint auto_increment
        primary key,
    name        varchar(100)                       not null comment '分类名',
    slug        varchar(100)                       not null comment 'URL标识',
    parent_id   bigint                             null comment '父分类ID',
    description varchar(255)                       null comment '分类描述',
    sort_order  int      default 0                 not null comment '排序',
    created_by  bigint                             null comment '创建者用户ID',
    created_at  datetime default CURRENT_TIMESTAMP not null,
    updated_at  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    deleted_at  datetime                           null comment '软删除时间',
    is_delelte  int      default 0                 null comment '是否删除',
    slug_active varchar(100) as (if(is_delelte = 0 and deleted_at is null, slug, null)) virtual null,
    constraint uk_slug
        unique (slug_active)
)
    comment '分类表';

create index idx_created_by
    on categories (created_by);

create index idx_parent_id
    on categories (parent_id);

create table comments
(
    id            bigint auto_increment
        primary key,
    post_id       bigint            not null,
    parent_id     bigint            null,
    root_id       bigint            null,
    user_id       bigint            not null,
    author_name   varchar(50)       not null,
    author_avatar varchar(500)      null,
    reply_to_uid  bigint            null,
    reply_to_name varchar(50)       null,
    content       text              not null,
    status        tinyint default 0 not null,
    like_count    int     default 0 not null,
    reply_count   int     default 0 not null,
    ip_address    varchar(45)       null,
    created_at    datetime          not null,
    updated_at    datetime          not null,
    deleted_at    datetime          null,
    is_delelte    int     default 0 null
)
    collate = utf8mb4_unicode_ci;

create index idx_parent
    on comments (parent_id);

create index idx_post_status
    on comments (post_id, status, created_at);

create index idx_root
    on comments (root_id);

create index idx_user
    on comments (user_id);

create table image_usages
(
    id       bigint auto_increment comment '主键'
        primary key,
    image_id bigint      not null comment '图片ID',
    ref_type varchar(20) not null comment '引用类型：post / user / album / ...',
    ref_id   bigint      not null comment '引用对象ID',
    field    varchar(20) not null comment '引用字段：body / featured_image / avatar / cover / ...',
    constraint uk_usage
        unique (image_id, ref_type, ref_id, field)
)
    comment '图片引用关系';

create index idx_image_id
    on image_usages (image_id);

create index idx_ref
    on image_usages (ref_type, ref_id);

create table images
(
    id            bigint auto_increment comment '主键'
        primary key,
    url           varchar(500)                       not null comment '访问URL',
    storage_path  varchar(500)                       not null comment '存储路径',
    original_name varchar(255)                       not null comment '原始文件名',
    mime_type     varchar(50)                        not null comment 'MIME类型',
    file_size     bigint   default 0                 not null comment '文件大小',
    width         int                                null comment '图片宽度',
    height        int                                null comment '图片高度',
    md5           varchar(32)                        null comment '文件MD5',
    created_by    bigint                             null comment '上传者用户ID',
    created_at    datetime default CURRENT_TIMESTAMP not null,
    deleted_at    datetime                           null comment '软删除时间'
)
    comment '图片库';

create index idx_created_at
    on images (created_at);

create index idx_created_by
    on images (created_by);

create index idx_md5
    on images (md5);

create index idx_url
    on images (url(191));

create table like_records
(
    id         bigint auto_increment
        primary key,
    post_id    bigint                             not null comment '文章ID',
    visitor_id varchar(64)                        not null comment '浏览器指纹',
    created_at datetime default CURRENT_TIMESTAMP not null,
    constraint uk_post_visitor
        unique (post_id, visitor_id)
)
    comment '点赞记录表';

create index idx_post_id
    on like_records (post_id);

create table permissions
(
    id         bigint auto_increment
        primary key,
    code       varchar(100)                       not null comment '权限编码',
    label      varchar(50)                        not null comment '显示名',
    module     varchar(50)                        not null comment '所属模块',
    created_at datetime default CURRENT_TIMESTAMP not null
)
    comment '权限表';

create table post_contents
(
    id         bigint auto_increment
        primary key,
    post_id    bigint                                not null comment '关联文章ID',
    body       longtext                              not null comment '文章正文（Markdown/HTML）',
    format     varchar(20) default 'markdown'        not null comment '内容格式',
    created_at datetime    default CURRENT_TIMESTAMP not null,
    updated_at datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    is_delelte int         default 0                 null comment '是否删除',
    constraint uk_post_id
        unique (post_id)
)
    comment '文章内容表';

create table post_tags
(
    post_id bigint not null,
    tag_id  bigint not null,
    primary key (post_id, tag_id)
)
    comment '文章标签关联表';

create index idx_tag_id
    on post_tags (tag_id);

create table posts
(
    id             bigint auto_increment
        primary key,
    title          varchar(255)                       not null comment '文章标题',
    slug           varchar(255)                       not null comment 'URL标识',
    excerpt        text                               null comment '摘要',
    featured_image varchar(500)                       null comment '特色图片',
    status         tinyint  default 0                 not null comment '0-草稿，1-已发布，2-私密',
    author_id      bigint                             null comment '作者用户ID',
    category_id    bigint                             null comment '所属分类ID',
    view_count     int      default 0                 not null comment '浏览量',
    like_count     int      default 0                 not null comment '点赞数',
    published_at   datetime                           null comment '发布时间',
    comment_count  int      default 0                 not null comment '评论数',
    comment_enable int      default 0                 not null comment '是否允许评论',
    created_at     datetime default CURRENT_TIMESTAMP not null,
    updated_at     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    deleted_at     datetime                           null comment '软删除时间',
    is_delelte     int      default 0                 null comment '是否删除',
    slug_active    varchar(255) as (if(is_delelte = 0 and deleted_at is null, slug, null)) virtual null,
    constraint uk_slug
        unique (slug_active)
)
    comment '文章元数据表';

create index idx_author_id
    on posts (author_id);

create index idx_category_id
    on posts (category_id);

create index idx_status_published
    on posts (status, published_at);

create table role_permissions
(
    id            bigint auto_increment
        primary key,
    role_id       bigint not null comment '角色ID',
    permission_id bigint not null comment '权限ID',
    constraint uk_role_perm
        unique (role_id, permission_id)
)
    comment '角色权限关联表';

create table roles
(
    id          bigint auto_increment
        primary key,
    name        varchar(50)                        not null comment '角色名称：admin/author/user',
    label       varchar(50)                        not null comment '显示名',
    description varchar(255)                       null,
    sort_order  int      default 0                 not null,
    created_at  datetime default CURRENT_TIMESTAMP not null,
    updated_at  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP
)
    comment '角色表';

create table series
(
    id              bigint auto_increment
        primary key,
    title           varchar(255)                       not null comment '专栏名称',
    slug            varchar(255)                       not null comment 'URL标识',
    description     text                               null comment '专栏简介',
    cover_image_url varchar(500)                       null comment '封面图URL',
    category_id     bigint                             null comment '所属分类ID',
    is_completed    tinyint  default 0                 not null comment '0-未完结，1-已完结',
    sort_order      int      default 0                 not null comment '排序',
    created_by      bigint                             null comment '创建者用户ID',
    created_at      datetime default CURRENT_TIMESTAMP not null,
    updated_at      datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    deleted_at      datetime                           null comment '软删除时间',
    is_delelte      int      default 0                 null comment '是否删除',
    slug_active     varchar(255) as (if(is_delelte = 0 and deleted_at is null, slug, null)) virtual null,
    constraint uk_slug
        unique (slug_active)
)
    comment '专栏表';

create index idx_category_id
    on series (category_id);

create index idx_created_by
    on series (created_by);

create table series_posts
(
    series_id  bigint        not null,
    post_id    bigint        not null,
    sort_order int default 0 not null comment '专栏内排序',
    primary key (series_id, post_id)
)
    comment '专栏文章关联表';

create index idx_post_id
    on series_posts (post_id);

create table site_config
(
    id           bigint auto_increment
        primary key,
    config_key   varchar(100)                           not null,
    config_value varchar(500) default ''                not null,
    created_at   datetime     default CURRENT_TIMESTAMP not null,
    updated_at   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint config_key
        unique (config_key)
)
    comment '站点配置';

create table tags
(
    id         bigint auto_increment
        primary key,
    name       varchar(100)                       not null comment '标签名',
    slug       varchar(100)                       not null comment 'URL标识',
    created_by bigint                             null comment '创建者用户ID',
    created_at datetime default CURRENT_TIMESTAMP not null,
    updated_at datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    deleted_at datetime                           null comment '软删除时间',
    is_delelte int      default 0                 null comment '是否删除',
    slug_active varchar(100) as (if(is_delelte = 0 and deleted_at is null, slug, null)) virtual null,
    constraint uk_slug
        unique (slug_active)
)
    comment '标签表';

create index idx_created_by
    on tags (created_by);

create table user_diagrams
(
    id          bigint auto_increment
        primary key,
    user_id     bigint                                 not null comment '所属用户 ID',
    title       varchar(200) default '未命名图表'      not null comment '图表标题',
    description varchar(500)                           null comment '图表描述',
    tags        varchar(500)                           null comment '标签，JSON 数组字符串',
    xml_data    mediumtext                             not null comment 'draw.io XML 完整内容',
    thumbnail   longtext                               null comment '缩略图 Base64 data URL',
    file_size   int          default 0                 null comment 'xml_data 字节数',
    created_at  datetime     default CURRENT_TIMESTAMP not null,
    updated_at  datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    deleted_at  datetime                               null comment '软删除时间'
)
    comment '用户绘图存储表' collate = utf8mb4_unicode_ci;

create index idx_updated_at
    on user_diagrams (updated_at);

create index idx_user_id
    on user_diagrams (user_id);

create index idx_user_updated
    on user_diagrams (user_id asc, updated_at desc);

create table user_roles
(
    id      bigint auto_increment
        primary key,
    user_id bigint not null comment '用户ID',
    role_id bigint not null comment '角色ID',
    constraint uk_user_role
        unique (user_id, role_id)
)
    comment '用户角色关联表';

create table users
(
    id            bigint auto_increment
        primary key,
    username      varchar(50)                           not null comment '登录名',
    password_hash varchar(255)                          not null comment '加密密码',
    nickname      varchar(100)                          null comment '显示名称',
    email         varchar(100)                          null comment '邮箱',
    avatar        varchar(500)                          null comment '头像URL',
    role          varchar(20) default 'author'          not null comment '角色：admin/author',
    status        tinyint     default 1                 not null comment '1-正常，0-禁用',
    created_at    datetime    default CURRENT_TIMESTAMP not null,
    updated_at    datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    deleted_at    datetime                              null comment '软删除时间',
    is_delelte    int         default 0                 null comment '是否删除',
    last_login_at datetime                              null comment '最后登录时间',
    login_count   int         default 0                 null comment '登录次数',
    constraint uk_email
        unique (email),
    constraint uk_username
        unique (username)
)
    comment '用户表';

create table user_tokens
(
    id          bigint auto_increment
        primary key,
    user_id     bigint                               not null comment '用户ID',
    token_hash  varchar(64)                          not null comment 'SHA-256(token)',
    token_type  varchar(10)                          not null comment 'ACCESS / REFRESH',
    expires_at  datetime                             not null comment '过期时间',
    created_at  datetime   default CURRENT_TIMESTAMP not null comment '创建时间',
    revoked     tinyint(1) default 0                 not null comment '是否吊销',
    replaced_by varchar(64)                          null comment 'rotation: 被哪个新 token_hash 替换',
    constraint uk_token_hash
        unique (token_hash),
    constraint fk_token_user
        foreign key (user_id) references users (id)
)
    comment '用户令牌表';

create index idx_expires
    on user_tokens (expires_at);

create index idx_user_id
    on user_tokens (user_id);

-- ============================================================
-- 初始化数据
-- ============================================================

-- 角色表
INSERT INTO roles (id, name, label, description, sort_order) VALUES
(1, 'admin',  '管理员', '系统管理员，拥有所有权限', 0),
(2, 'author', '作者',   '内容创作者',             1),
(3, 'user',   '用户',   '普通注册用户',           2);

-- 管理员用户 (密码: admin123，首次登录后请修改)
INSERT INTO users (id, username, password_hash, nickname, email, role, status) VALUES
(1, 'ekko', '{noop}admin123', 'Ekko', 'ekko@example.com', 'admin', 1);

-- 用户角色关联
INSERT INTO user_roles (user_id, role_id) VALUES (1, 1);

-- 站点配置
INSERT INTO site_config (config_key, config_value) VALUES
('registration_enabled', 'true'),
('site_title', 'My Blog'),
('ai_draw_chat_enabled', 'true'),
('image_cleanup_days', '0'),
('reasoning_inject', 'true');

