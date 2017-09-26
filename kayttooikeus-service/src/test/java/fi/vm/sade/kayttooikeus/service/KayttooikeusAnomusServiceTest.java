package fi.vm.sade.kayttooikeus.service;

import com.google.common.collect.Lists;
import fi.vm.sade.kayttooikeus.config.OrikaBeanMapper;
import fi.vm.sade.kayttooikeus.config.mapper.CachedDateTimeConverter;
import fi.vm.sade.kayttooikeus.config.mapper.LocalDateConverter;
import fi.vm.sade.kayttooikeus.config.properties.CommonProperties;
import fi.vm.sade.kayttooikeus.dto.*;
import fi.vm.sade.kayttooikeus.dto.types.AnomusTyyppi;
import fi.vm.sade.kayttooikeus.model.*;
import fi.vm.sade.kayttooikeus.repositories.*;
import fi.vm.sade.kayttooikeus.repositories.criteria.AnomusCriteria;
import fi.vm.sade.kayttooikeus.service.exception.ForbiddenException;
import fi.vm.sade.kayttooikeus.service.external.OrganisaatioClient;
import fi.vm.sade.kayttooikeus.service.impl.KayttooikeusAnomusServiceImpl;
import fi.vm.sade.kayttooikeus.service.validators.HaettuKayttooikeusryhmaValidator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static fi.vm.sade.kayttooikeus.dto.KayttoOikeudenTila.SULJETTU;
import static fi.vm.sade.kayttooikeus.util.CreateUtil.*;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {OrikaBeanMapper.class, LocalDateConverter.class, CachedDateTimeConverter.class, KayttooikeusAnomusServiceImpl.class})
public class KayttooikeusAnomusServiceTest {
    @Autowired
    private OrikaBeanMapper orikaBeanMapper;

    @MockBean
    private HaettuKayttooikeusRyhmaRepository haettuKayttooikeusRyhmaRepository;

    @MockBean
    private LocalizationService localizationService;

    @MockBean
    private HenkiloDataRepository henkiloDataRepository;

    @MockBean
    private HenkiloHibernateRepository henkiloHibernateRepository;

    @MockBean
    private MyonnettyKayttoOikeusRyhmaTapahtumaDataRepository myonnettyKayttoOikeusRyhmaTapahtumaDataRepository;

    @MockBean
    private KayttoOikeusRyhmaMyontoViiteRepository kayttoOikeusRyhmaMyontoViiteRepository;

    @MockBean
    private KayttoOikeusRyhmaTapahtumaHistoriaDataRepository kayttoOikeusRyhmaTapahtumaHistoriaDataRepository;

    @MockBean
    private HaettuKayttooikeusryhmaValidator haettuKayttooikeusryhmaValidator;

    @MockBean
    private PermissionCheckerService permissionCheckerService;

    @MockBean
    private KayttooikeusryhmaDataRepository kayttooikeusryhmaDataRepository;

    @MockBean
    private OrganisaatioClient organisaatioClient;

    @MockBean
    private AnomusRepository anomusRepository;

    @MockBean
    private EmailService emailService;

    @MockBean
    private LdapSynchronizationService ldapSynchronizationService;

    @MockBean
    private OrganisaatioHenkiloDataRepository organisaatioHenkiloDataRepository;

    @MockBean
    private OrganisaatioHenkiloRepository organisaatioHenkiloRepository;

    @MockBean
    private OrganisaatioService organisaatioService;

    @Captor
    private ArgumentCaptor<Set<String>> henkiloOidsCaptor;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @SpyBean
    private KayttooikeusAnomusService kayttooikeusAnomusService;

    @SpyBean
    private CommonProperties commonProperties;

    @Before
    public void setup() {
        doAnswer(returnsFirstArg()).when(this.localizationService).localize(any(LocalizableDto.class));
        this.commonProperties.setRootOrganizationOid("rootOid");
        doAnswer(returnsFirstArg()).when(this.organisaatioHenkiloDataRepository).save(any(OrganisaatioHenkilo.class));
        doAnswer(returnsFirstArg()).when(this.myonnettyKayttoOikeusRyhmaTapahtumaDataRepository).save(any(MyonnettyKayttoOikeusRyhmaTapahtuma.class));
        doAnswer(returnsFirstArg()).when(this.henkiloDataRepository).save(any(Henkilo.class));
    }


    @Test
    public void listHaetutKayttoOikeusRyhmat() {
        given(this.haettuKayttooikeusRyhmaRepository.findBy(any(), anyLong(), anyLong(), anyObject(), anyBoolean()))
                .willReturn(newArrayList(createHaettuKayttooikeusryhma("xmail", "kayttooikeusryhma1", "1.2.12.0.1")));

        AnomusCriteria criteria = AnomusCriteria.builder().anojaOid("1.2.3.4.5").build();
        List<HaettuKayttooikeusryhmaDto> haettuKayttooikeusryhmaDtoList = this.kayttooikeusAnomusService
                .listHaetutKayttoOikeusRyhmat(criteria, null, null, null);
        assertThat(haettuKayttooikeusryhmaDtoList.size()).isEqualTo(1);
        assertThat(haettuKayttooikeusryhmaDtoList.get(0).getKasittelyPvm()).isLessThanOrEqualTo(LocalDateTime.now());
        assertThat(haettuKayttooikeusryhmaDtoList.get(0).getTyyppi()).isEqualByComparingTo(KayttoOikeudenTila.ANOTTU);
        assertThat(haettuKayttooikeusryhmaDtoList.get(0).getKayttoOikeusRyhma().getName()).isEqualTo("kayttooikeusryhma1");
        assertThat(haettuKayttooikeusryhmaDtoList.get(0).getAnomus().getOrganisaatioOid()).isEqualTo("1.2.12.0.1");
        assertThat(haettuKayttooikeusryhmaDtoList.get(0).getAnomus().getAnomusTyyppi()).isEqualByComparingTo(AnomusTyyppi.UUSI);

        verify(this.haettuKayttooikeusRyhmaRepository).findBy(any(), eq(null), eq(null), eq(null), eq(null));
        verify(this.localizationService, atLeastOnce()).localize(any(LocalizableDto.class));
    }

    @Test
    public void listHaetutKayttoOikeusRyhmatForAdmin() {
        Set<String> organisaatioOids = Stream.of("1.2.3.4.5").collect(toSet());

        given(this.permissionCheckerService.isCurrentUserAdmin()).willReturn(true);

        AnomusCriteria criteria = AnomusCriteria.builder().organisaatioOids(organisaatioOids).build();
        this.kayttooikeusAnomusService.listHaetutKayttoOikeusRyhmat(criteria, null, null, null);

        assertThat(criteria.getOrganisaatioOids()).containsOnlyElementsOf(organisaatioOids);
    }

