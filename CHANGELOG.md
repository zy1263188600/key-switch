<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# key-switch Changelog

## [Unreleased]
### Fixed
- IUIAutomation模式优化切换性能 20ms -> 0.5ms
- 多行文本选择中不再触发切换 解决KeyboardSwitcher误触问题
### Added
- 支持以下UI场景识别和设置场景默认输入法：   
   1、代码编辑区   
   2、重命名文本框   
   3、终端   
   4、搜索
- 新增IUIAutomation模式获取当前系统输入法状态