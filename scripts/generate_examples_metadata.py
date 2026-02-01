import re
import json
import shutil
import difflib
from pathlib import Path
from lxml import etree
import requests
from bs4 import BeautifulSoup
from urllib.parse import urljoin
from download_worked_examples import get_all_scenario_links, sanitize_folder_name

# Config
# Config
BASE_URL = "https://standards.atlassian.net"
WORKED_EXAMPLES_URL = f"{BASE_URL}/wiki/spaces/EASD/pages/574586985/Worked+Examples"

# Determine project root based on script location
PROJECT_ROOT = Path(__file__).resolve().parent.parent

DOWNLOADS_DIR = PROJECT_ROOT / "worked_examples_downloads"
SITE_DATA_DIR = PROJECT_ROOT / "site/src/data"
SITE_PUBLIC_DIR = PROJECT_ROOT / "site/public/worked_examples"
MAPPING_FILE = SITE_DATA_DIR / "worked_examples.json"

# Version mapping from validate_worked_examples.py
VERSION_MAP = {
    "2134": "21.3.5",
    "2135": "21.3.5",
    "2136": "24.1",
    "243": "24.3",
    "244": "24.4",
    "245": "25.4",  # Added inferred mapping based on browsing
}

# Request headers
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
}

def extract_info(xml_path):
    try:
        tree = etree.parse(str(xml_path))
        root = tree.getroot()
        
        # Message name
        message_name = root.tag
        if '}' in message_name:
            message_name = message_name.split('}')[-1]
            
        # Version from schemaLocation
        version = None
        schema_location = root.get('{http://www.w3.org/2001/XMLSchema-instance}schemaLocation')
        if schema_location:
            match = re.search(r'xsd/(\d+)/', schema_location)
            if match:
                v_id = match.group(1)
                version = VERSION_MAP.get(v_id, v_id) # Use map or raw id
                
        return message_name, version
    except Exception as e:
        print(f"Error parsing {xml_path}: {e}")
        return None, None

def main():
    # Use the session-based fetch from download utility for consistency
    session = requests.Session()
    scenarios_list = get_all_scenario_links(session)
    
    # Map sanitized folder name -> URL (Single source of truth!)
    scenarios_map = {}
    with open("scenarios_dump.txt", "w") as f:
        for s in scenarios_list:
            folder_name = sanitize_folder_name(s['title'])
            scenarios_map[folder_name] = s['url']
            f.write(f"{s['title']} | {folder_name} | {s['url']}\n")

    SITE_DATA_DIR.mkdir(parents=True, exist_ok=True)
    SITE_PUBLIC_DIR.mkdir(parents=True, exist_ok=True)
    
    # Clear previous public files
    if SITE_PUBLIC_DIR.exists():
        shutil.rmtree(SITE_PUBLIC_DIR)
    SITE_PUBLIC_DIR.mkdir(parents=True, exist_ok=True)
    
    # Store results: message -> list of examples
    metadata = {}
    
    count = 0
    for scenario_dir in DOWNLOADS_DIR.iterdir():
        if not scenario_dir.is_dir():
            continue
            
        scenario_name = scenario_dir.name
        url = scenarios_map.get(scenario_name, "")
        
        if not url:
            print(f"WARNING: No URL found for scenario: '{scenario_name}'")
            
            # 1. Fuzzy match (handles broken truncation, stale names, typos)
            matches = difflib.get_close_matches(scenario_name, scenarios_map.keys(), n=1, cutoff=0.6)
            if matches:
                 match_name = matches[0]
                 url = scenarios_map[match_name]
                 print(f"  -> Recovered via FUZZY match: '{match_name}' (URL found)")
            else:
                # 2. Hard prefix Fallback (for very long prefixes)
                prefix = scenario_name[:50]
                for s_title, s_url in scenarios_map.items():
                    if s_title.startswith(prefix):
                        url = s_url
                        print(f"  -> Recovered via PREFIX match: '{s_title}'")
                        break
        
        for xml_file in scenario_dir.glob("*.xml"):
            msg_name, version = extract_info(xml_file)
            if not msg_name or not version:
                continue
                
            # Prepare destination
            target_filename = f"{msg_name}_{version}_{count}.xml"
            shutil.copy(xml_file, SITE_PUBLIC_DIR / target_filename)
            
            # Update metadata
            if msg_name not in metadata:
                metadata[msg_name] = []
                
            metadata[msg_name].append({
                "title": scenario_name,
                "file": xml_file.name,
                "url": url,
                "original_version": version,
                "path": f"/worked_examples/{target_filename}"
            })
            count += 1
            
    with open(MAPPING_FILE, 'w') as f:
        json.dump(metadata, f, indent=2)
        
    print(f"Generated metadata for {count} examples in {MAPPING_FILE}")

if __name__ == "__main__":
    main()
