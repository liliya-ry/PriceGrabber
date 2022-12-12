package dao;

public class Image {
    int id;
    int productId;
    String imgUrl;
    String imgPath;

    public Image() {}

    public Image(int productId, String imgUrl, String imgPath) {
        this.productId = productId;
        this.imgUrl = imgUrl;
        this.imgPath = imgPath;
    }
}
