package editoraction;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project; 
import com.intellij.openapi.startup.StartupActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PluginStartup implements com.intellij.openapi.startup.ProjectActivity {
    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
       return ApplicationManager.getApplication().getService(CursorTrackerService.class);
    }
}