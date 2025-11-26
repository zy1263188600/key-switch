package editoraction;

import com.intellij.openapi.CompositeDisposable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocTokenImpl;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Alarm;
import enums.InputState;
import inputmethod.cursor.CursorHandle;
import inputmethod.switcher.InputMethodSwitcher;
import org.jetbrains.annotations.NotNull;
import utlis.LogUtil;
import state.SettingsState;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

import static editoraction.FocusHandel.*;

@Service(Service.Level.APP)
public final class CursorTrackerService implements Disposable {
    //    private static final long EVENT_THRESHOLD = 100; // 事件过滤阈值（毫秒）
    private static final Character.UnicodeBlock[] CHINESE_BLOCKS = { // 中文相关Unicode区块
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION,
            Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
    };
    private final CompositeDisposable composite = new CompositeDisposable();
    //    private final Map<Editor, Long> lastInputTimeMap = new ConcurrentHashMap<>();
    private final Map<Editor, Boolean> selectionStateMap = new ConcurrentHashMap<>();

    public CursorTrackerService() {
        EditorEventMulticaster editorEventMulticaster = EditorFactory.getInstance().getEventMulticaster();
//        setupDocumentListener(editorEventMulticaster);
        setupCaretListener(editorEventMulticaster);
        setupMouseListener(editorEventMulticaster);
        setupEditorLifecycleListener();
        setupFocusListener();

        Disposer.register(this, this::disposeResources);
    }

    @Override
    public void dispose() {
        disposeResources();
    }

    private void disposeResources() {
        composite.dispose();
//        lastInputTimeMap.clear();
        selectionStateMap.clear();
    }

//    private void setupDocumentListener(EditorEventMulticaster multicaster) {
//        multicaster.addDocumentListener(new DocumentListener() {
//            //文档变化
//            @Override
//            public void documentChanged(@NotNull DocumentEvent event) {
//                updateLastInputTime(event.getDocument());
//            }
//        }, composite);
//    }

    private void setupCaretListener(EditorEventMulticaster multicaster) {
        multicaster.addCaretListener(new CaretListener() {
            //光标变化
            @Override
            public void caretPositionChanged(@NotNull CaretEvent event) {
                handleCaretMovement(event.getEditor());
            }
        }, composite);
    }

    private void setupMouseListener(EditorEventMulticaster multicaster) {
        multicaster.addEditorMouseListener(new EditorMouseListener() {
            //鼠标按下
            @Override
            public void mousePressed(@NotNull EditorMouseEvent e) {
                if (e.getMouseEvent().getButton() == MouseEvent.BUTTON1) {
                    selectionStateMap.put(e.getEditor(), true);
                }
            }

            //鼠标释放
            @Override
            public void mouseReleased(@NotNull EditorMouseEvent e) {
                if (e.getMouseEvent().getButton() != MouseEvent.BUTTON1) return;

                Editor editor = e.getEditor();
                if (selectionStateMap.remove(editor) != null &&
                        editor.getSelectionModel().hasSelection()) {
                    switchInputOnChar(editor);
                }
            }
        }, composite);
    }

