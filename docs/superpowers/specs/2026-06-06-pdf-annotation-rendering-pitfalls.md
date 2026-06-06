# PDF.js 标注存取 — 完整踩坑记录与正确做法

> 本文档是对 PDF.js v6.0.227 标注存取功能的完整调试记录。
> 每个问题都经过源码级验证，包含根因分析和可工作的解决方案。
> 按照本文档可以在任何新项目中集成 PDF.js 标注存取功能。

---

## 1. 存取路径选择

### 现象
尝试了多种存取路径，大部分失败。最终只有一条路径可用。

### 失败路径及根因

| 路径 | 存 | 取 | 为什么失败 |
|------|----|----|-----------|
| A | `annotationStorage.serializable` | `annotationStorage.setValue(key, data)` | `setValue` 只存数据，不创建编辑器实例。PDF.js 渲染管道需要 `AnnotationEditor` 实例才能渲染 |
| B | `editor.serialize(true)` | `layer.deserialize(data)` | `serialize(true)` 的 `true` 参数表示"用于剪贴板复制"。高亮标注（HighlightEditor）在 `serialize(true)` 时直接返回 null（pdf.mjs 源码：`editor.serialize(editor.mode !== AnnotationEditorType.HIGHLIGHT)` 在 mode 为 HIGHLIGHT 时传 false） |
| C | `editor.serialize(false)` + 精简字段 | `layer.deserialize(data)` + `addOrRebuild` | `addOrRebuild` 对反序列化后的编辑器（div 为 null）走 `add()` 分支，只加入内部数组不创建 DOM |
| D | `editor.serialize(false)` + 全字段 | `layer.deserialize(data)` + `editor.rebuild()` + `editor.show()` | `rebuild()` 需要运行时 DOM 选区数据，反序列化创建的编辑器没有 |

### 唯一可用路径

```
保存: annotationStorage 条目 → editor.serialize(false) → toPlain(TypedArray→Array) → JSON → 后端
恢复: JSON → TypedArray 还原 → outlines 修正 → layer.deserialize() → layer.add() → setValue(key, editor) → viewer.update()
```

---

## 2. `serialize(false)` vs `serialize(true)`

### 根因

PDF.js 源码中 `AnnotationEditor.serialize(isForCopying)` 的参数控制序列化行为：

```js
// pdf.mjs, AnnotationEditor.serialize()
serialize(isForCopying) {
    // isForCopying=false → 用于持久化，所有类型都序列化
    // isForCopying=true  → 用于剪贴板，高亮返回 null
}
```

各编辑器对 `isForCopying` 的处理不同：

| 编辑器 | `serialize(true)` | `serialize(false)` |
|--------|-------------------|-------------------|
| HighlightEditor | null（设计决定：高亮不可复制粘贴） | 完整数据 |
| FreeTextEditor | 完整数据 | 完整数据 |
| InkEditor | 完整数据 | 完整数据 |

### 正确做法

```js
// 始终用 false
var s = editor.serialize(false);
```

---

## 3. TypedArray 序列化

### 根因

`editor.serialize(false)` 返回的对象中，画笔（Ink）标注的 `paths.lines[].points` 和 `paths.lines[].bezier` 是 `Float32Array` 实例。`JSON.stringify` 对 TypedArray 的处理不是输出数组，而是输出索引对象：

```js
JSON.stringify(new Float32Array([1.5, 2.3, 3.7]))
// 结果: '{"0":1.5,"1":2.3,"2":3.7}'   ← 不是 "[1.5,2.3,3.7]"
```

恢复时 `deserialize` 内部对 `paths.lines[].points` 调 `.map()` 方法。普通对象 `{0:1.5, 1:2.3}` 没有 `.map()`，报错：`lines[i].map is not a function`。

### 正确做法

**存储前：** 递归遍历对象，将所有 TypedArray 转成普通数组。

