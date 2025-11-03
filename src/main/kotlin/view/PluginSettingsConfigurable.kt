package view

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.util.Disposer
import inputmethod.switcher.SwitcherStrategyFactory
import java.awt.Component
import java.awt.Dimension
import javax.swing.*
import java.util.Objects
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.ColorPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextField
import java.awt.FlowLayout
import javax.swing.JComponent
import com.intellij.util.ui.JBUI
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

class PluginSettingsConfigurable() : Configurable {

    private var switchLabel = "切换策略:"
    private var tipLabel = "提示策略:"

    private var inputSwitchStrategyComboBox: JComboBox<String>? = null
    private var switchingStrategyComboBox: JComboBox<String>? = null
    private var zhCursorColorPanel: ColorPanel? = null
    private var enCursorColorPanel: ColorPanel? = null
    private val state = SettingsState.getInstance() ?: throw IllegalStateException("SettingsState must not be null")
    private var balloonDurationField: JBTextField? = null
    private var balloonDurationValidator: ComponentValidator? = null

    override fun createComponent(): JComponent {
        return panel {
            group("输入法设置") {
                row {
                    label(switchLabel)
                    cell(
                        createStrategyComboBox(
                            items = arrayOf("UIAutomationSwitcher", "KeyboardSwitcher"),
                            displayMap = mapOf(
                                "UIAutomationSwitcher" to "UIAutomation模拟点击托盘输入法按钮(默认)",
                                "KeyboardSwitcher" to "shift快捷键(兼容性好，可能误触发事件)"
                            ),
                            selected = state.inputSwitchStrategyClass!!
                        )
                    ).applyToComponent {
                        inputSwitchStrategyComboBox = this
                    }
                    cell(createTestButton("测试输入法切换") { inputSwitch() })
                }
            }

            group("提示设置") {
                row {
                    label(tipLabel)
                    cell(
                        createStrategyComboBox(
                            items = arrayOf("CursorColorStrategy", "BalloonStrategy"),
                            displayMap = mapOf(
                                "BalloonStrategy" to "气泡提示",
                                "CursorColorStrategy" to "光标颜色"
                            ),
                            selected = state.switchingStrategyClass!!
                        )
                    ).applyToComponent {
                        switchingStrategyComboBox = this
                        addActionListener { updateDynamicSettings() }
                    }
                }

                // 动态设置区域
                row {
                    cell(createDynamicSettingsPanel())
                }
            }
        }.apply {
            // 初始加载时更新动态设置
            updateDynamicSettings()
        }
    }

