package fi.vm.sade.kayttooikeus.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "cas")
public class CasProperties {

    private String service;
    private Boolean sendRenew;
    private String key;
    private MfaAuth mfa = new MfaAuth();

    @Getter @Setter
    public static class MfaAuth {
        private String username;
        private String password;
    }
}
