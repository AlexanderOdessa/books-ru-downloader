package ru.books;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 *
 * This is a class for downloading purchased E-Books from http://www.books.ru
 *
 */
public class DownloadBooks {

    public static final String GENERAL_LOCATION = "http://www.books.ru";
    public static final String LOGIN_PAGE = GENERAL_LOCATION + "/member/login.php";
    public static final String ORDER_ADDRESS_PREFIX = GENERAL_LOCATION + "/order.php?order=";
    public static final String LOGIN_PARAM = "login";
    public static final String PASSWORD_PARAM = "password";
    public static final String GO_PARAM = "go";
    public static final String MAGIC_PARAM_X = "x";
    public static final String MAGIC_PARAM_Y = "y";
    public static final String MAGIC_VALUE_X = "45";
    public static final String MAGIC_VALUE_Y = "8";
    public static final String DEFAULT_ENCODING = "UTF-8";
    public static final String EMPTY_STRING = "";
    public static final String CSS_TITLE_SELECTOR = "p.title a";
    public static final String EQUAL = "=";
    public static final String HREF_ATTR = "href";
    public static final String CSS_URL_SELECTOR = "td.status a";
    public static final String CSS_FORMAT_SELECTOR = "td.status a:first";
    public static final String BOOKS_CATALOG = "books/";
    public static final String SEPARATOR = " : ";
    public static final String DOT = ".";

    public static void main(String[] args) throws IOException, URISyntaxException {
        if (args.length < 3 || args[0].isEmpty() || args[1].isEmpty() || args[2].isEmpty()) {
            System.out.println("");
            System.out.println("");
            System.out.println("Usage: java -jar books-downloader.jar {orderId} {username} {password}");
            System.out.println("");
            return;
        }

        BasicCookieStore cookieStore = new BasicCookieStore();
        CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .build();

        HttpUriRequest login = RequestBuilder
                .post()
                .setUri(new URI(LOGIN_PAGE))
                .addParameter(LOGIN_PARAM, args[1]).addParameter(PASSWORD_PARAM, args[2])
                .addParameter(GO_PARAM, LOGIN_PARAM)
                .addParameter(MAGIC_PARAM_X, MAGIC_VALUE_X)
                .addParameter(MAGIC_PARAM_Y, MAGIC_VALUE_Y)
                .build();
        CloseableHttpResponse loginResponse = httpclient.execute(login);

        if (loginResponse.getStatusLine().getStatusCode() == 302) {
            System.out.println("Login OK");
        } else {
            System.out.println("Login failed");
            return;
        }

        loginResponse.close();

        HttpGet httpGet = new HttpGet(ORDER_ADDRESS_PREFIX + args[0]);
        CloseableHttpResponse booksResponse = httpclient.execute(httpGet);

        Document doc = Jsoup.parse(booksResponse.getEntity().getContent(), DEFAULT_ENCODING, EMPTY_STRING);
        Elements res = doc.select("table.catalog > tbody > tr:not(:last-child)");

        int i = 1;
        for (Element element : res) {
            try {
                String title = element.select(CSS_TITLE_SELECTOR).text().replaceAll("/", "");
                String url = GENERAL_LOCATION + element.select(CSS_URL_SELECTOR).attr(HREF_ATTR).split(EQUAL)[1];
                String suffix = element.select(CSS_FORMAT_SELECTOR).text();

                HttpGet bookGet = new HttpGet(url);
                CloseableHttpResponse fileResponse = httpclient.execute(bookGet);

                BufferedInputStream inputStraem = new BufferedInputStream(fileResponse.getEntity().getContent());
                BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(BOOKS_CATALOG + (title.length() >= 250 ? title.substring(0, 255) : title) + DOT + suffix));

                int b;
                while ((b = inputStraem.read()) != -1) {
                    outputStream.write(b);
                }

                inputStraem.close();
                outputStream.close();
                System.out.println(i + SEPARATOR + title);

                fileResponse.close();
            } catch (Throwable ex) {
                ex.printStackTrace();
            } finally {
                i++;
            }
        }

        booksResponse.close();
        httpclient.close();

    }
}
