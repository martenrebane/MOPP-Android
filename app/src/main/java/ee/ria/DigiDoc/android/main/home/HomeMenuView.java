package ee.ria.DigiDoc.android.main.home;

import static android.util.TypedValue.COMPLEX_UNIT_SP;
import static ee.ria.DigiDoc.android.accessibility.AccessibilityUtils.isLargeFontEnabled;
import static ee.ria.DigiDoc.android.utils.TextUtil.convertPxToDp;
import static ee.ria.DigiDoc.android.utils.display.DisplayUtil.getDeviceOrientation;

import android.content.Context;
import android.content.res.Configuration;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import java.util.Locale;
import java.util.Set;

import ee.ria.DigiDoc.R;
import ee.ria.DigiDoc.android.utils.display.DisplayUtil;
import ee.ria.DigiDoc.android.utils.navigator.ContentView;
import ee.ria.DigiDoc.common.TextUtil;
import io.reactivex.rxjava3.core.Observable;

import static com.jakewharton.rxbinding4.view.RxView.clicks;
import static com.jakewharton.rxbinding4.widget.RxRadioGroup.checkedChanges;
import static ee.ria.DigiDoc.android.utils.TintUtils.tintCompoundDrawables;

public final class HomeMenuView extends NestedScrollView implements ContentView {

    private final View closeButton;

    private final Button helpView;
    private final Button accessibilityView;
    private final Button recentView;
    private final Button settingsView;
    private final Button aboutView;
    private final Button diagnosticsView;
    private final RadioGroup localeView;
    private final RadioButton estonianButton;
    private final RadioButton englishButton;
    private final RadioButton russianButton;

    private int initializationCount = 0;
    private static final int MAXIMUM_INITIALIZATION_COUNT = 5;
    private TextToSpeech textToSpeech;

    // Estonian TalkBack does not pronounce "dot"
    private final TextToSpeech.OnInitListener textToSpeechListener = new TextToSpeech.OnInitListener() {
        @Override
        public void onInit(int status) {
            if (status == TextToSpeech.SUCCESS) {
                Voice textToSpeechVoice = textToSpeech.getVoice();
                String language = Locale.getDefault().getLanguage();
                boolean isESTLanguageAvailable = isTextToSpeechLanguageAvailable(textToSpeech.getAvailableLanguages(),
                        Set.of(new Locale("est", "EST"), new Locale("et", "ET")));
                if (textToSpeechVoice != null) {
                    Locale textToSpeechLocale = textToSpeechVoice.getLocale();
                    if (textToSpeechLocale != null) {
                        String textToSpeechLanguage = textToSpeechLocale.getLanguage();
                        if (isESTLanguageAvailable ||
                                (textToSpeechLanguage.equals("et") ||
                                        textToSpeechLanguage.equals("est"))) {
                            language = "et";
                        }
                    }
                }
                if (language.equals("et")) {
                    helpView.setContentDescription(
                            getResources().getString(R.string.main_home_menu_help) +
                                    " link " +
                                    "w w w punkt i d punkt e e");
                } else {
                    helpView.setContentDescription(
                            getResources().getString(R.string.main_home_menu_help) + " " +
                                    TextUtil.splitTextAndJoin(
                                            getResources().getString(R.string.main_home_menu_help_url_short), "", " "));
                }
            } else {
                retryInitialization();
            }
        }
    };

    private void retryInitialization() {
        if (initializationCount < MAXIMUM_INITIALIZATION_COUNT) {
            initializationCount++;
            textToSpeech.shutdown();
            textToSpeech = new TextToSpeech(getContext(), textToSpeechListener);
        }
    }

    public HomeMenuView(@NonNull Context context) {
        this(context, null);
    }

