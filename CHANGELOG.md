<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# key-switch Changelog

## [0.0.7] - 2026-10-10
### Added
- 新增 ImmDirectSwitcher 定向直切策略并设为默认：Win32IME 更好的性能 无需任务栏按钮、无需 Shift 快捷键
- 设置页新增 ImmDirect 切换选项
- 欢迎通知新增“不再提示”持久化开关
### Fixed
- 切换策略后重置 Win32IME 去重缓存，避免旧状态残留
- UIAutomation 遇到未知托盘按钮不再静默误判为中文，改为失败走 Imm32 fallback
- 欢迎通知重启不再重复弹出