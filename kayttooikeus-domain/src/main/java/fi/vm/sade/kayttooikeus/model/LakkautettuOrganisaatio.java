package fi.vm.sade.kayttooikeus.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "lakkautettu_organisaatio")
public class LakkautettuOrganisaatio {

    @Id
    private String oid;

}
