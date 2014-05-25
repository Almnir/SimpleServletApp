package com.mycompany.simpleservletapp;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Простейший сервлет принимающий 2 POST запроса в XML формате и выдающий XML ответа
 * 
 * @author alm (Ярных А.О.)
 */
@WebServlet(urlPatterns="/simple", asyncSupported=true)
public class SimpleServlet extends HttpServlet {

    Logger log = LoggerFactory.getLogger(HttpServlet.class);    

    @Override
    public void init() throws ServletException {
        super.init(); 
        // инициализация БД
        
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            AsyncContext acontext = request.startAsync();
            
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            ServletInputStream streamIn = request.getInputStream();
            DocumentBuilder parser = factory.newDocumentBuilder();
            Document document = parser.parse(streamIn);
            document.getDocumentElement().normalize();
            NodeList requestBody = document.getElementsByTagName("request");
            for (int i = 0; i < requestBody.getLength(); i++) {
 		Node node = requestBody.item(i);
 		System.out.println("Текущий элемент: " + node.getNodeName());
            }
        } catch (ParserConfigurationException | SAXException ex) {
            log.error(ex.getMessage());
            response.getOutputStream().print(ex.toString());
        }
        
        response.getOutputStream().close();
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

        /*
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        InputStream xml = request.getInputStream();

        response.setContentType("application/xml");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("<head>");
            out.println("<title>Servlet SimpleServlet</title>");
            out.println("</head>");
            out.println("<body>");
            out.println("<h1>Servlet SimpleServlet at " + request.getContextPath() + "</h1>");
            out.println("</body>");
            out.println("</html>");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    */

}