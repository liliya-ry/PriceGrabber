package dao;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

public class Product {
    private static final Pattern PRICE_PATTERN = Pattern.compile("(\\d+(\\,)?(\\d+)?)");
    private static final Map<String, Integer> MONTHS;
    public static final String DEFAULT_STATUS = "AVAILABLE";

    public int id;
    public String title;
    public double price;
    public String address;
    public String imgSrc;
    public Date lastModified;
    public String status = DEFAULT_STATUS;
    public String description;

    static {
        MONTHS = new HashMap<>();
        String[] months = {"януари", "февруари", "март", "април", "май", "юни",
                "юли", "август", "септември", "октомври", "ноември", "декември"};
        for (int i = 0; i < months.length; i++) {
            MONTHS.put(months[i], i + 1);
        }

    }

    public Product() {}

    public void setLastModified(String modifiedStr) {
        this.lastModified = switch (modifiedStr) {
            case "днес" -> Date.valueOf(LocalDate.now());
            case "вчера" -> Date.valueOf(LocalDate.now().minusDays(1));
            default -> formatDate(modifiedStr);
        };
    }

    private Date formatDate(String date) {
        String[] dateParts = date.split(" ");
        int day = Integer.parseInt(dateParts[0]);
        int month = MONTHS.get(dateParts[1]);
        int year = LocalDate.now().getYear();
        return Date.valueOf(LocalDate.of(year, month, day));
    }

    public void setPrice(String priceStr) {
        Matcher matcher = PRICE_PATTERN.matcher(priceStr);
        if (matcher.find()) {
            String priceValue = matcher.group().replace(',', '.');
            this.price = Double.parseDouble(priceValue);
        }
    }
}
