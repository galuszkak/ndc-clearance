#!/usr/bin/env python3
"""
Script to download all Sample Messages from IATA NDC Worked Examples.
Each scenario's messages are saved to a separate folder.
"""

import os
import re
import time
import json
import shutil
import zipfile
import requests
from bs4 import BeautifulSoup
from lxml import etree
from urllib.parse import urljoin, unquote
from pathlib import Path

# Base URL for IATA standards wiki
BASE_URL = "https://standards.atlassian.net"
WORKED_EXAMPLES_URL = f"{BASE_URL}/wiki/spaces/EASD/pages/574586985/Worked+Examples"

# Output directories
# Determine project root based on script location (one level up from scripts/)
PROJECT_ROOT = Path(__file__).resolve().parent.parent

# Output directories
DOWNLOADS_DIR = PROJECT_ROOT / "worked_examples_downloads" # Keep local cache
SITE_DATA_DIR = PROJECT_ROOT / "site/src/data"
SITE_PUBLIC_DIR = PROJECT_ROOT / "site/public/worked_examples"
MAPPING_FILE = SITE_DATA_DIR / "worked_examples.json"

# Version mapping
VERSION_MAP = {
    "2134": "21.3.5",
    "2135": "21.3.5",
    "2136": "24.1",
    "243": "24.3",
    "244": "24.4",
    "245": "25.4",
}

# Request headers to mimic a browser
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.5",
}


def get_page_content(url: str, session: requests.Session) -> str:
    """Fetch page content with retries."""
    for attempt in range(3):
        try:
            response = session.get(url, headers=HEADERS, timeout=30)
            response.raise_for_status()
            return response.text
        except requests.RequestException as e:
            print(f"  Attempt {attempt + 1} failed: {e}")
            if attempt < 2:
                time.sleep(2)
    return ""



def extract_info(xml_path):
    """Extract message name and version from XML file."""
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


def extract_page_id_from_url(url: str) -> str:
    """Extract page ID from Confluence URL."""
    # URL pattern: /wiki/spaces/EASD/pages/600375364/...
    match = re.search(r'/pages/(\d+)/', url)
    if match:
        return match.group(1)
    return ""


def get_all_scenario_links(session: requests.Session) -> list[dict]:
    """Get all scenario page links from the Worked Examples page."""
    print("Fetching main Worked Examples page...")
    content = get_page_content(WORKED_EXAMPLES_URL, session)
    
    if not content:
        print("Failed to fetch main page!")
        return []
    
    soup = BeautifulSoup(content, 'html.parser')
    scenarios = []
    
    # Find all links within the content area
    # Links to scenario pages contain /pages/ in the URL
    for link in soup.find_all('a', href=True):
        href = link['href']
        
        # Filter for links to pages within EASD space
        if '/wiki/spaces/EASD/pages/' in href and 'Worked+Examples' not in href:
            full_url = urljoin(BASE_URL, href)
            title = link.get_text(strip=True)
            
            # Skip category pages (Offer Worked Examples, Order Worked Examples, etc.)
            if title and 'Worked Examples' not in title:
                page_id = extract_page_id_from_url(full_url)
                if page_id and not any(s['page_id'] == page_id for s in scenarios):
                    scenarios.append({
                        'title': title,
                        'url': full_url,
                        'page_id': page_id
                    })
    
    print(f"Found {len(scenarios)} scenario pages")
    return scenarios


def sanitize_folder_name(name: str) -> str:
    """Convert a title to a valid folder name."""
    # Remove or replace invalid characters
    name = re.sub(r'[<>:"/\\|?*]', '', name)
    # Replace multiple spaces with single space
    name = re.sub(r'\s+', ' ', name)
    # Truncate if too long
    return name[:100].strip()


def download_attachments(scenario: dict, session: requests.Session, metadata: dict, start_count: int) -> int:
    """Download all attachments for a scenario page and update metadata."""
    page_id = scenario['page_id']
    title = scenario['title']
    url = scenario['url']
    
    # Create folder for this scenario
    folder_name = sanitize_folder_name(title)
    folder_path = DOWNLOADS_DIR / folder_name
    folder_path.mkdir(parents=True, exist_ok=True)
    
    # Try to download all attachments as a zip
    download_all_url = f"{BASE_URL}/wiki/download/all_attachments?pageId={page_id}"
    
    current_count = start_count
    downloaded_files = 0
    
    try:
        response = session.get(download_all_url, headers=HEADERS, timeout=60, stream=True)
        
        if response.status_code == 200:
            content_type = response.headers.get('Content-Type', '')
            
            # Check if it's a zip file
            if 'zip' in content_type or 'octet-stream' in content_type:
                zip_path = folder_path / "attachments.zip"
                
                with open(zip_path, 'wb') as f:
                    for chunk in response.iter_content(chunk_size=8192):
                        f.write(chunk)
                
                # Extract the zip file
                try:
                    with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                        zip_ref.extractall(folder_path)
                    
                    # Remove the zip file after extraction
                    zip_path.unlink()
                    
                    # Process extracted files
                    for xml_file in folder_path.glob("*.xml"):
                        msg_name, version = extract_info(xml_file)
                        if not msg_name or not version:
                            continue
                            
                        # Prepare destination
                        target_filename = f"{msg_name}_{version}_{current_count}.xml"
                        shutil.copy(xml_file, SITE_PUBLIC_DIR / target_filename)
                        
                        # Update metadata
                        if msg_name not in metadata:
                            metadata[msg_name] = []
                            
                        metadata[msg_name].append({
                            "title": title,
                            "file": xml_file.name,
                            "url": url,
                            "original_version": version,
                            "path": f"/worked_examples/{target_filename}"
                        })
                        current_count += 1
                        downloaded_files += 1

                    return downloaded_files
                except zipfile.BadZipFile:
                    print(f"    Warning: Downloaded file is not a valid zip")
                    zip_path.unlink()
            else:
                # Not a zip, might be a single file or error page
                # Try alternative approach: scrape page for attachment links
                return download_attachments_individually(scenario, session, folder_path, metadata, start_count)
        else:
            print(f"    Download all returned status {response.status_code}")
            return download_attachments_individually(scenario, session, folder_path, metadata, start_count)
            
    except requests.RequestException as e:
        print(f"    Error downloading: {e}")
        return download_attachments_individually(scenario, session, folder_path, metadata, start_count)
    
    return 0


