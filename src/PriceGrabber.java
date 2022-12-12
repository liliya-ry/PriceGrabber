import static java.net.http.HttpResponse.BodyHandlers.*;

import dao.Image;
import dao.Product;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.logging.log4j.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;
import service.ImageService;
import service.ProductService;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;

public class PriceGrabber {
    private ProductService productService = new ProductService();
    private ImageService imageService = new ImageService();
    private HttpClient client = HttpClient.newBuilder().build();
    private Logger logger;
    private Set<Integer> addedProductsId = new HashSet<>();
    private Set<Integer> dbProductsIds;
    private String imageDir;
    private String baseUrl;

    public  PriceGrabber(String baseUrl, String imageDir) {
        this.baseUrl = baseUrl;
        this.imageDir = imageDir;
        dbProductsIds = productService.getAllProductIds();
        //LoggingConfigurator.configureLogging();
        this.logger = LogManager.getLogger(PriceGrabber.class);
    }

    public void grabProductList() {
        int lastIndex = baseUrl.indexOf('?');
        String pageOneUrl = lastIndex != -1 ? baseUrl.substring(0, lastIndex) : baseUrl;
        try {
            grabPage(pageOneUrl);
        } catch (Exception e) {
            logger.error(e);
            return;
        }
        markDeletedProducts();
    }

    public void grabPage(String url) throws Exception {
        Document document = getDocumentFromUrl(url);

        Elements productsOnPage = document.select("div[class=\"listItemContainer\"]");
        for (Element page : productsOnPage) {
            Product product = createProduct(page);
            addedProductsId.add(product.id);

            if (!dbProductsIds.contains(product.id)) {
                try {
                    productService.insertProduct(product);
                } catch (PersistenceException e) {
                    updateProduct(product);
                    continue;
                }

                logger.info("Product added: {} - {}", product.title, product.price);
                continue;
            }

            updateProduct(product);
        }

        grabNextPage(document);
    }

    private void updateProduct(Product product) {
        double oldPrice = productService.getPriceById(product.id);
        productService.updateProduct(product);
        if (oldPrice != product.price) {
            logger.info("Product: {}, Price changed from {} to {}", product.title, oldPrice, product.price);
        }
    }

    private void grabNextPage(Document document)  {
        Element nextPageEl = document.select("a[class=\"btn btn-default next\"]").first();
        if (nextPageEl == null) {
            return;
        }
        String nextPageUrl = nextPageEl.attr("href");
        try {
            grabPage(nextPageUrl);
        } catch (Exception e) {
            logger.error(e);
        }
    }


    private void markDeletedProducts() {
        for (Integer id : dbProductsIds) {
            if (addedProductsId.contains(id)) {
                continue;
            }
            productService.markProductAsDeleted(id);
            logger.info("Deleted Product: {}", id);
        }
    }

    private Product createProduct(Element page) {
        Product product = new Product();
        String idStr = getAttributeStr(page, "a", "data-id");
        product.id = Integer.parseInt(idStr);

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

        product.imgSrc = imageDir + "/" + product.id;
        String productUrl = getAttributeStr(page, "a", "href");
        try {
            loadGalleryAndDescription(product, productUrl);
        } catch (Exception e) {
            logger.error(e);
        }

        return product;
    }

    private void loadGalleryAndDescription(Product product, String productUrl) throws Exception {
        Document document = getDocumentFromUrl(productUrl);
        Element descriptionEl = document.select("div[itemprop=\"description\"]").first();
        product.description = descriptionEl.text();
        createDirIfDoesNotExist(imageDir);
        downloadImages(product, document);
    }

    private void downloadImages(Product product, Document document) throws IOException {
        createDirIfDoesNotExist(product.imgSrc);
        Elements galleryElements = document.select("span[class=\"gallery-element\"]");
        int imgNameCount = 1;
        for (Element galleryEl : galleryElements) {
            Element imageEl = galleryEl.getElementsByTag("img").first();
            String imageLink = "https:" + imageEl.attr("src");
            try {
                downloadImage(imageLink, product, imgNameCount++);
            } catch (Exception e) {
                logger.error(e);
                continue;
            }
        }
    }

    private void downloadImage(String imgUrl, Product product, int imageNameCount) throws Exception {
        HttpResponse<?> response = getHttpResponse(imgUrl, InputStream.class);
        try (InputStream in = (InputStream) response.body()) {
            String imgName = imageNameCount + "." + getImgFormat(response);
            String pathName = product.imgSrc + "/" + imgName;
            File file = new File(pathName);
            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Image image = new Image(product.id, imgUrl, pathName);
            try {
                imageService.insertImage(image);
            } catch (PersistenceException e) {
                imageService.updateImage(image);
            }
        }
    }

    private void createDirIfDoesNotExist(String dir) throws IOException {
        Path dirPath = Paths.get(dir);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
    }

    private String getImgFormat(HttpResponse<?> response) {
        HttpHeaders httpHeaders = response.headers();
        List<String> contentTypeList = httpHeaders.allValues("Content-Type");
        String imgFormat = contentTypeList.get(0);

        int startIndex = imgFormat.indexOf("/") + 1;
        imgFormat = imgFormat.substring(startIndex);

        if (imgFormat.equals("svg+xml")) {
            imgFormat = "svg";
        }

        return imgFormat;
    }

    private Document getDocumentFromUrl(String url) throws Exception {
        HttpResponse<?> response = getHttpResponse(url, String.class);
        String html = (String) response.body();
        Document document = Jsoup.parse(html);
        return document;
    }

    private String getAttributeStr(Element element, String tagName, String attrKey) {
        Element link = element.getElementsByTag(tagName).first();
        return link.attr(attrKey);
    }

    private HttpResponse<?> getHttpResponse(String url, Class<?> cl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .GET()
                .build();

        HttpResponse.BodyHandler<?> bodyHandler = cl.equals(String.class) ? ofString() : ofInputStream();
        return client.send(request, bodyHandler);
    }

    public static void main(String[] args) {
        if (args == null || args.length != 2) {
            System.out.println("Invalid command: Try: URL DIR");
            return;
        }

        String url = args[0];
        String dir = args[1];
        PriceGrabber priceGrabber = new PriceGrabber(url, dir);
        priceGrabber.grabProductList();

    }
}
