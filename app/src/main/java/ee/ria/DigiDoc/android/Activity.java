package ee.ria.DigiDoc.android;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.util.concurrent.Callable;

import javax.inject.Inject;
import javax.inject.Singleton;

import ee.ria.DigiDoc.BuildConfig;
import ee.ria.DigiDoc.R;
import ee.ria.DigiDoc.android.main.home.HomeScreen;
import ee.ria.DigiDoc.android.main.sharing.SharingScreen;
import ee.ria.DigiDoc.android.signature.create.SignatureCreateScreen;
import ee.ria.DigiDoc.android.utils.navigator.Navigator;
import ee.ria.DigiDoc.android.utils.navigator.Screen;
import ee.ria.DigiDoc.sign.SignedContainer;
import timber.log.Timber;

public final class Activity extends AppCompatActivity {

    private Navigator navigator;
    private RootScreenFactory rootScreenFactory;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.Theme_Application);
        setTitle(""); // ACCESSIBILITY: prevents application name read during each activity launch
        super.onCreate(savedInstanceState);

        if (getIntent().getDataString() != null && getIntent().getData() != null && getIntent().getData().getAuthority() != null && (!getIntent().getData().getAuthority().equals("com.android.providers.downloads.documents") && getIntent().getData().getAuthority().equals("com.google.android.apps.docs.storage.legacy")) && SignedContainer.isContainer(new File(getFileName()))) {
            try {
                if (getComponentName().equals(getAllTypesComponentName())) {
                    finish();
                    Intent newIntent = getIntent();
                    newIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    newIntent.setComponent(null);
                    disableOpenAllFileTypes();
                    startActivity(newIntent);
                }
            } catch (RuntimeException ex) {
                Timber.e(ex, "Failed to open file");
                throw new RuntimeException("Unable to open file", ex);
            }
        }

