package com.example.csvbatch.persistence;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.batch.autoconfigure.BatchTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class TransactionConfiguration {
    /** Batch steps and the JDBC JobRepository must commit through the same manager. */
    @Bean
    @Primary
    @BatchTransactionManager
    JdbcTransactionManager batchTransactionManager(DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }

    /** Spring Data JPA's default transaction-manager bean name. */
    @Bean(name = "transactionManager")
    JpaTransactionManager jpaTransactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
