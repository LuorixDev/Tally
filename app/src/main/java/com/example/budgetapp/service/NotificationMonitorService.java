package com.example.budgetapp.service;

import android.app.AlertDialog;
import android.app.Notification;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.view.WindowManager;

import com.example.budgetapp.BackupManager;
import com.example.budgetapp.R;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.util.NotificationRuleManager;
import com.example.budgetapp.util.AppStatusNotifier;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.util.AutoAssetManager;

import java.util.HashMap;
import java.util.Map;

/** Converts user-configured notification patterns into transactions. */
public class NotificationMonitorService extends NotificationListenerService {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Long> recentNotifications = new HashMap<>();

    @Override public void onNotificationPosted(StatusBarNotification notification) {
        if (notification == null || !NotificationRuleManager.isEnabled(this)) return;
        String packageName = notification.getPackageName();
        if (getPackageName().equals(packageName)) return;
        Notification source = notification.getNotification();
        if (source == null || source.extras == null) return;
        CharSequence title = source.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence body = source.extras.getCharSequence(Notification.EXTRA_TEXT);
        String text = ((title == null ? "" : title) + "\n" + (body == null ? "" : body)).trim();
        if (text.isEmpty()) return;

        String signature = packageName + "|" + text;
        long now = System.currentTimeMillis();
        synchronized (recentNotifications) {
            Long previous = recentNotifications.put(signature, now);
            if (previous != null && now - previous < 3000L) return;
            if (recentNotifications.size() > 100) recentNotifications.clear();
        }

        NotificationRuleManager.Match match = NotificationRuleManager.match(this, packageName, text);
        if (match == null) return;
        long lastAutomaticTrigger = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getLong("last_auto_accounting_trigger", 0L);
        if (now - lastAutomaticTrigger < match.rule.delayMs) return;

        if (match.rule.directPost) {
            postTransaction(match.rule, match.amount, text, true);
        } else {
            showConfirmation(match.rule, match.amount, text);
        }
    }

    private void showConfirmation(NotificationRuleManager.Rule rule, double amount, String sourceText) {
        if (!android.provider.Settings.canDrawOverlays(this)) return;
        mainHandler.post(() -> {
            android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null);
            AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
            ((android.widget.TextView) view.findViewById(R.id.tv_dialog_title)).setText(rule.appName + " 通知记账");
            ((android.widget.TextView) view.findViewById(R.id.tv_dialog_message)).setText("识别金额: " + amount + "\n" + sourceText);
            view.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> dialog.dismiss());
            view.findViewById(R.id.btn_dialog_confirm).setOnClickListener(v -> {
                postTransaction(rule, amount, sourceText, false);
                dialog.dismiss();
            });
            if (dialog.getWindow() != null) {
                dialog.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE);
            }
            dialog.show();
        });
    }

    private void postTransaction(NotificationRuleManager.Rule rule, double amount, String sourceText,
                                 boolean directPost) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
            Transaction transaction = new Transaction(System.currentTimeMillis(), rule.type,
                    "通知记账", amount, sourceText);
            int assetId = rule.assetId;
            if (assetId <= 0) assetId = AutoAssetManager.getAppDefaultAsset(this, rule.packageName);
            if (assetId <= 0) assetId = new AssistantConfig(this).getDefaultAssetId();
            transaction.assetId = assetId > 0 ? assetId : 0;
            db.transactionDao().insert(transaction);
            String assetName = null;
            if (transaction.assetId > 0) {
                AssetAccount asset = db.assetAccountDao().getAssetByIdSync(transaction.assetId);
                if (asset != null) {
                    assetName = asset.name;
                    if (asset.type == 0) asset.amount += rule.type == 1 ? amount : -amount;
                    else asset.amount += rule.type == 1 ? -amount : amount;
                    db.assetAccountDao().update(asset);
                }
            }
            getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                    .putLong("last_auto_accounting_trigger", System.currentTimeMillis()).apply();
            BackupManager.triggerAutoUploadIfEnabled(this);
            if (directPost && NotificationRuleManager.isDirectPostNotificationEnabled(this)) {
                AppStatusNotifier.notifyDirectAccounting(this, rule.appName, rule.type, amount, assetName);
            }
        });
    }
}
