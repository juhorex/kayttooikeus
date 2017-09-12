package fi.vm.sade.kayttooikeus.service.impl;

import com.google.common.collect.Sets;
import fi.vm.sade.kayttooikeus.config.OrikaBeanMapper;
import fi.vm.sade.kayttooikeus.config.properties.CommonProperties;
import fi.vm.sade.kayttooikeus.dto.*;
import fi.vm.sade.kayttooikeus.enumeration.KutsuOrganisaatioOrder;
import fi.vm.sade.kayttooikeus.model.Henkilo;
import fi.vm.sade.kayttooikeus.model.Kutsu;
import fi.vm.sade.kayttooikeus.repositories.HenkiloDataRepository;
import fi.vm.sade.kayttooikeus.repositories.KutsuRepository;
import fi.vm.sade.kayttooikeus.repositories.criteria.KutsuCriteria;
import fi.vm.sade.kayttooikeus.repositories.dto.HenkiloCreateByKutsuDto;
import fi.vm.sade.kayttooikeus.service.*;
import fi.vm.sade.kayttooikeus.service.exception.NotFoundException;
import fi.vm.sade.kayttooikeus.service.external.OppijanumerorekisteriClient;
import fi.vm.sade.kayttooikeus.service.validators.KutsuValidator;
import fi.vm.sade.oppijanumerorekisteri.dto.*;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static fi.vm.sade.kayttooikeus.dto.KutsunTila.AVOIN;

@Service
@RequiredArgsConstructor
public class KutsuServiceImpl extends AbstractService implements KutsuService {
    private final KutsuRepository kutsuRepository;
    private final HenkiloDataRepository henkiloDataRepository;

    private final OrikaBeanMapper mapper;
    private final KutsuValidator validator;

    private final EmailService emailService;
    private final LocalizationService localizationService;
    private final CryptoService cryptoService;
    private final KayttajatiedotService kayttajatiedotService;
    private final KayttooikeusAnomusService kayttooikeusAnomusService;
    private final IdentificationService identificationService;

    private final OppijanumerorekisteriClient oppijanumerorekisteriClient;

    private final CommonProperties commonProperties;

    @Override
    @Transactional(readOnly = true)
    public List<KutsuReadDto> listKutsus(KutsuOrganisaatioOrder sortBy,
                                         Sort.Direction direction,
                                         KutsuCriteria kutsuListCriteria,
                                         Long offset,
                                         Long amount) {
        List<KutsuReadDto> kutsuReadDtoList = this.mapper.mapAsList(this.kutsuRepository.listKutsuListDtos(kutsuListCriteria,
                sortBy.getSortWithDirection(direction), offset, amount), KutsuReadDto.class);
        kutsuReadDtoList.forEach(kutsuReadDto -> this.localizationService.localizeOrgs(kutsuReadDto.getOrganisaatiot()));

        return kutsuReadDtoList;
    }

    @Override
    @Transactional
    public long createKutsu(KutsuCreateDto dto) {
         if (!kutsuRepository.listKutsuListDtos(new KutsuCriteria().withTila(AVOIN).withSahkoposti(dto.getSahkoposti()),
                 KutsuOrganisaatioOrder.AIKALEIMA.getSortWithDirection()).isEmpty()) {
             throw new IllegalArgumentException("kutsu_with_sahkoposti_already_sent");
         }

        final Kutsu newKutsu = mapper.map(dto, Kutsu.class);

        newKutsu.setId(null);
        newKutsu.setAikaleima(LocalDateTime.now());
        newKutsu.setKutsuja(getCurrentUserOid());
        newKutsu.setSalaisuus(UUID.randomUUID().toString());
        newKutsu.setTila(AVOIN);
        newKutsu.getOrganisaatiot().forEach(kutsuOrganisaatio -> kutsuOrganisaatio.setKutsu(newKutsu));

        validator.validate(newKutsu);

        Kutsu persistedNewKutsu = this.kutsuRepository.save(newKutsu);
        
        this.emailService.sendInvitationEmail(persistedNewKutsu);

        return persistedNewKutsu.getId();
    }


