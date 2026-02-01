#!/usr/bin/env python3
"""
Script to validate IATA NDC Worked Examples XML files against raw XSD schemas using xmllint.
Produces a table showing validation results.
"""

import os
import re
import subprocess
import sys
from pathlib import Path
from collections import defaultdict

# Directories
PROJECT_ROOT = Path(__file__).resolve().parent.parent
WORKED_EXAMPLES_DIR = PROJECT_ROOT / "worked_examples_downloads"
RAW_SCHEMAS_DIR = PROJECT_ROOT / "raw_ndc_schemas"

# Version mapping: schema ID in URL -> raw schema folder name
VERSION_MAP = {
    "2134": "21.3.5_ndc",
    "2135": "21.3.5_ndc",
    "2136": "24.1_ndc",
    "243": "24.3_ndc",
    "244": "24.4_ndc",
}


def extract_schema_info(xml_path: Path) -> tuple[str | None, str | None]:
    """
    Extract schema version and schema filename from XML file using grep.
    Returns (version_id, schema_filename) or (None, None) if not found.
    """
    try:
        # Use grep to find schemaLocation - faster than parsing XML
        result = subprocess.run(
            ["grep", "-o", r'xsd/[0-9]*/[A-Za-z_]*\.xsd', str(xml_path)],
            capture_output=True,
            text=True,
            timeout=5
        )
        
        if result.returncode == 0 and result.stdout.strip():
            # Pattern: xsd/2134/IATA_AirShoppingRQ.xsd
            match = re.search(r'xsd/(\d+)/(\w+\.xsd)', result.stdout.strip())
            if match:
                return match.group(1), match.group(2)
    except Exception as e:
        print(f"  Error extracting schema info from {xml_path.name}: {e}")
    
    return None, None


def validate_xml_with_xmllint(xml_path: Path, schema_path: Path) -> tuple[bool, str]:
    """
    Validate XML file against XSD schema using xmllint.
    Returns (is_valid, error_message).
    """
    try:
        result = subprocess.run(
            ["xmllint", "--noout", "--schema", str(schema_path), str(xml_path)],
            capture_output=True,
            text=True,
            timeout=30
        )
        
        if result.returncode == 0:
            return True, ""
        else:
            # xmllint outputs errors to stderr
            error = result.stderr.strip()
            # Get first meaningful error line
            lines = [l for l in error.split('\n') if l and 'validates' not in l.lower()]
            if lines:
                return False, lines[0][:200]
            return False, error[:200]
    
    except subprocess.TimeoutExpired:
        return False, "Timeout during validation"
    except Exception as e:
        return False, f"Error: {e}"


def check_xml_wellformed(xml_path: Path) -> tuple[bool, str]:
    """Check if XML is well-formed using xmllint."""
    try:
        result = subprocess.run(
            ["xmllint", "--noout", str(xml_path)],
            capture_output=True,
            text=True,
            timeout=10
        )
        
        if result.returncode == 0:
            return True, ""
        else:
            return False, result.stderr.strip()[:200]
    except Exception as e:
        return False, str(e)


