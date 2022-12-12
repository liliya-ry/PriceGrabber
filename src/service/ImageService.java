package service;

import dao.Image;
import mappers.ImageMapper;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.*;

import java.io.IOException;
import java.io.Reader;

public class ImageService {
    private final SqlSessionFactory factory;

    public ImageService() {
        Reader reader;
        try {
            reader = Resources.getResourceAsReader("mybatis-config.xml");
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
        this.factory = new SqlSessionFactoryBuilder().build(reader);
    }

    public void insertImage(Image image) throws PersistenceException {
        try (SqlSession sqlSession = factory.openSession()) {
            ImageMapper imageMapper = sqlSession.getMapper(ImageMapper.class);
            imageMapper.insertImage(image);
            sqlSession.commit();
        }
    }

    public void updateImage(Image image) throws PersistenceException {
        try (SqlSession sqlSession = factory.openSession()) {
            ImageMapper imageMapper = sqlSession.getMapper(ImageMapper.class);
            imageMapper.updateImage(image);
            sqlSession.commit();
        }
    }
}
