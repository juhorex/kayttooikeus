package fi.vm.sade.kayttooikeus.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@ToString
public class KayttajaCriteriaDto {

    private KayttajaTyyppi kayttajaTyyppi;
    private Set<String> organisaatioOids;
    private Set<String> kayttoOikeusRyhmaNimet;
    private Map<String, Collection<String>> kayttooikeudet;

    // oppijanumerorekisterin hakuehdot
    private Boolean passivoitu;
    private Boolean duplikaatti;

}
