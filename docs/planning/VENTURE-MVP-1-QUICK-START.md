# VENTURE MVP-1 — Quick-Start Guide

**Status:** Draft, prepared during public-release preparation (2026-08-17). Not yet published.

## Getting started

1. **Install VENTURE Desktop** on the Windows computer where Tally runs (or that can reach it on
   your network). Run the installer and follow the prompts.
   - If Windows shows "Windows protected your PC," choose **More info → Run anyway**. This is
     expected until the installer is digitally signed.
2. **Make sure Tally is running** with the company you want to use already open.
3. **Choose where VENTURE keeps its data** when asked, the first time you open Desktop:
   - **Standard** — VENTURE keeps its local data on this computer. Simplest option for most users.
   - **Private removable storage** — VENTURE keeps its local data on a USB/removable drive you
     choose. Pick this if you want your business data to live on media you control.
   You can change this later, but VENTURE will always ask you to confirm first — see "Changing
   storage mode" below.
4. **Select your company** from the list VENTURE finds in Tally.
5. **Install VENTURE on your Android phone.**
6. **Connect your phone to Desktop.** Make sure your phone and the Desktop computer are on the
   **same Wi-Fi network** (your normal home/office router — not a phone hotspot). Open VENTURE on
   your phone; it should find your Desktop automatically. Follow the pairing steps shown.
7. **Pick your company on the phone** (it should match what you selected on Desktop).
8. **Sync.** Tap Sync to bring your Ledgers, Vouchers and Stock Items onto your phone.
9. **Use Ledgers, Vouchers and Stock** from the Home screen. Tap any Voucher or Ledger to see
   details, and use Preview to see a PDF before saving or sharing it (including to WhatsApp).

## Changing storage mode later

If you open Settings and choose a different storage mode than the one currently in use, VENTURE will
explain what's about to happen and ask you to click Continue a second time to confirm. Nothing is
deleted when you switch — your previous data stays where it was — but any phones paired to VENTURE
will need to be paired again, and Ledger/Voucher data will be freshly synced from Tally rather than
reused from the local cache. Read the message before confirming.

## Troubleshooting

**Tally not reachable**
Make sure Tally is open and the company is loaded. Check that Desktop's status shows "Connected" —
if not, confirm Tally is running on the same computer (or reachable on your network) and try the
Refresh button.

**Company not visible**
Confirm the company is actually open in Tally, not just listed. Some setups require Tally's own
network/ODBC settings to allow outside connections — check Tally's configuration if the company
still doesn't appear after a refresh.

**Phone not connecting to Desktop**
- Confirm both devices are on the same normal Wi-Fi network (not a mobile hotspot).
- On Windows, make sure the active network is set to **Private** (or "Trusted"), not "Public" —
  a Public network blocks VENTURE from being found.
- Try closing and reopening the app on your phone.

**Windows says the network is "Public"**
Open Windows network settings and change the active network's profile to **Private**. VENTURE
Desktop needs this to be discoverable on your local network.

**Private storage shows "not connected"**
Reconnect the removable drive VENTURE was configured to use, then click Retry. If you want to use a
different drive instead, use "Choose a different drive" — you'll be asked to confirm before VENTURE
switches to it (see "Changing storage mode" above).

**Don't remove your Private-storage drive while VENTURE Desktop is running.** If you need to
disconnect or swap it, close VENTURE Desktop first.

**Sync failed**
Check that Desktop shows Tally as reachable, then retry Sync from your phone. A single failed sync
does not affect data already downloaded — you'll keep seeing your last successfully synced data
until the next sync succeeds.

**Can't share/save a PDF**
Not every voucher type can be shared as a PDF invoice — VENTURE will tell you why if a specific
voucher can't be shared. Ledger statements can always be previewed and shared.

**After a restart**
Reopen Desktop and Tally, and reopen the app on your phone. VENTURE restores your company selection,
pairing and synced data automatically — you should not need to set anything up again.

## What not to do (for this release)

- Don't remove a Private-storage USB drive while VENTURE Desktop is running.
- Don't expect automatic discovery over a mobile hotspot — use your normal Wi-Fi/router.
- If something looks wrong after an update, try Refresh or a full app/Desktop restart before
  assuming data is lost — VENTURE's local data is designed to survive both.
