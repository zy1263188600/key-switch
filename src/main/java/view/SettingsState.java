package view;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage; 
import org.jetbrains.annotations.NotNull; 
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@State(
    name = "InputMethodSettings",
    storages = @Storage("InputMethodSettings.xml") 
)
public class SettingsState implements PersistentStateComponent<SettingsState> {
    // 输入法切换策略
    public String strategyClass = "UIAutomationSwitcher";

    // 新增光标颜色配置
    public Color englishCursorColor;
    public Color chineseCursorColor;
 
    @Nullable
    @Override
    public SettingsState getState() {
        return this;
    }
 
    @Override
    public void loadState(@NotNull SettingsState state) {
        this.strategyClass  = state.strategyClass; 
    }

    public static SettingsState getInstance() {
        return ApplicationManager.getApplication().getService(SettingsState.class);
    }
}