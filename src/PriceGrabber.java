import static java.net.http.HttpResponse.BodyHandlers.ofString;

import dao.Product;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;
import service.ProductService;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PriceGrabber {
    private ProductService service;
    private HttpClient client;
    private Logger logger;
    private String url;
    private Set<Integer> addedProductsId;

    public PriceGrabber(String url) {
        this.url = url;
        service = new ProductService();
        client = HttpClient.newBuilder().build();
        logger = LogManager.getLogger(PriceGrabber.class);
        addedProductsId = new HashSet<>();
    }

    private HttpResponse<?> getHttpResponse(String url) throws URISyntaxException, InterruptedException, IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .GET()
                .build();

        HttpResponse.BodyHandler<?> bodyHandler = ofString();
        return client.send(request, bodyHandler);
    }

    public void grabProductList() throws Exception {
        HttpResponse<?> response = getHttpResponse(url);
        String html = (String) response.body();
        Document document = Jsoup.parse(html);
        Element pagesNav = document.select("div[class=\"paging\"]").first();
        Elements pagesLinks = pagesNav.getElementsByTag("a");

        for (Element pageLink : pagesLinks) {
            String text = pageLink.text();
            if (text.equals("« Предишна") || text.equals("Следваща »")) {
                continue;
            }

            String pageUrl = pageLink.attr("href");
            try {
                grabPage(pageUrl);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

        markDeletedProducts();
    }

    public void grabPage(String url) throws Exception {
        HttpResponse<?> response = getHttpResponse(url);
        String html = (String) response.body();
        Document document = Jsoup.parse(html);

        Elements productsOnPage = document.select("div[class=\"listItemContainer\"]");
        for (Element page : productsOnPage) {
            Product product = createProduct(page);

            try {
                service.insertProduct(product);
                logger.info("Product added: {} - {}", product.title, product.price);
            } catch (PersistenceException e) {
                double oldPrice = service.getPriceById(product.id);
                service.updateProduct(product);
                if (oldPrice != product.price) {
                    logger.info("Product: {}, Price changed from {} to {}", product.title, oldPrice, product.price);
                }
            }

            addedProductsId.add(product.id);
        }
    }

    private void markDeletedProducts() {
        List<Integer> dbProductsId = service.getAllProductIds();
        for (Integer id : dbProductsId) {
            if (addedProductsId.contains(id)) {
                continue;
            }
            service.markProductAsDeleted(id);
            logger.info("Deleted Product: {}", id);
        }
    }

    private Product createProduct(Element page) {
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
                case "location" -> product.address = value;
                case "price" -> product.setPrice(value);
                case "date" -> product.setLastModified(value);
            }
        }

        return product;
    }

    private String getAttributeStr(Element element, String tagName, String attrKey) {
        Element link = element.getElementsByTag(tagName).first();
        return link.attr(attrKey);
    }

    public static void main(String[] args) {
        if (args == null || args.length != 1) {
            System.out.println("Invalid command");
            return;
        }

        String url = args[0];
        PriceGrabber priceGrabber = new PriceGrabber(url);
        try {
            priceGrabber.grabProductList();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
