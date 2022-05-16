package ee.ria.DigiDoc.android.signature.create;

import static android.app.Activity.RESULT_OK;
import static ee.ria.DigiDoc.android.utils.IntentUtils.parseGetContentIntent;

import android.app.Application;

import com.google.common.collect.ImmutableList;

import javax.inject.Inject;

import ee.ria.DigiDoc.android.signature.data.SignatureContainerDataSource;
import ee.ria.DigiDoc.android.utils.ToastUtil;
import ee.ria.DigiDoc.android.utils.files.FileStream;
import ee.ria.DigiDoc.android.utils.files.FileSystem;
import ee.ria.DigiDoc.android.utils.navigator.ActivityResult;
import ee.ria.DigiDoc.android.utils.navigator.ActivityResultException;
import ee.ria.DigiDoc.android.utils.navigator.Navigator;
import ee.ria.DigiDoc.android.utils.navigator.Transaction;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableSource;
import io.reactivex.rxjava3.core.ObservableTransformer;

final class Processor implements ObservableTransformer<Action, Result> {

    private final ObservableTransformer<Action.ChooseFilesAction, Result.ChooseFilesResult>
            chooseFiles;

    @Inject Processor(Navigator navigator,
                      SignatureContainerDataSource signatureContainerDataSource,
                      Application application,
                      FileSystem fileSystem) {
        chooseFiles = upstream -> upstream
                .switchMap(action -> {
                    if (action.intent() != null) {
                        throw new ActivityResultException(ActivityResult.create(
                                action.transaction().requestCode(), RESULT_OK, action.intent()));
                    }
                    navigator.execute(action.transaction());
                    return navigator.activityResults()
                            .filter(activityResult ->
                                    activityResult.requestCode()
                                            == action.transaction().requestCode())
                            .doOnNext(activityResult -> {
                                throw new ActivityResultException(activityResult);
                            })
                            .map(activityResult -> Result.ChooseFilesResult.create());
                })
                .onErrorResumeNext(throwable -> {
                    if (!(throwable instanceof ActivityResultException)) {
                        return Observable.error(throwable);
                    }
                    ActivityResult activityResult = ((ActivityResultException) throwable)
                            .activityResult;
                    if (activityResult.resultCode() == RESULT_OK) {
                        ImmutableList<FileStream> validFiles = FileSystem.getFilesWithValidSize(
                                parseGetContentIntent(application.getContentResolver(), activityResult.data(), fileSystem.getExternallyOpenedFilesDir()));
                        ToastUtil.handleEmptyFileError(validFiles, application);
                        if (SivaUtil.isSivaConfirmationNeeded(validFiles)) {
                            sivaConfirmationDialog.show();
                            ClickableDialogUtil.makeLinksInDialogClickable(sivaConfirmationDialog);
                            sivaConfirmationDialog.cancels()
                                    .doOnNext(next -> navigator.execute(Transaction.pop()))
                                    .subscribe();
                            sivaConfirmationDialog.positiveButtonClicks()
                                    .flatMap(next -> {
                                        sivaConfirmationDialog.dismiss();
                                        return addFilesToContainer(navigator, signatureContainerDataSource, application, validFiles);
                                    })
                                    .subscribe();
                            return Observable.just(Result.ChooseFilesResult.create());
                        } else {
                            return addFilesToContainer(navigator, signatureContainerDataSource, application, validFiles);
                        }
                    } else {
                        navigator.execute(Transaction.pop());
                        return Observable.just(Result.ChooseFilesResult.create());
                    }
                })
                .onErrorResumeNext(throwable -> {
                    ToastUtil.showEmptyFileError(application);
                    navigator.execute(Transaction.pop());
                });
    }

    @SuppressWarnings("unchecked")
    @Override
    public ObservableSource<Result> apply(Observable<Action> upstream) {
        return upstream.publish(shared -> Observable.mergeArray(
                shared.ofType(Action.ChooseFilesAction.class).compose(chooseFiles)));
    }
}