```js
function toPlain(obj) {
    // TypedArray → Array
    if (obj instanceof Float32Array || obj instanceof Float64Array ||
        obj instanceof Int32Array  || obj instanceof Uint8Array) {
        return Array.from(obj);
    }
    // 数组 → 递归处理元素
    if (Array.isArray(obj)) {
        return obj.map(toPlain);
    }
    // 普通对象 → 递归处理每个属性
    if (obj && typeof obj === 'object' && obj.constructor === Object) {
        var result = {};
        for (var key in obj) {
            if (Object.prototype.hasOwnProperty.call(obj, key)) {
                result[key] = toPlain(obj[key]);
            }
        }
        return result;
    }
    // 原始值 → 直接返回
    return obj;
}
```

**恢复时：** 将 paths 中的数组转回 Float32Array。

```js
if (val.paths) {
    // lines 是数组，每个元素是对象 {points: [], bezier: []}
    if (val.paths.lines && Array.isArray(val.paths.lines)) {
        val.paths.lines = val.paths.lines.map(function(line) {
            // line 是对象不是数组，检查类型后转内部字段
            if (line && typeof line === 'object' && !Array.isArray(line)) {
                if (line.points && Array.isArray(line.points)) {
                    line.points = new Float32Array(line.points);
                }
                if (line.bezier && Array.isArray(line.bezier)) {
                    line.bezier = new Float32Array(line.bezier);
                }
            }
            return line;
        });
    }
    // points 是数组，每个元素是数组
    if (val.paths.points && Array.isArray(val.paths.points)) {
        val.paths.points = val.paths.points.map(function(p) {
            return Array.isArray(p) ? new Float32Array(p) : p;
        });
    }
}
```

### 为什么 lines 元素是对象而非数组

`InkDrawOutline` 的结构定义：

```js
// lines[i] 的结构:
{
    points: Float32Array([x1, y1, x2, y2, ...]),   // 笔迹控制点
    bezier: Float32Array([bx1, by1, bx2, by2, ...])  // 贝塞尔曲线点
}
```

不是 `[[x1, y1, ...], [x2, y2, ...]]` 这种二维数组。所以 `Array.isArray(line)` 为 false，必须用 `typeof line === 'object'` 检查。

---

## 4. deserialize 的 annotationType 查找机制

### 根因

PDF.js 内部的编辑器类型注册表（pdf.mjs）：

```js
// 静态 Map，key 是 editor._editorType（数字），value 是编辑器类
AnnotationEditorLayer.#editorTypes = new Map(
    [FreeTextEditor, InkEditor, StampEditor, HighlightEditor, SignatureEditor]
        .map(type => [type._editorType, type])
);
```

`deserialize` 查找时用 `data.annotationType` 去这个 Map 中查：

```js
// pdf.mjs AnnotationEditorLayer.deserialize():
async deserialize(data) {
    var editorClass = AnnotationEditorLayer.#editorTypes
        .get(data.annotationType ?? data.annotationEditorType);
    if (!editorClass) return null;  // Map 查不到就返回 null
    return await editorClass.deserialize(data, this, this.#uiManager);
}
```

Map 的 key 来自 `_editorType` 静态属性，值是数字：

| 编辑器类 | `_editorType`（Map key） | `_type`（日志/显示用） |
|----------|--------------------------|------------------------|
| FreeTextEditor | 3 | `"freetext"` |
| HighlightEditor | 9 | `"highlight"` |
| InkEditor | 15 | `"ink"` |

手写 annotationType 时容易误用 `_type` 字符串，导致 Map 查不到，`deserialize` 静默返回 null。

### 正确做法

不要手写 `annotationType`。`editor.serialize(false)` 的输出已经包含正确的数字 `annotationType` 字段，直接用就行。

---

## 5. layer.addOrRebuild 不渲染

### 根因

`addOrRebuild` 方法（pdf.mjs AnnotationEditorLayer）：

```js
addOrRebuild(editor) {
    if (editor.needsToBeRebuilt()) {
        // 分支 A：编辑器已有 DOM，重新构建
        editor.parent ||= this;
        editor.rebuild();
        editor.show();
    } else {
        // 分支 B：编辑器无 DOM，只加入内部编辑器列表
        this.add(editor);
    }
}
```

`needsToBeRebuilt()` 的实现：

