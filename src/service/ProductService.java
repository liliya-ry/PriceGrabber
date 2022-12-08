package service;

import dao.Product;
import mappers.ProductMapper;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.*;

import java.io.*;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

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

    public void insertProduct(Product product) throws PersistenceException {
        try (SqlSession sqlSession = factory.openSession()) {
            ProductMapper productMapper = sqlSession.getMapper(ProductMapper.class);
            productMapper.insertProduct(product);
            sqlSession.commit();
        }
    }

    public Product getProductById(Integer id) {
        try (SqlSession sqlSession = factory.openSession()) {
            ProductMapper productMapper = sqlSession.getMapper(ProductMapper.class);
            return productMapper.getProductById(id);
        }
    }

    public double getPriceById(Integer id) {
        try (SqlSession sqlSession = factory.openSession()) {
            ProductMapper productMapper = sqlSession.getMapper(ProductMapper.class);
            return productMapper.getPriceById(id);
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
