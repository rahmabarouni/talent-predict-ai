# Journal de Stage — Stage de Fin d'Études

- Périodicité du suivi : Hebdomadaire
- Durée : 02/02/2026 → 30/05/2026 (17 semaines)
- Pas de déploiement en production (travail en environnement local)

Informations stagiaire
- Nom du stagiaire : [À remplir]
- Tuteur entreprise : [À remplir]
- Équipe / Projet : TalentPredict

Objectifs du stage (résumé)
- Contribuer au projet TalentPredict : extraction et normalisation de CV, API backend, intégration front, workflows n8n, tests automatisés, documentation et rapport final.

---

Modèle hebdomadaire (à réutiliser pour chaque semaine)
- Objectifs
- Activités réalisées
- Livrables
- Difficultés rencontrées / Risques
- Compétences développées
- Plan pour la semaine suivante

---

## Semaine 1 — 02/02/2026 - 08/02/2026
- Objectifs : Onboarding, accès aux dépôts, installation de l'environnement local.
- Activités réalisées : Clonage du dépôt, configuration de l'environnement Python (`.venv`) et Node, installation dépendances BackEnd/FrontEnd, lecture des documents `README.md` et `API_DOCUMENTATION.md`, réunion d'accueil avec le tuteur.
- Livrables : Checklist d'accès et d'installation validée.
- Difficultés : Accès à certaines variables d'environnement et bases de données (résolus avec le tuteur).
- Compétences : Mise en place d'un environnement de dev, usage IDE, lecture d'architecture.
- Plan : Analyser l'architecture détaillée et prioriser les tâches techniques.

## Semaine 2 — 09/02/2026 - 15/02/2026
- Objectifs : Analyse fonctionnelle et technique, définition des premières tâches.
- Activités réalisées : Étude des diagrammes (`diagrams/`), revue des modules `BackEnd` et `talentpredict-ai`, rédaction d'un plan de travail et estimation des tâches.
- Livrables : Plan de travail détaillé et backlog priorisé.
- Difficultés : Clarifier l'ordre de priorités entre extraction CV et API.
- Compétences : Analyse projet, rédaction de backlog.
- Plan : Débuter l'implémentation des endpoints backend pour la gestion des CV et candidats.

## Semaine 3 — 16/02/2026 - 22/02/2026
- Objectifs : Implémenter les endpoints CRUD pour candidats et gérer l'upload CV.
- Activités réalisées : Ajout de routes backend pour upload de CV, création de schéma de données minimale, premiers tests unitaires.
- Livrables : Endpoints CRUD et tests unitaires initiaux.
- Difficultés : Normalisation des formats de fichiers reçus.
- Compétences : Développement backend (Java/Spring ou stack du projet), tests unitaires.
- Plan : Intégrer le module d'extraction CV et tester sur exemples réels.

## Semaine 4 — 23/02/2026 - 01/03/2026
- Objectifs : Intégration du module `talentpredict-ai` (extracteur de CV).
- Activités réalisées : Lecture de `talentpredict-ai/cv_extractor.py`, création d'un wrapper API pour appeler l'extracteur, tests d'extraction sur un échantillon de CV.
- Livrables : Endpoint d'appel à l'extracteur et exemples de résultats bruts.
- Difficultés : Hétérogénéité des formats de CV et règles d'extraction incomplètes.
- Compétences : Traitement de texte, extraction d'information.
- Plan : Implémenter la normalisation des données extraites.

## Semaine 5 — 02/03/2026 - 08/03/2026
- Objectifs : Nettoyage et mapping des données extraites vers le schéma interne.
- Activités réalisées : Écriture de fonctions de normalisation (dates, expériences, compétences), tests d'intégration entre extracteur et backend.
- Livrables : Module de normalisation + jeux d'exemples testés.
- Difficultés : Cas limites (CV mal formatés, sections manquantes).
- Compétences : Data wrangling, tests d'intégration.
- Plan : Connecter les données normalisées à l'interface utilisateur.

## Semaine 6 — 09/03/2026 - 15/03/2026
- Objectifs : Intégration front-end pour visualiser les résultats d'extraction.
- Activités réalisées : Développement de composants UI (affichage CV, sections expériences, compétences), consommation des endpoints d'API, tests manuels UX.
- Livrables : Prototype front affichant données extraites.
- Difficultés : Adaptation des composants aux données partielles.
- Compétences : Angular/React, intégration API.
- Plan : Améliorer l'expérience utilisateur et gérer les erreurs.

## Semaine 7 — 16/03/2026 - 22/03/2026
- Objectifs : Automatisation des flux via n8n (ingestion, traitement, notifications).
- Activités réalisées : Revue des workflows dans `n8n/workflows-import`, adaptation d'un workflow d'import CV, scripts d'automatisation pour pré-traitement.
- Livrables : Workflow n8n fonctionnel pour ingestion et pré-traitement.
- Difficultés : Gestion des fichiers volumineux et timeouts dans les tâches.
- Compétences : Orchestration n8n, scripting bash/js.
- Plan : Ajouter logs et monitoring minimal aux workflows.

## Semaine 8 — 23/03/2026 - 29/03/2026
- Objectifs : Tests end-to-end (E2E) et mise en place d'un pipeline local de tests.
- Activités réalisées : Écriture de scénarios E2E (parcours upload → extraction → affichage), mise en place de jobs de tests automatisés locaux.
- Livrables : Suite de tests E2E et script d'exécution locale.
- Difficultés : Tests instables selon l'environnement (dépendances externes).
- Compétences : Tests automatisés, scénarios E2E.
- Plan : Stabiliser les tests et documenter comment les exécuter.

