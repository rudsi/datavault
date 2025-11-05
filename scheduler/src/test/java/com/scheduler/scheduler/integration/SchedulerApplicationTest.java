package com.scheduler.scheduler.integration;

import com.scheduler.scheduler.config.GrpcServerConfig;
import com.scheduler.scheduler.config.RabbitMQConfig;
import com.scheduler.scheduler.controller.FileUploadController;
import com.scheduler.scheduler.repository.FileMetadataRepository;
import com.scheduler.scheduler.service.CreateMetadataService;
import com.scheduler.scheduler.service.SchedulerServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Scheduler Application context loading and bean configuration.
 * Verifies that the Spring Boot application starts correctly with all required beans.
 */
@SpringBootTest
@Testcontainers
class SchedulerApplicationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.12-management");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("grpc.server.port", () -> "0"); // Use random port for testing
    }

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Tests that the Spring Boot application context loads successfully.
     * Verifies that:
     * - Application context is not null
     * - Application context contains beans
     */
    @Test
    void testApplicationContext_Loads_Successfully() {
        // Assert
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBeanDefinitionCount()).isGreaterThan(0);
    }

    /**
     * Tests that all required controller beans are created.
     * Verifies that:
     * - FileUploadController bean exists
     * - Controller is properly configured
     */
    @Test
    void testControllerBeans_Created_Successfully() {
        // Act
        FileUploadController controller = applicationContext.getBean(FileUploadController.class);

        // Assert
        assertThat(controller).isNotNull();
    }

    /**
     * Tests that all required service beans are created.
     * Verifies that:
     * - SchedulerServiceImpl bean exists
     * - CreateMetadataService bean exists
     * - Services are properly configured
     */
    @Test
    void testServiceBeans_Created_Successfully() {
        // Act
        SchedulerServiceImpl schedulerService = applicationContext.getBean(SchedulerServiceImpl.class);
        CreateMetadataService createMetadataService = applicationContext.getBean(CreateMetadataService.class);

        // Assert
        assertThat(schedulerService).isNotNull();
        assertThat(createMetadataService).isNotNull();
    }

    /**
     * Tests that all required repository beans are created.
     * Verifies that:
     * - FileMetadataRepository bean exists
     * - Repository is properly configured
     */
    @Test
    void testRepositoryBeans_Created_Successfully() {
        // Act
        FileMetadataRepository repository = applicationContext.getBean(FileMetadataRepository.class);

        // Assert
        assertThat(repository).isNotNull();
    }

    /**
     * Tests that RabbitMQ configuration beans are created correctly.
     * Verifies that:
     * - Queue bean exists with correct name
     * - RabbitAdmin bean exists
     * - RabbitTemplate bean exists
     * - ConnectionFactory bean exists
     */
    @Test
    void testRabbitMQBeans_Created_WithCorrectConfiguration() {
        // Act
        Queue queue = applicationContext.getBean("fileChunksQueue", Queue.class);
        RabbitAdmin rabbitAdmin = applicationContext.getBean(RabbitAdmin.class);
        RabbitTemplate rabbitTemplate = applicationContext.getBean(RabbitTemplate.class);
        ConnectionFactory connectionFactory = applicationContext.getBean(ConnectionFactory.class);

        // Assert
        assertThat(queue).isNotNull();
        assertThat(queue.getName()).isEqualTo(RabbitMQConfig.CHUNK_QUEUE);
        assertThat(queue.isDurable()).isTrue();
        
        assertThat(rabbitAdmin).isNotNull();
        assertThat(rabbitTemplate).isNotNull();
        assertThat(connectionFactory).isNotNull();
    }

    /**
     * Tests that gRPC server configuration bean is created.
     * Verifies that:
     * - GrpcServerConfig bean exists
     * - Server is properly initialized
     */
    @Test
    void testGrpcServerConfig_Created_Successfully() {
        // Act
        GrpcServerConfig grpcConfig = applicationContext.getBean(GrpcServerConfig.class);

        // Assert
        assertThat(grpcConfig).isNotNull();
        assertThat(grpcConfig.getPort()).isGreaterThan(0);
    }

    /**
     * Tests that configuration properties are applied correctly.
     * Verifies that:
     * - Database connection properties are set
     * - RabbitMQ connection properties are set
     * - gRPC server port is configured
     */
    @Test
    void testConfigurationProperties_Applied_Correctly() {
        // Act
        String datasourceUrl = applicationContext.getEnvironment().getProperty("spring.datasource.url");
        String rabbitmqHost = applicationContext.getEnvironment().getProperty("spring.rabbitmq.host");
        String grpcPort = applicationContext.getEnvironment().getProperty("grpc.server.port");

        // Assert
        assertThat(datasourceUrl).isNotNull();
        assertThat(datasourceUrl).contains("jdbc:postgresql");
        
        assertThat(rabbitmqHost).isNotNull();
        assertThat(rabbitmqHost).isEqualTo(rabbitmq.getHost());
        
        assertThat(grpcPort).isNotNull();
    }

    /**
     * Tests that JPA configuration is properly set up.
     * Verifies that:
     * - EntityManagerFactory bean exists
     * - TransactionManager bean exists
     */
    @Test
    void testJpaConfiguration_SetUp_Correctly() {
        // Act
        boolean hasEntityManagerFactory = applicationContext.containsBean("entityManagerFactory");
        boolean hasTransactionManager = applicationContext.containsBean("transactionManager");

        // Assert
        assertThat(hasEntityManagerFactory).isTrue();
        assertThat(hasTransactionManager).isTrue();
    }

    /**
     * Tests that all configuration classes are loaded.
     * Verifies that:
     * - RabbitMQConfig is loaded
     * - GrpcServerConfig is loaded
     */
    @Test
    void testConfigurationClasses_Loaded_Successfully() {
        // Act
        RabbitMQConfig rabbitMQConfig = applicationContext.getBean(RabbitMQConfig.class);
        GrpcServerConfig grpcServerConfig = applicationContext.getBean(GrpcServerConfig.class);

        // Assert
        assertThat(rabbitMQConfig).isNotNull();
        assertThat(grpcServerConfig).isNotNull();
    }

    /**
     * Tests that the application has the correct number of beans.
     * Verifies that:
     * - Application context contains multiple beans
     * - Core beans are present
     */
    @Test
    void testApplicationContext_ContainsExpectedBeans() {
        // Act
        int beanCount = applicationContext.getBeanDefinitionCount();

        // Assert
        assertThat(beanCount).isGreaterThan(0);
        
        // Verify core beans exist
        assertThat(applicationContext.containsBean("fileUploadController")).isTrue();
        assertThat(applicationContext.containsBean("schedulerServiceImpl")).isTrue();
        assertThat(applicationContext.containsBean("createMetadataService")).isTrue();
        assertThat(applicationContext.containsBean("fileMetadataRepository")).isTrue();
    }
}
