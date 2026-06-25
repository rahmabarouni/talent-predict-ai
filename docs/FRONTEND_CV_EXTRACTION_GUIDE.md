# 🔄 Guide d'extraction de CV - Migration Terminée

> [!NOTE]
> **Statut de la Migration : TERMINÉE ✅**
> Cette migration a été entièrement complétée et intégrée dans le code. Le service d'extraction utilise dorénavant l'approche hybride : extraction locale dans le navigateur via `pdfjs-dist` pour les actions instantanées de l'utilisateur, et extraction serveur via `Apache PDFBox` dans Spring Boot pour la persistance et l'analyse globale. Le serveur Node externe obsolète sur le port 3001 a été définitivement supprimé.

---

## Change Summary

**BEFORE (Old Approach):**

```
Frontend → n8n (with PDF binary)
  ↓
n8n calls PDF Server (port 3001)
  ↓
PDF Server extracts text
  ↓
n8n analyzes text
```

**AFTER (New Approach - Must Implement):**

```
Frontend → CV Extractor Service (TypeScript/Angular)
  ↓
Text extracted LOCALLY in browser ✅
  ↓
Frontend → n8n (with already-extracted text)
  ↓
n8n receives: extracted_cv_text (no PDF parsing needed)
  ↓
n8n analyzes text directly + GitHub + PCM
  ↓
Response with merged scores
```

---

## ✅ Required Changes to n8n Workflow

### WORKFLOW: "master soft skills agent"

**Current:** Expects binary PDF file
**New:** Expects text already extracted

---

### 1. Update Webhook Input

**Current Webhook receives:**

```json
{
  "full_name": "John Doe",
  "email": "john@example.com",
  "cv_file": [FILE_BINARY],      ← Binary PDF
  "github_username": "johndoe",
  "q1": 5, "q2": 3, ..., "q18": 2
}
```

**New Webhook will receive:**

```json
{
  "full_name": "John Doe",
  "email": "john@example.com",
  "extracted_cv_text": "Software Engineer...",  ← TEXT (already extracted)
  "github_username": "johndoe",
  "q1": 5, "q2": 3, ..., "q18": 2
}
```

---

### 2. Update "Code in JavaScript" Node

**Location:** master soft skills agent → "Code in JavaScript"

**BEFORE (Current Code):**

```javascript
// Handles binary PDF extraction
const body = $input.first().json.body || $input.first().json;
const binaryData = $input.first().binary;

if (binaryData && Object.keys(binaryData).length > 0) {
  // Extract PDF using PDF Server (port 3001)
  const fileData = binaryData[fieldName];
  const pdfBuffer = Buffer.from(fileData.data, 'base64');
  fs.writeFileSync('/tmp/master_cv.pdf', pdfBuffer);

  // Call PDF Server
  const cvText = await httpPost('http://localhost:3001/extract', {...});
}
```

**AFTER (New Code - Simplified):**

```javascript
const body = $input.first().json.body || $input.first().json;

// ✅ NEW: CV text already extracted by frontend!
const cvText = body.extracted_cv_text || '';

// GitHub analysis (unchanged)
if (body.github_username) {
  const ghResult = await httpPost('/webhook/github-analyze', {
    github_username: body.github_username
  });
}

// PCM scoring (unchanged)
const pcmResult = {
  soft_skills: {
    communication: {score: (q1+q2+q3)/3, evidence: 'PCM'},
    ...
  }
};

// ✅ Direct CV analysis (no PDF parsing needed)
if (cvText && cvText.length > 50) {
  const cvResult = await httpPost('/webhook/cv-upload-text', {
    full_name: body.full_name,
    cv_text: cvText  // ← Use extracted text directly!
  });
}
```

---

### 3. Simplify: Remove PDF Server Calls

**DELETE:** All references to PDF Server (port 3001)

```javascript
// ❌ DELETE THIS (no longer needed)
const extractBody = JSON.stringify({ base64: fileBuffer.toString('base64') });
const req = http.request({ hostname: 'localhost', port: 3001, ... });
```

