import json
import os
import subprocess

def main():
    json_path = 'iata_ndc_messages.json'
    raw_dir = 'raw_ndc_schemas'
    output_base_dir = 'ndc_schemas'

    if not os.path.exists(json_path):
        print(f"Error: {json_path} not found.")
        return

    with open(json_path, 'r') as f:
        data = json.load(f)

    versions = data.get('versions', {}).keys()
    
    # Get all subdirectories in raw_ndc_schemas
    raw_folders = [d for d in os.listdir(raw_dir) if os.path.isdir(os.path.join(raw_dir, d))]

    for version in versions:
        # Find matching folder: either exact match or starts with version + "." or starts with version + "_"
        match = None
        for folder in raw_folders:
            if folder == version or folder.startswith(version + '.') or folder.startswith(version + '_'):
                match = folder
                break
        
        if not match:
            print(f"Skipping version {version}: No matching directory found in {raw_dir}")
            continue

        input_dir = os.path.join(raw_dir, match)
        output_dir = os.path.join(output_base_dir, version)

        print(f"--- Processing version {version} (from {match}) ---")
        
        # Use the current script's directory to find the sibling script
        script_dir = os.path.dirname(os.path.abspath(__file__))
        flatten_script = os.path.join(script_dir, 'flatten_ndc_schemas.py')
        
        cmd = [
            'python3', flatten_script,
            '--input-dir', input_dir,
            '--output-dir', output_dir,
            '--message-list', json_path,
            '--version', version
        ]
        
        try:
            subprocess.run(cmd, check=True)
            print(f"Successfully flattened schemas for version {version} into {output_dir}")
        except subprocess.CalledProcessError as e:
            print(f"Error processing version {version}: {e}")

if __name__ == '__main__':
    main()
