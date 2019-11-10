package com.coap.example;

import org.eclipse.californium.elements.util.SslContextUtil;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.CertificateType;
import org.eclipse.californium.scandium.dtls.SingleNodeConnectionIdGenerator;
import org.eclipse.californium.scandium.dtls.cipher.CipherSuite;
import org.eclipse.californium.scandium.dtls.cipher.CipherSuite.KeyExchangeAlgorithm;
import org.eclipse.californium.scandium.dtls.pskstore.InMemoryPskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Credentials utility for setup DTLS credentials.
 */
public class CredentialsUtil {

    private static Logger logger = LoggerFactory.getLogger(CredentialsUtil.class);

    /**
     * Credentials mode.
     */
    public enum Mode {
        /**
         * Preshared secret keys.
         */
        PSK,
        /**
         * EC DHE, preshared secret keys.
         */
        ECDHE_PSK,
        /**
         * Raw public key certificates.
         */
        RPK,
        /**
         * X.509 certificates.
         */
        X509,
        /**
         * raw public key certificates just trusted (client only).
         */
        RPK_TRUST,
        /**
         * X.509 certificates just trusted (client only).
         */
        X509_TRUST,
        /**
         * Client authentication wanted (server only).
         */
        WANT_AUTH,
        /**
         * No client authentication (server only).
         */
        NO_AUTH,
    }

    /**
     * Default list of modes for clients.
     * <p>
     * Value is PSK, RPK, X509.
     */
    public static final List<Mode> DEFAULT_CLIENT_MODES = Arrays.asList(Mode.PSK, Mode.RPK, Mode.X509);

    /**
     * Default list of modes for servers.
     * <p>
     * Value is PSK, ECDHE_PSK, RPK, X509.
     */
    public static final List<Mode> DEFAULT_SERVER_MODES =
            Arrays.asList(Mode.PSK, Mode.ECDHE_PSK, Mode.RPK, Mode.X509);

    // from ETSI Plugtest test spec
    public static final String PSK_IDENTITY = "password";
    public static final byte[] PSK_SECRET = "sesame".getBytes();

    public static final String OPEN_PSK_IDENTITY = "Client_identity";
    public static final byte[] OPEN_PSK_SECRET = "secretPSK".getBytes();

    // CID
    public static final String OPT_CID = "CID:";
    public static final int DEFAULT_CID_LENGTH = 6;

    // from demo-certs
    public static final String SERVER_NAME = "server";
    public static final String CLIENT_NAME = "client";
    private static final String TRUST_NAME = "root";
    private static final char[] TRUST_STORE_PASSWORD = "rootPass".toCharArray();
    private static final char[] KEY_STORE_PASSWORD = "endPass".toCharArray();
    private static final String KEY_STORE_LOCATION = "certs/keyStore.jks";
    private static final String TRUST_STORE_LOCATION = "certs/trustStore.jks";

    private static final String[] OPT_CID_LIST = {OPT_CID};

    /**
     * Get opt-cid for argument.
     *
     * @param arg command line argument
     * @return opt-cid, of {@code null}, if argument is no opt-cid
     */
    private static String getOptCid(String arg) {
        for (String opt : OPT_CID_LIST) {
            if (arg.startsWith(opt)) {
                return opt;
            }
        }
        return null;
    }

    /**
     * Setup connection id configuration.
     * <p>
     * Supports "CID:length" for using CID after the handshake, "CID+:length" to sue
     * a CID even during the handshake.
     *
     * @param args    command line arguments
     * @param builder dtls configuration builder.
     */
    public static void setupCid(String[] args, DtlsConnectorConfig.Builder builder) {
        for (String mode : args) {
            String opt = getOptCid(mode);
            if (opt != null) {
                String value = mode.substring(opt.length());
                int cidLength = DEFAULT_CID_LENGTH;
                try {
                    cidLength = Integer.parseInt(value);
                    if (cidLength < 0) {
                        logger.error(">>>>> [{}] IS NEGATIVE! USE CID-LENGTH DEFAULT [{}]", value, DEFAULT_CID_LENGTH);
                        cidLength = DEFAULT_CID_LENGTH;
                    }
                } catch (NumberFormatException e) {
                    logger.error(">>>>> [{}] IS NO NUMBER! USE CID-LENGTH DEFAULT [{}]", value, DEFAULT_CID_LENGTH);
                }
                builder.setConnectionIdGenerator(new SingleNodeConnectionIdGenerator(cidLength));
                if (cidLength == 0) {
                    logger.info(">>>>> ENABLE CID SUPPORT");
                } else {
                    logger.info(">>>>> USE [{}] BYTES CID", cidLength);
                }
            }
        }
    }

