# Coast Line Tracking

>Ce service WPS est réalisé dans le cadre d'un projet de mise en pratique avancée à l'Université de Bretagne Occidentale au profil de l'Institut Universitaire Europèen de la Mer. 

Il consiste au dévéloppement d'un webservice pour le suivi de traits de côtes. Il est accessible via le service WPS de [GeoServer](https://geoserver.org/)

## Pré requis

>Geoserver 2.21.5 fonctionnant avec java 11 et l'[extension WPS](https://docs.geoserver.org/latest/en/user/services/wps/install.html)

Les drois d'accès au service WPS et spécifiquement à ce service doivent être configuré dans le GeoServer.
 [Documentation de configuration](https://docs.geoserver.org/stable/en/user/services/wps/security.html)

 Pour la migration de geoserver 2.19 vers 2.21, en plus du jar coastLinesTracking-x.y.jar vous devez ajouter dans les libs de geoserver
 - gt-geojson-27.5.jar
 - json-simple-1.1.1.jar

/!\ Attention la gestion des geojson dans geotools a changer ( librairie et gestion des dates)
Des évolutions seront à réalisé lors du prochaine changement de geotools.

## Documentation

> Quatres process sont disponibles : 
> - Draw radial - coa:drawRadial
> - Calculate distances between coastlines - coa:getDistances
> - distancesToCSV - coa:getDistancestoCSV
> - coastLinesTracking - coa:coastLinesTracking

Pour l'application maddog, 
le service coa:drawRadial permet de récupérer la radiale et de l'afficher sur la carte
le service coa:coastLinesTracking est la succession du service drawRadial, getDistances et getDistanceToCSV
