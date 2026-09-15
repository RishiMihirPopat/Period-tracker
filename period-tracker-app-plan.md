# Cycle Tracker — Build Plan
*A private, fully on-device Flo replacement*

## 0. Design philosophy

The one thing Flo can never credibly offer is what you're optimizing for: **her data never leaves her phone.** No account, no server, no analytics SDK, no "anonymized data sharing with partners." Build it so the app doesn't request the `INTERNET` permission at all (Android) — that's a claim you can prove by looking at the manifest, not just a privacy-policy promise. That single decision is the whole value proposition; everything below is in service of it.

---

## 1. Tech stack

**Flutter (Dart), single codebase for Android + iOS.**

Why this over the alternatives:
- One codebase covers both platforms without you maintaining Swift and Kotlin separately.
- Mature, boring, well-documented local-storage and local-notification packages — exactly what a no-backend app needs.
- Good widget/animation support for the kind of calendar-heavy, card-based UI Flo has.
- React Native is a reasonable second choice if you already know JS better than you'd like to know Dart, but Flutter's local-first ecosystem (drift, isar, flutter_local_notifications) is more mature for this specific use case.

Key packages:
| Purpose | Package |
|---|---|
| Local relational DB | `drift` (SQLite wrapper, type-safe, migrations) |
| DB encryption at rest | `sqlcipher_flutter_libs` + drift's cipher support |
| Local notifications | `flutter_local_notifications` |
| Biometric/PIN lock | `local_auth` + a custom PIN fallback |
| Charts (cycle stats) | `fl_chart` |
| State management | `riverpod` (simple, testable, no boilerplate) |

---

## 2. Data model

```
Cycle
  id, start_date, end_date (nullable until next period logged), period_length_days

DailyLog
  id, date, cycle_id (nullable), flow_intensity (none/spotting/light/medium/heavy),
  symptoms: [SymptomTag], mood: [MoodTag], notes (free text),
  bbt_celsius (nullable), sexual_activity (nullable: none/protected/unprotected),
  medication_taken (nullable, for birth control reminders)

SymptomTag (lookup table, pre-seeded + user-addable)
  cramps, headache, bloating, acne, breast_tenderness, fatigue, nausea, back_pain, ...

MoodTag (lookup table)
  happy, sad, anxious, irritable, calm, energetic, low_energy, sensitive, ...

Settings
  luteal_phase_length_default (14), reminder_times, pin_hash, biometric_enabled, cycle_history_privacy_lock
```

Everything lives in one encrypted local SQLite file. No `id` needs to be globally unique or server-assigned — this simplifies things a lot compared to a synced app.

---

## 3. Prediction engine (first-principles, no ML needed)

This doesn't need machine learning — cycle prediction is a solved statistics problem with well-understood biology behind it.

**Cycle length prediction:**
- Track `cycle_length = this period's start_date − previous period's start_date` for every completed cycle.
- Predicted next cycle length = **weighted average of the last 6 cycles**, weighting the most recent 3 cycles about 2x the older 3 (recent cycles are more representative of her current baseline than ones from 8 months ago).
- Track standard deviation across those same cycles → this becomes your confidence interval. Show a **range**, not a false-precision single date, e.g. "Period expected Mar 14–17" rather than "Mar 15."

**Ovulation / fertile window:**
- The luteal phase (ovulation → next period) is biologically far more consistent than the follicular phase (period → ovulation), typically 12–16 days, most commonly ~14.
- So: `predicted_ovulation_day = predicted_next_period_start − luteal_phase_length` (default 14, user-adjustable in Settings once she has enough history, or auto-refined if she logs BBT).
- Fertile window = ovulation day **−5 to +1** (sperm can survive up to 5 days; the egg is viable for ~24h after release).

**Cycle phase for "today":**
- Menstrual: day 1 → end of logged/predicted flow
- Follicular: end of period → ovulation day
- Ovulatory: ovulation day ± 1
- Luteal: day after ovulation → next period start

**Cold-start handling:** don't show any prediction until she's logged **2 full cycles**. Before that, show "Learning your cycle — log a couple more periods for predictions." A confident-looking wrong prediction on cycle 1 is worse than no prediction.

---

## 4. "What to expect" — the signature feature

This is the thing that makes it feel like Flo instead of a bare logging spreadsheet. Build it as a **phase → content map**, not a live model:

For each of the 4 phases, write (yourself, in your own words — don't scrape Flo's copy) 3–5 short entries covering:
- Typical hormonal backdrop (one line, e.g. "estrogen and progesterone are both low" for menstrual phase)
- Common physical patterns (energy level, cramping likelihood, appetite changes)
- Common mood patterns
- One practical tip

