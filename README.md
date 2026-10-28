# Fifary

一个纯本地的 Android Markdown 笔记 App。不登录、不上传、不配置服务器也能完整使用；笔记就是硬盘上的 `.md` 文件，目录就是分组。

纯 Kotlin + Jetpack Compose，没有数据库、没有账号体系。

## 截图

| 日间 | 夜间 |
| --- | --- |
| ![列表](docs/screenshots/01-list-light.png) | ![列表-夜间](docs/screenshots/02-list-dark.png) |
| ![编辑](docs/screenshots/03-editor-dark.png) | ![设置](docs/screenshots/04-settings-dark.png) |

## 功能

- **本地 Markdown**：一篇笔记 = 一个 `.md` 文件，文件头的 front matter 记时间/置顶/私密，用别的编辑器打开也不影响
- **分组即目录**：一级目录就是分组，和 Obsidian 一样，改目录结构 App 里立刻跟着变
- **富文本编辑**：标题、加粗、斜体、列表、待办、表格、分割线、图片、录音，存下来还是 Markdown
- **置顶 / 最近删除**：删除进回收站保留 30 天，随时找回
- **私密笔记**：AES-GCM 独立数据密钥，密码只在本机验证，改密码不用重新加密笔记
- **WebDAV 同步**：任意标准 WebDAV（坚果云、Nextcloud、群晖…），可开自动同步；删除会同步过去，两边都改过就留一份「(冲突 时间)」副本，谁也不覆盖谁
- **存储位置可选**：内部存储 / 应用外部目录 / 自定义目录（如 `/sdcard/Documents/Fifary`）
- **导入导出**：整库打成一个 zip，换机就是导出再导入
- **深浅色 + 六套配色 + 中英双语**

## 数据在哪

```
vault/
├── 工作/周会记录.md        # 目录 = 分组
├── attachments/            # 图片和录音，跟笔记放一起
└── 咖啡豆.md               # 根上的笔记 = 「默认」分组
```

整个 `vault` 目录拷走就是一次完整备份。设置里的「导出备份」打的就是这个目录。

## 同步怎么做

设置 → 同步数据 → 填 WebDAV 地址、账号、密码。App 只在填的地址下面建一个 `Fifary/` 目录放笔记，不碰你 WebDAV 里的其他文件。

每次同步完会记一份索引（两边各有哪文件，多大、什么时候改的）。下一轮靠它判断：

- 只有一边变了 → 把变的那边同步给另一边
- 只有一边**没了**、另一边没动过 → 那就是删的，跟着删（删除会真的同步，不会又拉回来）
- 两边都变了（而且不是一个内容）→ 时间新的占原名，另一份另存成 `名字 (冲突 2026-01-01 120000).md`，两个版本都在

## 构建

```bash
# JDK 17 + Android SDK 36
./gradlew assembleDebug
```

## 下载

到 [Releases](https://github.com/siwuli/Fifary/releases) 拿 APK。

## License

[MIT](LICENSE)
