import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Project root is up one level from site/scripts, then up one level from site
const projectRoot = path.resolve(__dirname, '../../');
const sourceDir = path.join(projectRoot, 'ndc_schemas');
const destDir = path.join(projectRoot, 'site/public/schemas');

console.log(`Copying schemas from ${sourceDir} to ${destDir}...`);

if (!fs.existsSync(sourceDir)) {
    console.error(`Source directory ${sourceDir} does not exist! Run the flattening script first.`);
    process.exit(1);
}

// Recursive copy function
function copyRecursive(src, dest) {
    if (!fs.existsSync(dest)) {
        fs.mkdirSync(dest, { recursive: true });
    }

    const entries = fs.readdirSync(src, { withFileTypes: true });

    for (const entry of entries) {
        const srcPath = path.join(src, entry.name);
        const destPath = path.join(dest, entry.name);

        if (entry.isDirectory()) {
            copyRecursive(srcPath, destPath);
        } else {
            fs.copyFileSync(srcPath, destPath);
        }
    }
}

copyRecursive(sourceDir, destDir);
console.log('Schemas copied successfully.');
