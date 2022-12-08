import static java.net.http.HttpResponse.BodyHandlers.ofString;

import dao.Product;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;
import service.ProductService;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.util.Map;

public class PriceGrabber {
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

    public static void grabProductList(String url) throws Exception {
        HttpResponse<?> response = getHttpResponse(url);
        String html = (String) response.body();
        Document document = Jsoup.parse(html);
        Element pagesNav = document.select("div[class=\"paging\"]").first();
        Elements pagesLinks = pagesNav.getElementsByTag("a");

        Map<Integer, Product> lastProducts = SERVICE.getAllProducts();

        for (Element pageLink : pagesLinks) {
            String text = pageLink.text();
            if (text.equals("« Предишна") || text.equals("Следваща »")) {
                continue;
            }

            String pageUrl = pageLink.attr("href");
            try {
                grabPage(pageUrl, lastProducts);
            } catch (Exception e) {
                System.out.println("Error processing: " + pageUrl);
            }
        }

        markDeletedProducts(lastProducts);
    }

    public static void grabPage(String url, Map<Integer, Product> lastProducts) throws Exception {
        HttpResponse<?> response = getHttpResponse(url);
        String html = (String) response.body();
        Document document = Jsoup.parse(html);

        Elements productsOnPage = document.select("div[class=\"listItemContainer\"]");
        for (Element page : productsOnPage) {
            Product product = createProduct(page);

            Product oldProduct = lastProducts.get(product.id);
            if (oldProduct == null) {
                SERVICE.insertProduct(product);
                logProduct(product.title, product.price);
                continue;
            }

            double oldPrice = product.price;
            SERVICE.updateProduct(product);
            if (oldPrice != product.price) {
                logPrices(product.title, oldPrice, product.price);
            }
            lastProducts.remove(product.id);
        }
    }

    private static void markDeletedProducts(Map<Integer, Product> lastProducts) {
        for (Map.Entry<Integer, Product> productEntry : lastProducts.entrySet()) {
            Product product = productEntry.getValue();
            product.status = "DELETED";
            SERVICE.updateProduct(product);
            logDeletedProduct(product.title);
        }
    }

    private static Product createProduct(Element page) {
        Product product = new Product();

        String idStr = getAttributeStr(page, "a", "data-id");
        product.id = Integer.parseInt(idStr);

        product.imgSrc = "https:" + getAttributeStr(page, "img", "src");

        Elements spans = page.getElementsByTag("span");
        for (Element spanEl : spans) {
            String classStr = spanEl.attr("class");
            String value = spanEl.text();
            switch (classStr) {
                case "title" -> product.title = value;
                case "location" -> product.description = value;
                case "price" -> product.setPrice(value);
                case "date" -> product.setLastModified(value);
            }
        }

        return product;
    }

    private static void logProduct(String title, double price) {
        System.out.printf("Product added: %s - %.2f%n", title, price);
    }

    private static void logPrices(String title, double oldPrice, double newPrice) {
        System.out.printf("Product: %s , Price changed from %.2f to %.2f%n", title, oldPrice, newPrice);
    }

    private static void logDeletedProduct(String title) {
        System.out.printf("Deleted product: %s%n", title);
    }

    private static String getAttributeStr(Element element, String tagName, String attrKey) {
        Element link = element.getElementsByTag(tagName).first();
        return link.attr(attrKey);
    }

    public static void main(String[] args) {
        if (args == null || args.length != 1) {
            System.out.println("Invalid command");
            return;
        }

        String url = args[0];

        try {
            PriceGrabber.grabProductList(url);
        } catch (Exception e) {
            System.out.println("Error processing : " + url);
        }
    }
}