```js
needsToBeRebuilt() {
    return this.div && !this.isAttachedToDOM;
    //      ^^^^^^^^  反序列化创建的编辑器 this.div 是 null
    //                所以返回 false，走分支 B
}
```

分支 B 的 `this.add(editor)` 只把编辑器加入 `this.#editors` Map，不创建 DOM 元素，不渲染。

### 正确做法

```js
var editor = await layer.deserialize(val);
if (!editor.div) {
    editor.render();  // 手动创建 DOM（div + 内部元素）
}
layer.add(editor);  // 加入 AnnotationEditorLayer 的编辑器列表
doc.annotationStorage.setValue(key, editor);  // 注册到 PDF 文档的注解存储
viewer.update();  // 触发全局重绘
```

**注意：** 不要用 `editor.rebuild()` 替代 `editor.render()`。`rebuild()` 要求编辑器已经有一次完整渲染（有选区 DOM 数据），反序列化创建的编辑器没有，会报错。

---

## 6. 文字编辑器在非文字模式下的可见性问题

### 根因

文字编辑器（FreeTextEditor）的 DOM 元素在 `annotationEditorMode` 不是 `FREETEXT(3)` 时可能不渲染。`editor.show()` 能解决大部分情况，但在某些时序下（特别是首次加载时）文字编辑器的 CSS display 属性仍可能被覆盖。

实测中需要在所有标注恢复完成后，短暂激活文字模式再切回，触发 PDF.js 内部的编辑器可见性更新逻辑：

```js
// 所有标注恢复完成后
viewer.update();
// 短暂激活文字模式 → 切回，强制文字编辑器刷新可见性
app.pdfViewer.annotationEditorMode = { mode: 3 };  // 3 = FREETEXT
setTimeout(function() {
    app.pdfViewer.annotationEditorMode = { mode: 0 };  // 0 = NONE
}, 100);
```

这个工作区不会改变用户当前选中的工具，因为 100ms 后自动切回。对用户透明。

---

## 7. 高亮 outlines 格式修正

### 根因

`editor.serialize(false)` 产出的高亮标注中，`outlines` 字段是对象格式 `{0: [...], 1: [...], ...}`（JavaScript 中 TypedArray/特殊对象经 JSON 循环引用处理后的产物）。但 `HighlightEditor.deserialize` 内部访问 `outlines[0]` 时，如果 `outlines` 是纯对象（key 是字符串 "0"、"1"），`outlines[0]` 可能是 undefined（取决于对象是否有数字键）。

**注意：** 这个问题的触发是间歇性的，取决于 `toPlain()` 如何处理 outlines 内部结构。`toPlain()` 把 TypedArray 转成了数组，但 outlines 顶层结构可能保持为对象或变成数组，取决于原始数据结构。

### 正确做法

恢复前强制转换 outlines 为数组：

```js
if (val.outlines && typeof val.outlines === 'object' && !Array.isArray(val.outlines)) {
    var arr = [];
    for (var k in val.outlines) {
        if (Object.prototype.hasOwnProperty.call(val.outlines, k)) {
            arr.push(val.outlines[k]);
        }
    }
    val.outlines = arr;
}
```

---

## 7. Freetext（文字批注）坐标修正

### 根因

文字编辑器内部使用**相对坐标**（x、y、width、height 都是 0-1 之间的比例值，相对于页面宽高）。`serialize(false)` 产出的是 `rect`（四个绝对坐标值，单位为 PDF 点）：

```js
rect: [x1, y1, x2, y2]  // 绝对坐标，PDF 坐标系（左下为原点）
```

`deserialize` 创建编辑器后，没有自动把 `rect` 转换回相对坐标。编辑器创建后 `x/y/width/height` 为默认值 0，导致编辑器 DOM 元素不可见。

**注意：** PDF 坐标系 Y 轴向上（左下为原点），而 DOM 坐标系 Y 轴向下（左上为原点）。rect[3] 在 PDF 坐标系中是上方 Y 值，需要翻转。

### 正确做法

