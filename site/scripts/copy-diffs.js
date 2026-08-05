import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const projectRoot = path.resolve(__dirname, '../../');
const sourceDir = path.join(projectRoot, 'ndc_diffs');
const destDir = path.join(projectRoot, 'site/public/diffs');

if (!fs.existsSync(sourceDir)) {
    // ponytail: soft-fail so `npm run dev` works without a local JDK; CI always precomputes
    console.warn(`No precomputed diffs at ${sourceDir} — /diff page will have no data. Run: cd backend && ./gradlew precomputeDiffs`);
    process.exit(0);
}

console.log(`Copying diffs from ${sourceDir} to ${destDir}...`);
fs.cpSync(sourceDir, destDir, { recursive: true });
console.log('Diffs copied successfully.');
