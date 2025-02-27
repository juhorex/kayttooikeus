package fi.vm.sade.kayttooikeus.service.it;

import fi.vm.sade.kayttooikeus.dto.KayttajaTyyppi;
import fi.vm.sade.kayttooikeus.dto.KayttajatiedotCreateDto;
import fi.vm.sade.kayttooikeus.dto.KayttajatiedotReadDto;
import fi.vm.sade.kayttooikeus.dto.KayttajatiedotUpdateDto;
import fi.vm.sade.kayttooikeus.model.Kayttajatiedot;
import fi.vm.sade.kayttooikeus.repositories.KayttajatiedotRepository;
import fi.vm.sade.kayttooikeus.service.HenkiloService;
import fi.vm.sade.kayttooikeus.service.KayttajatiedotService;
import fi.vm.sade.kayttooikeus.service.exception.NotFoundException;
import fi.vm.sade.kayttooikeus.service.exception.UnauthorizedException;
import fi.vm.sade.kayttooikeus.service.exception.ValidationException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Optional;

import static fi.vm.sade.kayttooikeus.repositories.populate.HenkiloPopulator.henkilo;
import static fi.vm.sade.kayttooikeus.repositories.populate.KayttajatiedotPopulator.kayttajatiedot;
import static org.assertj.core.api.Assertions.*;

@RunWith(SpringRunner.class)
public class KayttajatiedotServiceTest extends AbstractServiceIntegrationTest {

    private static final String TEST_PASSWORD = "This_is_example_of_strong_password";

    @Autowired
    private KayttajatiedotService kayttajatiedotService;

    @Autowired
    private HenkiloService henkiloService;

    @Autowired
    private KayttajatiedotRepository kayttajatiedotRepository;

    @Test
    @WithMockUser(username = "user1")
    public void createShouldReturn() {
        String oid = "1.2.3.4.5";
        KayttajatiedotCreateDto createDto = new KayttajatiedotCreateDto();
        createDto.setUsername("user1");

        KayttajatiedotReadDto readDto = kayttajatiedotService.create(oid, createDto);

        assertThat(readDto).isNotNull();
    }

    @Test
    public void updateShouldNotThrowIfUsernameNotChanged() {
        String oid = "1.2.3.4.5";
        populate(kayttajatiedot(henkilo(oid), "user1"));
        KayttajatiedotUpdateDto updateDto = new KayttajatiedotUpdateDto();
        updateDto.setUsername("user1");

        KayttajatiedotReadDto readDto = kayttajatiedotService.updateKayttajatiedot(oid, updateDto);

        assertThat(readDto.getUsername()).isEqualTo("user1");
    }

    @Test
    public void updateShouldThrowIfHenkiloMissing() {
        String oid = "1.2.3.4.5";
        KayttajatiedotUpdateDto updateDto = new KayttajatiedotUpdateDto();
        updateDto.setUsername("user1");

        Throwable throwable = catchThrowable(() -> kayttajatiedotService.updateKayttajatiedot(oid, updateDto));

        assertThat(throwable).isInstanceOf(NotFoundException.class);
    }

    @Test
    public void updateShouldThrowIfVirkailijaWithoutUsername() {
        String oid = "1.2.3.4.5";
        populate(henkilo(oid).withTyyppi(KayttajaTyyppi.VIRKAILIJA));
        KayttajatiedotUpdateDto updateDto = new KayttajatiedotUpdateDto();
        updateDto.setUsername("user1");

        Throwable throwable = catchThrowable(() -> kayttajatiedotService.updateKayttajatiedot(oid, updateDto));

        assertThat(throwable).isInstanceOf(ValidationException.class);
    }

    @Test
    public void updateShouldReturnIfPalveluWithoutUsername() {
        String oid = "1.2.3.4.5";
        populate(kayttajatiedot(henkilo(oid).withTyyppi(KayttajaTyyppi.PALVELU), "user1"));
        KayttajatiedotUpdateDto updateDto = new KayttajatiedotUpdateDto();
        updateDto.setUsername("user1");

        KayttajatiedotReadDto readDto = kayttajatiedotService.updateKayttajatiedot(oid, updateDto);

        assertThat(readDto.getUsername()).isEqualTo("user1");
    }

    @Test
    @WithMockUser(username = "user1")
    public void testValidateUsernamePassword() {
        final String henkiloOid = "1.2.246.562.24.27470134096";
        String username = "eetu.esimerkki@geemail.fi";
        populate(henkilo(henkiloOid));
        populate(kayttajatiedot(henkilo(henkiloOid), username));
        kayttajatiedotService.changePasswordAsAdmin(henkiloOid, TEST_PASSWORD);
        Optional<Kayttajatiedot> kayttajatiedot = this.kayttajatiedotRepository.findByUsername(username);
        assertThat(kayttajatiedot)
                .isNotEmpty()
                .hasValueSatisfying(kayttajatiedot1 -> assertThat(kayttajatiedot1.getPassword()).isNotEmpty());
    }

    @Test
    @WithMockUser(username = "oid1")
    public void getByUsernameAndPassword() {
        populate(henkilo("oid1"));

        // käyttäjää ei löydy
        assertThatThrownBy(() -> kayttajatiedotService.getByUsernameAndPassword("user2", "pass2"))
                .isInstanceOf(UnauthorizedException.class);

        // käyttäjällä ei ole salasanaa
        KayttajatiedotCreateDto createDto = new KayttajatiedotCreateDto("user2");
        kayttajatiedotService.create("oid2", createDto);
        assertThatThrownBy(() -> kayttajatiedotService.getByUsernameAndPassword("user2", "pass2"))
                .isInstanceOf(UnauthorizedException.class);

        // käyttäjällä on salasana
        kayttajatiedotService.changePasswordAsAdmin("oid2", TEST_PASSWORD);
        assertThatThrownBy(() -> kayttajatiedotService.getByUsernameAndPassword("user2", "pass2"))
                .isInstanceOf(UnauthorizedException.class);
        KayttajatiedotReadDto readDto = kayttajatiedotService.getByUsernameAndPassword("USER2", TEST_PASSWORD);
        assertThat(readDto).extracting(KayttajatiedotReadDto::getUsername).isEqualTo("user2");

        // käyttäjä on passivoitu
        henkiloService.passivoi("oid2", "oid1");
        assertThatThrownBy(() -> kayttajatiedotService.getByUsernameAndPassword("USER2", TEST_PASSWORD))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @WithMockUser(username = "counterTest")
    public void countSuccessfullLogins() {
        populate(henkilo("counterTest"));
        populate(kayttajatiedot(henkilo("counterTest"), "counterTest"));
        kayttajatiedotService.changePasswordAsAdmin("counterTest", TEST_PASSWORD);

        Kayttajatiedot userDetails = kayttajatiedotRepository.findByUsername("counterTest").orElseThrow();
        assertThat(userDetails.getLoginCounter()).isNull();

        kayttajatiedotService.getByUsernameAndPassword("counterTest", TEST_PASSWORD);
        kayttajatiedotService.getByUsernameAndPassword("counterTest", TEST_PASSWORD);

        userDetails = kayttajatiedotRepository.findByUsername("counterTest").orElseThrow();
        assertThat(userDetails.getLoginCounter()).isNotNull();
        assertThat(userDetails.getLoginCounter().getCount()).isEqualTo(2);
    }
}