---

### 4. Update CV Analysis Workflow

**Workflow: "cv parser"**

**Current:** Expects binary file + processes it
**New:** Receives text + analyzes it

**Changes needed:**

```javascript
// Node: "Code in JavaScript1"

// BEFORE
const fileBuffer = fs.readFileSync('/tmp/current_cv.pdf');
const body = JSON.stringify({ base64: fileBuffer.toString('base64') });
const rawResponse = await http.request({...localhost:3001/extract...});

// AFTER
const cvText = $input.first().json.cv_text || '';
// No need to call PDF Server anymore!
// Send directly to Ollama for analysis
```

---

### 5. Final Webhook Response

**Response structure (unchanged):**

```json
{
  "user_name": "John Doe",
  "user_email": "john@example.com",
  "merged_soft_skills": {
    "communication": 7.8,
    "discipline": 8.1,
    "curiosity": 6.5,
    "collaboration": 7.9,
    "ownership": 8.2,
    "leadership": 7.6
  },
  "overall_score": 7.8,
  "personality_type": "Promoteur",
  "personality_description": "...",
  "training_recommendations": {...},
  "career_advice": "..."
}
```

---

## 📊 Data Flow Comparison

### OLD (Current)

```
┌─────────────────┐
│ Frontend        │
├─────────────────┤
│ PDF Binary      │
│ (245KB)         │
└────────┬────────┘
         ↓
┌──────────────────────────────────┐
│ n8n Webhook                      │
├──────────────────────────────────┤
│ Receive PDF binary file          │
└────────┬─────────────────────────┘
         ↓
┌──────────────────────────────────┐
│ n8n Code Node                    │
├──────────────────────────────────┤
│ 1. Write PDF to disk             │
│ 2. Read back                     │
│ 3. Convert to base64             │
│ 4. HTTP POST to PDF Server       │
└────────┬─────────────────────────┘
         ↓
┌──────────────────────────────────┐
│ PDF Server (port 3001)           │
├──────────────────────────────────┤
│ Parse PDF with pdf-parse library │
│ Extract text from all pages      │
│ Return text to n8n               │
└────────┬─────────────────────────┘
         ↓
┌──────────────────────────────────┐
│ n8n Ollama Node                  │
├──────────────────────────────────┤
│ Analyze extracted text           │
│ Generate soft skills scores      │
└─────────────────────────────────┘

LATENCY: ~1-2 seconds for PDF parsing
LOAD: PDF Server handles all extraction
```

### NEW (Simplified - Must Implement)

```
┌─────────────────────────┐
│ Frontend                │
├─────────────────────────┤
│ CV File (PDF/TXT)       │
│                         │
│ => Extract locally with │
│    pdfjs-dist library   │
│                         │
│ => extracted_cv_text ✅ │
└────────┬────────────────┘
         ↓
┌──────────────────────────────────┐
│ n8n Webhook                      │
├──────────────────────────────────┤
│ Receive extracted text directly  │
│ extracted_cv_text: "..."         │
└────────┬─────────────────────────┘
         ↓
┌──────────────────────────────────┐
│ n8n Code Node (Simplified)       │
├──────────────────────────────────┤
│ ✅ NO PDF parsing!               │
│ ✅ NO disk I/O!                  │
│ ✅ NO base64 conversion!         │
│ ✅ NO PDF Server call!           │
│                                  │
│ Just:                            │
│ 1. Use extracted_cv_text         │
│ 2. GitHub analysis               │
│ 3. PCM scoring                   │
│ 4. Merge scores                  │
└────────┬─────────────────────────┘
         ↓
┌──────────────────────────────────┐
│ n8n Ollama Node                  │
├──────────────────────────────────┤
│ Analyze extracted text           │
│ Generate soft skills scores      │
└─────────────────────────────────┘

LATENCY: ~0 seconds (text already extracted)
LOAD: Distributed to client
BENEFITS: Faster, simpler, scalable
```

---

