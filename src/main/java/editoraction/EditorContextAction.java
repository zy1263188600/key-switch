package editoraction;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.regex.Pattern;

public class EditorContextAction extends AnAction {

    // IDEA 可编辑位置清单
    private static final String[][] EDITABLE_LOCATIONS = {
            {"主编辑器", "代码编辑窗口", "文本编辑"},
            {"文件重命名", "Shift+F6 重命名", "强制英文"},
            {"全局搜索框", "Ctrl+Shift+F 搜索", "文本编辑"},
            {"提交消息框", "版本控制提交窗口", "文本编辑"},
            {"调试器表达式", "调试时表达式计算", "文本编辑"},
            {"TODO 编辑", "TODO 工具窗口", "文本编辑"},
            {"设置面板", "设置菜单输入框", "文本编辑"},
            {"运行配置", "运行配置参数输入", "文本编辑"},
            {"数据库控制台", "数据库查询窗口", "文本编辑"},
            {"终端输入", "内置终端窗口", "文本编辑"}
    };

    public static void processContext(Component focusOwner) {
        if (!isFocusableComponent(focusOwner)) {
            return;
        }
        Editor editor = resolveEditorFromFocus(focusOwner);
        if (editor != null) {
            handleEditorContext(editor);
        } else if (focusOwner instanceof JTextComponent) {
            handleTextComponent((JTextComponent) focusOwner);
        } else if (isRenameTextField(focusOwner)) {
            handleRenameField((JTextField) focusOwner);
        }
    }

    private static boolean isFocusableComponent(Component comp) {
        return comp != null &&
                (comp instanceof JTextComponent ||
                        isRenameTextField(comp) ||
                        resolveEditorFromFocus(comp) != null);
    }

    @Nullable
    private static Editor resolveEditorFromFocus(Component component) {
        try {
            if (component instanceof JComponent) {
                JComponent jc = (JComponent) component;
                Editor editor = (Editor) jc.getClientProperty("Editor");
                if (editor != null) {
                    return editor;
                }
            }

            DataContext dataContext = DataManager.getInstance().getDataContext(component);
            return CommonDataKeys.EDITOR.getData(dataContext);
        } catch (Exception e) {
            return null;
        }
    }

    private static void handleEditorContext(Editor editor) {
        CaretModel caretModel = editor.getCaretModel();
        int offset = caretModel.getOffset();
        int start = Math.max(0, offset - 10);
        String textBeforeCursor = editor.getDocument().getText().substring(start, offset);

        String message = "主编辑器区域\n光标前内容: " +
                (textBeforeCursor.isEmpty() ? "[空]" : textBeforeCursor);
        showNotification("编辑器上下文", message);
    }

    private static void handleTextComponent(JTextComponent component) {
        String locationType = classifyComponent(component);
        showNotification("编辑区域信息", "当前位置: " + locationType);
    }

    private static void handleRenameField(JTextField field) {
        enforceEnglishInput(field);
        showNotification("位置信息", "文件重命名区域（已强制英文输入）");
    }

    private static String classifyComponent(JTextComponent component) {
        String name = component.getClass().getSimpleName();
        if (name.contains("SearchTextField")) {
            return "全局搜索框";
        }
        if (name.contains("CommitMessage")) {
            return "提交消息框";
        }
        if (name.contains("TodoPanel")) {
            return "TODO编辑区域";
        }
        return "文本输入区域";
    }

    private static void showNotification(String title, String content) {
        // 实际使用建议替换为状态栏通知
        Messages.showInfoMessage(content, title);
    }

    // 检测文件重命名文本框 
    private static boolean isRenameTextField(Component component) {
        if (!(component instanceof JTextField)) {
            return false;
        }

        // 通过父容器识别重命名对话框 
        Container parent = component.getParent();
        while (parent != null) {
            if (parent.getClass().getName().contains("RenameDialog")) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    // 强制英文输入实现 
    private static void enforceEnglishInput(JTextField textField) {
        // 使用 DocumentFilter 限制输入 
        ((javax.swing.text.PlainDocument) textField.getDocument())
                .setDocumentFilter(new DocumentFilter() {
                    private final Pattern englishPattern = Pattern.compile("^[\\x00-\\x7F]*$");

                    @Override
                    public void insertString(FilterBypass fb, int offset,
                                             String string, AttributeSet attr)
                            throws BadLocationException {
                        if (englishPattern.matcher(string).matches()) {
                            super.insertString(fb, offset, string, attr);
                        }
                    }

                    @Override
                    public void replace(FilterBypass fb, int offset, int length,
                                        String text, AttributeSet attrs)
                            throws BadLocationException {
                        if (englishPattern.matcher(text).matches()) {
                            super.replace(fb, offset, length, text, attrs);
                        }
                    }
                });
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        processContext(editor != null ? editor.getContentComponent() : focusOwner);
    }
}