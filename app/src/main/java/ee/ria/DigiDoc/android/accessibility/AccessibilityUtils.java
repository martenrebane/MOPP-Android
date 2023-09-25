package ee.ria.DigiDoc.android.accessibility;

import static android.content.Context.ACCESSIBILITY_SERVICE;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.material.textfield.TextInputLayout;

import net.cachapa.expandablelayout.ExpandableLayout;

import ee.ria.DigiDoc.android.Activity;
import ee.ria.DigiDoc.common.TextUtil;

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
            AccessibilityEvent event = getAccessibilityEvent();
            event.setEventType(eventType);
            event.getText().add(message);
            accessibilityManager.sendAccessibilityEvent(event);
        }
    }

    public static void sendAccessibilityEvent(Context context, int eventType, CharSequence... messages) {
        AccessibilityManager accessibilityManager = (AccessibilityManager) context.getSystemService(ACCESSIBILITY_SERVICE);
        if (accessibilityManager.isEnabled()) {
            AccessibilityEvent event = getAccessibilityEvent();
            event.setEventType(eventType);
            event.getText().add(combineMessages(messages));
            accessibilityManager.sendAccessibilityEvent(event);
        }
    }

    public static void sendDelayedAccessibilityEvent(Context context, int eventType, CharSequence message) {
        AccessibilityManager accessibilityManager = (AccessibilityManager) context.getSystemService(ACCESSIBILITY_SERVICE);
        if (accessibilityManager.isEnabled()) {
            AccessibilityEvent event = getAccessibilityEvent();
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
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(AccessibilityNodeInfoCompat.ACTION_FOCUS);
                info.removeAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK);
            }
        });
    }

    public static void setSingleCharactersContentDescription(TextView textView) {
        ViewCompat.setAccessibilityDelegate(textView, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                StringBuilder textViewAccessibility = new StringBuilder();
                String[] personalCodeTextSplit = textView.getText().toString().split(",");
                for (String nameText : personalCodeTextSplit) {
                    if (TextUtil.isOnlyDigits(nameText)) {
                        textViewAccessibility.append(TextUtil.splitTextAndJoin(nameText, "", " "));
                    } else {
                        textViewAccessibility.append(nameText);
                    }
                }
                info.setText(textViewAccessibility);
                info.setContentDescription(textViewAccessibility);
                host.setContentDescription(textViewAccessibility);
            }
        });
    }

    public static String getTextAsSingleCharacters(String text) {
        return TextUtil.splitTextAndJoin(text, "", " ");
    }

    public static void setEditTextCursorToEnd(EditText editText) {
        editText.post(() -> editText.setSelection(editText.getText().length()));
    }

    public static boolean isLargeFontEnabled(Resources resources) {
        Configuration configuration = resources.getConfiguration();
        return configuration.fontScale > 1;
    }

    public static boolean isSmallFontEnabled(Resources resources) {
        Configuration configuration = resources.getConfiguration();
        return configuration.fontScale < 1;
    }
    
    public static void setCustomClickAccessibilityFeedBack(TextView titleView, ExpandableLayout containerView) {
        ViewCompat.setAccessibilityDelegate(titleView, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                String message;
                if (containerView.isExpanded()) {
                    message = "deactivate";
                } else {
                    message = "activate";
                }
                AccessibilityNodeInfoCompat.AccessibilityActionCompat customClick = new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK, message);
                info.addAction(customClick);
            }
        });
    }

    private static String combineMessages(CharSequence... messages) {
        StringBuilder combinedMessage = new StringBuilder();
        for (CharSequence message : messages) {
            combinedMessage.append(message).append(", ");
        }
        return combinedMessage.toString();
    }

    private static AccessibilityEvent getAccessibilityEvent() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return new AccessibilityEvent();
        } else {
            return AccessibilityEvent.obtain();
        }
    }
}
