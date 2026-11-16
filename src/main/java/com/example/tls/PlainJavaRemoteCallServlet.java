package com.example.tls;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import jakarta.servlet.annotation.WebServlet;

/** Probes the configured HTTPS endpoint using the JDK's HttpClient. */
@WebServlet(name = "PlainJavaRemoteCallServlet", urlPatterns = "/remote-jdk")
public class PlainJavaRemoteCallServlet extends RemoteProbeServlet {

    private static final long serialVersionUID = 1L;

    private transient HttpClient httpClient;
    private transient HttpRequest request;

    @Override
    void configureClient(SSLContext sslContext) {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(TLS_PROTOCOLS);

        request = HttpRequest.newBuilder(remoteUri()).timeout(RESPONSE_TIMEOUT).build();
        httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .sslParameters(sslParameters)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    @Override
    int fetchStatus() throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Override
    public void destroy() {
        if (httpClient != null) {
            httpClient.close();
        }
    }
}
