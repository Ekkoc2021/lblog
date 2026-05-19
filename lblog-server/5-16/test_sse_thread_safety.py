#!/usr/bin/env python
"""
SSE 线程安全测试脚本。

发送 N 次并发请求，抓取所有 SSE 响应，分析是否有数据交错损坏。
保存所有响应到 output/ 目录，损坏的单独标出。
"""

import sys
import os
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
import json
import time
import threading
import urllib.request
import urllib.error
from datetime import datetime

BASE_URL = "http://localhost:8099/iblogserver/api/v1"
OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "sse-test-output")

# ==================== 登录 ====================

def login():
    req = urllib.request.Request(
        f"{BASE_URL}/auth/login",
        data=json.dumps({"username": "admin", "password": "admin"}).encode(),
        headers={"Content-Type": "application/json"}
    )
    try:
        resp = urllib.request.urlopen(req)
        data = json.loads(resp.read())
        if data.get("code") == 0:
            return data["data"]["accessToken"]
    except: pass

    # fallback
    req = urllib.request.Request(
        f"{BASE_URL}/auth/login",
        data=json.dumps({"username": "ekko", "password": "admin123"}).encode(),
        headers={"Content-Type": "application/json"}
    )
    resp = urllib.request.urlopen(req)
    return json.loads(resp.read())["data"]["accessToken"]

# ==================== SSE 请求 ====================

def send_chat_request(token, request_id, results):
    """发送一个绘图请求，记录完整 SSE 响应"""
    body = json.dumps({
        "messages": [{"role": "user", "content": "draw a simple flowchart with start and end"}],
        "xml": "", "previousXml": "", "sessionId": f"test-{request_id}",
        "minimalStyle": False
    }).encode()

    req = urllib.request.Request(
        f"{BASE_URL}/draw/chat",
        data=body,
        headers={
            "Content-Type": "application/json",
            "X-Token": token,
            "Accept": "text/event-stream"
        }
    )

    try:
        resp = urllib.request.urlopen(req, timeout=60)
        raw = resp.read()
        text = raw.decode("utf-8", errors="replace")

        # 分析每一行
        lines = text.split("\n")
        corrupted_lines = []
        data_lines = 0
        for i, line in enumerate(lines):
            if line.startswith("data:"):
                data_lines += 1
                # 尝试解析 JSON，失败说明被心跳包打断
                json_str = line[5:].strip()
                if json_str:
                    try:
                        json.loads(json_str)
                    except json.JSONDecodeError:
                        corrupted_lines.append((i, line[:200]))

        results.append({
            "id": request_id,
            "ok": resp.status == 200,
            "size": len(text),
            "lines": len(lines),
            "data_lines": data_lines,
            "corrupted": len(corrupted_lines),
            "corrupted_lines": corrupted_lines,
            "preview": text[:500] if len(corrupted_lines) > 0 else ""
        })

    except Exception as e:
        results.append({
            "id": request_id,
            "ok": False,
            "error": str(e),
            "size": 0, "lines": 0, "data_lines": 0,
            "corrupted": 0, "corrupted_lines": []
        })

# ==================== 主流程 ====================

def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    total = int(sys.argv[1]) if len(sys.argv) > 1 else 50
    concurrent = int(sys.argv[2]) if len(sys.argv) > 2 else 5

    # 等待服务器就绪
    for i in range(30):
        try:
            urllib.request.urlopen(f"{BASE_URL}/draw/config", timeout=3)
            break
        except:
            time.sleep(2)
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 服务器就绪")

    print(f"[{datetime.now().strftime('%H:%M:%S')}] 登录...")
    token = login()
    print(f"[{datetime.now().strftime('%H:%M:%S')}] Token 获取成功")

    results = []
    batch_size = concurrent

    print(f"[{datetime.now().strftime('%H:%M:%S')}] 开始发送 {total} 次请求, 并发 {concurrent}")

    for batch_start in range(0, total, batch_size):
        batch_end = min(batch_start + batch_size, total)
        threads = []

        for i in range(batch_start, batch_end):
            t = threading.Thread(target=send_chat_request, args=(token, i, results))
            threads.append(t)
            t.start()

        for t in threads:
            t.join()

        done = min(batch_end, total)
        corrupted = sum(1 for r in results if r["corrupted"] > 0)
        print(f"[{datetime.now().strftime('%H:%M:%S')}] {done}/{total} - 损坏: {corrupted}")

    # ==================== 报告 ====================

    print("\n" + "=" * 60)
    print("            SSE 线程安全测试报告")
    print("=" * 60)
    print(f"总请求数:    {len(results)}")
    print(f"成功请求数:  {sum(1 for r in results if r['ok'])}")
    print(f"失败请求数:  {sum(1 for r in results if not r['ok'])}")

    corrupted_results = [r for r in results if r["corrupted"] > 0]

    if corrupted_results:
        print(f"\n⚠️  检测到 {len(corrupted_results)} 个损坏请求:")
        print(f"损坏率:      {len(corrupted_results)/len(results)*100:.2f}%")
        for r in corrupted_results:
            print(f"\n  [请求 #{r['id']}] 损坏 {r['corrupted']} 行")
            for line_no, content in r["corrupted_lines"][:3]:
                print(f"    行 {line_no}: {content}")
            # 保存损坏请求的完整响应
            path = os.path.join(OUTPUT_DIR, f"corrupted_{r['id']}.txt")
            with open(path, "w", encoding="utf-8") as f:
                f.write(r["preview"])
            print(f"    完整响应已保存: {path}")
    else:
        print(f"\n✅ 未检测到损坏，线程安全没有触发。")

    # 保存完整 JSON 报告
    report = {
        "timestamp": datetime.now().isoformat(),
        "total": len(results),
        "corrupted": len(corrupted_results),
        "results": [
            {"id": r["id"], "ok": r["ok"], "corrupted": r["corrupted"],
             "size": r["size"], "data_lines": r["data_lines"]}
            for r in results
        ]
    }
    with open(os.path.join(OUTPUT_DIR, "report.json"), "w") as f:
        json.dump(report, f, indent=2)

    print(f"\n完整报告已保存: {OUTPUT_DIR}/report.json")

if __name__ == "__main__":
    main()
