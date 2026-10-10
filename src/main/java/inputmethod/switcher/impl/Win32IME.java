package inputmethod.switcher.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Windows 输入法控制核心 (第五代改进版 - 单输入法中英文转换模式专精 - Unsafe强突版)
 * 极致纯净、防回弹的中英模式切换：
 * 1. 零布局污染：完全保留用户唯一的“微软拼音”输入法，绝不引入额外的美式键盘。
 * 2. 自动清理：启动时自动扫描并清退上一次测试遗留在系统的 0x04090409 布局。
 * 3. 终极反射 (Unsafe 强突 JPMS 强封装)：
 *    - 由于 JBR 21 限制了 sun.awt.im 包的反射访问（没有 opens 给 unnamed module），普通 reflection 会抛出 InaccessibleObjectException。
 *    - 利用 sun.misc.Unsafe 物理偏移量直读技术，无视 JPMS 模块保护，强行从 InputContext 中读出 WInputMethod，并读取其私有 context (IMC句柄)。
 *    - 利用 Unsafe 强行改写 Method 对象的 override 标志为 true 强解访问限制，直接在 EDT 线程反射调用底层的 native 方法：
 *      `setOpenStatus(context, true)` 保持输入法开启，以及 `setConversionStatus(context, mode)` 物理切换中英模式。
 * 4. JNA IMM32 兜底防线：如果反射不可达，无缝退回到 JNA 物理层直接操作句柄状态。
 */
public final class Win32IME {
    private static final Logger LOG = Logger.getInstance(Win32IME.class);

    /** 是否已完成初始化 */
    private static boolean initialized = false;

    /** 记录当前的物理中英状态，避免高频重复发送冗余切换指令 */
    private static Boolean currentMode = null;

    private Win32IME() {
    }

    /**
     * 初始化：扫描并清理历史遗留的美式键盘布局
     */
    private static synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;

