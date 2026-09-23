# Daily Rhythm USB companion

Uses Garmin's official Python FIT SDK 21.214.0. Reads the connected Fenix through Windows MTP, never modifies watch files, and copies a filtered import into the debug Daily Rhythm app using an already-authorized Android USB connection. No Garmin login or cloud API credentials.

On Simon's computer the helper is already installed and started. It starts at Windows sign-in. Use Start-Garmin-Sync.cmd to start it manually, and Stop-Garmin-Sync.cmd to stop it. It checks once a minute while running. Both watch and Android phone must be connected for a fresh transfer to the phone. Reopen Daily Rhythm or tap Refresh to display the new snapshot. This is not a wireless/background Garmin cloud connection.

To set up another Windows checkout, install Python and Android SDK platform-tools, create `.venv` in this companion directory (`python -m venv .venv`), then install `requirements.txt` with that environment's pip. Run `Sync-Garmin.ps1` once, or with `-Loop` to keep checking. It expects Android SDK at `%LOCALAPPDATA%/Android/Sdk` and uses a sibling `garmin-cache` directory. One Fenix and one authorized Android phone must be attached. It does not change USB authorization or grant health permissions. This personal debug build supports `run-as`; this transfer method is not intended for a Play Store release.

Stop a loop by creating `garmin-cache/stop-sync`; remove that marker before starting another loop. A named mutex prevents duplicate sync processes. Status is recorded locally in `garmin-cache/sync-status.json`.

The importer validates FIT integrity, discards invalid/negative sensor codes, deduplicates timestamps, and emits documented stress, respiration, oxygen, heart/resting-heart and HRV fields when available. Activity summaries omit GPS coordinates. Monitoring heart records that lack full timestamps are skipped rather than assigned guessed times. Unknown vendor fields are recorded by message type only; Body Battery is not inferred. The app shows sample means with counts and ranges, not official time-weighted Garmin daily scores. Sleep continues through Health Connect; the local Sleep and HRV folders were empty during setup.

The phone snapshot covers up to 35 days from cached files; review prompts filter to 14 or 28 days. Raw FIT files remain in the computer cache and can contain sensitive health and location data. The sanitized phone import contains no serial number or GPS coordinates. Neither raw FIT files nor the imported snapshot are included in the journal's JSON backup. The versioned automatic backup ZIP includes the cache and captured Health Connect archives. See the main guide for backup scope and recovery. Stop sync before removing imported data, or it will be imported again.

SDK: https://github.com/garmin/fit-python-sdk
FIT overview: https://developer.garmin.com/fit/overview/
Tests: run `python -m unittest test_decode_fit.py` in this folder with the SDK environment.
