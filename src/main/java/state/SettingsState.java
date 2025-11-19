package state;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.ui.JBColor;
import enums.InputState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@State(
        name = "InputMethodSettings",
        storages = @Storage("InputMethodSettings.xml")
)
public class SettingsState implements PersistentStateComponent<SettingsState> {
    // 策略配置
    public String inputSwitchStrategyClass = "UIAutomationSwitcher";
    public String switchingStrategyClass = "CursorColorStrategy";

    // 光标颜色
    public int zhCursorColorRGB = JBColor.RED.getRGB();
    public int enCursorColorRGB = JBColor.BLUE.getRGB();

    // 气泡框配置
    public Integer balloonDuration = 500;

    public InputState editorInputState = InputState.ENGLISH;
    public InputState renameDialogInputState = InputState.ENGLISH;
    public InputState terminalInputState = InputState.ENGLISH;
    public InputState searchInputState = InputState.ENGLISH;
//    public InputState otherInputState = InputState.ENGLISH;

    // 运行时对象 (不序列化)
    public transient JBColor zhCursorColor;
    public transient JBColor enCursorColor;



    @Nullable
    @Override
    public SettingsState getState() {
        // 同步最新颜色值到存储字段
        if (zhCursorColor != null) {
            zhCursorColorRGB = zhCursorColor.getRGB();
        }
        if (enCursorColor != null) {
            enCursorColorRGB = enCursorColor.getRGB();
        }
        return this;
    }

    @Override
    public void loadState(@NotNull SettingsState state) {
        this.inputSwitchStrategyClass = state.inputSwitchStrategyClass;
        this.switchingStrategyClass = state.switchingStrategyClass;
        this.balloonDuration = state.balloonDuration;

        this.editorInputState = state.editorInputState;
        this.renameDialogInputState = state.renameDialogInputState;
        this.terminalInputState = state.terminalInputState;
        this.searchInputState = state.searchInputState;


        this.zhCursorColor = createThemeAwareColor(state.zhCursorColorRGB);
        this.enCursorColor = createThemeAwareColor(state.enCursorColorRGB);
        this.zhCursorColorRGB = state.zhCursorColorRGB;
        this.enCursorColorRGB = state.enCursorColorRGB;
    }

    /**
     * 创建主题感知的颜色对象
     *
     * @param rgb 颜色编码
     * @return 自动适应主题的JBColor实例
     */
    private JBColor createThemeAwareColor(int rgb) {
        Color baseColor = new Color(rgb);
        return new JBColor(
                baseColor,  // 亮色主题
                adjustForDarkTheme(baseColor)  // 暗色主题
        );
    }

    /**
     * 暗色主题颜色增强 (示例)
     */
    private Color adjustForDarkTheme(Color base) {
        // 实际项目可添加更复杂的调整逻辑
        return new Color(
                Math.min(255, base.getRed() + 30),
                Math.min(255, base.getGreen() + 30),
                Math.min(255, base.getBlue() + 30)
        );
    }

    public static SettingsState getInstance() {
        return ApplicationManager.getApplication().getService(SettingsState.class);
    }
}