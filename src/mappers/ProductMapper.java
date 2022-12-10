package mappers;

import dao.Product;

import java.util.Set;

public interface ProductMapper {
    Set<Integer> getAllProductIds();
    double getPriceById(int productId);
    void insertProduct(Product product);
    void updateProduct(Product product);
    void markProductAsDeleted(int productId);
}