    //编辑器变化监听
    private void setupEditorLifecycleListener() {
        EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
            //关闭编辑器时
            @Override
            public void editorReleased(@NotNull EditorFactoryEvent event) {
                Editor editor = event.getEditor();
//                lastInputTimeMap.remove(editor);
                selectionStateMap.remove(editor);
            }
        }, composite);
    }

    //焦点变化监听器
    private void setupFocusListener() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener("focusOwner", e -> {
//                    LogUtil.info(getFocusedComponentHierarchy());
                    FocusArea focusArea = detectFocusArea();
                    if (focusArea == FocusHandel.FocusArea.EDITOR) {
                        LogUtil.info(focusArea.name);
                        switchInputOnState(SettingsState.getInstance().editorInputState);
                    } else if (focusArea == FocusHandel.FocusArea.RENAME_DIALOG) {
                        LogUtil.info(focusArea.name);
                        //这里会弹出新的窗体导致windows的焦点切换所以暂定需要延迟进行切换 窗体构建完成切换是最准确的
                        Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
                        alarm.addRequest(() -> switchInputOnState(SettingsState.getInstance().renameDialogInputState), 1);
                    } else if (focusArea == FocusHandel.FocusArea.TERMINAL) {
                        LogUtil.info(focusArea.name);
                        switchInputOnState(SettingsState.getInstance().terminalInputState);
                    } else if (focusArea == FocusHandel.FocusArea.SEARCH) {
                        LogUtil.info(focusArea.name);
                        switchInputOnState(SettingsState.getInstance().searchInputState);

                    }
                });
    }


    private void handleCaretMovement(Editor editor) {
        //fix 延迟执行等待selectionModel.hasSelection()、selectionModel.getSelectionStart()/getSelectionEnd()更新最新的光标位置状态
        Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
        alarm.addRequest(() -> {
            if (editor.isDisposed()) {
                return;
            }
            if (isWithinSelectionBoundary(editor)) {
                LogUtil.debug(" 检测到文本选择，跳过输入法切换");
                return;
            }
//            InputState inputState = detectAndPrintElementType(editor);
//            if (inputState != null) {
//                //如果在特殊区域则按配置进行输入法切换
//                switchInputOnState(inputState);
//            } else {
                //非特殊区域才进行前一个字符判断是否输入法切换
                switchInputOnChar(editor);
//            }
        }, 1);
    }

    public static InputState detectAndPrintElementType(Editor editor) {
        Project project = editor.getProject();
        if (project == null || project.isDisposed()) {
            LogUtil.debug("Project 不可用或已释放");
            return null;
        }
        PsiFile psiFile = PsiDocumentManager.getInstance(project)
                .getPsiFile(editor.getDocument());
        if (psiFile == null) {
            LogUtil.debug("当前不在可解析的文件中");
            return null;
        }

        int offset = editor.getCaretModel().getOffset() > 0 ? editor.getCaretModel().getOffset() - 1 : 0;

        PsiElement element = psiFile.findElementAt(offset);
        if (element == null) {
            LogUtil.debug("无法确定当前Psi元素类型");
            return null;
        }
        System.out.println("未知元素Element:  " + element + ", Class: " + element.getClass());
        switch (element) {
            case PsiComment psiComment -> {
                System.out.println("普通注释:" + psiComment.getText());
                return InputState.CHINESE;
            }
            case PsiDocToken psiDocToken -> {
                System.out.println("文档注释:" + psiDocToken.getText()                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              );
                return InputState.CHINESE;
            }

            case PsiWhiteSpace psiWhiteSpace -> {
                System.out.println("空白区域:" + psiWhiteSpace.getText());
                return null;
            }
            case PsiJavaToken psiJavaToken -> {
                System.out.println("java关键字:" + psiJavaToken.getText());
                return null;
            }
            default -> {
//                System.out.println("未知元素Element:  " + element + ", Class: " + element.getClass());
                return null;
            }
        }
    }

    private boolean isWithinSelectionBoundary(Editor editor) {
        CaretModel caret = editor.getCaretModel();
        int currentOffset = caret.getOffset();
        int selStart = caret.getCurrentCaret().getSelectionStart();
        int selEnd = caret.getCurrentCaret().getSelectionEnd();
                /*
                 fix 修复选中文本后 鼠标首次单击其余区域不会切换
                 这里主要是KeyboardSwitcher模式会误触，UIAutomation影响不大
                 貌似是IDEA平台的BUG
                 selectionModel.hasSelection()好像没有正确返回是否在选取中 所以自行判断是否有选中状态
                 https://youtrack.jetbrains.com/issue/IDEA-381472/hasSelection-returns-true-in-CaretListener-after-moving-cursor-out-of-selection
                 -1 +1 表示判断当前光标是否紧跟随选择区域
                 */

                /*
                 fix 选中多行文本、并且含有中/英文字符 会频繁切换
                 这里主要是KeyboardSwitcher模式会误触，UIAutomation影响不大
                 selectionModel.getSelectionStart()/getSelectionEnd() 在多行选中时没有返回实时的选中位置
                 https://youtrack.jetbrains.com/issue/IJPL-216012/Selection-Model-Values-Not-Updated-Correctly-During-Multi-Line-Selection-in-CaretListener
                 */
        var flag = (currentOffset == selStart && selStart == selEnd);
        boolean b = !flag && currentOffset >= selStart - 1 && currentOffset <= selEnd + 1;
//        System.out.printf("""
//                offset:%d  | selStart:%d | selEnd:%d%n
//                """, currentOffset, selStart, selEnd);
//        System.out.println(b);
        return b;


    }

    private boolean switchInputOnState(InputState state) {
        InputState currentMode = InputMethodSwitcher.getCurrentMode();
        LogUtil.debug("当前输入法状态：" + currentMode);
        boolean b = !state.equals(currentMode);
        if (b) {
            LogUtil.info(" 触发输入法切换");
            InputMethodSwitcher.change();
        }
        return b;
    }

    private void switchInputOnChar(Editor editor) {
        int offset = editor.getCaretModel().getOffset();
        String prefix = getPrefixText(editor, offset, 1);
        if (prefix.isEmpty()) {
            return;
        }
        InputState prevCharState = getCharacterState(prefix.charAt(0));
        LogUtil.debug("前一个字符：" + prefix.charAt(0));
        LogUtil.debug("前一个字符状态：" + prevCharState);
        if (switchInputOnState(prevCharState)) {
            CursorHandle.change(editor, prevCharState);
        }
    }

    private static InputState getCharacterState(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        for (Character.UnicodeBlock chineseBlock : CHINESE_BLOCKS) {
            if (chineseBlock.equals(block)) {
                return InputState.CHINESE;
            }
        }
        return InputState.ENGLISH;
    }

    private String getPrefixText(Editor editor, int offset, int length) {
        if (offset <= 0 || length <= 0) {
            return "";
        }
        int start = Math.max(0, offset - length);
        Document doc = editor.getDocument();
        return doc.getText(new TextRange(start, offset));
    }
}