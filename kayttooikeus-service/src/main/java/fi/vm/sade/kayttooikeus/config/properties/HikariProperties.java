package fi.vm.sade.kayttooikeus.config.properties;

import com.zaxxer.hikari.HikariConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "hikari.datasource")
public class HikariProperties extends HikariConfig {
}
