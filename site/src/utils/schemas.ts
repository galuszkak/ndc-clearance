import fs from 'node:fs';
import path from 'node:path';

const SCHEMAS_DIR = path.join(process.cwd(), 'public/schemas');

export interface SchemaVersion {
    version: string;
    messages: string[];
}

export function getVersions(): string[] {
    if (!fs.existsSync(SCHEMAS_DIR)) return [];
    return fs.readdirSync(SCHEMAS_DIR).filter(file => {
        return fs.statSync(path.join(SCHEMAS_DIR, file)).isDirectory();
    }).sort().reverse(); // Newest first
}

export function getMessages(version: string): string[] {
    const versionDir = path.join(SCHEMAS_DIR, version);
    if (!fs.existsSync(versionDir)) return [];
    return fs.readdirSync(versionDir)
        .filter(file => file.endsWith('.xsd') && !file.toLowerCase().includes('common') && !file.toLowerCase().includes('simpletypes')) // Filter out common files if possible, though user wants strictly browsing
        .map(file => file.replace('.xsd', ''))
        .sort();
}