## 🛠️ Implementation Steps

### Step 1: Install pdfjs-dist in Frontend

```bash
cd FrontEnd
npm install pdfjs-dist
```

### Step 2: Add Extraction Service

```bash
# Create file:
src/app/core/services/cv-extractor.service.ts

# (Code provided in separate file)
```

### Step 3: Update Soft Skills Component

```bash
# Update file:
src/app/modules/evaluation/soft-skills/soft-skills.component.ts

# Key changes:
# - Inject CvExtractorService
# - Handle file upload locally
# - Extract text before sending to n8n
# - Send extracted_cv_text in payload
```

### Step 4: Update n8n Workflow

```bash
# In n8n UI:
1. Open "master soft skills agent" workflow
2. Go to "Code in JavaScript" node
3. Replace PDF parsing logic with:
   const cvText = $input.first().json.body.extracted_cv_text || '';
4. Remove all PDF Server calls
5. Save workflow
```

### Step 5: Test

```bash
1. Frontend: Select CV (PDF or TXT)
2. See extraction progress
3. Submit form
4. Verify n8n receives extracted_cv_text
5. Check merged scores in response
```

---

## 📝 Code Snippets

### Frontend Component Change

**Before:**

```typescript
// Form sends binary PDF to n8n
const formData = new FormData();
formData.append("cv_file", cvFile); // Binary
formData.append("full_name", name);
```

**After:**

```typescript
// Extract text first, then send to n8n
const extracted = await this.cvExtractorService.extractFromFile(cvFile);

const payload = {
  extracted_cv_text: extracted.text,  // Already extracted!
  full_name: name,
  email: email,
  q1: q1, ..., q18: q18,
  github_username: github
};

await this.softSkillsService.analyzeSoftSkills(payload);
```

### n8n Code Node Change

**Before:**

```javascript
// Complex PDF extraction
const pdfBuffer = Buffer.from(fileData.data, 'base64');
fs.writeFileSync('/tmp/cv.pdf', pdfBuffer);
const response = await http.post('localhost:3001/extract', {...});
```

**After:**

```javascript
// Simple: use extracted text
const cvText = $input.first().json.body.extracted_cv_text;

// That's it! Text is ready for analysis
return [{ json: { cv_text: cvText } }];
```

---

## ✅ Benefits of New Architecture

| Aspect                | Before                   | After                    |
| --------------------- | ------------------------ | ------------------------ |
| **PDF Parsing**       | Server-side (n8n)        | Client-side (Browser) ✅ |
| **File Transfer**     | Binary PDF (🔴 Large)    | Text only (🟢 Smaller)   |
| **Server Load**       | High (extract + analyze) | Low (analyze only) ✅    |
| **Latency**           | 1-2s for parsing         | 0s (already done) ✅     |
| **Scalability**       | Limited                  | Infinite ✅              |
| **Supported Formats** | PDF only                 | PDF + TXT ✅             |
| **Browser Privacy**   | Extract on server        | Extract locally ✅       |

---

## 🚀 Timeline

1. **Install pdfjs-dist** (5 min)
2. **Create extraction service** (10 min)
3. **Update component** (15 min)
4. **Update n8n workflow** (10 min)
5. **Test** (10 min)

**Total: ~50 minutes to full implementation**

---

## ❓ Questions / Issues?

- **"Can I still use existing PDF Server?"**
  - Yes, but won't be needed. Keep running if you want backward compatibility.

- **"What about large PDFs?"**
  - Works fine! Browser handles up to ~300MB PDFs with pdfjs.

- **"What about image-only PDFs?"**
  - Won't extract text (no OCR). User still uploads but gets "No text found".

- **"Backward compatible?"**
  - Old API still works. Create new API endpoint for new format.

---

## 📚 References

- **pdfjs-dist Docs:** https://mozilla.github.io/pdf.js/
- **Angular File Upload:** https://angular.io/guide/file-upload
- **n8n Webhooks:** https://docs.n8n.io/workflows/expressions/