    @Test
    public void listHaetutKayttoOikeusRyhmatForOphVirkailija() {
        List<String> userOrganisaatioOids = Arrays.asList("1.2.3.4.5", "rootOid");
        List<Long> kayttooikeusRyhmas = Arrays.asList(12345L, 23456L, 34567L);

        given(this.permissionCheckerService.isCurrentUserAdmin()).willReturn(false);
        given(this.organisaatioHenkiloRepository.findDistinctOrganisaatiosForHenkiloOid(any())).willReturn(userOrganisaatioOids);
        given(this.kayttoOikeusRyhmaMyontoViiteRepository.getSlaveIdsByMasterHenkiloOid(any())).willReturn(kayttooikeusRyhmas);

        AnomusCriteria criteria = AnomusCriteria.builder().build();
        this.kayttooikeusAnomusService.listHaetutKayttoOikeusRyhmat(criteria, null, null, null);

        assertThat(criteria.getOrganisaatioOids()).isNull();
        assertThat(criteria.getKayttooikeusRyhmaIds()).containsOnlyElementsOf(kayttooikeusRyhmas);
    }

    @Test
    public void listHaetutKayttoOikeusRyhmatForVirkailijaWithNoCriteriaOrganisaatios() {
        List<String> userOrganisaatioOids = Arrays.asList("1.2.3.4.5", "2.3.4.5.6");
        List<Long> kayttooikeusRyhmas = Arrays.asList(12345L, 23456L, 34567L);

        given(this.permissionCheckerService.isCurrentUserAdmin()).willReturn(false);
        given(this.organisaatioHenkiloRepository.findDistinctOrganisaatiosForHenkiloOid(any())).willReturn(userOrganisaatioOids);
        given(this.kayttoOikeusRyhmaMyontoViiteRepository.getSlaveIdsByMasterHenkiloOid(any())).willReturn(kayttooikeusRyhmas);

        AnomusCriteria criteria = AnomusCriteria.builder().build();
        this.kayttooikeusAnomusService.listHaetutKayttoOikeusRyhmat(criteria, null, null, null);

        assertThat(criteria.getKayttooikeusRyhmaIds()).containsOnlyElementsOf(kayttooikeusRyhmas);
        assertThat(criteria.getOrganisaatioOids()).containsOnlyElementsOf(userOrganisaatioOids);
    }

    @Test
    public void listHaetutKayttoOikeusRyhmatForVirkailijaWithShowOwnAnomus() {
        List<String> userOrganisaatioOids = Arrays.asList("1.2.3.4.5", "2.3.4.5.6");
        List<Long> kayttooikeusRyhmas = Arrays.asList(12345L, 23456L, 34567L);

        given(this.permissionCheckerService.isCurrentUserAdmin()).willReturn(false);
        given(this.organisaatioHenkiloRepository.findDistinctOrganisaatiosForHenkiloOid(any())).willReturn(userOrganisaatioOids);
        given(this.kayttoOikeusRyhmaMyontoViiteRepository.getSlaveIdsByMasterHenkiloOid(any())).willReturn(kayttooikeusRyhmas);

        AnomusCriteria criteria = AnomusCriteria.builder().build();
        this.kayttooikeusAnomusService.listHaetutKayttoOikeusRyhmat(criteria, null, null, null);

        assertThat(criteria.getKayttooikeusRyhmaIds()).containsOnlyElementsOf(kayttooikeusRyhmas);
        assertThat(criteria.getOrganisaatioOids()).containsOnlyElementsOf(userOrganisaatioOids);
    }

    @Test
    public void listHaetutKayttoOikeusRyhmatForVirkailijaWithCriteriaOrganisaatios() {
        List<String> userOrganisaatioOids = Arrays.asList("1.2.3.4.5", "2.3.4.5.6");
        List<String> userOrganisaatioChildOids = Arrays.asList("1.2.3", "2.3.4");
        List<Long> kayttooikeusRyhmas = Arrays.asList(12345L, 23456L, 34567L);
        Set<String> criteriaOrganisaatioOids = Stream.of("2.3.4.5.6", "2.3.4").collect(toSet());

        given(this.permissionCheckerService.isCurrentUserAdmin()).willReturn(false);
        given(this.organisaatioHenkiloRepository.findDistinctOrganisaatiosForHenkiloOid(any())).willReturn(userOrganisaatioOids);
        given(this.organisaatioClient.getActiveChildOids(any())).willReturn(userOrganisaatioChildOids);
        given(this.kayttoOikeusRyhmaMyontoViiteRepository.getSlaveIdsByMasterHenkiloOid(any())).willReturn(kayttooikeusRyhmas);

        AnomusCriteria criteria = AnomusCriteria.builder().organisaatioOids(criteriaOrganisaatioOids).build();
        this.kayttooikeusAnomusService.listHaetutKayttoOikeusRyhmat(criteria, null, null, null);

        assertThat(criteria.getKayttooikeusRyhmaIds()).containsOnlyElementsOf(kayttooikeusRyhmas);
        assertThat(criteria.getOrganisaatioOids()).containsExactlyInAnyOrder("2.3.4", "2.3.4.5.6");
    }



