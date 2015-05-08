package org.pente.tree;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

public class HttpNodeSearcherServlet extends HttpServlet {
    
    private static Category log4j = Category.getInstance(
        HttpNodeSearcherServlet.class.getName());

    private static NodeSearcher nodeSearcher;
    
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        
        try {
            nodeSearcher = new HibernateNodeSearcher(true);
        } catch (NodeSearchException nse) {
            log4j.error("Problem initializing hibernate node searcher", nse);
        }
    }

    public void doGet(HttpServletRequest request,
            HttpServletResponse response)
        throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(final HttpServletRequest request,
        final HttpServletResponse response)
        throws ServletException, IOException {
        
        String pathInfo = request.getPathInfo().substring(1);
        int hash = Integer.valueOf(pathInfo).intValue();
        log4j.info("loading nodes for hash = " + hash);
        
        ObjectOutputStream out = null;
        try {
            Node node = nodeSearcher.loadPosition(hash);
            out = new ObjectOutputStream(response.getOutputStream());
            out.writeObject(node);

        } catch (NodeSearchException nse) {
            log4j.error("Problem loading nodes.", nse);
        } finally {
            if (out != null) {
                try { out.close(); } catch (IOException ie) {}
            }
        }
    }
}
