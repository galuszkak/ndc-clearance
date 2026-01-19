import argparse
import json
import os
import xml.etree.ElementTree as ET
from collections import deque
import xml.dom.minidom

# Namespaces commonly used in XSD
XSD_NS = "http://www.w3.org/2001/XMLSchema"
ET.register_namespace("xs", XSD_NS)

def get_qname(tag):
    if '}' in tag:
        return tag.split('}', 1)[1]
    return tag

def get_namespace(tag):
    if '}' in tag:
        return tag.split('}', 1)[0][1:]
    return ""

def split_qname(text):
    if ':' in text:
        return text.split(':', 1)
    return None, text

class SchemaFlattener:
    def __init__(self, source_dir):
        self.source_dir = source_dir
        self.schemas = {} # path -> key info (root, target_ns, prefix_map, global_defs)
        self.global_definitions = {} # (ns, name) -> element

    def load_schema(self, filename):
        path = os.path.join(self.source_dir, filename)
        path = os.path.abspath(path)
        
        if path in self.schemas:
            return self.schemas[path]

        try:
            tree = ET.parse(path)
            root = tree.getroot()
        except Exception as e:
            print(f"Error loading {path}: {e}")
            return None

        # Extract targetNamespace
        target_ns = root.get('targetNamespace', '')
        
        # Extract namespace prefix mappings declared on this element
        # ElementTree hides xmlns attributes, but we can inspect the file or root
        # Actually, for resolving type="prefix:Name", we need the map.
        # We can reconstruct a simple map from the root's current scope hopefully, 
        # or parse the file manually for xmlns attributes if needed. 
        # ET handles namespaces by expanding them. Accessing original prefixes is hard.
        # However, attribute values like type="cns:ErrorType" are just strings.
        # We need to know what 'cns' maps to in THIS file.
        
        prefix_map = {}
        for event, (prefix, uri) in ET.iterparse(path, events=['start-ns']):
            prefix_map[prefix] = uri
        
        # Collect global definitions (complexType, simpleType, element, group, attributeGroup)
        defs = {}
        for child in root:
            tag_local = get_qname(child.tag)
            name = child.get('name')
            if name and tag_local in ('complexType', 'simpleType', 'element', 'group', 'attributeGroup'):
                defs[(target_ns, name)] = child

        schema_info = {
            'root': root,
            'target_ns': target_ns,
            'prefix_map': prefix_map,
            'definitions': defs,
            'imports': []
        }
        self.schemas[path] = schema_info
        self.global_definitions.update(defs)

        # Handle imports/includes recursively
        for child in root:
            tag_local = get_qname(child.tag)
            if tag_local in ('import', 'include'):
                loc = child.get('schemaLocation')
                if loc:
                    self.load_schema(loc)
                    schema_info['imports'].append(os.path.abspath(os.path.join(self.source_dir, loc)))

        return schema_info

    def resolve_reference(self, value, current_ns, prefix_map):
        """Resolves a string like 'cns:Type' to (namespace, local_name)"""
        if not value:
            return None
        
        prefix, local_name = split_qname(value)
        if prefix:
            ns = prefix_map.get(prefix)
            if ns:
                return (ns, local_name)
            # Check standard XS prefix just in case it wasn't captured or is implicit
            if prefix == 'xs': 
                return (XSD_NS, local_name)
            
            # Fallback: maybe the prefix IS the standard prefixes used in these files
            # For IATA 21.3, likely 'cns' is common types. 
            return (None, local_name) 
        else:
            # No prefix, usually means targetNamespace
            return (current_ns, local_name)

    def find_used_definitions(self, main_path):
        """Performs tree shaking starting from the main schema's global elements."""
        main_schema = self.schemas[main_path]
        used_keys = set()
        queue = deque()

        # Start with all global elements in the MAIN schema (the message itself)
        # e.g. IATA_OrderViewRS
        for (ns, name), el in main_schema['definitions'].items():
            if ns == main_schema['target_ns']:
                # Only root elements of THIS file are the entry points
                # (OR all globals in this file? typically the main file has one root element)
                # But let's assume everything defined in the main file is "used"
                queue.append((ns, name))
                used_keys.add((ns, name))

        while queue:
            current_key = queue.popleft()
            if current_key not in self.global_definitions:
                continue
            
            element = self.global_definitions[current_key]
            
            # Find the schema context where this element was defined 
            # (to resolve its prefixes correctly)
            # This is slow, so optimization: store origin in global_definitions or loop
            origin_schema = None
            for path, s in self.schemas.items():
                if current_key in s['definitions'] and s['definitions'][current_key] == element:
                    origin_schema = s
                    break
            
            if not origin_schema:
                continue

            # Traverse attributes and children for refs
            # We look for: type=, base=, ref=, itemType=, substitutionGroup=
            
            def check_ref(value):
                ref_key = self.resolve_reference(value, origin_schema['target_ns'], origin_schema['prefix_map'])
                # Only recursively add if it's not standard XSD and we define it
                if ref_key and ref_key[0] != XSD_NS:
                    # If we can't find the NS, we might skip it or warn.
                    # But for now, try to find it.
                    if ref_key in self.global_definitions:
                        if ref_key not in used_keys:
                            used_keys.add(ref_key)
                            queue.append(ref_key)

            # Check attributes of the element itself
            for attr in ['type', 'base', 'ref', 'itemType', 'substitutionGroup']:
                if element.get(attr):
                    check_ref(element.get(attr))

            # Recurse into children elements
            for child in element.iter():
                for attr in ['type', 'base', 'ref', 'itemType', 'substitutionGroup']:
                    if child.get(attr):
                        check_ref(child.get(attr))
        
        return used_keys

    def flatten_message(self, message_filename, output_path):
        main_info = self.load_schema(message_filename)
        if not main_info:
            return

        used_keys = self.find_used_definitions(os.path.abspath(os.path.join(self.source_dir, message_filename)))
        
        # Create new root
        new_root = ET.Element(f'{{{XSD_NS}}}schema')
        new_root.set('targetNamespace', main_info['target_ns'])
        new_root.set('elementFormDefault', 'qualified')
        new_root.set('version', main_info['root'].get('version', '1.0'))
        
        # Add xmlns explicitly if needed, but ElementTree handles serialization usually.
        # We want to bind the default namespace to targetNamespace?
        # Or Just use a prefix.
        
        # Iterate and add used definitions
        # Sort for deterministic output
        sorted_keys = sorted(list(used_keys), key=lambda x: x[1])
        
        # Helper to rewrite prefixes in the element tree
        def rewrite_prefixes(elem):
            # If an attribute references a type in our 'used_keys', we strip the prefix
            # assuming we merge everything to default/same namespace.
            # OR we ensure the type name is local.
            
            for attr in ['type', 'base', 'ref', 'itemType']:
                val = elem.get(attr)
                if val:
                    prefix, local = split_qname(val)
                    # If it's xsd, leave it
                    if prefix == 'xs' or prefix == 'xsd': 
                        continue
                    
                    # If we found this type in our used list (regardless of original NS),
                    # we change it to local name reference.
                    # Note: This assumes no name collisions across merged namespaces.
                    # Given IATA structure, this is generally safe (unique type names).
                    elem.set(attr, local)
            
            # Also handle substitutionGroup?
            
            # Recurse
            for child in elem:
                rewrite_prefixes(child)

        for key in sorted_keys:
            original_el = self.global_definitions[key]
            # strict copy to avoid modifying original?
            # deepcopy is safer
            import copy
            new_el = copy.deepcopy(original_el)
            
            # Rewrite references inside this element
            rewrite_prefixes(new_el)
            
            # Append to new root
            new_root.append(new_el)

        # Write to string
        ET.register_namespace('', main_info['target_ns'])
        xml_str = ET.tostring(new_root, encoding='utf-8')
        
        # Pretty print with minidom
        dom = xml.dom.minidom.parseString(xml_str)
        pretty_xml = dom.toprettyxml(indent="  ")
        
        # Remove empty lines
        pretty_xml = "\n".join([line for line in pretty_xml.split('\n') if line.strip()])
        
        with open(output_path, 'w') as f:
            f.write(pretty_xml)
        print(f"Generated {output_path}")

