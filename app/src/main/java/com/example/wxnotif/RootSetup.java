package com.example.wxnotif;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/** 用 root 一次性把权限、白名单都配好。 */
public final class RootSetup {

    public static final String PKG = "com.example.wxnotif";
    public static final String LISTENER = PKG + "/.WxListener";

    private RootSetup() {}

    public static boolean hasRoot() {
        String out = exec("id");
        return out != null && out.contains("uid=0");
    }

    /** 通知使用权 + 电池白名单 + 后台运行 + 活跃分组（存储用 App 专属目录，无需文件权限） */
    public static void applyAll() {
        exec("cmd notification allow_listener " + LISTENER);
        exec("dumpsys deviceidle whitelist +" + PKG);
        exec("cmd appops set " + PKG + " RUN_IN_BACKGROUND allow");
        exec("cmd appops set " + PKG + " RUN_ANY_IN_BACKGROUND allow");
        exec("am set-standby-bucket " + PKG + " active");
    }

    /** 在 root 下执行命令，返回 stdout（失败返回 null）。 */
    public static String exec(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            p.waitFor(5, TimeUnit.SECONDS);
            return sb.toString();
        } catch (Throwable e) {
            return null;
        }
    }
}
