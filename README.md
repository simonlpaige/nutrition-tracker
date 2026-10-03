# Daily Rhythm 0.6 — the worm keeps receipts

Installed on Simon’s Pixel 9 Pro. The update preserves the existing journal. Android 14+ is required. Keep the same signing key for future updates; uninstalling removes private app data.

## Weekly routine

The phone reminds you on Sundays at 8 p.m. local time when a review is due. Existing evening check-in reminders remain enabled. Weekly health analysis runs when you copy the prepared prompt into your existing ChatGPT account, select a frontier model and submit it. Optional automatic nutrition is a separate food-only API connection.

1. During the week, log food names, amounts, ingredients, oils, sauces, preparation, and recipe yield. Automatic nutrition can estimate after saving; leave unknown nutrients blank if the connection is paused. More detail gives a better estimate; vague descriptions may need a follow-up question.
2. Log water in ml or US fluid ounces. This tracks plain water separately from imported Health Connect hydration, avoiding double counting.
3. Use Daily check-in to confirm which logs are complete. One meal can be a complete day; the app never requires three meals. On review day, an optional checkbox can confirm food completeness for every day with food entries in the selected window at once. Days without food entries remain unknown. Unknown or unconfirmed values are not treated as zero.
4. On review day, open AI help → Prepare a pattern review. The default gathers 28 days for context; 56- and 84-day journal windows are also available using previously cached health data and asks ChatGPT to focus on the last seven days. Available health data is read automatically. No Garmin numbers need typing.
5. Preview and copy the prompt, then paste it into ChatGPT. Bring its weekly JSON answer back to Daily Rhythm. Check the nutrition estimates and assumptions before saving. A preview shows each change. New answers update blanks and earlier AI estimates in the same meals. Manually entered values are protected unless you select “Also replace entered nutrition.” Use AI help → Import a new or corrected review to re-import older results without preparing another prompt. Corrected reviews for the same period replace the previous saved review. Both weekly.v1 and the new structured weekly.v2 format are supported. Estimates remain labeled and editable. A plain written review can also be saved.

The review includes food, digestion, water, sleep, activity, heart measurements and available Garmin stress, with optional caffeine, cannabis vape, orgasm and mood details. Private notes are excluded; meal ingredient descriptions are included. Sparse data remains sparse. Correlations are exploratory and cannot establish causes, diagnose illness, or fully measure circadian rhythms. Weekly review never submits automatically to ChatGPT. The structured reply stores coverage, evidence-backed tentative patterns, limitations, questions and tracking suggestions alongside meal estimates. Timing and delayed comparisons are exploratory, not a complete measurement of biological rhythms.

## Automatic nutrition

AI help → Manage connection controls the personal OpenAI API connection. The default small model is `gpt-6-luna`, with at most 300 request attempts per calendar month and bounded input/output sizes. Only food description, ingredients and one-serving portion are sent; journal notes, health history and device IDs are excluded. Responses use strict JSON schema and `store=false`. The key is encrypted by Android Keystore, never bundled in the APK, and excluded from the computer backup, journal exports and Android transfers. API billing is separate from your manual ChatGPT workflow. Requests cannot run without a funded API account. The current configured account reported `credit_balance_exhausted` during the live fixture check, so automatic estimates are installed **paused** until credits are available. Enable the connection after resolving this; no successful live estimate is claimed yet.

Descriptions save immediately even if an estimate fails. Results show assumptions and confidence and fill unknown or previous AI values; manual edits are protected. A changed description invalidates an older pending result. Completed results survive app closure, and interrupted pending requests can retry on a later launch. “Estimate recent unknown meals” is a confirmed catch-up action for the preceding 28 days.

## Logs and appearance

Meals include calories, protein, carbs, fat, fiber, sugar and sodium. The journal also records detailed bowel movements, caffeine in total mg, cannabis vape hits and optional puff duration/product/potency/effects, orgasms and context, mood/energy/anxiety/focus/subjective stress, and water. Vape hits are not converted to an absorbed THC dose. Subjective stress stays separate from Garmin stress.

The journal is one continuous timeline of all intake and output, with newer days first. Food forms hide nutrition and private notes in optional details. Vape and orgasm forms keep basic counts prominent and collapse extra detail. Caffeine starts at 8 US fl oz black coffee, approximately 95 mg; edit this for a different drink, portion or caffeine amount. Daily check-in has one “Everything is logged” shortcut, requiring your explicit confirmation. The reminder opens that check-in when the app is visible; tapping the notification opens it directly when closed. Android background restrictions prevent forcing a popup over other apps.

The app and launcher use the gnarly pink bowel-worm on a dark plum background.

## Automatic collection and local backups

