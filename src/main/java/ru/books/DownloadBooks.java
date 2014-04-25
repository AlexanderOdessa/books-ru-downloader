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

    public static final String EMPTY_STRING = "";
    private static final Logger log = LoggerFactory.getLogger(DownloadBooks.class);
    private static final String URL = "https://www.books.ru";
    private static final String BOOKS_FOLDER_NAME = "books/";

    public static void main(String[] args) throws IOException, URISyntaxException {
        if (args.length < 3 || args[0].isEmpty() || args[1].isEmpty() || args[2].isEmpty()) {
            log.info(EMPTY_STRING);
            log.info(EMPTY_STRING);
            log.info("Usage: java -jar books-downloader.jar {orderId} {username} {password}");
            log.info(EMPTY_STRING);
            return;
        }

        File booksDir = new File(BOOKS_FOLDER_NAME);
        if (!booksDir.exists()) {
            if (!booksDir.mkdir()) {
                log.error("Error: Cannot create directory '{}'", BOOKS_FOLDER_NAME);
                return;
            }
        }

        BasicCookieStore cookieStore = new BasicCookieStore();
        CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .build();

        HttpUriRequest login = RequestBuilder
                .post()
                .setUri(new URI(URL + "/member/login.php"))
                .addParameter("login", args[1])
                .addParameter("password", args[2])
                .addParameter("go", "login")
                .addParameter("x", "45")
                .addParameter("y", "8")
                .build();
        CloseableHttpResponse loginResponse = httpclient.execute(login);

        if (loginResponse.getStatusLine().getStatusCode() == 302) {
            log.info("Login OK");
        } else {
            log.info("Login failed");
            return;
        }

        loginResponse.close();

        HttpGet httpGet = new HttpGet(URL + "/order.php?order=" + args[0]);
        CloseableHttpResponse booksResponse = httpclient.execute(httpGet);

        Document doc = Jsoup.parse(booksResponse.getEntity().getContent(), "UTF-8", EMPTY_STRING);
        Elements res = doc.select("table.catalog > tbody > tr:not(:last-child)");

        log.info("Found {} books", res.size());

        int i = 1;
        for (Element element : res) {
            try {
                String title = element.select("p.title a").text().replaceAll("/", EMPTY_STRING);

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
                    log.info("{} : {}", i, filename);

                    fileResponse.close();
                }
            } catch (Throwable ex) {
                ex.printStackTrace();
            } finally {
                i++;
            }
        }

        log.info("All books were downloaded");

        booksResponse.close();
        httpclient.close();

    }
}