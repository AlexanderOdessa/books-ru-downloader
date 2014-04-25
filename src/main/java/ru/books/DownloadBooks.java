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
 * Hello world!
 * <p/>
 * Usage: DownloadBooks [OrderNumber] [Username] [Password]
 */
public class DownloadBooks {
    public static void main(String[] args) throws IOException, URISyntaxException {
        BasicCookieStore cookieStore = new BasicCookieStore();
        CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .build();

        HttpUriRequest login = RequestBuilder.post().setUri(new URI("http://www.books.ru/member/login.php")).addParameter("login", args[1]).addParameter("password", args[2]).addParameter("go", "login").addParameter("x", "45").addParameter("y", "8").build();
        CloseableHttpResponse loginResponse = httpclient.execute(login);

        loginResponse.close();

        HttpGet httpGet = new HttpGet("http://www.books.ru/order.php?order=" + args[0]);
        CloseableHttpResponse booksResponse = httpclient.execute(httpGet);

        Document doc = Jsoup.parse(booksResponse.getEntity().getContent(), "UTF-8", "");
        Elements res = doc.select("table.catalog > tbody > tr:not(:last-child)");

        int i = 1;
        for (Element element : res) {
            try {
                String title = element.select("p.title a").text().replaceAll("/", "");
                String url = "http://www.books.ru" + element.select("td.status a").attr("href").split("=")[1];
                String suffix = element.select("td.status a").text();

                HttpGet bookGet = new HttpGet(url);
                CloseableHttpResponse fileResponse = httpclient.execute(bookGet);

                BufferedInputStream inputStraem = new BufferedInputStream(fileResponse.getEntity().getContent());
                BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream("books/" + (title.length() >= 100 ? title.substring(0, 97) + "..." : title) + "." + suffix));

                int b = 0;
                while ((b = inputStraem.read()) != -1) {
                    outputStream.write(b);
                }

                inputStraem.close();
                outputStream.close();
                System.out.println(i + " : " + title);

                fileResponse.close();
            } catch (Throwable ex) {
                ex.printStackTrace();
            } finally {
                i++;
            }
        }

        booksResponse.close();

    }
}