Open Daily Rhythm regularly so it can capture available Health Connect data while the app is in the foreground. It archives the preceding 28 days once per day: steps, distance, sleep sessions/stages, heart rate, resting heart rate, weight, active energy, total energy, hydration and nutrition. Permissions and source availability determine coverage. Categories with no records are reported as empty, not invented.

The Windows helper starts when Simon signs in. It reads the watch over USB and makes a verified local phone backup, at most once per hour while connected and running. Keep the computer awake and the authorized phone connected by USB for new phone backups. The watch is needed for new FIT collection. Start-Garmin-Sync.cmd starts the helper manually; Stop-Garmin-Sync.cmd stops it until restarted or the next sign-in. The Startup shortcut is named Daily Rhythm Backup and Garmin Sync.

Local backups live in `C:/Users/simon/Documents/Daily Rhythm Backups`. Dated ZIP files contain the private journal, recipes, check-ins, saved reviews, reminder settings, captured Health Connect records and cached Garmin FIT files. Each backup is reopened and every stored member checked against a SHA-256 manifest before it is marked successful. Older backups are retained.

The archives include available Health Connect source records and explicit coverage metadata. This covers the app-accessible data, not every Garmin cloud metric, all historical data, or unrelated apps. No backup process can copy data that the source has not shared.

Cloud destination: the private Health folder selected by Simon in Google Drive. Cloud setup status is recorded separately in the app’s Settings; a local backup is never presented as a completed cloud upload. Browser upload currently needs the ChatGPT Chrome extension’s Allow access to file URLs setting. The connected Drive API can read but lacks upload scope. A daily 8:15 p.m. Codex backup check is scheduled to upload the newest verified backup, avoid duplicates, and report problems. This requires the computer/Codex and the signed-in browser connection to be available; it does not run AI health analysis.

## Garmin connection

The companion uses Garmin’s official Python FIT SDK 21.214.0 to read available watch files through USB. The computer identified the watch as fenix 8 - 43mm. Connect IQ SDK Manager builds watch apps and does not grant Garmin Health cloud access.

This integration reads documented stress, respiration, oxygen, resting-heart and activity fields. Documented HRV fields are supported when present. During setup, the watch’s Sleep and HRV folders were empty; sleep was available through Health Connect. Body Battery and undocumented vendor fields are not inferred. Monitoring heart records without a complete timestamp are skipped. The phone import omits GPS and serial numbers; raw FIT backups can include health and location details.

At setup, the FIT import contained 102 valid samples, including 28 stress samples, and three activity summaries within the 35-day import window. This is coverage information, not a health assessment. Garmin Connect cloud history still needs a separate approved connection; this USB integration is not wireless Garmin cloud sync.

## Restore and privacy

The Android app has no analytics. Internet permission supports optional food-only automatic nutrition. Android’s automatic cloud backup is disabled; the explicit computer/Drive backups are separate. The full ZIP contains sensitive health data, including private notes and raw FIT content. Keep its destination private.

For journal recovery, open a verified ZIP, extract `phone/files/journal.json`, and import it through Daily Rhythm’s Settings after first exporting the current journal. Review the app’s replacement confirmation. The full ZIP also preserves Health Connect raw archives and FIT files for recovery or analysis; importing journal.json alone does not restore those separate archives or write data into Health Connect. With Room storage, restore the journal through the app’s JSON import so the database and portable mirror update together. Do not overwrite journal.json directly while an existing database remains authoritative. Separate archive/FIT recovery requires authorized USB access and a current backup retained.

Settings’ JSON export includes journal data, recipes, check-ins and reviews; it does not include the separate raw health archives. The automatic ZIP is the more complete backup.

## Verification and development

51 JavaScript regression checks, 12 Android instrumentation checks and six Python backup integrity checks passed. Android assembly and lint passed with zero errors. Browser checks verified corrected nutrition replacing prior estimates without duplicate meals. The journal now uses Room with a retained original JSON file and a verified portable mirror. Backups refuse a pending or stale Room export and exclude API credential storage. The installed update preserved the phone's journal without adding test entries or submitting health data to ChatGPT.

Build with JDK 17, Android SDK 35 and Gradle 8.9. Run `node --test tests/*.test.cjs` and the companion Python unittest suite. The source archive excludes private health data, local backup configuration and cached FIT files. Preserve the existing signing key separately for updates.

References: [Garmin FIT SDK](https://developer.garmin.com/fit/overview/), [official Python SDK](https://github.com/garmin/fit-python-sdk), [Garmin Health SDK](https://developer.garmin.com/health-sdk/overview/), [Garmin Health API](https://developer.garmin.com/gc-developer-program/health-api/).
Automatic nutrition references: [OpenAI GPT-6 Luna](https://developers.openai.com/api/docs/models/gpt-6-luna), [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs).
