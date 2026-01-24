import fs from 'node:fs';
import path from 'node:path';

const SCHEMAS_DIR = path.join(process.cwd(), 'public/schemas');

/**
 * Extracts the documentation from an XSD file's root element.
 * Looks for xs:element with matching name and extracts xs:annotation/xs:documentation
 */
export function getSchemaDescription(version: string, message: string): string {
    const schemaPath = path.join(SCHEMAS_DIR, version, `${message}.xsd`);
    
    if (!fs.existsSync(schemaPath)) {
        return getDefaultDescription(message, version);
    }
    
    try {
        const content = fs.readFileSync(schemaPath, 'utf-8');
        
        // Match the root element's documentation
        // Pattern: <xs:element name="IATA_MessageName">...<xs:documentation>...</xs:documentation>
        const elementPattern = new RegExp(
            `<xs:element[^>]*name="${message}"[^>]*>\\s*<xs:annotation>\\s*<xs:documentation>([\\s\\S]*?)</xs:documentation>`,
            'i'
        );
        
        const match = content.match(elementPattern);
        
        if (match && match[1]) {
            // Clean up the description: normalize whitespace, trim
            const description = match[1]
                .replace(/\s+/g, ' ')
                .trim();
            
            // Truncate to ~155 chars for meta description (with ellipsis if needed)
            if (description.length > 155) {
                return description.substring(0, 152).trim() + '...';
            }
            return description;
        }
    } catch (error) {
        console.warn(`Could not parse schema description for ${message}:`, error);
    }
    
    return getDefaultDescription(message, version);
}

function getDefaultDescription(message: string, version: string): string {
    const cleanName = message.replace('IATA_', '');
    const isRequest = cleanName.endsWith('RQ');
    const isResponse = cleanName.endsWith('RS');
    const baseName = cleanName.replace(/(RQ|RS|Notif)$/, '');
    
    if (isRequest) {
        return `IATA NDC ${baseName} Request schema documentation. Version ${version} XML structure and element reference.`;
    }
    if (isResponse) {
        return `IATA NDC ${baseName} Response schema documentation. Version ${version} XML structure and element reference.`;
    }
    return `IATA NDC ${cleanName} schema documentation. Version ${version} XML structure and element reference.`;
}