    public HomeMenuView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HomeMenuView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inflate(context, R.layout.main_home_menu, this);
        closeButton = findViewById(R.id.mainHomeMenuCloseButton);
        closeButton.setContentDescription(getResources().getString(R.string.close_menu));
        helpView = findViewById(R.id.mainHomeMenuHelp);
        helpView.setContentDescription(
                getResources().getString(R.string.main_home_menu_help) + " " +
                        TextUtil.splitTextAndJoin(
                                getResources().getString(R.string.main_home_menu_help_url_short), "", " "));
        accessibilityView = findViewById(R.id.mainHomeMenuAccessibility);
        recentView = findViewById(R.id.mainHomeMenuRecent);
        settingsView = findViewById(R.id.mainHomeMenuSettings);
        aboutView = findViewById(R.id.mainHomeMenuAbout);
        diagnosticsView = findViewById(R.id.mainHomeMenuDiagnostics);
        localeView = findViewById(R.id.mainHomeMenuLocale);
        estonianButton = findViewById(R.id.mainHomeMenuLocaleEt);
        englishButton = findViewById(R.id.mainHomeMenuLocaleEn);
        russianButton = findViewById(R.id.mainHomeMenuLocaleRu);

        tintCompoundDrawables(helpView);
        tintCompoundDrawables(recentView);
        tintCompoundDrawables(settingsView);
        tintCompoundDrawables(aboutView);
        tintCompoundDrawables(diagnosticsView);

        helpView.postDelayed(() -> {
            helpView.requestFocus();
            helpView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        }, 2000);

        setFontSize();

        ContentView.addInvisibleElement(getContext(), this);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        setFontSize();
    }

    public Observable closeButtonClicks() {
        return clicks(closeButton);
    }

    @SuppressWarnings("unchecked")
    public Observable<Integer> itemClicks() {
        return Observable.mergeArray(
                clicks(helpView).map(ignored -> R.id.mainHomeMenuHelp),
                clicks(accessibilityView).map(ignored -> R.id.mainHomeMenuAccessibility),
                clicks(recentView).map(ignored -> R.id.mainHomeMenuRecent),
                clicks(settingsView).map(ignored -> R.id.mainHomeMenuSettings),
                clicks(aboutView).map(ignored -> R.id.mainHomeMenuAbout),
                clicks(diagnosticsView).map(ignored -> R.id.mainHomeMenuDiagnostics));
    }

    public void locale(@Nullable Integer locale) {
        if (locale == null) {
            localeView.clearCheck();
        } else {
            localeView.check(locale);
        }
    }

    public Observable<Integer> localeChecks() {
        return checkedChanges(localeView).skipInitialValue();
    }

    private boolean isTextToSpeechLanguageAvailable(Set<Locale> availableLocales, Set<Locale> locales) {
        if (availableLocales != null) {
            return locales.stream().anyMatch(lo ->
                    availableLocales.stream().anyMatch(al -> al.getLanguage().equals(lo.getLanguage()))
            );
        } else {
            return false;
        }
    }

    private void setFontSize() {
        if (isLargeFontEnabled(getResources())) {
            setLargeFontTextSize();
        } else {
            setRegularFontTextSize();
        }
    }

    private void setRegularFontTextSize() {
        setTextSize();
        setButtonsParameters(72, localeView);
        setAutoTextSizeNone();
    }

    private void setButtonsParameters(int height, View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = convertPxToDp(height, getContext());
        view.setLayoutParams(params);
    }

    private void setLargeFontTextSize() {
        setButtonsParameters(100, localeView);

        if (getDeviceOrientation(getContext()) == Configuration.ORIENTATION_LANDSCAPE) {
            setTextSize();
            setAutoTextSizeNone();
        } else {
            setAutoTextSize();
        }
    }

    private void setTextSize() {
        int textSize = 18;
        estonianButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
        englishButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
        russianButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
    }

    private void setAutoTextSize() {
        int minTextSize = 17;
        int maxTextSize = 19;
        int largeMaxTextSize = 21;
        int autoStepSize = 1;

        estonianButton.setAutoSizeTextTypeUniformWithConfiguration(
                minTextSize, maxTextSize, autoStepSize, TypedValue.COMPLEX_UNIT_DIP);
        englishButton.setAutoSizeTextTypeUniformWithConfiguration(
                minTextSize, largeMaxTextSize, autoStepSize, TypedValue.COMPLEX_UNIT_DIP);
        russianButton.setAutoSizeTextTypeUniformWithConfiguration(
                minTextSize, largeMaxTextSize, autoStepSize, TypedValue.COMPLEX_UNIT_DIP);
    }

    private void setAutoTextSizeNone() {
        estonianButton.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
        englishButton.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
        russianButton.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
    }
}
