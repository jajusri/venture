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
        height: 800,
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
    document.getElementById('dashboard-connection').textContent = 'Connected';
    document.getElementById('connection-indicator').className = 'indicator indicator-connected';
  `);
    await new Promise((resolve) => setTimeout(resolve, 500));
    const screenshotDir = node_path_1.default.join(__dirname, '../../screenshots');
    node_fs_1.default.mkdirSync(screenshotDir, { recursive: true });
    const image = await window.capturePage();
    const pngPath = node_path_1.default.join(screenshotDir, 'dashboard.png');
    node_fs_1.default.writeFileSync(pngPath, image.toPNG());
    console.log(`Screenshot saved: ${pngPath}`);
    electron_1.app.quit();
}
void capture();
//# sourceMappingURL=capture-screenshot.js.map