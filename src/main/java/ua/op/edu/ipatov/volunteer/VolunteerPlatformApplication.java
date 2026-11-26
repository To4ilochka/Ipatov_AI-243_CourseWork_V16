package ua.op.edu.ipatov.volunteer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Точка входу вебсистеми координації волонтерських ініціатив VoluntHub.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class VolunteerPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(VolunteerPlatformApplication.class, args);
    }
}