    @Test
    @WithMockUser("1.2.3.4.1")
    public void grantKayttooikeusryhmaForDisabledOrganisaatioHenkilo() {
        given(this.permissionCheckerService.notOwnData(anyString())).willReturn(true);
        given(this.permissionCheckerService.checkRoleForOrganisation(any(), any())).willReturn(true);
        given(this.kayttooikeusryhmaDataRepository.findById(2001L)).willReturn(Optional.of(createKayttoOikeusRyhmaWithViite(2001L)));
        given(this.henkiloDataRepository.findByOidHenkilo("1.2.3.4.5")).willReturn(Optional.of(createHenkilo("1.2.3.4.5")));
        given(this.henkiloDataRepository.findByOidHenkilo("1.2.3.4.1"))
                .willReturn(Optional.of(createHenkiloWithOrganisaatio("1.2.3.4.5", "1.2.0.0.1", true)));
        given(this.permissionCheckerService.getCurrentUserOid()).willReturn("1.2.3.4.1");
        given(this.permissionCheckerService.organisaatioLimitationCheck(eq("1.2.0.0.1"), anySetOf(OrganisaatioViite.class))).willReturn(true);
        given(this.organisaatioHenkiloDataRepository.findByHenkiloOidHenkilo("1.2.3.4.1"))
                .willReturn(Lists.newArrayList(OrganisaatioHenkilo.builder().organisaatioOid("1.2.0.0.1").build()));
        given(this.permissionCheckerService.kayttooikeusMyontoviiteLimitationCheck(2001L)).willReturn(true);
        given(this.myonnettyKayttoOikeusRyhmaTapahtumaDataRepository.findMyonnettyTapahtuma(2001L,
                "1.2.0.0.1", "1.2.3.4.5"))
                .willReturn(Optional.empty());

        GrantKayttooikeusryhmaDto grantKayttooikeusryhmaDto = createGrantKayttooikeusryhmaDto(2001L,
                LocalDate.now().plusYears(1));
        // Service call
        this.kayttooikeusAnomusService.grantKayttooikeusryhma("1.2.3.4.5", "1.2.0.0.1",
                Lists.newArrayList(grantKayttooikeusryhmaDto));

        ArgumentCaptor<MyonnettyKayttoOikeusRyhmaTapahtuma> myonnettyKayttoOikeusRyhmaTapahtumaArgumentCaptor =
                ArgumentCaptor.forClass(MyonnettyKayttoOikeusRyhmaTapahtuma.class);
        verify(this.myonnettyKayttoOikeusRyhmaTapahtumaDataRepository, times(1))
                .save(myonnettyKayttoOikeusRyhmaTapahtumaArgumentCaptor.capture());
        MyonnettyKayttoOikeusRyhmaTapahtuma myonnettyKayttoOikeusRyhmaTapahtuma = myonnettyKayttoOikeusRyhmaTapahtumaArgumentCaptor.getValue();
        ArgumentCaptor<KayttoOikeusRyhmaTapahtumaHistoria> kayttoOikeusRyhmaTapahtumaHistoriaArgumentCaptor =
                ArgumentCaptor.forClass(KayttoOikeusRyhmaTapahtumaHistoria.class);
        verify(this.kayttoOikeusRyhmaTapahtumaHistoriaDataRepository, times(1))
                .save(kayttoOikeusRyhmaTapahtumaHistoriaArgumentCaptor.capture());
        KayttoOikeusRyhmaTapahtumaHistoria kayttoOikeusRyhmaTapahtumaHistoria = kayttoOikeusRyhmaTapahtumaHistoriaArgumentCaptor.getValue();
        assertThat(kayttoOikeusRyhmaTapahtumaHistoria.getTila())
                .isEqualByComparingTo(myonnettyKayttoOikeusRyhmaTapahtuma.getTila())
                .isEqualByComparingTo(KayttoOikeudenTila.MYONNETTY);
        assertThat(kayttoOikeusRyhmaTapahtumaHistoria.getOrganisaatioHenkilo().getOrganisaatioOid())
                .isEqualTo(myonnettyKayttoOikeusRyhmaTapahtuma.getOrganisaatioHenkilo().getOrganisaatioOid())
                .isEqualTo("1.2.0.0.1");
        assertThat(kayttoOikeusRyhmaTapahtumaHistoria.getOrganisaatioHenkilo().isPassivoitu()).isFalse();
        assertThat(kayttoOikeusRyhmaTapahtumaHistoria.getKayttoOikeusRyhma().getId())
                .isEqualTo(myonnettyKayttoOikeusRyhmaTapahtuma.getKayttoOikeusRyhma().getId())
                .isEqualTo(2001L);
        assertThat(kayttoOikeusRyhmaTapahtumaHistoria.getKayttoOikeusRyhma().getName())
                .isEqualTo(myonnettyKayttoOikeusRyhmaTapahtuma.getKayttoOikeusRyhma().getName())
                .isEqualTo("Kayttooikeusryhma x");
        assertThat(kayttoOikeusRyhmaTapahtumaHistoria.getKayttoOikeusRyhma().getRooliRajoite())
                .isEqualTo(myonnettyKayttoOikeusRyhmaTapahtuma.getKayttoOikeusRyhma().getRooliRajoite())
                .isEqualTo("10");

        assertThat(myonnettyKayttoOikeusRyhmaTapahtuma.getAnomus()).isNotNull();

        assertThat(kayttoOikeusRyhmaTapahtumaHistoria.getSyy()).isEqualTo("Oikeuksien lisäys");

    }

    @Test
    @WithMockUser("1.2.3.4.1")
    public void grantKayttooikeusryhmaWithoutActiveOrganisations() {
        this.expectedException.expect(ForbiddenException.class);
        this.expectedException.expectMessage("Target organization has invalid organization type");

        given(this.permissionCheckerService.notOwnData(anyString())).willReturn(true);
        given(this.permissionCheckerService.checkRoleForOrganisation(any(), any())).willReturn(true);
        given(this.kayttooikeusryhmaDataRepository.findById(2001L)).willReturn(Optional.of(createKayttoOikeusRyhmaWithViite(2001L)));
        given(this.henkiloDataRepository.findByOidHenkilo("1.2.3.4.5")).willReturn(Optional.of(createHenkilo("1.2.3.4.5")));
        given(this.permissionCheckerService.getCurrentUserOid()).willReturn("1.2.3.4.1");
        given(this.organisaatioHenkiloDataRepository.findByHenkiloOidHenkilo("1.2.3.4.1"))
                .willReturn(Lists.newArrayList(OrganisaatioHenkilo.builder().organisaatioOid("1.2.0.0.1").passivoitu(true).build()));
        given(this.permissionCheckerService.kayttooikeusMyontoviiteLimitationCheck(2001L)).willReturn(true);
        given(this.myonnettyKayttoOikeusRyhmaTapahtumaDataRepository.findMyonnettyTapahtuma(2001L,
                "1.2.0.0.1", "1.2.3.4.5"))
                .willReturn(Optional.empty());
        // Passivoitu organisation
        given(this.henkiloDataRepository.findByOidHenkilo("1.2.3.4.1"))
                .willReturn(Optional.of(createHenkiloWithOrganisaatio("1.2.3.4.5", "1.2.0.0.1", true)));

        GrantKayttooikeusryhmaDto grantKayttooikeusryhmaDto = createGrantKayttooikeusryhmaDto(2001L,
                LocalDate.now().plusYears(1));
        this.kayttooikeusAnomusService.grantKayttooikeusryhma("1.2.3.4.5", "1.2.0.0.1",
                Lists.newArrayList(grantKayttooikeusryhmaDto));
    }

