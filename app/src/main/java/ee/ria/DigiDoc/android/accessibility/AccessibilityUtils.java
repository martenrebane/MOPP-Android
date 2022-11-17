package ee.ria.DigiDoc.android.accessibility;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.material.textfield.TextInputLayout;

import ee.ria.DigiDoc.android.Activity;
import ee.ria.DigiDoc.common.TextUtil;

import static android.content.Context.ACCESSIBILITY_SERVICE;

public class AccessibilityUtils {

    public static boolean isAccessibilityEnabled() {
        Activity activity = (Activity) Activity.getContext().get();
        AccessibilityManager accessibilityManager = (AccessibilityManager) activity.getSystemService(ACCESSIBILITY_SERVICE);
        return accessibilityManager.isEnabled();
    }

    public static void sendAccessibilityEvent(Context context, int eventType, @StringRes int messageResId) {
        sendAccessibilityEvent(context, eventType, context.getString(messageResId));
    }

    public static void interrupt(Context context) {
        AccessibilityManager accessibilityManager = (AccessibilityManager) context.getSystemService(ACCESSIBILITY_SERVICE);
        accessibilityManager.interrupt();
    }

    public static void sendAccessibilityEvent(Context context, int eventType, CharSequence message) {
        AccessibilityManager accessibilityManager = (AccessibilityManager) context.getSystemService(ACCESSIBILITY_SERVICE);
        if (accessibilityManager.isEnabled()) {
            AccessibilityEvent event = AccessibilityEvent.obtain();
            event.setEventType(eventType);
            event.getText().add(message);
            accessibilityManager.sendAccessibilityEvent(event);
        }
    }

    public static void sendAccessibilityEvent(Context context, int eventType, CharSequence... messages) {
        AccessibilityManager accessibilityManager = (AccessibilityManager) context.getSystemService(ACCESSIBILITY_SERVICE);
        if (accessibilityManager.isEnabled()) {
            AccessibilityEvent event = AccessibilityEvent.obtain();
            event.setEventType(eventType);
            event.getText().add(combineMessages(messages));
            accessibilityManager.sendAccessibilityEvent(event);
        }
    }

    public static void sendDelayedAccessibilityEvent(Context context, int eventType, CharSequence message) {
        AccessibilityManager accessibilityManager = (AccessibilityManager) context.getSystemService(ACCESSIBILITY_SERVICE);
        if (accessibilityManager.isEnabled()) {
            AccessibilityEvent event = AccessibilityEvent.obtain();
            event.setEventType(eventType);
            event.getText().add(message);
            accessibilityManager.sendAccessibilityEvent(event);
        }
    }

    public static void setViewAccessibilityPaneTitle(View view, @StringRes int titleResId) {
        view.setAccessibilityPaneTitle((view.getResources().getString(titleResId)).toLowerCase());
    }

    public static void setContentDescription(View view, @Nullable String text) {
        ViewCompat.setAccessibilityDelegate(view, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                String contentDescription = "";
                if (text == null || text.isEmpty()) {
                    if (view instanceof TextView) {
                        contentDescription = ((TextView) view).getText().toString().toLowerCase();
                    } else if (view instanceof TextInputLayout) {
                        contentDescription = view.toString().toLowerCase();
                    }
                } else {
                    contentDescription = text.toLowerCase();
                }
                info.setText(contentDescription);
            }
        });
    }

    public static void disableContentDescription(View view) {
        view.setContentDescription(null);
    }

    public static void disableDoubleTapToActivateFeedback(View view) {
        ViewCompat.setAccessibilityDelegate(view, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(AccessibilityNodeInfoCompat.ACTION_FOCUS);
                info.removeAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK);
            }
        });
    }

    public static void setSingleCharactersContentDescription(EditText editText) {
        ViewCompat.setAccessibilityDelegate(editText, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                StringBuilder editTextAccessibility = new StringBuilder();
                String[] personalCodeTextSplit = editText.getText().toString().split(",");
                for (String nameText : personalCodeTextSplit) {
                    if (TextUtil.isOnlyDigits(nameText)) {
                        editTextAccessibility.append(TextUtil.splitTextAndJoin(nameText, "", " "));
                    } else {
                        editTextAccessibility.append(nameText);
                    }
                }
                info.setText(editTextAccessibility);
                info.setContentDescription(editTextAccessibility);
                host.setContentDescription(editTextAccessibility);
            }
        });
    }

    public static void setEditTextCursorToEnd(EditText editText) {
        editText.post(() -> editText.setSelection(editText.getText().length()));
    }

    private static String combineMessages(CharSequence... messages) {
        StringBuilder combinedMessage = new StringBuilder();
        for (CharSequence message : messages) {
            combinedMessage.append(message).append(", ");
        }
        return combinedMessage.toString();
    }
}
