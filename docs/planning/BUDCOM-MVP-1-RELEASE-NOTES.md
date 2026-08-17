# BUDCOM MVP-1 — Release Notes

**Status:** Draft, prepared during public-release preparation (2026-08-17). Not yet published.

## What BUDCOM does

BUDCOM connects your Tally accounting data to a simple, fast Desktop and Android experience:

- **Ledgers** — browse account balances and statements, with a period picker (Today, This Month,
  Current FY, and more).
- **Vouchers** — one unified, chronological list of Sales, Purchase, Receipt, Payment and other
  voucher types, with quick filters.
- **Stock Items** — browse item-level data synced from Tally.
- **Search** — find ledgers, stock items and vouchers from one place.
- **Sync** — bring your Tally company's data onto your phone, kept fresh with a tap.
- **PDF Preview, Save and Share** — preview a Ledger statement or Voucher as a PDF before saving it
  or sharing it (including directly to WhatsApp).
- **Desktop companion** — a lightweight Windows app that connects to Tally, shows connection and
  sync status, and manages where your data is kept.
- **Private storage (optional)** — for businesses that want it, BUDCOM can keep its local data on a
  removable drive you control, instead of the computer's own storage.

## What's new in this release

- A refreshed Android Home screen: your company name, a quick search, Sync status, and direct
  access to Vouchers and Ledgers, all on one screen.
- A compact filter strip on the Vouchers screen — jump straight to Sales, Purchase, Receipt,
  Payment and other types.
- A one-tap PDF Preview button on both Ledger statements and Voucher details.
- A refreshed Desktop look, with clearer connection and sync status.
- Safer storage-mode switching: if you ever change how BUDCOM stores its data, it now clearly
  explains what will happen and asks you to confirm before proceeding — your existing data is never
  silently discarded.

## Before you install

- **Network:** BUDCOM Desktop and your Android phone should be on the **same normal Wi-Fi/router
  network** for automatic discovery to work. A phone's personal mobile hotspot does not reliably
  support the discovery step BUDCOM uses — connect both devices to your regular router instead.
- **Windows network type:** if Windows asks whether your network is "Public" or "Private," choose
  **Private** (or "Trusted") — a Public network setting will block BUDCOM Desktop from being found
  by your phone.
- **Tally:** make sure Tally is open with the company you want to connect selected.

## Operating notes for this release

- **Don't remove a Private-storage USB drive while BUDCOM Desktop is running.** If you need to
  change or disconnect it, close BUDCOM Desktop first, then reconnect and reopen. (A future release
  will make this safer to do live; for now, treat it like removing a USB drive during any other
  running application — stop first.)
- **Windows may show a security warning ("Windows protected your PC")** the first time you run the
  installer, because it is not yet digitally signed. This is expected for this release; choose "More
  info" → "Run anyway" if you trust the source you downloaded it from.
- The Android app is a debug-signed build for this release; a signed release build is planned for
  a future update.

## Feedback

If something doesn't work as expected, see the Quick-Start guide's troubleshooting section
(`docs/planning/BUDCOM-MVP-1-QUICK-START.md`) first. For anything else, contact the BUDCOM team
through your usual support channel.
