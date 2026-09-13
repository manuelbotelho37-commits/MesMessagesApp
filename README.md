# MesMessagesApp

Application Android simple pour afficher, dans une seule liste, tous les SMS accessibles par Android sur le téléphone.

## Version actuelle

- importe l’historique SMS disponible sur le téléphone ;
- affiche les messages du plus récent au plus ancien ;
- bouton Actualiser pour recharger la liste ;
- demande l’autorisation SMS au premier lancement.

## Limite importante

Les conversations RCS de Google Messages ne sont pas toujours exposées comme des SMS classiques aux applications tierces. Si certains messages très récents sont des RCS, ils peuvent donc ne pas apparaître même avec l’autorisation SMS.

Les étapes suivantes pourront ajouter Gmail et Outlook avec leur connexion officielle. WhatsApp ne permet pas d’importer proprement tout l’historique via une API publique.

## APK

Le workflow GitHub Actions `Build Android APK` fabrique automatiquement un APK de test après chaque modification de la branche `main`.
