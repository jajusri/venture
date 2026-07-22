import { cpSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const srcRenderer = path.join(root, 'src', 'renderer');
const distRenderer = path.join(root, 'dist', 'renderer');

mkdirSync(distRenderer, { recursive: true });
cpSync(path.join(srcRenderer, 'index.html'), path.join(distRenderer, 'index.html'));
cpSync(path.join(srcRenderer, 'styles'), path.join(distRenderer, 'styles'), { recursive: true });

console.log('Renderer assets copied to dist/renderer');