    // MyonnettyKayttooikeusryhmaTapahtuma already exists
    @Test
    @WithMockUser("1.2.3.4.1")
    public void grantKayttooikeusryhmaUusittu() {
        given(this.permissionCheckerService.notOwnData(anyString())).willReturn(true);
        given(this.permissionCheckerService.checkRoleForOrganisation(any(), any())).willReturn(true);
        given(this.kayttooikeusryhmaDataRepository.findById(2001L)).willReturn(Optional.of(createKayttoOikeusRyhmaWithViite(2001L)));
        given(this.henkiloDataRepository.findByOidHenkilo("1.2.3.4.5")).willReturn(Optional.of(createHenkilo("1.2.3.4.5")));
        given(this.henkiloDataRepository.findByOidHenkilo("1.2.3.4.1")).willReturn(Optional.of(createHenkilo("1.2.3.4.5")));
        given(this.permissionCheckerService.getCurrentUserOid()).willReturn("1.2.3.4.1");
        given(this.permissionCheckerService.organisaatioLimitationCheck(eq("1.2.0.0.1"), anySetOf(OrganisaatioViite.class))).willReturn(true);
        given(this.organisaatioHenkiloDataRepository.findByHenkiloOidHenkilo("1.2.3.4.1"))
                .willReturn(Lists.newArrayList(OrganisaatioHenkilo.builder().organisaatioOid("1.2.0.0.1").build()));
        given(this.permissionCheckerService.kayttooikeusMyontoviiteLimitationCheck(2001L)).willReturn(true);
        MyonnettyKayttoOikeusRyhmaTapahtuma myonnettyKayttoOikeusRyhmaTapahtuma = createMyonnettyKayttoOikeusRyhmaTapahtuma(3001L, 2001L);
        given(this.myonnettyKayttoOikeusRyhmaTapahtumaDataRepository.findMyonnettyTapahtuma(2001L,
                "1.2.0.0.1", "1.2.3.4.5"))
                .willReturn(Optional.of(myonnettyKayttoOikeusRyhmaTapahtuma));

        GrantKayttooikeusryhmaDto grantKayttooikeusryhmaDto = createGrantKayttooikeusryhmaDto(2001L,
                LocalDate.now().plusYears(1));
        this.kayttooikeusAnomusService.grantKayttooikeusryhma("1.2.3.4.5", "1.2.0.0.1",
                Lists.newArrayList(grantKayttooikeusryhmaDto));

        ArgumentCaptor<MyonnettyKayttoOikeusRyhmaTapahtuma> myonnettyKayttoOikeusRyhmaTapahtumaArgumentCaptor =
                ArgumentCaptor.forClass(MyonnettyKayttoOikeusRyhmaTapahtuma.class);
        verify(this.myonnettyKayttoOikeusRyhmaTapahtumaDataRepository, times(0))
                .save(myonnettyKayttoOikeusRyhmaTapahtumaArgumentCaptor.capture());
        ArgumentCaptor<KayttoOikeusRyhmaTapahtumaHistoria> kayttoOikeusRyhmaTapahtumaHistoriaArgumentCaptor =
                ArgumentCaptor.forClass(KayttoOikeusRyhmaTapahtumaHistoria.class);
        verify(this.kayttoOikeusRyhmaTapahtumaHistoriaDataRepository, times(1))
                .save(kayttoOikeusRyhmaTapahtumaHistoriaArgumentCaptor.capture());
        KayttoOikeusRyhmaTapahtumaHistoria kayttoOikeusRyhmaTapahtumaHistoria = kayttoOikeusRyhmaTapahtumaHistoriaArgumentCaptor.getValue();
        assertThat(myonnettyKayttoOikeusRyhmaTapahtuma.getTila())
                .isEqualByComparingTo(kayttoOikeusRyhmaTapahtumaHistoria.getTila())
                .isEqualByComparingTo(KayttoOikeudenTila.UUSITTU);
        assertThat(myonnettyKayttoOikeusRyhmaTapahtuma.getAnomus()).isNotNull();

        assertThat(kayttoOikeusRyhmaTapahtumaHistoria.getSyy()).isEqualTo("Oikeuksien päivitys");
    }

    // Grant kayttooikeus internally as unauthorized user.
    @Test
    public void grantKayttooikeusryhmaAsAdmin() {
        given(this.permissionCheckerService.notOwnData(anyString())).willReturn(true);
        given(this.permissionCheckerService.checkRoleForOrganisation(any(), any())).willReturn(true);
        given(this.kayttooikeusryhmaDataRepository.findById(2001L)).willReturn(Optional.of(createKayttoOikeusRyhmaWithViite(2001L)));
        given(this.henkiloDataRepository.findByOidHenkilo("1.2.3.4.5")).willReturn(Optional.of(createHenkilo("1.2.3.4.5")));
        given(this.henkiloDataRepository.findByOidHenkilo("1.2.3.4.1")).willReturn(Optional.of(createHenkilo("1.2.3.4.5")));
        given(this.permissionCheckerService.getCurrentUserOid()).willReturn("1.2.3.4.1");
        given(this.permissionCheckerService.organisaatioLimitationCheck(eq("1.2.0.0.1"), anySetOf(OrganisaatioViite.class))).willReturn(true);
        given(this.organisaatioHenkiloDataRepository.findByHenkiloOidHenkilo("1.2.3.4.1"))
                .willReturn(Lists.newArrayList(OrganisaatioHenkilo.builder().organisaatioOid("1.2.0.0.1").build()));
        given(this.permissionCheckerService.kayttooikeusMyontoviiteLimitationCheck(2001L)).willReturn(true);
        MyonnettyKayttoOikeusRyhmaTapahtuma myonnettyKayttoOikeusRyhmaTapahtuma = createMyonnettyKayttoOikeusRyhmaTapahtuma(3001L, 2001L);
        given(this.myonnettyKayttoOikeusRyhmaTapahtumaDataRepository.findMyonnettyTapahtuma(2001L,
                "1.2.0.0.1", "1.2.3.4.5"))
                .willReturn(Optional.of(myonnettyKayttoOikeusRyhmaTapahtuma));

        KayttoOikeusRyhma grantKayttooikeusryhma = createKayttooikeusryhma(2001L);
        this.kayttooikeusAnomusService.grantKayttooikeusryhmaAsAdminWithoutPermissionCheck("1.2.3.4.5", "1.2.0.0.1",
                Lists.newArrayList(grantKayttooikeusryhma));

        ArgumentCaptor<MyonnettyKayttoOikeusRyhmaTapahtuma> myonnettyKayttoOikeusRyhmaTapahtumaArgumentCaptor =
                ArgumentCaptor.forClass(MyonnettyKayttoOikeusRyhmaTapahtuma.class);
        verify(this.myonnettyKayttoOikeusRyhmaTapahtumaDataRepository, times(0))
                .save(myonnettyKayttoOikeusRyhmaTapahtumaArgumentCaptor.capture());
        ArgumentCaptor<KayttoOikeusRyhmaTapahtumaHistoria> kayttoOikeusRyhmaTapahtumaHistoriaArgumentCaptor =
                ArgumentCaptor.forClass(KayttoOikeusRyhmaTapahtumaHistoria.class);
        verify(this.kayttoOikeusRyhmaTapahtumaHistoriaDataRepository, times(1))
                .save(kayttoOikeusRyhmaTapahtumaHistoriaArgumentCaptor.capture());
        KayttoOikeusRyhmaTapahtumaHistoria kayttoOikeusRyhmaTapahtumaHistoria = kayttoOikeusRyhmaTapahtumaHistoriaArgumentCaptor.getValue();
        assertThat(myonnettyKayttoOikeusRyhmaTapahtuma.getTila())
                .isEqualByComparingTo(kayttoOikeusRyhmaTapahtumaHistoria.getTila())
                .isEqualByComparingTo(KayttoOikeudenTila.UUSITTU);
        assertThat(myonnettyKayttoOikeusRyhmaTapahtuma.getAnomus()).isNotNull();

        assertThat(kayttoOikeusRyhmaTapahtumaHistoria.getSyy()).isEqualTo("Oikeuksien päivitys");
    }