    /**
     * Parse arguments to modes.
     *
     * @param args      arguments
     * @param defaults  default modes to use, if argument is empty or only contains
     *                  {@link Mode#NO_AUTH}.
     * @param supported supported modes
     * @return array of modes.
     */
    public static List<Mode> parse(String[] args, List<Mode> defaults, List<Mode> supported) {
        List<Mode> modes;
        if (args.length == 0) {
            modes = new ArrayList<>();
        } else {
            modes = new ArrayList<>(args.length);
            for (String mode : args) {
                if (getOptCid(mode) != null) {
                    continue;
                }
                try {
                    modes.add(Mode.valueOf(mode));
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Argument '" + mode + "' unkown!");
                }
            }
        }
        if (supported != null) {
            for (Mode mode : modes) {
                if (!supported.contains(mode)) {
                    throw new IllegalArgumentException("Mode '" + mode + "' not supported!");
                }
            }
        }
        if (defaults != null) {
            if (modes.isEmpty()
                    || (modes.size() == 1 && (modes.contains(Mode.NO_AUTH) || modes.contains(Mode.WANT_AUTH)))) {
                // adjust defaults, also for only "NO_AUTH"
                modes.addAll(defaults);
            }
        }
        return modes;
    }

    /**
     * Setup credentials for DTLS connector.
     * <p>
     * If PSK is provided and no PskStore is already set for the builder, a
     * {@link InMemoryPskStore} containing {@link #PSK_IDENTITY} assigned with
     * {@link #PSK_SECRET} is set. If PSK is provided with other mode(s) and
     * loading the certificates failed, this is just treated as warning and the
     * configuration is setup to use PSK only.
     * <p>
     * If RPK is provided, the certificates loaded for the provided alias and
     * this certificate is used as identity.
     * <p>
     * If X509 is provided, the trusts are also loaded an set additionally to
     * the credentials for the alias.
     * <p>
     * The Modes can be mixed. If RPK is before X509 in the list, RPK is set as
     * preferred.
     * <p>
     * Examples:
     *
     * <pre>
     * PSK, RPK setup for PSK an RPK.
     * RPK, X509 setup for RPK and X509, prefer RPK
     * PSK, X509, RPK setup for PSK, RPK and X509, prefer X509
     * </pre>
     *
     * @param config           DTLS configuration builder. May be already initialized with
     *                         PskStore.
     * @param certificateAlias alias for certificate to load as credentials.
     * @param modes            list of supported mode. If a RPK is in the list before X509,
     *                         or RPK is provided but not X509, then the RPK is setup as
     *                         preferred.
     * @throws IllegalArgumentException if loading the certificates fails for some reason
     */
    public static void setupCredentials(DtlsConnectorConfig.Builder config, String certificateAlias, List<Mode> modes) {

        boolean ecdhePsk = modes.contains(Mode.ECDHE_PSK);
        boolean plainPsk = modes.contains(Mode.PSK);
        boolean psk = ecdhePsk || plainPsk;

        if (psk && config.getIncompleteConfig().getPskStore() == null) {
            // Pre-shared secret keys
            InMemoryPskStore pskStore = new InMemoryPskStore();
            pskStore.setKey(PSK_IDENTITY, PSK_SECRET);
            pskStore.setKey(OPEN_PSK_IDENTITY, OPEN_PSK_SECRET);
            config.setPskStore(pskStore);
        }
        boolean noAuth = modes.contains(Mode.NO_AUTH);
        boolean x509Trust = modes.contains(Mode.X509_TRUST);
        boolean rpkTrust = modes.contains(Mode.RPK_TRUST);
        int x509 = modes.indexOf(Mode.X509);
        int rpk = modes.indexOf(Mode.RPK);

        if (noAuth) {
            if (x509Trust) {
                throw new IllegalArgumentException(Mode.NO_AUTH + " doesn't support " + Mode.X509_TRUST);
            }
            if (rpkTrust) {
                throw new IllegalArgumentException(Mode.NO_AUTH + " doesn't support " + Mode.RPK_TRUST);
            }
            config.setClientAuthenticationRequired(false);
        } else if (modes.contains(Mode.WANT_AUTH)) {
            config.setClientAuthenticationWanted(true);
        }

        if (x509 >= 0 || rpk >= 0) {
            try {
                // try to read certificates
                SslContextUtil.Credentials serverCredentials = SslContextUtil.loadCredentials(
                        SslContextUtil.CLASSPATH_SCHEME + KEY_STORE_LOCATION, certificateAlias, KEY_STORE_PASSWORD,
                        KEY_STORE_PASSWORD);
                if (!noAuth) {
                    if (x509 >= 0) {
                        Certificate[] trustedCertificates = SslContextUtil.loadTrustedCertificates(
                                SslContextUtil.CLASSPATH_SCHEME + TRUST_STORE_LOCATION, TRUST_NAME,
                                TRUST_STORE_PASSWORD);
                        config.setTrustStore(trustedCertificates);
                    }
                    if (rpk >= 0) {
                        config.setRpkTrustAll();
                    }
                }
                List<CertificateType> types = new ArrayList<>();
                if (x509 >= 0 && rpk >= 0) {
                    if (rpk < x509) {
                        types.add(CertificateType.RAW_PUBLIC_KEY);
                        types.add(CertificateType.X_509);
                    } else {
                        types.add(CertificateType.X_509);
                        types.add(CertificateType.RAW_PUBLIC_KEY);
                    }
                } else if (x509 >= 0) {
                    types.add(CertificateType.X_509);
                } else {
                    types.add(CertificateType.RAW_PUBLIC_KEY);
                }
                config.setIdentity(serverCredentials.getPrivateKey(), serverCredentials.getCertificateChain(), types);
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
                logger.error(">>>>> CERTIFICATES ARE INVALID!");
                if (psk) {
                    logger.error(">>>>> THEREFORE CERTIFICATES ARE NOT SUPPORTED!");
                } else {
                    throw new IllegalArgumentException(e.getMessage());
                }
            } catch (IOException e) {
                e.printStackTrace();
                logger.error(">>>>> CERTIFICATES ARE MISSING!");
                if (psk) {
                    logger.error(">>>>> THEREFORE CERTIFICATES ARE NOT SUPPORTED!");
                } else {
                    throw new IllegalArgumentException(e.getMessage());
                }
            }
        }
        if (x509Trust) {
            // trust all
            config.setTrustStore(new Certificate[0]);
        }
        if (rpkTrust) {
            // trust all
            config.setRpkTrustAll();
        }
        if (psk && config.getIncompleteConfig().getSupportedCipherSuites() == null) {
            List<CipherSuite> suites = new ArrayList<>();
            if (x509 >= 0 || rpk >= 0 || x509Trust || rpkTrust) {
                suites.addAll(CipherSuite.getEcdsaCipherSuites(false));
            }
            if (ecdhePsk) {
                suites.addAll(CipherSuite.getCipherSuitesByKeyExchangeAlgorithm(false, KeyExchangeAlgorithm.ECDHE_PSK));
            }
            if (plainPsk) {
                suites.addAll(CipherSuite.getCipherSuitesByKeyExchangeAlgorithm(false, KeyExchangeAlgorithm.PSK));
            }
            config.setSupportedCipherSuites(suites);
        }
    }
}
