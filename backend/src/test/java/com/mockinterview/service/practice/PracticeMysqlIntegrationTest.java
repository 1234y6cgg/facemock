package com.mockinterview.service.practice;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;

@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named="P2_MYSQL_TEST_URL",matches=".+")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PracticeMysqlIntegrationTest extends PracticeFlowTest {
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->System.getenv("P2_MYSQL_TEST_URL"));
        registry.add("spring.datasource.username",()->System.getenv().getOrDefault("P2_MYSQL_TEST_USER","root"));
        registry.add("spring.datasource.password",()->System.getenv().getOrDefault("P2_MYSQL_TEST_PASSWORD",""));
        registry.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
    }
}
