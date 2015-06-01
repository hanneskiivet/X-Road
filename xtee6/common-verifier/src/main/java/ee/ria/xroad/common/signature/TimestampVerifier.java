package ee.ria.xroad.common.signature;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.tsp.TimeStampToken;

import ee.ria.xroad.common.CodedException;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;

/**
 * Contains methods for verifying various time-stamp characteristics.
 */
@Slf4j
public final class TimestampVerifier {

    private TimestampVerifier() {
    }

    /**
     * Verifies that time-stamp applies to <code>stampedData</code>
     * and that it is signed by a trusted time-stamping authority
     * @param tsToken the time-stamp token
     * @param stampedData the allegedly time-stamped data
     * @param tspCerts list of TSP certificates
     * @throws Exception if the verification failed
     */
    public static void verify(TimeStampToken tsToken,
            byte[] stampedData, List<X509Certificate> tspCerts)
                    throws Exception {
        String thatHash = encodeBase64(calculateDigest(
                tsToken.getTimeStampInfo().getHashAlgorithm(), stampedData));
        String thisHash = encodeBase64(
                tsToken.getTimeStampInfo().getMessageImprintDigest());
        if (!thisHash.equals(thatHash)) {
            throw new CodedException(X_MALFORMED_SIGNATURE,
                    "Timestamp hashes do not match");
        }

        verify(tsToken, tspCerts);
    }

    /**
     * Verifies that the time-stamp token is signed by a trusted
     * time-stamping authority.
     * @param tsToken the time-stamp token
     * @param tspCerts list of TSP certificates
     * @throws Exception if the verification failed
     */
    public static void verify(TimeStampToken tsToken,
            List<X509Certificate> tspCerts) throws Exception {
        if (tspCerts.isEmpty()) {
            throw new CodedException(
                    X_INTERNAL_ERROR,
                    "No TSP service providers are configured.");
        }

        SignerId signerId = tsToken.getSID();

        X509Certificate cert = getTspCertificate(signerId, tspCerts);
        if (cert == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not find TSP certificate for timestamp");
        }

        SignerInformation signerInfo =
                tsToken.toCMSSignedData().getSignerInfos().get(signerId);
        if (signerInfo == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not get signer information for "
                            + signerId.getSerialNumber());
        }

        SignerInformationVerifier verifier = createVerifier(cert);
        if (!signerInfo.verify(verifier)) {
            throw new CodedException(X_TIMESTAMP_VALIDATION,
                    "Failed to verify timestamp");
        }
    }

    /**
     * Retrieves the time-stamp signer certificate.
     * @param tsToken the time-stamp token
     * @param tspCerts list of TSP certificates
     * @return X509Certificate
     * @throws Exception in case of any errors
     */
    public static X509Certificate getSignerCertificate(
            TimeStampToken tsToken, List<X509Certificate> tspCerts)
                    throws Exception {
        SignerId signerId = tsToken.getSID();

        return getTspCertificate(signerId, tspCerts);
    }

    private static X509Certificate getTspCertificate(SignerId signerId,
            List<X509Certificate> tspCerts) throws Exception {
        log.trace("getTspCertificate({}, {}, {})",
                new Object[] {signerId.getIssuer(), signerId.getSerialNumber(),
                Arrays.toString(signerId.getSubjectKeyIdentifier())});
        for (X509Certificate cert : tspCerts) {
            log.trace("Comparing with cert: {}, {}",
                    cert.getIssuerDN(), cert.getSerialNumber());
            if (signerId.match(new X509CertificateHolder(cert.getEncoded()))) {
                return cert;
            }
        }

        return null;
    }

    private static SignerInformationVerifier createVerifier(
            X509Certificate cert) throws OperatorCreationException {
        JcaSimpleSignerInfoVerifierBuilder verifierBuilder =
                new JcaSimpleSignerInfoVerifierBuilder();
        verifierBuilder.setProvider("BC");

        return verifierBuilder.build(cert);
    }

}