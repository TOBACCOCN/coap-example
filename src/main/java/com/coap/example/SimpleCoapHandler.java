package com.coap.example;

import org.eclipse.californium.core.server.resources.CoapExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CoapHandler
@CoapMapping("/coap")
public class SimpleCoapHandler {

    private static Logger logger = LoggerFactory.getLogger(SimpleCoapHandler.class);

    @CoapMapping(value = "/foo")
    public void foo(CoapExchange exchange) {
        logger.info(">>>>> URL_PARAM: [{}]", exchange.getRequestOptions().getUriQueryString());
        logger.info(">>>>> FOO: [{}]", exchange.getQueryParameter("foo"));
        logger.info(">>>>> CONTENT_FORMAT: [{}]", exchange.getRequestOptions().getContentFormat());
        logger.info(">>>>> REQUEST_BODY: [{}]", exchange.getRequestText());
        exchange.respond("FOO");
    }

    @CoapMapping("/fooooo")
    public void fooooo(CoapExchange exchange) {
        logger.info(">>>>> URL_PARAM: [{}]", exchange.getRequestOptions().getUriQueryString());
        logger.info(">>>>> FOOOOO: [{}]", exchange.getQueryParameter("fooooo"));
        logger.info(">>>>> CONTENT_FORMAT: [{}]", exchange.getRequestOptions().getContentFormat());
        logger.info(">>>>> REQUEST_BODY: [{}]", exchange.getRequestText());
        exchange.respond("FOOOOO");
    }

}
