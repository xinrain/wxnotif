package com.example.wxnotif;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 微信通知实时记录。
 *
 * 日志目录：/storage/emulated/0/Android/data/com.example.wxnotif/files/WeChatMonitor/
 *   - 当天：wx_yyyy-MM-dd.log（实时追加）
 *   - 隔天：自动压成 wx_yyyy-MM-dd.zip，删除原 .log
 *   - 保留 7 天：超过 7 天的 .zip 自动删除
 *
 * 这是 App 专属外部目录，不需要「所有文件访问」权限，普通文件管理器也不容易直接看到。
 */
public class WxListener extends NotificationListenerService {

    private static final String WX_PKG = "com.tencent.mm";
    private static final String SUB_DIR = "WeChatMonitor";
    private static final int KEEP_DAYS = 7;
    private static final long MAINT_INTERVAL_MS = 6 * 3600_000L; // 6 小时兜底跑一次

    private String lastKey = "";
    private long lastTime = 0;
    private long lastMaint = 0;
    private String lastDay = "";

    @Override
    public void onCreate() {
        super.onCreate();
        maintain();
        lastMaint = System.currentTimeMillis();
        lastDay = day();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !WX_PKG.equals(sbn.getPackageName())) return;

        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return;

        CharSequence t = n.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence x = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (x == null) x = n.extras.getCharSequence(Notification.EXTRA_TEXT);

        String title = t == null ? "" : t.toString().replace('\n', ' ').trim();
        String text = x == null ? "" : x.toString().replace('\n', ' ').trim();

        if (text.isEmpty()) {
            CharSequence[] lines = n.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null && lines.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (CharSequence line : lines) {
                    if (line != null) sb.append(line.toString().replace('\n', ' ')).append(" | ");
                }
                text = sb.toString().trim();
            }
        }

        if (title.isEmpty() && text.isEmpty()) return;

        // 同一条通知 5 秒内重复更新只记一次
        String key = title + "\u0000" + text;
        long now = System.currentTimeMillis();
        if (key.equals(lastKey) && now - lastTime < 5000) return;
        lastKey = key;
        lastTime = now;

        append(now, title, text);

        // 跨天或超过 6 小时，跑一次打包 + 清理
        if (!day().equals(lastDay) || now - lastMaint > MAINT_INTERVAL_MS) {
            lastDay = day();
            lastMaint = now;
            maintain();
        }
    }

    // ---------------- 写日志 ----------------

    private void append(long now, String title, String text) {
        String d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(now));
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(now));
        File f = new File(logDir(), "wx_" + d + ".log");
        try (FileOutputStream out = new FileOutputStream(f, true)) {
            out.write((ts + "\t" + title + "\t" + text + "\n").getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }

    // ---------------- 打包 + 清理 ----------------

    private void maintain() {
        File dir = logDir();
        String today = day();

        // 1) 历史 .log 压成 .zip，成功后删原文件
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                if (name.startsWith("wx_") && name.endsWith(".log")) {
                    String d = name.substring(3, name.length() - 4);
                    if (d.equals(today)) continue;
                    File zip = new File(dir, "wx_" + d + ".zip");
                    if (!zip.exists()) zipFile(f, zip);
                    if (zip.exists() && zip.length() > 0) f.delete();
                }
            }
        }

        // 2) 超过 7 天的 .zip 删除
        long cutoff = System.currentTimeMillis() - KEEP_DAYS * 24L * 3600_000L;
        files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                if (name.startsWith("wx_") && name.endsWith(".zip")) {
                    String d = name.substring(3, name.length() - 4);
                    long t = parseDate(d);
                    if (t > 0 && t < cutoff) f.delete();
                }
            }
        }
    }

    private void zipFile(File src, File dst) {
        try (FileInputStream in = new FileInputStream(src);
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(dst))) {
            out.putNextEntry(new ZipEntry(src.getName()));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.closeEntry();
        } catch (Exception e) {
            if (dst.exists()) dst.delete();
        }
    }

    // ---------------- 工具 ----------------

    private File logDir() {
        // App 专属外部目录：
        // /storage/emulated/0/Android/data/com.example.wxnotif/files/WeChatMonitor
        File root = getExternalFilesDir(null);
        if (root == null) root = getFilesDir();
        File d = new File(root, SUB_DIR);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private String day() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private long parseDate(String d) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d).getTime();
        } catch (Exception e) {
            return 0;
        }
    }
}
