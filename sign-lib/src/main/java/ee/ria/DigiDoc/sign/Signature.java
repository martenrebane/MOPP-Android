package ee.ria.DigiDoc.sign;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

@AutoValue
public abstract class Signature {

    /**
     * Unique ID per container.
     */
    public abstract String id();

    /**
     * Name to display.
     */
    public abstract String name();

    /**
     * Created date and time.
     */
    public abstract Instant createdAt();

    /**
     * Status of the signature.
     */
    public abstract SignatureStatus status();

    /**
     * Diagnostics info.
     */
    public abstract String diagnosticsInfo();

    /**
     * Whether this signature is valid or invalid.
     *
     * Valid statuses:
     * {@link SignatureStatus#VALID}
     * {@link SignatureStatus#WARNING}
     * {@link SignatureStatus#NON_QSCD}
     *
     * Invalid statuses:
     * {@link SignatureStatus#INVALID}
     * {@link SignatureStatus#UNKNOWN}
     *
     * @return Validity of the signature.
     */
    public final boolean valid() {
        return !status().equals(SignatureStatus.INVALID)
                && !status().equals(SignatureStatus.UNKNOWN);
    }

    /**
     * Signature profile.
     */
    public abstract String profile();

    /**
     * Signer's certificate issuer.
     */
    public abstract String signersCertificateIssuer();

    /**
     * Signing certificate.
     */
    @Nullable
    public abstract X509Certificate signingCertificate();

    /**
     * Signature method.
     */
    public abstract String signatureMethod();

    /**
     * Signature format.
     */
    public abstract String signatureFormat();

    /**
     * Signature timestamp.
     */
    public abstract String signatureTimestamp();

    /**
     * Signature timestamp UTC.
     */
    public abstract String signatureTimestampUTC();

    /**
     * Signature hash value.
     */
    public abstract String hashValueOfSignature();

    /**
     * Timestamp certificate issuer.
     */
    public abstract String tsCertificateIssuer();

    /**
     * Timestamp certificate.
     */
    @Nullable
    public abstract X509Certificate tsCertificate();

    /**
     * OCSP certificate issuer.
     */
    public abstract String ocspCertificateIssuer();

    /**
     * OCSP certificate.
     */
    @Nullable
    public abstract X509Certificate ocspCertificate();

    /**
     * OCSP time.
     */
    public abstract String ocspTime();

    /**
     * OCSP time UTC.
     */
    public abstract String ocspTimeUTC();

    /**
     * Signer's mobile time UTC.
     */
    public abstract String signersMobileTimeUTC();

    /*
     * Signature roles.
     */
    public abstract List<String> roles();

    /**
     * Signature role city.
     */
    public abstract String city();

    /**
     * Signature role state / county.
     */
    public abstract String state();

    /**
     * Signature role country.
     */
    public abstract String country();

    /**
     * Signature role zip / postal code.
     */
    public abstract String zip();

    /**
     * Creates a new signature object.
     *
     * @param id Signature ID.
     * @param name Signature display name.
     * @param createdAt Signature created date and time.
     * @param status Signature status.
     * @param diagnosticsInfo Diagnostics info.
     * @param profile Signature profile.
     * @param signersCertificateIssuer Signer's certificate issuer.
     * @param signingCertificate Signing certificate.
     * @param signatureMethod Signature method.
     * @param signatureFormat Signature format.
     * @param signatureTimestamp Signature timestamp.
     * @param signatureTimestampUTC Signature timestamp UTC.
     * @param hashValueOfSignature Hash value of signature.
     * @param tsCertificateIssuer Timestamp certificate issuer.
     * @param tsCertificate Timestamp certificate.
     * @param ocspCertificateIssuer OCSP certificate issuer.
     * @param ocspCertificate OCSP certificate.
     * @param ocspTime OCSP time.
     * @param ocspTimeUTC OCSP time UTC.
     * @param signersMobileTimeUTC Signer's mobile time UTC.
     * @param roles Signature roles.
     * @param city Signature city.
     * @param state Signature state / county.
     * @param country Signature country.
     * @param zip Signature zip / postal code.
     *
     */
    public static Signature create(String id, String name, Instant createdAt,
                                   SignatureStatus status, String diagnosticsInfo, String profile,
                                   String signersCertificateIssuer, X509Certificate signingCertificate,
                                   String signatureMethod, String signatureFormat,
                                   String signatureTimestamp, String signatureTimestampUTC,
                                   String hashValueOfSignature, String tsCertificateIssuer, X509Certificate tsCertificate,
                                   String ocspCertificateIssuer, X509Certificate ocspCertificate,
                                   String ocspTime, String ocspTimeUTC, String signersMobileTimeUTC,
                                   List<String> roles, String city, String state,
                                   String country, String zip) {
        return new AutoValue_Signature(id, name, createdAt, status, diagnosticsInfo, profile,
                signersCertificateIssuer, signingCertificate, signatureMethod,
                signatureFormat, signatureTimestamp, signatureTimestampUTC,
                hashValueOfSignature, tsCertificateIssuer, tsCertificate,
                ocspCertificateIssuer, ocspCertificate, ocspTime, ocspTimeUTC,
                signersMobileTimeUTC, roles, city, state, country, zip);
    }

    @NonNull
    @Override
    public String toString() {
        return "Signature{" +
                "id=" + id() + " " +
                ", name=" + name() +
                ", createdAt=" + createdAt() +
                ", status=" + status() +
                ", diagnosticsInfo=" + diagnosticsInfo() +
                ", profile=" + profile() +
                ", signersCertificateIssuer=" + signersCertificateIssuer() +
                ", signingCertificate exists=" + (signingCertificate() != null) +
                ", signatureMethod=" + signatureMethod() +
                ", signatureFormat=" + signatureFormat() +
                ", signatureTimestamp=" + signatureTimestamp() +
                ", signatureTimestampUTC=" + signatureTimestampUTC() +
                ", hashValueOfSignature=" + hashValueOfSignature() +
                ", tsCertificateIssuer=" + tsCertificateIssuer() +
                ", tsCertificate exists=" + (tsCertificate() != null) +
                ", ocspCertificateIssuer=" + ocspCertificateIssuer() +
                ", ocspCertificate exists=" + (ocspCertificate() != null) +
                ", ocspTime=" + ocspTime() +
                ", ocspTimeUTC=" + ocspTimeUTC() +
                ", signersMobileTimeUTC=" + signersMobileTimeUTC() +
                ", roles=" + roles() +
                ", city=" + city() +
                ", state=" + state() +
                ", country=" + country() +
                ", zip=" + zip() +
                "}";
    }
}
