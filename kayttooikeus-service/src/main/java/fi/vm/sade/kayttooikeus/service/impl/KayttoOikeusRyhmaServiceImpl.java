package fi.vm.sade.kayttooikeus.service.impl;

import fi.vm.sade.kayttooikeus.config.OrikaBeanMapper;
import fi.vm.sade.kayttooikeus.model.KayttoOikeusRyhma;
import fi.vm.sade.kayttooikeus.model.KayttoOikeusRyhmaTapahtumaHistoria;
import fi.vm.sade.kayttooikeus.model.MyonnettyKayttoOikeusRyhmaTapahtuma;
import fi.vm.sade.kayttooikeus.model.Palvelu;
import fi.vm.sade.kayttooikeus.repositories.*;
import fi.vm.sade.kayttooikeus.service.KayttoOikeusRyhmaService;
import fi.vm.sade.kayttooikeus.service.dto.KayttoOikeusRyhmaDto;
import fi.vm.sade.kayttooikeus.service.dto.MyonnettyKayttoOikeusDTO;
import fi.vm.sade.kayttooikeus.service.dto.PalveluRoooliDto;
import fi.vm.sade.kayttooikeus.util.AccessRightManagementUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class KayttoOikeusRyhmaServiceImpl extends AbstractService implements KayttoOikeusRyhmaService {
    private KayttoOikeusRyhmaRepository kayttoOikeusRyhmaRepository;
    private OrikaBeanMapper mapper;
    private AccessRightManagementUtils accessRightManagementUtils;
    private MyonnettyKayttoOikeusRyhmaTapahtumaRepository myonnettyKayttoOikeusRyhmaTapahtumaRepository;
    private KayttoOikeusRyhmaMyontoViiteRepository kayttoOikeusRyhmaMyontoViiteRepository;
    private KayttoOikeusRepository kayttoOikeusRepository;
    private PalveluRepository palveluRepository;
    private KayttoOikeusRyhmaTapahtumaHistoriaRepository kayttoOikeusRyhmaTapahtumaHistoriaRepository;

    @Autowired
    public KayttoOikeusRyhmaServiceImpl(KayttoOikeusRyhmaRepository kayttoOikeusRyhmaRepository, OrikaBeanMapper mapper,
                                        AccessRightManagementUtils accessRightManagementUtils,
                                        MyonnettyKayttoOikeusRyhmaTapahtumaRepository myonnettyKayttoOikeusRyhmaTapahtumaRepository,
                                        KayttoOikeusRyhmaMyontoViiteRepository kayttoOikeusRyhmaMyontoViiteRepository,
                                        KayttoOikeusRepository kayttoOikeusRepository,
                                        PalveluRepository palveluRepository,
                                        KayttoOikeusRyhmaTapahtumaHistoriaRepository kayttoOikeusRyhmaTapahtumaHistoriaRepository) {
        this.kayttoOikeusRyhmaRepository = kayttoOikeusRyhmaRepository;
        this.mapper = mapper;
        this.accessRightManagementUtils = accessRightManagementUtils;
        this.kayttoOikeusRyhmaMyontoViiteRepository = kayttoOikeusRyhmaMyontoViiteRepository;
        this.myonnettyKayttoOikeusRyhmaTapahtumaRepository = myonnettyKayttoOikeusRyhmaTapahtumaRepository;
        this.kayttoOikeusRepository = kayttoOikeusRepository;
        this.palveluRepository = palveluRepository;
        this.kayttoOikeusRyhmaTapahtumaHistoriaRepository = kayttoOikeusRyhmaTapahtumaHistoriaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<KayttoOikeusRyhmaDto> listAllKayttoOikeusRyhmas() {
        return mapper.mapAsList(kayttoOikeusRyhmaRepository.listAll(), KayttoOikeusRyhmaDto.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KayttoOikeusRyhmaDto> listPossibleRyhmasByOrganization(String organisaatioOid) {
        List<KayttoOikeusRyhma> allRyhmas = kayttoOikeusRyhmaRepository.listAll();
        accessRightManagementUtils.parseRyhmaLimitationsBasedOnOrgOid(organisaatioOid, allRyhmas);
        return mapper.mapAsList(kayttoOikeusRyhmaRepository.listAll(), KayttoOikeusRyhmaDto.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MyonnettyKayttoOikeusDTO> listMyonnettyKayttoOikeusRyhmasMergedWithHenkilos(String henkiloOid, String organisaatioOid, String myontajaOid) {
        List<KayttoOikeusRyhma> allRyhmas;
        /* The list of groups that can be granted must be checked
         * from the granting person's limitation list, if the granting
         * person has any limitations, if not then all groups are listed
         */

        List<Long> slaveIds = accessRightManagementUtils.getGrantableKayttooikeusRyhmas(myontajaOid);

        if (!CollectionUtils.isEmpty(slaveIds)) {
            allRyhmas = kayttoOikeusRyhmaRepository.findByIdList(slaveIds);
        } else {
            allRyhmas = kayttoOikeusRyhmaRepository.listAll();
        }

        /* If groups have limitations based on organization restrictions, those
         * groups must be removed from the list since it confuses the user as UI
         * can't know these limitations and the error message doesn't really help
         */
        accessRightManagementUtils.parseRyhmaLimitationsBasedOnOrgOid(organisaatioOid, allRyhmas);

        if (!allRyhmas.isEmpty()) {
            List<MyonnettyKayttoOikeusRyhmaTapahtuma> henkilosKORs = myonnettyKayttoOikeusRyhmaTapahtumaRepository.findByHenkiloInOrganisaatio(henkiloOid, organisaatioOid);
            return accessRightManagementUtils.createMyonnettyKayttoOikeusDTO(allRyhmas, henkilosKORs);
        }

        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MyonnettyKayttoOikeusDTO> listMyonnettyKayttoOikeusRyhmasByHenkiloAndOrganisaatio(String henkiloOid, String organisaatioOid) {

        List<MyonnettyKayttoOikeusDTO> all = myonnettyKayttoOikeusRyhmaTapahtumaRepository.findByHenkiloInOrganisaatio(henkiloOid, organisaatioOid)
                .stream()
                .map(myonnettyKayttoOikeusRyhmaTapahtuma -> mapper.map(myonnettyKayttoOikeusRyhmaTapahtuma, MyonnettyKayttoOikeusDTO.class))
                .collect(Collectors.toList());

        /* History data must be fetched also since it's additional information for admin users
         * if they need to solve possible conflicts with users' access rights
         */
        List<MyonnettyKayttoOikeusDTO> histories = kayttoOikeusRyhmaTapahtumaHistoriaRepository.findByHenkiloInOrganisaatio(henkiloOid, organisaatioOid)
                .stream()
                .map(kayttoOikeusRyhmaTapahtumaHistoria -> mapper.map(kayttoOikeusRyhmaTapahtumaHistoria, MyonnettyKayttoOikeusDTO.class))
                .collect(Collectors.toList());

        all.addAll(histories);
        return all;
    }

    @Override
    @Transactional(readOnly = true)
    public KayttoOikeusRyhmaDto findKayttoOikeusRyhma(Long id) {
        KayttoOikeusRyhma ryhma = kayttoOikeusRyhmaRepository.findById(id);
        return mapper.map(ryhma, KayttoOikeusRyhmaDto.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KayttoOikeusRyhmaDto> findSubRyhmasByMasterRyhma(Long id) {
        List<Long> slaveIds = kayttoOikeusRyhmaMyontoViiteRepository.getSlaveIdsByMasterIds(Collections.singletonList(id));
        if(slaveIds != null && !slaveIds.isEmpty()) {
            List<KayttoOikeusRyhma> results = kayttoOikeusRyhmaRepository.findByIdList(slaveIds);
            mapper.map(results, KayttoOikeusRyhmaDto.class);
        }
        return new ArrayList<>();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PalveluRoooliDto> findKayttoOikeusByKayttoOikeusRyhma(Long id) {
        List<Long> kos = kayttoOikeusRepository.findByKayttoOikeusRyhmaIds(id);
        List<Palvelu> palvelus = palveluRepository.findByKayttoOikeusIds(kos);
        return accessRightManagementUtils.createPalveluRooliDTO(palvelus);
    }
}
