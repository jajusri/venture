import { app, BrowserWindow } from 'electron';
import fs from 'node:fs';
import path from 'node:path';

async function capture(): Promise<void> {
  await app.whenReady();

  const window = new BrowserWindow({
    width: 1280,
    height: 900,
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
    document.getElementById('header-connection-label').textContent = 'Connected';
    document.getElementById('dashboard-connection').textContent = 'Connected';
    document.getElementById('connection-indicator').className = 'indicator indicator-connected';
    document.getElementById('dashboard-company-name').textContent = 'ESTIMATION';
    document.getElementById('dashboard-company-id').textContent = 'estimation';
    document.getElementById('dashboard-session-status').textContent = 'ACTIVE';
    document.getElementById('dashboard-erp-name').textContent = 'Tally';
    document.getElementById('dashboard-last-refresh').textContent = '2026-07-23T00:00:00.000Z';
    document.getElementById('dashboard-desktop-version').textContent = '0.4.1';
    document.getElementById('company-list-status').textContent = '1 companies available';
    document.getElementById('company-list').innerHTML = '<button type="button" class="company-item selected" data-company-id="estimation"><span class="company-name">ESTIMATION</span><span class="company-id">estimation</span></button>';
  `);

  await new Promise((resolve) => setTimeout(resolve, 500));

  const screenshotDir = path.join(__dirname, '../../screenshots');
  fs.mkdirSync(screenshotDir, { recursive: true });

  const dashboardImage = await window.capturePage();
  fs.writeFileSync(path.join(screenshotDir, 'dashboard-live.png'), dashboardImage.toPNG());

  await window.webContents.executeJavaScript(`
    document.querySelector('[data-view="settings"]').click();
  `);
  await new Promise((resolve) => setTimeout(resolve, 200));
  const settingsImage = await window.capturePage();
  fs.writeFileSync(path.join(screenshotDir, 'settings.png'), settingsImage.toPNG());

  console.log('Screenshots saved to', screenshotDir);
  app.quit();
}

void capture();