```js
if (val.annotationType === 3) {  // 仅文字批注需要坐标修正
    var pw = editor.pageDimensions[0];  // 页面宽度（PDF 点）
    var ph = editor.pageDimensions[1];  // 页面高度（PDF 点）
    var r = val.rect;                   // [x1, y1, x2, y2]
    if (r && pw && ph) {
        editor.x = r[0] / pw;                           // 左边距比例
        editor.y = (ph - r[3]) / ph;                    // 上边距比例（Y 轴翻转）
        editor.width  = (r[2] - r[0]) / pw;            // 宽度比例
        editor.height = (r[3] - r[1]) / ph;            // 高度比例
    }
    editor.fixAndSetPosition();  // 将比例坐标应用到编辑器 DOM
    if (val.value && editor.editorDiv) {
        editor.editorDiv.textContent = val.value;  // 恢复文字内容
    }
}
```

---

## 8. 文字编辑器在非文字模式下的可见性问题

### 根因

文字编辑器（FreeTextEditor）有基于当前编辑模式的显示/隐藏逻辑。当 `annotationEditorMode` 不是 `FREETEXT(3)` 时，文字编辑器可能处于隐藏状态。恢复后虽然 DOM 创建了、坐标正确了，但 CSS display 或 visibility 可能被设置为隐藏。

### 正确做法

恢复文字标注后调用 `editor.show()`：

```js
if (val.annotationType === 3) {
    // ...坐标修正...
    try { editor.show(); } catch(e) {}
}
```

---

## 9. 识别每个标注的所属页

### 根因

`editor.serialize(false)` 的输出中不含 `pageIndex` 字段。但编辑器实例上有 `editor.pageIndex` 属性（0-based）。

### 正确做法

保存时把 `editor.pageIndex` 写入序列化数据：

```js
var s = editor.serialize(false);
s = toPlain(s);
s.pageIndex = editor.pageIndex;  // 添加页码
data.push({ key: key, value: s });
```

恢复时从 `val.pageIndex` 取页码，定位到对应页的 annotationEditorLayer：

```js
var pi = val.pageIndex || 0;
var page = viewer._pages[pi];
if (!page.annotationEditorLayer) {
    // 页面可能未渲染，触发渲染
    page._renderAnnotationEditorLayer();
}
var layer = page.annotationEditorLayer.annotationEditorLayer;
```

---

## 10. 访问 annotationEditorLayer 的正确路径

### 路径说明

```js
viewer._pages[pageIndex]                              // PDFPageView 实例
    .annotationEditorLayer                             // AnnotationEditorLayerBuilder 实例
    .annotationEditorLayer                             // AnnotationEditorLayer 实例（我们需要的）
```

第一层 `.annotationEditorLayer` 是 `AnnotationEditorLayerBuilder`（构建器），第二层 `.annotationEditorLayer` 才是真正的 `AnnotationEditorLayer`（编辑器层）。这个命名容易混淆。

---

## 11. 完整 Bridge 脚本

将此脚本内联插入 `viewer.html` 的 `</head>` 之前。

**前置条件：** viewer.html 的 CSP 必须包含 `'unsafe-inline'`：
```html
content="... script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval'; ..."
```

