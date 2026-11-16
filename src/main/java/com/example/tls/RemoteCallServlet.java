package com.example.tls;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.util.Timeout;

import jakarta.servlet.annotation.WebServlet;

/** Probes the configured HTTPS endpoint using Apache HttpClient 5. */
@WebServlet(name = "RemoteCallServlet", urlPatterns = "/remote")
public class RemoteCallServlet extends RemoteProbeServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(RemoteCallServlet.class.getName());

    private transient CloseableHttpClient httpClient;

    @Override
    void configureClient(SSLContext sslContext) {
        Timeout connectTimeout = Timeout.of(CONNECT_TIMEOUT);
        Timeout responseTimeout = Timeout.of(RESPONSE_TIMEOUT);
        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setTlsSocketStrategy(ClientTlsStrategyBuilder.create()
                        .setSslContext(sslContext)
                        .setTlsVersions(TLS_PROTOCOLS)
                        .buildClassic())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(connectTimeout)
                        .setSocketTimeout(responseTimeout)
                        .build())
                .setMaxConnTotal(20)
                .setMaxConnPerRoute(10)
                .build();

        httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(connectTimeout)
                        .setResponseTimeout(responseTimeout)
                        .build())
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .build();
    }

    @Override
    int fetchStatus() throws IOException {
        return httpClient.execute(new HttpGet(remoteUri()), response -> response.getCode());
    }

    @Override
    public void destroy() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                LOG.log(Level.FINE, "Error closing HTTP client", e);
            }
        }
    }
}
