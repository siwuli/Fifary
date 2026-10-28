var PASS = 0, FAILS = [];
function bad(n, d) { FAILS.push(n + ' :: ' + d); }
function same(n, got, want) {
  if (got === want) { PASS++; return; }
  bad(n, 'got=<' + got + '> want=<' + want + '>');
}
function reset(html) {
  document.body.innerHTML = html;
  saved = null; lastBlk = null; hist = []; at = -1;
  var s = window.getSelection(); if (s) s.removeAllRanges();
  snap(); // 跟真实打开笔记一样，先有一份初始历史
}
function tn(el) {
  var w = document.createTreeWalker(el, NodeFilter.SHOW_TEXT), n = w.nextNode();
  return n || el;
}
function c2(el, off) {
  var t = tn(el), r = document.createRange();
  if (t.nodeType === 3) r.setStart(t, off == null ? t.length : Math.min(off, t.length));
  else r.selectNodeContents(el);
  r.collapse(true);
  var s = window.getSelection(); s.removeAllRanges(); s.addRange(r);
  saved = r.cloneRange(); lastBlk = blockOf(el);
}
function pick(a, b) {
  var r = document.createRange();
  r.setStart(a, 0);
  r.setEnd(b, b.childNodes.length);
  var s = window.getSelection(); s.removeAllRanges(); s.addRange(r);
  saved = r.cloneRange(); lastBlk = blockOf(b);
}
function tx(a, from, b, to) {
  var r = document.createRange();
  r.setStart(tn(a), from); r.setEnd(tn(b), to);
  var s = window.getSelection(); s.removeAllRanges(); s.addRange(r);
  saved = r.cloneRange(); lastBlk = blockOf(a);
}
function T(n, html, act, want) {
  reset(html || '');
  try { act(); snap(); } catch (e) { return bad(n, 'threw ' + e.message); }
  if (want === null) return;
  same(n, getMd(), want);
  var m1 = getMd();
  setMd(m1);
  if (getMd() !== m1) bad(n + ' 存盘往返', 'diff=<' + getMd() + '>');
  else PASS++;
}
function B(n) { return document.body.children[n]; }
function key(el, k) { el.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true, cancelable: true })); }
var TODO = '<ul class="todo"><li><input type="checkbox"><span class="t">aa</span></li></ul>';

// ===== A 文字样式 =====
T('A1 p->h1', '<p>aa</p>', function () { c2(B(0)); block('h1'); }, '# aa');
T('A2 h1->h1', '<h1>aa</h1>', function () { c2(B(0)); block('h1'); }, '# aa');
T('A3 h2->p', '<h2>aa</h2>', function () { c2(B(0)); block('p'); }, 'aa');
T('A4 quote', '<blockquote>aa</blockquote>', function () { c2(B(0)); block('blockquote'); }, '> aa');
T('A5 quote->p', '<blockquote>aa</blockquote>', function () { c2(B(0)); block('p'); }, 'aa');
T('A6 multi h1', '<p>aa</p><p>bb</p>', function () { pick(B(0), B(1)); block('h1'); }, '# aa\n# bb');
T('A6b multi back', '<p>aa</p><p>bb</p>', function () { pick(B(0), B(1)); block('h1'); block('p'); }, 'aa\nbb');
T('A7 li->h1：还是列表项，只改字号', '<ul><li>aa</li><li>bb</li></ul>', function () {
  c2(document.querySelector('li')); block('h1');
  if (document.querySelectorAll('ul > li').length !== 2) bad('A7 列表被拆了', document.body.innerHTML); else PASS++;
  if (document.querySelector('li').style.fontSize !== '24px') bad('A7 标题字号没进去', document.body.innerHTML); else PASS++;
  var m1 = getMd(); setMd(m1);
  if (document.querySelectorAll('ul > li').length !== 2 || document.querySelector('li').style.fontSize !== '24px')
    bad('A7 存盘往返丢了', m1 + ' -> ' + document.body.innerHTML);
  else PASS++;
}, null);
T('A8 li->h2', '<ul><li>aa</li><li>bb</li></ul>', function () {
  c2(document.querySelectorAll('li')[1]); block('h2');
  if (document.querySelectorAll('ul > li').length !== 2) bad('A8 列表被拆了', document.body.innerHTML); else PASS++;
  if (document.querySelectorAll('li')[1].style.fontSize !== '20px') bad('A8 副标题字号没进去', document.body.innerHTML); else PASS++;
  var m1 = getMd(); setMd(m1);
  if (document.querySelectorAll('li')[1].style.fontSize !== '20px') bad('A8 存盘往返丢了', m1); else PASS++;
}, null);
T('A9 todo->h3：框留着，只改字号', '<ul class="todo"><li><input type="checkbox"><span class="t">aa</span></li><li><input type="checkbox"><span class="t">bb</span></li></ul>',
  function () {
    c2(document.querySelector('li .t')); block('h3');
    if (!document.querySelector('ul.todo > li > input')) bad('A9 勾选框被顶掉了', document.body.innerHTML); else PASS++;
    if (document.querySelector('li').style.fontSize !== '16px') bad('A9 小标题字号没进去', document.body.innerHTML); else PASS++;
    var m1 = getMd(); setMd(m1);
    if (!document.querySelector('ul.todo > li > input') || document.querySelector('li').style.fontSize !== '16px')
      bad('A9 存盘往返丢了', m1 + ' -> ' + document.body.innerHTML);
    else PASS++;
  }, null);