//        startService(new Intent(getBaseContext(), TaskService.class));

        if (!BuildConfig.BUILD_TYPE.contentEquals("debug")) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        }

        Intent intent = getIntent();

        if ((Intent.ACTION_SEND.equals(intent.getAction()) || Intent.ACTION_VIEW.equals(intent.getAction())) && intent.getType() != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(intent.getAction());
            handleIncomingFiles(intent);
        } else if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
            getIntent().setAction(Intent.ACTION_MAIN);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            restartAppWithIntent(intent);
        } else if (Intent.ACTION_GET_CONTENT.equals(intent.getAction())) {
            rootScreenFactory.intent(intent);
        }
        else {
          // Avoid blank screen on language change
          if (savedInstanceState != null) {
              restartAppWithIntent(intent);
          }
          rootScreenFactory.intent(intent);
        }

        initializeApplicationFileTypesAssociation();

        navigator.onCreate(this, findViewById(android.R.id.content), savedInstanceState);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public void restartAppWithIntent(Intent intent) {
        finish();
        startActivity(intent);
        overridePendingTransition (0, 0);
    }

    private void handleIncomingFiles(Intent intent) {
        try {
            intent.setDataAndType(intent.getData(), "*/*");
            rootScreenFactory.intent(intent);
        } catch (ActivityNotFoundException e) {
            Timber.e(e, "Handling incoming file intent");
        }
    }

    private void initializeApplicationFileTypesAssociation() {
        if (getIntent().getAction().equals(Intent.ACTION_VIEW)) {
            disableOpenAllFileTypes();
        } else {
            enableOpenAllFileTypes();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        Application.ApplicationComponent component = Application.component(newBase);
        navigator = component.navigator();
        rootScreenFactory = component.rootScreenFactory();
        super.attachBaseContext(component.localeService().attachBaseContext(newBase));
    }

    @Override
    public void onBackPressed() {
        if (!navigator.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isOpenAllTypesEnabled() && !isOpenAllTypesComponentEnabled()) {
            PackageManager pm = getApplicationContext().getPackageManager();
            pm.setComponentEnabledSetting(getAllTypesComponentName(), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(getOpenPDFComponentName(), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        } else {
            enableOpenAllFileTypesIfNeeded();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (isOpenAllTypesEnabled() && !isOpenAllTypesComponentEnabled()) {
            PackageManager pm = getApplicationContext().getPackageManager();
            pm.setComponentEnabledSetting(getAllTypesComponentName(), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(getCustomTypesComponentName(), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(getOpenPDFComponentName(), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            finishAndRemoveTask();

        } else {
            enableOpenAllFileTypesIfNeeded();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        enableOpenAllFileTypesIfNeeded();
    }

    private void enableOpenAllFileTypesIfNeeded() {
        try {
            if (isOpenAllTypesEnabled() && isContainerView()) {
                disableOpenAllFileTypes();
            } else {
                enableOpenAllFileTypes();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (isOpenAllTypesEnabled() && !isOpenAllTypesComponentEnabled()) {
            PackageManager pm = getApplicationContext().getPackageManager();
            pm.setComponentEnabledSetting(getAllTypesComponentName(), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(getCustomTypesComponentName(), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(getOpenPDFComponentName(), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        }
        finish();
    }

    private boolean isContainerView() {
        return getIntent().getAction() != null && getIntent().getAction().equals(Intent.ACTION_VIEW);
    }

    private String getFileName() {
        Uri returnUri = getIntent().getData();
        Cursor returnCursor = getContentResolver().query(returnUri, null, null, null, null);

        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        returnCursor.moveToFirst();

        return returnCursor.getString(nameIndex);
    }

    private boolean isOpenAllTypesComponentEnabled() {

        PackageManager pm = getApplicationContext().getPackageManager();

        ComponentName componentName = getAllTypesComponentName();
        int componentEnabledSetting = pm.getComponentEnabledSetting(componentName);

        System.out.println(componentEnabledSetting);

        switch (componentEnabledSetting) {
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
                return true;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
            default:
                return false;

        }
    }

    private ComponentName getAllTypesComponentName() {
        return new ComponentName(getPackageName(), getClass().getName() + ".OPEN_ALL_FILE_TYPES");
    }

    private ComponentName getCustomTypesComponentName() {
        return new ComponentName(getPackageName(), getClass().getName() + ".OPEN_CUSTOM_TYPES");
    }

    private ComponentName getOpenPDFComponentName() {
        return new ComponentName(getPackageName(), getClass().getName() + ".OPEN_PDF_TYPE");
    }

    private boolean isOpenAllTypesEnabled() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return sharedPreferences.getBoolean(getString(R.string.main_settings_open_all_filetypes_key), true);
    }

    private void enableOpenAllFileTypes() {
        PackageManager pm = getApplicationContext().getPackageManager();
        pm.setComponentEnabledSetting(getAllTypesComponentName(), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(getOpenPDFComponentName(), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(getCustomTypesComponentName(), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }

    private void disableOpenAllFileTypes() {
        PackageManager pm = getApplicationContext().getPackageManager();
        pm.setComponentEnabledSetting(getAllTypesComponentName(), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(getOpenPDFComponentName(), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(getCustomTypesComponentName(), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        navigator.onActivityResult(requestCode, resultCode, data);
    }

    @Singleton
    static final class RootScreenFactory implements Callable<Screen> {

        @Nullable private Intent intent;

        @Inject RootScreenFactory() {}

        void intent(Intent intent) {
            this.intent = intent;
        }


        @Override
        public Screen call() {
            if ((intent.getAction() != null && Intent.ACTION_SEND.equals(intent.getAction()) || Intent.ACTION_VIEW.equals(intent.getAction())) && intent.getType() != null) {
                return SignatureCreateScreen.create(intent);
            } else if (intent.getAction() != null && Intent.ACTION_GET_CONTENT.equals(intent.getAction())) {
                return SharingScreen.create();
            }
            return HomeScreen.create(intent);
        }
    }


}
