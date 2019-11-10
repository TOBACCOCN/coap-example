package com.coap.example;

import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@CoapHandler
@CoapMapping("/coap")
public class UpDownloadCoapHandler {

    private static Logger logger = LoggerFactory.getLogger(UpDownloadCoapHandler.class);

    @CoapMapping("/upload")
    public void upload(CoapExchange exchange) throws IOException {
        logger.info(">>>>> URL_PARAM: [{}]", exchange.getRequestOptions().getUriQueryString());
        logger.info(">>>>> CONTENT_FORMAT: [{}]", exchange.getRequestOptions().getContentFormat());
        String filename = exchange.getQueryParameter("filename");
        byte[] bytes = exchange.getRequestPayload();
        String uploadPath = "D:\\" + filename;
        IOUtil.write2File(bytes, uploadPath);
        exchange.respond("UPLOAD_SUCCESS");
    }

    @CoapMapping("/download")
    public void download(CoapExchange exchange) throws IOException {
        logger.info(">>>>> URL_PARAM: [{}]", exchange.getRequestOptions().getUriQueryString());
        logger.info(">>>>> CONTENT_FORMAT: [{}]", exchange.getRequestOptions().getContentFormat());
        logger.info(">>>>> REQUEST_BODY: [{}]", exchange.getRequestText());
        String filename = exchange.getQueryParameter("filename");
        String downloadFilePath = "D:\\download\\" + filename;
        IOUtil.file2Bytes(downloadFilePath);
        exchange.respond(CoAP.ResponseCode.CONTENT,
                IOUtil.file2Bytes(downloadFilePath), MediaTypeRegistry.APPLICATION_OCTET_STREAM);
    }

}
