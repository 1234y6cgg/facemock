package com.mockinterview.service.project;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;

@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named="P4_MYSQL_TEST_URL",matches=".+") @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectMysqlIntegrationTest extends ProjectFlowTest {
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->System.getenv("P4_MYSQL_TEST_URL"));r.add("spring.datasource.username",()->System.getenv().getOrDefault("P4_MYSQL_TEST_USER","root"));
        r.add("spring.datasource.password",()->System.getenv().getOrDefault("P4_MYSQL_TEST_PASSWORD",""));r.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");}
}
