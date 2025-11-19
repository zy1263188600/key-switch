package inputmethod.cursor;

import com.intellij.openapi.editor.Editor;
import enums.InputState;
import state.SettingsState;

public class CursorHandle {

    // 私有构造防止实例化
    private CursorHandle() {
    }

    public static void change(Editor editor, InputState state) {
        SettingsState settingsState = SettingsState.getInstance();
        CursorStrategyFactory.createStrategy(settingsState.switchingStrategyClass).change(editor, state);
    }
}