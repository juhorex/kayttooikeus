package fi.vm.sade.kayttooikeus.service.impl;

import com.google.common.collect.Lists;
import fi.vm.sade.kayttooikeus.config.properties.CommonProperties;
import fi.vm.sade.kayttooikeus.config.properties.EmailInvitationProperties;
import fi.vm.sade.kayttooikeus.dto.TextGroupMapDto;
import fi.vm.sade.kayttooikeus.dto.YhteystietojenTyypit;
import fi.vm.sade.kayttooikeus.model.Anomus;
import fi.vm.sade.kayttooikeus.model.Henkilo;
import fi.vm.sade.kayttooikeus.model.KayttoOikeusRyhma;
import fi.vm.sade.kayttooikeus.model.Kutsu;
import fi.vm.sade.kayttooikeus.repositories.KayttoOikeusRyhmaRepository;
import fi.vm.sade.kayttooikeus.repositories.dto.ExpiringKayttoOikeusDto;
import fi.vm.sade.kayttooikeus.service.EmailService;
import fi.vm.sade.kayttooikeus.service.exception.NotFoundException;
import fi.vm.sade.kayttooikeus.service.external.OppijanumerorekisteriClient;
import fi.vm.sade.kayttooikeus.service.external.OrganisaatioClient;
import fi.vm.sade.kayttooikeus.service.external.RyhmasahkopostiClient;
import fi.vm.sade.kayttooikeus.util.YhteystietoUtil;
import fi.vm.sade.kayttooikeus.util.AnomusKasiteltySahkopostiBuilder;
import fi.vm.sade.kayttooikeus.util.UserDetailsUtil;
import fi.vm.sade.oppijanumerorekisteri.dto.HenkiloDto;
import fi.vm.sade.oppijanumerorekisteri.dto.HenkiloPerustietoDto;
import fi.vm.sade.oppijanumerorekisteri.dto.HenkilonYhteystiedotViewDto;
import fi.vm.sade.oppijanumerorekisteri.dto.YhteystietoTyyppi;
import fi.vm.sade.properties.OphProperties;
import fi.vm.sade.ryhmasahkoposti.api.dto.EmailData;
import fi.vm.sade.ryhmasahkoposti.api.dto.EmailMessage;
import fi.vm.sade.ryhmasahkoposti.api.dto.EmailRecipient;
import fi.vm.sade.ryhmasahkoposti.api.dto.ReportedRecipientReplacementDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.sql.Date;
import java.text.DateFormat;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import java.util.Optional;
import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;
import java.util.Set;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import org.apache.commons.codec.binary.Base64;