    @Test
    @WithMockUser("1.2.3.4.1")
    public void updateHaettuKayttooikeusryhmaMyonnetty() {
        HaettuKayttoOikeusRyhma haettuKayttoOikeusRyhma = createHaettuKayttoOikeusRyhma("1.2.3.4.5", "1.2.3.4.1",
                "1.2.0.0.1", "devaaja", "Haluan devata", 2001L);
        // this has it's own test
        doNothing().when(this.kayttooikeusAnomusService).grantKayttooikeusryhma(any(), anyString(), anyListOf(GrantKayttooikeusryhmaDto.class));
        given(this.haettuKayttooikeusRyhmaRepository.findById(1L))
                .willReturn(Optional.of(haettuKayttoOikeusRyhma));
        given(this.kayttooikeusryhmaDataRepository.findById(2001L)).willReturn(Optional.of(haettuKayttoOikeusRyhma.getKayttoOikeusRyhma()));
        given(this.permissionCheckerService.checkRoleForOrganisation(anyListOf(String.class), anyListOf(String.class)))
                .willReturn(true);
        given(this.permissionCheckerService.getCurrentUserOid()).willReturn("1.2.3.4.1");
        given(this.permissionCheckerService.organisaatioLimitationCheck(eq("1.2.0.0.1"), anySetOf(OrganisaatioViite.class))).willReturn(true);
        given(this.organisaatioHenkiloDataRepository.findByHenkiloOidHenkilo("1.2.3.4.1"))
                .willReturn(Lists.newArrayList(OrganisaatioHenkilo.builder().organisaatioOid("1.2.0.0.1").build()));
        given(this.permissionCheckerService.kayttooikeusMyontoviiteLimitationCheck(2001L)).willReturn(true);
        given(this.permissionCheckerService.notOwnData("1.2.3.4.5")).willReturn(true);
        given(this.henkiloDataRepository.findByOidHenkilo("1.2.3.4.5")).willReturn(Optional.of(createHenkilo("1.2.3.4.5")));
        given(this.henkiloDataRepository.findByOidHenkilo("1.2.3.4.1")).willReturn(Optional.of(createHenkilo("1.2.3.4.1")));
        given(this.myonnettyKayttoOikeusRyhmaTapahtumaDataRepository.findMyonnettyTapahtuma(2001L,
                "1.2.0.0.1", "1.2.3.4.5"))
                .willReturn(Optional.of(createMyonnettyKayttoOikeusRyhmaTapahtuma(3001L, 2001L)));

        UpdateHaettuKayttooikeusryhmaDto updateHaettuKayttooikeusryhmaDto = createUpdateHaettuKayttooikeusryhmaDto(1L,
                "MYONNETTY", LocalDate.now().plusYears(1));
        this.kayttooikeusAnomusService.updateHaettuKayttooikeusryhma(updateHaettuKayttooikeusryhmaDto);

        assertThat(haettuKayttoOikeusRyhma.getAnomus().getAnomuksenTila()).isEqualByComparingTo(AnomuksenTila.KASITELTY);
        assertThat(haettuKayttoOikeusRyhma.getAnomus().getAnomusTyyppi()).isEqualByComparingTo(AnomusTyyppi.UUSI);
        // New MyonnettyKayttooikeusRyhma has been added to the Anomus
        assertThat(haettuKayttoOikeusRyhma.getAnomus().getMyonnettyKayttooikeusRyhmas()).hasSize(1)
                .extracting("id").containsExactly(3001L);
    }

    @Test
    @WithMockUser("1.2.3.4.1")
    public void updateHaettuKayttooikeusryhmaHylatty() {
        HaettuKayttoOikeusRyhma haettuKayttoOikeusRyhma = createHaettuKayttoOikeusRyhma("1.2.3.4.5", "1.2.3.4.1",
                "1.2.0.0.1", "devaaja", "Haluan devata", 2001L);
        // this has it's own test
        doNothing().when(this.kayttooikeusAnomusService).grantKayttooikeusryhma(any(), anyString(), anyListOf(GrantKayttooikeusryhmaDto.class));
        given(this.haettuKayttooikeusRyhmaRepository.findById(1L))
                .willReturn(Optional.of(haettuKayttoOikeusRyhma));
        given(this.kayttooikeusryhmaDataRepository.findById(2001L)).willReturn(Optional.of(haettuKayttoOikeusRyhma.getKayttoOikeusRyhma()));
        given(this.permissionCheckerService.checkRoleForOrganisation(anyListOf(String.class), anyListOf(String.class)))
                .willReturn(true);
        given(this.permissionCheckerService.getCurrentUserOid()).willReturn("1.2.3.4.1");
        given(this.permissionCheckerService.organisaatioLimitationCheck(eq("1.2.0.0.1"), anySetOf(OrganisaatioViite.class))).willReturn(true);
        given(this.organisaatioHenkiloDataRepository.findByHenkiloOidHenkilo("1.2.3.4.1"))
                .willReturn(Lists.newArrayList(OrganisaatioHenkilo.builder().organisaatioOid("1.2.0.0.1").build()));
        given(this.permissionCheckerService.kayttooikeusMyontoviiteLimitationCheck(2001L)).willReturn(true);
        given(this.permissionCheckerService.notOwnData("1.2.3.4.5")).willReturn(true);
        given(this.henkiloDataRepository.findByOidHenkilo("1.2.3.4.5")).willReturn(Optional.of(createHenkilo("1.2.3.4.5")));
        given(this.henkiloDataRepository.findByOidHenkilo("1.2.3.4.1")).willReturn(Optional.of(createHenkilo("1.2.3.4.1")));

        UpdateHaettuKayttooikeusryhmaDto updateHaettuKayttooikeusryhmaDto = createUpdateHaettuKayttooikeusryhmaDto(1L,
                "HYLATTY", LocalDate.now().plusYears(1));
        this.kayttooikeusAnomusService.updateHaettuKayttooikeusryhma(updateHaettuKayttooikeusryhmaDto);

        assertThat(haettuKayttoOikeusRyhma.getAnomus().getAnomuksenTila()).isEqualByComparingTo(AnomuksenTila.HYLATTY);
        assertThat(haettuKayttoOikeusRyhma.getAnomus().getAnomusTyyppi()).isEqualByComparingTo(AnomusTyyppi.UUSI);
    }

