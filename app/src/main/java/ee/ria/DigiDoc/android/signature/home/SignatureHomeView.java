package ee.ria.DigiDoc.android.signature.home;

import static com.jakewharton.rxbinding4.view.RxView.clicks;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import ee.ria.DigiDoc.R;
import ee.ria.DigiDoc.android.ApplicationApp;
import ee.ria.DigiDoc.android.accessibility.AccessibilityUtils;
import ee.ria.DigiDoc.android.main.home.HomeToolbar;
import ee.ria.DigiDoc.android.main.home.HomeView;
import ee.ria.DigiDoc.android.signature.create.SignatureCreateScreen;
import ee.ria.DigiDoc.android.signature.list.SignatureListScreen;
import ee.ria.DigiDoc.android.utils.ViewDisposables;
import ee.ria.DigiDoc.android.utils.navigator.Navigator;
import ee.ria.DigiDoc.android.utils.navigator.Transaction;
import io.reactivex.rxjava3.core.Observable;

public final class SignatureHomeView extends CoordinatorLayout implements HomeView.HomeViewChild {

    private final HomeToolbar toolbarView;
    private final Button createButton;
    private final Button recentDocumentsButton;

    private final Navigator navigator;

    private final ViewDisposables disposables;

    public SignatureHomeView(Context context) {
        this(context, null);
    }

    public SignatureHomeView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignatureHomeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inflate(context, R.layout.signature_home, this);
        toolbarView = findViewById(R.id.toolbar);
        createButton = findViewById(R.id.signatureHomeCreateButton);
        navigator = ApplicationApp.component(context).navigator();
        recentDocumentsButton = findViewById(R.id.signatureHomeRecentDocumentsButton);
        disposables = new ViewDisposables();
        AccessibilityUtils.setViewAccessibilityPaneTitle(this, R.string.main_home_navigation_signature);

        createButton.postDelayed(() -> {
            createButton.requestFocus();
            createButton.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        }, 2000);

        LinearLayout linearLayout = findViewById(R.id.signatureHomeLayout);
        Button crashButton = new Button(navigator.activity());
        crashButton.setText("Test Crash");
        crashButton.setTextColor(getResources().getColor(R.color.material_color_white, null));
        crashButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                throw new RuntimeException("Testing the framework crash"); // Force a crash
            }
        });

        linearLayout.addView(crashButton, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override
    public HomeToolbar homeToolbar() {
        return toolbarView;
    }

    @Override
    public Observable<Boolean> navigationViewVisibility() {
        return Observable.never();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        disposables.attach();
        disposables.add(clicks(createButton).subscribe(o ->
                navigator.execute(Transaction.push(SignatureCreateScreen.create(null)))));
        disposables.add(clicks(recentDocumentsButton).subscribe(o ->
                navigator.execute(Transaction.push(SignatureListScreen.create()))));
    }

    @Override
    public void onDetachedFromWindow() {
        disposables.detach();
        super.onDetachedFromWindow();
    }
}
