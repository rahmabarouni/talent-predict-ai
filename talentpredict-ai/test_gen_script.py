import asyncio
import logging
import sys

# Configure rich logging output
logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s")

from services.test_generator import generate_test

async def test():
    print("Starting generation test...")
    try:
        res = await generate_test(["Python", "Docker"], "EXPERT", "test-user")
        print("\nSUCCESS! RESULT:")
        print("Question Count:", res.get("question_count"))
        for q in res.get("questions", []):
            print(f"- [{q.get('skill')}] {q.get('question')[:60]}... (Correct: {q.get('correct')})")
    except Exception as e:
        print("\nERROR IN GENERATION:", e)
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    # Ensure event loop runs correctly
    asyncio.run(test())
