package com.nerdwin15.stash.webhook.service;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.BasicClientConnectionManager;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.nio.client.HttpAsyncClient;
import org.apache.http.nio.client.util.HttpAsyncClientUtils;

/**
 * An implementation of the {@link HttpClientFactory} that returns a
 * DefaultHttpClient that is either not configured at all (non-ssl and default
 * trusts) or configured to accept all certificates.  If told to accept all
 * certificates, an unsafe X509 trust manager is used.
 *
 * If setup of the "trust-all" HttpClient fails, a non-configured HttpClient
 * is returned.
 *
 * @author Michael Irwin (mikesir87)
 *
 */
public class ConcreteHttpClientFactory implements HttpClientFactory {

  /**
   * {@inheritDoc}
   */
  public CloseableHttpAsyncClient getHttpClient(boolean usingSsl, boolean trustAllCerts) {
    return HttpAsyncClientBuilder.create()
        .setSSLContext(usingSsl && trustAllCerts ? createContext() : null)
        .build();
  }

  /**
   * Creates an SSL context
   * @return The SSL context
   */
  private SSLContext createContext() {
    try {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(
          null,
          new TrustManager[]{new UnsafeX509TrustManager()},
          new SecureRandom());
      return sslContext;
    } catch (Exception e) {
      return null;
    }
  }
}