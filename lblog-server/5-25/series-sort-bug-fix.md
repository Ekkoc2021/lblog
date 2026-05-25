# 更新文章时专栏排序被重置 — 修复设计

> 状态：设计中 | 日期：2026-05-25

## 一、问题

编辑文章（改标题、标签、正文等）后，文章在专栏中的 `sort_order` 被重置为最大值，排到专栏末尾。

## 二、根因

`PostsServiceImpl.updatePost()` 第 508-516 行：

```java
// 更新专栏关联（先删，有值再插）
seriesPostsMapper.deleteByPostId(id);          // ← 无条件删除
if (req.getSeriesId() != null) {
    SeriesPosts sp = new SeriesPosts();
    sp.setSeriesId(req.getSeriesId());
    sp.setPostId(id);
    sp.setSortOrder(seriesPostsMapper.selectMaxSortOrder(req.getSeriesId()) + 1);  // ← MAX+1 排到末尾
    seriesPostsMapper.insert(sp);
}
```

每次 `updatePost` 被调用（改任何字段），都会**无条件删除**已有的 `series_posts` 记录，然后重新插入并分配 `sort_order = MAX + 1`。即使用户根本没动专栏设置，排序也被推到末尾。

## 三、修复

只在 `seriesId` **发生变化**时才删旧插新，否则保持原排序不变。

```java
// 更新专栏关联（仅 seriesId 变化时才操作，保留原 sort_order）
SeriesPosts existingLink = seriesPostsMapper.selectByPostId(id);
Long oldSeriesId = existingLink != null ? existingLink.getSeriesId() : null;
Long newSeriesId = req.getSeriesId();

if (!Objects.equals(oldSeriesId, newSeriesId)) {
    seriesPostsMapper.deleteByPostId(id);
    if (newSeriesId != null) {
        SeriesPosts sp = new SeriesPosts();
        sp.setSeriesId(newSeriesId);
        sp.setPostId(id);
        sp.setSortOrder(seriesPostsMapper.selectMaxSortOrder(newSeriesId) + 1);
        seriesPostsMapper.insert(sp);
    }
}
```

**行为变化：**

| 场景 | 修改前 | 修改后 |
|------|--------|--------|
| 不改专栏，改标题 | sort_order 重置为 MAX+1 | 保持原值 |
| 不改专栏，改标签 | sort_order 重置为 MAX+1 | 保持原值 |
| 不改专栏，改正文 | sort_order 重置为 MAX+1 | 保持原值 |
| A 专栏 → B 专栏 | sort_order 重置为 MAX+1 | sort_order 重置为 MAX+1（正确） |
| 未关联 → 关联专栏 | sort_order = MAX+1 | sort_order = MAX+1（不变） |
| 关联专栏 → 取消关联 | 删除记录 | 删除记录（不变） |

## 四、改动清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `PostsServiceImpl.java` L508-516 | 加 `existingLink` 查询 + `equals` 判断，仅在 seriesId 变化时操作 |

无需改前端。

## 五、测试要点

| # | 场景 | 预期 |
|---|------|------|
| 1 | 文章已在专栏 A，编辑标题后保存 | sort_order 不变 |
| 2 | 文章已在专栏 A，改标签后保存 | sort_order 不变 |
| 3 | 文章已在专栏 A，改正文后保存 | sort_order 不变 |
| 4 | 文章已在专栏 A，改为专栏 B 后保存 | sort_order = B 专栏的 MAX+1，旧记录删除 |
| 5 | 文章未关联专栏，关联专栏 A 后保存 | sort_order = A 专栏的 MAX+1 |
| 6 | 文章已在专栏 A，取消关联后保存 | 旧记录删除，不插入新记录 |
| 7 | 文章已在专栏 A，连续保存两次（不改专栏） | 两次都不影响 sort_order |
| 8 | 新建文章时关联专栏 | sort_order = MAX+1（不受影响） |

## 六、实施检查清单

- [ ] `PostsServiceImpl.updatePost()` L508-516 替换为新逻辑
- [ ] 编译通过
- [ ] 按测试要点逐条验证
