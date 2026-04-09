# ShiftTrack

A personal shift-scheduling app for rotating-shift workers. ShiftTrack automatically calculates your shift pattern, tracks leave and overtime, and lets you share your schedule with family or a manager.

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Your Dashboard](#your-dashboard)
3. [Calendar](#calendar)
4. [Recording Leave](#recording-leave)
5. [Logging Overtime](#logging-overtime)
6. [Customising Colors](#customising-colors)
7. [Home-Screen Widget](#home-screen-widget)
8. [Sharing Your Schedule](#sharing-your-schedule)
9. [Spectator Mode](#spectator-mode)
10. [Offline Use & Sync](#offline-use--sync)
11. [Troubleshooting](#troubleshooting)
12. [For Developers](#for-developers)


---

## Getting Started

### Step 1 — Sign In

Open ShiftTrack and tap **Sign in with Google**. A Google account is required for cloud sync and schedule sharing.

### Step 2 — Set Your Anchor Date

ShiftTrack uses a repeating 5-day cycle:

| Position | Shift |
|----------|-------|
| 1 | Day |
| 2 | Day |
| 3 | Night |
| 4 | Rest |
| 5 | Off |

During onboarding, pick **any date** where you know which shift you worked, then select the matching position. The app calculates every other date from that single reference point.

> **Tip:** Use today's date and whatever shift you are on right now.

### Step 3 — Enter Your Leave Balance

Set your yearly allowance for each leave category (Annual, Sick, Personal, Unpaid, Study). The app deducts from these as you record leave throughout the year.

### Step 4 — You're Ready

The bottom navigation bar has three tabs:

| Tab | What you'll find |
|-----|-----------------|
| **Home** | Today's shift, upcoming days, leave balance, overtime summary |
| **Calendar** | Full month view with colour-coded shifts |
| **Settings** | Anchor date, leave, overtime, colours, widget, and invites |

---

## Your Dashboard

The **Home** tab gives you a quick overview:

- **Shift cards** — Today's shift (highlighted) and the next 6 days, each showing the date, weekday, and shift type.
- **Leave balance** — Per-category progress bars (Annual, Sick, Personal, Unpaid, Study) showing remaining vs total days.
- **Overtime summary** — Hours worked this week and the cumulative annual total.

---

## Calendar

The **Calendar** tab shows a full month with each day colour-coded by shift type. Tap any day to open the **Day Detail** screen.

- **Half-day leave** is shown as a split cell — the top half displays the shift colour and the bottom half uses light grey.
- **Leave type indicators** appear as coloured dots on days with full-day leave, using the leave type's designated colour (Annual = green, Sick = red, Personal = blue, Unpaid = orange, Study = purple).
- The **legend** below the calendar shows both shift types and leave types with large, easy-to-read colour circles.
- **Export to CSV** — Tap the share icon in the top bar to export your calendar data. Pick a date range (defaults to the current month) and share or save the generated `.csv` file. Columns: Date, Day of Week, Shift Type, Leave Type, Half Day, Overtime Hours, Note.

On the Day Detail screen you can:

| Action | How |
|--------|-----|
| Override a shift | Tap a shift-type button (Day / Night / Rest / Off / Leave) to replace the computed shift |
| Clear an override | Tap the active override again to restore the automatic shift |
| Add leave | Tap **Add Leave**, choose a category, toggle **Half Day** if needed, add an optional note |
| Remove leave | Tap the leave entry and confirm |
| Log overtime | Tap **Log Overtime**, enter hours, add an optional note |
| Remove overtime | Tap the overtime entry and confirm |
| Save a note | Type in the notes field and tap **Save note** |

---

## Recording Leave

1. Open any day from the calendar.
2. Tap **Add Leave**.
3. Choose a leave type: Annual, Sick, Personal, Unpaid, or Study.
4. Toggle **Half Day** if you only need half the day.
5. Add an optional note (up to 500 characters).
6. Tap **Save**.

Your leave balance updates immediately on the Dashboard. At the start of each new year, balances roll over automatically — each category's total is carried forward independently.

---

## Logging Overtime

1. Open any day from the calendar.
2. Tap **Log Overtime**.
3. Enter the number of hours.
4. Add an optional note.
5. Tap **Save**.

To mark overtime as compensated (paid out or taken as time-off), go to **Settings → Overtime compensation**.

---

## Customising Colors

### Shift Colors

Go to **Settings → Shift Colors** to set a custom colour for each shift type (Day, Night, Rest, Off, Leave). Use the **color picker** to choose any colour by adjusting Hue, Saturation, and Brightness sliders. A preview swatch shows your selected colour in real time. Tap **Reset to default** to revert to the original colour.

Your colour choices are applied everywhere — calendar, dashboard, day detail, and home-screen widget.

### Overtime Color

Customise the overtime indicator colour in the same section using the same color picker.

### Leave Type Colors

Go to **Settings → Leave Type Colors** to assign a distinct colour to each leave category (Annual, Sick, Personal, Unpaid, Study). Colors are shown as dots on calendar cells with full-day leave and appear in the calendar legend. Use the same HSV color picker as shift colors.

---

## Home-Screen Widget

ShiftTrack includes a home-screen widget that shows your upcoming shifts.

| Size | Shows |
|------|-------|
| 4×1 | Upcoming days (1–7, configurable) with day number and shift type |

Each widget day cell shows the day number and shift/leave type stacked vertically, using the same colours as the calendar. Half-day leave renders as a split background (top = shift colour, bottom = darker shade). Full-day leave shows the leave type colour and a dot indicator.

**Setup:** Long-press an empty area of your home screen → Widgets → ShiftTrack. Drag the widget to your home screen. A configuration screen appears where you can set the background color, transparency, and number of days.

**Reconfigure:** Long-press the widget → tap the pencil (Reconfigure) icon → adjust settings → tap **Done** to save and return to the home screen.

Tap any day in the widget to open the Day Detail screen for that date.

### When Does the Widget Update?

The widget refreshes automatically when you make any change (override, leave, overtime, settings) or when a background sync completes.

---

## Sharing Your Schedule

### Inviting a Viewer

1. Go to **Settings → Invite a viewer**.
2. Tap **Generate invite link** — a unique link is copied to your clipboard.
3. Send the link to a family member or manager via any messaging app.

The link expires after **7 days** and can only be used once.

### For the Recipient

1. Tap the invite link.
2. ShiftTrack opens to the invite screen showing who sent it.
3. Tap **Accept** to get read-only access to their schedule.

A viewer (spectator) can see the full calendar, dashboard, and all entries but cannot modify anything.

---

## Spectator Mode

During onboarding, toggle **Spectator Only** to skip shift anchor and leave setup. This mode is intended for users who only want to view someone else's schedule.

### Viewing a Spectated Schedule

After accepting an invite link, the host is saved to your **watched list**. Use the dropdown selector at the top of the **Calendar** tab to switch between schedules:

- **My** — your own schedule (hidden in spectator-only mode)
- **Host name** — any accepted invite's schedule, fetched from the cloud

The last selected schedule is remembered when you reopen the app.

### What's Different in Spectator Mode

| Feature | Own schedule | Spectated schedule |
|---------|-------------|-------------------|
| Calendar view | Full month, colour-coded | Full month, colour-coded |
| Day Detail | Can edit overrides, leave, overtime, notes | Read-only — shows shift, leave type, overtime flag, and notes |
| Dashboard | Shows your shifts, leave balance, overtime | Shows the host's upcoming shifts (leave balance and overtime hidden) |
| Widget | Shows your upcoming shifts | Shows the host's upcoming shifts (fetched from the cloud, cached for offline use) |
| Settings | All sections visible | Account, Shift Colors, Leave Type Colors, and Widget visible |
| Push notifications | — | Receive a silent notification when the host edits their schedule; the app refreshes automatically |

---

## Offline Use & Sync

ShiftTrack works fully offline. Every change you make is saved to your device immediately. When you reconnect, data syncs to the cloud automatically in the background.

- You never need to wait for a network connection to use the app.
- If you use the app on multiple devices, the most recent change for each day wins.
- Opening the app after being offline triggers an immediate sync.
- **Spectators** can also view a host's schedule offline — the last fetched data is cached locally and served automatically when the network is unavailable.

---

## Troubleshooting

### Widget not updating
The widget refreshes automatically at midnight and after every local data change. If it still shows old data, try removing and re-adding it. On some devices, you may need to grant the "Alarms & reminders" permission (Settings → Apps → ShiftTrack → Alarms). Open the app to force a sync.

### Sync seems stuck
All changes are saved locally first. Open the app while connected to trigger a sync. Background sync runs within a few minutes of reconnecting.

### Leave balance looks wrong after New Year
The annual roll-over happens automatically on the first launch of each new year. Check **Settings → Leave entitlement** and adjust if needed.

### Sign-in not working
An internet connection is required for the first sign-in. After that, your credentials are cached for offline use.

### Invite link not working
Links expire after 7 days and are single-use. Ask the sender to generate a new one. You must be signed in to accept an invite.

---

## For Developers

See the [docs/](docs/) folder for technical documentation:

- [Architecture overview](docs/architecture.md) — data flow, sync strategy, deep-link scheme
- [Maintenance notes](docs/maintenance.md) — dependency versions, Firebase setup, upgrade checklist
- [Full user guide](docs/user-guide.md) — detailed feature reference

### Quick Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew testDebugUnitTest      # Run unit tests
./gradlew assembleRelease        # Build release APK
firebase deploy --only functions # Deploy Cloud Functions (spectator push notifications)
```

Min SDK 34 · Target SDK 35 · Kotlin 2.0.21 · Jetpack Compose · v4.1.0
