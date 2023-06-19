package ee.ria.DigiDoc.android.utils;

import android.content.Context;
import android.util.Log;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

import java.util.regex.Matcher;

import ee.ria.DigiDoc.R;
import timber.log.Timber;

public final class ErrorMessageUtil {
    public static String extractLink(String text) {
        Matcher m = Patterns.WEB_URL.matcher(text);
        while (m.find()) {
            return m.group();
        }

        return "";
    }

    public static String removeLink(String text) {
        Matcher m = Patterns.WEB_URL.matcher(text);
        while (m.find()) {
            return text.replace(m.group(),"");
        }

        return text;
    }

    public static void setTextViewError(Context context, @Nullable String error, TextView textView,
                          TextInputLayout textInputLayout, @Nullable EditText editText) {
        if (error != null) {
            Timber.log(Log.DEBUG, "DIGIDOC: Setting TextViewError: " + error);
            System.out.println("DIGIDOC: Setting TextViewError: " + error);
            int errorColor = context.getColor(R.color.design_error);
            textView.setTextColor(errorColor);
            textInputLayout.setError(error);
        } else {
            Timber.log(Log.DEBUG, "DIGIDOC: No TextViewError");
            System.out.println("DIGIDOC: No TextViewError");
            int defaultTextColor = context.getColor(android.R.color.tab_indicator_text);
            textView.setTextColor(defaultTextColor);
            textInputLayout.setError(null);
            if (editText != null) {
                editText.setError(null);
            }
        }
    }
}