T('A10 multi h3', '<h1>aa</h1><p>bb</p>', function () { pick(B(0), B(1)); block('h3'); }, '### aa\n### bb');

// ===== B 加粗/斜体/删除线 =====
T('B1 bold', '<p>aa</p>', function () { c2(B(0)); wrapTag('b'); }, '**aa**');
T('B2 bold off', '<p>aa</p>', function () { c2(B(0)); wrapTag('b'); wrapTag('b'); }, 'aa');
T('B3 bold part', '<p>abcd</p>', function () { tx(B(0), 2, B(0), 4); wrapTag('b'); }, 'ab**cd**');
T('B4 bold off inside', '<p><b>ab</b>cd</p>', function () { c2(document.querySelector('b')); wrapTag('b'); }, 'abcd');
T('B5 multi bold', '<p>aa</p><p>bb</p>', function () { pick(B(0), B(1)); wrapTag('b'); }, '**aa**\n**bb**');
T('B5b multi bold off', '<p>aa</p><p>bb</p>', function () { pick(B(0), B(1)); wrapTag('b'); wrapTag('b'); }, 'aa\nbb');
T('B6 mixed multi', '<p><b>aa</b></p><p>bb</p>', function () { pick(B(0), B(1)); wrapTag('b'); }, '**aa**\n**bb**');
T('B6b mixed then off', '<p><b>aa</b></p><p>bb</p>', function () { pick(B(0), B(1)); wrapTag('b'); wrapTag('b'); }, 'aa\nbb');
T('B7 italic', '<p>aa</p>', function () { c2(B(0)); wrapTag('i'); }, '*aa*');
T('B7b strike', '<p>aa</p>', function () { c2(B(0)); wrapTag('s'); }, '~~aa~~');
T('B8 bold in todo', TODO, function () { c2(document.querySelector('.t')); wrapTag('b'); }, '- [ ] **aa**');
T('B9 bold in h1', '<h1>aa</h1>', function () { c2(B(0)); wrapTag('b'); }, '# **aa**');
T('B10 bold list item', '<ul><li>aa</li></ul>', function () { c2(document.querySelector('li')); wrapTag('b'); }, '- **aa**');
T('B11 bold after reload', '<p>aa</p>', function () {
  c2(B(0)); wrapTag('b'); snap();
  setMd(getMd());
  c2(document.body.querySelector('p')); wrapTag('b');
}, 'aa');

// ===== D 列表 =====
T('D1 ul', '<p>aa</p>', function () { c2(B(0)); list(false); }, '- aa');
T('D2 ul off', '<p>aa</p>', function () { c2(B(0)); list(false); list(false); }, 'aa');
T('D3 ol', '<p>aa</p>', function () { c2(B(0)); list(true); }, '1. aa');
T('D4 ul->ol', '<ul><li>aa</li></ul>', function () { c2(document.querySelector('li')); list(true); }, '1. aa');
T('D5 h1->ul', '<h1>aa</h1>', function () { c2(B(0)); list(false); }, '<ul><li style="font-size: 24px; font-weight: 600;">aa</li></ul>');
T('D6 multi ul', '<p>aa</p><p>bb</p>', function () { pick(B(0), B(1)); list(false); }, '- aa\n- bb');
T('D7 todo->ol', TODO, function () { c2(document.querySelector('.t')); list(true); }, '1. aa');
T('D8 ul middle off', '<ul><li>aa</li><li>bb</li><li>cc</li></ul>', function () {
  c2(document.querySelectorAll('li')[1]); list(false);
}, '- aa\nbb\n- cc');

