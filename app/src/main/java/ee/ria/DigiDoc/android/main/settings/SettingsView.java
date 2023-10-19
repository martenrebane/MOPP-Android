package ee.ria.DigiDoc.android.main.settings;

import static com.jakewharton.rxbinding4.view.RxView.clicks;
import static com.jakewharton.rxbinding4.widget.RxToolbar.navigationClicks;
import static ee.ria.DigiDoc.android.main.settings.util.SettingsUtil.getToolbarViewTitle;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toolbar;

import androidx.coordinatorlayout.widget.CoordinatorLayout;

import ee.ria.DigiDoc.R;
import ee.ria.DigiDoc.android.ApplicationApp;
import ee.ria.DigiDoc.android.main.settings.access.SettingsAccessScreen;
import ee.ria.DigiDoc.android.main.settings.access.SettingsAccessView;
import ee.ria.DigiDoc.android.main.settings.role.SettingsRoleAndAddressScreen;
import ee.ria.DigiDoc.android.main.settings.role.SettingsRoleAndAddressView;
import ee.ria.DigiDoc.android.utils.ViewDisposables;
import ee.ria.DigiDoc.android.utils.navigator.ContentView;
import ee.ria.DigiDoc.android.utils.navigator.Navigator;
import ee.ria.DigiDoc.android.utils.navigator.Transaction;

public final class SettingsView extends CoordinatorLayout implements ContentView  {

    private final Toolbar toolbarView;

    private final Navigator navigator;
    private final SettingsDataStore settingsDataStore;

    private final ViewDisposables disposables;

    private final Button accessCategory;
    private final Button roleAndAddressCategory;
    private final Button defaultSettingsButton;

    public SettingsView(Context context) {
        this(context, null);
    }

    public SettingsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SettingsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inflate(context, R.layout.main_settings, this);
        toolbarView = findViewById(R.id.toolbar);
        navigator = ApplicationApp.component(context).navigator();
        settingsDataStore = ApplicationApp.component(context).settingsDataStore();
        TextView toolbarTitleView = getToolbarViewTitle(toolbarView);
        disposables = new ViewDisposables();

        accessCategory = findViewById(R.id.mainSettingsAccessCategory);
        roleAndAddressCategory = findViewById(R.id.mainSettingsRoleAndAddressCategory);
        defaultSettingsButton = findViewById(R.id.mainSettingsUseDefaultSettings);

        toolbarView.setTitle(R.string.main_settings_title);
        toolbarView.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbarView.setNavigationContentDescription(R.string.back);

        if (toolbarTitleView != null) {
            toolbarTitleView.setContentDescription("\u202F");
        }
    }

    private void resetToDefaultSettings(SettingsDataStore settingsDataStore) {
        SettingsAccessView.resetSettings(getContext(), settingsDataStore);
        SettingsRoleAndAddressView.resetSettings(settingsDataStore);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        disposables.attach();
        disposables.add(navigationClicks(toolbarView).subscribe(o ->
                navigator.execute(Transaction.pop())));
        disposables.add(clicks(accessCategory).subscribe(o ->
                navigator.execute(
                        Transaction.push(SettingsAccessScreen.create()))));
        disposables.add(clicks(roleAndAddressCategory).subscribe(o ->
                navigator.execute(
                        Transaction.push(SettingsRoleAndAddressScreen.create()))));
        disposables.add(clicks(defaultSettingsButton).subscribe(o ->
                resetToDefaultSettings(settingsDataStore)
        ));
    }

    @Override
    public void onDetachedFromWindow() {
        disposables.detach();
        super.onDetachedFromWindow();
    }
}
