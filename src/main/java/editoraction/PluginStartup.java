package editoraction;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import state.SettingsState;

import java.awt.Desktop;
import java.net.URI;

public class PluginStartup implements com.intellij.openapi.startup.ProjectActivity {

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        // 预热输入法跟踪服务
        ApplicationManager.getApplication().getService(CursorTrackerService.class);
        // 欢迎通知（持久化开关，重启不重复打扰）
        maybeNotifyWelcome(project);
        return null;
    }

    private void maybeNotifyWelcome(Project project) {
        SettingsState settings;
        try {
            settings = SettingsState.getInstance();
        } catch (Exception e) {
            return;
        }
        if (!settings.showWelcomeNotification) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            Notification notification = new Notification(
                    "key-switch",
                    "欢迎使用 key-switch 智能输入法自动切换工具",
                    "这个工具旨在帮助您更高效地进行编程，不再频繁切换中英文输入状态",
                    NotificationType.INFORMATION
            );

            // 添加操作按钮
            notification.addAction(new AnAction("插件设置") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(
                            e.getProject(),
                            "key-switch" // 必须与plugin.xml 中定义的<name>完全一致
                    );
                }
            });

            notification.addAction(new AnAction("Star支持") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    openUrl("https://github.com/zy1263188600/key-switch", notification);
                }
            });

            notification.addAction(new AnAction("五星好评") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    openUrl("https://plugins.jetbrains.com/plugin/28418-key-switch", notification);
                }
            });

            notification.addAction(new AnAction("不再提示") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    SettingsState.getInstance().showWelcomeNotification = false;
                    notification.expire();
                }
            });

            Notifications.Bus.notify(notification, project);
        });
    }

    private void openUrl(String url, Notification notification) {
        try {
            Desktop.getDesktop().browse(new URI(url));
            notification.expire();
        } catch (Exception ignored) {
        }
    }
}