// ===== E 待办 =====
T('E1 p->todo', '<p>aa</p>', function () { c2(B(0)); todo(); }, '- [ ] aa');
T('E2 todo off', '<p>aa</p>', function () { c2(B(0)); todo(); todo(); }, 'aa');
T('E3 ul->todo', '<ul><li>aa</li><li>bb</li></ul>', function () { c2(document.querySelector('li')); todo(); }, '- [ ] aa\n- [ ] bb');
T('E5 checked', '<ul class="todo"><li><input type="checkbox" checked=""><span class="t">aa</span></li></ul>', function () {
  document.querySelector('input').checked = true;
}, '- [x] aa');
T('E6 multi todo', '<p>aa</p><p>bb</p>', function () { pick(B(0), B(1)); todo(); }, '- [ ] aa\n- [ ] bb');
T('E7 todo off middle', '<ul class="todo"><li><input type="checkbox"><span class="t">aa</span></li><li><input type="checkbox"><span class="t">bb</span></li></ul>',
  function () { c2(document.querySelectorAll('.t')[0]); todo(); }, 'aa\n- [ ] bb');
T('E8 checked stays', '<ul class="todo"><li><input type="checkbox" checked=""><span class="t">aa</span></li></ul>', function () {
  document.querySelector('input').checked = true;
  c2(document.querySelector('.t')); wrapTag('b');
}, '- [x] **aa**');

// ===== P 空笔记要留一行落光标，但不能凭空多出空行 =====
T('P1 空笔记留能落光标的行', '', function () {
  setMd('');
  var kids = document.body.children;
  if (!kids.length || !/^(P|H1)$/.test(kids[0].nodeName) || !kids[0].querySelector('br')) bad('P1 空笔记没留行', document.body.innerHTML);
  else PASS++;
}, '');
T('P2 有字不补空段落', '<p>aa</p>', function () {
  keepEmpty();
  if (document.body.children.length !== 1) bad('P2 多出空段落', document.body.innerHTML);
  else PASS++;
}, 'aa');
T('P3 待办不补空段落', TODO, function () {
  keepEmpty();
  if (document.body.children.length !== 1) bad('P3 多出空段落', document.body.innerHTML);
  else PASS++;
}, '- [ ] aa');
T('P4 裸文本不补空段落', 'hi', function () {
  document.body.dispatchEvent(new Event('input', { bubbles: true }));
  if (document.body.children.length) bad('P4 裸文本后面补了空段落', document.body.innerHTML);
  else PASS++;
}, 'hi');
T('P5 空笔记打字留在那一行', '', function () {
  setMd(''); caretAt(document.body.firstElementChild, false); document.execCommand('insertText', false, 'x');
  var kids = document.body.children;
  if (kids.length !== 2 || !kids[0].textContent.trim() || kids[0].className) bad('P5 打字串到别处了', document.body.innerHTML);
  else PASS++;
}, '# x');
// ===== R 分割线/表格不能进列表，末尾要留一行能删它 =====
T('R1 两段之间插分割线', '<p>aa</p><p>bb</p>', function () {
  c2(B(1), 0); insertHr();
}, 'aa\nbb\n---');
T('R2 待办后面插分割线不进列表', '<ul class="todo"><li><input type="checkbox"><span class="t">aa</span></li></ul>', function () {
  c2(document.querySelector('li .t')); insertHr();
  if (document.querySelector('ul hr')) bad('R2 分割线塞进 ul 了', document.body.innerHTML);
  else PASS++;
}, '- [ ] aa\n---');
T('R3 末尾插分割线自动补一行', '<p>aa</p>', function () {
  c2(B(0)); insertHr();
  var last = document.body.lastElementChild;
  if (!last || last.nodeName !== 'P' || !last.querySelector('br')) bad('R3 分割线后面没补行', document.body.innerHTML);
  else PASS++;
}, 'aa\n---');
T('R4 读入末尾分割线也补一行', '', function () {
  setMd('aa\n---');
  var last = document.body.lastElementChild;
  if (!last || last.nodeName !== 'P') bad('R4 读入后末尾没补行', document.body.innerHTML);
  else PASS++;
}, 'aa\n---');
T('R5 分割线下面退格能删掉', '<p>aa</p><hr><p>bb</p>', function () {
  c2(B(2), 0);
  var ev = new KeyboardEvent('keydown', { key: 'Backspace', bubbles: true, cancelable: true });
  document.body.dispatchEvent(ev);
  if (!ev.defaultPrevented) document.execCommand('delete');
  if (document.querySelector('hr')) bad('R5 分割线没删掉', document.body.innerHTML);
  else PASS++;
}, 'aa\nbb');
T('R6 列表里插图不进列表', '<ul><li>aa</li><li>bb</li></ul>', function () {
  c2(document.querySelectorAll('li')[1]); insertImage('a.png');
  if (document.querySelector('ul img')) bad('R6 图片塞进 ul 了', document.body.innerHTML);
  else PASS++;
}, '- aa\n- bb\n![](a.png)');
T('R7 待办里插表格不进列表', '<ul class="todo"><li><input type="checkbox"><span class="t">aa</span></li></ul>', function () {
  c2(document.querySelector('li .t')); insertTable();
  if (document.querySelector('ul table')) bad('R7 表格塞进 ul 了', document.body.innerHTML);
  else PASS++;
}, null);

