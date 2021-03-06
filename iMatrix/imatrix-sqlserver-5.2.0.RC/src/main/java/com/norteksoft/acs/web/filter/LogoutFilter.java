/* Copyright 2004, 2005, 2006 Acegi Technology Pty Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.norteksoft.acs.web.filter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.Authentication;
import org.springframework.security.context.SecurityContextHolder;
import org.springframework.security.ui.FilterChainOrder;
import org.springframework.security.ui.SpringSecurityFilter;
import org.springframework.security.ui.logout.LogoutHandler;
import org.springframework.security.util.RedirectUtils;
import org.springframework.security.util.UrlUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.norteksoft.product.util.PropUtils;

/**
 * Logs a principal out.
 * <p>
 * Polls a series of {@link LogoutHandler}s. The handlers should be specified in the order they are required.
 * Generally you will want to call logout handlers <code>TokenBasedRememberMeServices</code> and
 * <code>SecurityContextLogoutHandler</code> (in that order).
 * </p>
 * <p>
 * After logout, the URL specified by {@link #logoutSuccessUrl} will be shown.
 * </p>
 * 
 * @author Ben Alex
 * @version $Id: LogoutFilter.java 3111 2008-06-01 16:15:09Z luke_t $
 * 
 * 重写org.springframework.security.ui.logout.LogoutFilter
 */
public class LogoutFilter extends SpringSecurityFilter {
	
	private Map<String, String> ipUsers = new HashMap<String, String>(); // ip和用户对应关系

    //~ Instance fields ================================================================================================

    private String filterProcessesUrl = "/j_spring_security_logout";
    private String logoutSuccessUrl;
    private LogoutHandler[] handlers;
    private boolean useRelativeContext;

    //~ Constructors ===================================================================================================

    public LogoutFilter(String logoutSuccessUrl, LogoutHandler[] handlers) {
        Assert.notEmpty(handlers, "LogoutHandlers are required");
        this.logoutSuccessUrl = logoutSuccessUrl;
        Assert.isTrue(UrlUtils.isValidRedirectUrl(logoutSuccessUrl), logoutSuccessUrl + " isn't a valid redirect URL");
        this.handlers = handlers;
    }

    //~ Methods ========================================================================================================

    public void doFilterHttp(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException,
            ServletException {
    	String host = request.getRemoteHost();
    	String queryString = request.getQueryString();
    	//自动登录处理
		if(ipUsers.get(host)==null){
			Authentication auth1 = SecurityContextHolder.getContext().getAuthentication();
			if(auth1 != null){
				ipUsers.put(host, auth1.getName());
			}
    	} else if(queryString!=null && queryString.contains("type=auto&") && !request.getRequestURI().contains("j_spring_security_logout")){//是否是自动单点登录
			 sendRedirect(request, response, PropUtils.getProp("host.app")+"/j_spring_security_logout?_service="+request.getRequestURL().toString()+"?"+queryString);
			 return;
    	}
    	
        if (requiresLogout(request, response)) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
          //自动登录处理
            ipUsers.remove(host);
            
            if (logger.isDebugEnabled()) {
                logger.debug("Logging out user '" + auth + "' and redirecting to logout page");
            }

            for (int i = 0; i < handlers.length; i++) {
                handlers[i].logout(request, response, auth);
            }

            String targetUrl = determineTargetUrl(request, response);
            
          //自动登录处理
            if(queryString!=null && queryString.contains("type=auto&")){
            	targetUrl = targetUrl.split("\\?")[0];
            	targetUrl = targetUrl+"?"+queryString;
            }
            
            sendRedirect(request, response, targetUrl);

            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Allow subclasses to modify when a logout should take place.
     *
     * @param request the request
     * @param response the response
     *
     * @return <code>true</code> if logout should occur, <code>false</code> otherwise
     */
    protected boolean requiresLogout(HttpServletRequest request, HttpServletResponse response) {
        String uri = request.getRequestURI();
        int pathParamIndex = uri.indexOf(';');

        if (pathParamIndex > 0) {
            // strip everything from the first semi-colon
            uri = uri.substring(0, pathParamIndex);
        }

        int queryParamIndex = uri.indexOf('?');

        if (queryParamIndex > 0) {
            // strip everything from the first question mark
            uri = uri.substring(0, queryParamIndex);
        }

        if ("".equals(request.getContextPath())) {
            return uri.endsWith(filterProcessesUrl);
        }

        return uri.endsWith(request.getContextPath() + filterProcessesUrl);
    }

    /**
     * Returns the target URL to redirect to after logout.
     * <p>
     * By default it will check for a <tt>logoutSuccessUrl</tt> parameter in
     * the request and use this. If that isn't present it will use the configured <tt>logoutSuccessUrl</tt>. If this
     * hasn't been set it will check the Referer header and use the URL from there.
     *
     */
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response) {
        String targetUrl = request.getParameter("logoutSuccessUrl");

        if(!StringUtils.hasLength(targetUrl)) {
            targetUrl = getLogoutSuccessUrl();
        }

        if (!StringUtils.hasLength(targetUrl)) {
            targetUrl = request.getHeader("Referer");
        }        

        if (!StringUtils.hasLength(targetUrl)) {
            targetUrl = "/";
        }

        return targetUrl;
    }

    /**
     * Allow subclasses to modify the redirection message.
     *
     * @param request  the request
     * @param response the response
     * @param url      the URL to redirect to
     *
     * @throws IOException in the event of any failure
     */
    protected void sendRedirect(HttpServletRequest request, HttpServletResponse response, String url)
            throws IOException {

        RedirectUtils.sendRedirect(request, response, url, useRelativeContext);
    }

    public void setFilterProcessesUrl(String filterProcessesUrl) {
        Assert.hasText(filterProcessesUrl, "FilterProcessesUrl required");
        Assert.isTrue(UrlUtils.isValidRedirectUrl(filterProcessesUrl), filterProcessesUrl + " isn't a valid redirect URL");
        this.filterProcessesUrl = filterProcessesUrl;
    }

    protected String getLogoutSuccessUrl() {
        return logoutSuccessUrl;
    }    
    
    protected String getFilterProcessesUrl() {
        return filterProcessesUrl;
    }

    public void setUseRelativeContext(boolean useRelativeContext) {
        this.useRelativeContext = useRelativeContext;
    }

    public int getOrder() {
        return FilterChainOrder.LOGOUT_FILTER;
    }
}