def main():
    print("=" * 100)
    print("IATA NDC Worked Examples - Schema Validation Report (using xmllint)")
    print("=" * 100)
    print()
    
    # Check directories exist
    if not WORKED_EXAMPLES_DIR.exists():
        print(f"Error: {WORKED_EXAMPLES_DIR} not found. Run download script first.")
        sys.exit(1)
    
    if not RAW_SCHEMAS_DIR.exists():
        print(f"Error: {RAW_SCHEMAS_DIR} not found.")
        sys.exit(1)
    
    # Collect all XML files
    xml_files = sorted(WORKED_EXAMPLES_DIR.rglob("*.xml"))
    print(f"Found {len(xml_files)} XML files to validate")
    print()
    
    # Results storage
    results = []
    version_stats = defaultdict(lambda: {"total": 0, "valid": 0, "invalid": 0, "no_schema": 0})
    
    # Process each XML file
    for xml_path in xml_files:
        scenario = xml_path.parent.name
        filename = xml_path.name
        
        # First check if XML is well-formed
        is_wellformed, wellformed_error = check_xml_wellformed(xml_path)
        if not is_wellformed:
            results.append({
                "scenario": scenario,
                "file": filename,
                "version": "N/A",
                "schema_folder": "N/A",
                "status": "MALFORMED_XML",
                "error": wellformed_error
            })
            version_stats["malformed"]["total"] += 1
            version_stats["malformed"]["invalid"] += 1
            continue
        
        # Extract schema info
        version_id, schema_filename = extract_schema_info(xml_path)
        
        if not version_id or not schema_filename:
            results.append({
                "scenario": scenario,
                "file": filename,
                "version": "N/A",
                "schema_folder": "N/A",
                "status": "NO_SCHEMA_REF",
                "error": "Could not extract schema reference from XML"
            })
            version_stats["unknown"]["total"] += 1
            version_stats["unknown"]["no_schema"] += 1
            continue
        
        # Map version to schema folder
        schema_folder = VERSION_MAP.get(version_id)
        if not schema_folder:
            results.append({
                "scenario": scenario,
                "file": filename,
                "version": version_id,
                "schema_folder": "UNMAPPED",
                "status": "UNMAPPED_VERSION",
                "error": f"Version {version_id} not in VERSION_MAP"
            })
            version_stats[version_id]["total"] += 1
            version_stats[version_id]["no_schema"] += 1
            continue
        
        # Find schema file
        schema_path = RAW_SCHEMAS_DIR / schema_folder / schema_filename
        if not schema_path.exists():
            results.append({
                "scenario": scenario,
                "file": filename,
                "version": version_id,
                "schema_folder": schema_folder,
                "status": "SCHEMA_NOT_FOUND",
                "error": f"Schema file not found: {schema_path}"
            })
            version_stats[version_id]["total"] += 1
            version_stats[version_id]["no_schema"] += 1
            continue
        
        # Validate with xmllint
        is_valid, error_msg = validate_xml_with_xmllint(xml_path, schema_path)
        
        version_stats[version_id]["total"] += 1
        if is_valid:
            version_stats[version_id]["valid"] += 1
            status = "VALID"
        else:
            version_stats[version_id]["invalid"] += 1
            status = "INVALID"
        
        results.append({
            "scenario": scenario,
            "file": filename,
            "version": version_id,
            "schema_folder": schema_folder,
            "status": status,
            "error": error_msg if not is_valid else ""
        })
    
    # Print summary by version
    print("=" * 100)
    print("SUMMARY BY SCHEMA VERSION")
    print("=" * 100)
    print(f"{'Version ID':<12} {'Schema Folder':<20} {'Total':<8} {'Valid':<8} {'Invalid':<8} {'No Schema':<10}")
    print("-" * 100)
    
    for version_id in sorted(version_stats.keys()):
        stats = version_stats[version_id]
        schema_folder = VERSION_MAP.get(version_id, "N/A")
        print(f"{version_id:<12} {schema_folder:<20} {stats['total']:<8} {stats['valid']:<8} {stats['invalid']:<8} {stats['no_schema']:<10}")
    
    # Totals
    total_files = sum(s["total"] for s in version_stats.values())
    total_valid = sum(s["valid"] for s in version_stats.values())
    total_invalid = sum(s["invalid"] for s in version_stats.values())
    total_no_schema = sum(s["no_schema"] for s in version_stats.values())
    
    print("-" * 100)
    print(f"{'TOTAL':<12} {'':<20} {total_files:<8} {total_valid:<8} {total_invalid:<8} {total_no_schema:<10}")
    print()
    
    # Print detailed results - only failures
    failed_results = [r for r in results if r["status"] not in ("VALID",)]
    
    if failed_results:
        print("=" * 100)
        print("VALIDATION FAILURES")
        print("=" * 100)
        print()
        
        # Group by scenario
        by_scenario = defaultdict(list)
        for r in failed_results:
            by_scenario[r["scenario"]].append(r)
        
        for scenario in sorted(by_scenario.keys()):
            print(f"📁 {scenario}")
            for r in by_scenario[scenario]:
                print(f"   ❌ {r['file']}")
                print(f"      Version: {r['version']} -> {r['schema_folder']}")
                print(f"      Status: {r['status']}")
                if r['error']:
                    print(f"      Error: {r['error']}")
                print()
    else:
        print("✅ All files validated successfully!")
    
    # Print full table
    print()
    print("=" * 100)
    print("FULL VALIDATION TABLE")
    print("=" * 100)
    print(f"{'Status':<15} {'Version':<8} {'Schema':<15} {'File':<55}")
    print("-" * 100)
    
    for r in results:
        status_icon = "✅" if r["status"] == "VALID" else "❌"
        file_display = r["file"][:50] + "..." if len(r["file"]) > 50 else r["file"]
        print(f"{status_icon} {r['status']:<13} {r['version']:<8} {r['schema_folder']:<15} {file_display}")
    
    print()
    print("=" * 100)
    print(f"Total: {total_files} files | Valid: {total_valid} | Invalid: {total_invalid} | No Schema: {total_no_schema}")
    print("=" * 100)
    
    # Return exit code based on results
    return 0 if total_invalid == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
