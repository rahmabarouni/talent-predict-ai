#!/usr/bin/env python3
import json
import sys

try:
    with open('n8n-workflows-import/master soft skills agent.json', 'r', encoding='utf-8') as f:
        workflow = json.load(f)
        
    print("✅ Workflow JSON is VALID and properly formatted")
    print(f"   Workflow ID: {workflow.get('id', 'N/A')}")
    print(f"   Workflow Name: {workflow['name']}")
    print(f"   Number of Nodes: {len(workflow['nodes'])}")
    print(f"   Active: {workflow.get('active', False)}")
    
    print("\n📋 Nodes in workflow:")
    for i, node in enumerate(workflow['nodes'], 1):
        print(f"   {i}. {node['name']} ({node['type']})")
        if node['type'] == 'n8n-nodes-base.code':
            code_length = len(node['parameters'].get('jsCode', ''))
            print(f"      ↳ Code length: {code_length} chars")
            # Check if simplified code (without PDF Server calls)
            if "localhost:3001" in node['parameters'].get('jsCode', ''):
                print(f"      ⚠️ WARNING: Still contains PDF Server calls!")
            elif "extracted_cv_text" in node['parameters'].get('jsCode', ''):
                print(f"      ✅ Uses extracted_cv_text (simplified!)")
    
    print("\n📊 Connections check:")
    for source, targets in workflow.get('connections', {}).items():
        print(f"   {source} ->", end='')
        for connection_list in targets.get('main', []):
            for conn in connection_list:
                print(f" {conn['node']}", end='')
        print()
    
    sys.exit(0)
    
except json.JSONDecodeError as e:
    print(f"❌ JSON Parse Error: {e}")
    sys.exit(1)
except Exception as e:
    print(f"❌ Error: {e}")
    sys.exit(1)
