package com.coap.example;

import org.eclipse.californium.core.server.resources.CoapExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CoapHandler
@CoapMapping("/coap")
public class DefaultCoapHandler {

    private static Logger logger = LoggerFactory.getLogger(DefaultCoapHandler.class);

    @CoapMapping(value = "/bar")
    public void bar(CoapExchange exchange) {
        logger.info(">>>>> URL_PARAM: [{}]", exchange.getRequestOptions().getUriQueryString());
        logger.info(">>>>> BAR: [{}]", exchange.getQueryParameter("bar"));
        logger.info(">>>>> CONTENT_FORMAT: [{}]", exchange.getRequestOptions().getContentFormat());
        logger.info(">>>>> REQUEST_BODY: [{}]", exchange.getRequestText());
        exchange.respond("BAR");
    }

    @CoapMapping("/baz")
    public void baz(CoapExchange exchange) {
        logger.info(">>>>> URL_PARAM: [{}]", exchange.getRequestOptions().getUriQueryString());
        logger.info(">>>>> BAZ: [{}]", exchange.getQueryParameter("baz"));
        logger.info(">>>>> CONTENT_FORMAT: [{}]", exchange.getRequestOptions().getContentFormat());
        logger.info(">>>>> REQUEST_BODY: [{}]", exchange.getRequestText());
        exchange.respond("BAZ");
    }

}
