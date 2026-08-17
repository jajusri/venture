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

  // TD-033: a prior Continue click can come back needing confirmation (this picker reopens for an
  // already-configured installation via the "storage not connected" screen's Locate button, not
  // only at genuine first-run). Tracks exactly which choice was warned about, so only resubmitting
  // that same choice counts as confirming it — changing the selection starts over as a fresh,
  // unconfirmed choice.
  type PendingChoice = { mode: 'standard' } | { mode: 'private-removable'; driveLetter: string };
  let pendingConfirmation: PendingChoice | null = null;
  const sameChoice = (a: PendingChoice, b: PendingChoice): boolean =>
    a.mode === b.mode && (a.mode !== 'private-removable' || (b.mode === 'private-removable' && a.driveLetter === b.driveLetter));
  const clearPendingConfirmation = (): void => {
    pendingConfirmation = null;
  };

  const syncDrivePickerVisibility = (): void => {
    clearPendingConfirmation();
    if (privateRadio.checked) {
      show(drivePicker);
      void refreshDriveList(driveList, noDrivesHint);
    } else {
      hide(drivePicker);
    }
  };
  standardRadio.addEventListener('change', syncDrivePickerVisibility);
  privateRadio.addEventListener('change', syncDrivePickerVisibility);
  driveList.addEventListener('change', clearPendingConfirmation);
  rescanButton.addEventListener('click', () => void refreshDriveList(driveList, noDrivesHint));
  syncDrivePickerVisibility();

  await new Promise<void>((resolve) => {
    // Deliberately NOT { once: true }: TD-033's confirmation flow requires the user to click
    // Continue a second time after seeing the warning, and every pre-existing error path (missing
    // drive selection, save failure) must also remain retryable without a page reload. The
    // listener is only ever removed once, explicitly, on genuine success below.
    const onContinue = (): void => {
      if (continueButton.disabled) return;
      void (async () => {
        hide(errorEl);
        continueButton.disabled = true;
        try {
          const choice: PendingChoice = privateRadio.checked
            ? { mode: 'private-removable', driveLetter: selectedDriveLetter(driveList) ?? '' }
            : { mode: 'standard' };
          if (choice.mode === 'private-removable' && !choice.driveLetter) {
            clearPendingConfirmation();
            errorEl.textContent = 'Select a removable drive first.';
            show(errorEl);
            return;
          }
          const confirmSwitch = pendingConfirmation !== null && sameChoice(pendingConfirmation, choice);
          const result = await window.budcomDesktop.chooseStorageMode({ ...choice, confirmSwitch });
          if (result.requiresConfirmation) {
            pendingConfirmation = choice;
            errorEl.textContent = `${result.message ?? 'This will switch storage modes.'} Click Continue again to confirm.`;
            show(errorEl);
            return;
          }
          if (!result.ok) {
            clearPendingConfirmation();
            errorEl.textContent = result.message ?? 'Could not apply that storage choice. Try again.';
            show(errorEl);
            return;
          }
          continueButton.removeEventListener('click', onContinue);
          hide(overlay);
          resolve();
        } finally {
          continueButton.disabled = false;
        }
      })();
    };
    continueButton.addEventListener('click', onContinue);
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

/** How often the renderer re-checks a still-'resolving' startup state. Cheap: getStorageStatus()
 * just reads main.ts's in-memory storageGateState, no I/O. */
const STORAGE_GATE_RESOLVING_POLL_INTERVAL_MS = 200;
/** Bounded ceiling on how long 'resolving' is treated as "still starting up" before surfacing an
 * honest timeout state. Covers realistic resolution latency (e.g. a slow PowerShell removable-
 * volume enumeration) without leaving the user staring at a stuck screen indefinitely. */
const STORAGE_GATE_RESOLVING_TIMEOUT_MS = 6000;

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Bridges the transient window between window creation and main.ts's resolveStorageGate()
 * settling (main.ts creates the window and starts this resolution concurrently, not
 * sequentially — see bootstrapApp()). Polls the cheap in-memory getStorageStatus() read until it
 * moves off 'resolving' or the bounded interval above elapses; never waits unboundedly.
 */
async function waitForStorageGateResolution(initial: StorageGateState): Promise<StorageGateState> {
  let state = initial;
  const deadline = Date.now() + STORAGE_GATE_RESOLVING_TIMEOUT_MS;
  while (state.kind === 'resolving' && Date.now() < deadline) {
    await sleep(STORAGE_GATE_RESOLVING_POLL_INTERVAL_MS);
    state = await window.budcomDesktop.getStorageStatus();
  }
  return state;
}

/**
 * Shows an honest "still starting up" screen once bounded polling has genuinely timed out —
 * deliberately distinct from runUnavailableFlow(): a slow first check says nothing about whether
 * a private vault is missing, so this must never reuse that wording. Retry forces a fresh,
 * fully-awaited resolveStorageGate() via IPC (never itself returns 'resolving'), so the caller's
 * dispatch loop always has a concrete state to act on afterward.
 */
async function runStartupTimeoutFlow(overlay: HTMLElement): Promise<StorageGateState> {
  const section = byId<HTMLElement>('storage-gate-timeout');
  const setupSection = byId<HTMLElement>('storage-gate-setup');
  const unavailableSection = byId<HTMLElement>('storage-gate-unavailable');
  hide(setupSection);
  hide(unavailableSection);
  show(section);
  show(overlay);

  const retryButton = byId<HTMLButtonElement>('storage-gate-timeout-retry');
  const exitButton = byId<HTMLButtonElement>('storage-gate-timeout-exit');

  return new Promise<StorageGateState>((resolve) => {
    const onRetry = (): void => {
      // Same overlapping-click guard as runUnavailableFlow's Retry — see its comment.
      if (retryButton.disabled) return;
      retryButton.disabled = true;
      void (async () => {
        try {
          const state = await window.budcomDesktop.retryStorageConnection();
          cleanup();
          resolve(state);
        } finally {
          retryButton.disabled = false;
        }
      })();
    };
    const onExit = (): void => {
      window.close();
    };
    function cleanup(): void {
      retryButton.removeEventListener('click', onRetry);
      exitButton.removeEventListener('click', onExit);
    }
    retryButton.addEventListener('click', onRetry);
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
  let state: StorageGateState = await window.budcomDesktop.getStorageStatus();

  for (;;) {
    if (state.kind === 'resolving') {
      // Transient startup state, never a failure — bounded-wait it before deciding anything.
      state = await waitForStorageGateResolution(state);
    }

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
    // Still 'resolving' after the bounded wait above: an honest generic startup-initialization
    // delay, never a false "private storage missing" claim. Retry re-dispatches on whatever
    // concrete state it settles to.
    state = await runStartupTimeoutFlow(overlay);
  }
}
