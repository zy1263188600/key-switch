import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.awt.Desktop;
import java.net.URI;

public class PluginStartupListener implements com.intellij.openapi.startup.ProjectActivity {
    private static boolean isFirstRun = true;

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        if (isFirstRun) {
            // 在EDT线程中执行UI操作
            ApplicationManager.getApplication().invokeLater(() -> {
                // 创建通知并设置点击监听器
                Notification notification = new Notification("key-switch",
                        "欢迎使用 key-switch 智能输入法自动切换工具",
                        "<html>这个工具旨在帮助您更高效地进行编程，不再频繁切换中英文输入状态" +
                                "<div style='margin-top: px;'>" +
                                "<a href=\"https://github.com/zy1263188600/key-switch\">⭐star支持</a>" +
                                "<span style='margin: 0 px;'>或</span>" +
                                "<a href=\"https://plugins.jetbrains.com/plugin/28418-key-switch\">⭐ 五星好评</a>" +
                                "</div></html>",
                        NotificationType.INFORMATION);

                // 设置通知监听器，处理链接点击
                notification.setListener((notification1, event) -> {
                    if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                        try {
                            String url = event.getDescription();
                            Desktop.getDesktop().browse(new URI(url));
                            // 关闭通知
                            notification1.expire();
                        } catch (Exception e) {
                            // 忽略打开浏览器失败的情况
                        }
                    }
                });

                Notifications.Bus.notify(notification, project);
                isFirstRun = false;
            });
        }
        return null;
    }
}