package utlis;

import com.intellij.openapi.diagnostic.Logger;

public class LogUtil {


    public static void info(String message) {
        info(message, LogUtil.class);
    }

    public static void info(String message, Class<?> clazz) {
        // 输出到控制台
        System.out.println("[info] [" + System.currentTimeMillis() + "] " + message);
        // 输出到 IDEA 日志系统 
        Logger.getInstance(clazz).info(message);
    }


    public static void debug(String message) {
        debug(message, LogUtil.class);
    }

    public static void debug(String message, Class<?> clazz) {
        // 输出到 IDEA 日志系统
        Logger.getInstance(clazz).debug(message);
    }

    public static void error(String message) {
        error(message, LogUtil.class);
    }

    public static void error(String message, Class<?> clazz) {
        // 输出到 IDEA 日志系统
        Logger.getInstance(clazz).error(message);
    }
}