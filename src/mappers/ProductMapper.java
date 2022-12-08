package mappers;

import dao.Product;

import java.util.List;

public interface ProductMapper {
    List<Product> getAllProducts();
    void insertProduct(Product product);
    void updateProduct(Product product);
}
