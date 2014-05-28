package com.mycompany.simpleservletapp;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Простейший сервлет принимающий 2 POST запроса в XML формате и выдающий XML
 * ответа
 *
 * @author alm (Ярных А.О.)
 */
@WebServlet(urlPatterns = "/simple")
public class SimpleServlet extends HttpServlet {

    Logger log = LoggerFactory.getLogger(HttpServlet.class);

    Connection conn;
    String driver = "org.apache.derby.jdbc.EmbeddedDriver";
    String connectionURL = "jdbc:derby:servletDatabase;create=true";
    private Statement statement;
    private PreparedStatement pstatement;

    enum Result {

        OK, NAME_TAKEN, INVALID_NAME, WRONG_PASSWORD, UNKNOWN_ERROR
    }

    public static class Agent {

        public static String phoneLogin;
        public static String password;
        public static Timestamp updTime;
    }

    @Override
    public void init() throws ServletException {
        super.init();
        log.debug("Initializing SimpleServlet...");
        // инициализация БД
        try {
            Class.forName("org.apache.derby.jdbc.ClientDriver").newInstance();
            conn = DriverManager.getConnection(connectionURL);
            statement = conn.createStatement();
            statement.executeUpdate(
                    "CREATE TABLE Agent ("
                    + " ID INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,"
                    + " PhoneLogin VARCHAR(20) NOT NULL,"
                    + " Password VARCHAR(20) NOT NULL,"
                    + " UpdTime TIMESTAMP");
            statement.close();
        } catch (SQLException | ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            log.error(ex.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        ServletInputStream inStream = null;
        String requestType = null;
        String loginString = "";
        String password = "";
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            inStream = request.getInputStream();
            DocumentBuilder parser = factory.newDocumentBuilder();
            Document document = parser.parse(inStream);
            document.getDocumentElement().normalize();
            NodeList requestBody = document.getElementsByTagName("request");
            for (int i = 0; i < requestBody.getLength(); i++) {
                Node node = requestBody.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    requestType = element.getElementsByTagName("request-type").item(0).getTextContent();
                    loginString = element.getElementsByTagName("login").item(0).getTextContent();
                    password = element.getElementsByTagName("password").item(0).getTextContent();
                }
            }
        } catch (ParserConfigurationException | SAXException ex) {
            log.error("Parse exception " + ex.getMessage());
        } catch (IOException ex) {
            log.error("IO exception " + ex.getMessage());
        } finally {
            if (inStream != null) {
                inStream.close();
            }
        }
        Result rv;
        // проверяем на валидность логина-телефона
        String md5hash = md5(Agent.phoneLogin);
        if (md5hash != null) {
            Agent.phoneLogin = md5hash;
            Agent.password = password;

            switch (requestType) {
                case "new-agt":
                    createNewAgent();
                    break;
                case "agt-bal":
                    getAgentBalance();
                    break;
                default:
                    unknownRequestAnswer();
            }
        } else {
            rv = Result.INVALID_NAME;
        }

    }

    private void writeXMLAnswer(Result resultCode, HttpServletResponse response) {
        PrintWriter printWriter = null;
        try {
            printWriter = response.getWriter();
            StringBuilder sfXml = new StringBuilder();
            response.setContentType("text/xml");
            sfXml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            sfXml.append("<response>\n");
            sfXml.append("\t<result-code>" + String.valueOf(resultCode) + "</result-code>\n");
            sfXml.append("</response>\n");
            printWriter.println(sfXml.toString());
            printWriter.flush();
            printWriter.close();
        } catch (IOException ex) {
            log.error(ex.getMessage());
        } finally {
            if (printWriter != null) {
                printWriter.close();
            }
        }
    }

    private void createNewAgent() {

        try {
            PreparedStatement pstatement = conn
                    .prepareStatement("insert into Agent values (?,?,?)");

            pstatement.setString(1, Agent.phoneLogin);
            pstatement.setString(2, Agent.password);
            pstatement.setTimestamp(3, new Timestamp(new Date().getTime()));

            pstatement.executeUpdate();
        } catch (SQLException ex) {
            log.error(ex.getMessage());
        }

    }

    private String md5(String string) {
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.update(string.getBytes(), 0, string.length());
            BigInteger bigint = new BigInteger(1, m.digest());
            return String.format("%1$032x", bigint);
        } catch (NoSuchAlgorithmException e) {
            log.error(e.getMessage());
        }
        return null;
    }

    private void getAgentBalance() {

    }

    private Result checkValidations(String loginHash) {
        Result rv = Result.OK;
        int count = 0;
        try {
            pstatement = conn.prepareStatement("select * from Agent where PhoneLogin=?");
            pstatement.setString(1, loginHash);
            ResultSet resultSet = pstatement.executeQuery();
            while (resultSet.next()) {
                count++;
            }
        } catch (SQLException ex) {
            log.error(ex.getMessage());
            rv = Result.UNKNOWN_ERROR;
        }
        if (count > 0) {
            if (rv == Result.OK) {
                rv = Result.NAME_TAKEN;
            }
        }
        return rv;
    }
}