    @Test
    @WithMockUser("1.2.3.4.1")
    public void updateHaettuKayttooikeusryhmaHylkaaOneFromAnomus() {
        HaettuKayttoOikeusRyhma haettuKayttoOikeusRyhma = createHaettuKayttoOikeusRyhma("1.2.3.4.5", "1.2.3.4.1",
                "1.2.0.0.1", "devaaja", "Haluan devata", 2001L);
        // Second haettu kayttooikeusryhma so anomus will not be finalized
        HaettuKayttoOikeusRyhma anotherHaettuKayttoOikeusRyhma = createHaettuKayttoOikeusRyhma("1.2.3.4.5", "1.2.3.4.1",
                "1.2.0.0.1", "devaaja", "Haluan devata", 2002L);
        haettuKayttoOikeusRyhma.getAnomus().addHaettuKayttoOikeusRyhma(anotherHaettuKayttoOikeusRyhma);
        // this has it's own test
        doNothing().when(this.kayttooikeusAnomusService).grantKayttooikeusryhma(any(), anyString(), anyListOf(GrantKayttooikeusryhmaDto.class));
        given(this.haettuKayttooikeusRyhmaRepository.findById(1L))
                .willReturn(Optional.of(haettuKayttoOikeusRyhma));
        given(this.kayttooikeusryhmaDataRepository.findById(2001L)).willReturn(Optional.of(haettuKayttoOikeusRyhma.getKayttoOikeusRyhma()));
        given(this.permissionCheckerService.checkRoleForOrganisation(anyListOf(String.class), anyListOf(String.class)))
                .willReturn(true);
        given(this.permissionCheckerService.getCurrentUserOid()).willReturn("1.2.3.4.1");
        given(this.permissionCheckerService.organisaatioLimitationCheck(eq("1.2.0.0.1"), anySetOf(OrganisaatioViite.class))).willReturn(true);
        given(this.organisaatioHenkiloDataRepository.findByHenkiloOidHenkilo("1.2.3.4.1"))
                .willReturn(Lists.newArrayList(OrganisaatioHenkilo.builder().organisaatioOid("1.2.0.0.1").build()));
        given(this.permissionCheckerService.kayttooikeusMyontoviiteLimitationCheck(2001L)).willReturn(true);
        given(this.permissionCheckerService.notOwnData("1.2.3.4.5")).willReturn(true);
        given(this.henkiloDataRepository.findByOidHenkilo("1.2.3.4.5")).willReturn(Optional.of(createHenkilo("1.2.3.4.5")));
        given(this.henkiloDataRepository.findByOidHenkilo("1.2.3.4.1")).willReturn(Optional.of(createHenkilo("1.2.3.4.1")));

        UpdateHaettuKayttooikeusryhmaDto updateHaettuKayttooikeusryhmaDto = createUpdateHaettuKayttooikeusryhmaDto(1L,
                "HYLATTY", LocalDate.now().plusYears(1));
        this.kayttooikeusAnomusService.updateHaettuKayttooikeusryhma(updateHaettuKayttooikeusryhmaDto);

        assertThat(haettuKayttoOikeusRyhma.getAnomus().getAnomuksenTila()).isEqualByComparingTo(AnomuksenTila.ANOTTU);
        assertThat(haettuKayttoOikeusRyhma.getAnomus().getAnomusTyyppi()).isEqualByComparingTo(AnomusTyyppi.UUSI);
    }

    @Test
    public void lahetaUusienAnomuksienIlmoituksetEiJuuriOrganisaatioAnomukselle() {
        Anomus anomus = Anomus.builder()
                .henkilo(Henkilo.builder().oidHenkilo("user1").build())
                .organisaatioOid("organisaatio1")
                .haettuKayttoOikeusRyhmas(Stream.of(
                        HaettuKayttoOikeusRyhma.builder().kayttoOikeusRyhma(KayttoOikeusRyhma.builder().build()).build(),
                        HaettuKayttoOikeusRyhma.builder().kayttoOikeusRyhma(KayttoOikeusRyhma.builder().build()).build()
                ).collect(toSet()))
                .build();
        when(anomusRepository.findBy(any()))
                .thenReturn(Collections.singletonList(anomus));
        when(kayttoOikeusRyhmaMyontoViiteRepository.getMasterIdsBySlaveIds(any()))
                .thenReturn(Stream.of(1L, 2L).collect(toSet()));
        when(henkiloHibernateRepository.findByKayttoOikeusRyhmatAndOrganisaatiot(any(), any()))
                .thenReturn(Arrays.asList(
                        Henkilo.builder().oidHenkilo("user2").build(),
                        Henkilo.builder().oidHenkilo("user3").build()
                ));
        when(organisaatioClient.getParentOids(any())).thenReturn(Arrays.asList("rootOid", "organisaatio1"));

        kayttooikeusAnomusService.lahetaUusienAnomuksienIlmoitukset(LocalDate.now());

        verify(organisaatioClient).getParentOids(eq("organisaatio1"));
        verify(henkiloHibernateRepository).findByKayttoOikeusRyhmatAndOrganisaatiot(
                eq(Stream.of(1L, 2L).collect(toSet())), eq(singleton("organisaatio1"))
        );
        verify(emailService).sendNewRequisitionNotificationEmails(henkiloOidsCaptor.capture());
        Set<String> henkilot = henkiloOidsCaptor.getValue();
        assertThat(henkilot).containsExactlyInAnyOrder("user2", "user3");
    }

    @Test
    public void lahetaUusienAnomuksienIlmoituksetJuuriOrganisaatioAnomukselle() {
        Anomus anomus = Anomus.builder()
                .henkilo(Henkilo.builder().oidHenkilo("user1").build())
                .organisaatioOid("rootOid")
                .haettuKayttoOikeusRyhmas(Stream.of(
                        HaettuKayttoOikeusRyhma.builder().kayttoOikeusRyhma(KayttoOikeusRyhma.builder().build()).build(),
                        HaettuKayttoOikeusRyhma.builder().kayttoOikeusRyhma(KayttoOikeusRyhma.builder().build()).build()
                ).collect(toSet()))
                .build();
        when(anomusRepository.findBy(any()))
                .thenReturn(Collections.singletonList(anomus));
        when(kayttoOikeusRyhmaMyontoViiteRepository.getMasterIdsBySlaveIds(any()))
                .thenReturn(Stream.of(1L, 2L).collect(toSet()));
        when(henkiloHibernateRepository.findByKayttoOikeusRyhmatAndOrganisaatiot(any(), any()))
                .thenReturn(Arrays.asList(
                        Henkilo.builder().oidHenkilo("user2").build(),
                        Henkilo.builder().oidHenkilo("user3").build()
                ));

        kayttooikeusAnomusService.lahetaUusienAnomuksienIlmoitukset(LocalDate.now());

        verifyZeroInteractions(organisaatioClient);
        verify(henkiloHibernateRepository).findByKayttoOikeusRyhmatAndOrganisaatiot(
                eq(Stream.of(1L, 2L).collect(toSet())), eq(singleton("rootOid"))
        );
        verify(emailService).sendNewRequisitionNotificationEmails(henkiloOidsCaptor.capture());
        Set<String> henkilot = henkiloOidsCaptor.getValue();
        assertThat(henkilot).containsExactlyInAnyOrder("user2", "user3");
    }

