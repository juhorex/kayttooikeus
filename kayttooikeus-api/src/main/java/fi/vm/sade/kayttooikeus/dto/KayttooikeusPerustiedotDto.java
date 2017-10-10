package fi.vm.sade.kayttooikeus.dto;

import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KayttooikeusPerustiedotDto {
    String oidHenkilo;
    Set<KayttooikeusOrganisaatiotDto> organisaatiot = new HashSet<>();

    public KayttooikeusPerustiedotDto(String oidHenkilo, String organisaatioOid, String oikeus, String palvelu) {
        this.oidHenkilo = oidHenkilo;
        KayttooikeusOrganisaatiotDto.KayttooikeusOikeudetDto kayttooikeusOikeudetDto
                = new KayttooikeusOrganisaatiotDto.KayttooikeusOikeudetDto(palvelu, oikeus);
        KayttooikeusOrganisaatiotDto kayttooikeusOrganisaatiotDto = new KayttooikeusOrganisaatiotDto(
                organisaatioOid,
                new HashSet<KayttooikeusOrganisaatiotDto.KayttooikeusOikeudetDto>() {{add(kayttooikeusOikeudetDto);}}
        );
        this.organisaatiot.add(kayttooikeusOrganisaatiotDto);
    }

    public KayttooikeusPerustiedotDto mergeIfSameOid(KayttooikeusPerustiedotDto kayttooikeusPerustiedotDto) {
        if(kayttooikeusPerustiedotDto.getOidHenkilo().equals(this.getOidHenkilo())) {
            this.organisaatiot.addAll(kayttooikeusPerustiedotDto.getOrganisaatiot());
            this.setOrganisaatiot(this.getOrganisaatiot()
                    .stream()
                    .collect(Collectors.groupingBy(KayttooikeusOrganisaatiotDto::getOrganisaatioOid))
                    .values()
                    .stream()
                    .map(kayttooikeusOrganisaatiotDtoGroup -> kayttooikeusOrganisaatiotDtoGroup
                            .stream()
                            .reduce(KayttooikeusOrganisaatiotDto::mergeIfSameOid)
                            .orElseGet(KayttooikeusPerustiedotDto.KayttooikeusOrganisaatiotDto::new))
                    .collect(Collectors.toSet()));
        }
        return this;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KayttooikeusOrganisaatiotDto {
        String organisaatioOid;
        Set<KayttooikeusOikeudetDto> kayttooikeudet = new HashSet<>();

        public KayttooikeusOrganisaatiotDto mergeIfSameOid(KayttooikeusOrganisaatiotDto kayttooikeusOrganisaatiotDto) {
            if(kayttooikeusOrganisaatiotDto.getOrganisaatioOid().equals(this.getOrganisaatioOid())) {
                this.kayttooikeudet.addAll(kayttooikeusOrganisaatiotDto.getKayttooikeudet());
                this.setKayttooikeudet(this.getKayttooikeudet()
                        .stream()
                        .distinct()
                        .collect(Collectors.toSet()));
            }
            return this;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @EqualsAndHashCode
        public static class KayttooikeusOikeudetDto {
            String palvelu;
            String oikeus;
        }
    }
}
