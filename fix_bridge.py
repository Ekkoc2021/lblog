"""Fix bridge script in viewer.html"""
import sys

path = sys.argv[1] if len(sys.argv) > 1 else r"E:\workspace\java\lblog\lblog-web\public\pdfjs\web\viewer.html"

with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

MARKER = '<script src="viewer.mjs" type="module"></script>'
END = '</head>'

idx1 = content.find(MARKER)
idx2 = content.find(END, idx1)

assert idx1 > 0 and idx2 > 0, f"markers not found: {idx1}, {idx2}"

BRIDGE = '''<script src="viewer.mjs" type="module"></script>
  <script>
  window.parent.postMessage({type:'bridge-loaded'},'*');
  (function(){
    var poll=0;
    function init(){
      var app=window.PDFViewerApplication;
      if(!app||!app.pdfViewer||!app.eventBus){if(++poll<50)setTimeout(init,200);return;}
      console.log('[Bridge] Hooked');
      window.parent.postMessage({type:'bridge-hooked'},'*');

      window.addEventListener('message',function(evt){
        function toPlain(o){
          if(o instanceof Float32Array||o instanceof Float64Array||o instanceof Int32Array||o instanceof Uint8Array)return Array.from(o);
          if(Array.isArray(o))return o.map(toPlain);
          if(o&&typeof o==='object'&&o.constructor===Object){var r={};for(var k in o)r[k]=toPlain(o[k]);return r;}
          return o;
        }
        // === SAVE ===
        if(evt.data.type==='save-annotations'&&app.pdfDocument){
          var storage=app.pdfDocument.annotationStorage;
          var saveData=[];
          for(var entry of storage){
            var val=entry[1];
            if(val&&typeof val.serialize==='function'){
              var s=toPlain(val.serialize(false));
              s.pageIndex=val.pageIndex;
              saveData.push({key:entry[0],value:s});
            }
          }
          window.parent.postMessage({type:'pdf-annotations',data:JSON.stringify(saveData)},'*');
        }
        // === JUMP TO PAGE ===
        if(evt.data.type==='jump-to-page'&&app.pdfViewer){
          app.pdfViewer.currentPageNumber=evt.data.page;
        }
        // === RESTORE ===
        if(evt.data.type!=='restore-annotations'||!app.pdfDocument)return;
        var data=evt.data.data,doc=app.pdfDocument,viewer=app.pdfViewer,restored=0;
        (async function(){
          for(var i=0;i<data.length;i++){
            try{
              var val=data[i].value,key=data[i].key;
              if(!val||typeof val!=='object')continue;
              var pi=val.pageIndex||0;
              var page=viewer._pages[pi];if(!page)continue;
              if(!page.annotationEditorLayer){try{page._renderAnnotationEditorLayer();}catch(e){continue;}}
              var layer=page.annotationEditorLayer.annotationEditorLayer;if(!layer)continue;
              // Restore TypedArrays in paths
              if(val.paths){try{
                if(val.paths.lines&&Array.isArray(val.paths.lines)){
                  val.paths.lines=val.paths.lines.map(function(line){
                    if(line&&typeof line==='object'&&!Array.isArray(line)){
                      if(line.points)line.points=Array.isArray(line.points)?new Float32Array(line.points):line.points;
                      if(line.bezier)line.bezier=Array.isArray(line.bezier)?new Float32Array(line.bezier):line.bezier;
                    }return line;
                  });
                }
                if(val.paths.points&&Array.isArray(val.paths.points)){
                  val.paths.points=val.paths.points.map(function(p){return Array.isArray(p)?new Float32Array(p):p;});
                }
              }catch(e){}}
              var editor=await layer.deserialize(val);
              if(editor){
                if(!editor.div){try{editor.render();}catch(e){}}
                // Fix freetext position and content
                if(val.annotationType===3&&editor.pageDimensions){
                  var pw=editor.pageDimensions[0],ph=editor.pageDimensions[1],r=val.rect;
                  if(r){editor.x=r[0]/pw;editor.y=(ph-r[3])/ph;editor.width=(r[2]-r[0])/pw;editor.height=(r[3]-r[1])/ph;}
                  if(typeof editor.fixAndSetPosition==='function')editor.fixAndSetPosition();
                  if(val.value&&editor.editorDiv){try{editor.editorDiv.textContent=val.value;}catch(e){}}
                  // Force text annotation to be visible
                  try{editor.show();}catch(e){}
                }
                layer.add(editor);
                doc.annotationStorage.setValue(key,editor);
                restored++;
              }
            }catch(e){console.error('[Bridge] restore error:',e.message||e,JSON.stringify(val).substring(0,200));}
          }
          console.log('[Bridge] Restored',restored,'/',data.length,'annotations');
          if(restored>0){viewer.update();try{app.pdfViewer.annotationEditorMode={mode:3};setTimeout(function(){app.pdfViewer.annotationEditorMode={mode:0};},100);}catch(e){}}
        })();
      });

      app.eventBus.on('pagechanging',function(evt){
        window.parent.postMessage({type:'pdf-page-change',page:evt.pageNumber},'*');
      });
    }
    setTimeout(init,500);
  })();
  </script>
</head>
'''

content = content[:idx1] + BRIDGE + content[idx2 + len(END):]

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

print('OK')
