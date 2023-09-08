package ee.ria.DigiDoc.android.main.settings.create;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ee.ria.DigiDoc.R;
import ee.ria.DigiDoc.android.ApplicationApp;
import ee.ria.DigiDoc.android.utils.ViewDisposables;
import ee.ria.DigiDoc.android.utils.mvi.MviView;
import ee.ria.DigiDoc.android.utils.navigator.Navigator;
import ee.ria.DigiDoc.android.utils.navigator.Screen;
import ee.ria.DigiDoc.android.utils.navigator.Transaction;
import ee.ria.DigiDoc.android.utils.navigator.conductor.ConductorScreen;
import io.reactivex.rxjava3.core.Observable;

public final class ChooseFileScreen extends ConductorScreen implements Screen, MviView<Intent, ViewState> {

    public static ChooseFileScreen create() {
        return new ChooseFileScreen();
    }

    @SuppressWarnings("WeakerAccess")
    public ChooseFileScreen() {
        super(R.id.mainSettingsChooseFileScreen);
    }

    @Override
    protected View view(Context context) {
        return new View(context);
    }

    @Override
    protected void onDestroyView(@NonNull View view) {
        super.onDestroyView(view);
    }

    @Override
    public Observable<Intent> intents() {
        return Observable.mergeArray(chooseFileIntent());
    }

    private final ViewDisposables disposables = new ViewDisposables();
    private TSACertificateAddViewModel viewModel;

    @NonNull
    @Override
    protected View onCreateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container, @Nullable Bundle savedViewState) {
        disposables.attach();
        disposables.add(viewModel.viewStates().subscribe(this::render));
        viewModel.process(intents());

        return super.onCreateView(inflater, container, savedViewState);
    }

    @Override
    protected void onContextAvailable(@NonNull Context context) {
        super.onContextAvailable(context);
        viewModel = ApplicationApp.component(context).navigator()
                .viewModel(getInstanceId(), TSACertificateAddViewModel.class);
    }

    @Override
    protected void onContextUnavailable() {
        viewModel = null;
        super.onContextUnavailable();
    }

    @Override
    public void render(ViewState state) {

        if (state.context() != null) {
            Navigator navigator = ApplicationApp.component(state.context()).navigator();
            navigator.execute(Transaction.pop());
        }
    }

    private Observable<Intent.ChooseFileIntent> chooseFileIntent() {
        return Observable.just(new Intent.ChooseFileIntent().create());
    }
}
