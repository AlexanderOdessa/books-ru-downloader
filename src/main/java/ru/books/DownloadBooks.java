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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * This is a class for downloading purchased E-Books from http://www.books.ru
 */
public class DownloadBooks {

    public static final String UNRERLINE = "_";
    private static final Logger log = LoggerFactory.getLogger(DownloadBooks.class);
    private static final String EMPTY_STRING = "";
    private static final String URL = "https://www.books.ru";
    private static final String BOOKS_FOLDER_NAME = "books/";

    public static void main(String[] parameters) {
        try {
            checkInputParameters(parameters);

            String orderId = parameters[0];
            String username = parameters[1];
            String password = parameters[2];

            checkOutputFolder();
            CloseableHttpClient httpclient = createHttpClient();
            login(httpclient, username, password);
            getBooks(orderId, httpclient);
            httpclient.close();
        } catch (IOException ex) {
            log.info("Warning: Cannot properly close HTTP connection to books.ru");
        } catch (DownloadException ex) {
            log.error(ex.getMessage());
        }
    }

    private static void getBooks(String orderId, CloseableHttpClient httpclient) throws DownloadException {
        try {
            HttpGet httpGet = new HttpGet(URL + "/order.php?order=" + orderId);
            CloseableHttpResponse booksResponse = httpclient.execute(httpGet);

            Document doc = Jsoup.parse(booksResponse.getEntity().getContent(), "UTF-8", EMPTY_STRING);
            Elements bookElements = doc.select("table.catalog > tbody > tr:not(:last-child)");

            log.info("Found {} books", bookElements.size());

            processBooks(httpclient, bookElements);

            log.info("All books were downloaded");

            booksResponse.close();
        } catch (IOException ex) {
            throw new DownloadException("Error: Cannot get books. Reason: " + ex.getMessage());
        }
    }

    private static void processBooks(CloseableHttpClient httpclient, Elements res) {
        int booksCount = 1;
        for (Element element : res) {
            try {
                String title = element.select("p.title a").text()
                        .replaceAll("/", EMPTY_STRING)
                        .replaceAll("\\\\", EMPTY_STRING)
                        .replaceAll("\\?", EMPTY_STRING)
                        .replaceAll("\\%", EMPTY_STRING)
                        .replaceAll("\\*", EMPTY_STRING)
                        .replaceAll("\\:", EMPTY_STRING)
                        .replaceAll("\\|", EMPTY_STRING)
                        .replaceAll("\"", EMPTY_STRING)
                        .replaceAll("\\<", EMPTY_STRING)
                        .replaceAll("\\>", EMPTY_STRING)
                        .replaceAll("\\,", EMPTY_STRING)
                        .replaceAll(" ", UNRERLINE);

                Elements urls = element.select("td.status a");
                for (Element url : urls) {
                    String bookUrl = URL + url.attr("href").split("=")[1];
                    String suffix = url.text();

                    HttpGet bookGet = new HttpGet(bookUrl);
                    CloseableHttpResponse fileResponse = httpclient.execute(bookGet);

                    BufferedInputStream inputStraem = new BufferedInputStream(fileResponse.getEntity().getContent());
                    String filename = (title.length() > 120 ? title.substring(0, 119) : title) + "." + suffix;
                    BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(BOOKS_FOLDER_NAME + filename));

                    int b;
                    while ((b = inputStraem.read()) != -1) {
                        outputStream.write(b);
                    }

                    inputStraem.close();
                    outputStream.close();
                    log.info("{} : {} ... Saved", booksCount, filename);

                    fileResponse.close();
                }
            } catch (Throwable ex) {
                ex.printStackTrace();
            } finally {
                booksCount++;
            }
        }
    }

    private static void login(CloseableHttpClient httpclient, String username, String password) throws DownloadException {
        HttpUriRequest login;
        try {
            login = RequestBuilder
                    .post()
                    .setUri(new URI(URL + "/member/login.php"))
                    .addParameter("login", username)
                    .addParameter("password", password)
                    .addParameter("go", "login")
                    .addParameter("x", "45")
                    .addParameter("y", "8")
                    .build();
        } catch (URISyntaxException ex) {
            throw new DownloadException("Error: You do not see that error. It's impossible.");
        }

        CloseableHttpResponse loginResponse;
        try {
            loginResponse = httpclient.execute(login);
        } catch (IOException ex) {
            throw new DownloadException("Error: Cannot process login request. Reason: " + ex.getMessage());
        }

        if (loginResponse.getStatusLine().getStatusCode() == 302) {
            log.info("Login OK");
        } else {
            log.info("Login failed");
            throw new DownloadException("Error: Login failed. Check username and password");
        }

        try {
            loginResponse.close();
        } catch (IOException ex) {
            log.info("Warning: Cannot properly close HTTP connection to books.ru");
        }
    }

    private static CloseableHttpClient createHttpClient() {
        BasicCookieStore cookieStore = new BasicCookieStore();
        return HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .build();
    }

    private static void checkInputParameters(String[] parameters) throws DownloadException {
        if (parameters.length < 3 || parameters[0].isEmpty() || parameters[1].isEmpty() || parameters[2].isEmpty()) {
            log.info(EMPTY_STRING);
            log.info(EMPTY_STRING);
            log.info("Usage: java -jar books-downloader.jar {orderId} {username} {password}");
            log.info(EMPTY_STRING);
            throw new DownloadException("Wrong arguments");
        }
    }

    private static void checkOutputFolder() throws DownloadException {
        File booksDir = new File(BOOKS_FOLDER_NAME);
        if (!booksDir.exists()) {
            if (!booksDir.mkdir()) {
                log.error("Error: Cannot create directory '{}'", BOOKS_FOLDER_NAME);
                throw new DownloadException("Error: Cannot create directory '" + BOOKS_FOLDER_NAME + "'");
            }
        }
    }
}
