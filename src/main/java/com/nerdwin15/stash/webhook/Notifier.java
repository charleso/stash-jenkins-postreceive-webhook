package com.nerdwin15.stash.webhook;

import com.atlassian.stash.hook.repository.RepositoryHook;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.setting.Settings;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.nerdwin15.stash.webhook.service.HttpClientFactory;
import com.nerdwin15.stash.webhook.service.SettingsService;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.entity.ContentBufferEntity;
import org.apache.http.nio.protocol.AbstractAsyncResponseConsumer;
import org.apache.http.nio.protocol.BasicAsyncRequestProducer;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.nio.util.SimpleInputBuffer;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.concurrent.Future;

/**
 * Service object that does the actual notification.
 *
 * @author Michael Irwin (mikesir87)
 * @author Peter Leibiger (kuhnroyal)
 */
public class Notifier {

  /**
   * Key for the repository hook
   */
  public static final String KEY =
      "com.nerdwin15.stash-stash-webhook-jenkins:jenkinsPostReceiveHook";

  /**
   * Field name for the Jenkins base URL property
   */
  public static final String JENKINS_BASE = "jenkinsBase";

  /**
   * Field name for the Repo Clone Url property
   */
  public static final String CLONE_URL = "gitRepoUrl";

  /**
   * Field name for the ignore certs property
   */
  public static final String IGNORE_CERTS = "ignoreCerts";

  /**
   * Field name for the ignore committers property
   */
  public static final String IGNORE_COMMITTERS = "ignoreCommitters";

  private static final Logger LOGGER =
      LoggerFactory.getLogger(Notifier.class);
  private static final String URL = "%s/git/notifyCommit?url=%s";

  private final HttpClientFactory httpClientFactory;
  private final SettingsService settingsService;

  /**
   * Create a new instance
   * @param settingsService Service used to get webhook settings
   * @param httpClientFactory Factory to generate HttpClients
   */
  public Notifier(SettingsService settingsService,
      HttpClientFactory httpClientFactory) {

    this.httpClientFactory = httpClientFactory;
    this.settingsService = settingsService;
  }

  /**
   * Send notification to Jenkins for the provided repository.
   * @param repo The repository to base the notification on.
   * @return Text result from Jenkins
   */
  public @Nullable Future<NotificationResult> notify(@Nonnull Repository repo) { //CHECKSTYLE:annot
    final RepositoryHook hook = settingsService.getRepositoryHook(repo);
    final Settings settings = settingsService.getSettings(repo);
    if (hook == null || !hook.isEnabled() || settings == null) {
      LOGGER.debug("Hook not configured correctly or not enabled, returning.");
      return null;
    }

    return notify(repo, settings.getString(JENKINS_BASE),
        settings.getBoolean(IGNORE_CERTS, false),
        settings.getString(CLONE_URL));
  }

  /**
   * Send notification to Jenkins using the provided settings
   * @param repo The repository to base the notification on.
   * @param jenkinsBase Base URL for Jenkins instance
   * @param ignoreCerts True if all certs should be allowed
   * @param cloneUrl The repository url
   * @return The notification result.
   */
  public @Nullable Future<NotificationResult> notify(@Nonnull Repository repo, //CHECKSTYLE:annot
      String jenkinsBase, boolean ignoreCerts, String cloneUrl) {

    final String url = getUrl(repo, maybeReplaceSlash(jenkinsBase), cloneUrl);

    final CloseableHttpAsyncClient httpClient = httpClientFactory.getHttpClient(url.startsWith("https"), ignoreCerts);
    httpClient.start();
    return httpClient.execute(new BasicAsyncRequestProducer(URIUtils.extractHost(URI.create(url)), new HttpGet(url)),
        new AbstractAsyncResponseConsumer<NotificationResult>() {

          private volatile HttpResponse response;
          private volatile SimpleInputBuffer buf;

          @Override
          protected void onResponseReceived(HttpResponse response) throws HttpException, IOException {
            this.response = response;
          }

          @Override
          protected void onContentReceived(ContentDecoder decoder, IOControl ioctrl) throws IOException {
            this.buf.consumeContent(decoder);
          }

          @Override
          protected void onEntityEnclosed(HttpEntity entity, ContentType contentType) throws IOException {
            long len = entity.getContentLength();
            // This is horrible but we only need a short string from jenkins
            this.buf = new SimpleInputBuffer((int) (len < 0 ? 4096 : len), new HeapByteBufferAllocator());
            this.response.setEntity(new ContentBufferEntity(entity, this.buf));
          }

          @Override
          protected NotificationResult buildResult(HttpContext context) throws Exception {
            try {
              LOGGER.debug("Successfully triggered jenkins with url '{}': ", url);
              InputStream content = response.getEntity().getContent();
              String responseBody = CharStreams.toString(
                  new InputStreamReader(content, Charsets.UTF_8));
              boolean successful = responseBody.startsWith("Scheduled");

              return new NotificationResult(successful, url, "Jenkins response: " + responseBody);
            } catch (Exception e) {
              LOGGER.error("Error triggering jenkins with url '" + url + "'", e);
              return new NotificationResult(false, url, e.getMessage());
            }
          }

          @Override
          protected void releaseResources() {
            IOUtils.closeQuietly(httpClient);
          }
        }, new FutureCallback<NotificationResult>() {
          @Override
          public void completed(NotificationResult notificationResult) {
          }

          @Override
          public void failed(Exception e) {
          }

          @Override
          public void cancelled() {
          }
        }
    );
  }

  /**
   * Get the url for notifying of Jenkins. Protected for testing purposes
   * @param repository The repository to base the request to.
   * @param jenkinsBase The base URL of the Jenkins instance
   * @param cloneUrl The url used for cloning the repository
   * @return The url to use for notifying Jenkins
   */
  protected String getUrl(Repository repository, String jenkinsBase,
      String cloneUrl) {
    return String.format(URL, jenkinsBase, urlEncode(cloneUrl));
  }

  private static String urlEncode(String string) {
    try {
      return URLEncoder.encode(string, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  private String maybeReplaceSlash(String string) {
    return string == null ? null : string.replaceFirst("/$", "");
  }
}