/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sdklib.internal.repository;

import com.android.util.Pair;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.ProxySelectorRoutePlanner;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.util.HashMap;
import java.util.Map;

/**
 * This class holds methods for adding URLs management.
 * @see #openURL(String, ITaskMonitor)
 */
public class UrlOpener {

    public static class CanceledByUserException extends Exception {
        private static final long serialVersionUID = -7669346110926032403L;

        public CanceledByUserException(String message) {
            super(message);
        }
    }

    private static Map<String, Pair<String, String>> sRealmCache =
            new HashMap<String, Pair<String, String>>();

    /**
     * Opens a URL. It can be a simple URL or one which requires basic
     * authentication.
     * <p/>
     * Tries to access the given URL. If http response is either
     * {@code HttpStatus.SC_UNAUTHORIZED} or
     * {@code HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED}, asks for
     * login/password and tries to authenticate into proxy server and/or URL.
     * <p/>
     * This implementation relies on the Apache Http Client due to its
     * capabilities of proxy/http authentication. <br/>
     * Proxy configuration is determined by {@link ProxySelectorRoutePlanner} using the JVM proxy
     * settings by default.
     * <p/>
     * For more information see: <br/>
     * - {@code http://hc.apache.org/httpcomponents-client-ga/} <br/>
     * - {@code http://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/impl/conn/ProxySelectorRoutePlanner.html}
     * <p/>
     * There's a very simple <b>cache</b> implementation.
     * Login/Password for each realm are stored in a static {@link Map}.
     * Before asking the user the method verifies if the information is already available in cache.
     *
     * @param url the URL string to be opened.
     * @param monitor {@link ITaskMonitor} which is related to this URL
     *            fetching.
     * @return Returns an {@link InputStream} holding the URL content.
     * @throws IOException Exception thrown when there are problems retrieving
     *             the URL or its content.
     * @throws CanceledByUserException Exception thrown if the user cancels the
     *              authentication dialog.
     */
    @SuppressWarnings("deprecation")
    static InputStream openURL(String url, ITaskMonitor monitor)
        throws IOException, CanceledByUserException {

        InputStream stream = null;
        HttpEntity entity = null;
        Pair<String, String> result = null;
        String realm = null;

        // use the simple one
        DefaultHttpClient httpclient = new DefaultHttpClient();

        // create local execution context
        HttpContext localContext = new BasicHttpContext();
        HttpGet httpget = new HttpGet(url);

        // retrieve local java configured network in case there is the need to
        // authenticate a proxy
        ProxySelectorRoutePlanner routePlanner = new ProxySelectorRoutePlanner(httpclient
                .getConnectionManager().getSchemeRegistry(), ProxySelector.getDefault());
        httpclient.setRoutePlanner(routePlanner);

        boolean trying = true;
        // loop while the response is being fetched
        while (trying) {
            // connect and get status code
            HttpResponse response = httpclient.execute(httpget, localContext);
            int statusCode = response.getStatusLine().getStatusCode();

            // check whether any authentication is required
            AuthState authenticationState = null;
            if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
                // Target host authentication required
                authenticationState = (AuthState) localContext
                        .getAttribute(ClientContext.TARGET_AUTH_STATE);
            }
            if (statusCode == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED) {
                // Proxy authentication required
                authenticationState = (AuthState) localContext
                        .getAttribute(ClientContext.PROXY_AUTH_STATE);
            }
            if (statusCode == HttpStatus.SC_OK) {
                // in case the status is OK and there is a realm and result,
                // cache it
                if (realm != null && result != null) {
                    sRealmCache.put(realm, result);
                }
            }

            // there is the need for authentication
            if (authenticationState != null) {

                // get scope and realm
                AuthScope authScope = authenticationState.getAuthScope();

                // If the current realm is different from the last one it means
                // a pass was performed successfully to the last URL, therefore
                // cache the last realm
                if (realm != null && !realm.equals(authScope.getRealm())) {
                    sRealmCache.put(realm, result);
                }

                realm = authScope.getRealm();

                // in case there is cache for this Realm, use it to authenticate
                if (sRealmCache.containsKey(realm)) {
                    result = sRealmCache.get(realm);
                } else {
                    // since there is no cache, request for login and password
                    result = monitor.displayLoginPasswordPrompt("Site Authentication",
                            "Please login to the following domain: " + realm +
                            "\n\nServer requiring authentication:\n" + authScope.getHost());
                    if (result == null) {
                        throw new CanceledByUserException("User canceled login dialog.");
                    }
                }

                // retrieve authentication data
                String user = result.getFirst();
                String password = result.getSecond();

                // proceed in case there is indeed a user
                if (user != null && user.length() > 0) {
                    Credentials credentials = new UsernamePasswordCredentials(user, password);
                    httpclient.getCredentialsProvider().setCredentials(authScope, credentials);
                    trying = true;
                } else {
                    trying = false;
                }
            } else {
                trying = false;
            }
            entity = response.getEntity();
            if (entity != null) {
                // in case another pass to the Http Client will be performed,
                // consume the entity
                if (trying) {
                    entity.consumeContent();
                }
                // since no pass to the Http Client is needed, retrieve the
                // entity's content
                else {
                    // Use a buffered entity because the stream in which it will
                    // be transfered, will not be closed later, unexpectedly

                    // TODO: an unfortunate side effect is that creating the BufferedHttpEntity
                    // seems to perform the *actual* download (looking at it, there's a buffer
                    // being filled in there). So the caller doesn't have a chance to produce
                    // a meaningful callback with download speed/ETA stats.
                    // Behavior might be different with a slower network.

                    BufferedHttpEntity bufferedEntity = new BufferedHttpEntity(entity);
                    stream = bufferedEntity.getContent();
                }
            }
        }

        // since Http Client is no longer needed, close it
        httpclient.getConnectionManager().shutdown();

        return stream;
    }
}