    @Override
    @Transactional(readOnly = true)
    public KutsuReadDto getKutsu(Long id) {
        Kutsu kutsu = kutsuRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Kutsu not found"));
        KutsuReadDto kutsuReadDto = mapper.map(kutsu, KutsuReadDto.class);
        this.localizationService.localizeOrgs(kutsuReadDto.getOrganisaatiot());
        return kutsuReadDto;
    }

    @Override
    @Transactional
    public Kutsu deleteKutsu(long id) {
        Kutsu deletedKutsu = kutsuRepository.findById(id).filter(kutsu -> kutsu.getKutsuja().equals(getCurrentUserOid()))
            .orElseThrow(() -> new NotFoundException("Kutsu not found"));
        deletedKutsu.poista(getCurrentUserOid());
        return deletedKutsu;
    }

    @Override
    @Transactional(readOnly = true)
    public KutsuReadDto getByTemporaryToken(String temporaryToken) {
        Kutsu kutsuByToken = this.kutsuRepository.findByTemporaryTokenIsValidIsActive(temporaryToken)
                .orElseThrow(() -> new NotFoundException("Could not find kutsu by token " + temporaryToken + " or token is invalid"));
        KutsuReadDto kutsuReadDto = this.mapper.map(kutsuByToken, KutsuReadDto.class);
        this.localizationService.localizeOrgs(kutsuReadDto.getOrganisaatiot());
        return kutsuReadDto;
    }

    @Override
    @Transactional
    public String createHenkilo(String temporaryToken, HenkiloCreateByKutsuDto henkiloCreateByKutsuDto) {
        Kutsu kutsuByToken = this.kutsuRepository.findByTemporaryTokenIsValidIsActive(temporaryToken)
                .orElseThrow(() -> new NotFoundException("Could not find kutsu by token " + temporaryToken + " or token is invalid"));
        if(StringUtils.isEmpty(kutsuByToken.getHakaIdentifier())) {
            // Validation
            this.cryptoService.throwIfNotStrongPassword(henkiloCreateByKutsuDto.getPassword());
            this.kayttajatiedotService.throwIfUsernameExists(henkiloCreateByKutsuDto.getKayttajanimi());
            this.kayttajatiedotService.throwIfUsernameIsNotValid(henkiloCreateByKutsuDto.getKayttajanimi());
        }

        // Create henkilo
        String createdHenkiloOid = this.oppijanumerorekisteriClient
                .createHenkilo(getHenkiloCreateDto(henkiloCreateByKutsuDto, kutsuByToken));

        // Create credentials
        createCredentials(henkiloCreateByKutsuDto, kutsuByToken, createdHenkiloOid);

        // Add privileges
        kutsuByToken.getOrganisaatiot().forEach(kutsuOrganisaatio ->
                this.kayttooikeusAnomusService.grantKayttooikeusryhmaAsAdminWithoutPermissionCheck(
                        createdHenkiloOid,
                        kutsuOrganisaatio.getOrganisaatioOid(),
                        kutsuOrganisaatio.getRyhmat(),
                        kutsuByToken.getKutsuja()));

        // Set henkilo strongly identified
        Henkilo henkilo = this.henkiloDataRepository.findByOidHenkilo(createdHenkiloOid)
                .orElseGet(() -> this.henkiloDataRepository.save(new Henkilo()));
        henkilo.setVahvastiTunnistettu(true);

        // Update kutsu
        kutsuByToken.setKaytetty(LocalDateTime.now());
        kutsuByToken.setTemporaryToken(null);
        kutsuByToken.setLuotuHenkiloOid(createdHenkiloOid);
        kutsuByToken.setTila(KutsunTila.KAYTETTY);

        return identificationService.updateIdentificationAndGenerateTokenForHenkiloByHetu(kutsuByToken.getHetu());
    }

