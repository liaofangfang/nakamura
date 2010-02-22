/**
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.util;

import java.io.IOException;
import java.security.MessageDigest;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.UserNotDefinedException;

/**
 * <pre>
 *  A filter to come after the standard sakai request fileter to allow services
 *  to encode a token containing the user id accessing the service. 
 *  
 *  The filter must be configured with a shared secret and requests contain a parameter 't'
 *  or a header X-SAKAI-TOKEN. This token is used to validate the Request and
 *  associate a user with the request.
 *  
 *  The token contains:
 *  hash;user;other
 *  
 *  hash is a SHA1 hash, user is the username to associate with the request and other is some random
 *  data to make the hash change per request.
 *  
 *  The hash is created by performing
 *  SHA1(sharedSecret;user;other)
 *  
 *  This is encoded as a string of hex bytes eg
 *  0123432ABCEF11131D etc
 *  
 *  The shared secret must be known by both ends of the conversation, and must not be distributed outside a trusted zone.
 *  
 *  To use this filter add it AFTER the Sakai Request Filter in you web.xml like
 *  
 *  
 *  	&lt;!-- 
 * 	The Sakai Request Hander 
 * 	--&gt;
 * 	&lt;filter&gt;
 * 		&lt;filter-name&gt;sakai.request&lt;/filter-name&gt;
 * 		&lt;filter-class&gt;org.sakaiproject.util.RequestFilter&lt;/filter-class&gt;
 * 	&lt;/filter&gt;
 * 	&lt;filter&gt;
 * 		&lt;filter-name&gt;sakai.trusted&lt;/filter-name&gt;
 * 		&lt;filter-class&gt;org.sakaiproject.util.TrustedLoginFilter&lt;/filter-class&gt;
 *       &lt;init-param&gt;
 *       	&lt;param-name&gt;shared.secret&lt;/param-name&gt;
 *           &lt;param-value&gt;The Snow on the Volga falls only under the bridges&lt;/param-value&gt;
 *       &lt;/init-param&gt;
 * 	&lt;/filter&gt;
 * 	
 * 	&lt;!--
 * 	Mapped onto Handler
 * 	--&gt;
 * 	&lt;filter-mapping&gt;
 * 		&lt;filter-name&gt;sakai.request&lt;/filter-name&gt;
 * 		&lt;servlet-name&gt;sakai.mytoolservlet&lt;/servlet-name&gt;
 * 		&lt;dispatcher&gt;REQUEST&lt;/dispatcher&gt;
 * 		&lt;dispatcher&gt;FORWARD&lt;/dispatcher&gt;
 * 		&lt;dispatcher&gt;INCLUDE&lt;/dispatcher&gt;
 * 	&lt;/filter-mapping&gt; 
 * 
 * 	&lt;filter-mapping&gt;
 * 		&lt;filter-name&gt;sakai.trusted&lt;/filter-name&gt;
 * 		&lt;servlet-name&gt;sakai.mytoolservlet&lt;/servlet-name&gt;
 * 		&lt;dispatcher&gt;REQUEST&lt;/dispatcher&gt;
 * 		&lt;dispatcher&gt;FORWARD&lt;/dispatcher&gt;
 * 		&lt;dispatcher&gt;INCLUDE&lt;/dispatcher&gt;
 * 	&lt;/filter-mapping&gt;
 * 
 * </pre>
 * 
 * @author ieb
 */
public class TrustedLoginFilter implements Filter {
	private final static Log LOG = LogFactory.getLog(TrustedLoginFilter.class);
	private static final String ORG_SAKAIPROJECT_UTIL_TRUSTED_LOGIN_FILTER_SHARED_SECRET = "org.sakaiproject.util.TrustedLoginFilter.sharedSecret";

	private SessionManager sessionManager;
	private String sharedSecret;

	/**
	 * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
	 *      javax.servlet.ServletResponse, javax.servlet.FilterChain)
	 */
	public void doFilter(ServletRequest req, ServletResponse resp,
			FilterChain chain) throws IOException, ServletException {
		HttpServletRequest hreq = (HttpServletRequest) req;
		String token = hreq.getHeader("X-SAKAI-TOKEN");
		if (token == null) {
			token = hreq.getParameter("t");
		}
		Session currentSession = null;
		Session requestSession = null;
		if (token != null) {
			String user = decodeToken(token);
			if (user != null) {
				currentSession = sessionManager.getCurrentSession();
				if (!user.equals(currentSession.getUserEid())) {
					requestSession = sessionManager.startSession();
					org.sakaiproject.user.api.User usr;
					try {
						usr = org.sakaiproject.user.cover.UserDirectoryService
								.getUserByEid(user);
						requestSession.setUserEid(usr.getEid());
						requestSession.setUserId(usr.getId());
						requestSession.setActive();
					} catch (UserNotDefinedException e) {
						e.printStackTrace();
					}
					sessionManager.setCurrentSession(requestSession);
					// wrap the request so that we can get the user via
					// getRemoteUser() in other places.
					if (!(hreq instanceof ToolRequestWrapper)) {
						hreq = new ToolRequestWrapper(hreq, user);
					}
				}
			}
		}
		try {
			chain.doFilter(hreq, resp);
		} finally {
			if (requestSession != null) {
				if (currentSession != null) {
					sessionManager.setCurrentSession(currentSession);
				}
				requestSession.invalidate();
			}
		}
	}

	/**
	 * @param token
	 * @return
	 */
	protected String decodeToken(String token) {
		try {
			int sep = token.indexOf(";");
			if (sep > 0) {
				String hash = token.substring(0, sep);
				String data = token.substring(sep + 1);
				String key = sharedSecret + ";" + data;
				String computedHash;
				computedHash = byteArrayToHexStr(MessageDigest.getInstance(
						"SHA1").digest(key.getBytes("UTF-8")));
				if (hash.equals(computedHash)) {
					sep = data.indexOf(";");
					return data.substring(0, sep);
				}
			}
		} catch (Exception ex) {
			LOG.warn("Failed to decode token " + token + "  :"
					+ ex.getMessage());
		}
		return null;
	}

	/**
	 * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
	 */
	public void init(FilterConfig config) throws ServletException {
		sessionManager = org.sakaiproject.tool.cover.SessionManager
				.getInstance();
		// get sharedSecret from web.xml first
		sharedSecret = config.getInitParameter("sharedSecret");
		if (sharedSecret == null) {
			// not in web.xml may be specified in sakai.properties
			sharedSecret = ServerConfigurationService
					.getString(ORG_SAKAIPROJECT_UTIL_TRUSTED_LOGIN_FILTER_SHARED_SECRET);
		}
	}

	/**
	 * @see javax.servlet.Filter#destroy()
	 */
	public void destroy() {
		// nothing to do here
	}

	protected String byteArrayToHexStr(byte[] data) {
		char[] chars = new char[data.length * 2];
		for (int i = 0; i < data.length; i++) {
			byte current = data[i];
			int hi = (current & 0xF0) >> 4;
			int lo = current & 0x0F;
			chars[2 * i] = (char) (hi < 10 ? ('0' + hi) : ('A' + hi - 10));
			chars[2 * i + 1] = (char) (lo < 10 ? ('0' + lo) : ('A' + lo - 10));
		}
		return new String(chars);
	}

	/**
	 * @param sharedSecret
	 *            the sharedSecret to set
	 */
	protected void setSharedSecret(String sharedSecret) {
		this.sharedSecret = sharedSecret;
	}

}