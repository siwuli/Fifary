var PASS = 0, FAILS = [];
function bad(n, d) { FAILS.push(n + ' :: ' + d); }
function ok(n, c, d) { if (c) PASS++; else bad(n, d || ''); }
function esc(s) { return String(s).replace(/\n/g, '\\n').replace(/\u00a0/g, '~'); }

function blocksOf() {
  var out = [], kids = document.body.children;
  for (var i = 0; i < kids.length; i++) {
    var tag = kids[i].nodeName, txt = kids[i].textContent.replace(/\u00a0/g, ' ').trim();
    // 第一行空着就是标题位（加载时会被换成 <h1>），这不算内容变了
    out.push((i === 0 && !txt ? 'HEAD' : tag) + '|' + txt);
  }
  while (out.length && /\|$/.test(out[out.length - 1])) out.pop();
  return out.join(' / ');
}

var seed = 987654321;
function rndInt(n) { seed = (seed * 1103515245 + 12345) % 2147483648; return seed % n; }

function textNodes() {
  var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null), out = [], n;
  while ((n = w.nextNode())) {
    if (n.nodeValue.trim() && !n.parentNode.closest('.player')) out.push(n);
  }
  return out;
}

// 把光标丢到随便一个有字的文本里
function placeCaret() {
  var ts = textNodes();
  if (ts.length) {
    var t = ts[rndInt(ts.length)], r = document.createRange();
    r.setStart(t, rndInt(t.length + 1));
    r.collapse(true);
    var s = window.getSelection();
    s.removeAllRanges(); s.addRange(r);
    saved = r.cloneRange(); lastBlk = blockOf(t);
    return true;
  }
  var blk = document.body.lastElementChild;
  if (!blk) return false;
  caretAt(blk, false);
  return true;
}

function key(el, k) {
  return el.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true, cancelable: true }));
}

var INS = ['x', 'Y', '中', '文', ' ', '123', 'ab', '，'];
var FMT = [
  function () { block('h1'); }, function () { block('h2'); }, function () { block('p'); },
  function () { block('blockquote'); }, function () { wrapTag('b'); }, function () { wrapTag('i'); },
  function () { wrapTag('s'); }, function () { list(false); }, function () { list(true); },
  function () { todo(); }, function () { insertHr(); }, function () { insertTable(); },
  function () { undo(); }, function () { redo(); }, function () { undo(); }
];

function op() {
  var pick = rndInt(10);
  if (pick < 4) {
    if (!placeCaret()) return;
    document.execCommand('insertText', false, INS[rndInt(INS.length)]);
  } else if (pick < 6) {
    if (!placeCaret()) return;
    // 真的浏览器行为：没被我们吃掉就自己拆行
    var host = document.activeElement && document.activeElement !== document.body ? document.activeElement : document.body;
    if (key(host, 'Enter')) document.execCommand('insertParagraph');
  } else if (pick < 7) {
    if (!placeCaret()) return;
    var host2 = document.body;
    if (key(host2, 'Backspace')) document.execCommand('delete');
  } else if (pick < 8) {
    placeCaret();
    var f = FMT[rndInt(FMT.length)]();
    syncSel();
    return f;
  } else {
    placeCaret();
  }
  snap();
}

var DOCS = ['', 'a', '标题\n正文', 'a\n\nb', '- 列表\n- 第二行', '1. 编号\n2. 编号',
  '- [ ] 待办\n- [x] 做完', '> 引用', 'a\n---\nb', '# 大标题\n## 副标题\n正文',
  '![](attachments/i.jpg)\n文字', '**粗** 普通 *斜*', 'a\n\n\nb', '中文段落一\n中文段落二'];

var N = 160;
for (var s = 0; s < N; s++) {
  var md0 = DOCS[rndInt(DOCS.length)];
  setMd(md0);
  var ops = 1 + rndInt(9), log = [];
  try {
    for (var i = 0; i < ops; i++) { op(); }
  } catch (e) {
    bad('M' + s + ' 抛异常', esc(md0) + ' 操作' + log.join(',') + ' :: ' + e.message);
    continue;
  }
  var b0 = blocksOf(), m1 = getMd();
  setMd(m1);
  var b1 = blocksOf(), m2 = getMd();
  ok('M' + s + ' 存一次定形', m1 === m2, 'doc=' + esc(md0) + ' m1=' + esc(m1) + ' m2=' + esc(m2));
  ok('M' + s + ' 内容不丢', b0 === b1 || b0.indexOf('BLOCKQUOTE| ') >= 0 &&
    b1.indexOf('BLOCKQUOTE| ') < 0, 'doc=' + esc(md0) + ' 存前=' + b0 + ' 存后=' + b1);
}

document.title = 'PASS=' + PASS + ' FAIL=' + FAILS.length;
var pre = document.createElement('pre');
pre.id = 'rep';
pre.textContent = FAILS.slice(0, 40).join('\n') || 'ALL PASS';
document.documentElement.appendChild(pre);
