import dao.Product;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import service.ProductService;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Pattern;
import static java.net.http.HttpResponse.BodyHandlers.ofString;

public class PriceGrabber {
    private static final Pattern PRICE_PATTERN = Pattern.compile("(\\d+)");
    private final HttpClient client;
    public ProductService service;

    public PriceGrabber() {
        this.client = HttpClient.newBuilder().build();
        this.service = new ProductService();
    }

    private HttpResponse<?> getHttpResponse(String url) throws URISyntaxException, InterruptedException, IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .GET()
                .build();

        HttpResponse.BodyHandler<?> bodyHandler = ofString();
        return client.send(request, bodyHandler);
    }

    public void processUrl(String url) throws URISyntaxException, IOException, InterruptedException {
        HttpResponse<?> response = getHttpResponse(url);
        String html = (String) response.body();
        Document document = Jsoup.parse(html);

        Elements productsOnPage = document.select("div[class=\"listItemContainer\"]");
        for (Element page : productsOnPage) {
            Product product = new Product();

            Elements links = page.getElementsByTag("a");
            for (Element link : links) {
                String idStr = link.attr("data-id");
                product.id = Integer.parseInt(idStr);
            }

            Elements images = page.getElementsByTag("img");
            for (Element image : images) {
                product.imgSrc = image.attr("abs:src");
            }

            Elements spans = page.getElementsByTag("span");
            for (Element spanEl : spans) {
                String classStr = spanEl.attr("class");
                String value = spanEl.text();
                switch (classStr) {
                    case "title" -> product.title = value;
                    case "location" -> product.description = value;
                    case "price" -> {
                        String priceStr = value.substring(0, 2);
                        product.price = Double.parseDouble(priceStr);
                    }
                    case "date" -> product.modified = value;
                }
            }
            service.insertProduct(product);
        }
    }

    public static void main(String[] args) throws URISyntaxException, IOException, InterruptedException {
//        if (args == null || args.length != 1) {
//            System.out.println("Invalid command");
//            return;
//        }
//
//        String url = args[0];
        PriceGrabber grabber = new PriceGrabber();
        grabber.processUrl("https://bazar.bg/obiavi/damski-drehi");
    }
}
