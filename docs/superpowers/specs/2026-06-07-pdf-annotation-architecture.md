# PDF 标注渲染架构文档

> PDF.js 6.0.227 / viewer.html bridge / PdfViewer.tsx / Spring Boot API / MySQL

## 1. 全链路数据流

```
用户画标注 → 点保存
  │
  ├─ SAVE ──────────────────────────────────────────────────────────────
  │ [viewer.html bridge]
  │   1. commitOrRemove: 切换 mode=0 强制提交活跃编辑器（画笔必需）
  │   2. 遍历 app.pdfDocument.annotationStorage
  │   3. editor.serialize(false) → toPlain(TypedArray→Array) → 加 pageIndex
  │   4. JSON.stringify → postMessage('pdf-annotations') 到父窗口
  │
  │ [PdfViewer.tsx]
  │   5. 收到 pdf-annotations → savePdfAnnotation(id, page=0, data)
  │
  │ [Spring Boot]
  │   6. PUT /api/v1/pdf/{id}/annotations/page/0 → MySQL INSERT ON DUPLICATE KEY UPDATE
  │
  │ [MySQL]
  │   7. pdf_annotations 表，data 列存 JSON 字符串
  │
  ├─ RESTORE ───────────────────────────────────────────────────────────
  │ [PdfViewer.tsx]
  │   1. iframe 加载 → bridge-hooked → GET /api/v1/pdf/{id}/annotations?page=0
  │   2. JSON.parse → postMessage('restore-annotations', data) 到 iframe
  │
  │ [viewer.html bridge]
  │   3. 提取所有唯一 pageIndex → 导航到每页（强制懒加载）
  │   4. 逐条标注：
  │      a. outlines 数字键对象→数组（荧光笔格式A修复）
  │      b. Float32Array 还原（画笔路径）
  │      c. layer.deserialize(val) → 重建编辑器实例
  │      d. editor.render()（如果无 DOM）
  │      e. Freetext 坐标修正：PDF绝对坐标→相对坐标+Y轴翻转
  │      f. layer.add(editor) → annotationStorage.setValue()
  │   5. viewer.update() + 模式切换 0→3→0 强制刷新
```

---

## 2. 三种标注数据结构

### 2.1 荧光笔 Highlight (annotationType=9)

```json
{
  "annotationType": 9,
  "pageIndex": 0,
  "rect": [x1, y1, x2, y2],
  "color": [255, 255, 0],
  "opacity": 1,
  "outlines": <两种格式之一>
}
```

**outlines 格式A：文字选中荧光笔**
```json
{
  "outlines": [[x1,y1,x2,y2,...], [x1,y1,x2,y2,...], ...]
}
```
序列化产出普通数组，恢复无需特殊处理。

**outlines 格式B：自由绘制荧光笔**
```json
{
  "outlines": { "points": [...], "outline": [...] }
}
```
序列化产出命名键对象，恢复时**不能转数组**。

**关键修复（viewer.html:108）：** 只对全部 key 为数字的 outlines 做 `{0,1,2...}→[...]` 转换。`{points, outline}` 保持原样。

### 2.2 文字批注 Freetext (annotationType=3)

```json
{
  "annotationType": 3,
  "pageIndex": 0,
  "rect": [x1, y1, x2, y2],
  "color": [0, 0, 0],
  "value": "文字内容",
  "fontSize": 10,
  "rotation": 0
}
```

**坐标修正（必须）：**
```
rect [left, bottom, right, top] 是 PDF 绝对坐标（左下原点）
FreetextEditor 用相对坐标 (x, y, w, h 都是 0~1)

转换: x = left / pageWidth
      y = (pageHeight - top) / pageHeight  ← Y轴翻转!
      w = (right - left) / pageWidth
      h = (top - bottom) / pageHeight
```

### 2.3 画笔 Ink (annotationType=15)

```json
{
  "annotationType": 15,
  "pageIndex": 0,
  "rect": [x1, y1, x2, y2],
  "color": [255, 0, 0],
  "thickness": 1,
  "opacity": 1,
  "paths": {
    "lines": [
      [x1,y1, x2,y2, ...],
      ...
    ],
    "points": [...]
  }
}
```

**lines[i] 结构：** 在序列化前是 Float32Array，`toPlain()` 转普通数组。恢复时 `InkEditor.deserialize()` 内部处理坐标，无需外部修正。

**关键问题：InkEditor 不自动提交**
- 荧光笔/文字：创建完自动 commit → annotationStorage
- 画笔：画完保持 active 状态，切换工具时才 commit
- **修复（viewer.html:96-98）：** 保存前切 mode=0 → setTimeout(100ms) → 再读 storage

---

## 3. 页面定位机制

```
pageIndex = 0-based 页码（editor.pageIndex）
viewer._pages[pageIndex] = PDFPageView 实例
viewer.currentPageNumber = pageIndex + 1  （1-based 页面号，用于导航）

page.annotationEditorLayer            → AnnotationEditorLayerBuilder（构建器）
    .annotationEditorLayer            → AnnotationEditorLayer（真正的编辑器层）
```

**懒加载问题：** `viewer._pages[pi]` 在页面被访问前为 null。恢复前必须先 `viewer.currentPageNumber = pi + 1` 导航到每一页，等待 300ms 让页面渲染。

---

## 4. toPlain 函数设计

```js
function toPlain(o) {
  if (TypedArray) return Array.from(o);           // Float32Array → [...]
  if (Array)     return o.map(toPlain);           // 递归处理数组元素
  if (object)    { var r={}; for(k in o) r[k]=toPlain(o[k]); return r; }  // 遍历所有可枚举属性
  return o;                                        // 原始值
}
```

**为什么不用 constructor===Object 判断：**
InkDrawOutline 是 PDF.js 内部类实例（constructor !== Object），包含 `points: Float32Array` 和 `bezier: Float32Array`。必须也遍历它的属性，否则内部的 TypedArray 残留，JSON.stringify 会毁掉。

---

## 5. 故障排查速查

| 现象 | 根因 | 位置 |
|------|------|------|
| 保存后 data length=2 | InkEditor 未 commit | 保存前切 mode=0+setTimeout |
| `L*_pts:MISSING` | toPlain 未处理 class 实例 | toPlain 去掉 constructor 检查 |
| outlines 修复后报错 highlight.js:993 | `{points,outline}` 格式被错误转数组 | 只转数字 key 对象 |
| 恢复后标注不可见 | iframeRef 闭包捕获 null | 用 iframeRef.current 实时读取 |
| bridge-hooked 后 restore 无反应 | pdfDocument 异步未加载 | 等 pdfDocument 就绪再发 bridge-hooked |
| 恢复时标注串页 | pageIndex 映射错误 | 确认 0-based ↔ 1-based 转换一致 |
| UIManager 不可访问 | PDF.js 内部封闭 | 只能通过 mode 切换触发 commit |

## 6. 调试开关

```js
// viewer.html 第一行
var ANNOTATION_DEBUG = true;  // 生产环境设为 false
```

日志前缀：
- `[Bridge] SAVE` — 保存流程
- `[Bridge] RESTORE` — 恢复流程
- `[toPlain]` — TypedArray 转换
- `[PdfViewer]` — React 侧 bridge 通信
