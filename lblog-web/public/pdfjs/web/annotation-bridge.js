// Annotation bridge: hooks into PDF.js viewer and sends annotation data to parent via postMessage

// Signal parent IMMEDIATELY that we're alive
window.parent.postMessage({ type: 'bridge-loaded' }, '*');

console.log('[Bridge] Script loaded, waiting for PDFViewerApplication...');

let pollCount = 0;
function init() {
  var app = window.PDFViewerApplication;
  if (!app || !app.pdfViewer || !app.eventBus) {
    if (++pollCount < 50) setTimeout(init, 200);
    else {
      console.log('[Bridge] Failed after ' + pollCount + ' attempts');
      window.parent.postMessage({ type: 'bridge-error', msg: 'PDFViewerApplication not found after 50 attempts' }, '*');
    }
    return;
  }

  console.log('[Bridge] Hooked into PDFViewerApplication');
  window.parent.postMessage({ type: 'bridge-hooked' }, '*');

  var lastSaveSize = 0;

  setInterval(function() {
    try {
      var storage = app.pdfViewer.annotationStorage;
      if (!storage) return;

      var size = storage.size;
      console.log('[Bridge] annotationStorage size: ' + size + ' (was ' + lastSaveSize + ')');

      if (size > 0 && size !== lastSaveSize) {
        lastSaveSize = size;
        var serializable = storage.serializable;
        if (!serializable || serializable === 'empty') {
          console.log('[Bridge] serializable empty');
          return;
        }

        var data = [];
        if (typeof serializable.forEach === 'function') {
          serializable.forEach(function(val, key) {
            data.push(Object.assign({ key: key }, val));
          });
        }

        console.log('[Bridge] Sending ' + data.length + ' annotations to parent');
        window.parent.postMessage({
          type: 'pdf-annotations',
          data: JSON.stringify(data),
        }, '*');
      }
    } catch(e) {
      console.error('[Bridge] Error:', e);
      window.parent.postMessage({ type: 'bridge-error', msg: e.message }, '*');
    }
  }, 3000);

  // Page change
  app.eventBus.on('pagechanging', function(evt) {
    window.parent.postMessage({
      type: 'pdf-page-change',
      page: evt.pageNumber,
    }, '*');
  });
}

setTimeout(init, 500);
