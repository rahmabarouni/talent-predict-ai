# Diagramme d'activité du pipeline multi-source

Ce diagramme décrit le flux global de TalentPredict pour l'analyse d'un candidat à partir de plusieurs sources: CV, GitHub, PCM et QCM. Le pipeline collecte, normalise, score puis fusionne les signaux avant de produire un résultat consolidé.

```mermaid
flowchart TD
    A([Début]) --> B[Utilisateur soumet les sources]
    B --> C{Sources disponibles ?}

    C -->|CV| D1[CV PDF / texte]
    C -->|GitHub| D2[Profil GitHub]
    C -->|PCM| D3[Réponses PCM]
    C -->|QCM| D4[Réponses QCM]

    D1 --> E1[Extraction du texte CV]
    D2 --> E2[Collecte des dépôts, langages, activité et signaux]
    D3 --> E3[Calcul des scores comportementaux]
    D4 --> E4[Correction et calcul des scores QCM]

    E1 --> F[Normalisation commune des données]
    E2 --> F
    E3 --> F
    E4 --> F

    F --> G{Toutes les sources ont été traitées ?}
    G -->|Non| H[Ignorer la source absente et continuer]
    H --> I[Calcul des scores par source]
    G -->|Oui| I[Calcul des scores par source]

    I --> J[Fusion des scores multi-sources]
    J --> K[Application de la pondération métier et du score de confiance]
    K --> L[Classement final des compétences et des recommandations]
    L --> M[Stockage des résultats et préparation de la réponse]
    M --> N[Affichage dans le dashboard / API de résultats]
    N --> O([Fin])
```

## Lecture du flux

1. Le candidat ou le système lance l'analyse avec les sources disponibles.
2. Le CV est d'abord extrait et normalisé afin d'obtenir du texte exploitable.
3. GitHub est analysé pour détecter les langages, la régularité des contributions et les signaux techniques.
4. Le PCM fournit un score comportemental centré sur les soft skills.
5. Le QCM apporte une mesure complémentaire plus structurée et comparable.
6. Chaque source produit ses propres scores, puis le moteur de fusion combine ces résultats dans un score global.
7. Le système calcule ensuite un score de confiance, un classement des compétences et les résultats finaux.
8. Les résultats sont persistés puis exposés à l'interface de consultation.

## Résultat attendu

Le pipeline produit généralement:

- un score global du candidat;
- un détail par source;
- un classement des compétences fortes et des points faibles;
- des recommandations ou une synthèse finale pour l'utilisateur.

## Remarque

Le pipeline reste tolérant aux sources manquantes: si CV, GitHub, PCM ou QCM n'est pas disponible, l'analyse continue avec les signaux restants.