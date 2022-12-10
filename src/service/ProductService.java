package service;

import dao.Product;
import mappers.ProductMapper;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.*;

import java.io.*;
import java.io.Reader;
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

    public double getPriceById(int productId) {
        try (SqlSession sqlSession = factory.openSession()) {
            ProductMapper productMapper = sqlSession.getMapper(ProductMapper.class);
            return productMapper.getPriceById(productId);
        }
    }

    public Set<Integer> getAllProductIds() {
        try (SqlSession sqlSession = factory.openSession()) {
            ProductMapper productMapper = sqlSession.getMapper(ProductMapper.class);
            return productMapper.getAllProductIds();
        }
    }

    public void insertProduct(Product product) throws PersistenceException {
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

    public void markProductAsDeleted(int productId) {
        try (SqlSession sqlSession = factory.openSession()) {
            ProductMapper productMapper = sqlSession.getMapper(ProductMapper.class);
            productMapper.markProductAsDeleted(productId);
            sqlSession.commit();
        }
    }
}
