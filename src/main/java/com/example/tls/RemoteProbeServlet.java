package com.example.tls;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

import org.apache.hc.core5.http.ConnectionRequestTimeoutException;

import com.ibm.websphere.ssl.JSSEHelper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

abstract class RemoteProbeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(RemoteProbeServlet.class.getName());

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(10);
    static final String[] TLS_PROTOCOLS = { "TLSv1.3", "TLSv1.2" };

    private transient URI remoteUri;

    @Override
    public final void init() throws ServletException {
        String target = System.getProperty("remote.url", System.getenv("REMOTE_URL"));
        try {
            remoteUri = new URI(target == null || target.isBlank() ? "https://www.ibm.com" : target);
            if (!"https".equalsIgnoreCase(remoteUri.getScheme()) || remoteUri.getHost() == null) {
                throw new IllegalArgumentException("remote.url must be an absolute HTTPS URL");
            }
            configureClient(JSSEHelper.getInstance().getSSLContext("defaultSSLConfig", null, null));
        } catch (com.ibm.websphere.ssl.SSLException | URISyntaxException | IllegalArgumentException e) {
            throw new ServletException("Unable to initialise the HTTP client", e);
        }
    }

    final URI remoteUri() {
        return remoteUri;
    }

    abstract void configureClient(SSLContext sslContext);

    abstract int fetchStatus() throws IOException, InterruptedException;

    @Override
    protected final void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/plain;charset=UTF-8");

        String host = remoteUri.getHost();
        try {
            int status = fetchStatus();
            LOG.log(Level.INFO, "{0} connected to {1} (HTTP {2})",
                    new Object[] { getServletName(), host, status });
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.log(Level.WARNING, getServletName() + " connection to " + host + " failed", e);
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            response.getWriter().write("Connection failed: " + describe(e) + " (" + e.getClass().getSimpleName()
                    + "). See the server log for details.");
            return;
        }

        response.getWriter().write("Connected successfully");
    }

    private static String describe(Exception failure) {
        return switch (failure) {
            case SSLException _ -> "TLS handshake failed";
            case UnknownHostException _ -> "host name could not be resolved";
            case ConnectException e when e.getCause() instanceof UnresolvedAddressException ->
                    "host name could not be resolved";
            case HttpTimeoutException _, SocketTimeoutException _, ConnectionRequestTimeoutException _ -> "timed out";
            case ConnectException _ -> "could not connect to the host";
            case InterruptedException _ -> "interrupted";
            default -> "I/O error";
        };
    }
}