// ===== S 选区只动选中的字，再点能取消 =====
T('S1 选两字加粗只改这两字', '<p>abcd</p>', function () { tx(B(0), 1, B(0), 3); wrapTag('b'); }, 'a**bc**d');
T('S2 再点一次只取消这两字', '<p>abcd</p>', function () { tx(B(0), 1, B(0), 3); wrapTag('b'); wrapTag('b'); }, 'abcd');
T('S3 加粗后点斜体再点斜体', '<p>abcd</p>', function () { tx(B(0), 1, B(0), 3); wrapTag('b'); wrapTag('i'); wrapTag('i'); }, 'a**bc**d');
T('S4 选中整行加粗', '<p>ab cd</p>', function () { tx(B(0), 0, B(0), 5); wrapTag('b'); }, '**ab cd**');
T('S5 跨块选区加粗', '<p>aa</p><p>bb</p>', function () { pick(B(0), B(1)); wrapTag('b'); }, '**aa**\n**bb**');
// ===== K 行首退格拆掉列表/待办标记 =====
T('K1 列表项行首退格拆回段落', '<ul><li>aa</li><li>bb</li></ul>', function () {
  c2(document.querySelectorAll('li')[1], 0); key(document.body, 'Backspace');
}, '- aa\nbb');
T('K2 待办行首退格拆回段落', TODO, function () {
  c2(document.querySelector('.t'), 0); key(document.body, 'Backspace');
}, 'aa');
T('K3 行中退格不拆标记', '<ul><li>aa</li></ul>', function () {
  c2(document.querySelector('li'), 1); key(document.body, 'Backspace');
}, null);
// ===== L 新笔记第一行是裸文本 =====
T('L1 裸文本加标题', 'hi', function () { block('h1'); }, '# hi');
T('L2 裸文本加粗', 'hi', function () { wrapTag('b'); }, '**hi**');
// ===== I 撤销/重做 =====
T('I1 撤销一次', '<p>aa</p>', function () { c2(B(0)); block('h1'); snap(); undo(); }, 'aa');
T('I2 重做', '<p>aa</p>', function () { c2(B(0)); block('h1'); snap(); undo(); redo(); }, '# aa');
// ===== J 存取保真 =====
T('J1 行内样式整段存 HTML', '<p style="text-align:center">aa</p>', function () { keepEmpty(); }, '<p style="text-align:center">aa</p>');
T('J2 勾选状态存盘', '<ul class="todo"><li data-done="1"><input type="checkbox" checked><span class="t">aa</span></li></ul>', function () { syncBoxes(); }, '- [x] aa');
T('J3 表格存盘', '<table><tbody><tr><td>a</td></tr></tbody></table>', function () { keepEmpty(); }, null);
// ===== H 插入 =====
T('H1 插图片', '<p>aa</p>', function () { c2(B(0)); insertImage('a.png'); }, 'aa\n![](a.png)');
T('H2 插文字', '<p>aa</p>', function () { c2(B(0)); insertText('zz'); }, 'aazz');
// ===== N 空行补 br =====
T('N1 删光字补 br', '<p>abc</p>', function () {
  B(0).textContent = ''; document.body.dispatchEvent(new Event('input', { bubbles: true }));
  if (!B(0).querySelector('br')) bad('N1 没补 br', document.body.innerHTML); else PASS++;
}, '');

