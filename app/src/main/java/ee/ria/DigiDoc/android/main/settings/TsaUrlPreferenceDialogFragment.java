package ee.ria.DigiDoc.android.main.settings;


import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.accessibility.AccessibilityEvent.TYPE_ANNOUNCEMENT;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.CheckBox;

import androidx.appcompat.widget.AppCompatEditText;
import androidx.preference.EditTextPreferenceDialogFragmentCompat;

import ee.ria.DigiDoc.R;
import ee.ria.DigiDoc.android.Application;
import ee.ria.DigiDoc.android.accessibility.AccessibilityUtils;
import ee.ria.DigiDoc.android.utils.SecureUtil;
import ee.ria.DigiDoc.android.utils.TextUtil;
import ee.ria.DigiDoc.android.utils.display.DisplayUtil;
import ee.ria.DigiDoc.configuration.ConfigurationProvider;

public class TsaUrlPreferenceDialogFragment extends EditTextPreferenceDialogFragmentCompat {

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        TsaUrlPreference tsaUrlPreference = getTsaUrlPreference();
        if (tsaUrlPreference != null) {
            ConfigurationProvider configurationProvider = ((Application) getContext().getApplicationContext()).getConfigurationProvider();
            CheckBox checkBox = tsaUrlPreference.getCheckBox();

            AppCompatEditText appCompatEditText = TextUtil.getTextView(view);

            tsaUrlPreference.setOnBindEditTextListener(editText -> {
                checkBox.setChecked(false);
                editText.setText(tsaUrlPreference.getText());
            });

            if (appCompatEditText != null) {
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    appCompatEditText.setEnabled(!isChecked);
                    if (isChecked) {
                        disableTextViewOnChecked(appCompatEditText, configurationProvider);
                    }
                });

                checkBox.setChecked(TextUtils.isEmpty(tsaUrlPreference.getText()));

                SettingsView.setTsaCertificateViewVisibleValue(!checkBox.isChecked());

                ViewGroup parent = ((ViewGroup) appCompatEditText.getParent());
                View oldCheckBox = appCompatEditText.findViewById(checkBox.getId());
                if (oldCheckBox != null) {
                    parent.removeView(oldCheckBox);
                }
                ViewParent oldParent = checkBox.getParent();
                if (parent != oldParent) {
                    if (oldParent != null) {
                        ((ViewGroup) oldParent).removeView(checkBox);
                    }
                    parent.addView(checkBox, ViewGroup.LayoutParams.MATCH_PARENT,
                            WRAP_CONTENT);
                }
            }
        }
    }

    private void disableTextViewOnChecked(AppCompatEditText appCompatEditText,
                                          ConfigurationProvider configurationProvider) {
        appCompatEditText.setText(null);
        appCompatEditText.setHint(configurationProvider.getTsaUrl());
        appCompatEditText.clearFocus();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = DisplayUtil.setCustomDialogSettings(super.onCreateDialog(savedInstanceState));
        SecureUtil.markAsSecure(getContext(), dialog.getWindow());
        return dialog;
    }

    private TsaUrlPreference getTsaUrlPreference() {
        return (TsaUrlPreference) this.getPreference();
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (!positiveResult && getContext() != null) {
            AccessibilityUtils.sendAccessibilityEvent(getContext(), TYPE_ANNOUNCEMENT, R.string.setting_value_change_cancelled);
        }
    }
}
