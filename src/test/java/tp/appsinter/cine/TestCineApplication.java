package tp.appsinter.cine;

import org.springframework.boot.SpringApplication;

public class TestCineApplication {

    static void main(String[] args) {
        SpringApplication.from(CineApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
