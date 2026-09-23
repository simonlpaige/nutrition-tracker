# Daily Rhythm 0.5 — the worm keeps receipts

Installed on Simon’s Pixel 9 Pro. The update preserves the existing journal. Android 14+ is required. Keep the same signing key for future updates; uninstalling removes private app data.

## Weekly routine

The phone reminds you on Sundays at 8 p.m. local time when a review is due. Existing evening check-in reminders remain enabled. AI runs only when you copy the prepared prompt into your existing ChatGPT account and submit it there.

1. During the week, log food names, amounts, ingredients, oils, sauces, preparation, and recipe yield. Leave unknown nutrients blank. More detail gives a better estimate; vague descriptions may need a follow-up question.
2. Log water in ml or US fluid ounces. This tracks plain water separately from imported Health Connect hydration, avoiding double counting.
3. Use Daily check-in to confirm which logs are complete. One meal can be a complete day; the app never requires three meals. Unknown or unconfirmed values are not treated as zero.
4. On review day, open AI help → Prepare a pattern review. The default gathers 28 days for context and asks ChatGPT to focus on the last seven days. Available health data is read automatically. No Garmin numbers need typing.
5. Preview and copy the prompt, then paste it into ChatGPT. Bring its weekly JSON answer back to Daily Rhythm. Check the nutrition estimates and assumptions before saving. Only blank nutrient values are filled; existing numbers are preserved. Estimates remain labeled and editable. A plain written review can also be saved.

The review includes food, digestion, water, sleep, activity, heart measurements and available Garmin stress, with optional caffeine, cannabis vape, orgasm and mood details. Private notes are excluded; meal ingredient descriptions are included. Sparse data remains sparse. Correlations are exploratory and cannot establish causes, diagnose illness, or fully measure circadian rhythms. Nothing is automatically sent to ChatGPT and no API key is needed.

## Logs and appearance

Meals include calories, protein, carbs, fat, fiber, sugar and sodium. The journal also records detailed bowel movements, caffeine in total mg, cannabis vape hits and optional puff duration/product/potency/effects, orgasms and context, mood/energy/anxiety/focus/subjective stress, and water. Vape hits are not converted to an absorbed THC dose. Subjective stress stays separate from Garmin stress.

The app and launcher use the gnarly pink bowel-worm on a dark plum background.

## Automatic collection and local backups

Open Daily Rhythm regularly so it can capture available Health Connect data while the app is in the foreground. It archives the preceding 28 days once per day: steps, distance, sleep sessions/stages, heart rate, resting heart rate, weight, active energy, total energy, hydration and nutrition. Permissions and source availability determine coverage. Categories with no records are reported as empty, not invented.

The Windows helper starts when Simon signs in. It reads the watch over USB and makes a verified local phone backup, at most once per hour while connected and running. Keep the computer awake and the authorized phone connected by USB for new phone backups. The watch is needed for new FIT collection. Start-Garmin-Sync.cmd starts the helper manually; Stop-Garmin-Sync.cmd stops it until restarted or the next sign-in. The Startup shortcut is named Daily Rhythm Backup and Garmin Sync.

Local backups live in `C:/Users/simon/Documents/Daily Rhythm Backups`. Dated ZIP files contain the private journal, recipes, check-ins, saved reviews, reminder settings, captured Health Connect records and cached Garmin FIT files. Each backup is reopened and every stored member checked against a SHA-256 manifest before it is marked successful. Older backups are retained.

The first full 0.5 backup contains 23 files and 19,127 Health Connect records from the available 28-day window. This covers the app-accessible data, not every Garmin cloud metric, all historical data, or unrelated apps. No backup process can copy data that the source has not shared.

Cloud destination: the private Health folder selected by Simon in Google Drive. Cloud setup status is recorded separately in the app’s Settings; a local backup is never presented as a completed cloud upload. Browser upload currently needs the ChatGPT Chrome extension’s Allow access to file URLs setting. The connected Drive API can read but lacks upload scope. A daily 8:15 p.m. Codex backup check is scheduled to upload the newest verified backup, avoid duplicates, and report problems. This requires the computer/Codex and the signed-in browser connection to be available; it does not run AI health analysis.

## Garmin connection

The companion uses Garmin’s official Python FIT SDK 21.214.0 to read available watch files through USB. The computer identified the watch as fenix 8 - 43mm. Connect IQ SDK Manager builds watch apps and does not grant Garmin Health cloud access.

This integration reads documented stress, respiration, oxygen, resting-heart and activity fields. Documented HRV fields are supported when present. During setup, the watch’s Sleep and HRV folders were empty; sleep was available through Health Connect. Body Battery and undocumented vendor fields are not inferred. Monitoring heart records without a complete timestamp are skipped. The phone import omits GPS and serial numbers; raw FIT backups can include health and location details.

At setup, the FIT import contained 102 valid samples, including 28 stress samples, and three activity summaries within the 35-day import window. This is coverage information, not a health assessment. Garmin Connect cloud history still needs a separate approved connection; this USB integration is not wireless Garmin cloud sync.

## Restore and privacy

The Android app has no analytics or Internet permission. Android’s automatic cloud backup is disabled; the explicit computer/Drive backups are separate. The full ZIP contains sensitive health data, including private notes and raw FIT content. Keep its destination private.

For journal recovery, open a verified ZIP, extract `phone/files/journal.json`, and import it through Daily Rhythm’s Settings after first exporting the current journal. Review the app’s replacement confirmation. The full ZIP also preserves Health Connect raw archives and FIT files for recovery or analysis; importing journal.json alone does not restore those separate archives or write data into Health Connect. Full private-file recovery requires the same debug app/authorized USB setup and should be performed with the app closed and a current backup retained.

Settings’ JSON export includes journal data, recipes, check-ins and reviews; it does not include the separate raw health archives. The automatic ZIP is the more complete backup.

## Verification and development

42 JavaScript checks and five Python checks (two FIT importer and three backup integrity checks) passed. Android assembly and lint passed with zero errors. Browser checks verified water unit conversion, leaving nutrition unknown, reviewing weekly estimates and applying them to a meal. The real phone archive was read and verified without adding test entries or submitting health data to ChatGPT.

Build with JDK 17, Android SDK 35 and Gradle 8.9. Run `node --test tests/*.test.cjs` and the companion Python unittest suite. The source archive excludes private health data, local backup configuration and cached FIT files. Preserve the existing signing key separately for updates.

References: [Garmin FIT SDK](https://developer.garmin.com/fit/overview/), [official Python SDK](https://github.com/garmin/fit-python-sdk), [Garmin Health SDK](https://developer.garmin.com/health-sdk/overview/), [Garmin Health API](https://developer.garmin.com/gc-developer-program/health-api/).