import urllib.request
import json
import time
import concurrent.futures

def fetch_llm(prompt_id, prompt_text):
    ollama_url = "http://localhost:11434/api/generate"
    payload = {
        "model": "llama3.2:latest",
        "prompt": prompt_text,
        "stream": False
    }
    
    start = time.perf_counter()
    print(f"[{time.strftime('%X')}] 🚀 Task {prompt_id} started...")
    
    req = urllib.request.Request(
        ollama_url,
        data=json.dumps(payload).encode('utf-8'),
        headers={"Content-Type": "application/json"}
    )
    
    try:
        with urllib.request.urlopen(req, timeout=60.0) as response:
            res_body = response.read().decode('utf-8')
            elapsed = time.perf_counter() - start
            print(f"[{time.strftime('%X')}] ✅ Task {prompt_id} completed in {elapsed:.2f} seconds")
            return elapsed
    except Exception as e:
        print(f"[{time.strftime('%X')}] ❌ Task {prompt_id} failed: {e}")
        return -1

def main():
    print("Starting Concurrent LLM Test (Testing OLLAMA_NUM_PARALLEL=2)...")
    tasks = [
        (1, "Rédige un poème de 4 lignes sur le code propre."),
        (2, "Explique le concept d'architecture microservice en 3 mots.")
    ]
    
    start_total = time.perf_counter()
    
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        # Fire off two requests at the exact same time
        futures = [executor.submit(fetch_llm, t[0], t[1]) for t in tasks]
        results = [f.result() for f in futures]
        
    total_time = time.perf_counter() - start_total
    
    print(f"\nResults: Task 1 = {results[0]:.2f}s, Task 2 = {results[1]:.2f}s")
    print(f"Total Execution Time: {total_time:.2f}s")
    
    # If they run in sequence, total time > task 1 + task 2
    # If they run in parallel, total time is roughly max(task 1, task 2)
    if total_time >= (results[0] + results[1]) * 0.8: # Allowing some overhead margin
         print("⚠️  Warning: The total time is close to the sum of both tasks. The second task was likely queued.")
    else:
         print("🎯 Success: Total time is less than the sum of tasks, indicating true parallel execution!")

if __name__ == "__main__":
    main()
