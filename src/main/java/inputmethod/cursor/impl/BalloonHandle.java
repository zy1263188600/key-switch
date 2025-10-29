package inputmethod.cursor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import enums.InputState;
import inputmethod.cursor.CursorHandleStrategy;
import view.SettingsState;

import javax.swing.*;
import java.awt.*;
import java.util.WeakHashMap;

public class BalloonHandle implements CursorHandleStrategy {

    // 存储每个Editor对应的气泡框实例
    private static final WeakHashMap<Editor, Balloon> balloonMap = new WeakHashMap<>();
    private static final WeakHashMap<Editor, Alarm> alarmMap = new WeakHashMap<>();
    private static final WeakHashMap<Editor, VisibleAreaListener> scrollListenerMap = new WeakHashMap<>();

    @Override
    public void change(Editor editor, InputState state) {
        JLabel label;
        if (state == InputState.CHINESE) {
            label = new JLabel("中", SwingConstants.CENTER);
        } else {
            label = new JLabel("en", SwingConstants.CENTER);
        }
        label.setFont(JBUI.Fonts.create(Font.MONOSPACED, 14));
//        label.setForeground(new Color(0x4985FA));

        // 2. 创建气泡框构建器
        Balloon balloon = JBPopupFactory.getInstance()
                .createBalloonBuilder(label)
//                .setFillColor(new Color(0x2B2D30))
//                .setBorderColor(JBColor.GRAY)
                .setBorderInsets(JBUI.insets(2))
                .setShadow(false)
                .setCloseButtonEnabled(false)
                .setHideOnAction(false)
                .setHideOnClickOutside(false)
                .setHideOnKeyOutside(false)
                .setShowCallout(false)
                .setAnimationCycle(0)
                .setDisposable(getAnchorDisposable(editor))
                .createBalloon();

        Point relativePoint = calculateRelativePosition(editor);
        disposeExistingBalloon(editor);
        removeScrollListener(editor);
        balloon.show(new RelativePoint(editor.getContentComponent(), relativePoint),
                Balloon.Position.above);

        balloonMap.put(editor, balloon);
        setupAutoHideTimer(editor, balloon);
        VisibleAreaListener scrollListener = e -> hideBalloonForEditor(editor);
        editor.getScrollingModel().addVisibleAreaListener(scrollListener);
        scrollListenerMap.put(editor, scrollListener);
    }

    private Point calculateRelativePosition(Editor editor) {
        VisualPosition visualPos = editor.getCaretModel().getVisualPosition();
        Point point = editor.visualPositionToXY(visualPos);
        point.y -= 10;
        return point;
    }

    private Disposable getAnchorDisposable(Editor editor) {
        if (editor instanceof Disposable) {
            return (Disposable) editor;
        }

        Project project = editor.getProject();
        return project != null ? project : ApplicationManager.getApplication();
    }

    private void disposeExistingBalloon(Editor editor) {
        Balloon existingBalloon = balloonMap.get(editor);
        if (existingBalloon != null && !existingBalloon.isDisposed()) {
            existingBalloon.hide();
            balloonMap.remove(editor);
        }
    }

    private void removeScrollListener(Editor editor) {
        VisibleAreaListener listener = scrollListenerMap.get(editor);
        if (listener != null) {
            editor.getScrollingModel().removeVisibleAreaListener(listener);
            scrollListenerMap.remove(editor);
        }
    }

    private void hideBalloonForEditor(Editor editor) {
        Balloon balloon = balloonMap.get(editor);
        if (balloon != null) {
            balloon.hide();
            balloonMap.remove(editor);
        }
        Alarm alarm = alarmMap.get(editor);
        if (alarm != null) {
            alarm.cancelAllRequests();
            alarmMap.remove(editor);
        }
        removeScrollListener(editor);
    }

    private void setupAutoHideTimer(Editor editor, Balloon balloon) {
        Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, getAnchorDisposable(editor));
        alarmMap.put(editor, alarm);
        alarm.addRequest(() -> {
            if (!balloon.isDisposed()) {
                balloon.hide();
                balloonMap.remove(editor);
            }
            alarmMap.remove(editor);
        }, SettingsState.getInstance().balloonDuration); // 1000毫秒 = 1秒
    }
}
