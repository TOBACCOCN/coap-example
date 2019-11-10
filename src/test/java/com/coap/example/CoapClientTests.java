package com.coap.example;

import com.alibaba.fastjson.JSONObject;
import com.coap.example.CredentialsUtil.Mode;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.Utils;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.cipher.CipherSuite;
import org.eclipse.californium.scandium.dtls.pskstore.StaticPskStore;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class CoapClientTests {

    private static Logger logger = LoggerFactory.getLogger(CoapClientTests.class);

    private static final List<Mode> SUPPORTED_MODES = Arrays.asList(Mode.PSK, Mode.ECDHE_PSK, Mode.RPK, Mode.X509,
            Mode.RPK_TRUST, Mode.X509_TRUST);

    private String[] args = new String[]{Mode.PSK.toString(), Mode.RPK.toString(), Mode.X509.toString()};

    private CoapHandler getClientCoapHandler(CountDownLatch countDown, String filename) {
        return new CoapHandler() {
            @Override
            public void onLoad(CoapResponse response) {
                if (response.isSuccess()) {
                    if (MediaTypeRegistry.APPLICATION_OCTET_STREAM == response.getOptions().getContentFormat()) {
                        String filePath = "D:\\" + filename;
                        try {
                            IOUtil.write2File(response.getPayload(), filePath);
                        } catch (IOException e) {
                            StringWriter stringWriter = new StringWriter();
                            e.printStackTrace(new PrintWriter(stringWriter, true));
                            logger.error(stringWriter.toString());
                        }
                    } else {
                        logger.info(">>>>> ON_LOAD: \r\n{}", Utils.prettyPrint(response));
                    }

                }
                countDown.countDown();
            }

            @Override
            public void onError() {
                logger.info(">>>>> ON_ERROR");
                countDown.countDown();
            }
        };
    }

    @Test
    void get() throws InterruptedException {
        long start = Clock.systemUTC().millis();

        CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
        builder.setConnector(getDtlsConnector());
        // CoapClient coapClient =
        //         new CoapClient("coaps://192.168.1.126:5683/coap/foo?foo=fooo").setEndpoint(builder.build());
        CoapClient coapClient =
                new CoapClient("coaps://test-trans.eliteei.com:5683/coap/foo?foo=foo").setEndpoint(builder.build());
        // CoapClient coapClient =
        //         new CoapClient("coaps://127.0.0.1:5684/coap/foo?foo=foo").setEndpoint(builder.build());
        // CoapClient coapClient =
        //         new CoapClient("coaps://127.0.0.1:5684/coap/fooooo?fooooo=fooooo").setEndpoint(builder.build());
        // CoapClient coapClient =
        //         new CoapClient("coaps://127.0.0.1:5684/coap/bar?bar=bar").setEndpoint(builder.build());
        // CoapClient coapClient =
        //         new CoapClient("coaps://127.0.0.1:5684/coap/baz?baz=baz").setEndpoint(builder.build());
        // CoapClient coapClient =
        //         new CoapClient("coaps://127.0.0.1:5684/coap?").setEndpoint(builder.build());

        CountDownLatch countDown = new CountDownLatch(1);
        coapClient.get(getClientCoapHandler(countDown, null));

        countDown.await();
        coapClient.shutdown();

        long end = Clock.systemUTC().millis();
        logger.info(">>>>> COST: [{}] MS", end - start);
    }

    @Test
    void post() throws InterruptedException {
        long start = Clock.systemUTC().millis();

        CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
        builder.setConnector(getDtlsConnector());
        CoapClient coapClient =
                new CoapClient("coaps://127.0.0.1:5684/coap/foo?foo=foo").setEndpoint(builder.build());

        CountDownLatch countDown = new CountDownLatch(1);
        coapClient.post(getClientCoapHandler(countDown, null), "payload message", MediaTypeRegistry.TEXT_PLAIN);

        countDown.await();
        coapClient.shutdown();

        long end = Clock.systemUTC().millis();
        logger.info(">>>>> COST: [{}] MS", end - start);
    }

    @Test
    void postJson() throws InterruptedException {
        long start = Clock.systemUTC().millis();

        CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
        builder.setConnector(getDtlsConnector());
        CoapClient coapClient =
                new CoapClient("coaps://127.0.0.1:5684/coap/foo?foo=foo").setEndpoint(builder.build());

        CountDownLatch countDown = new CountDownLatch(1);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("message", "I am coap client");

        coapClient.post(getClientCoapHandler(countDown, null),
                jsonObject.toString(), MediaTypeRegistry.APPLICATION_JSON);

        countDown.await();
        coapClient.shutdown();

        long end = Clock.systemUTC().millis();
        logger.info(">>>>> COST: [{}] MS", end - start);
    }

    @Test
    void postBytes() throws InterruptedException, IOException {
        long start = Clock.systemUTC().millis();

        CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
        builder.setConnector(getDtlsConnector());
        CoapClient coapClient = new CoapClient("coaps://127.0.0.1:5684/coap/upload?filename=shiro.properties")
                .setEndpoint(builder.build());

        CountDownLatch countDown = new CountDownLatch(1);
        String filePath = "D:\\download\\shiro.properties";

        coapClient.post(getClientCoapHandler(countDown, null),
                IOUtil.file2Bytes(filePath), MediaTypeRegistry.APPLICATION_OCTET_STREAM);

        countDown.await();
        coapClient.shutdown();

        long end = Clock.systemUTC().millis();
        logger.info(">>>>> COST: [{}] MS", end - start);
    }

    @Test
    void download() throws InterruptedException {
        long start = Clock.systemUTC().millis();

        CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
        builder.setConnector(getDtlsConnector());
        String filename = "shiro.properties";
        CoapClient coapClient = new CoapClient("coaps://127.0.0.1:5684/coap/download?filename=" + filename);
        coapClient.setEndpoint(builder.build());

        CountDownLatch countDown = new CountDownLatch(1);

        coapClient.get(getClientCoapHandler(countDown, filename));

        countDown.await();
        coapClient.shutdown();

        long end = Clock.systemUTC().millis();
        logger.info(">>>>> COST: [{}] MS", end - start);
    }

    private DTLSConnector getDtlsConnector() {
        DtlsConnectorConfig.Builder builder = new DtlsConnectorConfig.Builder();
        CredentialsUtil.setupCid(args, builder);
        builder.setClientOnly();
        builder.setSniEnabled(false);
        builder.setRecommendedCipherSuitesOnly(false);
        List<Mode> modes = CredentialsUtil.parse(args, CredentialsUtil.DEFAULT_CLIENT_MODES, SUPPORTED_MODES);
        if (modes.contains(Mode.PSK) || modes.contains(Mode.ECDHE_PSK)) {
            builder.setPskStore(new StaticPskStore(CredentialsUtil.OPEN_PSK_IDENTITY,
                    CredentialsUtil.OPEN_PSK_SECRET));
        } else {
            builder.setSupportedCipherSuites(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256);
        }
        CredentialsUtil.setupCredentials(builder, CredentialsUtil.CLIENT_NAME, modes);
        return new DTLSConnector(builder.build());
    }

}
