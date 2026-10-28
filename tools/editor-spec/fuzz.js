var PASS = 0, FAILS = [];
function bad(n, d) { FAILS.push(n + ' :: ' + d); }
function ok(n, c, d) { if (c) PASS++; else bad(n, d || ''); }
function esc(s) { return String(s).replace(/\n/g, '\\n').replace(/\u00a0/g, '~'); }

function blocksOf() {
  var out = [], kids = document.body.children;
  for (var i = 0; i < kids.length; i++) {
    out.push(kids[i].nodeName + '|' + kids[i].textContent.replace(/\u00a0/g, ' ').trim());
  }
  while (out.length && /\|$/.test(out[out.length - 1])) out.pop();
  return out.join(' / ');
}

// 一次存取往返：打开笔记 → 点完成，什么都不改
function check(name, md) {
  setMd(md); var b0 = blocksOf();
  var m1 = getMd();
  setMd(m1); var b1 = blocksOf();
  var m2 = getMd();
  ok(name + ' 存一次就定形', m1 === m2, 'md=' + esc(md) + ' m1=' + esc(m1) + ' m2=' + esc(m2));
  ok(name + ' 排版不跑', b0 === b1, 'md=' + esc(md) + ' 打开=' + b0 + ' 再打开=' + b1);
}

var CORPUS = [
  '', 'a', 'a\nb', 'a\n\nb', 'a\nb\n', 'a\n\n\nb', '\na', 'a\n\n', '\n\na',
  '# t\na', '标题\n正文', '### 小标题\n内容', '# a # b', '## 带 **粗体** 的标题',
  '正文 **粗** *斜* ~~删~~ `码`', 'a < b', 'a & b', 'x <= y && z > 1',
  '<p>原生 html</p>', '<div style="text-align:center">居中</div>',
  '<h2>raw h2</h2>', '<ul><li>x</li></ul>',
  '- a\n- b', '- a\n  - b\n    - c', '1. a\n2. b', '1. a\n- b', '* a\n+ b',
  '- [ ] a\n- [x] b', '- [ ] a\n- [ ]', '> 引用\n> 第二行', '> 空引用\n>\n> 尾',
  '---', 'a\n---\nb', '```\ncode\n```', '| a | b |\n|---|---|',
  '![](attachments/img_1.jpg)', '![图](attachments/img_1.jpg)', '[链接](https://x.com)',
  '[录音 3 秒](attachments/rec_1.m4a)', '字\n\n---\n\n字', 'a\u00a0b', '  a  ', 'a\r\nb',
  '😀 表情\n第二行', '中英混排 mixed content 123', '2026. 数字开头', '- 减号开头',
  '*星号开头*', '**整行粗体**', '`in code`', 'a*b*c', 'a_b_c', 'tab\there', 'a  b',
  '1)', '-', '#', '- ', '>', '中文，标点！？；：', 'a\n\n\n\nb', 'link [a](b) c',
  '**a** **b**', '**未闭合', '~~未闭合', '``', '\\*转义\\*', '1\\. 不是列表',
  '>a', '> > 套引用', '- a\n\n- b', '# 一\n## 二\n### 三\n正文',
  '很长的中文段落。'.repeat(20), 'a\n\nb\n\nc\n\nd',
];

var FRAG = ['普通文字', '# 标题', '## 副标题', '### 小标题', '- 列表', '1. 编号',
  '- [ ] 待办', '- [x] 做完', '> 引用', '---', '![](attachments/i.jpg)', '', '',
  '**粗** 和 *斜*', '`code`', 'a < b & c > d', '  缩进行', '  - 嵌套', '\t制表',
  '<b>raw</b>', '[链接](u)', '中文 English 123', 'a*b*c', '~~删~~', '|表格|'];
var seed = 12345;
function rndInt(n) { seed = (seed * 1103515245 + 12345) % 2147483648; return seed % n; }

for (var c = 0; c < CORPUS.length; c++) check('C' + c, CORPUS[c]);
for (var g = 0; g < 150; g++) {
  var n = 1 + rndInt(6), doc = [];
  for (var k = 0; k < n; k++) doc.push(FRAG[rndInt(FRAG.length)]);
  check('G' + g, doc.join('\n'));
}

document.title = 'PASS=' + PASS + ' FAIL=' + FAILS.length;
var pre = document.createElement('pre');
pre.id = 'rep';
pre.textContent = FAILS.slice(0, 40).join('\n') || 'ALL PASS';
document.documentElement.appendChild(pre);
