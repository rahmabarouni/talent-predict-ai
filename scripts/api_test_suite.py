import requests
import json
import uuid
import time
import sys

BASE_URL = "http://localhost:8081"
AI_URL = "http://localhost:8000"
CV_EXTRACTOR_URL = "http://localhost:9000"

def log_test(name, success, message="", details=None):
    status = "✅ PASS" if success else "❌ FAIL"
    print(f"[{status}] {name}: {message}")
    if not success and details:
        print(f"      Details: {details}")

class TalentPredictTester:
    def __init__(self):
        self.token = None
        self.user_id = None
        self.email = f"test_{uuid.uuid4().hex[:8]}@example.com"
        self.password = "Password123!"
        self.headers = {"Content-Type": "application/json"}

    def run_tests(self):
        print(f"🚀 Starting API Test Suite for TalentPredict")
        print(f"📧 Test Email: {self.email}")
        print("-" * 50)

        # 1. Health Checks
        self.test_backend_health()
        self.test_ai_health()
        self.test_cv_extractor_health()

        # 2. Auth
        if not self.test_register():
            print("🛑 Registration failed. Aborting further tests.")
            return

        # 3. User Profile
        self.test_get_profile()

        # 4. Skills
        self.test_skills()

        # 5. Personality Tests
        self.test_personality_tests()

        # 6. Formations
        self.test_formations()

        # 7. Predictions
        self.test_predictions()

        # 8. Dashboard
        self.test_dashboard()
        
        # 9. AI Service routes (POST)
        self.test_ai_routes()

        print("-" * 50)
        print("🏁 Test Suite Completed")

    def test_backend_health(self):
        try:
            resp = requests.get(f"{BASE_URL}/api/health", timeout=5)
            log_test("Backend Health", resp.status_code == 200, f"Status: {resp.status_code}")
        except Exception as e:
            log_test("Backend Health", False, str(e))

    def test_ai_health(self):
        try:
            resp = requests.get(f"{AI_URL}/health", timeout=5)
            log_test("AI Service Health", resp.status_code == 200, f"Response: {resp.json()}")
        except Exception as e:
            log_test("AI Service Health", False, str(e))

    def test_cv_extractor_health(self):
        try:
            resp = requests.get(f"{CV_EXTRACTOR_URL}/health", timeout=5)
            log_test("CV Extractor Health", resp.status_code == 200, f"Response: {resp.json()}")
        except Exception as e:
            log_test("CV Extractor Health", False, str(e))

    def test_register(self):
        payload = {
            "lastName": "Tester",
            "firstName": "API",
            "email": self.email,
            "password": self.password
        }
        try:
            resp = requests.post(f"{BASE_URL}/api/auth/register", json=payload)
            if resp.status_code == 201:
                data = resp.json()
                self.token = data.get("token")
                self.user_id = data.get("id")
                self.headers["Authorization"] = f"Bearer {self.token}"
                log_test("Registration", True, f"User ID: {self.user_id}")
                return True
            else:
                log_test("Registration", False, f"Status: {resp.status_code}", resp.text)
                return False
        except Exception as e:
            log_test("Registration", False, str(e))
            return False

    def test_get_profile(self):
        try:
            resp = requests.get(f"{BASE_URL}/api/users/{self.user_id}", headers=self.headers)
            log_test("Get Profile", resp.status_code == 200, f"Status: {resp.status_code}", resp.text if resp.status_code != 200 else None)
        except Exception as e:
            log_test("Get Profile", False, str(e))

    def test_skills(self):
        payload = {
            "nom": "Python",
            "type": "TECH",
            "niveau": 5,
            "description": "API Testing with Python"
        }
        try:
            resp = requests.post(f"{BASE_URL}/api/skills/accounts/{self.user_id}", json=payload, headers=self.headers)
            if resp.status_code == 201:
                log_test("Add Skill", True, f"Skill ID: {resp.json().get('id')}")
            else:
                log_test("Add Skill", False, f"Status: {resp.status_code}", resp.text)
 
            resp = requests.get(f"{BASE_URL}/api/skills/accounts/{self.user_id}", headers=self.headers)
            log_test("List Skills", resp.status_code == 200, f"Count: {len(resp.json()) if resp.status_code == 200 else 'N/A'}")
        except Exception as e:
            log_test("Skills Testing", False, str(e))
 
    def test_personality_tests(self):
        payload = {
            "fullName": "API Tester",
            "email": self.email,
            "githubUsername": "test-github",
            "q1": 5, "q2": 5, "q3": 5, "q4": 5, "q5": 5, "q6": 5, "q7": 5, "q8": 5, "q9": 5,
            "q10": 5, "q11": 5, "q12": 5, "q13": 5, "q14": 5, "q15": 5, "q16": 5, "q17": 5, "q18": 5
        }
        try:
            resp = requests.post(f"{BASE_URL}/api/soft-skills/analyze", json=payload, headers=self.headers)
            if resp.status_code == 200:
                log_test("Submit Soft Skills Analysis", True)
            else:
                log_test("Submit Soft Skills Analysis", False, f"Status: {resp.status_code}", resp.text)
  
            resp = requests.get(f"{BASE_URL}/api/soft-skills/last", headers=self.headers)
            log_test("Get Last Soft Skills Analysis", resp.status_code == 200)

            resp = requests.get(f"{BASE_URL}/api/soft-skills/progress", headers=self.headers)
            log_test("Get Soft Skills Progress", resp.status_code == 200)
        except Exception as e:
            log_test("Soft Skills Analysis Testing", False, str(e))
 
    def test_formations(self):
        payload = {
            "titre": "Mastering APIs",
            "description": "Advanced API testing course",
            "type": "TECH_SKILL",
            "duree": 10,
            "fournisseur": "Internal",
            "url": "http://example.com",
            "dateDebut": "2026-05-01T10:00:00"
        }
        try:
            resp = requests.post(f"{BASE_URL}/api/formations/utilisateur/{self.user_id}", json=payload, headers=self.headers)
            if resp.status_code == 201:
                log_test("Create Formation", True)
            else:
                log_test("Create Formation", False, f"Status: {resp.status_code}", resp.text)
 
            resp = requests.get(f"{BASE_URL}/api/formations/utilisateur/{self.user_id}", headers=self.headers)
            log_test("List Formations", resp.status_code == 200)
        except Exception as e:
            log_test("Formations Testing", False, str(e))

    def test_predictions(self):
        try:
            print("⏳ Generating prediction (may take a few seconds)...")
            resp = requests.post(f"{BASE_URL}/api/predictions/users/{self.user_id}/generer", headers=self.headers)
            log_test("Generate Prediction", resp.status_code in [201, 200], f"Status: {resp.status_code}", resp.text if resp.status_code not in [201, 200] else None)

            resp = requests.get(f"{BASE_URL}/api/predictions/users/{self.user_id}", headers=self.headers)
            log_test("List Predictions", resp.status_code == 200)
        except Exception as e:
            log_test("Predictions Testing", False, str(e))

    def test_dashboard(self):
        try:
            resp = requests.get(f"{BASE_URL}/api/dashboard/users/{self.user_id}", headers=self.headers)
            log_test("Get Dashboard", resp.status_code == 200, f"Status: {resp.status_code}", resp.text if resp.status_code != 200 else None)
        except Exception as e:
            log_test("Dashboard Testing", False, str(e))

    def test_ai_routes(self):
        try:
            # AI: Scenario Generate (POST)
            scen_payload = {
                "role": "Frontend Developer",
                "level": "Mid-Level"
            }
            resp = requests.post(f"{AI_URL}/api/test/scenario/generate", json=scen_payload, timeout=60)
            log_test("AI: Scenario Generate", resp.status_code == 200)
            
            # AI: Scenario Evaluate (POST)
            eval_payload = {
                "scenario": "You are leading a project and a team member is constantly late.",
                "response": "I would talk to them privately to understand the root cause and set clear expectations."
            }
            resp = requests.post(f"{AI_URL}/api/test/scenario/evaluate", json=eval_payload, timeout=60)
            log_test("AI: Scenario Evaluate", resp.status_code == 200)

            # AI: Career Prediction (POST)
            pred_payload = {
                "candidate_id": self.user_id,
                "full_name": "API Tester",
                "skills": ["Python", "Docker"],
                "test_results": [],
                "target_role": "Backend Engineer",
                "language": "en"
            }
            resp = requests.post(f"{AI_URL}/api/career/prediction", json=pred_payload, timeout=60)
            log_test("AI: Career Prediction", resp.status_code == 200)

            # AI: Learning Plan (POST)
            plan_payload = {
                "candidate_id": self.user_id,
                "target_role": "Backend Engineer",
                "experience_level": "intermediate",
                "weak_skills": [{"name": "Docker", "score": 25.0}],
                "language": "en"
            }
            resp = requests.post(f"{AI_URL}/api/career/learning-plan", json=plan_payload, timeout=180)
            log_test("AI: Learning Plan", resp.status_code == 200)
        except Exception as e:
            log_test("AI Service Direct Testing", False, str(e))

if __name__ == "__main__":
    tester = TalentPredictTester()
    tester.run_tests()
