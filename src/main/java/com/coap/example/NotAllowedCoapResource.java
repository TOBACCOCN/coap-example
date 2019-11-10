package com.coap.example;

import org.eclipse.californium.core.network.Exchange;

public class NotAllowedCoapResource extends SimpleCoapResource {

    NotAllowedCoapResource(String name) {
        super(name);
    }

    public void handleRequest(Exchange exchange) {
        super.handleRequest(exchange);
    }
}
