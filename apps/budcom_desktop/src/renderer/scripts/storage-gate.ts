import type { RemovableVolumeInfo } from '../../application/private-storage/removable-volume-enumerator.js';
import type { StorageGateState } from '../../application/private-storage/private-storage-types.js';

function byId<T extends HTMLElement>(id: string): T {
  const element = document.getElementById(id);
  if (!element) throw new Error(`storage-gate: missing element #${id}`);
  return element as T;
}

function show(element: HTMLElement): void {
  element.classList.remove('hidden');
}

function hide(element: HTMLElement): void {
  element.classList.add('hidden');
}

/**
 * Renders the private-storage-drive radio list from scratch every time it's (re)populated —
 * always via safe DOM APIs (createElement/textContent), never innerHTML, since volume labels
 * originate from removable media and must be treated as untrusted display text.
 */
function renderDriveList(container: HTMLElement, volumes: readonly RemovableVolumeInfo[], selectedDriveLetter: string | null): void {
  container.replaceChildren();
  volumes.forEach((volume, index) => {
    const row = document.createElement('label');
    row.className = 'storage-gate-drive-row';

    const radio = document.createElement('input');
    radio.type = 'radio';
    radio.name = 'storage-gate-drive';
    radio.value = volume.driveLetter;
    radio.checked = selectedDriveLetter ? volume.driveLetter === selectedDriveLetter : index === 0;

    const letterSpan = document.createElement('span');
    letterSpan.className = 'drive-letter';
    letterSpan.textContent = volume.driveLetter;

    const detailSpan = document.createElement('span');
    detailSpan.className = 'drive-detail';
    const sizeLabel = volume.sizeBytes ? ` · ${(volume.sizeBytes / (1024 * 1024 * 1024)).toFixed(1)} GB` : '';
    detailSpan.textContent = `${volume.label || 'Removable drive'}${sizeLabel}`;

    row.append(radio, letterSpan, detailSpan);
    container.appendChild(row);
  });
}

function selectedDriveLetter(container: HTMLElement): string | null {
  const checked = container.querySelector<HTMLInputElement>('input[name="storage-gate-drive"]:checked');
  return checked ? checked.value : null;
}

async function refreshDriveList(driveListEl: HTMLElement, noDrivesEl: HTMLElement): Promise<void> {
  const volumes = await window.budcomDesktop.listRemovableVolumes();
  renderDriveList(driveListEl, volumes, null);
  if (volumes.length === 0) {
    show(noDrivesEl);
  } else {
    hide(noDrivesEl);
  }
}

/**
 * Shows the first-run "Where should BUDCOM keep your private business data?" setup screen and
 * resolves once the user has made and successfully applied a choice.
 */
async function runSetupFlow(overlay: HTMLElement): Promise<void> {
  const section = byId<HTMLElement>('storage-gate-setup');
  const unavailableSection = byId<HTMLElement>('storage-gate-unavailable');
  hide(unavailableSection);
  show(section);
  show(overlay);

  const standardRadio = byId<HTMLInputElement>('storage-gate-mode-standard');
  const privateRadio = byId<HTMLInputElement>('storage-gate-mode-private');
  const drivePicker = byId<HTMLElement>('storage-gate-drive-picker');
  const driveList = byId<HTMLElement>('storage-gate-drive-list');
  const noDrivesHint = byId<HTMLElement>('storage-gate-no-drives');
  const rescanButton = byId<HTMLButtonElement>('storage-gate-rescan');
  const continueButton = byId<HTMLButtonElement>('storage-gate-continue');
  const errorEl = byId<HTMLElement>('storage-gate-setup-error');

  const syncDrivePickerVisibility = (): void => {
    if (privateRadio.checked) {
      show(drivePicker);
      void refreshDriveList(driveList, noDrivesHint);
    } else {
      hide(drivePicker);
    }
  };
  standardRadio.addEventListener('change', syncDrivePickerVisibility);
  privateRadio.addEventListener('change', syncDrivePickerVisibility);
  rescanButton.addEventListener('click', () => void refreshDriveList(driveList, noDrivesHint));
  syncDrivePickerVisibility();

  await new Promise<void>((resolve) => {
    continueButton.addEventListener('click', () => {
      void (async () => {
        hide(errorEl);
        continueButton.disabled = true;
        try {
          const input = privateRadio.checked
            ? { mode: 'private-removable' as const, driveLetter: selectedDriveLetter(driveList) ?? '' }
            : { mode: 'standard' as const };
          if (input.mode === 'private-removable' && !input.driveLetter) {
            errorEl.textContent = 'Select a removable drive first.';
            show(errorEl);
            return;
          }
          const result = await window.budcomDesktop.chooseStorageMode(input);
          if (!result.ok) {
            errorEl.textContent = result.message ?? 'Could not apply that storage choice. Try again.';
            show(errorEl);
            return;
          }
          hide(overlay);
          resolve();
        } finally {
          continueButton.disabled = false;
        }
      })();
    }, { once: true });
  });
}