    private void createCredentials(HenkiloCreateByKutsuDto henkiloCreateByKutsuDto, Kutsu kutsuByToken, String createdHenkiloOid) {
        // Create username/password and haka identifier if provided
        if(StringUtils.hasLength(kutsuByToken.getHakaIdentifier())) {
            // If haka identifier is provided add it to henkilo identifiers
            this.identificationService.updateHakatunnuksetByHenkiloAndIdp(createdHenkiloOid,
                    Sets.newHashSet(kutsuByToken.getHakaIdentifier()));
            createHakaUsername(henkiloCreateByKutsuDto, kutsuByToken);
        }
        this.kayttajatiedotService.create(
                createdHenkiloOid,
                new KayttajatiedotCreateDto(henkiloCreateByKutsuDto.getKayttajanimi()),
                LdapSynchronizationService.LdapSynchronizationType.ASAP);
        if(StringUtils.isEmpty(kutsuByToken.getHakaIdentifier())) {
            this.kayttajatiedotService.changePasswordAsAdmin(createdHenkiloOid, henkiloCreateByKutsuDto.getPassword());
        }
    }

    private void createHakaUsername(HenkiloCreateByKutsuDto henkiloCreateByKutsuDto, Kutsu kutsuByToken) {
        String parsedIdentifier = kutsuByToken.getHakaIdentifier().replaceAll("[^A-Za-z0-9]", "");
        String username = parsedIdentifier + (new Random().nextInt(900) + 100); // 100-999
        // This username that is not meant to be used for CAS authentication
        // CAS does not accept empty password authentication so this is fine without password
        henkiloCreateByKutsuDto.setKayttajanimi(username);
    }

    @NotNull
    private HenkiloCreateDto getHenkiloCreateDto(HenkiloCreateByKutsuDto henkiloCreateByKutsuDto, Kutsu kutsuByToken) {
        HenkiloCreateDto henkiloCreateDto = new HenkiloCreateDto();
        henkiloCreateDto.setHetu(kutsuByToken.getHetu());
        henkiloCreateDto.setAsiointiKieli(henkiloCreateByKutsuDto.getAsiointiKieli());
        henkiloCreateDto.setEtunimet(kutsuByToken.getEtunimi());
        henkiloCreateDto.setSukunimi(kutsuByToken.getSukunimi());
        henkiloCreateDto.setKutsumanimi(henkiloCreateByKutsuDto.getKutsumanimi());
        henkiloCreateDto.setHenkiloTyyppi(HenkiloTyyppi.VIRKAILIJA);
        henkiloCreateDto.setYhteystiedotRyhma(Sets.newHashSet(YhteystiedotRyhmaDto.builder()
                .ryhmaAlkuperaTieto(this.commonProperties.getYhteystiedotRyhmaAlkuperaVirkailijaUi())
                .ryhmaKuvaus(this.commonProperties.getYhteystiedotRyhmaKuvausTyoosoite())
                .yhteystieto(YhteystietoDto.builder()
                        .yhteystietoArvo(kutsuByToken.getSahkoposti())
                        .yhteystietoTyyppi(YhteystietoTyyppi.YHTEYSTIETO_SAHKOPOSTI)
                        .build()).build()));
        return henkiloCreateDto;
    }

    @Override
    @Transactional
    // Nulls are not mapped for KutsuUpdateDto -> Kutsu
    public void updateHakaIdentifierToKutsu(String temporaryToken, KutsuUpdateDto kutsuUpdateDto) {
        Kutsu kutsu = this.kutsuRepository.findByTemporaryTokenIsValidIsActive(temporaryToken)
                .orElseThrow(() -> new NotFoundException("Could not find kutsu by token " + temporaryToken + " or token is invalid"));
        this.mapper.map(kutsuUpdateDto, kutsu);
    }
}
