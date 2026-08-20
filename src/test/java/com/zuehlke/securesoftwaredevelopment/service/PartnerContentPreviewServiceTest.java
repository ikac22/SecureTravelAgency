package com.zuehlke.securesoftwaredevelopment.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartnerContentPreviewServiceTest {

    @Test
    void userControlledUrlCanReachLoopbackService() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal", exchange -> {
            byte[] body = "internal-only-content".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            String body = new PartnerContentPreviewService().fetch("http://127.0.0.1:" + port + "/internal");
            assertEquals("internal-only-content", body);
        } finally {
            server.stop(0);
        }
    }
}
