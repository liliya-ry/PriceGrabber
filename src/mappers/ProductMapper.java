package mappers;

import dao.Product;

import java.util.List;

public interface ProductMapper {
    List<Integer> getAllProductIds();
    double getPriceById(int productId);
    void insertProduct(Product product);
    void updateProduct(Product product);
    void markProductAsDeleted(int productId);
}
