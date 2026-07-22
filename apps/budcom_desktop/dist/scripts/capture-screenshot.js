"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const electron_1 = require("electron");
const node_fs_1 = __importDefault(require("node:fs"));
const node_path_1 = __importDefault(require("node:path"));
async function capture() {
    await electron_1.app.whenReady();
    const window = new electron_1.BrowserWindow({
        width: 1280,
        height: 900,
        show: false,
        webPreferences: {
            preload: node_path_1.default.join(__dirname, '../preload/preload.js'),
            contextIsolation: true,
            nodeIntegration: false,
            sandbox: true,
        },
    });
    await window.loadFile(node_path_1.default.join(__dirname, '../renderer/index.html'));
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
    const screenshotDir = node_path_1.default.join(__dirname, '../../screenshots');
    node_fs_1.default.mkdirSync(screenshotDir, { recursive: true });
    const dashboardImage = await window.capturePage();
    node_fs_1.default.writeFileSync(node_path_1.default.join(screenshotDir, 'dashboard-live.png'), dashboardImage.toPNG());
    await window.webContents.executeJavaScript(`
    document.querySelector('[data-view="settings"]').click();
  `);
    await new Promise((resolve) => setTimeout(resolve, 200));
    const settingsImage = await window.capturePage();
    node_fs_1.default.writeFileSync(node_path_1.default.join(screenshotDir, 'settings.png'), settingsImage.toPNG());
    console.log('Screenshots saved to', screenshotDir);
    electron_1.app.quit();
}
void capture();
//# sourceMappingURL=capture-screenshot.js.map