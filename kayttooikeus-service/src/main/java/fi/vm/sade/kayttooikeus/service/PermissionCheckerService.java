package fi.vm.sade.kayttooikeus.service;


import fi.vm.sade.organisaatio.api.search.OrganisaatioPerustieto;

import java.util.List;
import java.util.Set;

public interface PermissionCheckerService {
    boolean isAllowedToAccessPerson(String callingUserOid, String personOid, List<String> allowedRoles,
                                    ExternalPermissionService permissionCheckService, Set<String> callingUserRoles);
    List<OrganisaatioPerustieto> listOrganisaatiosByHenkiloOid(String oid);
    enum ExternalPermissionService {
        HAKU_APP, SURE
    }

}