@Service
public class EmailServiceImpl implements EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);
    private static final String DEFAULT_LANGUAGE_CODE = "fi";
    private static final Locale DEFAULT_LOCALE = new Locale(DEFAULT_LANGUAGE_CODE);
    private static final String KAYTTOOIKEUSMUISTUTUS_EMAIL_TEMPLATE_NAME = "kayttooikeusmuistutus_email";
    private static final String KAYTTOOIKEUSANOMUSILMOITUS_EMAIL_TEMPLATE_NAME = "kayttooikeushakemusilmoitus_email";
    private static final String ANOMUS_KASITELTY_EMAIL_TEMPLATE_NAME = "kayttooikeusanomuskasitelty_email";
    private static final String ANOMUS_KASITELTY_EMAIL_REPLACEMENT_TILA = "anomuksenTila";
    private static final String ANOMUS_KASITELTY_EMAIL_REPLACEMENT_PERUSTE = "hylkaamisperuste";
    private static final String ANOMUS_KASITELTY_EMAIL_REPLACEMENT_ROOLIT = "roolit";
    private static final String UNOHTUNUT_SALASANA_EMAIL_TEMPLATE_NAME = "salasanareset_email";
    private static final String UNOHTUNUT_SALASANA_EMAIL_REPLACEMENT_LINKKI = "linkki";
    private static final String UNOHTUNUT_SALASANA_EMAIL_REPLACEMENT_ETUNIMI = "etunimi";
    private static final String UNOHTUNUT_SALASANA_EMAIL_REPLACEMENT_SUKUNIMI = "sukunimi";
    private static final String CALLING_PROCESS = "kayttooikeus";

    private final String expirationReminderSenderEmail;
    private final String expirationReminderPersonUrl;

    private final OppijanumerorekisteriClient oppijanumerorekisteriClient;
    private final RyhmasahkopostiClient ryhmasahkopostiClient;
    private final OrganisaatioClient organisaatioClient;

    private final KayttoOikeusRyhmaRepository kayttoOikeusRyhmaRepository;

    private final CommonProperties commonProperties;
    private final OphProperties urlProperties;

    @Autowired
    public EmailServiceImpl(OppijanumerorekisteriClient oppijanumerorekisteriClient,
                            RyhmasahkopostiClient ryhmasahkopostiClient,
                            EmailInvitationProperties config,
                            OphProperties ophProperties,
                            KayttoOikeusRyhmaRepository kayttoOikeusRyhmaRepository,
                            CommonProperties commonProperties,
                            OphProperties urlProperties,
                            OrganisaatioClient organisaatioClient) {
        this.oppijanumerorekisteriClient = oppijanumerorekisteriClient;
        this.ryhmasahkopostiClient = ryhmasahkopostiClient;
        this.expirationReminderSenderEmail = config.getSenderEmail();
        this.expirationReminderPersonUrl = ophProperties.url("authentication-henkiloui.expirationreminder.personurl");
        this.kayttoOikeusRyhmaRepository = kayttoOikeusRyhmaRepository;
        this.commonProperties = commonProperties;
        this.urlProperties = urlProperties;
        this.organisaatioClient = organisaatioClient;
    }

    @Override
    public void sendEmailAnomusAccepted(Anomus anomus) {
        // add all handled with accepted status to email
        this.sendEmailAnomusHandled(anomus, input -> input.myonnetyt(anomus.getMyonnettyKayttooikeusRyhmas()));
    }

    private void sendEmailAnomusHandled(Anomus anomus, Consumer<AnomusKasiteltySahkopostiBuilder> consumer) {
        /* If this fails, the whole requisition handling process MUST NOT fail!!
         * Having an email sent from handled requisitions is a nice-to-have feature
         * but it's not a reason to cancel the whole transaction
         */
        try {
            EmailRecipient recipient = this.newEmailRecipient(anomus.getHenkilo());
            recipient.setEmail(anomus.getSahkopostiosoite());
            String languageCode = recipient.getLanguageCode();

            List<ReportedRecipientReplacementDTO> replacements = new ArrayList<>();
            replacements.add(new ReportedRecipientReplacementDTO(ANOMUS_KASITELTY_EMAIL_REPLACEMENT_TILA, anomus.getAnomuksenTila()));
            replacements.add(new ReportedRecipientReplacementDTO(ANOMUS_KASITELTY_EMAIL_REPLACEMENT_PERUSTE, anomus.getHylkaamisperuste()));
            AnomusKasiteltySahkopostiBuilder builder = new AnomusKasiteltySahkopostiBuilder(this.kayttoOikeusRyhmaRepository, languageCode);
            consumer.accept(builder);
            replacements.add(new ReportedRecipientReplacementDTO(ANOMUS_KASITELTY_EMAIL_REPLACEMENT_ROOLIT, builder.build()));
            recipient.setRecipientReplacements(replacements);

            EmailMessage message = this.generateEmailMessage(ANOMUS_KASITELTY_EMAIL_TEMPLATE_NAME, languageCode);
            this.ryhmasahkopostiClient.sendRyhmasahkoposti(new EmailData(Lists.newArrayList(recipient), message));
        }
        catch (Exception e) {
            logger.error("Error sending requisition handled email", e);
        }
    }

    private EmailMessage generateEmailMessage(String templateName, String languageCode) {
        return this.generateEmailMessage(languageCode, this.expirationReminderSenderEmail,
                this.expirationReminderSenderEmail, templateName);
    }

    private EmailMessage generateEmailMessage(String languageCode, String fromEmail, String replyToEmail, String templateName) {
        EmailMessage message = new EmailMessage();
        message.setCallingProcess(CALLING_PROCESS);
        message.setFrom(fromEmail);
        message.setReplyTo(replyToEmail);
        message.setTemplateName(templateName);
        message.setHtml(true);
        message.setLanguageCode(languageCode);
        return message;
    }

    private EmailRecipient newEmailRecipient(Henkilo henkilo) {
        HenkiloDto henkiloDto = this.oppijanumerorekisteriClient.getHenkiloByOid(henkilo.getOidHenkilo());
        String email = UserDetailsUtil.getEmailByPriority(henkiloDto)
                .orElseThrow(() -> new NotFoundException("User has no valid email"));
        EmailRecipient recipient = new EmailRecipient();
        recipient.setEmail(email);
        recipient.setOid(henkilo.getOidHenkilo());
        recipient.setOidType("henkilo");
        recipient.setName(UserDetailsUtil.getName(henkiloDto));
        recipient.setLanguageCode(UserDetailsUtil.getLanguageCode(henkiloDto));
        return recipient;
    }

    @Override
    @Transactional
    public void sendExpirationReminder(String henkiloOid, List<ExpiringKayttoOikeusDto> tapahtumas) {
        // Not grouped by language code since might change to one TX / receiver in the future.
        
        HenkiloPerustietoDto henkilonPerustiedot = oppijanumerorekisteriClient.getHenkilonPerustiedot(henkiloOid)
                .orElseThrow(() -> new NotFoundException("Henkilö not found by henkiloOid=" + henkiloOid));
        String languageCode = UserDetailsUtil.getLanguageCode(henkilonPerustiedot);
        
        EmailData email = new EmailData();
        email.setEmail(this.generateEmailMessage(KAYTTOOIKEUSMUISTUTUS_EMAIL_TEMPLATE_NAME, languageCode));
        email.setRecipient(singletonList(getEmailRecipient(henkiloOid, languageCode, tapahtumas)));
        ryhmasahkopostiClient.sendRyhmasahkoposti(email);
    }

    private EmailRecipient getEmailRecipient(String henkiloOid, String langugeCode, List<ExpiringKayttoOikeusDto> kayttoOikeudet) {
        HenkilonYhteystiedotViewDto yhteystiedot = oppijanumerorekisteriClient.getHenkilonYhteystiedot(henkiloOid);
        String email = yhteystiedot.get(YhteystietojenTyypit.PRIORITY_ORDER).getSahkoposti();
        if (email == null) {
            logger.warn("Henkilöllä (oid={}) ei ole sähköpostia yhteystietona. Käyttöoikeuksien vanhentumisesta ei lähetetty muistutusta", henkiloOid);
            return null;
        }

        List<ReportedRecipientReplacementDTO> replacements = new ArrayList<>();
        replacements.add(new ReportedRecipientReplacementDTO("expirations", getExpirationsText(kayttoOikeudet, langugeCode)));
        replacements.add(new ReportedRecipientReplacementDTO("url", expirationReminderPersonUrl));

        EmailRecipient recipient = new EmailRecipient(henkiloOid, email);
        recipient.setLanguageCode(langugeCode);
        recipient.setRecipientReplacements(replacements);

        return recipient;
    }

    private String getExpirationsText(List<ExpiringKayttoOikeusDto> kayttoOikeudet, String languageCode) {
        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, DEFAULT_LOCALE);
        return kayttoOikeudet.stream().map(kayttoOikeus -> {
            String kayttoOikeusRyhmaNimi = ofNullable(kayttoOikeus.getRyhmaDescription())
                    .flatMap(d -> d.getOrAny(languageCode)).orElse(kayttoOikeus.getRyhmaName());
            String voimassaLoppuPvmStr = dateFormat.format(Date.from(kayttoOikeus.getVoimassaLoppuPvm().atStartOfDay(ZoneId.systemDefault()).toInstant()));
            return String.format("%s (%s)", kayttoOikeusRyhmaNimi, voimassaLoppuPvmStr);
        }).collect(joining(", "));
    }

    @Override
    @Transactional
    public void sendNewRequisitionNotificationEmails(Set<String> henkiloOids) {
        henkiloOids.stream()
                .map(this::getRecipient)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(groupingBy(EmailRecipient::getLanguageCode))
                .forEach(this::sendNewRequisitionNotificationEmail);
    }

    private Optional<EmailRecipient> getRecipient(String henkiloOid) {
        HenkiloDto henkiloDto = oppijanumerorekisteriClient.getHenkiloByOid(henkiloOid);
        Optional<String> yhteystietoArvo = YhteystietoUtil.getYhteystietoArvo(
                henkiloDto.getYhteystiedotRyhma(),
                YhteystietoTyyppi.YHTEYSTIETO_SAHKOPOSTI,
                YhteystietojenTyypit.PRIORITY_ORDER);
        return yhteystietoArvo.map(sahkoposti -> createRecipient(henkiloDto, sahkoposti));
    }

    private EmailRecipient createRecipient(HenkiloDto henkilo, String sahkoposti) {
        String kieliKoodi = UserDetailsUtil.getLanguageCode(henkilo);
        EmailRecipient recipient = new EmailRecipient(henkilo.getOidHenkilo(), sahkoposti);
        recipient.setLanguageCode(kieliKoodi);
        return recipient;
    }

    private void sendNewRequisitionNotificationEmail(String kieliKoodi, List<EmailRecipient> recipients) {
        EmailData data = new EmailData();
        data.setEmail(generateEmailMessage(KAYTTOOIKEUSANOMUSILMOITUS_EMAIL_TEMPLATE_NAME, kieliKoodi));
        data.setRecipient(recipients);
        ryhmasahkopostiClient.sendRyhmasahkoposti(data);
    }

    public void sendInvitationEmail(Kutsu kutsu) {
        EmailData emailData = new EmailData();

        EmailMessage email = new EmailMessage();
        email.setTemplateName(this.commonProperties.getInvitationEmail().getTemplate());
        email.setLanguageCode(kutsu.getKieliKoodi());
        email.setCallingProcess(CALLING_PROCESS);
        email.setFrom(this.commonProperties.getInvitationEmail().getFrom());
        email.setReplyTo(this.commonProperties.getInvitationEmail().getFrom());
        email.setCharset("UTF-8");
        email.setHtml(true);
        email.setSender(this.commonProperties.getInvitationEmail().getSender());
        emailData.setEmail(email);

        EmailRecipient recipient = new EmailRecipient();
        recipient.setEmail(kutsu.getSahkoposti());
        recipient.setLanguageCode(kutsu.getKieliKoodi());
        recipient.setName(kutsu.getEtunimi() + " " + kutsu.getSukunimi());

        OrganisaatioClient.Mode organizationClientState = OrganisaatioClient.Mode.multiple();
        recipient.setRecipientReplacements(asList(
                replacement("url", this.urlProperties.url("kayttooikeus-service.invitation.url", kutsu.getSalaisuus())),
                replacement("etunimi", kutsu.getEtunimi()),
                replacement("sukunimi", kutsu.getSukunimi()),
                replacement("organisaatiot", kutsu.getOrganisaatiot().stream()
                        .map(org -> new OranizationReplacement(new TextGroupMapDto(
                                        this.organisaatioClient.getOrganisaatioPerustiedotCached(org.getOrganisaatioOid(),
                                                organizationClientState)
                                                .orElseThrow(() -> new NotFoundException("Organisation not found with oid " + org.getOrganisaatioOid()))
                                                .getNimi()).getOrAny(kutsu.getKieliKoodi()).orElse(null),
                                        org.getRyhmat().stream().map(KayttoOikeusRyhma::getDescription)
                                                .map(desc -> desc.getOrAny(kutsu.getKieliKoodi()).orElse(null))
                                                .filter(Objects::nonNull).sorted().collect(toList())
                                )
                        ).sorted(comparing(OranizationReplacement::getName)).collect(toList()))
        ));
        emailData.setRecipient(singletonList(recipient));

        logger.info("Sending invitation email to {}", kutsu.getSahkoposti());
        HttpResponse response = this.ryhmasahkopostiClient.sendRyhmasahkoposti(emailData);
        try {
            logger.info("Sent invitation email to {}, ryhmasahkoposti-result: {}", kutsu.getSahkoposti(),
                    IOUtils.toString(response.getEntity().getContent()));
        } catch (IOException|NullPointerException e) {
            logger.error("Could not read ryhmasahkoposti-result: " + e.getMessage(), e);
        }
    }

    @NotNull
    private ReportedRecipientReplacementDTO replacement(String name, Object value) {
        ReportedRecipientReplacementDTO replacement = new ReportedRecipientReplacementDTO();
        replacement.setName(name);
        replacement.setValue(value);
        return replacement;
    }

    @Getter
    @AllArgsConstructor
    public static class OranizationReplacement {
        private final String name;
        private final List<String> permissions;
    }

    @Override
    public void sendEmailReset(HenkiloDto henkilo, String sahkoposti, String poletti) {
        String linkki = urlProperties.url("registration-ui.passwordReset", encode(poletti));

        EmailRecipient recipient = new EmailRecipient(henkilo.getOidHenkilo(), sahkoposti);
        recipient.setRecipientReplacements(Arrays.asList(
                new ReportedRecipientReplacementDTO(UNOHTUNUT_SALASANA_EMAIL_REPLACEMENT_LINKKI, linkki),
                new ReportedRecipientReplacementDTO(UNOHTUNUT_SALASANA_EMAIL_REPLACEMENT_ETUNIMI, henkilo.getKutsumanimi()),
                new ReportedRecipientReplacementDTO(UNOHTUNUT_SALASANA_EMAIL_REPLACEMENT_SUKUNIMI, henkilo.getSukunimi()),
                // TODO KJHH-1070: kielistä otsikko suoraan templatessa
                new ReportedRecipientReplacementDTO("subject", "Salasanan nollaus")
        ));

        // TODO KJHH-1070: kun kaikkiin kieliin on templatet niin ota käyttöön
        //String kieliKoodi = UserDetailsUtil.getLanguageCode(henkilo);
        String kieliKoodi = "fi"; //UserDetailsUtil.getLanguageCode(henkilo);
        EmailMessage emailMessage = generateEmailMessage(UNOHTUNUT_SALASANA_EMAIL_TEMPLATE_NAME, kieliKoodi);

        EmailData emailData = new EmailData();
        emailData.setEmail(emailMessage);
        emailData.setRecipient(singletonList(recipient));
        ryhmasahkopostiClient.sendRyhmasahkoposti(emailData);
    }

    private static String encode(String value) {
        Base64 base64 = new Base64(true);
        return new String(base64.encode(value.getBytes())).replaceAll("(?:\\r\\n|\\n\\r|\\n|\\r)", "");
    }

}
