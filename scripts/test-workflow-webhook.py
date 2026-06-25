#!/usr/bin/env python3
import requests
import json
import sys
from time import time

# Prepare test payload WITH EXTRACTED TEXT (no binary, no PDF extraction needed)
test_payload = {
    "full_name": "Ahmed Khalifa",
    "email": "ahmed@example.com",
    "extracted_cv_text": """
    Senior Full-Stack Developer with 8 years of experience in enterprise software development.
    Specialized in JavaScript, TypeScript, Angular, Node.js, AWS, and Docker.
    
    EXPERIENCE:
    - Led team of 5 developers on microservices architecture project
    - Designed and implemented REST APIs serving 100k+ users
    - Mentored 3 junior developers, conducted code reviews
    - Strong communication with stakeholders, excellent presentation skills
    
    SKILLS:
    - Languages: JavaScript, TypeScript, Python, SQL
    - Frontend: Angular, React
    - Backend: Node.js, Express, NestJS
    - DevOps: Docker, Kubernetes, AWS, CI/CD
    - Databases: PostgreSQL, MongoDB, Redis
    
    PERSONAL TRAITS:
    - Highly disciplined and detail-oriented
    - Curious about new technologies, always learning
    - Team player, strong collaboration skills
    - Ownership mindset, takes initiative
    - Natural leader, enjoys mentoring
    """,
    "github_username": "ahmed-khalifa",
    # PCM Test Scores (1-5 scale)
    "q1": 5,   # communication 1
    "q2": 5,   # communication 2
    "q3": 4,   # communication 3
    "q4": 4,   # discipline 1
    "q5": 5,   # discipline 2
    "q6": 5,   # discipline 3
    "q7": 3,   # curiosity 1
    "q8": 4,   # curiosity 2
    "q9": 3,   # curiosity 3
    "q10": 5,  # collaboration 1
    "q11": 5,  # collaboration 2
    "q12": 4,  # collaboration 3
    "q13": 5,  # ownership 1
    "q14": 5,  # ownership 2
    "q15": 4,  # ownership 3
    "q16": 4,  # leadership 1
    "q17": 3,  # leadership 2
    "q18": 4   # leadership 3
}

# Test the webhook
print("🧪 Testing n8n webhook with EXTRACTED CV TEXT")
print(f"   📍 URL: http://localhost:5678/webhook/master-agent")
print(f"   📄 Name: {test_payload['full_name']}")
print(f"   📊 CV Text Length: {len(test_payload['extracted_cv_text'])} chars")
print(f"   🐙 GitHub: {test_payload['github_username']}")

try:
    start_time = time()
    response = requests.post(
        "http://localhost:5678/webhook/master-agent",
        json=test_payload,
        timeout=30
    )
    elapsed = time() - start_time
    
    print(f"\n✅ Webhook responded in {elapsed:.2f}s")
    print(f"   Status Code: {response.status_code}")
    
    if response.status_code in [200, 201]:
        try:
            result = response.json()
            print("\n📊 Response received:")
            
            # Pretty print the response
            if isinstance(result, dict):
                important_fields = [
                    'user_name', 'user_email', 'overall_score',
                    'personality_type', 'merged_soft_skills',
                    'top_3_strengths', 'top_3_weaknesses',
                    'extraction_source'
                ]
                
                for field in important_fields:
                    if field in result:
                        value = result[field]
                        if isinstance(value, dict):
                            print(f"   {field}:")
                            for k, v in value.items():
                                print(f"      {k}: {v}")
                        elif isinstance(value, list):
                            print(f"   {field}: {', '.join(str(x) for x in value)}")
                        else:
                            print(f"   {field}: {value}")
                
                print("\n✅ SUCCESS! Workflow processed extracted text correctly")
                print("   ✓ No PDF parsing needed")
                print("   ✓ Used extracted_cv_text directly")
                print("   ✓ Full soft skills analysis complete")
                
                # Save response
                with open('n8n-test-success-response.json', 'w') as f:
                    json.dump(result, f, indent=2)
                print("\n   Response saved to: n8n-test-success-response.json")
                
            sys.exit(0)
        except json.JSONDecodeError:
            print(f"\n   Response text: {response.text[:500]}")
            sys.exit(1)
    else:
        print(f"\n❌ Error: {response.status_code}")
        print(f"   Response: {response.text[:500]}")
        sys.exit(1)

except requests.exceptions.ConnectionError:
    print(f"\n❌ Connection Error: Could not reach n8n at localhost:5678")
    print("   Make sure n8n container is running:")
    print("   docker-compose -f docker-compose-n8n-only.yml up -d")
    sys.exit(1)

except requests.exceptions.Timeout:
    print(f"\n❌ Timeout: Webhook took more than 30 seconds")
    sys.exit(1)

except Exception as e:
    print(f"\n❌ Error: {e}")
    sys.exit(1)
