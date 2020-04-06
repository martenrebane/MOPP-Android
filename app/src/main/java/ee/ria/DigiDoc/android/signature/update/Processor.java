package ee.ria.DigiDoc.android.signature.update;

import android.app.Application;
import android.content.Context;
import android.view.accessibility.AccessibilityEvent;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import ee.ria.DigiDoc.R;
import ee.ria.DigiDoc.android.accessibility.AccessibilityUtils;
import ee.ria.DigiDoc.android.crypto.create.CryptoCreateScreen;
import ee.ria.DigiDoc.android.signature.data.SignatureContainerDataSource;
import ee.ria.DigiDoc.android.utils.IntentUtils;
import ee.ria.DigiDoc.android.utils.files.FileAlreadyExistsException;
import ee.ria.DigiDoc.android.utils.files.FileStream;
import ee.ria.DigiDoc.android.utils.navigator.Navigator;
import ee.ria.DigiDoc.android.utils.navigator.Transaction;
import ee.ria.DigiDoc.crypto.CryptoContainer;
import ee.ria.DigiDoc.sign.SignatureStatus;
import ee.ria.DigiDoc.sign.SignedContainer;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.ObservableTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import static android.app.Activity.RESULT_OK;
import static com.google.common.io.Files.getFileExtension;
import static ee.ria.DigiDoc.android.utils.IntentUtils.createSendIntent;
import static ee.ria.DigiDoc.android.utils.IntentUtils.parseGetContentIntent;

final class Processor implements ObservableTransformer<Action, Result> {

    private final ObservableTransformer<Action.ContainerLoadAction,
                                        Result.ContainerLoadResult> containerLoad;

    private final ObservableTransformer<Intent.NameUpdateIntent, Result.NameUpdateResult>
            nameUpdate;

    private final ObservableTransformer<Action.DocumentsAddAction,
                                        Result.DocumentsAddResult> documentsAdd;

    private final ObservableTransformer<Intent.DocumentViewIntent,
                                        Result.DocumentViewResult> documentView;

    private final ObservableTransformer<Action.DocumentRemoveAction,
                                        Result.DocumentRemoveResult> documentRemove;

    private final ObservableTransformer<Action.SignatureRemoveAction,
                                        Result.SignatureRemoveResult> signatureRemove;

    private final ObservableTransformer<Action.SignatureAddAction,
                                        Result.SignatureAddResult> signatureAdd;

    private final ObservableTransformer<Action.SendAction, Result.SendResult> send;

