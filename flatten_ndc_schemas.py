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

    def collect_namespaces(self, used_keys):
        """Collect all unique namespaces from used definitions."""
        namespaces = set()
        for (ns, name) in used_keys:
            if ns and ns != XSD_NS:
                namespaces.add(ns)
        return namespaces

    def build_prefix_map(self, namespaces, main_ns):
        """Build a consistent prefix map for all namespaces."""
        prefix_map = {'xs': XSD_NS}
        prefix_counter = 1
        
        for ns in sorted(namespaces):  # Sort for deterministic output
            if ns == main_ns:
                # Use 'tns' for target namespace or leave unprefixed
                prefix_map['tns'] = ns
            else:
                # Find an existing prefix from loaded schemas or generate one
                found_prefix = None
                for schema_info in self.schemas.values():
                    for prefix, uri in schema_info['prefix_map'].items():
                        if uri == ns and prefix and prefix not in prefix_map:
                            found_prefix = prefix
                            break
                    if found_prefix:
                        break
                
                if found_prefix:
                    prefix_map[found_prefix] = ns
                else:
                    prefix_map[f'ns{prefix_counter}'] = ns
                    prefix_counter += 1
        
        return prefix_map

    def get_prefix_for_ns(self, ns, output_prefix_map):
        """Get the prefix for a namespace in the output prefix map."""
        for prefix, uri in output_prefix_map.items():
            if uri == ns:
                return prefix
        return None

    def _write_schema_file(self, root, target_ns, output_path, xmlns_decls=None):
        """Write a schema element tree to a file with proper formatting."""
        xml_str = ET.tostring(root, encoding='utf-8').decode('utf-8')
        
        # Add any extra xmlns declarations
        if xmlns_decls:
            schema_tag_end = xml_str.find('>')
            if schema_tag_end > 0:
                xml_str = xml_str[:schema_tag_end] + ' ' + ' '.join(xmlns_decls) + xml_str[schema_tag_end:]
        
        # Pretty print with minidom
        dom = xml.dom.minidom.parseString(xml_str.encode('utf-8'))
        pretty_xml = dom.toprettyxml(indent="  ")
        
        # Remove empty lines
        pretty_xml = "\n".join([line for line in pretty_xml.split('\n') if line.strip()])
        
        with open(output_path, 'w') as f:
            f.write(pretty_xml)
        print(f"Generated {output_path}")

    def flatten_message(self, message_filename, output_path):
        """Flatten a message schema into two files: main + common types."""
        import copy
        
        main_info = self.load_schema(message_filename)
        if not main_info:
            return

        used_keys = self.find_used_definitions(os.path.abspath(os.path.join(self.source_dir, message_filename)))
        main_ns = main_info['target_ns']
        
        # Separate definitions by namespace
        defs_by_ns = {}
        for key in used_keys:
            ns, name = key
            if ns not in defs_by_ns:
                defs_by_ns[ns] = []
            defs_by_ns[ns].append(key)
        
        # Identify namespaces
        other_namespaces = [ns for ns in defs_by_ns.keys() if ns != main_ns and ns != XSD_NS]
        
        # Build prefix map for cross-references
        prefix_map = {'xs': XSD_NS}
        if main_ns:
            prefix_map[''] = main_ns  # default namespace for main schema types
        
        # Assign prefixes for other namespaces (typically just 'cns' for common types)
        for ns in other_namespaces:
            # Try to find existing prefix from source schemas
            found_prefix = None
            for schema_info in self.schemas.values():
                for pfx, uri in schema_info['prefix_map'].items():
                    if uri == ns and pfx:
                        found_prefix = pfx
                        break
                if found_prefix:
                    break
            prefix_map[found_prefix or 'cns'] = ns
        
        # Get prefix for common types namespace
        cns_prefix = None
        cns_ns = None
        for pfx, ns in prefix_map.items():
            if ns != main_ns and ns != XSD_NS and pfx:
                cns_prefix = pfx
                cns_ns = ns
                break

        # Helper to rewrite prefixes consistently
        # is_cns_schema: True when writing CommonTypes schema (needs cns: prefix for local types)
        def rewrite_prefixes(elem, origin_schema, target_schema_ns, is_cns_schema=False):
            for attr in ['type', 'base', 'ref', 'itemType', 'substitutionGroup']:
                val = elem.get(attr)
                if val:
                    ref_key = self.resolve_reference(val, origin_schema['target_ns'], origin_schema['prefix_map'])
                    if ref_key:
                        ref_ns, ref_local = ref_key
                        if ref_ns == XSD_NS:
                            elem.set(attr, f'xs:{ref_local}')
                        elif ref_ns == cns_ns and cns_prefix:
                            # Reference to common types - always use cns: prefix
                            elem.set(attr, f'{cns_prefix}:{ref_local}')
                        elif ref_ns == main_ns:
                            if is_cns_schema:
                                # From CommonTypes to main ns - need main ns prefix
                                elem.set(attr, f'msg:{ref_local}')
                            else:
                                # Same namespace (main) - no prefix needed
                                elem.set(attr, ref_local)
                        else:
                            elem.set(attr, ref_local)
            for child in elem:
                rewrite_prefixes(child, origin_schema, target_schema_ns, is_cns_schema)

        # Determine output filenames
        base_name = os.path.splitext(os.path.basename(output_path))[0]
        output_dir = os.path.dirname(output_path)
        common_types_filename = f"{base_name}_CommonTypes.xsd"
        common_types_path = os.path.join(output_dir, common_types_filename)

        # === Generate Common Types file (if there are types from other namespaces) ===
        if cns_ns and cns_ns in defs_by_ns:
            ET.register_namespace('xs', XSD_NS)
            ET.register_namespace(cns_prefix, cns_ns)  # Register cns for self-references
            
            cns_root = ET.Element(f'{{{XSD_NS}}}schema')
            cns_root.set('targetNamespace', cns_ns)
            cns_root.set('elementFormDefault', 'qualified')
            cns_root.set('version', '1.0')
            
            sorted_cns_keys = sorted(defs_by_ns[cns_ns], key=lambda x: x[1])
            
            for key in sorted_cns_keys:
                original_el = self.global_definitions[key]
                
                # Find origin schema
                origin_schema = None
                for path, s in self.schemas.items():
                    if key in s['definitions'] and s['definitions'][key] == original_el:
                        origin_schema = s
                        break
                if not origin_schema:
                    continue
                
                new_el = copy.deepcopy(original_el)
                rewrite_prefixes(new_el, origin_schema, cns_ns, is_cns_schema=True)
                cns_root.append(new_el)
            
            # Add xmlns:cns declaration for self-references
            xmlns_decls = [f'xmlns:{cns_prefix}="{cns_ns}"']
            self._write_schema_file(cns_root, cns_ns, common_types_path, xmlns_decls=xmlns_decls)

        # === Generate Main Message file ===
        ET.register_namespace('xs', XSD_NS)
        if cns_prefix and cns_ns:
            ET.register_namespace(cns_prefix, cns_ns)
        
        main_root = ET.Element(f'{{{XSD_NS}}}schema')
        main_root.set('targetNamespace', main_ns)
        main_root.set('elementFormDefault', 'qualified')
        main_root.set('version', main_info['root'].get('version', '1.0'))
        
        # Add import for common types
        if cns_ns and cns_ns in defs_by_ns:
            import_elem = ET.SubElement(main_root, f'{{{XSD_NS}}}import')
            import_elem.set('namespace', cns_ns)
            import_elem.set('schemaLocation', common_types_filename)
        
        # Add main namespace definitions
        if main_ns in defs_by_ns:
            sorted_main_keys = sorted(defs_by_ns[main_ns], key=lambda x: x[1])
            
            for key in sorted_main_keys:
                original_el = self.global_definitions[key]
                
                origin_schema = None
                for path, s in self.schemas.items():
                    if key in s['definitions'] and s['definitions'][key] == original_el:
                        origin_schema = s
                        break
                if not origin_schema:
                    continue
                
                new_el = copy.deepcopy(original_el)
                rewrite_prefixes(new_el, origin_schema, main_ns)
                main_root.append(new_el)
        
        # Build xmlns declarations for prefixes used in attribute values
        xmlns_decls = []
        if cns_prefix and cns_ns:
            xmlns_decls.append(f'xmlns:{cns_prefix}="{cns_ns}"')
        
        self._write_schema_file(main_root, main_ns, output_path, xmlns_decls)

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
