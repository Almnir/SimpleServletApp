package com.mycompany.simpleservlettest;

import com.mycompany.simpleservletapp.SimpleServlet;
import com.mycompany.simpleservletapp.SimpleServlet.Result;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import junit.framework.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Тест ответа сервлета
 * 
 * @author Ярных А.О.
 */
public class ServletUnitTest extends TestCase {

    Logger log = LoggerFactory.getLogger(ServletUnitTest.class);

    public ServletUnitTest(String testName) {
        super(testName);
    }

    void testServletAnswer() {
        try {
            HttpURLConnection conn = null;
            String xmlData = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<request>\n"
                    + " <request-type>new-agt</request-type>\n"
                    + " <login>1234567890</login> \n"
                    + " <password>password</password> \n"
                    + "</request>";

            String servletAddress = "http://localhost:8080/SimpleServlet/simple";
            String encodedData = URLEncoder.encode(xmlData, "UTF-8");
            URL servletURL = new URL(servletAddress);

            URLConnection uc = servletURL.openConnection();
            conn = (HttpURLConnection) uc;

            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-type", "text/xml");
            PrintWriter pw = new PrintWriter(conn.getOutputStream());
            pw.write(encodedData);
            pw.close();

            // результат
            BufferedReader rd = new BufferedReader(new InputStreamReader(
                    conn.getInputStream()));
            StringBuilder sb = new StringBuilder();

            String line = null;
            while ((line = rd.readLine()) != null) {
                sb.append(line).append('\n');
            }
            rd.close();
            Result answered = parseAnswer(sb);
            if (answered != null) {
                if (answered != Result.OK) {
                    fail("Тест не пройден");
                    log.warn("Сервлет возвратил значение" + answered.toString());
                } else {
                    log.info("Сервлет отработал нормально");
                }
            } else {
                log.warn("Неизвестная ошибка");
            }
        } catch (IOException ex) {
            log.error(ex.getMessage());
        }

    }

    private Result parseAnswer(StringBuilder sb) {
        Result answer = null;
        try {
            // разбор DOM XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder parser = factory.newDocumentBuilder();
            InputSource is = new InputSource();
            is.setCharacterStream(new StringReader(sb.toString()));
            Document document = parser.parse(is);
            document.getDocumentElement().normalize();
            NodeList requestBody = document.getElementsByTagName("request");
            for (int i = 0; i < requestBody.getLength(); i++) {
                Node node = requestBody.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String answerStr = element.getElementsByTagName("result").item(0).getTextContent();
                    answer = Result.valueOf(answerStr);
                }
            }
        } catch (ParserConfigurationException | SAXException ex) {
            log.error("Parse exception " + ex.getMessage());
            SimpleServlet.Answer.resultCode = SimpleServlet.Result.WRONG_DATA;
        } catch (IOException ex) {
            log.error("IO exception " + ex.getMessage());
            SimpleServlet.Answer.resultCode = SimpleServlet.Result.WRONG_DATA;
        }
        return answer;
    }
}
