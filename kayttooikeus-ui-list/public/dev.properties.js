var devOverrides = {
 'kayttooikeus-service.l10n': '/kayttooikeus-service/l10n',
 'kayttooikeus-service.kutsu': '/kayttooikeus-service/kutsu',
 'kayttooikeus-service.peruutaKutsu': '/kayttooikeus-service/kutsu/$1',
 'kayttooikeus-service.buildversion': '/kayttooikeus-service/buildversion.txt'
};
Object.keys(devOverrides).map(function(key) {
 window.urls.override[key] = devOverrides[key];
});