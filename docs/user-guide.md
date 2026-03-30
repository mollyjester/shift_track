# ShiftTrack — User Documentation

## Quick Start Guide

### Step 1 — First Launch and Sign In

1. Open **ShiftTrack** on your Android device.
2. Tap **Sign in with Google** and choose your Google account.
3. You will be taken to the **Onboarding** screen.

### Step 2 — Set the Anchor Date

The app uses a rotating 5-day cycle (Day → Day → Night → Rest → Off). To calculate your schedule correctly, it needs to know **one specific date and which position in the cycle that date falls on.**

1. On the Onboarding screen, select the **date** for which you know your shift position.
2. Tap the **cycle position** that matches that date (Day, Day, Night, Rest, or Off).
3. Tap **Next**.

> **Tip:** Use today's date and whatever shift you are working right now.

### Step 3 — Enter Your Leave Balance

1. Set leave allowances for each leave category:
   - **Annual** — default 28 days
   - **Sick** — default 10 days
   - **Personal** — default 5 days
   - **Unpaid** — default 0 days
   - **Study** — default 0 days
2. Use the **+/−** buttons or type a number directly for each category.
3. The app will track how many days you use per category as you record leave.
4. Tap **Finish** to complete onboarding.

### Step 4 — Navigating the App

The bottom navigation bar has three sections:

| Tab | What you see |
|---|---|
| **Home** (Dashboard) | Today's shift, the next 6 days, leave balance remaining, weekly/annual overtime |
| **Calendar** | A full month view with each day's shift colour-coded |
| **Settings** | Anchor date, leave entitlement, overtime compensation, and invite management |

---

## Feature Reference

### Dashboard

- **Shift cards** — Each card shows the date, weekday, and shift type. Today's card is highlighted.
- **Leave balance** — Shows per-category progress bars (Annual, Sick, Personal, Unpaid, Study). Each bar gradually fills as leave is consumed. Remaining days are shown as `remaining / total` next to each bar.
- **Overtime summary** — Shows hours worked this week and the cumulative yearly total.

### Calendar

- Tap any day to open the **Day Detail** screen for that day.
- **Half-day leave** is shown as a split cell — the top half displays the shift colour and the bottom half uses a darker shade.
- **Full-day leave** (non-half-day) shows a coloured dot matching the leave type: Annual (green), Sick (red), Personal (blue), Unpaid (orange), Study (purple).
- The **legend** below the calendar shows both shift types and leave types with large colour circles.
- Days with overtime show an overtime indicator.
- Manual overrides (days where you changed the computed shift) are visually distinguished.

### Day Detail Screen

Opening a day shows:

- The computed shift (from the cadence engine).
- Any manually set override.
- Any leave recorded for that day.
- Any overtime recorded.
- A **notes** field for attaching free-text notes to the day.

**Available actions:**

| Action | How |
|---|---|
| Override shift | Tap a shift type button (Day / Night / Rest / Off / Leave) |
| Clear override | Tap the current override to remove it and restore the computed shift |
| Add leave | Tap **Add Leave**, choose leave type (Annual / Sick / Personal / Unpaid / Study), toggle **Half Day** if needed, optionally add a note |
| Remove leave | Tap the leave entry and confirm removal |
| Add overtime | Tap **Log Overtime**, enter hours, optionally add a note |
| Remove overtime | Tap the overtime entry and confirm removal |
| Save note | Enter text in the notes field and tap **Save note** |

### Settings

| Setting | Description |
|---|---|
| **Anchor date** | Change the reference date for the cadence engine. You may need to update this if your shift pattern was re-based. |
| **Leave entitlement** | Per-category leave days for the current year (Annual, Sick, Personal, Unpaid, Study). The app resets automatically on the first launch of each new year, carrying over each category's total independently. |
| **Overtime compensation** | Mark hours as compensated (paid out or taken as time-off). This does not remove them from the history. |
| **Shift colors** | Customise the colour for each shift type (Day, Night, Rest, Off, Leave) using a color picker with Hue, Saturation, and Brightness sliders. A preview swatch shows your selection in real time. Tap **Reset to default** to revert. Changes take effect immediately across the calendar, dashboard, day detail screens, and widget. |
| **Widget** | Configure widget appearance by long-pressing the widget and tapping the pencil icon, or from **Settings → Widget**. |
| **Invite a viewer** | Generate a 7-day invite link to share your schedule with a family member or manager (see *Invite Guide* below). |

---

## Invite Guide

### Generating an Invite

1. Go to **Settings → Invite a viewer**.
2. Tap **Generate invite link**.
3. A unique link is created and copied to your clipboard, e.g., `shiftapp://invite/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`.
4. Share this link via any messaging app.

> **Note:** The link expires after **7 days**. After it has been redeemed once, it cannot be reused. Generate a new link if needed.

### Redeeming an Invite (Recipient Steps)

1. Tap the invite link on your device.
2. ShiftTrack opens on the **Invite Redemption** screen.
3. You will see the name of the person who sent the invite.
4. Tap **Accept** to gain read-only access to their schedule.

> You must be signed in to accept an invite. If you are not signed in, you will be taken to the sign-in screen first.

### What a Spectator Can See

- The host's full shift calendar (all days, overrides, leave, overtime) via the Calendar tab dropdown selector.
- Tapping any day in the host's calendar opens the **Day Detail** screen showing the shift type, leave information, overtime status, and notes — all in read-only mode.
- Schedule updates are pushed automatically via push notifications. The calendar and widget refresh when the host makes changes.
- Previously viewed schedule data is cached locally, so the calendar is viewable even when offline.

### What a Spectator Cannot Do