## Semaine 9 — 30/03/2026 - 05/04/2026
- Objectifs : Conteneurisation pour développement local (pas de déploiement en production).
- Activités réalisées : Vérification et ajustement des `Dockerfile` (BackEnd/FrontEnd/talentpredict-ai), configuration d'un `docker-compose` pour exécution locale des services.
- Livrables : `docker-compose` local pour tests et développement.
- Difficultés : Configuration réseau entre conteneurs et volumes, adaptée pour dev.
- Compétences : Docker, gestion d'images locales.
- Plan : Documenter la façon d'exécuter l'environnement local avec Docker.

## Semaine 10 — 06/04/2026 - 12/04/2026
- Objectifs : Sécurité et gestion des secrets en local.
- Activités réalisées : Mise en place d'un modèle de configuration pour variables d'environnement (fichier `.env.example`), intégration de vérifications d'accès, révision des permissions d'API.
- Livrables : Guide local de gestion des secrets et corrections sécuritaires.
- Difficultés : Différencier config dev / config future prod (clarifié : pas de déploiement).
- Compétences : Sécurité applicative minimaliste, gestion config.
- Plan : Finaliser documentation d'exploitation locale.

## Semaine 11 — 13/04/2026 - 19/04/2026
- Objectifs : Améliorations fonctionnelles selon retours utilisateurs internes.
- Activités réalisées : Correction de bugs signalés, ajout de filtres et options de tri, amélioration des messages d'erreur côté UI et API.
- Livrables : Pull requests et tickets fermés.
- Difficultés : Régression mineure après refactorings (résolue).
- Compétences : Revue de code, refactoring.
- Plan : Implémenter un scoring initial pour aider le tri des candidats.

## Semaine 12 — 20/04/2026 - 26/04/2026
- Objectifs : Prototype de scoring/Ranking des candidats.
- Activités réalisées : Développement d'un algorithme heuristique (mots-clés pondérés, correspondance compétences), tests comparatifs sur jeu d'essai.
- Livrables : Module de scoring et rapport expérimental.
- Difficultés : Choix des poids et métriques d'évaluation.
- Compétences : Modélisation heuristique, évaluation quantitative.
- Plan : Ajuster le scoring avec retours RH fictifs / tuteur.

## Semaine 13 — 27/04/2026 - 03/05/2026
- Objectifs : Tests utilisateurs et collecte de feedback.
- Activités réalisées : Organisation de sessions internes, collecte de retours et priorisation des corrections.
- Livrables : Rapport de feedback et backlog mis à jour.
- Difficultés : Divergence d'attentes entre utilisateurs (RH vs dev).
- Compétences : Conduite de tests utilisateurs, synthèse.
- Plan : Corriger bugs critiques et améliorer l'ergonomie.

## Semaine 14 — 04/05/2026 - 10/05/2026
- Objectifs : Sprint de robustesse (stability sprint).
- Activités réalisées : Corrections de performance sur endpoints lourds, optimisation des requêtes, réduction des temps de réponse pour les appels d'extraction.
- Livrables : Version plus stable et notes de performance.
- Difficultés : Optimisations limitées par contraintes de l'extracteur tiers.
- Compétences : Profiling, optimisation backend.
- Plan : Préparer la documentation utilisateur et technique pour livraison.

## Semaine 15 — 11/05/2026 - 17/05/2026
- Objectifs : Finalisation des livrables et procédures de restauration locale (en l'absence de déploiement).
- Activités réalisées : Rédaction de scripts de migration locale, procédures de sauvegarde/restauration, checklist de livraison du code et documentation.
- Livrables : Scripts de migration testés localement, checklist de livraison.
- Difficultés : S'assurer de la reproductibilité sur une autre machine dev.
- Compétences : Mise en place de procédures d'exploitation locales.
- Plan : Vérifier reproductibilité et rédiger la section "mise en route" du rapport.

## Semaine 16 — 18/05/2026 - 24/05/2026
- Objectifs : Recette finale en environnement local et validation des fonctionnalités.
- Activités réalisées : Exécution des scénarios de recette, corrections finales, compilation du lot de livrables (code, tests, docs).
- Livrables : Rapport de recette et liste des corrections finales appliquées.
- Difficultés : Ajustements de dernière minute sur l'UI.
- Compétences : Validation fonctionnelle, QA légère.
- Plan : Rédiger le rapport de stage et préparer la soutenance.

## Semaine 17 — 25/05/2026 - 30/05/2026 (Fin de stage)
- Objectifs : Rédaction du rapport final, préparation de la soutenance, restitution des livrables.
- Activités réalisées : Rédaction du rapport de stage structuré (contexte, travail réalisé, résultats, bilan), préparation d'un diaporama de soutenance, réunion de clôture avec le tuteur, archivage du code et documentation.
- Livrables : Rapport de stage final, slides de soutenance, code et documentation fournis au tuteur.
- Difficultés : Respect des délais pour finaliser les documents (maîtrisé en planifiant des plages dédiées).
- Compétences : Rédaction scientifique, communication orale, bilan professionnel.
- Plan : Soutenance et bilan final.

---

### Annexes et annexes techniques (suggestions)
- Inclure un extrait du `README.md` pour la mise en route locale.
- Joindre des exemples de CV et sorties d'extraction (échantillons anonymisés).
- Lister les commandes utiles pour exécuter les tests et l'environnement docker local.

---

Signature

Fait le : 30/05/2026

Stagiaire : ______________________
Tuteur : ______________________