    @Test
    @WithMockUser("1.2.3.4.1")
    public void removePrivilege() {
        // Pass permission check
        given(this.kayttooikeusryhmaDataRepository.findById(2001L)).willReturn(Optional.of(createKayttoOikeusRyhmaWithViite(2001L)));
        given(this.permissionCheckerService.checkRoleForOrganisation(anyListOf(String.class), anyListOf(String.class)))
                .willReturn(true);
        given(this.permissionCheckerService.getCurrentUserOid()).willReturn("1.2.3.4.1");
        given(this.permissionCheckerService.organisaatioLimitationCheck(eq("1.2.0.0.1"), anySetOf(OrganisaatioViite.class))).willReturn(true);
        given(this.organisaatioHenkiloDataRepository.findByHenkiloOidHenkilo("1.2.3.4.1"))
                .willReturn(Lists.newArrayList(OrganisaatioHenkilo.builder().organisaatioOid("1.2.0.0.1").build()));
        given(this.permissionCheckerService.kayttooikeusMyontoviiteLimitationCheck(2001L)).willReturn(true);
        given(this.permissionCheckerService.notOwnData("1.2.3.4.5")).willReturn(true);
        // Actual mocks
        given(this.henkiloDataRepository.findByOidHenkilo("1.2.3.4.1")).willReturn(Optional.of(new Henkilo()));
        given(this.myonnettyKayttoOikeusRyhmaTapahtumaDataRepository.findMyonnettyTapahtuma(2001L,
                "1.2.0.0.1", "1.2.3.4.5"))
                .willReturn(Optional.of(createMyonnettyKayttoOikeusRyhmaTapahtuma(3001L, 2001L)));
        // Service call
        this.kayttooikeusAnomusService.removePrivilege("1.2.3.4.5", 2001L, "1.2.0.0.1");
        // Capture
        ArgumentCaptor<KayttoOikeusRyhmaTapahtumaHistoria> myonnettyKayttoOikeusRyhmaTapahtumaArgumentCaptor =
                ArgumentCaptor.forClass(KayttoOikeusRyhmaTapahtumaHistoria.class);
        verify(this.kayttoOikeusRyhmaTapahtumaHistoriaDataRepository, times(1))
                .save(myonnettyKayttoOikeusRyhmaTapahtumaArgumentCaptor.capture());
        KayttoOikeusRyhmaTapahtumaHistoria kayttoOikeusRyhmaTapahtumaHistoria = myonnettyKayttoOikeusRyhmaTapahtumaArgumentCaptor.getValue();
        verify(this.myonnettyKayttoOikeusRyhmaTapahtumaDataRepository, times(1))
                .delete(any(MyonnettyKayttoOikeusRyhmaTapahtuma.class));

        assertThat(kayttoOikeusRyhmaTapahtumaHistoria.getTila()).isEqualTo(SULJETTU);
        assertThat(kayttoOikeusRyhmaTapahtumaHistoria.getSyy()).isEqualTo("Käyttöoikeuden sulkeminen");
        assertThat(kayttoOikeusRyhmaTapahtumaHistoria.getAikaleima()).isNotNull();
    }

    @Test
    @WithMockUser("1.2.3.4.5")
    public void findCurrentHenkiloCanGrantNoRoles() {
        Map<String, Set<Long>> currentHenkiloCanGrant = this.kayttooikeusAnomusService.findCurrentHenkiloCanGrant("1.2.3.4.6");
        assertThat(currentHenkiloCanGrant).isEmpty();
    }

    @Test
    @WithMockUser(value = "1.2.3.4.5", roles = "HENKILONHALLINTA_OPHREKISTERI")
    public void findCurrentHenkiloCanGrantAsAdmin() {
        this.commonProperties.setRootOrganizationOid("1.2.0.0.1");
        given(this.permissionCheckerService.getCurrentUserOid()).willReturn("1.2.3.4.5");
        given(this.myonnettyKayttoOikeusRyhmaTapahtumaDataRepository
                .findCrudAnomustenhallinta("1.2.3.4.5"))
                .willReturn(Lists.newArrayList(
                        createMyonnettyKayttoOikeusRyhmaTapahtumaWithOrganisation(1001L, 2001L, "1.2.0.0.1")));

        given(this.anomusRepository.findByHenkiloOidHenkilo("1.2.3.4.6"))
                .willReturn(Lists.newArrayList(createAnomusWithHaettuKayttooikeusryhma("1.2.3.4.6",
                        "1.2.3.4.7", "1.2.0.0.1", "tehtava", "perustelu", 2001L)));
        given(this.myonnettyKayttoOikeusRyhmaTapahtumaDataRepository.findByOrganisaatioHenkiloHenkiloOidHenkilo("1.2.3.4.6"))
                .willReturn(Lists.newArrayList(createMyonnettyKayttoOikeusRyhmaTapahtumaWithOrganisation(1001L, 2002L, "1.2.0.0.2")));
        given(this.kayttoOikeusRyhmaTapahtumaHistoriaDataRepository.findByOrganisaatioHenkiloHenkiloOidHenkiloAndTila("1.2.3.4.6", KayttoOikeudenTila.SULJETTU))
                .willReturn(Lists.newArrayList(createKayttooikeusryhmaTapahtumaHistoria(2003L, "1.2.0.0.3", KayttoOikeudenTila.SULJETTU)));

        Map<String, Set<Long>> currentHenkiloCanGrant = this.kayttooikeusAnomusService.findCurrentHenkiloCanGrant("1.2.3.4.6");
        assertThat(currentHenkiloCanGrant.size()).isEqualTo(3);
        assertThat(currentHenkiloCanGrant.get("1.2.0.0.1")).containsExactly(2001L);
        assertThat(currentHenkiloCanGrant.get("1.2.0.0.2")).containsExactly(2002L);
        assertThat(currentHenkiloCanGrant.get("1.2.0.0.3")).containsExactly(2003L);
    }

    @Test
    @WithMockUser("1.2.3.4.5")
    public void findCurrentHenkiloCanGrantWithNegativeOrganisaatioviite() {
        given(this.anomusRepository.findByHenkiloOidHenkilo("1.2.3.4.6"))
                .willReturn(Lists.newArrayList(createAnomusWithHaettuKayttooikeusryhma("1.2.3.4.6",
                        "1.2.3.4.7", "1.2.0.0.1", "tehtava", "perustelu", 2001L)));
        given(this.myonnettyKayttoOikeusRyhmaTapahtumaDataRepository.findByOrganisaatioHenkiloHenkiloOidHenkilo("1.2.3.4.6"))
                .willReturn(Lists.newArrayList(createMyonnettyKayttoOikeusRyhmaTapahtumaWithOrganisation(1001L, 2002L, "1.2.0.0.2")));
        given(this.kayttoOikeusRyhmaTapahtumaHistoriaDataRepository.findByOrganisaatioHenkiloHenkiloOidHenkiloAndTila("1.2.3.4.6", KayttoOikeudenTila.SULJETTU))
                .willReturn(Lists.newArrayList(createKayttooikeusryhmaTapahtumaHistoria(2003L, "1.2.0.0.3", KayttoOikeudenTila.SULJETTU)));

        // organisaatioviite mock
        given(this.kayttooikeusryhmaDataRepository.findById(2001L)).willReturn(Optional.of(createKayttoOikeusRyhmaWithViite(2001L)));

        Map<String, Set<Long>> kayttooikeusHenkiloCanGrantDto = this.kayttooikeusAnomusService.findCurrentHenkiloCanGrant("1.2.3.4.6");
        assertThat(kayttooikeusHenkiloCanGrantDto).isEmpty();
    }

