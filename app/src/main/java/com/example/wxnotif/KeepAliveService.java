package com.example.wxnotif;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.service.notification.NotificationListenerService;

/**
 * 前台服务保活：
 *  - 常驻通知，降低被 MIUI 杀掉的概率
 *  - 启动时用 root 把权限/白名单配好
 *  - 每 60 秒 requestRebind 一次，保证通知监听不掉线
 */
public class KeepAliveService extends Service {

    private static final String CHANNEL = "wxnotif_keepalive";
    private static final int NOTI_ID = 1001;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            try {
                NotificationListenerService.requestRebind(
                        new ComponentName(KeepAliveService.this, WxListener.class));
            } catch (Throwable ignored) {
            }
            handler.postDelayed(this, 60_000L);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTI_ID, buildNotification());
        new Thread(RootSetup::applyAll).start();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 5_000L);
        return START_STICKY;
    }

    private Notification buildNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "微信通知记录", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setContentTitle("微信通知记录运行中")
                .setContentText("正在监听微信通知并写入 /storage/emulated/0/WeChatMonitor")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
