package editoraction;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorComponentImpl;

import java.awt.*;

public class FocusHandel {

    public static String getFocusedComponentHierarchy() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner == null) {
            return "No focused component";
        }
        StringBuilder sb = new StringBuilder();
        Container parent = focusOwner.getParent();
        // 自底向上遍历组件树
        sb.append("Focused:  ").append(focusOwner.getClass().getName()).append("\n");
        while (parent != null) {
            sb.append("   ↑ ").append(parent.getClass().getName()).append("\n");
            parent = parent.getParent();
        }
        return sb.toString();
    }

    // 焦点区域类型检测
    public enum FocusArea {
        EDITOR("代码编辑区"), //代码编辑区
        RENAME_DIALOG("重命名文本框"), //重命名文本框
        TERMINAL("终端"), //终端
        SEARCH("搜索"),  //搜索
        OTHER("其他"); //其他

        public final String name;

        FocusArea(String name) {
            this.name = name;
        }
    }

    public static FocusArea detectFocusArea() {
        Component comp = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        // 检测文本框处于什么区域
        while (comp != null) {
            String className = comp.getClass().getName();
            if (className.contains("PsiAwareTextEditorComponent")) {
                return FocusArea.EDITOR;
            }
            if (className.contains("RenameDialog")) {
                return FocusArea.RENAME_DIALOG;
            }
            if (className.contains("Terminal")) {
                return FocusArea.TERMINAL;
            }
            if (
                //全局搜索
                    className.contains("SearchEverywhereUI") ||
                            //代码区域的搜索
                            className.contains("SearchTextArea")) {
                return FocusArea.SEARCH;
            }
            comp = comp.getParent();
        }
        return FocusArea.OTHER;
    }
}
