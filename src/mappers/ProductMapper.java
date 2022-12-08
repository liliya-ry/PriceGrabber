package mappers;

import dao.Product;

public interface ProductMapper {
    Product getProductById(int id);
    void insertProduct(Product product);
    int updateProduct(Product product);
}
