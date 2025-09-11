package view

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import inputmethod.StrategyFactory
import java.awt.Component
import java.awt.Dimension
import javax.swing.*
import java.util.Objects
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class PluginSettingsConfigurable : Configurable {
    private var inputSwitchStrategyComboBox: JComboBox<String>? = null
    private var switchingStrategyComboBox: JComboBox<String>? = null
    private val state = SettingsState.getInstance() ?: throw IllegalStateException("SettingsState must not be null")

    override fun createComponent(): JComponent? {
        return panel {
            group("输入法设置") {
                row {
                    cell(JLabel("切换策略:"))
                    cell(createStrategyComboBox(
                        items = arrayOf("UIAutomationSwitcher", "KeyboardSwitcher"),
                        displayMap = mapOf(
                            "UIAutomationSwitcher" to "UIAutomation模拟点击托盘输入法按钮(默认)",
                            "KeyboardSwitcher" to "shift快捷键(兼容性好，可能误触发事件)"
                        ),
                        selected = state.inputSwitchStrategyClass!!
                    )).applyToComponent {
                        inputSwitchStrategyComboBox = this
                    }
                    cell(  createTestButton("测试输入法切换") { inputSwitch() })
                }
            }

            group("提示设置") {
                row {
                    cell(JLabel("切换中英文时提示策略:"))
                    cell(createStrategyComboBox(
                        items = arrayOf("CursorColorStrategy", "MessageBoxStrategy"),
                        displayMap = mapOf(
                            "MessageBoxStrategy" to "光标气泡框",
                            "CursorColorStrategy" to "光标颜色"
                        ),
                        selected = state.switchingStrategyClass!!
                    )).applyToComponent {
                        switchingStrategyComboBox = this
                    }
                }
            }
        }
    }

    private fun createTestButton(text: String, action: () -> Unit): JButton {
        return JButton(text).apply {
            preferredSize = Dimension(120, preferredSize.height)  // 统一按钮宽度
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
            val strategy = StrategyFactory.createStrategy(strategyName)
            strategy.change()
        } catch (ex: Exception) {
            Messages.showErrorDialog("  切换失败: " + ex.message, "操作异常")
        }
    }

    override fun isModified(): Boolean {
        return !Objects.equals(inputSwitchStrategyComboBox!!.selectedItem, state.inputSwitchStrategyClass) ||
                !Objects.equals(switchingStrategyComboBox!!.selectedItem, state.switchingStrategyClass)
    }

    override fun apply() {
        state.inputSwitchStrategyClass = inputSwitchStrategyComboBox!!.selectedItem as String
        state.switchingStrategyClass = switchingStrategyComboBox!!.selectedItem as String
    }

    override fun reset() {
        inputSwitchStrategyComboBox!!.selectedItem = state.inputSwitchStrategyClass!!
        switchingStrategyComboBox!!.selectedItem = state.switchingStrategyClass!!
    }

    override fun getDisplayName() = "智能输入法切换插件"
}