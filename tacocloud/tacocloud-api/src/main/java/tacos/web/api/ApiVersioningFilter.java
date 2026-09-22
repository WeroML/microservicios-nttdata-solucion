package tacos.web.api;

import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class ApiVersioningFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();
        
        if (path.startsWith("/api/") && !path.startsWith("/api/v1/")) {
            httpResponse.addHeader("Warning", "299 - \"Deprecated API: Please migrate to /api/v1\"");
            
            String newPath = path.replaceFirst("/api/", "/api/v1/");
            httpRequest.getRequestDispatcher(newPath).forward(request, response);
            return;
        }
        
        chain.doFilter(request, response);
    }
}