    @Test
    @WithMockUser("1.2.3.4.5")
    public void findCurrentHenkiloCanGrantNormalUser() {
        given(this.permissionCheckerService.getCurrentUserOid()).willReturn("1.2.3.4.5");
        given(this.myonnettyKayttoOikeusRyhmaTapahtumaDataRepository
                .findCrudAnomustenhallinta("1.2.3.4.5"))
                .willReturn(Lists.newArrayList(
                        createMyonnettyKayttoOikeusRyhmaTapahtumaWithOrganisation(1001L, 2001L, "1.2.0.0.1")));

        given(this.anomusRepository.findByHenkiloOidHenkilo("1.2.3.4.6"))
                .willReturn(Lists.newArrayList(createAnomusWithHaettuKayttooikeusryhma("1.2.3.4.6",
                        "1.2.3.4.7", "1.2.0.0.1", "tehtava", "perustelu", 2001L)));
        given(this.myonnettyKayttoOikeusRyhmaTapahtumaDataRepository.findByOrganisaatioHenkiloHenkiloOidHenkilo("1.2.3.4.6"))
                .willReturn(Lists.newArrayList(createMyonnettyKayttoOikeusRyhmaTapahtumaWithOrganisation(1001L, 2002L, "1.2.0.0.2")));
        given(this.kayttoOikeusRyhmaTapahtumaHistoriaDataRepository.findByOrganisaatioHenkiloHenkiloOidHenkiloAndTila("1.2.3.4.6", KayttoOikeudenTila.SULJETTU))
                .willReturn(Lists.newArrayList(createKayttooikeusryhmaTapahtumaHistoria(2003L, "1.2.0.0.3", KayttoOikeudenTila.SULJETTU)));

        // organisaatioviite mock
        KayttoOikeusRyhma kayttoOikeusRyhma = createKayttoOikeusRyhmaWithViite(2001L);
        given(this.kayttooikeusryhmaDataRepository.findById(2001L)).willReturn(Optional.of(kayttoOikeusRyhma));
        given(this.organisaatioHenkiloDataRepository.findByHenkiloOidHenkilo("1.2.3.4.5"))
                .willReturn(Lists.newArrayList(createOrganisaatioHenkilo("1.2.0.0.1", false)));
        given(this.permissionCheckerService.organisaatioLimitationCheck("1.2.0.0.1", kayttoOikeusRyhma.getOrganisaatioViite()))
                .willReturn(true);

        Map<String, Set<Long>> currentHenkiloCanGrant = this.kayttooikeusAnomusService.findCurrentHenkiloCanGrant("1.2.3.4.6");
        assertThat(currentHenkiloCanGrant).containsOnlyKeys("1.2.0.0.1");
        assertThat(currentHenkiloCanGrant.get("1.2.0.0.1")).containsExactly(2001L);
    }

    @Test
    @WithMockUser("1.2.3.4.5")
    public void findCurrentHenkiloCanGrantRootOrganisationUser() {
        this.commonProperties.setRootOrganizationOid("1.2.0.0.1");
        given(this.permissionCheckerService.getCurrentUserOid()).willReturn("1.2.3.4.5");
        given(this.myonnettyKayttoOikeusRyhmaTapahtumaDataRepository
                .findCrudAnomustenhallinta("1.2.3.4.5"))
                .willReturn(Lists.newArrayList(
                        createMyonnettyKayttoOikeusRyhmaTapahtumaWithOrganisation(1001L, 2001L, "1.2.0.0.1")));

        given(this.anomusRepository.findByHenkiloOidHenkilo("1.2.3.4.6"))
                .willReturn(Lists.newArrayList(createAnomusWithHaettuKayttooikeusryhma("1.2.3.4.6",
                        "1.2.3.4.7", "1.2.0.0.1", "tehtava", "perustelu", 2001L)));
        given(this.myonnettyKayttoOikeusRyhmaTapahtumaDataRepository.findByOrganisaatioHenkiloHenkiloOidHenkilo("1.2.3.4.6"))
                .willReturn(Lists.newArrayList(createMyonnettyKayttoOikeusRyhmaTapahtumaWithOrganisation(1001L, 2002L, "1.2.0.0.2")));
        given(this.kayttoOikeusRyhmaTapahtumaHistoriaDataRepository.findByOrganisaatioHenkiloHenkiloOidHenkiloAndTila("1.2.3.4.6", KayttoOikeudenTila.SULJETTU))
                .willReturn(Lists.newArrayList(createKayttooikeusryhmaTapahtumaHistoria(2003L, "1.2.0.0.3", KayttoOikeudenTila.SULJETTU)));

        // organisaatioviite mock
        KayttoOikeusRyhma kayttooikeusryhmaWithoutViite = createKayttooikeusryhma(2001L);
        KayttoOikeusRyhma kayttoOikeusRyhma = createKayttoOikeusRyhmaWithViite(2002L);
        given(this.kayttooikeusryhmaDataRepository.findById(2001L)).willReturn(Optional.of(kayttooikeusryhmaWithoutViite));
        given(this.kayttooikeusryhmaDataRepository.findById(2002L)).willReturn(Optional.of(kayttoOikeusRyhma));
        given(this.kayttooikeusryhmaDataRepository.findById(2003L)).willReturn(Optional.of(kayttoOikeusRyhma));
        given(this.organisaatioHenkiloDataRepository.findByHenkiloOidHenkilo("1.2.3.4.5"))
                .willReturn(Lists.newArrayList(createOrganisaatioHenkilo("1.2.0.0.1", false),
                        createOrganisaatioHenkilo("1.2.0.0.2", false), createOrganisaatioHenkilo("1.2.0.0.3", false)));
        given(this.permissionCheckerService.organisaatioLimitationCheck("1.2.0.0.2", kayttoOikeusRyhma.getOrganisaatioViite()))
                .willReturn(true);
        given(this.permissionCheckerService.organisaatioLimitationCheck("1.2.0.0.3", kayttoOikeusRyhma.getOrganisaatioViite()))
                .willReturn(false);

        Map<String, Set<Long>> currentHenkiloCanGrant = this.kayttooikeusAnomusService.findCurrentHenkiloCanGrant("1.2.3.4.6");
        assertThat(currentHenkiloCanGrant).containsOnlyKeys("1.2.0.0.1", "1.2.0.0.2", "1.2.0.0.3");
        assertThat(currentHenkiloCanGrant.get("1.2.0.0.1")).containsExactly(2001L);
        assertThat(currentHenkiloCanGrant.get("1.2.0.0.2")).containsExactly(2002L);
        assertThat(currentHenkiloCanGrant.get("1.2.0.0.3")).containsExactly(2003L);
    }

}
