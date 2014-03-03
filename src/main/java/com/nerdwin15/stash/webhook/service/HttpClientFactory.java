package com.nerdwin15.stash.webhook.service;

import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;

/**
 * Defines a generator that will create a HttpClient used to communicate with
 * the Jenkins instance.
 * 
 * @author Michael Irwin (mikesir87)
 */
public interface HttpClientFactory {

  /**
   * Generate a HttpClient to communicate with Jenkins.
   * @param usingSsl True if using ssl.
   * @param trustAllCerts True if all certs should be trusted.
   * @return An HttpAsyncClient configured to communicate with Jenkins.
   */
  CloseableHttpAsyncClient getHttpClient(boolean usingSsl, boolean trustAllCerts);
}