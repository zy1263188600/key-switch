package inputmethod.cursor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import enums.InputState;
import inputmethod.cursor.CursorHandleStrategy;
import view.SettingsState;

import java.awt.*;

public class CursorColorHandle implements CursorHandleStrategy {
    @Override
    public void change(Editor editor, InputState state) {
        ApplicationManager.getApplication().invokeLater(() -> {
            EditorColorsScheme scheme = editor.getColorsScheme();
            Color color = (state == InputState.CHINESE) ?
                    SettingsState.getInstance().zhCursorColor :
                    SettingsState.getInstance().enCursorColor;
            scheme.setColor(EditorColors.CARET_COLOR, color);
            editor.getContentComponent().repaint();
        });
    }
}
