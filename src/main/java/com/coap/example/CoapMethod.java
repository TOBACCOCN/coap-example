package com.coap.example;

public enum CoapMethod {

    GET(1),
    POST(2),
    PUT(3),
    DELETE( 4),
    FETCH(5),
    PATCH(6),
    IPATCH(7);

    public final int value;

    private CoapMethod(int value) {
        this.value = value;
    }

}