- Add, change, or remove any shifts, leave, or overtime entries.
- Modify any settings for the host's account.

### Spectator-Only Mode (Onboarding)

During onboarding, toggle **Spectator Only** to skip shift anchor and leave balance setup entirely. In spectator-only mode:

- The **Dashboard** shows the selected host's upcoming shifts, including today's shift card and the coming days. Leave balance and overtime sections are hidden since those are local-only data. If no host is selected yet, a prompt directs you to the Calendar tab.
- The **Calendar** tab shows a dropdown selector with your watched hosts (no "My" option since you have no own schedule).
- The **Settings** screen shows Account, Shift Colors, Leave Type Colors, and Widget sections. Schedule, leave allowance, overtime, and invite sections are hidden.
- The **Widget** shows the selected host's upcoming shifts, fetched from the cloud. Colour settings are applied the same way as for own-schedule users.
- The **Day Detail** screen shows shift information, leave type and half-day status, overtime flag, and notes — all in read-only mode. Tapping any day on the calendar opens this view.

### Switching Between Schedules

After accepting one or more invite links, use the **dropdown** at the top of the Calendar tab to switch between:

- **My** — your own schedule (not shown in spectator-only mode)
- **Host names** — each accepted invite appears by name

The last selected schedule is remembered across app restarts.

---

## Widget Setup

### Adding the Widget

1. Long-press an empty area of your home screen.
2. Tap **Widgets**.
3. Find **ShiftTrack** in the list.
4. Drag the **4×1** widget to your home screen (or the **2×2** for a compact today-only view).

### What Each Size Shows

| Size | Content |
|---|---|
| **2×2** | Today's shift |
| **4×1** | Today + upcoming days (1–7, configurable). Each day cell shows the day-of-month number and shift/leave type stacked vertically, matching calendar colours. Half-day leave renders as a split background; full-day leave shows a coloured dot. |

### Configuring the Widget

To configure the widget, long-press it on your home screen and tap the **pencil** (Reconfigure) icon in the system menu. This opens the Widget Settings screen where you can adjust:

| Option | Description |
|---|---|
| **Background color** | Pick any colour using the color picker (Hue, Saturation, Brightness sliders). Default is a light surface tone. |
| **Transparency** | Slider from 0% (fully transparent) to 100% (fully opaque). Default is 100%. |
| **Days to show** | Number of days displayed in the wide (4×1) widget, from 1 to 7. Default is 4. |

When you're finished, tap the **Done** button to save your settings and return to the home screen.

You can also adjust these settings from within the app at **Settings → Widget**.

Changes are applied immediately — no need to remove and re-add the widget.

> **Note:** The widget also uses your custom shift colors from **Settings → Shift Colors**, so the calendar, dashboard, and widget all stay consistent.

### Tapping Widget Days

Tap any day shown in the widget to open ShiftTrack directly to the Day Detail screen for that date. This uses the deep-link `shiftapp://day/{date}`.

### Widget Refresh

The widget updates automatically:
- **At midnight** — an exact alarm triggers a refresh so the widget always shows the correct day, even if you don't open the app.
- After every local data change (override, leave, overtime).
- After settings changes (anchor date, colors, widget config).
- After sync completes (coming back online, push notifications).

> **Note:** On some devices, you may need to grant the "Alarms & reminders" permission for midnight refresh to work (Settings → Apps → ShiftTrack → Alarms).

---

## Troubleshooting

### Widget not updating

- The widget refreshes after every local data change. If it still shows old data, try removing and re-adding it.
- The system may delay widget updates to conserve battery. Open the app to force a sync.

### Sync delay after offline use

- All changes are saved locally immediately. When you reconnect, the app syncs automatically in the background within a few minutes.
- If you are in a hurry, open the app — opening it triggers an immediate sync request.

### Annual reset prompt not appearing

- The annual leave roll-over fires silently on the first launch of the new year. If you miss it, the new year's leave balance is created automatically with the previous year's entitlement copied over.
- Check **Settings → Leave entitlement** to verify the new year's total is correct and adjust if needed.

### Sign-in not working

- Ensure you have an active internet connection for the first sign-in.
- Once signed in, the app caches your credentials so you can use it offline.

### Invite link not working

- The link expires after 7 days. Ask the sender to generate a new one.
- Each link can only be redeemed once. If you have already accepted an invite from this person, no action is needed.

---

## Experimental Features

### Auto Wake-Up Alarms

ShiftTrack can set wake-up alarms in your phone's Clock app before DAY shifts.

#### Setup

1. Go to **Settings → Experimental Features**.
2. Toggle **Auto wake-up alarms** on.
3. Configure the defaults:
   - **Evening trigger** — When to send the notification (default 21:00).
   - **First alarm at** — Time of the first alarm (default 04:30).
   - **Number of alarms** — How many alarms to set (default 4, max 10).
   - **Interval** — Minutes between each alarm (default 10, range 5–20).
4. A preview line shows the computed alarm times.

#### Using It

The evening before a DAY shift, you'll receive a high-priority notification:

> **Day shift tomorrow — Mon, 31 Mar**
> Tap to set wake-up alarms

Tap the notification to open the alarm setter screen:

1. You'll see the **default alarm times** at the top.
2. Toggle **Custom alarms for this day** if you want different times.
3. Adjust the first alarm time, count, and interval as needed.
4. Tap **Set alarms** to silently create the alarms in your Clock app.

Custom per-day settings are saved and synced to the cloud.

#### Requirements

- Android 14+ (API 34)
- The `SCHEDULE_EXACT_ALARM` permission must be granted (the app requests it automatically).
- The `SET_ALARM` permission is declared in the manifest.
- Only available for host users (not spectators).