    private fun createDynamicSettingsPanel(): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)

            // 光标颜色设置 - 使用颜色选择面板
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JLabel("中文光标颜色:"))
                add(ColorPanel().apply {
                    zhCursorColorPanel = this
                    preferredSize = Dimension(80, preferredSize.height)
                    selectedColor = state.zhCursorColor ?: JBColor.RED
                })
                add(JLabel("英文光标颜色:"))
                add(ColorPanel().apply {
                    enCursorColorPanel = this
                    preferredSize = Dimension(80, preferredSize.height)
                    selectedColor = state.enCursorColor ?: JBColor.BLUE
                })
            })

            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                border = JBUI.Borders.emptyTop(5) // 添加顶部间距
                add(JLabel("气泡持续时间(ms):"))
                add(JBTextField().apply {
                    balloonDurationField = this
                    text = state.balloonDuration?.toString() ?: "500"
                    columns = 6

                    addKeyListener(object : KeyAdapter() {
                        override fun keyReleased(e: KeyEvent?) {
                            if (switchingStrategyComboBox?.selectedItem == "BalloonStrategy") {
                                balloonDurationValidator?.revalidate()
                            }
                        }
                    })

                    fun validate(): ValidationInfo? {
                        if (switchingStrategyComboBox?.selectedItem == "BalloonStrategy") {
                            val error = validateBalloonDuration()
                            if (error != null) {
                                return ValidationInfo(error, balloonDurationField)
                            }
                        }
                        return null
                    }

                    val validatorsDisposable = Disposer.newDisposable()
                    // 创建验证器
                    balloonDurationValidator =
                        ComponentValidator(validatorsDisposable).withValidator(::validate).installOn(this)
                })
            })
        }
    }

    private fun validateBalloonDuration(): String? {
        val text = balloonDurationField?.text ?: ""
        if (text.isBlank()) {
            return "请输入持续时间"
        }
        val value = try {
            text.toInt()
        } catch (_: NumberFormatException) {
            return "必须输入数字"
        }
        if (value !in 1..10000) {
            return "请输入1-10000之间的数字"
        }
        return null
    }

    private fun updateDynamicSettings() {
        val strategy = switchingStrategyComboBox?.selectedItem?.toString() ?: return

        // 控制组件可见性
        zhCursorColorPanel?.parent?.isVisible = strategy == "CursorColorStrategy"
        enCursorColorPanel?.parent?.isVisible = strategy == "CursorColorStrategy"

        // 控制气泡持续时间组件的可见性
        balloonDurationField?.parent?.isVisible = strategy == "BalloonStrategy"

        // 策略切换时重新验证
        if (strategy == "BalloonStrategy") {
            balloonDurationValidator?.revalidate()
        } else {
            balloonDurationValidator?.updateInfo(null) // 清除错误状态
        }

        // 设置初始值
        zhCursorColorPanel?.selectedColor = state.zhCursorColor ?: JBColor.RED
        enCursorColorPanel?.selectedColor = state.enCursorColor ?: JBColor.BLUE
        balloonDurationField?.text = state.balloonDuration?.toString() ?: "500"
    }

    private fun createTestButton(text: String, action: () -> Unit): JButton {
        return JButton(text).apply {
            preferredSize = Dimension(120, preferredSize.height)
            addActionListener { action() }
        }
    }

    private fun createStrategyComboBox(
        items: Array<String>, displayMap: Map<String, String>, selected: String
    ): ComboBox<String> {
        return ComboBox(items).apply {
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
                ): Component {
                    val displayValue = value?.toString()?.let { displayMap[it] ?: it }
                    return super.getListCellRendererComponent(
                        list, displayValue, index, isSelected, cellHasFocus
                    )
                }
            }
            selectedItem = selected
        }
    }

    private fun inputSwitch() {
        try {
            val strategyName = inputSwitchStrategyComboBox!!.selectedItem as String
            val strategy = SwitcherStrategyFactory.createStrategy(strategyName)
            strategy.change()
        } catch (ex: Exception) {
            Messages.showErrorDialog("   切换失败: ${ex.message}", "操作异常")
        }
    }

    override fun isModified(): Boolean {
        if (balloonDurationField?.isVisible == true) {
            validateBalloonDuration() == null
        }
        return !Objects.equals(inputSwitchStrategyComboBox!!.selectedItem, state.inputSwitchStrategyClass) ||
                !Objects.equals(switchingStrategyComboBox!!.selectedItem, state.switchingStrategyClass) ||
                !Objects.equals(zhCursorColorPanel?.selectedColor, state.zhCursorColor) ||
                !Objects.equals(enCursorColorPanel?.selectedColor, state.enCursorColor) ||
                (balloonDurationField?.isVisible == true && balloonDurationField?.text != state.balloonDuration?.toString())
    }

    override fun apply() {
        state.inputSwitchStrategyClass = inputSwitchStrategyComboBox!!.selectedItem as String
        state.switchingStrategyClass = switchingStrategyComboBox!!.selectedItem as String
        state.zhCursorColor = zhCursorColorPanel?.selectedColor.let { it?.let { regular -> JBColor(regular, it) } }
        state.enCursorColor = enCursorColorPanel?.selectedColor.let { it?.let { regular -> JBColor(regular, it) } }

        if (switchingStrategyComboBox?.selectedItem == "BalloonStrategy") {
            state.balloonDuration = try {
                balloonDurationField?.text?.toInt() ?: 2000
            } catch (e: NumberFormatException) {
                2000
            }
        }
    }

    override fun reset() {
        inputSwitchStrategyComboBox!!.selectedItem = state.inputSwitchStrategyClass!!
        switchingStrategyComboBox!!.selectedItem = state.switchingStrategyClass!!
        zhCursorColorPanel?.selectedColor = state.zhCursorColor ?: JBColor.RED
        enCursorColorPanel?.selectedColor = state.enCursorColor ?: JBColor.BLUE
        balloonDurationField?.text = state.balloonDuration?.toString() ?: "2000"
        updateDynamicSettings()
    }

    override fun getDisplayName() = "智能输入法切换插件"
}