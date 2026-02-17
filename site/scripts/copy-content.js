import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const projectRoot = path.resolve(__dirname, '../../');
const sourceDir = path.join(projectRoot, 'ndc_content', 'examples');
const destDir = path.join(projectRoot, 'site', 'public', 'content', 'examples');

const sourceFlowsDir = path.join(projectRoot, 'ndc_content', 'flows');
const destFlowsDir = path.join(projectRoot, 'site', 'public', 'content', 'flows');


console.log(`Copying content from ${sourceDir} to ${destDir}...`);

if (!fs.existsSync(sourceDir)) {
    console.error(`Source directory ${sourceDir} does not exist! Run migration/download + catalog build first.`);
    process.exit(1);
}

function copyRecursive(src, dest) {
    const stats = fs.statSync(src);
    if (stats.isDirectory()) {
        fs.mkdirSync(dest, { recursive: true });
        const entries = fs.readdirSync(src, { withFileTypes: true });
        for (const entry of entries) {
            const srcPath = path.join(src, entry.name);
            const destPath = path.join(dest, entry.name);
            copyRecursive(srcPath, destPath);
        }
        return;
    }

    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.copyFileSync(src, dest);
}

if (fs.existsSync(destDir)) {
    fs.rmSync(destDir, { recursive: true, force: true });
}
copyRecursive(sourceDir, destDir);

if (fs.existsSync(destFlowsDir)) {
    fs.rmSync(destFlowsDir, { recursive: true, force: true });
}
if (fs.existsSync(sourceFlowsDir)) {
    console.log(`Copying flows from ${sourceFlowsDir} to ${destFlowsDir}...`);
    copyRecursive(sourceFlowsDir, destFlowsDir);
}

console.log('Content copied successfully.');
