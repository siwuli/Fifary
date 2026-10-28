"""编辑器自检：把 editor.html 和下面三个用例脚本拼成临时页面，用无头 Chrome 跑一遍。

    python tools/editor-spec/run.py

spec.js    交互用例（格式按钮、回车、退格、存盘往返）
fuzz.js    markdown 往返（打开→存盘→再打开，内容不能变）
monkey.js  随机操作（随机打字/回车/退格/点格式，存一次就得定形、内容不丢）

Chrome 路径用环境变量 CHROME 覆盖，默认走 Windows 常见安装位置。
"""

import io
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
EDITOR = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'editor.html')
CHROME = os.environ.get('CHROME', r'C:\Program Files\Google\Chrome\Application\chrome.exe')
SUITES = ('spec.js', 'fuzz.js', 'monkey.js')


def run(suite):
    html = io.open(EDITOR, encoding='utf-8').read()
    js = io.open(os.path.join(HERE, suite), encoding='utf-8').read()
    # 页面得自己带 charset，报错就走 title，省得无头 Chrome 只吐一段空白
    html = html.replace(
        '<head>',
        '<head>\n<meta charset="utf-8">\n'
        '<script>window.onerror=function(m,s,l){document.title="ERR "+m+" @line "+l};</script>',
        1,
    )
    page = os.path.join(tempfile.gettempdir(), 'editor-spec-' + suite + '.html')
    io.open(page, 'w', encoding='utf-8').write(html + '\n<script>\n' + js + '\n</script>\n')
    res = subprocess.run(
        [CHROME, '--headless=new', '--disable-gpu', '--no-sandbox', '--dump-dom',
         'file:///' + page.replace('\\', '/')],
        capture_output=True, timeout=600,
    )
    dom = res.stdout.decode('utf-8', 'replace')
    found = re.search(r'<title>(.*?)</title>', dom, re.S)
    title = found.group(1) if found else '(没有 title)'
    print('%-10s %s' % (suite, title))
    passed = 'FAIL=0' in title
    if not passed:
        rep = re.search(r'<pre id="rep">(.*?)</pre>', dom, re.S)
        if rep:
            print(rep.group(1).replace('&lt;', '<').replace('&gt;', '>')
                  .replace('&quot;', '"').replace('&amp;', '&'))
    return passed


results = [run(s) for s in SUITES]
print('ALL PASS' if all(results) else 'FAILED')
sys.exit(0 if all(results) else 1)
