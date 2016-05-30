package org.pente.filter;  
  
import java.io.IOException;  
import javax.servlet.*;  
import javax.servlet.http.*;  
  
/** 
 * Set the character encoding of the request to as set in "encoding" 
 * this should be done before the request is accessed. 
 */  
public class SetCharacterEncodingFilter implements Filter {  
  
    private FilterConfig filterConfig;  
    private String encoding;  

    public void init(FilterConfig filterConfig) {  
        this.filterConfig = filterConfig;  
        this.encoding = filterConfig.getInitParameter("encoding");  
    }  
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {  
        request.setCharacterEncoding(this.encoding);  
        chain.doFilter(request, response);  
    }  
    public void destroy() {
        this.filterConfig = null;
    }
}