Then **personalize it with her own logged history**: "Last cycle, you logged fatigue on days like this — might be worth an early night." This is where a purely local app actually beats Flo, since you're not fighting a generic model trained on millions of users — you're pattern-matching against *her* last 3–6 cycles directly, which is a much smaller and more honest dataset to reason over.

Add a one-line disclaimer in Settings/About: this is general pattern information, not medical advice, and won't be accurate for irregular cycles, PCOS, perimenopause, etc.

---

## 5. Notifications (all local, no server needed)

Since everything's on-device, `flutter_local_notifications` can schedule these directly against the predicted dates — no push infrastructure required at all:
- **Daily "what to expect"** digest (opt-in time, e.g. 8am)
- **Period expected in N days** (fires once, ~2 days before predicted start)
- **Fertile window starting** (if she wants it — make this toggleable, not everyone wants it)
- **Log reminder** if she hasn't opened the app in 2+ days during her period window
- **Medication/pill reminder** (daily, if she uses hormonal birth control)

All reschedule automatically whenever a new period is logged and predictions update.

---

## 6. Privacy & security (the actual point of the app)

- SQLite database encrypted at rest via SQLCipher — even a phone backup or file-manager snoop can't read it without the key.
- App-level lock: biometric (Face ID / fingerprint) with PIN fallback, required on every app open.
- **No `INTERNET` permission requested at all.** This is the strongest claim you can make — it's independently verifiable, not just a policy statement.
- No analytics, no crash reporting SDKs that phone home (if you want crash logs, write them to a local file she can send you manually).
- Optional: hide the app's notification previews on the lock screen by default (a period app buzzing "Fertile window starting today" on a lock screen is a real, common complaint).

---

## 7. Core screens

1. **Today** — cycle day number, current phase, "what to expect" card, quick-log button
2. **Log Entry** — flow, symptom chips (multi-select), mood chips, notes, optional BBT/sexual activity
3. **Calendar** — month view, color-coded by phase, tap any day to see/edit that day's log
4. **Insights** — average cycle length, period length, regularity trend, symptom frequency chart over time
5. **Settings** — reminder toggles/times, luteal phase override, app lock, data export

---

## 8. One gap worth deciding on now: backup

"Fully on-device" means if her phone is lost, factory-reset, or dies, the cycle history is gone — there's no server copy to recover from. Worth building, even in v1:
- A **manual encrypted export** (single `.ctbackup` file, password-protected) she can save wherever she likes — her own Google Drive/iCloud, a laptop, nothing at all. This keeps the "no server, no sync" promise intact since *she* chooses if/where it goes, rather than the app doing it automatically.
- Cheap to build (dump the SQLite file, encrypt with a passphrase, share via the OS share sheet) and worth doing before v1 ships rather than after she's lost 6 months of data.

---

## 9. Distribution — a real platform gotcha

You said native Android/iOS — one thing to plan around depending on her phone:
- **Android:** trivial. Build a release APK, install it directly on her phone (or your own private F-Droid-style repo). No app store needed.
- **iOS:** Apple doesn't allow arbitrary sideloading. Options are (a) a free Apple ID lets you install a dev build via Xcode, but it **expires and needs reinstalling every 7 days** — annoying but free and fine for testing; (b) an Apple Developer Program account ($99/year) lets you install a build that lasts a full year via TestFlight, without ever submitting to the App Store. If she has an iPhone, (b) is worth the $99 for something you want to actually last.

---

## 10. Build roadmap

**Phase 1 — MVP (core loop working)**
- Data model + encrypted local DB
- Log entry screen (flow, symptoms, mood, notes)
- Today screen with basic cycle day counter
- Calendar view
- Simple prediction (average of last cycles, no confidence range yet)

**Phase 2 — the differentiators**
- "What to expect" content engine + personalization from her own history
- Local notifications (period soon, daily digest)
- Insights/stats screen
- App lock (PIN + biometric)

**Phase 3 — polish**
- Confidence ranges on predictions, phase-aware fertile window
- Manual encrypted export/backup
- BBT tracking, medication reminders (if she wants hormonal birth control support)
- Home screen widget (cycle day at a glance)

Realistically, for one person building solo: Phase 1 is a solid weekend-to-week project once the data model is settled, Phase 2 is another 1–2 weeks, Phase 3 is ongoing polish you add as she actually uses it and tells you what's missing — which, honestly, is the most important input here. Build Phase 1, get it on her phone fast, and let her real day-to-day use drive what Phase 2/3 actually prioritizes.
