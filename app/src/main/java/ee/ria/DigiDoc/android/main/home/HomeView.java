package ee.ria.DigiDoc.android.main.home;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Parcelable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.internal.BaselineLayout;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import ee.ria.DigiDoc.R;
import ee.ria.DigiDoc.android.Application;
import ee.ria.DigiDoc.android.crypto.home.CryptoHomeView;
import ee.ria.DigiDoc.android.eid.EIDHomeView;
import ee.ria.DigiDoc.android.signature.home.SignatureHomeView;
import ee.ria.DigiDoc.android.utils.TextUtil;
import ee.ria.DigiDoc.android.utils.ViewDisposables;
import ee.ria.DigiDoc.android.utils.mvi.MviView;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

import static com.jakewharton.rxbinding4.material.RxBottomNavigationView.itemSelections;
import static ee.ria.DigiDoc.android.utils.Predicates.duplicates;
import static ee.ria.DigiDoc.android.utils.rxbinding.app.RxDialog.cancels;

@SuppressLint("ViewConstructor")
public final class HomeView extends LinearLayout implements MviView<Intent, ViewState> {

    public interface HomeViewChild {

        HomeToolbar homeToolbar();

        Observable<Boolean> navigationViewVisibility();
    }

    private final android.content.Intent intent;
    private final String eidScreenId;

    private final FrameLayout navigationContainerView;
    private final BottomNavigationView navigationView;
    private final HomeMenuDialog menuDialog;
    private final HomeMenuView menuView;

    private final HomeViewModel viewModel;
    private final ViewDisposables disposables = new ViewDisposables();

    private final Subject<Intent.MenuIntent> menuIntentSubject = PublishSubject.create();
    private final Subject<Intent.NavigationVisibilityIntent> navigationVisibilityIntentSubject =
            PublishSubject.create();

    @Nullable private SparseArray<Parcelable> hierarchyState;

    public HomeView(Context context, android.content.Intent intent, String screenId) {
        super(context);
        this.intent = intent;
        eidScreenId = screenId + "eid";
        setOrientation(VERTICAL);
        inflate(context, R.layout.main_home, this);
        navigationContainerView = findViewById(R.id.mainHomeNavigationContainer);
        navigationView = findViewById(R.id.mainHomeNavigation);

        BottomNavigationItemView signatureItem = findViewById(R.id.mainHomeNavigationSignature);
        BottomNavigationItemView cryptoItem = findViewById(R.id.mainHomeNavigationCrypto);
        BottomNavigationItemView eidItem = findViewById(R.id.mainHomeNavigationEID);
        setCustomAccessibilityFeedback(signatureItem, R.string.main_home_navigation_signature, getResources().getString(R.string.signature_content_description, 1, 3));
        setCustomAccessibilityFeedback(cryptoItem, R.string.main_home_navigation_crypto, getResources().getString(R.string.crypto_content_description, 2, 3));
        setCustomAccessibilityFeedback(eidItem, R.string.main_home_navigation_eid, getResources().getString(R.string.my_eid_content_description, 3, 3));

        setBottomToolbarItemSizes(signatureItem);
        setBottomToolbarItemSizes(cryptoItem);
        setBottomToolbarItemSizes(eidItem);

        menuDialog = new HomeMenuDialog(context);
        menuView = menuDialog.getMenuView();
        viewModel = Application.component(context).navigator().viewModel(screenId,
                HomeViewModel.class);
        viewModel.eidScreenId(eidScreenId);
    }

