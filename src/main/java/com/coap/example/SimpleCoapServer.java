package com.coap.example;

import com.coap.example.CredentialsUtil.Mode;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.stream.Stream;

import static com.coap.example.CredentialsUtil.DEFAULT_SERVER_MODES;
import static com.coap.example.CredentialsUtil.SERVER_NAME;

@Component
public class SimpleCoapServer {

    private static Logger logger = LoggerFactory.getLogger(SimpleCoapServer.class);

    private static final List<CredentialsUtil.Mode> SUPPORTED_MODES =
            Arrays.asList(Mode.PSK, Mode.ECDHE_PSK, Mode.RPK, Mode.X509, Mode.WANT_AUTH, Mode.NO_AUTH);

    // 服务端可以不需要受信任的证书
    // private String[] args = new String[]{Mode.PSK.toString(), Mode.ECDHE_PSK.toString(), Mode.RPK.toString(),
    //         Mode.X509.toString(), Mode.WANT_AUTH.toString()};
    private String[] args = new String[]{Mode.PSK.toString(), Mode.ECDHE_PSK.toString(), Mode.RPK.toString(),
            Mode.WANT_AUTH.toString()};
    private Map<String, Method> url2MethodMap = new HashMap<>();

    @Autowired
    private SimpleApplicationContextAware simpleApplicationContextAware;

    @Value("${coap.port}")
    private int port;


    public void start() {
        CoapServer coapServer = new CoapServer();

        // 添加 DTLS 支持，也就是通过 coaps 安全方式访问
        // CoapEndpoint.Builder endpointBuilder = new CoapEndpoint.Builder();
        // endpointBuilder.setConnector(new DTLSConnector(initDtlsConfigBuilder()));
        // coapServer.addEndpoint(endpointBuilder.build());

        Map<String, Object> coapHandlerMap =
                simpleApplicationContextAware.getApplicationContext().getBeansWithAnnotation(CoapHandler.class);

        // 进行 url 到 method 的映射
        SimpleCoapResource mainCoapResource = null;
        for (Object coapHandler : coapHandlerMap.values()) {
            Class<?> clazz = coapHandler.getClass();
            CoapMapping classCoapMapping = clazz.getAnnotation(CoapMapping.class);
            String[] classMappingUrls = classCoapMapping == null ? new String[]{""} : classCoapMapping.value();

            SimpleCoapResource classCoapResource = null;
            for (Method method : clazz.getMethods()) {
                CoapMapping methodCoapMapping = method.getAnnotation(CoapMapping.class);
                if (methodCoapMapping != null) {
                    CoapMethod[] coapMethods = methodCoapMapping.method();
                    List<Integer> coapMethodValues = new ArrayList<>();
                    Stream.of(coapMethods).forEach(coapMethod -> coapMethodValues.add(coapMethod.value));

                    SimpleCoapResource methodCoapResource = null;
                    for (String classMappingUrl : classMappingUrls) {
                        SimpleCoapResource classUrlCoapResource = null;
                        for (String methodMappingUrl : methodCoapMapping.value()) {

                            String url = classMappingUrl + methodMappingUrl;
                            if (url.startsWith("/")) {
                                url = url.substring(1);
                            }
                            if (url.endsWith("/")) {
                                url = url.substring(0, url.length() - 1);
                            }

                            if (url2MethodMap.keySet().contains(url)) {
                                throw new IllegalStateException(">>>>> Ambiguous mapping. Cannot map \n[" + method +
                                        "] \nto [" + url + "]\n. There is already \n[" + url2MethodMap.get(url) +
                                        "]\n mapped.");
                            } else {
                                url2MethodMap.put(url, method);
                            }

                            String[] resources = url.split("/");

                            SimpleCoapResource methodUrlCoapResource = null;
                            for (int i = 0; i < resources.length; i++) {
                                if (resources.length == 1) {
                                    methodUrlCoapResource = getBusinessCoapResource(coapHandler, method,
                                            coapMethodValues, resources[i]);
                                } else {
                                    if (i == 0) {
                                        methodUrlCoapResource = new NotAllowedCoapResource(resources[i]);
                                    } else if (i == resources.length - 1) {
                                        methodUrlCoapResource =
                                                methodUrlCoapResource.add(getBusinessCoapResource(coapHandler, method,
                                                        coapMethodValues, resources[i]));
                                    } else {
                                        methodUrlCoapResource = methodUrlCoapResource.add(new NotAllowedCoapResource(resources[i]));
                                    }
                                }
                            }

                            classUrlCoapResource = adjustParentChild(classUrlCoapResource, methodUrlCoapResource);
                        }

                        methodCoapResource = adjustParentChild(methodCoapResource, classUrlCoapResource);
                    }

                    classCoapResource = adjustParentChild(classCoapResource, methodCoapResource);
                }
            }

            mainCoapResource = adjustParentChild(mainCoapResource, classCoapResource);
        }

        coapServer.add(mainCoapResource);
        coapServer.start();
    }

    private SimpleCoapResource adjustParentChild(SimpleCoapResource parent, SimpleCoapResource child) {
        if (parent == null) {
            parent = child;
        } else if (child != null) {
            parent.add(child);
        }
        return parent;
    }

    // https://github.com/eclipse/californium/blob/master/demo-apps/cf-secure/src/main/java/org/eclipse/californium/examples/SecureServer.java
    private DtlsConnectorConfig initDtlsConfigBuilder() {
        DtlsConnectorConfig.Builder builder = new DtlsConnectorConfig.Builder();
        CredentialsUtil.setupCid(args, builder);
        builder.setAddress(new InetSocketAddress(port));
        builder.setRecommendedCipherSuitesOnly(false);
        List<Mode> modes = CredentialsUtil.parse(args, DEFAULT_SERVER_MODES, SUPPORTED_MODES);
        CredentialsUtil.setupCredentials(builder, SERVER_NAME, modes);
        return builder.build();
    }

    private SimpleCoapResource getBusinessCoapResource(Object coapHandler, Method method,
                                                       List<Integer> coapMethodValues, String resource) {
        return new SimpleCoapResource(resource) {
            @Override
            public void handleRequest(Exchange exchange) {
                CoAP.Code code = exchange.getRequest().getCode();
                try {
                    if (coapMethodValues.size() == 0 || coapMethodValues.contains(code.value)) {
                        method.invoke(coapHandler, new CoapExchange(exchange, this));
                    } else {
                        super.handleRequest(exchange);
                    }
                } catch (Exception e) {
                    StringWriter stringWriter = new StringWriter();
                    e.printStackTrace(new PrintWriter(stringWriter, true));
                    logger.error(">>>>> INVOKE_METHOD_ERROR: [{}]", method.getName());
                    logger.error(stringWriter.toString());
                }
            }
        };
    }

}
