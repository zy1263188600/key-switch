package view;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import inputmethod.InputMethodSwitchStrategy;
import inputmethod.StrategyFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Objects;

public class PluginSettingsConfigurable implements Configurable {
    private static final String DEFAULT_STRATEGY = "UIAutomationSwitcher";
    // 默认颜色配置
    private static final Color DEFAULT_EN_COLOR = new JBColor(new Color(255, 0, 0, 255), new Color(255, 0, 0, 255));   // 红色
    private static final Color DEFAULT_ZH_COLOR = new JBColor(new Color(0, 150, 255, 255), new Color(0, 150, 255, 255)); // 蓝色

    private JPanel settingsPanel;
    private JComboBox<String> strategyComboBox;
    private JTextField enColorField;
    private JTextField zhColorField;

    private final SettingsState state;

    public PluginSettingsConfigurable() {
        this.state = SettingsState.getInstance();
        if (this.state == null) {
            throw new IllegalStateException("SettingsState must not be null");
        }
    }

    @Override
    public JComponent createComponent() {
        settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        // 1. 切换策略选择组件
        JPanel strategyPanel = createStrategyPanel();
        settingsPanel.add(strategyPanel);
        // 2. 光标颜色配置组件
//        settingsPanel.add(createColorConfigPanel(" 英文光标颜色:", "enColor"));
//        settingsPanel.add(createColorConfigPanel(" 中文光标颜色:", "zhColor"));

        // 3. 操作按钮
        JPanel buttonPanel = createButtonPanel();
        settingsPanel.add(buttonPanel);

        return settingsPanel;
    }

    // 创建策略选择面板
    private JPanel createStrategyPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel("切换策略:"));
        strategyComboBox = new ComboBox<>(new String[]{
                "UIAutomationSwitcher",
                "InputMethodChecker"
        });
        strategyComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                String display = value.toString()
                        .replace("UIAutomationSwitcher", "UIAutomation模拟点击托盘输入法按钮(默认)")
                        .replace("InputMethodChecker", "shift快捷键(兼容性好，可能误触发事件)");
                return super.getListCellRendererComponent(list, display, index, isSelected, cellHasFocus);
            }
        });
        panel.add(strategyComboBox);
        return panel;
    }

    // 创建颜色配置面板
    private JPanel createColorConfigPanel(String label, String type) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel(label));

        JTextField colorField = new JTextField(7);
        colorField.setEditable(false);

        JButton colorBtn = new JButton("选择");
        colorBtn.setMargin(JBUI.insets(2, 8));

        if ("enColor".equals(type)) {
            enColorField = colorField;
//            enColorBtn = colorBtn;
            initColorField(colorField, state.englishCursorColor, DEFAULT_EN_COLOR);
        } else {
            zhColorField = colorField;
//            zhColorBtn = colorBtn;
            initColorField(colorField, state.chineseCursorColor, DEFAULT_ZH_COLOR);
        }

        colorBtn.addActionListener(e -> chooseColor(colorField));
        panel.add(colorField);
        panel.add(colorBtn);

        return panel;
    }

    // 初始化颜色文本框
    private void initColorField(JTextField field, Color savedColor, Color defaultColor) {
        Color color = savedColor != null ? savedColor : defaultColor;
        field.setText(colorToHex(color));
        field.setBackground(color);
        field.setForeground(getContrastColor(color));  // 自动适配文本颜色
    }

    // 创建按钮面板
    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(15, 0, 5, 0));

        JButton testInputSwitchBut = new JButton("测试输入法切换");
        testInputSwitchBut.addActionListener(this::inputSwitch);
        buttonPanel.add(testInputSwitchBut);

        return buttonPanel;
    }

    private void inputSwitch(ActionEvent e) {
        try {
            String strategyName = (String) strategyComboBox.getSelectedItem();
            InputMethodSwitchStrategy strategy = StrategyFactory.createStrategy(strategyName);
            strategy.change();
        } catch (Exception ex) {
            Messages.showErrorDialog(" 切换失败: " + ex.getMessage(), "操作异常");
        }
    }

    // 颜色选择对话框
    private void chooseColor(JTextField targetField) {
        Color initial = hexToColor(targetField.getText());
        Color color = JColorChooser.showDialog(
                settingsPanel,
                "选择光标颜色",
                initial != null ? initial : JBColor.WHITE
        );

        if (color != null) {
            targetField.setText(colorToHex(color));
            targetField.setBackground(color);
            targetField.setForeground(getContrastColor(color));
        }
    }

    // 颜色转换工具方法
    private String colorToHex(Color color) {
        return String.format("#%02x%02x%02x",
                color.getRed(),
                color.getGreen(),
                color.getBlue()
        );
    }

    private Color hexToColor(String hex) {
        try {
            return Color.decode(hex);
        } catch (Exception e) {
            return null;
        }
    }

    // 获取对比色（自动选择白/黑文字）
    private Color getContrastColor(Color color) {
        double luminance = (0.299 * color.getRed() +
                0.587 * color.getGreen() +
                0.114 * color.getBlue()) / 255;
        return luminance > 0.5 ? JBColor.BLACK : JBColor.WHITE;
    }

    @Override
    public boolean isModified() {
        boolean strategyModified = !Objects.equals(strategyComboBox.getSelectedItem(), state.strategyClass);

        boolean enColorModified = !enColorField.getText().equals(
                state.englishCursorColor != null ?
                        colorToHex(state.englishCursorColor) :
                        colorToHex(DEFAULT_EN_COLOR)
        );

        boolean zhColorModified = !zhColorField.getText().equals(
                state.chineseCursorColor != null ?
                        colorToHex(state.chineseCursorColor) :
                        colorToHex(DEFAULT_ZH_COLOR)
        );

        return strategyModified || enColorModified || zhColorModified;
    }

    @Override
    public void apply() {
        if (state != null) {
            // 保存策略配置
            state.strategyClass = (String) strategyComboBox.getSelectedItem();

            // 保存颜色配置
            state.englishCursorColor = hexToColor(enColorField.getText());
            state.chineseCursorColor = hexToColor(zhColorField.getText());
        } else {
            Messages.showWarningDialog("Err", "系统警告");
        }
    }

    @Override
    public void reset() {
        if (state != null) {
            // 重置策略选择
            strategyComboBox.setSelectedItem(
                    state.strategyClass != null ? state.strategyClass : DEFAULT_STRATEGY);

            // 重置颜色配置
            initColorField(enColorField, state.englishCursorColor, DEFAULT_EN_COLOR);
            initColorField(zhColorField, state.chineseCursorColor, DEFAULT_ZH_COLOR);
        }
    }

    @Override
    public String getDisplayName() {
        return "智能输入法切换插件";
    }
}