```html
<script>
window.parent.postMessage({type:'bridge-loaded'},'*');
(function(){
  var poll=0;
  function init(){
    var app=window.PDFViewerApplication;
    if(!app||!app.pdfViewer||!app.eventBus){if(++poll<50)setTimeout(init,200);return;}
    window.parent.postMessage({type:'bridge-hooked'},'*');

    // ====== SAVE ======
    var lastSize=0;
    setInterval(function(){
      var doc=app.pdfDocument;if(!doc)return;
      var s=doc.annotationStorage;
      if(s.size===0){lastSize=0;return;}
      if(s.size===lastSize)return;
      lastSize=s.size;
      var data=[];
      for(var e of s){try{
        var k=e[0],v=e[1];
        if(v&&typeof v.serialize==='function'){
          var d=v.serialize(false);
          if(d){
            d=function toPlain(o){
              if(o instanceof Float32Array||o instanceof Float64Array||o instanceof Int32Array||o instanceof Uint8Array)return Array.from(o);
              if(Array.isArray(o))return o.map(toPlain);
              if(o&&typeof o==='object'&&o.constructor===Object){var r={};for(var x in o)if(Object.prototype.hasOwnProperty.call(o,x))r[x]=toPlain(o[x]);return r;}
              return o;
            }(d);
            d.pageIndex=v.pageIndex;
            data.push({key:k,value:d});
          }
        }
      }catch(e){}}
      if(data.length)window.parent.postMessage({type:'pdf-annotations',data:JSON.stringify(data)},'*');
    },3000);

    // ====== RESTORE ======
    window.addEventListener('message',function(evt){
      if(evt.data.type!=='restore-annotations'||!app.pdfDocument)return;
      var data=evt.data.data,doc=app.pdfDocument,viewer=app.pdfViewer,c=0;
      (async function(){
        for(var i=0;i<data.length;i++){try{
          var val=data[i].value,key=data[i].key;
          if(!val||typeof val!=='object')continue;
          var pi=val.pageIndex||0;
          var page=viewer._pages[pi];if(!page)continue;
          if(!page.annotationEditorLayer){try{page._renderAnnotationEditorLayer();}catch(e){continue;}}
          var layer=page.annotationEditorLayer.annotationEditorLayer;if(!layer)continue;
          // outlines 对象→数组
          if(val.outlines&&typeof val.outlines==='object'&&!Array.isArray(val.outlines)){
            var arr=[];for(var k in val.outlines){if(Object.prototype.hasOwnProperty.call(val.outlines,k))arr.push(val.outlines[k]);}
            val.outlines=arr;
          }
          // TypedArray 恢复
          if(val.paths){try{
            if(val.paths.lines&&Array.isArray(val.paths.lines))
              val.paths.lines=val.paths.lines.map(function(L){if(L&&typeof L==='object'&&!Array.isArray(L)){if(L.points&&Array.isArray(L.points))L.points=new Float32Array(L.points);if(L.bezier&&Array.isArray(L.bezier))L.bezier=new Float32Array(L.bezier);}return L;});
            if(val.paths.points&&Array.isArray(val.paths.points))
              val.paths.points=val.paths.points.map(function(p){return Array.isArray(p)?new Float32Array(p):p;});
          }catch(e){}}
          var editor=await layer.deserialize(val);
          if(editor){
            if(!editor.div){try{editor.render();}catch(e){}}
            if(val.annotationType===3&&editor.pageDimensions){
              var pw=editor.pageDimensions[0],ph=editor.pageDimensions[1],r=val.rect;
              if(r){editor.x=r[0]/pw;editor.y=(ph-r[3])/ph;editor.width=(r[2]-r[0])/pw;editor.height=(r[3]-r[1])/ph;}
              if(typeof editor.fixAndSetPosition==='function')editor.fixAndSetPosition();
              if(val.value&&editor.editorDiv){try{editor.editorDiv.textContent=val.value;}catch(e){}}
              try{editor.show();}catch(e){}
            }
            layer.add(editor);doc.annotationStorage.setValue(key,editor);c++;
          }
        }catch(e){console.error('[Bridge] restore:',e.message||e);}}
        console.log('[Bridge] Restored',c,'/',data.length);
        if(c>0){viewer.update();try{app.pdfViewer.annotationEditorMode={mode:3};setTimeout(function(){app.pdfViewer.annotationEditorMode={mode:0};},100);}catch(e){}}
      })();
    });

    // ====== PAGE CHANGE ======
    app.eventBus.on('pagechanging',function(evt){
      window.parent.postMessage({type:'pdf-page-change',page:evt.pageNumber},'*');
    });
  }
  setTimeout(init,500);
})();
</script>
```

---

## 12. 错误速查表

| 错误信息 | 直接原因 | 对应坑点 |
|---------|---------|---------|
| 高亮序列化后丢失 | `serialize(true)` 返回 null | #2 |
| `lines[i].map is not a function` | Float32Array 被 JSON.stringify 破坏 | #3 |
| `Cannot read properties of undefined (reading '0')` | outlines 格式是对象不是数组 | #6 |
| deserialize 返回 null | annotationType 用字符串而非数字 | #4 |
| addOrRebuild 后不显示 | needsToBeRebuilt() 返回 false | #5 |
| PDFViewer 初始化崩溃 | 容器缺少完整 DOM | 用 iframe 方式 |
| bridge 脚本不执行 | CSP 缺少 unsafe-inline | #10 |
| 恢复后标注不显示 | 缺少 viewer.update() | - |
| 文字标注位置错误/不可见 | 坐标未修正 | #7, #8 |
| 标注存了但恢复时找不到 | 没保存 pageIndex | #9 |

