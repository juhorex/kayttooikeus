package fi.vm.sade.kayttooikeus.util;

import fi.vm.sade.kayttooikeus.dto.YhteystietojenTyypit;
import fi.vm.sade.oppijanumerorekisteri.dto.HenkiloDto;
import fi.vm.sade.oppijanumerorekisteri.dto.HenkiloPerustietoDto;
import fi.vm.sade.oppijanumerorekisteri.dto.YhteystietoDto;
import fi.vm.sade.oppijanumerorekisteri.dto.YhteystietoTyyppi;
import org.apache.commons.lang.StringUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static java.util.Optional.ofNullable;

public class UserDetailsUtil {
    private static final String DEFAULT_LANGUAGE_CODE = "fi";

    public static String getCurrentUserOid() throws NullPointerException {
        String oid = SecurityContextHolder.getContext().getAuthentication().getName();
        if (oid == null) {
            throw new NullPointerException("No user name available from SecurityContext!");
        }
        return oid;
    }

    /**
     * Returns name for given {@link HenkiloDto}.
     *
     * @param henkilo
     * @return name
     */
    public static String getName(HenkiloDto henkilo) {
        return henkilo.getKutsumanimi() + " " + henkilo.getSukunimi();
    }

    /**
     * Returns language code for given {@link HenkiloDto} (defaults to
     * {@link #DEFAULT_LANGUAGE_CODE}).
     *
     * @param henkilo
     * @return language code
     */
    public static String getLanguageCode(HenkiloDto henkilo) {
        return henkilo.getAsiointiKieli() != null && henkilo.getAsiointiKieli().getKieliKoodi() != null
                ? henkilo.getAsiointiKieli().getKieliKoodi()
                : DEFAULT_LANGUAGE_CODE;
    }

    public static String getLanguageCode(HenkiloPerustietoDto henkilo) {
        return ofNullable(henkilo.getAsiointiKieli()).flatMap(k -> ofNullable(k.getKieliKoodi()))
                .orElse(DEFAULT_LANGUAGE_CODE);
    }


    /**
     * Emails are parsed and preferred using YhteystiedotComparator
     * 1. work email
     * 2. home email
     * 3. other email
     * 4. free time email
     * @param henkilo
     * @return First email by priority
     */
    public static Optional<String> getEmailByPriority(HenkiloDto henkilo) {
        YhteystiedotComparator yhteystiedotComparator = new YhteystiedotComparator();
        return henkilo.getYhteystiedotRyhma().stream()
                .sorted(yhteystiedotComparator)
                .flatMap(yhteystiedotRyhmaDto -> yhteystiedotRyhmaDto.getYhteystieto().stream())
                .filter(yhteystietoDto -> yhteystietoDto.getYhteystietoTyyppi().equals(YhteystietoTyyppi.YHTEYSTIETO_SAHKOPOSTI)
                        && !StringUtils.isEmpty(yhteystietoDto.getYhteystietoArvo()))

                .map(YhteystietoDto::getYhteystietoArvo).findFirst();
    }
}
