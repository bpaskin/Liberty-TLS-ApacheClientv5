# Outbound HTTPS with Open Liberty

This sample compares two Java HTTP clients using a TLS configuration managed by Open Liberty.
Each endpoint sends a GET to the same configured HTTPS URL.

| Local endpoint | Outbound client |
| --- | --- |
| `GET /remote` | Apache HttpClient 5 |
| `GET /remote-jdk` | JDK `java.net.http.HttpClient` |

## Run locally

Use JDK 25 and Maven. Create `src/main/liberty/config/server.env` from the example if it does
not already exist.

```sh
cp -n src/main/liberty/config/server.env.example src/main/liberty/config/server.env
```

Set `KEYSTORE_PASSWORD` in that file. To choose an upstream, add a line such as
`REMOTE_URL=https://example.com`. A `-Dremote.url=...` entry in
`src/main/liberty/config/jvm.options` takes precedence over `REMOTE_URL`. If the selected
value is missing or blank, the servlet uses `https://www.ibm.com`. The URL must have an
`https` scheme and a host.

```sh
mvn liberty:run
```

### Set up upstream trust

On first start, Liberty generates `key.p12`. The sample uses it for the local HTTPS
certificate and the certificates trusted by the outbound clients.

- To use the JDK's trusted roots for a public upstream, set `trustDefaultCerts="true"`
  on `<ssl id="defaultSSLConfig">` in `src/main/liberty/config/server.xml` and restart
  Liberty. [Open Liberty transport security](https://openliberty.io/docs/latest/reference/feature/transportSecurity.html)
- To trust a private CA, stop Liberty and import its certificate. Enter `KEYSTORE_PASSWORD`
  at the prompt, verify the certificate fingerprint, and restart with `mvn liberty:run`.

```sh
keytool -importcert -alias upstream-ca -file /path/to/ca.pem \
  -keystore target/liberty/wlp/usr/servers/tlsServer/resources/security/key.p12
```

`mvn clean` deletes the generated keystore along with the rest of `target/`. Reimport any
private CA certificates after Liberty generates a new keystore.

### Call the endpoints

```sh
curl --insecure --include https://localhost:9443/remote
curl --insecure --include https://localhost:9443/remote-jdk
```

`--insecure` applies to curl's connection to the local, self-signed Liberty listener.
Both Java clients validate the upstream certificate and hostname.

| Local result | Meaning |
| --- | --- |
| `200` with `Connected successfully` | The upstream exchange completed. An upstream redirect or error status counts as a successful connection. |
| `502` with `Connection failed: …` | The exchange failed. The response gives a short reason, such as a TLS handshake failure or a timeout. |

Find the upstream HTTP status or the connection exception in
`target/liberty/wlp/usr/servers/tlsServer/logs/messages.log`.

## How Liberty supplies TLS

`RemoteProbeServlet.init()` uses `com.ibm.websphere.ssl.JSSEHelper` from Liberty's
`transportSecurity-1.0` feature to look up the `defaultSSLConfig` alias.
[Servlet source](src/main/java/com/example/tls/RemoteProbeServlet.java)

```java
configureClient(JSSEHelper.getInstance().getSSLContext("defaultSSLConfig", null, null));
```

The Apache servlet supplies the returned `SSLContext` to
`ClientTlsStrategyBuilder.setSslContext(...)`. The JDK servlet supplies it to
`HttpClient.Builder.sslContext(...)`. Both therefore use the truststore referenced by
Liberty's `defaultSSLConfig` for their outbound HTTPS calls. The Maven build packages
Apache HttpClient in the WAR and uses Liberty's SSL API as a provided dependency.
[Liberty configuration](src/main/liberty/config/server.xml) · [Maven configuration](pom.xml)