// ===== T 大字标题和编号/待办可以同时有 =====
T('T1 编号保留大字', '<h1>aa</h1>', function () {
  c2(B(0)); list(true);
  var li = document.querySelector('li');
  if (!li || !/24px/.test(li.getAttribute('style') || '')) bad('T1 编号把大字吃掉了', document.body.innerHTML);
  else PASS++;
  if (getMd().indexOf('font-size: 24px') < 0) bad('T1 存盘丢了字号', getMd());
  else PASS++;
}, null);
T('T2 待办保留大字', '<h2>bb</h2>', function () {
  c2(B(0)); todo();
  var li = document.querySelector('li');
  if (!li || !/20px/.test(li.getAttribute('style') || '')) bad('T2 待办把大字吃掉了', document.body.innerHTML);
  else PASS++;
  if (!li.querySelector('input') || !li.querySelector('.t')) bad('T2 待办的框/格子没了', document.body.innerHTML);
  else PASS++;
}, null);
T('T3 列表项点正文回小字', '<ul><li style="font-size:24px">aa</li></ul>', function () {
  c2(document.querySelector('li')); block('p');
  if (document.body.firstElementChild.getAttribute('style')) bad('T3 点正文还是大字', document.body.innerHTML);
  else PASS++;
  if (document.querySelector('li').style.fontSize) bad('T3 列表项字号没清掉', document.body.innerHTML);
  else PASS++;
}, '- aa');
T('T4 列表项点标题', '<ul><li style="font-size:24px">aa</li></ul>', function () {
  c2(document.querySelector('li')); block('h1');
  if (!document.querySelector('ul > li')) bad('T4 列表项没了', document.body.innerHTML); else PASS++;
}, null);

T('T5 存好的大字列表能接着打字', '<ol><li style="font-size: 24px; font-weight: 600;">head</li></ol>', function () {
  c2(document.querySelector('li'));
  document.execCommand('insertText', false, 'X');
}, '<ol><li style="font-size: 24px; font-weight: 600;">headX</li></ol>');
T('T6 大字列表项再加待办', '<ol><li style="font-size: 24px; font-weight: 600;">head</li></ol>', function () {
  c2(document.querySelector('li')); todo();
  var li = document.querySelector('ul.todo > li');
  if (!li || !/24px/.test(li.getAttribute('style') || '')) bad('T6 加待办丢了大字', document.body.innerHTML);
  else PASS++;
  if (!li.querySelector('input') || !li.querySelector('.t')) bad('T6 框/格子没了', document.body.innerHTML);
  else PASS++;
}, null);

// ===== U 空笔记有标题位，但标题只是块格式 =====
T('U1 空笔记留标题位 + 正文行', '', function () {
  setMd('');
  var kids = document.body.children;
  if (!kids[0] || kids[0].nodeName !== 'H1') bad('U1 空笔记没有标题行', document.body.innerHTML);
  else PASS++;
  if (!kids[0].classList.contains('ph')) bad('U1 标题位没挂提示', document.body.innerHTML);
  else PASS++;
  if (!kids[1] || kids[1].nodeName !== 'P' || !kids[1].querySelector('br')) bad('U1 标题下面没有正文行', document.body.innerHTML);
  else PASS++;
}, '');
T('U2 有内容就不挂提示', '', function () {
  setMd('aa');
  var first = document.body.firstElementChild;
  if (first.nodeName !== 'P' || first.className) bad('U2 有内容还挂提示/换了标签', document.body.innerHTML);
  else PASS++;
}, 'aa');
T('U3 标题位打字就是标题格式', '', function () {
  setMd('');
  c2(B(0)); document.execCommand('insertText', false, 'tt');
}, '# tt');

