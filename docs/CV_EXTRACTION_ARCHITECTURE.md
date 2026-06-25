# 📄 Extraction de Texte CV - Architecture

## 🎯 Vue d'ensemble

Le système d'extraction de CV de **TalentPredict** a été migré d'un serveur Node externe temporaire (port 3001) vers une solution d'extraction hybride performante :
1.  **Extraction Client (Frontend Angular) :** Utilisation de `pdfjs-dist` pour extraire le texte directement dans le navigateur pour des opérations instantanées et légères.
2.  **Extraction Serveur (Backend Spring Boot) :** Intégration directe d'**Apache PDFBox** pour l'extraction de texte côté serveur, suivie d'une analyse sémantique par LLM (Claude 3.5 Sonnet / Llama 3.2).

Cette approche élimine le besoin d'écrire des fichiers temporaires dans `/tmp` sur le disque et supprime le serveur Node externe obsolète qui tournait sur le port 3001.

---

## 📊 Architecture Héréditaire vs Actuelle

```
[ANCIENNE ARCHITECTURE] (Obsolète)
Frontend (Upload PDF) ──> n8n (Fichier temporaire /tmp) ──> PDF Server (port 3001) ──> Ollama

[NOUVELLE ARCHITECTURE] (Active)
Option A (Client-Side) :
Frontend Angular (Upload PDF) ──> Extraction locale (pdfjs-dist) ──> Utilisation immédiate dans l'UI

Option B (Server-Side) :
Frontend (Upload PDF) ──> Spring Boot [/api/profiles/accounts/{id}/upload-cv]
                              └──> CvAnalysisService (Apache PDFBox)
                                      └──> LLM via OpenRouter (Claude 3.5) / Ollama (Llama 3.2)
                                              └──> Sauvegarde en DB (Skills & Profil)
```

---

## 💻 1. Extraction Côté Client (Frontend Angular)

### Composant : [CvExtractorService](file:///c:/Projet/TalentPredict-wt-clean-merged/FrontEnd/src/app/core/services/cv-extractor.service.ts)

Le service extrait le texte de manière asynchrone directement depuis le navigateur. Il supporte les fichiers `.pdf` et `.txt`.

```typescript
// Initialisation du worker PDF.js (CDN JSDelivr aligné sur la version locale)
const version = (pdfjsLib as any).version || '5.6.205';
pdfjsLib.GlobalWorkerOptions.workerSrc = 
  `https://cdn.jsdelivr.net/npm/pdfjs-dist@${version}/build/pdf.worker.min.mjs`;
```

#### Logique d'extraction :
1.  Le fichier est converti en `ArrayBuffer` à l'aide de l'API standard `File.arrayBuffer()`.
2.  Le document PDF est chargé via `pdfjsLib.getDocument()`. Si le worker échoue à s'instancier, un mode de repli (fallback) sans worker (`disableWorker: true`) prend automatiquement le relais.
3.  Une boucle itère sur chaque page pour extraire les éléments textuels avec `page.getTextContent()`.
4.  Les chaînes de caractères sont assemblées et retournées directement au composant appelant.

---

## ☕ 2. Extraction Côté Serveur (Backend Spring Boot)

### Composant : [CvAnalysisService](file:///c:/Projet/TalentPredict-wt-clean-merged/BackEnd/src/main/java/com/talentpredict/modules/ai/services/CvAnalysisService.java)

Le backend extrait le texte d'un CV en utilisant la bibliothèque Java **Apache PDFBox** pour deux cas d'usage principaux :
*   **Cas 1 (Upload Direct) :** L'utilisateur envoie son CV au format PDF via un formulaire multipart à l'endpoint `/api/profiles/accounts/{id}/upload-cv`.
*   **Cas 2 (URL Publique) :** Le service télécharge et extrait le texte d'un PDF hébergé à distance via une URL ou lit directement le fichier local s'il est déjà stocké dans le dossier `uploads` du serveur.

#### Logique technique (PDFBox) :
```java
private String extraireTextePDF(InputStream inputStream) throws Exception {
    try (PDDocument document = Loader.loadPDF(IOUtils.toByteArray(inputStream))) {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        return stripper.getText(document);
    }
}
```

#### Pipeline de traitement post-extraction :
1.  **Limitation de contexte :** Le texte extrait est tronqué à 4 000 caractères pour optimiser le prompt et éviter les coûts excessifs ou les dépassements de fenêtre de contexte des LLM.
2.  **Analyse LLM :** Le texte est transmis à `OpenRouterService` (modèle `anthropic/claude-sonnet-4-5`) ou `Ollama` (`llama3.2:latest`) avec un prompt structurant.
3.  **Extraction JSON :** L'IA renvoie un objet JSON contenant :
    *   Le titre professionnel recommandé.
    *   Une biographie/description résumée.
    *   Les années d'expérience estimées.
    *   La liste des compétences techniques (`TECH`) et comportementales (`SOFT`) identifiées.
4.  **Mise à jour & Sauvegarde :** Le profil de l'utilisateur est mis à jour et les compétences détectées qui n'existent pas encore dans son profil (sans doublons) sont automatiquement insérées en base de données.

---

## 📦 Dépendances requises

### Backend Maven (`BackEnd/pom.xml`)
```xml
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.3</version>
</dependency>
```

### Frontend Node (`FrontEnd/package.json`)
```json
"dependencies": {
  "pdfjs-dist": "^5.6.205"
}
```

---

## 🧪 Guide de test et de validation

### Tester l'extraction backend via cURL
Pour tester l'endpoint d'upload de CV et vérifier que le parseur PDFBox extrait et que le LLM structure correctement le profil :

```bash
curl -X POST http://localhost:8081/api/profiles/accounts/{id}/upload-cv \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -F "file=@/chemin/vers/votre/cv.pdf"
```

**Réponse attendue :**
```json
{
  "message": "Votre profil a été mis à jour et 4 nouveaux skills ont été détectés depuis votre CV",
  "status": "SUCCESS",
  "skillsAjoutes": ["Java", "Spring Boot", "Angular", "Docker"],
  "skillsDejaPresentss": [],
  "totalDetectes": 4,
  "extractedInfo": {
    "title": "Ingénieur d'études et développement Java",
    "experience": 3
  }
}
```
