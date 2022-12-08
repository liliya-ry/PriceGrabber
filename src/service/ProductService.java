package service;

import dao.Product;
import mappers.ProductMapper;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.*;

import java.io.*;
import java.util.*;

public class ProductService {
    private final SqlSessionFactory factory;

    public ProductService() {
        Reader reader;
        try {
            reader = Resources.getResourceAsReader("mybatis-config.xml");
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
        this.factory = new SqlSessionFactoryBuilder().build(reader);
    }

    public Map<Integer, Product> getAllProducts() {
        try (SqlSession sqlSession = factory.openSession()) {
            ProductMapper productMapper = sqlSession.getMapper(ProductMapper.class);
            List<Product> products = productMapper.getAllProducts();
            Map<Integer, Product> res = new HashMap<>();
            for (Product product : products) {
                res.put(product.id, product);
            }
            return res;
        }
    }

    public void insertProduct(Product product) {
        try (SqlSession sqlSession = factory.openSession()) {
            ProductMapper productMapper = sqlSession.getMapper(ProductMapper.class);
            productMapper.insertProduct(product);
            sqlSession.commit();
        }
    }

    public void updateProduct(Product product) {
        try (SqlSession sqlSession = factory.openSession()) {
            ProductMapper productMapper = sqlSession.getMapper(ProductMapper.class);
            productMapper.updateProduct(product);
            sqlSession.commit();
        }
    }
}
