package ee.ria.DigiDoc.android;

import static ee.ria.DigiDoc.android.Constants.DIR_EXTERNALLY_OPENED_FILES;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import com.google.android.gms.common.util.CollectionUtils;
import com.google.android.gms.tasks.Task;
import com.google.common.collect.ImmutableList;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.crashlytics.internal.common.CommonUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import javax.inject.Inject;
import javax.inject.Singleton;

import ee.ria.DigiDoc.R;
import ee.ria.DigiDoc.android.crypto.create.CryptoCreateScreen;
import ee.ria.DigiDoc.android.main.home.HomeScreen;
import ee.ria.DigiDoc.android.main.settings.SettingsDataStore;
import ee.ria.DigiDoc.android.main.sharing.SharingScreen;
import ee.ria.DigiDoc.android.signature.create.SignatureCreateScreen;
import ee.ria.DigiDoc.android.utils.ContainerMimeTypeUtil;
import ee.ria.DigiDoc.android.utils.IntentUtils;
import ee.ria.DigiDoc.android.utils.SecureUtil;
import ee.ria.DigiDoc.android.utils.ToastUtil;
import ee.ria.DigiDoc.android.utils.files.FileStream;
import ee.ria.DigiDoc.android.utils.navigator.Navigator;
import ee.ria.DigiDoc.android.utils.navigator.Screen;
import ee.ria.DigiDoc.android.utils.widget.ErrorDialog;
import ee.ria.DigiDoc.common.FileUtil;
import ee.ria.DigiDoc.crypto.CryptoContainer;
import ee.ria.DigiDoc.sign.SignedContainer;
import timber.log.Timber;

public final class Activity extends AppCompatActivity {

    private Navigator navigator;
    private RootScreenFactory rootScreenFactory;
    private SettingsDataStore settingsDataStore;

    private static WeakReference<Context> mContext;

