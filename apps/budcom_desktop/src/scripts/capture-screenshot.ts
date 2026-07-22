import { app, BrowserWindow } from 'electron';
import fs from 'node:fs';
import path from 'node:path';

async function capture(): Promise<void> {
  await app.whenReady();

  const window = new BrowserWindow({
    width: 1280,
    height: 800,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, '../preload/preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  await window.loadFile(path.join(__dirname, '../renderer/index.html'));
  await window.webContents.executeJavaScript(`
    document.getElementById('header-version').textContent = '0.3.1';
    document.getElementById('dashboard-connection').textContent = 'Connected';
    document.getElementById('connection-indicator').className = 'indicator indicator-connected';
  `);

  await new Promise((resolve) => setTimeout(resolve, 500));

  const screenshotDir = path.join(__dirname, '../../screenshots');
  fs.mkdirSync(screenshotDir, { recursive: true });
  const image = await window.capturePage();
  const pngPath = path.join(screenshotDir, 'dashboard.png');
  fs.writeFileSync(pngPath, image.toPNG());
  console.log(`Screenshot saved: ${pngPath}`);

  app.quit();
}

void capture();