T('U7 正文有字时标题位仍留提示', '', function () {
  setMd('');
  c2(B(1)); document.execCommand('insertText', false, 'cc');
  var head = document.body.firstElementChild;
  if (head.nodeName !== 'H1' || !head.classList.contains('ph')) bad('U7 正文一有字标题提示就没了', document.body.innerHTML);
  else PASS++;
}, '\ncc');

T('U8 正文行首退格能删掉空的标题位', '', function () {
  setMd('');
  c2(B(1));
  key(document.body, 'Backspace');
  var first = document.body.firstElementChild;
  if (!first || first.nodeName !== 'P' || document.body.children.length !== 1) bad('U8 标题位没删掉', document.body.innerHTML);
  else PASS++;
  if (document.querySelector('.ph')) bad('U8 删完还挂着提示', document.body.innerHTML);
  else PASS++;
}, '');

T('U9 正文删空也不把标题位补回来', '', function () {
  setMd('');
  document.body.innerHTML = '';   // 真机上在最后一行退格会把这一行也删掉
  keepEmpty();
  if (document.querySelector('h1')) bad('U9 标题位又回来了', document.body.innerHTML);
  else PASS++;
}, '');

T('U10 第一行行首退格不交给浏览器', '<p><br></p>', function () {
  c2(B(0));
  var ev = new KeyboardEvent('keydown', { key: 'Backspace', bubbles: true, cancelable: true });
  document.body.dispatchEvent(ev);
  if (!ev.defaultPrevented) bad('U10 行首退格还交给浏览器，光标会弹一下', document.body.innerHTML);
  else PASS++;
}, '');

// ===== V 回车敲出来的空行要能存住 =====
T('V1 中间空行存成空行', '<p>a</p><p><br></p><p>b</p>', function () { keepEmpty(); }, 'a\n\nb');
T('V2 两个空行', '<p>a</p><p><br></p><p><br></p><p>b</p>', function () { keepEmpty(); }, 'a\n\n\nb');
T('V3 读回来空行还在', '', function () { setMd('a\n\nb'); }, 'a\n\nb');
T('V4 结尾空行不留下', '<p>a</p><p><br></p>', function () { keepEmpty(); }, 'a');

T('U5 标题行末尾回车下一行是正文', '', function () {
  setMd('');
  c2(B(0)); document.execCommand('insertText', false, 'tt');
  key(document.body, 'Enter');
  var second = document.body.children[1];
  if (!second || second.nodeName !== 'P') bad('U5 回车没落成正文段落', document.body.innerHTML);
  else PASS++;
  document.execCommand('insertText', false, 'bb');
  snap();
}, '# tt\nbb');
T('U6 标题能改成正文', '', function () {
  setMd('# tt');
  c2(B(0)); block('p');
  if (document.body.firstElementChild.nodeName !== 'P') bad('U6 标题没改成正文', document.body.innerHTML);
  else PASS++;
}, 'tt');

// ===== W 列表项/待办和标题字号能同时存在 =====
function liOf(html) {
  var d = document.createElement('div');
  d.innerHTML = html;
  return d.firstElementChild.firstElementChild;
}
T('W1 待办改成标题：框和列表项都还在', TODO, function () {
  var li = document.querySelector('ul.todo > li');
  c2(li); block('h1');
  if (!document.querySelector('ul.todo > li > input')) bad('W1 勾选框被顶掉了', document.body.innerHTML); else PASS++;
  if (document.querySelectorAll('ul.todo > li').length !== 1) bad('W1 列表项被顶掉了', document.body.innerHTML); else PASS++;
  if (li.style.fontSize !== '24px' || li.style.fontWeight !== '600') bad('W1 标题字号没写进去', li.getAttribute('style')); else PASS++;
}, null);

T('W2 待办改标题：存盘再打开，框和字号都还在', '', function () {
  setMd('- [ ] aa');
  c2(document.querySelector('ul.todo > li')); block('h1');
  var m1 = getMd();
  setMd(m1);
  var li = document.querySelector('ul.todo > li');
  if (!li) return bad('W2 打开后不是待办了', document.body.innerHTML);
  PASS++;
  if (!li.querySelector('input')) bad('W2 框没了', document.body.innerHTML); else PASS++;
  if (li.style.fontSize !== '24px') bad('W2 字号丢了', document.body.innerHTML); else PASS++;
}, null);

