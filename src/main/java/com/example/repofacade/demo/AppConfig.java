package com.example.repofacade.demo;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import com.example.repofacade.core.data.RepoFacadeRegistrar;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.example.repofacade")
@MapperScan(basePackages = "com.example.repofacade.demo",
        annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class AppConfig {

    @Bean
    public DataSource dataSource() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        try (var conn = ds.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("init.sql"));
        }
        return ds;
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        org.apache.ibatis.session.Configuration config =
                new org.apache.ibatis.session.Configuration();
        config.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(config);
        return factory.getObject();
    }

    /** BeanFactoryPostProcessor는 static @Bean으로 등록해야 설정 클래스 초기화보다 먼저 안전하게 뜬다. */
    @Bean
    public static RepoFacadeRegistrar repoFacadeRegistrar() {
        return new RepoFacadeRegistrar();
    }
}
