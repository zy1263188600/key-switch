<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# key-switch Changelog

## [0.0.7] - 2026-10-10
### Added
- 新增 ImmDirectSwitcher 定向直切策略并设为默认：Win32IME 四重保障（Unsafe 强突 JBR21 WInputMethod + IMM32 直接 set + WM_IME_CONTROL 消息注入 + 状态双发校正），无需任务栏按钮、无需 Shift 快捷键
- 设置页新增 ImmDirect 切换选项与一键测试
- 欢迎通知新增“不再提示”持久化开关
### Changed
- 结构优化：修正 utlis/FocusHandel 包类拼写；Win32IME 搬入 impl 包并拆分 JNA 接口；合并重复启动项；CursorTrackerService 抽取可测纯函数 CaretDecision
### Fixed
- 切换策略后重置 Win32IME 去重缓存，避免旧状态残留
- UIAutomation 遇到未知托盘按钮不再静默误判为中文，改为失败走 Imm32 fallback
- 欢迎通知重启不再重复弹出

## [Unreleased]
### Fixed
- IUIAutomation模式优化切换性能 20ms -> 0.5ms
- 多行文本选择中不再触发切换 解决KeyboardSwitcher误触问题
### Added
- 增加使用说明链接
- 新增IUIAutomation模式获取当前系统输入法状态