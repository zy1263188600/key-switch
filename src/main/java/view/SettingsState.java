package view;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "InputMethodSettings",
        storages = @Storage("InputMethodSettings.xml")
)
public class SettingsState implements PersistentStateComponent<SettingsState> {
    // 输入法切换策略
    public String inputSwitchStrategyClass = "UIAutomationSwitcher";
    // 切换输入法时的提示策略
    public String switchingStrategyClass = "CursorColorStrategy";

    @Nullable
    @Override
    public SettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull SettingsState state) {
        this.inputSwitchStrategyClass = state.inputSwitchStrategyClass;
    }

    public static SettingsState getInstance() {
        return ApplicationManager.getApplication().getService(SettingsState.class);
    }
}