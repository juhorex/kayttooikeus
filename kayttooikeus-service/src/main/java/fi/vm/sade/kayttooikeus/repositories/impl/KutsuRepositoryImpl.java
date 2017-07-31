package fi.vm.sade.kayttooikeus.repositories.impl;

import fi.vm.sade.kayttooikeus.dto.KutsuListDto;
import fi.vm.sade.kayttooikeus.dto.KutsuOrganisaatioListDto;
import fi.vm.sade.kayttooikeus.enumeration.KutsuOrganisaatioOrder;
import fi.vm.sade.kayttooikeus.model.Kutsu;
import fi.vm.sade.kayttooikeus.repositories.criteria.KutsuCriteria;
import fi.vm.sade.kayttooikeus.repositories.KutsuRepository;
import fi.vm.sade.kayttooikeus.repositories.OrderBy;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.querydsl.core.types.Projections.bean;
import static fi.vm.sade.kayttooikeus.model.QKutsu.kutsu;
import static fi.vm.sade.kayttooikeus.model.QKutsuOrganisaatio.kutsuOrganisaatio;

@Repository
public class KutsuRepositoryImpl extends BaseRepositoryImpl<Kutsu> implements KutsuRepository {
    @Override
    public List<KutsuListDto> listKutsuListDtos(KutsuCriteria criteria) {
        return jpa().from(kutsu).where(criteria.builder(kutsu))
                .select(bean(KutsuListDto.class,
                    kutsu.id.as("id"),
                    kutsu.tila.as("tila"),
                    kutsu.aikaleima.as("aikaleima"),
                    kutsu.sahkoposti.as("sahkoposti"),
                    kutsu.etunimi.as("etunimi"),
                    kutsu.sukunimi.as("sukunimi")
                )).orderBy(kutsu.aikaleima.desc()).fetch();
    }
}
