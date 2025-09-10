package view

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import inputmethod.StrategyFactory
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import javax.swing.*
import java.util.Objects

class PluginSettingsConfigurable : Configurable {
    private var settingsPanel: JPanel? = null
    private var strategyComboBox: JComboBox<String>? = null
    private val state = SettingsState.getInstance() ?: throw IllegalStateException("SettingsState must not be null")

    override fun createComponent(): JComponent {
        settingsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(createStrategyPanel())
        }
        return settingsPanel!!
    }

    private fun createStrategyPanel(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(JLabel("切换策略:"))
            strategyComboBox = ComboBox(arrayOf("UIAutomationSwitcher", "InputMethodChecker")).apply {
                renderer = object : DefaultListCellRenderer() {
                    override fun getListCellRendererComponent(
                        list: JList<*>?,
                        value: Any?,
                        index: Int,
                        isSelected: Boolean,
                        cellHasFocus: Boolean
                    ): Component {
                        val display = value.toString()
                            .replace("UIAutomationSwitcher", "UIAutomation模拟点击托盘输入法按钮(默认)")
                            .replace("InputMethodChecker", "shift快捷键(兼容性好，可能误触发事件)")
                        return super.getListCellRendererComponent(list, display, index, isSelected, cellHasFocus)
                    }
                }
                selectedItem = state.strategyClass ?: DEFAULT_STRATEGY
            }
            add(strategyComboBox)
            add(JButton("测试输入法切换").apply {
                addActionListener { e: ActionEvent? -> inputSwitch() }
            })
        }
    }

    private fun inputSwitch() {
        try {
            val strategyName = strategyComboBox!!.selectedItem as String
            val strategy = StrategyFactory.createStrategy(strategyName)
            strategy.change()
        } catch (ex: Exception) {
            Messages.showErrorDialog("  切换失败: " + ex.message, "操作异常")
        }
    }

    override fun isModified(): Boolean {
        return !Objects.equals(strategyComboBox!!.selectedItem, state.strategyClass)
    }

    override fun apply() {
        state.strategyClass = strategyComboBox!!.selectedItem as String
    }

    override fun reset() {
        strategyComboBox!!.selectedItem = state.strategyClass ?: DEFAULT_STRATEGY
    }

    override fun getDisplayName() = "智能输入法切换插件"

    companion object {
        private const val DEFAULT_STRATEGY = "UIAutomationSwitcher"
    }
}