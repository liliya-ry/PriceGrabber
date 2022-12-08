package dao;

import java.time.LocalDate;
import java.util.Date;

public class Product {
    public int id;
    public String title;
    public double price;
    public String description;
    public String imgSrc;
    public String modified;

    public Product(int id, String title, double price, String description, String imgSrc, String modified) {
        this.id = id;
        this.title = title;
        this.price = price;
        this.description = description;
        this.imgSrc = imgSrc;
        this.modified = modified;
    }

    public Product() {}
}
