package fi.vm.sade.kayttooikeus.config.scheduling;

import fi.vm.sade.kayttooikeus.config.properties.CommonProperties;
import fi.vm.sade.kayttooikeus.repositories.HenkiloDataRepository;
import fi.vm.sade.kayttooikeus.service.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import fi.vm.sade.kayttooikeus.service.LdapSynchronizationService;

import java.time.LocalDate;
import java.time.Period;

/**
 * Ajastusten konfigurointi.
 *
 * @see SchedulingConfiguration ajastuksen aktivointi
 */
@Component
@RequiredArgsConstructor
public class ScheduledTasks {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduledTasks.class);

    private final OrganisaatioService organisaatioService;
    private final MyonnettyKayttoOikeusService myonnettyKayttoOikeusService;
    private final TaskExecutorService taskExecutorService;
    private final KayttooikeusAnomusService kayttooikeusAnomusService;
    private final LdapSynchronizationService ldapSynchronizationService;
    private final HenkiloCacheService henkiloCacheService;

    private final HenkiloDataRepository henkiloDataRepository;

    private final CommonProperties commonProperties;

    @Scheduled(cron = "${kayttooikeus.scheduling.configuration.organisaatiocache}")
    public void updateOrganisaatioCache() {
        organisaatioService.updateOrganisaatioCache();
    }

    @Scheduled(cron = "${kayttooikeus.scheduling.configuration.vanhentuneetkayttooikeudet}")
    public void poistaVanhentuneetKayttoOikeudet() {
        myonnettyKayttoOikeusService.poistaVanhentuneet(commonProperties.getAdminOid());
    }

    @Scheduled(cron = "${kayttooikeus.scheduling.configuration.kayttooikeusmuistutus}")
    public void sendExpirationReminders() {
        taskExecutorService.sendExpirationReminders(Period.ofWeeks(4), Period.ofWeeks(1));
    }

    @Scheduled(cron = "${kayttooikeus.scheduling.configuration.kayttooikeusanomusilmoitukset}")
    public void lahetaUusienAnomuksienIlmoitukset() {
        kayttooikeusAnomusService.lahetaUusienAnomuksienIlmoitukset(LocalDate.now().minusDays(1));
    }

    @Scheduled(fixedDelayString = "${kayttooikeus.scheduling.ldapsynkronointi.fixeddelayinmillis}",
            initialDelayString = "${kayttooikeus.scheduling.ldapsynkronointi.initialdelayinmillis}")
    public void ldapSynkronointi() {
        ldapSynchronizationService.runSynchronizer();
    }

    @Scheduled(fixedDelayString = "${kayttooikeus.scheduling.configuration.henkiloNimiCache}")
    public void updateHenkiloNimiCache() {
        if(this.henkiloDataRepository.countByEtunimetCachedNotNull() > 0L) {
            this.henkiloCacheService.updateHenkiloCache();
        }
        // Fetch whole henkilo nimi cache
        else {
            this.henkiloCacheService.forceCleanUpdateHenkiloCache();
        }
    }

}
