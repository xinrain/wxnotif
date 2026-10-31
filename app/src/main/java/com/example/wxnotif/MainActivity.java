package com.example.wxnotif;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 一键授权 + 保活控制台。 */
public class MainActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(0, 0, 0, pad);
        root.addView(status);

        add(root, "① 一键 root 授权（推荐）", v -> new Thread(() -> {
            boolean ok = RootSetup.hasRoot();
            if (ok) RootSetup.applyAll();
            runOnUiThread(() -> {
                toast(ok ? "已用 root 完成配置" : "root 未授权");
                refresh();
            });
        }).start());

        add(root, "② 通知使用权设置页", v -> {
            try {
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            } catch (Throwable t) {
                toast("打不开设置页");
            }
        });

        add(root, "③ 启动保活服务", v -> {
            Intent svc = new Intent(this, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
            else startService(svc);
            toast("保活服务已启动");
        });

        add(root, "④ 打开 MIUI 自启动管理", v -> {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                startActivity(i);
            } catch (Throwable t) {
                toast("不是 MIUI 或打不开");
            }
        });

        add(root, "⑤ 忽略电池优化", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Throwable t) {
                toast("打不开");
            }
        });

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void add(LinearLayout root, String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        root.addView(b);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void refresh() {
        new Thread(() -> {
            boolean root = RootSetup.hasRoot();
            boolean noti = isNotificationAccess();
            runOnUiThread(() -> status.setText(
                    "root：" + (root ? "✅" : "❌")
                    + "\n通知使用权：" + (noti ? "✅" : "❌")
                    + "\n日志目录：/storage/emulated/0/Android/data/com.example.wxnotif/files/WeChatMonitor"));
        }).start();
    }

    private boolean isNotificationAccess() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }
}
