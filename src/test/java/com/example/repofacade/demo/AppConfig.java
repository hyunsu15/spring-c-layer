package com.example.repofacade.demo;

import java.util.UUID;

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

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.example.repofacade")
@MapperScan(basePackages = "com.example.repofacade.demo",
        annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class AppConfig {

    @Bean
    public DataSource dataSource() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        // 컨텍스트마다 별도 DB — 테스트가 설정을 바꿔 컨텍스트를 하나 더 띄워도 init.sql이 충돌하지 않게
        ds.setURL("jdbc:h2:mem:demo-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
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

    // 파사드(DefaultRepoFacade)와 MyBatis 연동(MyBatisRepoFacadeConfiguration)은 따로 선언하지 않는다
    // — 컴포넌트 스캔이 core 패키지를 잡고, MyBatis 연동은 클래스패스에 MyBatis가 있을 때만 붙는다.
}
