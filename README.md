# WhaleChat

> 一个只属于你和一只鲸鱼娘的聊天软件。

仓库：[github.com/xuerongyvdong/WhaleChat](https://github.com/xuerongyvdong/WhaleChat)　下载：[Releases](https://github.com/xuerongyvdong/WhaleChat/releases)

WhaleChat 是一个原生 Android 应用：屏幕里住着一只蓝色鲸鱼娘「小鱼」，她记得你说过的话，会在你安静时先开口，会写日记、发动态，相处久了还会给你写一段故事。

所有聊天、日记、记忆都只存在手机本地；只有你填写的 AI 接口会收到消息内容。

## 功能

- **聊天**：流式逐字回复；分几条由模型决定；支持表情、图片（可选视觉模型看图并记住画面）
- **性格**：人格与规则自定义；顶栏状态随时间变化；每天首次打开会主动问候；安静久了会主动发消息并推送通知
- **记忆**：多重记忆系统（脑区标签、重要度加权、有向图 + 向量检索）；**本地语义模型 bge-large-zh-v1.5**（1024 维，内置）；遗忘曲线 / 再巩固 / 每日睡眠巩固；记忆工作台；星图可视化
- **日记**：她按当天聊天写日记，也可自己写；亲密度升级解锁专属剧情
- **动态**：朋友圈式短动态，按间隔自动生成
- **相处**：活跃度与亲密度双轨成长，七个阶段；30 天热力图
- **成就**：26 个成就（含动漫主题），解锁时左侧弹窗 + 提示音，进度可在成就页查看
- **外观**：液态玻璃（模糊 / 折射 / 色散 / 高光边）；自定义壁纸；星图圆形展开
- **数据**：备份导入导出；API Key 使用 Android Keystore 加密存储；聊天图片自动清理

## 构建

环境要求：

- JDK 17
- Android SDK（compileSdk 36，需 build-tools 35+）
- Gradle 8.14+

```bash
# 1. 配置 SDK 路径
echo "sdk.dir=/path/to/android-sdk" > local.properties

# 2. 构建
gradle assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

> 默认使用本机 debug 签名，方便自用安装；如需上架，请自行配置 release 签名。

## 首次使用

1. 安装后打开，先回答一页填空：「我们的愿望是？」→ 输入 `世界和平`
2. 设置 → 接口 API，填写任意 OpenAI 兼容接口：
   - 接口地址：`https://api.deepseek.com/v1`
   - API Key：你自己的 Key
   - 模型名：`deepseek-flash`
3. 点「测试连接」通过后即可聊天

想要她「看图」，需要在 设置 → 模型 → 看图配置 里填一个支持视觉的模型（例如智谱 `glm-4v-flash`）。

## 技术栈

- Kotlin 2.2 + Jetpack Compose 1.9
- [Kyant0/Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) 液态玻璃渲染
- 自研多重记忆系统（脑区 / 图 / 向量检索）
- JobScheduler 后台主动消息 + 通知

## 目录

```
app/src/main/java/com/feiyu/app/
├── MainActivity.kt      主框架、模型调用、状态与持久化
├── Components.kt        设计令牌与通用组件
├── ChatScreen.kt        聊天页
├── SettingsScreen.kt    设置页、记忆工作台
├── Memory.kt            多重记忆系统
├── MemoryMap.kt         星图
├── Relationship.kt      活跃度与亲密度
├── Achievements.kt      成就
├── GateScreen.kt        首次启动门槛
├── StoryScreen.kt       好感度剧情
├── Proactive.kt         后台主动消息
└── SecureKey.kt         Key 加密
```

## 说明

- 这是一个个人项目，按「先能跑、再变好」的节奏迭代，代码结构还在逐步整理
- 不含任何内置密钥，请使用你自己的 API Key
- 卸载重装后加密的 Key 会失效，需要重新填写

## License

MIT