    private void setBottomToolbarItemSizes(BottomNavigationItemView itemView) {
        for (int i = 0; i < itemView.getChildCount(); i++) {
            if (itemView.getChildAt(i) instanceof BaselineLayout) {
                BaselineLayout baselineLayout = ((BaselineLayout) itemView.getChildAt(i));
                if (baselineLayout.getChildCount() > 0) {
                    for (int j = 0; j < baselineLayout.getChildCount(); j++) {
                        if (baselineLayout.getChildAt(j) instanceof AppCompatTextView) {
                            AppCompatTextView textView = (AppCompatTextView) baselineLayout.getChildAt(j);
                            textView.setPadding(0, 50, 0, 0);
                            TextUtil.setTextViewSizeInContainer(textView);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void render(ViewState state) {
        View currentNavigationView = navigationContainerView.getChildAt(0);
        if (currentNavigationView == null || currentNavigationView.getId() != state.viewId()) {
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            View view;
            if (state.viewId() == R.id.mainHomeSignature) {
                view = new SignatureHomeView(getContext());
            } else if (state.viewId() == R.id.mainHomeCrypto) {
                view = new CryptoHomeView(getContext());
            } else if (state.viewId() == R.id.mainHomeEID) {
                view = new EIDHomeView(getContext(), eidScreenId);
            } else {
                throw new IllegalArgumentException("Unknown view ID " + state.viewId());
            }
            view.setId(state.viewId());
            navigationContainerView.removeAllViews();
            if (hierarchyState != null) {
                view.restoreHierarchyState(hierarchyState);
                hierarchyState = null;
            }
            navigationContainerView.addView(view, layoutParams);

            HomeViewChild homeViewChild = (HomeViewChild) view;
            homeViewChild.homeToolbar().overflowButtonClicks()
                    .map(ignored -> Intent.MenuIntent.state(true))
                    .subscribe(menuIntentSubject);
            homeViewChild.navigationViewVisibility()
                    .map(Intent.NavigationVisibilityIntent::create)
                    .subscribe(navigationVisibilityIntentSubject);
        }

        if (state.menuOpen()) {
            menuDialog.show();
        } else {
            menuDialog.dismiss();
        }

        navigationView.setVisibility(state.navigationVisible() ? VISIBLE : GONE);
        menuView.locale(state.locale());
    }

    private Observable<Intent.InitialIntent> initialIntent() {
        return Observable.just(Intent.InitialIntent.create(intent));
    }

    private Observable<Intent.NavigationIntent> navigationIntent() {
        return itemSelections(navigationView)
                .filter(duplicates())
                .map(item -> Intent.NavigationIntent.create(item.getItemId()));
    }

    private Observable<Intent.MenuIntent> menuIntent() {
        return Observable.merge(
                menuIntentSubject,
                cancels(menuDialog).map(ignored -> Intent.MenuIntent.state(false)),
                menuView.closeButtonClicks().map(ignored -> Intent.MenuIntent.state(false)),
                menuView.itemClicks().map(Intent.MenuIntent::navigate));
    }

    private Observable<Intent.NavigationVisibilityIntent> navigationVisibilityIntent() {
        return navigationVisibilityIntentSubject;
    }

    private Observable<Intent.LocaleChangeIntent> localeChangeIntent() {
        return menuView.localeChecks()
                .map(Intent.LocaleChangeIntent::create);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Observable<Intent> intents() {
        return Observable.mergeArray(initialIntent(), navigationIntent(), menuIntent(),
                navigationVisibilityIntent(), localeChangeIntent());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        disposables.attach();
        disposables.add(viewModel.viewStates().filter(duplicates()).subscribe(this::render));
        viewModel.process(intents());
    }

    @Override
    protected void onDetachedFromWindow() {
        menuDialog.dismiss();
        disposables.detach();
        super.onDetachedFromWindow();
    }

    @Override
    public void restoreHierarchyState(SparseArray<Parcelable> container) {
        super.restoreHierarchyState(container);
        this.hierarchyState = container;
    }

    private void setCustomAccessibilityFeedback(BottomNavigationItemView bottomNavigationItemView, @StringRes int contentDescription, String toolTipText) {
        bottomNavigationItemView.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setContentDescription(getResources().getString(contentDescription).toLowerCase());
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.setTooltipText(toolTipText);
                } else {
                    info.setContentDescription(toolTipText);
                }
                info.setClassName("");
                info.setPackageName("");
                info.setViewIdResourceName("");
            }
        });
    }
}
