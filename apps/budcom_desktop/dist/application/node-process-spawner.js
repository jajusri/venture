"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.NodeProcessSpawner = void 0;
const node_child_process_1 = require("node:child_process");
class NodeManagedProcess {
    pid;
    child;
    constructor(child) {
        if (!child.pid) {
            throw new Error('Failed to spawn connector process.');
        }
        this.child = child;
        this.pid = child.pid;
    }
    async kill(signal = 'SIGTERM') {
        await new Promise((resolve, reject) => {
            this.child.once('error', reject);
            this.child.kill(signal);
            this.child.once('exit', () => resolve());
        });
    }
    onExit(listener) {
        this.child.once('exit', (code, signal) => {
            listener(code, signal);
        });
    }
}
class NodeProcessSpawner {
    spawn(spec) {
        const child = (0, node_child_process_1.spawn)(spec.command, [...spec.args], {
            cwd: spec.cwd,
            env: { ...process.env, ...spec.env },
            stdio: 'ignore',
            windowsHide: true,
        });
        return new NodeManagedProcess(child);
    }
}
exports.NodeProcessSpawner = NodeProcessSpawner;
//# sourceMappingURL=node-process-spawner.js.map