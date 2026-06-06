"""Search and fetch PDF.js annotation save/restore solutions."""
import urllib.request
import urllib.parse
import json
import re
import ssl
import gzip
from io import BytesIO

ssl_ctx = ssl.create_default_context()
ssl_ctx.check_hostname = False
ssl_ctx.verify_mode = ssl.CERT_NONE

def fetch(url, headers=None):
    if headers is None:
        headers = {}
    headers.setdefault('User-Agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36')
    headers.setdefault('Accept', 'text/html,application/json')
    headers.setdefault('Accept-Encoding', 'gzip')
    req = urllib.request.Request(url, headers=headers)
    try:
        resp = urllib.request.urlopen(req, timeout=15, context=ssl_ctx)
        data = resp.read()
        if resp.headers.get('Content-Encoding') == 'gzip':
            data = gzip.decompress(data)
        return data.decode('utf-8', errors='replace')
    except Exception as e:
        return f"ERROR: {e}"

def google_search(query, num=10):
    """Use DuckDuckGo lite (no JS required)"""
    url = f"https://lite.duckduckgo.com/lite/?q={urllib.parse.quote(query)}"
    html = fetch(url)
    results = []
    for m in re.finditer(r'<a[^>]*class="result-link"[^>]*href="([^"]+)"[^>]*>([^<]+)</a>', html):
        results.append({'url': m.group(1), 'title': m.group(2).strip()})
    return results[:num]

# Search queries
queries = [
    "pdf.js annotation save restore JSON serialize deserialize example code",
    "pdfjs-dist annotation storage persist server backend solution",
    "PDF.js AnnotationStorage serializable import export workaround 2024 2025",
    "pdf.js highlight freetext ink annotation programmatically restore render",
    "mozilla pdf.js annotation editor save load external storage github",
    "PDF.js viewer annotation save button custom implementation postMessage",
    "pdfjs annotation persistence AnnotationEditorUIManager serialize deserialize",
]

for q in queries:
    print(f"\n{'='*80}")
    print(f"SEARCH: {q}")
    print(f"{'='*80}")
    results = google_search(q)
    for r in results[:5]:
        print(f"\n  {r['title']}")
        print(f"  {r['url']}")
        # Try to fetch the page
        if 'github.com' in r['url'] or 'stackoverflow.com' in r['url'] or 'discourse' in r['url']:
            html = fetch(r['url'])
            # Extract relevant code blocks or paragraphs
            # Look for code snippets containing annotation keywords
            code_blocks = re.findall(r'<code[^>]*>(.*?)</code>', html, re.DOTALL)
            relevant = [c for c in code_blocks if any(k in c.lower() for k in ['annotation', 'storage', 'serializ', 'deserializ', 'setvalue', 'getvalue'])]
            for c in relevant[:3]:
                clean = re.sub(r'<[^>]+>', '', c).strip()
                if len(clean) > 20:
                    print(f"    CODE: {clean[:300]}")
            # Also look for paragraphs
            paras = re.findall(r'<p[^>]*>(.*?)</p>', html, re.DOTALL)
            relevant_p = [p for p in paras if any(k in p.lower() for k in ['annotation', 'storage', 'serializ', 'restore', 'save'])]
            for p in relevant_p[:2]:
                clean = re.sub(r'<[^>]+>', '', p).strip()[:500]
                if len(clean) > 30:
                    print(f"    TEXT: {clean}")