        try {
            // 扫描并卸载可能残留的美式键盘布局 (0x04090409)
            unloadEnglishLayoutIfExists();
            // 打印 WInputMethod 结构以诊断 JBR 21 的方法和字段
            dumpWInputMethodClassInfo();
        } catch (Exception e) {
            LOG.error("SmartInput: Exception during initialization", e);
        }
    }

    private static void dumpWInputMethodClassInfo() {
        try {
            LOG.info("SmartInput: NODE_LOG - Start dumping WInputMethod class info");
            Class<?> clazz = Class.forName("sun.awt.windows.WInputMethod");

            LOG.info("SmartInput: NODE_LOG --- Fields of sun.awt.windows.WInputMethod ---");
            Class<?> currentClass = clazz;
            while (currentClass != null) {
                LOG.info("SmartInput: NODE_LOG Class: " + currentClass.getName());
                for (Field field : currentClass.getDeclaredFields()) {
                    LOG.info("SmartInput: NODE_LOG   Field: " + field.getName() + " (" + field.getType().getName() + ")");
                }
                currentClass = currentClass.getSuperclass();
            }

            LOG.info("SmartInput: NODE_LOG --- Methods of sun.awt.windows.WInputMethod ---");
            currentClass = clazz;
            while (currentClass != null) {
                LOG.info("SmartInput: NODE_LOG Class: " + currentClass.getName());
                for (Method method : currentClass.getDeclaredMethods()) {
                    StringBuilder params = new StringBuilder();
                    Class<?>[] paramTypes = method.getParameterTypes();
                    for (int i = 0; i < paramTypes.length; i++) {
                        if (i > 0) {
                            params.append(", ");
                        }
                        params.append(paramTypes[i].getName());
                    }
                    LOG.info("SmartInput: NODE_LOG   Method: " + method.getName() + "(" + params + ") returns " + method.getReturnType().getName());
                }
                currentClass = currentClass.getSuperclass();
            }
            LOG.info("SmartInput: NODE_LOG - End dumping WInputMethod class info");
        } catch (Exception e) {
            LOG.error("SmartInput: NODE_LOG - Failed to dump WInputMethod class info", e);
        }
    }

    /**
     * 自动扫描已安装布局列表，若包含之前加载的美式键盘布局，则执行卸载
     */
    private static void unloadEnglishLayoutIfExists() {
        try {
            int count = MyUser32.INSTANCE.GetKeyboardLayoutList(0, null);
            if (count > 0) {
                int pointerSize = Native.POINTER_SIZE;
                Memory memory = new Memory((long) count * pointerSize);
                int filled = MyUser32.INSTANCE.GetKeyboardLayoutList(count, memory);
                for (int i = 0; i < filled; i++) {
                    Pointer hkl = memory.getPointer((long) i * pointerSize);
                    if (hkl != null) {
                        long valL = Pointer.nativeValue(hkl);
                        // 检测美式英语键盘布局 HKL：0x04090409
                        if (valL == 0x04090409L || (valL & 0xFFFFFFFFL) == 0x04090409L) {
                            boolean success = MyUser32.INSTANCE.UnloadKeyboardLayout(hkl);
                            LOG.info("SmartInput: Clean up legacy English keyboard layout " + hkl + ", Unload result=" + success);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("SmartInput: Error scanning/unloading legacy English layout", e);
        }
    }

    /**
     * 纯反射获取 Unsafe 实例 (避免任何编译期依赖)
     */
    private static Object getUnsafe() {
        try {
            Class<?> clazz = Class.forName("sun.misc.Unsafe");
            Field field = clazz.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return field.get(null);
        } catch (Exception e) {
            LOG.error("SmartInput: Failed to get Unsafe", e);
            return null;
        }
    }

    /**
     * 利用 Unsafe 绕过 JPMS 模块校验读取对象的私有引用字段
     */
    private static Object getPrivateFieldViaUnsafe(Object obj, String className, String fieldName) {
        try {
            Object unsafe = getUnsafe();
            if (unsafe == null) {
                return null;
            }
            Class<?> unsafeClass = unsafe.getClass();

            Method objectFieldOffsetMethod = unsafeClass.getMethod("objectFieldOffset", Field.class);
            Method getObjectMethod = unsafeClass.getMethod("getObject", Object.class, long.class);

            Class<?> targetClass = Class.forName(className);
            Field field = targetClass.getDeclaredField(fieldName);

            long offset = (Long) objectFieldOffsetMethod.invoke(unsafe, field);
            return getObjectMethod.invoke(unsafe, obj, offset);
        } catch (Exception e) {
            LOG.error("SmartInput: Unsafe read field failed: " + className + "." + fieldName, e);
        }
        return null;
    }

    /**
     * 利用 Unsafe 绕过 JPMS 模块校验读取对象的私有 long 基本类型字段
     */
    private static Long getPrivateLongFieldViaUnsafe(Object obj, String className, String fieldName) {
        try {
            Object unsafe = getUnsafe();
            if (unsafe == null) {
                return null;
            }
            Class<?> unsafeClass = unsafe.getClass();

            Method objectFieldOffsetMethod = unsafeClass.getMethod("objectFieldOffset", Field.class);
            Method getLongMethod = unsafeClass.getMethod("getLong", Object.class, long.class);

            Class<?> targetClass = Class.forName(className);
            Field field = targetClass.getDeclaredField(fieldName);

            long offset = (Long) objectFieldOffsetMethod.invoke(unsafe, field);
            return (Long) getLongMethod.invoke(unsafe, obj, offset);
        } catch (Exception e) {
            LOG.error("SmartInput: Unsafe read long field failed: " + className + "." + fieldName, e);
        }
        return null;
    }

    /**
     * 利用 Unsafe 绕过 JPMS 模块校验读取对象的私有 int 基本类型字段
     */
    private static Integer getPrivateIntFieldViaUnsafe(Object obj, String className, String fieldName) {
        try {
            Object unsafe = getUnsafe();
            if (unsafe == null) {
                return null;
            }
            Class<?> unsafeClass = unsafe.getClass();

            Method objectFieldOffsetMethod = unsafeClass.getMethod("objectFieldOffset", Field.class);
            Method getIntMethod = unsafeClass.getMethod("getInt", Object.class, long.class);

            Class<?> targetClass = Class.forName(className);
            Field field = targetClass.getDeclaredField(fieldName);

            long offset = (Long) objectFieldOffsetMethod.invoke(unsafe, field);
            return (Integer) getIntMethod.invoke(unsafe, obj, offset);
        } catch (Exception e) {
            LOG.error("SmartInput: Unsafe read int field failed: " + className + "." + fieldName, e);
        }
        return null;
    }

    /**
     * 利用 Unsafe 物理强写私有 boolean 字段
     */
    private static void writePrivateBooleanViaUnsafe(Object obj, String className, String fieldName, boolean value) {
        try {
            Object unsafe = getUnsafe();
            if (unsafe == null) {
                return;
            }
            Class<?> unsafeClass = unsafe.getClass();

            Method objectFieldOffsetMethod = unsafeClass.getMethod("objectFieldOffset", Field.class);
            Method putBooleanMethod = unsafeClass.getMethod("putBoolean", Object.class, long.class, boolean.class);

            Class<?> targetClass = Class.forName(className);
            Field field = targetClass.getDeclaredField(fieldName);

            long offset = (Long) objectFieldOffsetMethod.invoke(unsafe, field);
            putBooleanMethod.invoke(unsafe, obj, offset, value);
            LOG.info("SmartInput: NODE_LOG - Overwrote " + className + "." + fieldName + " to " + value + " via Unsafe");
        } catch (Exception e) {
            LOG.error("SmartInput: Unsafe write boolean field failed: " + className + "." + fieldName, e);
        }
    }

    /**
     * 利用 Unsafe 物理强写私有 int 字段
     */
    private static void writePrivateIntViaUnsafe(Object obj, String className, String fieldName, int value) {
        try {
            Object unsafe = getUnsafe();
            if (unsafe == null) {
                return;
            }
            Class<?> unsafeClass = unsafe.getClass();

            Method objectFieldOffsetMethod = unsafeClass.getMethod("objectFieldOffset", Field.class);
            Method putIntMethod = unsafeClass.getMethod("putInt", Object.class, long.class, int.class);

            Class<?> targetClass = Class.forName(className);
            Field field = targetClass.getDeclaredField(fieldName);

            long offset = (Long) objectFieldOffsetMethod.invoke(unsafe, field);
            putIntMethod.invoke(unsafe, obj, offset, value);
            LOG.info("SmartInput: NODE_LOG - Overwrote " + className + "." + fieldName + " to " + value + " via Unsafe");
        } catch (Exception e) {
            LOG.error("SmartInput: Unsafe write int field failed: " + className + "." + fieldName, e);
        }
    }

    /**
     * 利用 Unsafe 直接将 AccessibleObject.override 设为 true 强行破解 JPMS 模块访问限制
     */
    private static boolean makeMethodAccessibleViaUnsafe(Method method) {
        try {
            Object unsafe = getUnsafe();
            if (unsafe == null) {
                return false;
            }
            Class<?> unsafeClass = unsafe.getClass();

            Field overrideField = java.lang.reflect.AccessibleObject.class.getDeclaredField("override");
            Method objectFieldOffsetMethod = unsafeClass.getMethod("objectFieldOffset", Field.class);
            Method putBooleanMethod = unsafeClass.getMethod("putBoolean", Object.class, long.class, boolean.class);

            long offset = (Long) objectFieldOffsetMethod.invoke(unsafe, overrideField);
            putBooleanMethod.invoke(unsafe, method, offset, true);
            return true;
        } catch (Exception e) {
            LOG.error("SmartInput: Unsafe override method bypass failed", e);
        }
        return false;
    }

    /**
     * 通过 AWT 重量级 Peer 物理穿透获取真实的 Windows HWND 句柄
     */
    private static HWND getHWndFromComponent(Component component) {
        if (component == null) {
            return null;
        }
        try {
            Component cur = component;
            Object peer = null;
            // 向上搜寻拥有重量级 Peer 的父级组件 (跳过 NullComponentPeer 占位组件)
            while (cur != null) {
                Object p = getPrivateFieldViaUnsafe(cur, "java.awt.Component", "peer");
                if (p != null && !p.getClass().getName().contains("NullComponentPeer")) {
                    peer = p;
                    break;
                }
                cur = cur.getParent();
            }

            if (peer == null) {
                LOG.warn("SmartInput: Physical Peer not found for component chain of " + component.getClass().getName());
                return null;
            }

            String peerClassName = peer.getClass().getName();
            if (peerClassName.startsWith("sun.awt.windows.")) {
                // 通过 Unsafe 强制读取 WComponentPeer 内部的 long 类型私有字段 hwnd
                Long hwndLong = getPrivateLongFieldViaUnsafe(peer, "sun.awt.windows.WComponentPeer", "hwnd");
                long hwndValue = hwndLong == null ? 0L : hwndLong;
                if (hwndValue != 0L) {
                    LOG.info("SmartInput: NODE_LOG - Found physical HWND=0x" + Long.toHexString(hwndValue)
                            + " for component=" + component.getClass().getName() + " via peer=" + peerClassName);
                    return new HWND(new Pointer(hwndValue));
                }
            } else {
                LOG.warn("SmartInput: Found peer for component, but it's not a Windows Component Peer: " + peerClassName);
            }
        } catch (Exception e) {
            LOG.error("SmartInput: Failed to get physical HWND from component peer", e);
        }
        return null;
    }

    /**
     * 通过 Unsafe 强行获取 JVM 内部 WInputMethod 实例并调用 Native 方法
     */
    private static boolean tryReflectiveSetConversion(Component component, boolean chinese) {
        if (component == null) {
            LOG.info("SmartInput: tryReflectiveSetConversion - component is null");
            return false;
        }
        try {
            java.awt.im.InputContext inputContext = component.getInputContext();
            LOG.info("SmartInput: tryReflectiveSetConversion - component=" + component.getClass().getName() + ", inputContext=" + inputContext);

            if (inputContext == null) {
                java.awt.Window window = SwingUtilities.getWindowAncestor(component);
                LOG.info("SmartInput: tryReflectiveSetConversion - component inputContext is null, searching window ancestor: " + window);
                if (window != null) {
                    inputContext = window.getInputContext();
                    LOG.info("SmartInput: tryReflectiveSetConversion - window inputContext=" + inputContext);
                }
            }

            if (inputContext == null) {
                LOG.warn("SmartInput: tryReflectiveSetConversion - inputContext is still null");
                return false;
            }

            // 1. 利用 Unsafe 绕过 sun.awt.im 的强封装，直接拿取 inputMethod 实例
            Object wInputMethod = getPrivateFieldViaUnsafe(inputContext, "sun.awt.im.InputContext", "inputMethod");
            if (wInputMethod == null) {
                LOG.info("SmartInput: wInputMethod is null, trying to trigger selectInputMethod to initialize it");
                try {
                    inputContext.selectInputMethod(java.util.Locale.getDefault());
                } catch (Exception e) {
                    LOG.error("SmartInput: Failed to invoke selectInputMethod", e);
                }
                wInputMethod = getPrivateFieldViaUnsafe(inputContext, "sun.awt.im.InputContext", "inputMethod");
            }

            if (wInputMethod == null) {
                LOG.warn("SmartInput: tryReflectiveSetConversion - wInputMethod is still null after initialization attempt");
                return false;
            }

            LOG.info("SmartInput: tryReflectiveSetConversion - wInputMethod class=" + wInputMethod.getClass().getName());

            // 确保是 WInputMethod
            if (!wInputMethod.getClass().getName().contains("WInputMethod")) {
                LOG.warn("SmartInput: tryReflectiveSetConversion - wInputMethod is not WInputMethod");
                return false;
            }

            // 2. 利用 Unsafe 直接读出其内部 of native IMC 句柄 (context) - 在 JBR 21 中为 int 类型
            Integer contextIntObj = getPrivateIntFieldViaUnsafe(wInputMethod, "sun.awt.windows.WInputMethod", "context");
            int contextInt = contextIntObj == null ? 0 : contextIntObj;
            LOG.info("SmartInput: tryReflectiveSetConversion - contextInt=0x" + Integer.toHexString(contextInt));
            if (contextInt == 0) {
                LOG.warn("SmartInput: Reflective IMC context handle is 0");
                return false;
            }

            // 【核心修复】：物理强写 WInputMethod 内存中的 open (boolean) 和 cmode (int) 状态缓存，消除 JBR 状态回弹
            int targetMode = chinese ? MyImm32.IME_CMODE_NATIVE : MyImm32.IME_CMODE_ALPHANUMERIC;
            writePrivateBooleanViaUnsafe(wInputMethod, "sun.awt.windows.WInputMethod", "open", chinese);
            writePrivateIntViaUnsafe(wInputMethod, "sun.awt.windows.WInputMethod", "cmode", targetMode);

            // 3. 反射获取 setOpenStatus(int, boolean) 方法，并使用 Unsafe 解锁访问 (在 JBR 21 中第一个参数是 int 类型的 context)
            Method setOpenMethod = wInputMethod.getClass().getDeclaredMethod("setOpenStatus", int.class, boolean.class);
            try {
                setOpenMethod.setAccessible(true);
            } catch (Exception e) {
                makeMethodAccessibleViaUnsafe(setOpenMethod);
            }
            setOpenMethod.invoke(wInputMethod, contextInt, chinese);
            LOG.info("SmartInput: tryReflectiveSetConversion - setOpenStatus(" + contextInt + ", " + chinese + ") invoked");

            // 4. 反射获取 setConversionStatus(int, int) 方法，并使用 Unsafe 解锁访问 (在 JBR 21 中第一个参数是 int 类型的 context)
            Method setConvMethod = wInputMethod.getClass().getDeclaredMethod("setConversionStatus", int.class, int.class);
            try {
                setConvMethod.setAccessible(true);
            } catch (Exception e) {
                makeMethodAccessibleViaUnsafe(setConvMethod);
            }

            setConvMethod.invoke(wInputMethod, contextInt, targetMode);

            LOG.info("SmartInput: NODE_LOG - Reflectively forced WInputMethod state to mode=0x" + Integer.toHexString(targetMode) + " successfully!");
            return true;
        } catch (Throwable e) {
            LOG.error("SmartInput: NODE_LOG - WInputMethod reflective chain failed", e);
        }
        return false;
    }

    /**
     * 切换输入法模式入口
     *
     * @param component 当前聚焦的 AWT 组件
     * @param chinese   true 为中文模式，false 为英文模式
     */
    private static void executeSetChineseMode(Component component, boolean chinese) {
        try {
            // 【第一重保障】：直接使用 Java AWT 的标准公开 API 切换 Composition 状态
            try {
                java.awt.im.InputContext inputContext = component != null ? component.getInputContext() : null;
                if (inputContext == null && component != null) {
                    java.awt.Window ancestor = SwingUtilities.getWindowAncestor(component);
                    if (ancestor != null) {
                        inputContext = ancestor.getInputContext();
                    }
                }
                if (inputContext != null) {
                    inputContext.setCompositionEnabled(chinese);
                    LOG.info("SmartInput: NODE_LOG - setCompositionEnabled(" + chinese + ") invoked via standard AWT API");
                }
            } catch (Throwable t) {
                LOG.warn("SmartInput: Standard AWT setCompositionEnabled failed", t);
            }

            // 【第二重保障】：Unsafe 反射强控 WInputMethod Native 方法
            tryReflectiveSetConversion(component, chinese);

            // 物理 HWND 提取 (已过滤 NullComponentPeer)
            HWND peerHwnd = getHWndFromComponent(component);
            HWND focusHwnd = MyUser32.INSTANCE.GetFocus();
            HWND fgHwnd = MyUser32.INSTANCE.GetForegroundWindow();
            HWND hwnd = peerHwnd != null ? peerHwnd : (focusHwnd != null ? focusHwnd : fgHwnd);

            if (hwnd == null) {
                LOG.warn("SmartInput: HWND is null in physical messenger, skipping SendMessage");
                return;
            }

            // 【第三重保障】：发送 Windows WM_IME_CONTROL 物理消息强控穿透
            int msg = 0x0283; // WM_IME_CONTROL
            HWND defaultImeWnd = MyImm32.INSTANCE.ImmGetDefaultIMEWnd(hwnd);
            LOG.info("SmartInput: Physical control - HWND=" + hwnd + " (peerHwnd=" + peerHwnd + ", focusHwnd=" + focusHwnd + "), DefaultIMEWnd=" + defaultImeWnd);

            if (defaultImeWnd != null && defaultImeWnd.getPointer() != null && Pointer.nativeValue(defaultImeWnd.getPointer()) != 0L) {
                if (chinese) {
                    int r1 = MyUser32.INSTANCE.SendMessageW(defaultImeWnd, msg, 0x0006, 1); // IMC_SETOPENSTATUS, 1 (Open)
                    int r2 = MyUser32.INSTANCE.SendMessageW(defaultImeWnd, msg, 0x0002, 0x0001); // IMC_SETCONVERSIONMODE, IME_CMODE_NATIVE (Chinese)
                    LOG.info("SmartInput: NODE_LOG - Sent Chinese CMD to DefaultIMEWnd, resOpen=" + r1 + ", resConv=" + r2);
                } else {
                    int r1 = MyUser32.INSTANCE.SendMessageW(defaultImeWnd, msg, 0x0006, 0); // IMC_SETOPENSTATUS, 0 (Close)
                    int r2 = MyUser32.INSTANCE.SendMessageW(defaultImeWnd, msg, 0x0002, 0x0000); // IMC_SETCONVERSIONMODE, IME_CMODE_ALPHANUMERIC
                    LOG.info("SmartInput: NODE_LOG - Sent English CMD to DefaultIMEWnd, resOpen=" + r1 + ", resConv=" + r2);
                }
            }

            // 同时也发送给窗口本身 (DefWindowProc 转发)
            if (chinese) {
                MyUser32.INSTANCE.SendMessageW(hwnd, msg, 0x0006, 1);
                MyUser32.INSTANCE.SendMessageW(hwnd, msg, 0x0002, 0x0001);
            } else {
                MyUser32.INSTANCE.SendMessageW(hwnd, msg, 0x0006, 0);
                MyUser32.INSTANCE.SendMessageW(hwnd, msg, 0x0002, 0x0000);
            }

            // 【第四重保障】：JNA IMM32 直接操作句柄
            Pointer himc = MyImm32.INSTANCE.ImmGetContext(hwnd);
            if (himc != null) {
                boolean openStatus = MyImm32.INSTANCE.ImmGetOpenStatus(himc);
                int[] conversion = new int[1];
                int[] sentence = new int[1];
                MyImm32.INSTANCE.ImmGetConversionStatus(himc, conversion, sentence);
                int oldConversion = conversion[0];
                int currentSentence = sentence[0];

                LOG.info("SmartInput: NODE_LOG [BEFORE Fallback IMM32] HWND=" + hwnd + ", OpenStatus=" + openStatus
                        + ", ConversionStatus=0x" + Integer.toHexString(oldConversion) + ", Sentence=0x" + Integer.toHexString(currentSentence));

                if (!openStatus) {
                    MyImm32.INSTANCE.ImmSetOpenStatus(himc, true);
                }

                if (chinese) {
                    if ((oldConversion & MyImm32.IME_CMODE_NATIVE) == 0) {
                        int newConversion = oldConversion | MyImm32.IME_CMODE_NATIVE;
                        boolean setRes = MyImm32.INSTANCE.ImmSetConversionStatus(himc, newConversion, currentSentence);
                        LOG.info("SmartInput: NODE_LOG - ImmSetConversionStatus NATIVE result=" + setRes);
                    }
                } else {
                    if ((oldConversion & MyImm32.IME_CMODE_NATIVE) != 0) {
                        int newConversion = oldConversion & (~MyImm32.IME_CMODE_NATIVE);
                        boolean setRes = MyImm32.INSTANCE.ImmSetConversionStatus(himc, newConversion, currentSentence);
                        LOG.info("SmartInput: NODE_LOG - ImmSetConversionStatus ALPHANUMERIC result=" + setRes);
                    }
                }

                int[] verifyConversion = new int[1];
                MyImm32.INSTANCE.ImmGetConversionStatus(himc, verifyConversion, new int[1]);
                boolean verifyOpen = MyImm32.INSTANCE.ImmGetOpenStatus(himc);
                LOG.info("SmartInput: NODE_LOG [AFTER Fallback IMM32] HWND=" + hwnd + ", OpenStatus=" + verifyOpen
                        + ", ConversionStatus=0x" + Integer.toHexString(verifyConversion[0]));

                MyImm32.INSTANCE.ImmReleaseContext(hwnd, himc);
            } else {
                LOG.warn("SmartInput: IMM32 Context is null for HWND=" + hwnd);
            }

        } catch (Exception e) {
            LOG.error("SmartInput: Error inside executeSetChineseMode", e);
        }
    }

    /**
     * 获取输入法的 Windows 物理状态
     */
    public static boolean getPhysicalChineseMode(Component component) {
        try {
            HWND peerHwnd = getHWndFromComponent(component);
            HWND focusHwnd = MyUser32.INSTANCE.GetFocus();
            HWND fgHwnd = MyUser32.INSTANCE.GetForegroundWindow();
            HWND hwnd = peerHwnd != null ? peerHwnd : (focusHwnd != null ? focusHwnd : fgHwnd);
            if (hwnd == null) {
                return false;
            }

            Pointer himc = MyImm32.INSTANCE.ImmGetContext(hwnd);
            if (himc != null) {
                boolean openStatus = MyImm32.INSTANCE.ImmGetOpenStatus(himc);
                int[] conversion = new int[1];
                MyImm32.INSTANCE.ImmGetConversionStatus(himc, conversion, new int[1]);
                MyImm32.INSTANCE.ImmReleaseContext(hwnd, himc);

                boolean isNative = (conversion[0] & MyImm32.IME_CMODE_NATIVE) != 0;
                return openStatus && isNative;
            }
        } catch (Exception e) {
            LOG.error("SmartInput: Error getting physical Chinese mode", e);
        }
        return false;
    }

    /**
     * 切换输入法模式入口
     *
     * @param component 当前聚焦的 AWT 组件
     * @param chinese   true 为中文模式，false 为英文模式
     */
    public static void setChineseMode(Component component, boolean chinese) {
        setChineseMode(component, chinese, false);
    }

    /**
     * 切换输入法模式入口
     *
     * @param component 当前聚焦的 AWT 组件
     * @param chinese   true 为中文模式，false 为英文模式
     * @param force     是否跳过去重强制执行
     */
    public static void setChineseMode(Component component, boolean chinese, boolean force) {
        ensureInitialized();

        // 状态去重判定：如果当前物理状态已经和目标状态一致，直接拦截，防止高频冗余消息
        if (!force && currentMode != null && currentMode == chinese) {
            return;
        }
        // 更新当前状态
        currentMode = chinese;

        String componentName = component != null ? component.getClass().getName() : "null";
        LOG.info("SmartInput: Requesting setChineseMode(" + chinese + ") for component=" + componentName + " (async)");

        // 立即执行一次
        final Component target = component;
        final boolean targetChinese = chinese;
        SwingUtilities.invokeLater(() -> executeSetChineseMode(target, targetChinese));

        // 100ms 后再次强制校正执行一次，防止被 AWT native 焦点转移消息流覆盖
        javax.swing.Timer timer = new javax.swing.Timer(100, e -> executeSetChineseMode(target, targetChinese));
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * 重置状态缓存 (允许在配置改变等场景下清空缓存)
     */
    public static void resetState() {
        currentMode = null;
    }
}