---

## 13. 保存/恢复完整流程

### 13.1 保存（save-annotations 消息）

```
用户点击保存按钮
  → PdfReaderPage.handleSave()
    → viewerRef.current.save()
      → postMessage({type:'save-annotations'}) 到 iframe
        → bridge: 遍历 annotationStorage
          → 每个条目 val.serialize(false)
            → toPlain() 转 TypedArray → 普通数组
            → s.pageIndex = val.pageIndex （0-based）
            → 收集到 [{key, value}] 数组
          → postMessage({type:'pdf-annotations', data: JSON})
            → PdfViewer 收到 → savePdfAnnotation(id, 0, data)
              → PUT /api/v1/pdf/{id}/annotations/page/0
```

**关键代码：** `annotationStorage` 条目 → `editor.serialize(false)` → `toPlain()`。不是 `serializable` 全局属性，不是 `serialize(true)`。

### 13.2 恢复（restore-annotations 消息）

```
PdfViewer 收到 bridge-hooked
  → GET /api/v1/pdf/{id}/annotations?page=0
    → postMessage({type:'restore-annotations', data: [...]}) 到 iframe
      → bridge: 
        1. 提取所有唯一 pageIndex 值
        2. 对每个 pageIndex，导航到对应页（viewer.currentPageNumber = pageIndex + 1）
           等待 300ms 让页面渲染
        3. 回到原始页面
        4. 遍历每条数据：
           a. outlines 对象→数组转换
           b. TypedArray 还原（Float32Array）
           c. layer.deserialize(val) → 创建编辑器
           d. editor.render()（如果无 div）
           e. Freetext 坐标修正（annotationType===3）
           f. layer.add(editor)
           g. annotationStorage.setValue(key, editor)
        5. viewer.update()
        6. 短暂激活 annotationEditorMode=3 再切回 0
```

**关键点：** 恢复前必须先导航到每个标注所在页面，强制 PDF.js 懒加载这些页面。否则 `viewer._pages[pi]` 为 null，无法访问 `annotationEditorLayer`。

### 13.3 页面懒加载问题

PDF.js 使用懒加载，只有当前可见页及其附近的页才会被渲染。恢复标注时，如果标注所在的页面没有渲染过，`viewer._pages[pageIndex]` 为 null。

**修复：** 恢复前遍历所有标注的唯一 pageIndex，设置 `viewer.currentPageNumber` 到每一页，等待 300ms 让页面渲染，再切换回去。

```js
// 强制加载所有有标注的页面
var savedPage = viewer.currentPageNumber;
var pages = [...new Set(data.map(d => d.value.pageIndex || 0))];
for (var p of pages) {
    viewer.currentPageNumber = p + 1;
    await new Promise(r => setTimeout(r, 300));
}
viewer.currentPageNumber = savedPage;
```

---

## 14. 集成清单

在一个全新项目中集成 PDF.js 标注存取功能，需要：

1. **部署 PDF.js：** 下载发布包 → `public/pdfjs/`（保持 `web/` + `build/` 结构）
2. **修改 viewer.html：** CSP 加 `'unsafe-inline'` + 在 `</head>` 前插入 bridge 脚本（完整脚本见 §11）
3. **写 PdfViewer.tsx：** iframe + 4 个 postMessage 事件处理（bridge-hooked / pdf-page-change / pdf-annotations / save-complete）
4. **写后端 API：** 标注存取接口（GET + PUT /{pdfId}/annotations/page/0）
5. **数据库：** `pdf_annotations` 表（pdf_id + page_num + user_id + data JSON）
6. **验证：** 打开 PDF → 用三种标注工具 → 点保存 → 刷新 → 标注仍在（所有页面）
