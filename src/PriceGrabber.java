import static java.net.http.HttpResponse.BodyHandlers.*;

import dao.Image;
import dao.Product;
import org.apache.commons.cli.*;
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
    private static final String DEFAULT_DIR = "images";
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
        this.logger = LogManager.getLogger(PriceGrabber.class);
    }

    public void grabProductList() {
        String pageOneUrl = getFirstPageUrl();
        try {
            grabPage(pageOneUrl);
        } catch (Exception e) {
            logger.error(e);
            return;
        }
        markDeletedProducts();
    }

    private String getFirstPageUrl() {
        int lastIndex = baseUrl.indexOf('?');
        return lastIndex != -1 ? baseUrl.substring(0, lastIndex) : baseUrl;
    }

    public void grabPage(String url) throws Exception {
        Document document = getDocumentFromUrl(url);

        Elements productsOnPage = document.select("div[class=\"listItemContainer\"]");
        for (Element page : productsOnPage) {
            Product product = createProduct(page);
            parseDataFromProductPage(page, product);

            if (!dbProductsIds.isEmpty() && (dbProductsIds.contains(product.id) || addedProductsId.contains(product.id))) {
                updateProduct(product);
                continue;
            }

            productService.insertProduct(product);
            addedProductsId.add(product.id);
            logger.info("Product added: {} - {}", product.title, product.price);
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

    private void grabNextPage(Document document) throws Exception {
        Element nextPageEl = document.select("a[class=\"btn btn-default next\"]").first();
        if (nextPageEl == null) {
            return;
        }
        String nextPageUrl = nextPageEl.attr("href");
        grabPage(nextPageUrl);
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

        return product;
    }

    private void parseDataFromProductPage(Element page, Product product) throws Exception {
        product.imgSrc = "/" + product.id;
        String productUrl = getAttributeStr(page, "a", "href");

        Document document = getDocumentFromUrl(productUrl);
        Element descriptionEl = document.select("div[itemprop=\"description\"]").first();
        product.description = descriptionEl.text();

        createDirIfDoesNotExist(imageDir);
        downloadProductImages(product, document);
    }

    private void downloadProductImages(Product product, Document document) throws IOException {
        createDirIfDoesNotExist(imageDir + "/" + product.imgSrc);
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
            String path = product.imgSrc + "/" + imgName;
            String fullPath = imageDir + "/" + path;
            File file = new File(fullPath);
            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Image image = new Image(product.id, imgUrl, path);
            imageService.insertImage(image);
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
        return Jsoup.parse(html);
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

    public static Options createOptions(String[] args) {
        Options options = new Options();
        Option option = Option.builder("d").hasArg().build();
        options.addOption(option);
        return options;
    }

    private static CommandLine getCmd(Options options, String[] args) {
        CommandLineParser parser = new DefaultParser();
        try {
            return parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println("price grabber: unknown option --");
            return null;
        }
    }

    private static void printUsage() {
        System.out.println("Invalid command: Try: URL DIR");
    }

    public static void main(String[] args) {
        if (args == null) {
            printUsage();
            return;
        }

        Options options = createOptions(args);
        CommandLine cmd = getCmd(options, args);
        if (cmd == null) {
            return;
        }

        String[] urls = cmd.getArgs();
        if (urls.length != 1) {
            printUsage();
            return;
        }

        String url = urls[0];
        String dir = cmd.getOptionValue("d", DEFAULT_DIR);
        PriceGrabber priceGrabber = new PriceGrabber(url, dir);
        priceGrabber.grabProductList();

    }
}
