package com.mycompany.simpleservletapp;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
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
    private String requestType;
    private String loginName;
    private String password;

    Connection conn;
    String driver = "org.apache.derby.jdbc.EmbeddedDriver";
    String connectionURL = "jdbc:derby:servletDatabase;create=true";
    private Statement statement;
    private PreparedStatement pstatement;

    enum Results {

        OK, NAME_TAKEN, INVALID_NAME, WRONG_PASSWORD, UNKNOWN_ERROR_CALL_RETRY_LATER
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
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            inStream = request.getInputStream();
            DocumentBuilder parser = factory.newDocumentBuilder();
            Document document = parser.parse(inStream);
            document.getDocumentElement().normalize();
            NodeList requestBody = document.getElementsByTagName("request");
            for (int i = 0; i < requestBody.getLength(); i++) {
                Node node = requestBody.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().contains("request-type")) {
                    Element element = (Element) node;
                    requestType = element.getElementsByTagName("request-type").item(0).getTextContent();
                    loginName = element.getElementsByTagName("login").item(0).getTextContent();
                    password = element.getElementsByTagName("password").item(0).getTextContent();
                }
            }
        } catch (ParserConfigurationException | SAXException ex) {
            log.error("Parse exception " + ex.getMessage());
        } catch (IOException ex) {
            log.error("IO exception " + ex.getMessage());
        } finally {
            inStream.close();
        }
        switch (requestType) {
            case "new-agt":
                createNewAgent();
                break;
            case "agt-bal":
                getAgentBalance();
                break;
        }
    }

    private void createNewAgent() {

        try {
            PreparedStatement pstatement = conn
                    .prepareStatement("insert into Agent values (?,?,?)");

            pstatement.setString(1, );
            pstatement.setString(2,);

            pstatement.executeUpdate();
        } catch (SQLException ex) {
            log.error(ex.getMessage());
        }

    }

    private String md5(String s) {
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.update(s.getBytes(), 0, s.length());
            BigInteger i = new BigInteger(1, m.digest());
            return String.format("%1$032x", i);
        } catch (NoSuchAlgorithmException e) {
            log.error(e.getMessage());
        }
        return null;
    }

    private void getAgentBalance() {

    }

    private Results checkValidations() {
        Results rv = Results.UNKNOWN_ERROR_CALL_RETRY_LATER;
        // проверяем на валидность логина-телефона
        String md5hash = md5(loginName);
        if (md5hash != null) {
            int count = 0;
            try {
                pstatement = conn.prepareStatement("select * from Agent where PhoneLogin=?");
                pstatement.setString(1, md5hash);
                ResultSet resultSet = pstatement.executeQuery();
                while (resultSet.next()) {
                    count++;
                }
            } catch (SQLException ex) {
                rv = Results.UNKNOWN_ERROR_CALL_RETRY_LATER;
                log.error(ex.getMessage());
            }
            if (count > 0) {
                rv = Results.NAME_TAKEN;
            }
        } else {
            return (Results.INVALID_NAME);
        }
        return rv;
    }

}
