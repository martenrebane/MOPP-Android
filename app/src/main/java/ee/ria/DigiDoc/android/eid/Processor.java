package ee.ria.DigiDoc.android.eid;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.common.collect.ImmutableSet;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import ee.ria.DigiDoc.R;
import ee.ria.DigiDoc.android.accessibility.AccessibilityUtils;
import ee.ria.DigiDoc.android.eid.CodeUpdateError.CodeInvalidError;
import ee.ria.DigiDoc.android.eid.CodeUpdateError.CodeMinLengthError;
import ee.ria.DigiDoc.android.eid.CodeUpdateError.CodePartOfDateOfBirthError;
import ee.ria.DigiDoc.android.eid.CodeUpdateError.CodePartOfPersonalCodeError;
import ee.ria.DigiDoc.android.eid.CodeUpdateError.CodeSameAsCurrentError;
import ee.ria.DigiDoc.android.eid.CodeUpdateError.CodeTooEasyError;
import ee.ria.DigiDoc.android.model.idcard.IdCardData;
import ee.ria.DigiDoc.android.model.idcard.IdCardService;
import ee.ria.DigiDoc.android.utils.LocaleService;
import ee.ria.DigiDoc.android.utils.navigator.Navigator;
import ee.ria.DigiDoc.android.utils.navigator.Transaction;
import ee.ria.DigiDoc.idcard.CodeType;
import ee.ria.DigiDoc.idcard.CodeVerificationException;
import ee.ria.DigiDoc.idcard.Token;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableSource;
import io.reactivex.rxjava3.core.ObservableTransformer;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

import static ee.ria.DigiDoc.android.utils.IntentUtils.createBrowserIntent;

final class Processor implements ObservableTransformer<Action, Result> {

    private final ObservableTransformer<Action.LoadAction, Result.LoadResult> load;

    private final ObservableTransformer<Action.CertificatesTitleClickAction,
                                        Result.CertificatesTitleClickResult> certificatesTitleClick;

    private final ObservableTransformer<Intent.CodeUpdateIntent, Result.CodeUpdateResult>
            codeUpdate;

