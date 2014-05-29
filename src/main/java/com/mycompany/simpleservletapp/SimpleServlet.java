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
 * Простой сервлет принимающий 2 POST запроса в XML формате и выдающий XML ответ
 *
 * @author (Ярных А.О.)
 */
@WebServlet(urlPatterns = "/simple")
public class SimpleServlet extends HttpServlet {

    Logger log = LoggerFactory.getLogger(SimpleServlet.class);

    /**
     * Строка драйвера БД
     */
    String driver = "org.apache.derby.jdbc.EmbeddedDriver";
    /**
     * Строка соединения с базой данных
     */
    String connectionURL = "jdbc:derby:servletDatabase;create=true";
    Connection conn;
    private Statement statement;
    private PreparedStatement pstatement;

    /**
     * Перечисление результирующих кодов
     */
    public enum Result {

        OK, WRONG_DATA, NAME_TAKEN, INVALID_NAME, WRONG_PASSWORD, UNKNOWN_ERROR
    }

    /**
     * Класс состояния (мог бы быть более ёмким)
     */
    public static class Answer {

        public static Result resultCode;
    }

    /**
     * Класс клиента
     */
    public static class Agent {

        public static String phoneLogin;
        public static String password;
        public static Timestamp updTime;
        public static double funds;
    }

    /**
     * Инициализация сервлета
     *
     * @throws ServletException
     */
    @Override
    public void init() throws ServletException {
        super.init();
        Answer.resultCode = Result.OK;
        log.debug("Initializing SimpleServlet...");
        // инициализация БД
        try {
            Class.forName("org.apache.derby.jdbc.ClientDriver").newInstance();
            conn = DriverManager.getConnection(connectionURL);
            statement = conn.createStatement();
            statement.executeUpdate(
                    "create table Agent ("
                    + " id integer generated always as identity,"
                    + " phonelogin varchar(20) not null,"
                    + " password varchar(20) not null,"
                    + " updtime timestamp, primary key (id))");
            statement.executeUpdate(
                    "create table Balance ("
                    + " id integer generated always as identity,"
                    + " accountfunds double not null,"
                    + " loginid integer,"
                    + " updtime timestamp, foreign key (loginid) references Agent (id))");
            statement.close();
        } catch (SQLException | ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            log.error(ex.getMessage());
        }
    }

    /**
     * Обработчик метода POST
     *
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        ServletInputStream inStream = null;
        String requestType = "";
        String loginString = "";
        String password = "";
        String funds = "";
        try {
            // разбор DOM XML
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
            Answer.resultCode = Result.WRONG_DATA;
        } catch (IOException ex) {
            log.error("IO exception " + ex.getMessage());
            Answer.resultCode = Result.WRONG_DATA;
        } finally {
            if (inStream != null) {
                inStream.close();
            }
        }
        // проверяем на валидность логина-телефона
        String md5hash = md5(loginString);
        if (md5hash != null) {
            Agent.phoneLogin = md5hash;
            Agent.password = password;
            Agent.funds = Double.parseDouble(funds);
            // извлекаем пару логин-пароль
            Map<String, String> loginPassword = getLoginPassword(Agent.phoneLogin);

            switch (requestType) {
                case "new-agt":
                    if (loginPassword.isEmpty()) {
                        createNewAgent();
                    } else {
                        Answer.resultCode = Result.NAME_TAKEN;
                    }
                    break;
                case "set-funds":
                    if (loginPassword != null && !loginPassword.isEmpty()) {
                        // проверка на совпадение пароля
                        if (loginPassword.get(Agent.phoneLogin).equals(Agent.password)) {
                            // установить средства
                            setFunds();
                        } else {
                            Answer.resultCode = Result.WRONG_PASSWORD;
                        }
                    } else {
                        Answer.resultCode = Result.INVALID_NAME;
                    }
                    break;
                case "agt-bal":
                    if (!loginPassword.isEmpty()) {
                        // запросить баланс
                        getAgentBalance();
                    } else {
                        Answer.resultCode = Result.INVALID_NAME;
                    }
                    break;
                default:
                    if (Answer.resultCode == Result.OK) {
                        Answer.resultCode = Result.WRONG_DATA;
                    }
            }
        } else {
            Answer.resultCode = Result.INVALID_NAME;
        }
        // ответ в XML
        writeXMLAnswer(Answer.resultCode, response);
    }

    /**
     * Метод получения пары логин-пароль по заданному хэшу
     *
     * @param login
     * @return
     */
    private Map<String, String> getLoginPassword(String login) {
        Map<String, String> loginPassword = new HashMap<>();
        try {
            pstatement = conn.prepareStatement("select phonelogin, password from Agent"
                    + "where phonelogin = ?");

            pstatement.setString(1, login);
            ResultSet resultSet = pstatement.executeQuery();
            if (resultSet.next()) {
                String md5login = resultSet.getString(1);
                String password = resultSet.getString(2);
                if (md5login != null && !md5login.isEmpty()) {
                    loginPassword.put(md5login, password);
                }
            }
        } catch (SQLException ex) {
            log.error(ex.getMessage());
        }
        return loginPassword;
    }

    /**
     * Возврат ответа в XML
     *
     * @param resultCode, Result код возврата
     * @param response, HttpServletResponse
     */
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

    /**
     * Создание нового клиента
     */
    private void createNewAgent() {

        try {
            pstatement = conn
                    .prepareStatement("insert into Agent(phonelogin, password, updtime) values (?,?,?)");

            pstatement.setString(1, Agent.phoneLogin);
            pstatement.setString(2, Agent.password);
            pstatement.setTimestamp(3, new Timestamp(new Date().getTime()));

            pstatement.executeUpdate();
        } catch (SQLException ex) {
            log.error(ex.getMessage());
        }

    }

    /**
     * Запись средств на счёт
     */
    private void setFunds() {
        try {
            pstatement = conn
                    .prepareStatement("insert into Balance(accountfunds, updtime) values (?,?)");

            pstatement.setDouble(1, Agent.funds);
            pstatement.setTimestamp(2, new Timestamp(new Date().getTime()));

            pstatement.executeUpdate();

            pstatement = conn
                    .prepareStatement("update Balance set Balance.loginid = (select Agent.id from Agent where Agent.phonelogin = ?)");

            pstatement.setString(1, Agent.phoneLogin);
        } catch (SQLException ex) {
            log.error(ex.getMessage());
        }

    }

    /**
     * Получение md5 строки
     *
     * @param string, входная строка
     * @return string, md5 строка
     */
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

    /**
     * Метод получения баланса счёта
     *
     * @return Double, сумма
     */
    private Double getAgentBalance() {
        Double rv = null;
        try {
            pstatement = conn.prepareStatement("select b.accountfunds from Balance b, Agent a "
                    + " where a.id = b.loginid"
                    + " and a.phonelogin = ?");
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

}