def download_attachments_individually(scenario: dict, session: requests.Session, folder_path: Path, metadata: dict, start_count: int) -> int:
    """Download attachments individually by parsing the page and update metadata."""
    page_url = scenario['url']
    title = scenario['title']
    url = scenario['url']
    
    content = get_page_content(page_url, session)
    if not content:
        return 0
    
    soup = BeautifulSoup(content, 'html.parser')
    downloaded = 0
    current_count = start_count
    
    # Look for attachment links (usually .xml files)
    for link in soup.find_all('a', href=True):
        href = link['href']
        
        # Confluence attachment URLs contain /attachments/ or /download/attachments/
        if '/attachments/' in href or '/download/' in href:
            if href.endswith('.xml') or '.xml' in href:
                full_url = urljoin(BASE_URL, href)
                
                # Extract filename from URL or link text
                filename = link.get_text(strip=True)
                if not filename.endswith('.xml'):
                    # Try to extract from URL
                    filename = unquote(href.split('/')[-1].split('?')[0])
                
                if filename:
                    try:
                        file_response = session.get(full_url, headers=HEADERS, timeout=30)
                        if file_response.status_code == 200:
                            file_path = folder_path / sanitize_folder_name(filename)
                            with open(file_path, 'wb') as f:
                                f.write(file_response.content)
                                
                            # Process file immediately
                            msg_name, version = extract_info(file_path)
                            if msg_name and version:
                                # Prepare destination
                                target_filename = f"{msg_name}_{version}_{current_count}.xml"
                                shutil.copy(file_path, SITE_PUBLIC_DIR / target_filename)
                                
                                # Update metadata
                                if msg_name not in metadata:
                                    metadata[msg_name] = []
                                    
                                metadata[msg_name].append({
                                    "title": title,
                                    "file": file_path.name,
                                    "url": url,
                                    "original_version": version,
                                    "path": f"/worked_examples/{target_filename}"
                                })
                                current_count += 1
                                downloaded += 1
                                
                    except requests.RequestException as e:
                        print(f"      Failed to download {filename}: {e}")
    
    return downloaded


def main():
    """Main function to orchestrate the download process."""
    print("=" * 60)
    print("IATA NDC Worked Examples Sample Messages Downloader")
    print("=" * 60)
    print()
    
    # Create output directories
    DOWNLOADS_DIR.mkdir(exist_ok=True)
    SITE_DATA_DIR.mkdir(parents=True, exist_ok=True)
    
    # Clear and recreate public directory
    if SITE_PUBLIC_DIR.exists():
        shutil.rmtree(SITE_PUBLIC_DIR)
    SITE_PUBLIC_DIR.mkdir(parents=True, exist_ok=True)
    
    # Create a session for persistent connections
    session = requests.Session()
    
    # Get all scenario links
    scenarios = get_all_scenario_links(session)
    
    if not scenarios:
        print("No scenarios found!")
        return
    
    print()
    print("Starting downloads...")
    print("-" * 60)
    
    total_files = 0
    successful_scenarios = 0
    metadata = {}
    
    for i, scenario in enumerate(scenarios, 1):
        print(f"\n[{i}/{len(scenarios)}] {scenario['title']}")
        print(f"  Page ID: {scenario['page_id']}")
        
        files_saved = download_attachments(scenario, session, metadata, total_files)
        
        if files_saved > 0:
            print(f"  ✓ Processed {files_saved} file(s)")
            total_files += files_saved
            successful_scenarios += 1
        else:
            print(f"  ⚠ No new xml files processed")
        
        # Be polite to the server
        time.sleep(1)
        
    # Save metadata
    with open(MAPPING_FILE, 'w') as f:
        json.dump(metadata, f, indent=2)
    
    print()
    print("=" * 60)
    print("Download Summary")
    print("=" * 60)
    print(f"Total scenarios processed: {len(scenarios)}")
    print(f"Scenarios with downloads: {successful_scenarios}")
    print(f"Total files processed: {total_files}")
    print(f"Output directory: {DOWNLOADS_DIR.absolute()}")
    print(f"Public directory: {SITE_PUBLIC_DIR.absolute()}")
    print(f"Metadata file: {MAPPING_FILE.absolute()}")
    print()


if __name__ == "__main__":
    main()
