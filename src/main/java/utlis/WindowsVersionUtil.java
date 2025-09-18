package utlis;

import com.intellij.openapi.util.SystemInfo;

public class WindowsVersionUtil {
    // 判断 Windows 11 
    public static boolean isWindows11() {
        return isWindows() && getWindowsBuildNumber() >= 22000;
    }
 
    // 判断 Windows 10
    public static boolean isWindows10() {
        return isWindows() && getWindowsBuildNumber() >= 10240 && getWindowsBuildNumber() < 22000;
    }
 
    // 基础 Windows 检测 
    private static boolean isWindows() {
        return SystemInfo.isWindows;
    }
 
    // 获取 Windows 构建版本号
    private static int getWindowsBuildNumber() {
        String osVersion = System.getProperty("os.version"); 
        if (osVersion == null || osVersion.isEmpty())  {
            return 0;
        }
        
        String[] versionParts = osVersion.split("\\."); 
        if (versionParts.length  < 3) {
            return 0;
        }
        
        try {
            return Integer.parseInt(versionParts[2]); 
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    public static String getWindowsVersionDetail() {
        if (!isWindows()) {
            return "Not Windows";
        }
        int build = getWindowsBuildNumber();
        if (build >= 22000) {
            return "Windows 11 (Build " + build + ")";
        }
        if (build >= 10240) {
            return "Windows 10 (Build " + build + ")";
        }
        return "Older Windows (Build " + build + ")";
    }

}