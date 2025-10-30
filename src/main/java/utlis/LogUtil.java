package utlis;

import com.intellij.openapi.diagnostic.Logger;
import editoraction.CursorTrackerService;

public class LogUtil {


    public static void info(String message) {
        // 输出到控制台
        System.out.println("[info] [" + System.currentTimeMillis() + "] " + message);
        // 输出到 IDEA 日志系统
        Logger.getInstance(LogUtil.class).info(message);
    }

    public static void info(String message, Class<?> clazz) {
        // 输出到控制台
        System.out.println("[info] [" + System.currentTimeMillis() + "] " + message);
        // 输出到 IDEA 日志系统 
        Logger.getInstance(clazz).info(message);
    }


    public static void debug(String message) {
        // 输出到 IDEA 日志系统
        Logger.getInstance(LogUtil.class).info(message);
    }

    public static void debug(String message, Class<?> clazz) {
        // 输出到 IDEA 日志系统
        Logger.getInstance(clazz).debug(message);
    }
}