def main():
    parser = argparse.ArgumentParser(description='Flatten NDC Schemas')
    parser.add_argument('--input-dir', required=True, help='Path to schema directory')
    parser.add_argument('--output-dir', required=True, help='Path to output directory')
    parser.add_argument('--message-list', help='JSON file containing message list or comma separated list')
    parser.add_argument('--version', help='Version key for JSON (e.g. 21.3.5)')

    args = parser.parse_args()

    if not os.path.exists(args.output_dir):
        os.makedirs(args.output_dir)

    target_files = []
    if args.message_list:
        if args.message_list.endswith('.json'):
            with open(args.message_list, 'r') as f:
                data = json.load(f)
                if args.version and 'versions' in data and args.version in data['versions']:
                    target_files = [m + ".xsd" for m in data['versions'][args.version]]
                else:
                    # Fallback or error
                    print("Version not found or invalid JSON structure")
                    return
        else:
            target_files = [m.strip() + ".xsd" if not m.endswith('.xsd') else m.strip() for m in args.message_list.split(',')]
    else:
        # Default: all top level XSDs in input dir?
        # Or just specific ones provided in prompt.
        # User prompt implies specific list "from iata_ndc_messages.json"
        pass

    if not target_files:
        print("No target files found.")
        return

    flattener = SchemaFlattener(args.input_dir)
    
    for fname in target_files:
        if not os.path.exists(os.path.join(args.input_dir, fname)):
            print(f"Skipping {fname} (not found)")
            continue
            
        out_name = os.path.join(args.output_dir, fname)
        flattener.flatten_message(fname, out_name)

if __name__ == '__main__':
    main()