    @Inject Processor(Application application, Navigator navigator, IdCardService idCardService, LocaleService localeService) {
        load = upstream -> upstream.switchMap(action -> {
            Observable<Result.LoadResult> resultObservable = idCardService.data()
                    .map(idCardDataResponse -> {
                        if (idCardDataResponse.error() != null) {
                            Timber.log(Log.DEBUG, "DIGIDOC: Load idCardDataResponse error: " + idCardDataResponse.error());
                            System.out.println("DIGIDOC: Load idCardDataResponse error: " + idCardDataResponse.error());
                            Timber.log(Log.DEBUG, "DIGIDOC: Load idCardDataResponse error: " + idCardDataResponse.error().getLocalizedMessage());
                            System.out.println("DIGIDOC: Load idCardDataResponse error: " + idCardDataResponse.error().getLocalizedMessage());
                            return Result.LoadResult.failure(idCardDataResponse.error());
                        } else {
                            Timber.log(Log.DEBUG, "DIGIDOC: Load idCardDataResponse success");
                            System.out.println("DIGIDOC: Load idCardDataResponse success");
                            return Result.LoadResult.success(idCardDataResponse);
                        }
                    })
                    .onErrorReturn(Result.LoadResult::failure);
            if (action.clear()) {
                Timber.log(Log.DEBUG, "DIGIDOC: Load idCardDataResponse clear");
                System.out.println("DIGIDOC: Load idCardDataResponse clear");
                return resultObservable
                        .startWithItem(Result.LoadResult.clear());
            }
            return resultObservable;
        });

        certificatesTitleClick = upstream -> upstream.map(action ->
                Result.CertificatesTitleClickResult.create(action.expand()));

        codeUpdate = upstream -> upstream.flatMap(action -> {
            CodeUpdateAction updateAction = action.action();
            Timber.log(Log.DEBUG, "DIGIDOC: updateAction: " + updateAction);
            System.out.println("DIGIDOC: updateAction: " + updateAction);
            CodeUpdateRequest request = action.request();
            Timber.log(Log.DEBUG, "DIGIDOC: CodeUpdateRequest: " + request);
            System.out.println("DIGIDOC: CodeUpdateRequest: " + request);
            IdCardData data = action.data();
            Timber.log(Log.DEBUG, "DIGIDOC: IdCardData: " + data);
            System.out.println("DIGIDOC: IdCardData: " + data);
            Token token = action.token();
            Timber.log(Log.DEBUG, "DIGIDOC: Token: " + token);
            System.out.println("DIGIDOC: Token: " + token);
            if (action.cleared()) {
                Timber.log(Log.DEBUG, "DIGIDOC: Action cleared");
                System.out.println("DIGIDOC: Action cleared");
                return Observable.just(Result.CodeUpdateResult.clear())
                        .doFinally(() -> {
                            Timber.log(Log.DEBUG, "DIGIDOC: CodeUpdateResult.clear");
                            System.out.println("DIGIDOC: CodeUpdateResult.clear");
                            sendCancellationAccessibilityEvent(updateAction, application,
                                    localeService.applicationConfigurationWithLocale(application.getApplicationContext(),
                                            localeService.applicationLocale()));
                        });
            } else if (updateAction == null) {
                Timber.log(Log.DEBUG, "DIGIDOC: updateAction null");
                System.out.println("DIGIDOC: updateAction null");
                return Observable.just(Result.CodeUpdateResult.clear());
            } else if (request == null || data == null || token == null) {
                Timber.log(Log.DEBUG, "DIGIDOC: request, data, token null");
                System.out.println("DIGIDOC: request, data, token null");
                if (updateAction.pinType().equals(CodeType.PUK)
                        && updateAction.updateType().equals(CodeUpdateType.UNBLOCK)) {
                    Timber.log(Log.DEBUG, "DIGIDOC: CodeType PUK, CodeUpdateType UNBLOCK");
                    System.out.println("DIGIDOC: CodeType PUK, CodeUpdateType UNBLOCK");
                    navigator.execute(Transaction
                            .activity(createBrowserIntent(application,
                                    R.string.eid_home_data_certificates_puk_link_url,
                                    localeService.applicationConfigurationWithLocale(application.getApplicationContext(),
                                            localeService.applicationLocale())), null));
                    return Observable.just(Result.CodeUpdateResult.clear());
                } else {
                    Timber.log(Log.DEBUG, "DIGIDOC: action: " + updateAction);
                    System.out.println("DIGIDOC: action: " + updateAction);
                    return Observable.just(Result.CodeUpdateResult.action(updateAction));
                }
            } else {
                Timber.log(Log.DEBUG, "DIGIDOC: Validating request");
                System.out.println("DIGIDOC: Validating request");
                CodeUpdateResponse response = validate(updateAction, request, data);
                Timber.log(Log.DEBUG, "DIGIDOC: CodeUpdateResponse response: " + response.toString());
                System.out.println("DIGIDOC: CodeUpdateResponse response: " + response);
                if (!response.success()) {
                    Timber.log(Log.DEBUG, "DIGIDOC: Response not successful");
                    System.out.println("DIGIDOC: Response not successful");

                    Timber.log(Log.DEBUG, "DIGIDOC: Response error: " + response.error());
                    System.out.println("DIGIDOC: Response error: " + response.error());
                    return Observable.just(Result.CodeUpdateResult
                            .response(updateAction, response, null, null));
                }

                Timber.log(Log.DEBUG, "DIGIDOC: Response successful");
                System.out.println("DIGIDOC: Response successful");

                Single<IdCardData> operation;
                if (updateAction.updateType().equals(CodeUpdateType.EDIT)) {
                    Timber.log(Log.DEBUG, "DIGIDOC: Editing PIN");
                    System.out.println("DIGIDOC: Editing PIN");
                    operation = idCardService
                            .editPin(token, updateAction.pinType(), request.currentValue(),
                                    request.newValue());
                } else {
                    Timber.log(Log.DEBUG, "DIGIDOC: Unblocking PIN");
                    System.out.println("DIGIDOC: Unblocking PIN");
                    operation = idCardService
                            .unblockPin(token, updateAction.pinType(), request.currentValue(),
                                    request.newValue());
                }
                return operation
                        .toObservable()
                        .flatMap(idCardData ->
                                Observable
                                        .timer(3, TimeUnit.SECONDS)
                                        .map(ignored -> {
                                            Timber.log(Log.DEBUG, "DIGIDOC: hideSuccessResponse");
                                            System.out.println("DIGIDOC: hideSuccessResponse");
                                            return Result.CodeUpdateResult
                                                    .hideSuccessResponse(updateAction,
                                                            CodeUpdateResponse.valid(),
                                                            idCardData, token);
                                        })
                                        .startWithArray(
                                                Result.CodeUpdateResult
                                                        .clearResponse(updateAction,
                                                                CodeUpdateResponse.valid(),
                                                                idCardData, token),
                                                Result.CodeUpdateResult
                                                        .successResponse(updateAction,
                                                                CodeUpdateResponse.valid(),
                                                                idCardData, token)))
                        .onErrorReturn(throwable -> {
                            Timber.log(Log.DEBUG, "DIGIDOC: ID-card error: " + throwable.getLocalizedMessage());
                            System.out.println("DIGIDOC: ID-card error: " + throwable.getLocalizedMessage());
                            IdCardData idCardData = IdCardService.data(token);
                            Timber.log(Log.DEBUG, "DIGIDOC: idCardData: " + idCardData);
                            System.out.println("DIGIDOC: idCardData: " + idCardData);
                            int retryCount = retryCount(updateAction, idCardData);

                            Timber.log(Log.DEBUG, "DIGIDOC: retryCount: " + retryCount);
                            System.out.println("DIGIDOC: retryCount: " + retryCount);

                            CodeUpdateResponse.Builder builder = CodeUpdateResponse.valid()
                                    .buildWith();
                            if (throwable instanceof CodeVerificationException && retryCount > 0) {
                                Timber.log(Log.DEBUG, "DIGIDOC: CodeInvalidError");
                                System.out.println("DIGIDOC: CodeInvalidError");
                                builder.currentError(CodeInvalidError.create(retryCount));
                            } else {
                                builder.error(throwable);
                            }

                            Timber.log(Log.DEBUG, "DIGIDOC: CodeUpdateResponse error: " + builder.build().error());
                            System.out.println("DIGIDOC: CodeUpdateResponse error: " + builder.build().error());

                            Timber.log(Log.DEBUG, "DIGIDOC: CodeUpdateResponse error: " + builder.build().toString());
                            System.out.println("DIGIDOC: CodeUpdateResponse error: " + builder.build().toString());

                            return Result.CodeUpdateResult
                                    .response(updateAction, builder.build(), idCardData, token);
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .startWithItem(Result.CodeUpdateResult.progress(updateAction));
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public ObservableSource<Result> apply(Observable<Action> upstream) {
        return upstream.publish(shared -> Observable.mergeArray(
                shared.ofType(Action.LoadAction.class).compose(load),
                shared.ofType(Action.CertificatesTitleClickAction.class)
                        .compose(certificatesTitleClick),
                shared.ofType(Intent.CodeUpdateIntent.class).compose(codeUpdate)));
    }

    private static CodeUpdateResponse validate(CodeUpdateAction action, CodeUpdateRequest request,
                                               IdCardData data) {
        Timber.log(Log.DEBUG, "DIGIDOC: Validating PIN");
        System.out.println("DIGIDOC: Validating PIN");
        LocalDate dateOfBirth = data.personalData().dateOfBirth();
        Timber.log(Log.DEBUG, "DIGIDOC: dateOfBirth: " + dateOfBirth);
        System.out.println("DIGIDOC: dateOfBirth: " + dateOfBirth);
        ImmutableSet.Builder<String> dateOfBirthValuesBuilder = ImmutableSet.builder();
        if (dateOfBirth != null) {
            dateOfBirthValuesBuilder
                    .add(dateOfBirth.format(DateTimeFormatter.ofPattern("yyyy")))
                    .add(dateOfBirth.format(DateTimeFormatter.ofPattern("MMdd")))
                    .add(dateOfBirth.format(DateTimeFormatter.ofPattern("ddMM")))
                    .add(dateOfBirth.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        }
        ImmutableSet<String> dateOfBirthValues = dateOfBirthValuesBuilder.build();

        CodeUpdateResponse.Builder builder = CodeUpdateResponse.valid().buildWith();

        // current
        if (request.currentValue().length() < action.currentMinLength()) {
            Timber.log(Log.DEBUG, "DIGIDOC: CodeMinLengthError (current)");
            System.out.println("DIGIDOC: CodeMinLengthError (current)");
            builder.currentError(CodeMinLengthError.create(action.currentMinLength()));
        }

        // new
        if (request.newValue().length() < action.newMinLength()) {
            Timber.log(Log.DEBUG, "DIGIDOC: CodeMinLengthError (new)");
            System.out.println("DIGIDOC: CodeMinLengthError (new)");
            builder.newError(CodeMinLengthError.create(action.newMinLength()));
        } else if (action.updateType().equals(CodeUpdateType.EDIT)
                && request.newValue().equals(request.currentValue())) {
            Timber.log(Log.DEBUG, "DIGIDOC: CodeSameAsCurrentError (new)");
            System.out.println("DIGIDOC: CodeSameAsCurrentError (new)");
            builder.newError(CodeSameAsCurrentError.create());
        } else if (data.personalData().personalCode().contains(request.newValue())) {
            Timber.log(Log.DEBUG, "DIGIDOC: CodePartOfPersonalCodeError (new)");
            System.out.println("DIGIDOC: CodePartOfPersonalCodeError (new)");
            builder.newError(CodePartOfPersonalCodeError.create());
        } else if (dateOfBirthValues.contains(request.newValue())) {
            Timber.log(Log.DEBUG, "DIGIDOC: CodePartOfDateOfBirthError (new)");
            System.out.println("DIGIDOC: CodePartOfDateOfBirthError (new)");
            builder.newError(CodePartOfDateOfBirthError.create());
        } else if (isCodeTooEasy(request.newValue())) {
            Timber.log(Log.DEBUG, "DIGIDOC: CodeTooEasyError (new)");
            System.out.println("DIGIDOC: CodeTooEasyError (new)");
            builder.newError(CodeTooEasyError.create());
        }

        // repeat
        if (!request.newValue().equals(request.repeatValue())) {
            Timber.log(Log.DEBUG, "DIGIDOC: CodeRepeatMismatchError (repeat)");
            System.out.println("DIGIDOC: CodeRepeatMismatchError (repeat)");
            builder.repeatError(CodeUpdateError.CodeRepeatMismatchError.create());
        }

        return builder.build();
    }

    /**
     * Checks that the code doesn't contain only one number nor growing or shrinking by one.
     *
     * Examples: 00000, 5555, 1234, 98765.
     *
     * @param code Code to check.
     * @return True if the code is too easy.
     */
    private static boolean isCodeTooEasy(String code) {
        Integer delta = null;
        for (int i = 0; i < code.length() - 1; i++) {
            int currentNumber = Character.getNumericValue(code.charAt(i));
            int nextNumber = Character.getNumericValue(code.charAt(i + 1));

            int d = currentNumber - nextNumber;

            // Reset sequence
            if (Math.abs(d) == 9) {
                delta = null;
                if ((currentNumber == 9 && nextNumber == 0)) {
                    d = -1;
                } else if ((currentNumber == 0 && nextNumber == 9)) {
                    d = 1;
                }
            }

            if (Math.abs(d) > 1) {
                return false;
            }
            if (delta != null && delta != d) {
                return false;
            }
            delta = d;
        }
        return true;
    }

    private int retryCount(CodeUpdateAction action, IdCardData data) {
        CodeType pinType = action.pinType();
        String updateType = action.updateType();
        if (updateType.equals(CodeUpdateType.UNBLOCK) || pinType.equals(CodeType.PUK)) {
            return data.pukRetryCount();
        } else if (pinType.equals(CodeType.PIN1)) {
            return data.pin1RetryCount();
        } else {
            return data.pin2RetryCount();
        }
    }

    private void sendCancellationAccessibilityEvent(CodeUpdateAction updateAction, Application application, Configuration configuration) {
        String actionText = "";
        Context configurationContext = application.getApplicationContext().createConfigurationContext(configuration);
        switch (updateAction.pinType()) {
            case PIN1:
                if (updateAction.updateType().equals(CodeUpdateType.UNBLOCK)) {
                    actionText = configurationContext.getText(application.getResources()
                                    .getIdentifier("pin1_unblock_cancelled", "string",
                                            application.getPackageName())).toString();
                } else {
                    actionText = configurationContext.getText(application.getResources()
                            .getIdentifier("pin1_change_cancelled", "string",
                                    application.getPackageName())).toString();
                }
                break;
            case PIN2:
                if (updateAction.updateType().equals(CodeUpdateType.UNBLOCK)) {
                    actionText = configurationContext.getText(application.getResources()
                            .getIdentifier("pin2_unblock_cancelled", "string",
                                    application.getPackageName())).toString();
                } else {
                    actionText = configurationContext.getText(application.getResources()
                            .getIdentifier("pin2_change_cancelled", "string",
                                    application.getPackageName())).toString();
                }
                break;
            case PUK:
                actionText = configurationContext.getText(application.getResources()
                        .getIdentifier("puk_code_change_cancelled", "string",
                                application.getPackageName())).toString();
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + updateAction.pinType());
        }
        AccessibilityUtils.sendAccessibilityEvent(
                application.getApplicationContext(), AccessibilityEvent.TYPE_ANNOUNCEMENT, actionText);
    }
}
