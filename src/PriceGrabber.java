import dao.Product;
import org.apache.ibatis.exceptions.PersistenceException;
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
    public static  final ProductService SERVICE = new ProductService();
    private static final HttpClient CLIENT = HttpClient.newBuilder().build();

    private static HttpResponse<?> getHttpResponse(String url) throws URISyntaxException, InterruptedException, IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .GET()
                .build();

        HttpResponse.BodyHandler<?> bodyHandler = ofString();
        return CLIENT.send(request, bodyHandler);
    }

    public static void processUrl(String url) throws URISyntaxException, IOException, InterruptedException {
        HttpResponse<?> response = getHttpResponse(url);
        String html = (String) response.body();
        Document document = Jsoup.parse(html);

        Elements productsOnPage = document.select("div[class=\"listItemContainer\"]");
        for (Element page : productsOnPage) {
            Product product = createProduct(page);

            try {
                SERVICE.insertProduct(product);
                logProduct(product.title, product.price);
            } catch (PersistenceException e) {
                double oldPrice = SERVICE.getPriceById(product.id);
                SERVICE.updateProduct(product);
                if (oldPrice != product.price) {
                    logPrices(product.title, oldPrice, product.price);
                }
            }
        }
    }

    private static Product createProduct(Element page) {
        Product product = new Product();

        String idStr = getAttributeStr(page, "a", "data-id");
        product.id = Integer.parseInt(idStr);

        product.imgSrc = getAttributeStr(page, "img", "abs:src");

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

        return product;
    }

    private static void logProduct(String title, double price) {
        System.out.printf("Product added: %s - %f%n", title, price);
    }

    private static void logPrices(String title, double oldPrice, double newPrice) {
        System.out.printf("Product: %s , Price changed from %f to f%n", title, oldPrice, newPrice);
    }

    private static String getAttributeStr(Element element, String tagName, String attrKey) {
        Element link = element.getElementsByTag(tagName).first();
        return link.attr(attrKey);
    }

    public static void main(String[] args) throws URISyntaxException, IOException, InterruptedException {
        if (args == null || args.length != 1) {
            System.out.println("Invalid command");
            return;
        }

        String url = args[0];
        PriceGrabber.processUrl(url);
    }
}