    @Inject Processor(SignatureContainerDataSource signatureContainerDataSource,
                      SignatureAddSource signatureAddSource, Application application,
                      Navigator navigator) {
        containerLoad = upstream -> upstream.switchMap(action ->
                signatureContainerDataSource.get(action.containerFile())
                        .toObservable()
                        .switchMap(container -> {
                            if (action.signatureAddSuccessMessageVisible()) {
                                return Observable.timer(3, TimeUnit.SECONDS)
                                        .map(ignored ->
                                                Result.ContainerLoadResult.success(container, null,
                                                        false))
                                        .startWith(Result.ContainerLoadResult.success(container,
                                                null, true));
                            } else {
                                final Observable<Result.ContainerLoadResult> just = Observable
                                        .just(Result.ContainerLoadResult.success(container,
                                                action.signatureAddMethod(),
                                                action.signatureAddSuccessMessageVisible()));
                                sendContainerStatusAccessibilityMessage(container, application.getApplicationContext());
                                return just;
                            }
                        })
                        .onErrorReturn(Result.ContainerLoadResult::failure)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .startWith(Result.ContainerLoadResult.progress()));

        nameUpdate = upstream -> upstream.switchMap(action -> {
            File containerFile = action.containerFile();
            String name = containerFile != null ? assignName(action, containerFile) : null;

            if (containerFile == null) {
                return Observable.just(Result.NameUpdateResult.hide());
            } else if (name == null) {
                return Observable.just(
                        Result.NameUpdateResult.name(containerFile),
                        Result.NameUpdateResult.show(containerFile));
            } else if (name.equals(containerFile.getName())) {
                return Observable.just(Result.NameUpdateResult.hide());
            } else if (name.isEmpty()) {
                return Observable.just(Result.NameUpdateResult
                        .failure(containerFile, new IOException()));
            } else {
                return Observable
                        .fromCallable(() -> {
                            File newFile = new File(containerFile.getParentFile(), name);
                            if (!newFile.getParentFile().equals(containerFile.getParentFile())) {
                                throw new IOException("Can't jump directories");
                            } else if (newFile.createNewFile()) {

                                checkContainerName(newFile);

                                //noinspection ResultOfMethodCallIgnored
                                newFile.delete();
                                if (!containerFile.renameTo(newFile)) {
                                    throw new IOException();
                                }

                                AccessibilityUtils.sendAccessibilityEvent(
                                        application.getApplicationContext(), AccessibilityEvent.TYPE_ANNOUNCEMENT, R.string.container_name_changed);


                                return newFile;
                            } else {
                                checkContainerName(newFile);

                                throw new FileAlreadyExistsException(newFile);
                            }
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .map(newFile -> {
                            navigator.execute(Transaction.replace(SignatureUpdateScreen
                                    .create(true, false, newFile, false, false)));
                            return Result.NameUpdateResult.progress(newFile);
                        })
                        .onErrorReturn(throwable ->
                                Result.NameUpdateResult.failure(containerFile, throwable))
                        .startWith(Result.NameUpdateResult.progress(containerFile));
            }
        });

        documentsAdd = upstream -> upstream
                .switchMap(action -> {
                    if (action.containerFile() == null) {
                        return Observable.just(Result.DocumentsAddResult.clear());
                    } else {
                        navigator.execute(action.transaction());
                        return navigator.activityResults()
                                .filter(activityResult ->
                                        activityResult.requestCode()
                                                == action.transaction().requestCode())
                                .switchMap(activityResult -> {
                                    android.content.Intent data = activityResult.data();
                                    if (activityResult.resultCode() == RESULT_OK && data != null) {
                                        return signatureContainerDataSource
                                                .addDocuments(action.containerFile(),
                                                        parseGetContentIntent(
                                                                application.getContentResolver(),
                                                                data))
                                                .toObservable()
                                                .map(container -> {
                                                    AccessibilityUtils.sendAccessibilityEvent(application.getApplicationContext(), AccessibilityEvent.TYPE_ANNOUNCEMENT, R.string.file_added);
                                                    return Result.DocumentsAddResult.success(container);
                                                })
                                                .onErrorReturn(Result.DocumentsAddResult::failure)
                                                .subscribeOn(Schedulers.io())
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .startWith(Result.DocumentsAddResult.adding());
                                    } else {
                                        return Observable.just(Result.DocumentsAddResult.clear());
                                    }
                                });
                    }
                });

        documentView = upstream -> upstream.switchMap(action -> {
            File containerFile = action.containerFile();
            return signatureContainerDataSource
                    .getDocumentFile(containerFile, action.document())
                    .toObservable()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .map(documentFile -> {
                        Transaction transaction;
                        boolean isSignedPdfDataFile =
                                getFileExtension(containerFile.getName()).toLowerCase(Locale.US)
                                                .equals("pdf")
                                        && containerFile.getName().equals(documentFile.getName());
                        if (!isSignedPdfDataFile && SignedContainer.isContainer(documentFile)) {
                            transaction = Transaction.push(SignatureUpdateScreen
                                    .create(true, true, documentFile, false, false));
                        } else if (CryptoContainer.isContainerFileName(documentFile.getName())) {
                            transaction = Transaction.push(CryptoCreateScreen.open(documentFile));
                        } else {
                            transaction = Transaction.activity(IntentUtils
                                    .createViewIntent(application, documentFile,
                                            SignedContainer.mimeType(documentFile)), null);
                        }
                        navigator.execute(transaction);
                        return Result.DocumentViewResult.idle();
                    })
                    .onErrorReturn(ignored -> Result.DocumentViewResult.idle())
                    .startWith(Result.DocumentViewResult.activity());
        });

        documentRemove = upstream -> upstream.flatMap(action -> {
            if (action.containerFile() == null || action.document() == null) {
                return Observable.just(Result.DocumentRemoveResult.clear());
            } else if (action.showConfirmation()) {
                return Observable.just(Result.DocumentRemoveResult.confirmation(action.document()));
            } else {
                return signatureContainerDataSource
                        .removeDocument(action.containerFile(), action.document())
                        .toObservable()
                        .map(container -> {
                            AccessibilityUtils.sendAccessibilityEvent(application.getApplicationContext(), AccessibilityEvent.TYPE_ANNOUNCEMENT, R.string.file_removed);
                            return Result.DocumentRemoveResult.success(container);
                        })
                        .onErrorReturn(Result.DocumentRemoveResult::failure)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .startWith(Result.DocumentRemoveResult.progress());
            }
        });

        signatureRemove = upstream -> upstream.flatMap(action -> {
            if (action.containerFile() == null || action.signature() == null) {
                return Observable.just(Result.SignatureRemoveResult.clear());
            } else if (action.showConfirmation()) {
                return Observable.just(Result.SignatureRemoveResult
                        .confirmation(action.signature()));
            } else {
                return signatureContainerDataSource
                        .removeSignature(action.containerFile(), action.signature())
                        .toObservable()
                        .map(container -> {
                            AccessibilityUtils.sendAccessibilityEvent(application.getApplicationContext(), AccessibilityEvent.TYPE_ANNOUNCEMENT, R.string.signature_removed);
                            return Result.SignatureRemoveResult.success(container);
                        })
                        .onErrorReturn(Result.SignatureRemoveResult::failure)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .startWith(Result.SignatureRemoveResult.progress());
            }
        });

        signatureAdd = upstream -> upstream.switchMap(action -> {
            Integer method = action.method();
            Boolean existingContainer = action.existingContainer();
            File containerFile = action.containerFile();
            SignatureAddRequest request = action.request();
            if (method == null) {
                return Observable.just(Result.SignatureAddResult.clear());
            } else if (request == null && existingContainer != null && containerFile != null) {
                if (SignedContainer.isLegacyContainer(containerFile)) {
                    return signatureContainerDataSource
                            .addContainer(ImmutableList.of(FileStream.create(containerFile)), true)
                            .toObservable()
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .doOnNext(containerAdd ->
                                    navigator.execute(Transaction.push(SignatureUpdateScreen.create(
                                            containerAdd.isExistingContainer(), false,
                                            containerAdd.containerFile(), true, false))))
                            .map(containerAdd -> Result.SignatureAddResult.clear())
                            .onErrorReturn(Result.SignatureAddResult::failure)
                            .startWith(Result.SignatureAddResult.activity());
                } else {
                    return signatureAddSource.show(method);
                }
            } else if (existingContainer != null && containerFile != null) {
                return signatureAddSource.sign(containerFile, request)
                        .switchMap(response -> {
                            if (response.container() != null) {
                                return Observable.fromCallable(() -> {
                                    navigator.execute(Transaction.replace(SignatureUpdateScreen
                                            .create(true, false, containerFile, false, true)));
                                    return Result.SignatureAddResult.method(method, response);
                                });
                            } else {
                                return Observable
                                        .just(Result.SignatureAddResult.method(method, response));
                            }
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .onErrorReturn(Result.SignatureAddResult::failure)
                        .startWith(Result.SignatureAddResult.activity(method));
            } else {
                throw new IllegalArgumentException("Can't handle action " + action);
            }
        });

        send = upstream -> upstream
                .doOnNext(action ->
                        navigator.execute(Transaction.activity(
                                createSendIntent(application, action.containerFile()), null)))
                .map(action -> Result.SendResult.success())
                .onErrorReturn(Result.SendResult::failure);
    }

    @SuppressWarnings("unchecked")
    @Override
    public ObservableSource<Result> apply(Observable<Action> upstream) {
        return upstream.publish(shared -> Observable.mergeArray(
                shared.ofType(Action.ContainerLoadAction.class).compose(containerLoad),
                shared.ofType(Intent.NameUpdateIntent.class).compose(nameUpdate),
                shared.ofType(Action.DocumentsAddAction.class).compose(documentsAdd),
                shared.ofType(Intent.DocumentViewIntent.class).compose(documentView),
                shared.ofType(Action.DocumentRemoveAction.class).compose(documentRemove),
                shared.ofType(Action.SignatureRemoveAction.class).compose(signatureRemove),
                shared.ofType(Action.SignatureAddAction.class).compose(signatureAdd),
                shared.ofType(Action.SendAction.class).compose(send)));
    }

    private void checkContainerName(File newContainerFileName) throws IOException {
        if (newContainerFileName.getName().startsWith(".")) {
            throw new IOException();
        }
    }

    private String addContainerExtension(File oldContainerFileName, String newName) {
        String[] oldContainerNameParts = oldContainerFileName.getName().split("\\.");
        String oldContainerNamePart = oldContainerNameParts[oldContainerNameParts.length - 1];

        return newName.concat(".").concat(oldContainerNamePart);
    }

    private String assignName(Intent.NameUpdateIntent action, File containerFile) {
        String name = action.name();
        if (name != null && !name.isEmpty()) {
            return addContainerExtension(containerFile, name);
        }

        return name;
    }


    private void sendContainerStatusAccessibilityMessage(SignedContainer container, Context context) {
        StringBuilder messageBuilder = new StringBuilder();
        if (container.signaturesValid()) {
            messageBuilder.append("Container has ");
            messageBuilder.append(container.signatures().size());
            messageBuilder.append(" valid signatures");
        } else {
            int unknownSignaturesCount = container.invalidSignatureCounts().get(SignatureStatus.UNKNOWN);
            int invalidSignatureCount = container.invalidSignatureCounts().get(SignatureStatus.INVALID);
            messageBuilder.append("Container is invalid, contains");
            if (unknownSignaturesCount > 0) {
                messageBuilder.append(" ").append(context.getResources().getQuantityString(
                        R.plurals.signature_update_signatures_unknown, unknownSignaturesCount, unknownSignaturesCount));
            }
            if (invalidSignatureCount > 0) {
                messageBuilder.append(" ").append(context.getResources().getQuantityString(
                        R.plurals.signature_update_signatures_invalid, invalidSignatureCount, invalidSignatureCount));
            }
        }
        AccessibilityUtils.sendAccessibilityEvent(context, AccessibilityEvent.TYPE_ANNOUNCEMENT, messageBuilder.toString());
    }
}