    public SettingsDataStore getSettingsDataStore() {
        return settingsDataStore;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        System.out.println("DIGIDOC: onCreate");
        Timber.log(Log.INFO, "DIGIDOC: onCreate");
        handleRootedDevice();
        setTheme(R.style.Theme_Application);
        setTitle(""); // ACCESSIBILITY: prevents application name read during each activity launch
        super.onCreate(savedInstanceState);

        System.out.println("DIGIDOC: Preventing screen recording");
        Timber.log(Log.INFO, "DIGIDOC: Preventing screen recording");

        // Prevent screen recording
        SecureUtil.markAsSecure(this, getWindow());

        System.out.println("DIGIDOC: Handling previous crashes");
        Timber.log(Log.INFO, "DIGIDOC: Handling previous crashes");

        handleCrashOnPreviousExecution();

        System.out.println("DIGIDOC: Intent: " + getIntent().toString());
        Timber.log(Log.INFO, "DIGIDOC: Intent: " + getIntent().toString());

        Intent intent = sanitizeIntent(getIntent());

        System.out.println("DIGIDOC: Sanitized intent: " + intent.toString());
        Timber.log(Log.INFO, "DIGIDOC: Sanitized intent: " + intent);

        if ((Intent.ACTION_SEND.equals(intent.getAction()) || Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction()) || Intent.ACTION_VIEW.equals(intent.getAction())) && intent.getType() != null) {
            System.out.println("DIGIDOC: Got ACTION_SEND intent: " + intent);
            Timber.log(Log.INFO, "DIGIDOC: Got ACTION_SEND intent: " + intent);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(intent.getAction());
            handleIncomingFiles(intent, this);
        } else if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
            System.out.println("DIGIDOC: Got ACTION_CONFIGURATION_CHANGED intent: " + intent);
            Timber.log(Log.INFO, "DIGIDOC: Got ACTION_CONFIGURATION_CHANGED intent: " + intent);
            getIntent().setAction(Intent.ACTION_MAIN);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            restartAppWithIntent(intent, false);
        } else if (Intent.ACTION_GET_CONTENT.equals(intent.getAction())) {
            System.out.println("DIGIDOC: Got ACTION_GET_CONTENT intent: " + intent);
            Timber.log(Log.INFO, "DIGIDOC: Got ACTION_GET_CONTENT intent: " + intent);
            rootScreenFactory.intent(intent, this);
        } else if (Intent.ACTION_MAIN.equals(intent.getAction()) && savedInstanceState != null) {
            System.out.println("DIGIDOC: Got ACTION_MAIN intent with savedInstanceState: " + intent);
            Timber.log(Log.INFO, "DIGIDOC: Got ACTION_MAIN intent with savedInstanceState: " + intent);
            savedInstanceState = null;
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            restartAppWithIntent(intent, false);
        } else {
            System.out.println("DIGIDOC: Creating rootScreenFactory: " + intent);
            Timber.log(Log.INFO, "DIGIDOC: Creating rootScreenFactory: " + intent);
            rootScreenFactory.intent(intent, this);
        }

        mContext = new WeakReference<>(this);

        System.out.println("DIGIDOC: initializeApplicationFileTypesAssociation");
        Timber.log(Log.INFO, "DIGIDOC: initializeApplicationFileTypesAssociation");

        initializeApplicationFileTypesAssociation();

        System.out.println("DIGIDOC: navigator.onCreate");
        Timber.log(Log.INFO, "DIGIDOC: navigator.onCreate");

        navigator.onCreate(this, findViewById(android.R.id.content), savedInstanceState);
    }

    private void handleRootedDevice() {
        if (CommonUtils.isRooted()) {
            System.out.println("DIGIDOC: ROOTED DEVICE");
            Timber.log(Log.INFO, "DIGIDOC: ROOTED DEVICE");
            ErrorDialog errorDialog = new ErrorDialog(this);
            errorDialog.setMessage(getResources().getString(R.string.rooted_device));
            errorDialog.setButton(DialogInterface.BUTTON_POSITIVE, getResources().getString(android.R.string.ok), (dialog, which) -> dialog.cancel());
            errorDialog.show();
        }
    }

    private void handleCrashOnPreviousExecution() {
        if (FirebaseCrashlytics.getInstance().didCrashOnPreviousExecution()) {
            System.out.println("DIGIDOC: didCrashOnPreviousExecution: yes");
            Timber.log(Log.INFO, "DIGIDOC: didCrashOnPreviousExecution: yes");
            if (settingsDataStore.getAlwaysSendCrashReport()) {
                sendUnsentCrashReports();
                return;
            }
            Dialog crashReportDialog = new Dialog(this);
            SecureUtil.markAsSecure(this, crashReportDialog.getWindow());
            crashReportDialog.setContentView(R.layout.crash_report_dialog);

            Button sendButton = crashReportDialog.findViewById(R.id.sendButton);
            sendButton.setOnClickListener(v -> {
                sendUnsentCrashReports();
                crashReportDialog.dismiss();
            });
            Button alwaysSendButton = crashReportDialog.findViewById(R.id.alwaysSendButton);
            alwaysSendButton.setOnClickListener(v -> {
                settingsDataStore.setAlwaysSendCrashReport(true);
                sendUnsentCrashReports();
                crashReportDialog.dismiss();
            });
            Button dontSendButton = crashReportDialog.findViewById(R.id.dontSendButton);
            dontSendButton.setOnClickListener(v -> {
                crashReportDialog.dismiss();
            });

            crashReportDialog.show();
        }
    }

    private void sendUnsentCrashReports() {
        System.out.println("DIGIDOC: sendUnsentCrashReports");
        Timber.log(Log.INFO, "DIGIDOC: sendUnsentCrashReports");
        Task<Boolean> task = FirebaseCrashlytics.getInstance().checkForUnsentReports();
        task.addOnSuccessListener(hasUnsentReports -> {
            if (hasUnsentReports) {
                FirebaseCrashlytics.getInstance().sendUnsentReports();
            } else {
                FirebaseCrashlytics.getInstance().deleteUnsentReports();
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public void restartAppWithIntent(Intent intent, boolean withExit) {
        System.out.println("DIGIDOC: restartAppWithIntent: " + intent);
        Timber.log(Log.INFO, "DIGIDOC: restartAppWithIntent: " + intent);
        finish();
        startActivity(intent);
        overridePendingTransition (0, 0);
        if (withExit) {
            int pid = android.os.Process.myPid();
            android.os.Process.killProcess(pid);
        }
    }

    private void handleIncomingFiles(Intent intent, Activity activity) {
        System.out.println("DIGIDOC: Handling incoming files");
        Timber.log(Log.INFO, "DIGIDOC: Handling incoming files");
        try {
            intent.setDataAndType(FileUtil.normalizeUri(intent.getData()), "*/*");
            rootScreenFactory.intent(intent, activity);
        } catch (ActivityNotFoundException e) {
            System.out.println("DIGIDOC: Handling incoming file intent. Error: " + e.getMessage());
            Timber.log(Log.INFO, "DIGIDOC: Handling incoming file intent. Error: " + e.getMessage());
            Timber.log(Log.ERROR, e, "Handling incoming file intent");
        }
    }

    private Intent sanitizeIntent(Intent intent) {
        if (intent.getDataString() != null) {
            Uri normalizedUri = FileUtil.normalizeUri(Uri.parse(intent.getDataString()));
            intent.setDataAndNormalize(normalizedUri);
        }
        if (intent.getExtras() != null && !(intent.getExtras().containsKey(Intent.EXTRA_REFERRER) &&
                intent.getExtras().get(Intent.EXTRA_REFERRER).equals(R.string.application_name))) {
            intent.replaceExtras(new Bundle());
        }
        return intent;
    }

    private void initializeApplicationFileTypesAssociation() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean isOpenAllTypesEnabled = sharedPreferences.getBoolean(getString(R.string.main_settings_open_all_filetypes_key), true);

        PackageManager pm = getApplicationContext().getPackageManager();
        ComponentName openAllTypesComponent = new ComponentName(getPackageName(), getClass().getName() + ".OPEN_ALL_FILE_TYPES");
        ComponentName openCustomTypesComponent = new ComponentName(getPackageName(), getClass().getName() + ".OPEN_CUSTOM_TYPES");

        if (isOpenAllTypesEnabled) {
            pm.setComponentEnabledSetting(openAllTypesComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(openCustomTypesComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        } else {
            pm.setComponentEnabledSetting(openCustomTypesComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(openAllTypesComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        Application.ApplicationComponent component = Application.component(newBase);
        navigator = component.navigator();
        rootScreenFactory = component.rootScreenFactory();
        settingsDataStore = component.settingsDataStore();
        super.attachBaseContext(component.localeService().attachBaseContext(newBase));
    }

    @Override
    public void onBackPressed() {
        if (!navigator.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        navigator.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        navigator.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public static WeakReference<Context> getContext() {
        return mContext;
    }

    @Singleton
    static final class RootScreenFactory implements Callable<Screen> {

        @Nullable private Intent intent;

        private android.app.Activity activity;

        @Inject RootScreenFactory() {}

        void intent(Intent intent, android.app.Activity activity) {
            this.intent = intent;
            this.activity = activity;
        }

        @Override
        public Screen call() {
            System.out.println("DIGIDOC: RootScreenFactory call");
            Timber.log(Log.INFO, "DIGIDOC: RootScreenFactory call");
            if ((intent != null && intent.getAction() != null &&
                    (Intent.ACTION_SEND.equals(intent.getAction()) ||
                            Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction()) ||
                            Intent.ACTION_VIEW.equals(intent.getAction()))) &&
                    intent.getType() != null) {
                System.out.println("DIGIDOC: Choosing screen");
                Timber.log(Log.INFO, "DIGIDOC: Choosing screen");
                return chooseScreen(intent, activity);
            } else if (intent != null && intent.getAction() != null &&
                    Intent.ACTION_GET_CONTENT.equals(intent.getAction())) {
                System.out.println("DIGIDOC: Creating SharingScreen");
                Timber.log(Log.INFO, "DIGIDOC: Creating SharingScreen");
                return SharingScreen.create();
            }
            System.out.println("DIGIDOC: Creating HomeScreen");
            Timber.log(Log.INFO, "DIGIDOC: Creating HomeScreen");
            return HomeScreen.create(intent);
        }

        private Screen chooseScreen(Intent intent, android.app.Activity activity) {
            ImmutableList<FileStream> fileStreams;
            File externallyOpenedFilesDir = new File(activity.getFilesDir(), DIR_EXTERNALLY_OPENED_FILES);
            try {
                fileStreams = IntentUtils.parseGetContentIntent(getContext().get(),
                        activity.getContentResolver(), intent, externallyOpenedFilesDir);
            } catch (Exception e) {
                System.out.println("DIGIDOC: Unable to open file. Error: " + e.getMessage());
                Timber.log(Log.INFO, "DIGIDOC: Unable to open file. Error: " + e.getMessage());
                Timber.log(Log.ERROR, e, "Unable to open file");
                ToastUtil.showError(getContext().get(), R.string.signature_create_error);
                System.out.println("DIGIDOC: Choosing screen HomeScreen");
                Timber.log(Log.INFO, "DIGIDOC: Choosing screen HomeScreen");
                return HomeScreen.create(
                        new Intent(Intent.ACTION_MAIN)
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP)
                );
            }
            if (!CollectionUtils.isEmpty(fileStreams) && fileStreams.size() == 1) {
                String fileName = fileStreams.get(0).displayName();
                int extensionPart = fileName.lastIndexOf(".");
                if (extensionPart != -1) {
                    String extension = fileName.substring(fileName.lastIndexOf("."));
                    if (".cdoc".equalsIgnoreCase(extension)) {
                        System.out.println("DIGIDOC: Choosing screen CryptoCreateScreen");
                        Timber.log(Log.INFO, "DIGIDOC: Choosing screen CryptoCreateScreen");
                        return CryptoCreateScreen.open(intent);
                    }
                } else if (intent.getClipData() != null || intent.getData() != null) {
                    File file = IntentUtils.parseGetContentIntent(getContext().get(),
                            activity.getContentResolver(), intent.getClipData() != null ?
                                    intent.getClipData().getItemAt(0).getUri() :
                                    intent.getData(),
                            externallyOpenedFilesDir);
                    try {
                        String newFileName = "container";
                        if (SignedContainer.isCdoc(file)) {
                            Path renamedFile = FileUtil.renameFile(file.toPath(),
                                    newFileName + ".cdoc");
                            CryptoContainer.open(renamedFile.toFile());
                            Intent updatedIntent = setIntentData(intent, renamedFile, activity);
                            System.out.println("DIGIDOC: Choosing updatedIntent screen CryptoCreateScreen");
                            Timber.log(Log.INFO, "DIGIDOC: Choosing updatedIntent screen CryptoCreateScreen");
                            return CryptoCreateScreen.open(updatedIntent);
                        } else {
                            String externalFileName = getFileName(file);
                            if (!externalFileName.isEmpty()) {
                                Path renamedFile = FileUtil.renameFile(file.toPath(),
                                        newFileName);
                                SignedContainer.open(renamedFile.toFile());
                                Intent updatedIntent = setIntentData(intent, renamedFile, activity);
                                System.out.println("DIGIDOC: Choosing updatedIntent screen SignatureCreateScreen");
                                Timber.log(Log.INFO, "DIGIDOC: Choosing updatedIntent screen SignatureCreateScreen");
                                return SignatureCreateScreen.create(updatedIntent);
                            } else {
                                System.out.println("DIGIDOC: Choosing creation screen SignatureCreateScreen");
                                Timber.log(Log.INFO, "DIGIDOC: Choosing creation screen SignatureCreateScreen");
                                return SignatureCreateScreen.create(intent);
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("DIGIDOC: Unable to open container. Opening as file. Error: " + e.getMessage());
                        Timber.log(Log.INFO, "DIGIDOC: Unable to open container. Opening as file. Error: " + e.getMessage());
                        Timber.log(Log.ERROR, e, "Unable to open container. Opening as file");
                        return SignatureCreateScreen.create(intent);
                    }
                }
            }
            System.out.println("DIGIDOC: Creating screen SignatureCreateScreen");
            Timber.log(Log.INFO, "DIGIDOC: Creating screen SignatureCreateScreen");
            return SignatureCreateScreen.create(intent);
        }

        private static String getFileName(File file) {
            if (SignedContainer.isDdoc(file)) {
                return "container." + "ddoc";
            } else if (FileUtil.isPDF(file)) {
                return "file.pdf";
            } else {
                String extension = ContainerMimeTypeUtil.getContainerExtension(file);
                if (!extension.isEmpty()) {
                    return "container." + extension;
                } else {
                    System.out.println("DIGIDOC: File.getName(): " + file.getName());
                    Timber.log(Log.INFO, "DIGIDOC: File.getName(): " + file.getName());
                    return file.getName();
                }
            }
        }

        private static Intent setIntentData(Intent intent, Path filePath, android.app.Activity activity) {
            System.out.println("DIGIDOC: Setting intentData");
            Timber.log(Log.INFO, "DIGIDOC: Setting intentData");
            intent.setData(Uri.parse(filePath.toUri().toString()));
            intent.setClipData(ClipData.newRawUri(filePath.getFileName().toString(), FileProvider.getUriForFile(
                    activity,
                    activity.getString(R.string.file_provider_authority),
                    filePath.toFile())));
            return intent;
        }


    }


}
