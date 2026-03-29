# AutoClicker — Android 自动点击器

## 功能模块

| 模块 | 说明 |
|------|------|
| 悬浮工具条 | 三级展开，可拖拽，叠加任意 App 上方 |
| 点位脚本 | 多点位有序点击，显示序号+坐标，支持拖拽排序 |
| 毫秒级间隔 | 分:秒.毫秒三段拖拽调节，最小 1ms |
| 脚本保存 | SharedPreferences 本地持久化 |
| 导入/导出 | JSON 格式，通过 SAF 文件选择器 |

## 技术实现

- **点击执行**：`AccessibilityService.dispatchGesture()` — 无需 root
- **悬浮窗**：`TYPE_APPLICATION_OVERLAY` + 前台 Service
- **协程调度**：Kotlin Coroutines，精准 `delay()` 毫秒级间隔

## 快速开始

### 1. 导入项目
```
Android Studio → File → Open → 选择 AutoClicker 目录
```

### 2. 编译运行
```
Run → Run 'app'（连接设备或模拟器）
```

### 3. 权限开启顺序
1. **悬浮窗权限**：App 启动后自动跳转，点击「AutoClicker」→ 允许
2. **无障碍服务**：设置 → 无障碍 → 已安装的服务 → AutoClicker → 开启

### 4. 使用流程
```
主界面 → 新建脚本 → 在悬浮窗展开面板 → 点击"+ 添加点位"输入坐标
→ 设置间隔和次数 → 按 ▶ 开始执行
```

## 项目结构

```
app/src/main/java/com/autoclicker/
├── data/
│   ├── ClickScript.kt       # 数据模型（ClickStep, ClickScript）
│   └── ScriptRepository.kt  # 本地存储 + 导入导出
├── service/
│   ├── AutoClickAccessibilityService.kt  # 手势执行核心
│   └── FloatWindowService.kt             # 悬浮窗前台服务
└── ui/
    ├── MainActivity.kt      # 主界面（权限引导 + 脚本列表）
    ├── FloatBarView.kt      # 悬浮工具条视图
    └── StepAdapter.kt       # 点位列表 RecyclerView
```

## 扩展提示

- **录制模式**：在 `FloatBarView.toggleRecording()` 中，可通过监听系统 `WindowCallback` 捕获触摸坐标，自动添加步骤
- **随机抖动**：在 `AutoClickAccessibilityService.performClick()` 中为 x/y 加 `Random.nextInt(-5, 5)` 即可
- **步骤自定义延迟**：`ClickStep.delayMs > 0` 时优先使用，适合不同步骤需要不同间隔的场景
