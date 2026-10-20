package tp.appsinter.cine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("Application with the full container stack")
class CineApplicationTests {

    @Test
    @DisplayName("starts")
    void contextLoads() {
    }

}
