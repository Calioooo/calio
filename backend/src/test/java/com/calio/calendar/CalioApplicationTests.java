package com.calio.calendar;

import com.calio.calendar.common.testsupport.SharedIntegrationDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-shared-integration-test;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@SharedIntegrationDatabase
class CalioApplicationTests {

    @Test
    void contextLoads() {
    }
}
