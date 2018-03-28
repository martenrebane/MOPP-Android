package ee.ria.DigiDoc.android.signature.update.idcard;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class IdCardData {

    public abstract String givenNames();

    public abstract String surname();

    public abstract String personalCode();

    static IdCardData create(String givenNames, String surname, String personalCode) {
        return new AutoValue_IdCardData(givenNames, surname, personalCode);
    }
}
