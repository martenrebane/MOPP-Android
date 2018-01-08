package ee.ria.DigiDoc.android.signature.update;

import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;

import java.io.File;

import ee.ria.DigiDoc.android.signature.data.Signature;
import ee.ria.DigiDoc.android.signature.data.SignatureAddStatus;
import ee.ria.DigiDoc.android.signature.data.SignatureContainer;
import ee.ria.DigiDoc.android.utils.mvi.MviViewState;

@AutoValue
abstract class ViewState implements MviViewState {

    @Nullable abstract SignatureContainer container();
    abstract boolean loadContainerInProgress();
    @Nullable abstract Throwable loadContainerError();

    abstract boolean pickingDocuments();
    abstract boolean documentsProgress();
    @Nullable abstract Throwable addDocumentsError();
    @Nullable abstract File openedDocumentFile();
    @Nullable abstract Throwable openDocumentError();

    @Nullable abstract Throwable removeDocumentsError();

    @Nullable abstract Signature signatureRemoveConfirmation();
    abstract boolean signatureRemoveInProgress();
    @Nullable abstract Throwable signatureRemoveError();

    abstract boolean signatureAddCreateContainerInProgress();
    abstract boolean signatureAddVisible();
    abstract boolean signatureAddInProgress();
    @Nullable @SignatureAddStatus abstract String signatureAddStatus();
    @Nullable abstract String signatureAddChallenge();
    abstract boolean signatureAddSuccessMessageVisible();
    @Nullable abstract Throwable signatureAddError();

    abstract Builder buildWith();

    static ViewState initial() {
        return new AutoValue_ViewState.Builder()
                .loadContainerInProgress(false)
                .pickingDocuments(false)
                .documentsProgress(false)
                .signatureRemoveInProgress(false)
                .signatureAddCreateContainerInProgress(false)
                .signatureAddVisible(false)
                .signatureAddInProgress(false)
                .signatureAddSuccessMessageVisible(false)
                .build();
    }

    @AutoValue.Builder
    interface Builder {
        Builder container(@Nullable SignatureContainer container);
        Builder loadContainerInProgress(boolean loadContainerInProgress);
        Builder loadContainerError(@Nullable Throwable error);
        Builder pickingDocuments(boolean pickingDocuments);
        Builder documentsProgress(boolean documentsProgress);
        Builder addDocumentsError(@Nullable Throwable addDocumentsError);
        Builder openedDocumentFile(@Nullable File openedDocumentFile);
        Builder openDocumentError(@Nullable Throwable openDocumentError);
        Builder removeDocumentsError(@Nullable Throwable removeDocumentsError);
        Builder signatureRemoveConfirmation(@Nullable Signature signatureRemoveConfirmation);
        Builder signatureRemoveInProgress(boolean signatureRemoveInProgress);
        Builder signatureRemoveError(@Nullable Throwable signatureRemoveError);
        Builder signatureAddCreateContainerInProgress(
                boolean signatureAddCreateContainerInProgress);
        Builder signatureAddVisible(boolean signatureAddVisible);
        Builder signatureAddInProgress(boolean signatureAddInProgress);
        Builder signatureAddStatus(@Nullable @SignatureAddStatus String signatureAddStatus);
        Builder signatureAddChallenge(@Nullable String signatureAddChallenge);
        Builder signatureAddSuccessMessageVisible(boolean signatureAddSuccessMessageVisible);
        Builder signatureAddError(@Nullable Throwable signatureAddError);
        ViewState build();
    }
}