/** Shows the "Private BUDCOM storage is not connected" screen with Retry / Locate / Exit. */
async function runUnavailableFlow(overlay: HTMLElement, reason: 'missing' | 'mismatched'): Promise<void> {
  const section = byId<HTMLElement>('storage-gate-unavailable');
  const setupSection = byId<HTMLElement>('storage-gate-setup');
  hide(setupSection);
  show(section);
  show(overlay);

  const detailEl = byId<HTMLElement>('storage-gate-unavailable-detail');
  detailEl.textContent = reason === 'mismatched'
    ? 'The drive at the expected letter now holds different removable media. Connect the correct BUDCOM storage device.'
    : 'Connect the removable storage device BUDCOM was configured to use, then retry.';

  const retryButton = byId<HTMLButtonElement>('storage-gate-retry');
  const locateButton = byId<HTMLButtonElement>('storage-gate-locate');
  const exitButton = byId<HTMLButtonElement>('storage-gate-exit');

  await new Promise<void>((resolve) => {
    const onRetry = (): void => {
      // Without this guard, rapid repeat clicks each start their own overlapping
      // desktop:retry-storage-connection call — main.ts's activateConnectorLifecycle()
      // reassigns a shared module-level lifecycleService per call with no queuing, so two
      // in-flight retries can each spawn/track a Connector process, orphaning the earlier one.
      // runSetupFlow's continueButton already guards this way; retryButton needs the same guard.
      if (retryButton.disabled) return;
      retryButton.disabled = true;
      void (async () => {
        try {
          const state = await window.budcomDesktop.retryStorageConnection();
          if (state.kind === 'ready') {
            cleanup();
            hide(overlay);
            resolve();
          } else if (state.kind === 'unavailable') {
            detailEl.textContent = state.reason === 'mismatched'
              ? 'Still not connected: a different removable device is present at the expected drive letter.'
              : 'Still not connected. Check the cable/drive and retry.';
          }
        } finally {
          retryButton.disabled = false;
        }
      })();
    };
    const onLocate = (): void => {
      cleanup();
      void runSetupFlow(overlay).then(resolve);
    };
    const onExit = (): void => {
      window.close();
    };
    function cleanup(): void {
      retryButton.removeEventListener('click', onRetry);
      locateButton.removeEventListener('click', onLocate);
      exitButton.removeEventListener('click', onExit);
    }
    retryButton.addEventListener('click', onRetry);
    locateButton.addEventListener('click', onLocate);
    exitButton.addEventListener('click', onExit);
  });
}

/**
 * Checks the current storage-gate state and, if it blocks normal startup, shows the appropriate
 * screen and waits for the user to resolve it. Returns true when the app may proceed to its
 * normal dashboard init immediately (storage already ready); false when it showed a blocking
 * screen and has already waited for resolution — the caller should re-run its own startup flow
 * once this resolves, since the gate itself does not know how to initialize the dashboard.
 */
export async function renderStorageGate(): Promise<boolean> {
  const state: StorageGateState = await window.budcomDesktop.getStorageStatus();

  if (state.kind === 'ready') {
    // No DOM dependency in the common (already-resolved) case — deliberately does not require
    // the overlay markup to exist at all, so a host page without it (e.g. a minimal test
    // fixture) behaves exactly like a normal ready launch, not an error.
    document.getElementById('storage-gate-overlay')?.classList.add('hidden');
    return true;
  }

  const overlay = byId<HTMLElement>('storage-gate-overlay');
  if (state.kind === 'first-run') {
    await runSetupFlow(overlay);
    return false;
  }
  if (state.kind === 'unavailable') {
    await runUnavailableFlow(overlay, state.reason);
    return false;
  }
  // 'resolving' — main process hasn't finished its own first check yet; treat as blocking and
  // let the caller retry shortly rather than racing ahead into a dashboard with no storage.
  await runUnavailableFlow(overlay, 'missing');
  return false;
}
