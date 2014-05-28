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
import java.util.HashMap;
import java.util.Map;
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

        OK, WRONG_DATA, NAME_TAKEN, INVALID_NAME, WRONG_PASSWORD, UNKNOWN_ERROR
    }

    public static class Agent {

        public static String phoneLogin;
        public static String password;
        public static Timestamp updTime;
        private static double funds;
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
                    + " ID INTEGER GENERATED ALWAYS AS IDENTITY,"
                    + " PhoneLogin VARCHAR(20) NOT NULL,"
                    + " Password VARCHAR(20) NOT NULL,"
                    + " UpdTime TIMESTAMP, PRIMARY KEY (ID))");
            statement.executeUpdate(
                    "CREATE TABLE Balance ("
                    + " ID INTEGER GENERATED ALWAYS AS IDENTITY,"
                    + " AccountFunds DOUBLE NOT NULL,"
                    + " LoginID INTEGER,"
                    + " UpdTime TIMESTAMP, FOREIGN KEY (LoginID) REFERENCES Agent (ID))");
            statement.close();
        } catch (SQLException | ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            log.error(ex.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        ServletInputStream inStream = null;
        String requestType = "";
        String loginString = "";
        String password = "";
        String funds = "";
        Result resultAnswer = Result.OK;
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
                    funds = element.getElementsByTagName("funds").item(0).getTextContent();
                }
            }
        } catch (ParserConfigurationException | SAXException ex) {
            log.error("Parse exception " + ex.getMessage());
            resultAnswer = Result.WRONG_DATA;
        } catch (IOException ex) {
            log.error("IO exception " + ex.getMessage());
            resultAnswer = Result.WRONG_DATA;
        } finally {
            if (inStream != null) {
                inStream.close();
            }
        }
        // проверяем на валидность логина-телефона
        String md5hash = md5(Agent.phoneLogin);
        if (md5hash != null) {
            Agent.phoneLogin = md5hash;
            Agent.password = password;
            Agent.funds = Double.parseDouble(funds);
            
            switch (requestType) {
                case "new-agt":
                    createNewAgent();
                    break;
                case "add-funds":
                    // добавить средства
                    addFunds();
                    break;
                case "agt-bal":
                    // запросить баланс
                    getAgentBalance();
                    break;
                default:
                    resultAnswer = Result.UNKNOWN_ERROR;
            }
        } else {
            resultAnswer = Result.INVALID_NAME;
        }

    }

    private Map<String, String> checkLoginPassword(String login) {
        Map<String, String> loginPassword = new HashMap<>();
        try {
            pstatement = conn.prepareStatement("select PhoneLogin, Password from Agent"
                    + "where PhoneLogin = ?");

            pstatement.setString(1, login);
            ResultSet resultSet = pstatement.executeQuery();
            if (resultSet.next()) {
                loginPassword.put(resultSet.getString(0), resultSet.getString(1));
            }
        } catch (SQLException ex) {
            log.error(ex.getMessage());
        }
        return loginPassword;
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
            pstatement = conn
                    .prepareStatement("insert into Agent(PhoneLogin, Password, UpdTime) values (?,?,?)");

            pstatement.setString(1, Agent.phoneLogin);
            pstatement.setString(2, Agent.password);
            pstatement.setTimestamp(3, new Timestamp(new Date().getTime()));

            pstatement.executeUpdate();
        } catch (SQLException ex) {
            log.error(ex.getMessage());
        }

    }

    private void addFunds() {
        try {
            pstatement = conn
                    .prepareStatement("insert into Balance(AccountFunds, UpdTime) values (?,?)");

            pstatement.setDouble(1, Agent.funds);
            pstatement.setTimestamp(2, new Timestamp(new Date().getTime()));

            pstatement.executeUpdate();

            pstatement = conn
                    .prepareStatement("update Balance set Balance.LoginID = (select Agent.ID from Agent where Agent.PhoneLogin = ?)");

            pstatement.setString(1, Agent.phoneLogin);
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

    private Double getAgentBalance() {
        Double rv = null;
        try {
            pstatement = conn.prepareStatement("select b.AccountFunds from Balance b, Agent a "
                    + " where a.ID = b.LoginID"
                    + " and a.PhoneLogin = ?");
            pstatement.setString(1, Agent.phoneLogin);
            ResultSet resultSet = pstatement.executeQuery();
            if (resultSet.next()) {
                rv = resultSet.getDouble(1);
            }

        } catch (SQLException ex) {
            log.error(ex.getMessage());
        }
        return rv;
    }

    private Result getBalance(String loginHash) {
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
