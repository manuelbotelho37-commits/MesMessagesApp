# Mes tâches

Application Android native en français. Android 8 ou plus récent.

- Tâches et rendez-vous avec date complète et heure sur 24 heures.
- Ajout, modification, suppression avec confirmation.
- Classement chronologique, cases à cocher, onglet Terminées.
- Enregistrement dans une base SQLite sur le téléphone.
- Brouillon conservé lors d'une rotation ou d'un changement de taille de l'écran.
- Aucun compte à créer, aucune connexion réseau ni permission SMS ou contacts.
- Aucune notification automatique dans cette version.
- La sauvegarde Android de la base est activée, selon les réglages du téléphone ; la synchronisation entre appareils n'est pas fournie.

L'identifiant Android est fr.manubotelho.mestaches. Cette application s'installe séparément de MesMessages.

Sources conservées dans le sous-dossier mes-taches sur la branche mes-taches. Le projet MesMessages reste indépendant.

Compilation : Gradle 8.9, Java 17, Android SDK 35.
Validation : gradle -p mes-taches testDebugUnitTest lintDebug assembleRelease

La clé de signature privée est conservée dans un artefact GitHub Actions restreint, jamais dans le dépôt ou dans la publication APK. Le workflow refuse de créer une nouvelle clé si une version publiée existe et que la clé précédente est indisponible. Garder une copie sécurisée de cet artefact avant son expiration est nécessaire pour assurer de futures mises à jour.