T('W3 分条改成副标题：还是列表项', '<ul><li>aa</li><li>bb</li></ul>', function () {
  c2(document.querySelector('li'));
  block('h2');
  if (document.querySelectorAll('ul > li').length !== 2) bad('W3 列表被拆了', document.body.innerHTML); else PASS++;
  if (document.querySelector('li').style.fontSize !== '20px') bad('W3 副标题字号没进去', document.body.innerHTML); else PASS++;
}, null);

T('W4 待办点正文：还是待办，字号清掉', TODO, function () {
  var li = document.querySelector('ul.todo > li');
  li.style.fontSize = '24px';
  c2(li); block('p');
  if (!document.querySelector('ul.todo > li > input')) bad('W4 待办没了', document.body.innerHTML); else PASS++;
  if (li.style.fontSize) bad('W4 字号没清掉', li.getAttribute('style')); else PASS++;
}, null);

T('W5 正文改标题还是用标签（存成 # ）', '<p>aa</p>', function () {
  c2(B(0)); block('h1');
}, '# aa');

T('W6 标题改分条：字号跟着带进列表', '<h1>aa</h1>', function () {
  c2(B(0)); list(false);
  if (document.querySelector('li').style.fontSize !== '24px') bad('W6 标题字号没带进列表', document.body.innerHTML); else PASS++;
}, null);

// ===== X 勾选框跟着字号缩放 =====
T('X1 勾选框字号跟行字号', '', function () {
  setMd('- [ ] aa');
  var box = document.querySelector('ul.todo > li > input');
  var li = box.parentNode;
  var small = getComputedStyle(box).height;
  li.style.fontSize = '24px';
  var big = getComputedStyle(box).height;
  var got = parseFloat(small), want = parseFloat(big);
  if (!(want > got * 1.3)) bad('X1 字号放大勾选框没跟着放大', 'small=' + small + ' big=' + big); else PASS++;
  // 位置也得跟：框要落在第一行的中间（li 行高的中线减去半个框高）
  var top = parseFloat(getComputedStyle(box).top);
  if (Math.abs(top - (1.65 - 1) / 2 * 24) > 1.5) bad('X1 勾选框竖直位置不对', 'top=' + top); else PASS++;
}, null);

// ===== Y 空着的一行列表/待办再回车就退出列表 =====
T('Y1 空待办回车回正文', '<h1>aa</h1><ul class="todo"><li><input type="checkbox"><span class="t"><br></span></li></ul>', function () {
  c2(document.querySelector('li .t')); key(document.body, 'Enter');
  if (document.querySelector('ul,input')) bad('Y1 空待办回车还留在列表里', document.body.innerHTML); else PASS++;
}, '# aa');
T('Y2 空分条回车回正文', '<ul><li>aa</li><li><br></li></ul>', function () {
  c2(document.querySelectorAll('li')[1]); key(document.body, 'Enter');
  if (document.querySelectorAll('li').length !== 1) bad('Y2 空分条回车多出一项', document.body.innerHTML); else PASS++;
}, '- aa');
T('Y3 中间空项回车把列表拆两半', '<ul><li>aa</li><li><br></li><li>bb</li></ul>', function () {
  c2(document.querySelectorAll('li')[1]); key(document.body, 'Enter');
  var k = document.body.children;
  if (k.length !== 3 || k[0].nodeName !== 'UL' || k[1].nodeName !== 'P' || k[2].nodeName !== 'UL')
    bad('Y3 拆出来的顺序不对', document.body.innerHTML);
  else if (k[0].querySelectorAll('li').length !== 1 || k[2].querySelectorAll('li').length !== 1)
    bad('Y3 拆出来的项数不对', document.body.innerHTML);
  else PASS++;
}, null);

document.title = 'PASS=' + PASS + ' FAIL=' + FAILS.length;
var pre = document.createElement('pre');
pre.id = 'rep';
pre.textContent = FAILS.join('\n') || 'ALL PASS';
document.documentElement.appendChild(pre);
