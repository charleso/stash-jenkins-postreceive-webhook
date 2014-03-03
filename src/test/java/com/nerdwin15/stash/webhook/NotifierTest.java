package com.nerdwin15.stash.webhook;

import com.atlassian.stash.hook.repository.RepositoryHook;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.setting.Settings;
import com.nerdwin15.stash.webhook.service.ConcreteHttpClientFactory;
import com.nerdwin15.stash.webhook.service.SettingsService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test for the Notifier class
 * 
 * @author Peter Leibiger (kuhnroyal)
 * @author Michael Irwin (mikesir87)
 */
public class NotifierTest {

  private static final String JENKINS_BASE_URL2 = "http://localhost";
  private static final String CLONE_URL =
      "http://some.stash.com/scm/foo/bar.git";


  private Repository repo;
  private RepositoryHook repoHook;
  private Settings settings;
  private SettingsService settingsService;
  private Notifier notifier;
  private HttpServer httpServer;
  private String outgoingUrl;
  private String response;
  private String url;

  /**
   * Setup tasks
   */
  @Before
  public void setup() throws Exception {
    settingsService = mock(SettingsService.class);
    notifier = new Notifier(settingsService, new ConcreteHttpClientFactory());

    repo = mock(Repository.class);
    repoHook = mock(RepositoryHook.class);
    settings = mock(Settings.class);

    when(repoHook.isEnabled()).thenReturn(true);
    when(settingsService.getRepositoryHook(repo)).thenReturn(repoHook);
    when(settingsService.getSettings(repo)).thenReturn(settings);

    httpServer = HttpServer.create(new InetSocketAddress(0), 0);
    outgoingUrl = JENKINS_BASE_URL2 + ":" + httpServer.getAddress().getPort();
    when(settings.getString(Notifier.JENKINS_BASE)).thenReturn(outgoingUrl);
    when(settings.getString(Notifier.CLONE_URL)).thenReturn(CLONE_URL);
    when(settings.getBoolean(Notifier.IGNORE_CERTS, false)).thenReturn(false);

    httpServer.createContext("/").setHandler(new HttpHandler() {
      @Override
      public void handle(HttpExchange httpExchange) throws IOException {

        url = httpExchange.getRequestURI().toString();
        httpExchange.sendResponseHeaders(200, 0);
        httpExchange.getResponseBody().write(response.getBytes());
        httpExchange.close();
      }
    });
    httpServer.start();
  }

  @After
  public void tearDown() {
    httpServer.stop(0);
  }

  /**
   * Validates nothing happens if the hook isn't found
   * @throws Exception
   */
  @Test
  public void shouldReturnEarlyWhenHookIsNull() throws Exception {
    when(settingsService.getRepositoryHook(repo)).thenReturn(null);
    assertNull(notifier.notify(repo));
  }

  /**
   * Validates that nothing happens if the hook is disabled
   * @throws Exception
   */
  @Test
  public void shouldReturnEarlyWhenHookIsNotEnabled() throws Exception {
    when(repoHook.isEnabled()).thenReturn(false);
    assertNull(notifier.notify(repo));
  }

  /**
   * Validates that nothing happens if the settings aren't set properly
   * @throws Exception
   */
  @Test
  public void shouldReturnEarlyWhenSettingsAreNull() throws Exception {
    when(settingsService.getSettings(repo)).thenReturn(null);
    assertNull(notifier.notify(repo));
  }

  /**
   * Validates the URL is correct when using a non-SSL path
   * @throws Exception
   */
  @Test
  public void shouldCallTheCorrectUrlWithoutSsl() throws Exception {
    response = "Scheduled build 123";
    NotificationResult result = notifier.notify(repo).get(10, TimeUnit.SECONDS);
    assertEquals(true, result.isSuccessful());
    assertEquals("Jenkins response: " + response, result.getMessage());
  }

  /**
   * Validates that the correct path is used, even when a trailing slash
   * is provided on the Jenkins Base URL
   * @throws Exception
   */
  @Test
  public void shouldCallTheCorrectUrlWithTrailingSlashOnJenkinsBaseUrl() 
      throws Exception {
    when(settings.getString(Notifier.JENKINS_BASE))
      .thenReturn(outgoingUrl.concat("/"));

    notifier.notify(repo).get(10, TimeUnit.SECONDS);

    assertEquals("/git/notifyCommit?"
        + "url=http%3A%2F%2Fsome.stash.com%2Fscm%2Ffoo%2Fbar.git",
        url);
  }
    
}
