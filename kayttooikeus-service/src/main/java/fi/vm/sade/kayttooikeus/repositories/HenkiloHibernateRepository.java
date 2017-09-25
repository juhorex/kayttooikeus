package fi.vm.sade.kayttooikeus.repositories;

import com.querydsl.core.types.OrderSpecifier;
import fi.vm.sade.kayttooikeus.model.QHenkilo;
import fi.vm.sade.kayttooikeus.model.QMyonnettyKayttoOikeusRyhmaTapahtuma;
import fi.vm.sade.kayttooikeus.model.QOrganisaatioHenkilo;
import fi.vm.sade.kayttooikeus.repositories.criteria.HenkiloCriteria;
import fi.vm.sade.kayttooikeus.repositories.dto.HenkilohakuResultDto;
import fi.vm.sade.kayttooikeus.model.Henkilo;
import fi.vm.sade.kayttooikeus.repositories.criteria.OrganisaatioHenkiloCriteria;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface HenkiloHibernateRepository extends BaseRepository<Henkilo> {

    /**
     * Palauttaa hakukriteerien mukaiset henkilöiden oid:t.
     *
     * @param criteria hakukriteerit
     * @return henkilö oid:t
     */
    Set<String> findOidsBy(OrganisaatioHenkiloCriteria criteria);

    /**
     * Palauttaa henkilöiden oid:t joilla on sama organisaatio kuin annetulla
     * henkilöllä.
     *
     * @param henkiloOid henkilö oid
     * @param criteria haun lisäehdot
     * @return henkilö oid:t
     */
    Set<String> findOidsBySamaOrganisaatio(String henkiloOid, OrganisaatioHenkiloCriteria criteria);

    List<HenkilohakuResultDto> findByCriteria(HenkiloCriteria.HenkiloCriteriaFunction<QHenkilo, QOrganisaatioHenkilo, QMyonnettyKayttoOikeusRyhmaTapahtuma> criteria, Long offset, List<String> organisaatioOidRestrictionList, List<OrderSpecifier> orderBy);

    /**
     * Palauttaa henkilöt jotka kuuluvat johonkin annettuun käyttöoikeusryhmään
     * ja organisaatioon.
     *
     * @param kayttoOikeusRyhmaIds käyttöoikeusryhmät
     * @param organisaatioOids organisaatiot
     * @return henkilöt
     */
    List<Henkilo> findByKayttoOikeusRyhmatAndOrganisaatiot(Set<Long> kayttoOikeusRyhmaIds, Set<String> organisaatioOids);

    /**
     * Palauttaa henkilöt joilla on myönnettynä annettu käyttöoikeusryhmä.
     *
     * @param kayttoOikeusRyhmaId käyttöoikeusryhmän id
     * @return henkilöt
     */
    Set<String> findOidsByKayttoOikeusRyhmaId(Long kayttoOikeusRyhmaId);

    /**
     * Palauttaa henkilöiden oid:t joilta löytyy käyttäjätunnus.
     *
     * @return henkilö oid:t
     */
    Set<String> findOidsByHavingUsername();
}
