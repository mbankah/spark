# Timetable CSV, version 1

One format, read the same way on every platform (ledger IM1 to IM3). Spark checks the whole file before it changes anything. A file with
any error is rejected as a whole, and each error names the line and the column.

## Getting a file into Spark

- **Open with** Spark from a file manager, or **Share** a file or plain text to Spark from any app (an AI app, email, Drive).
- Or **Setup, Import a timetable file**, then choose the file.
- Spark always shows a preview first: what is added, what is cancelled, the Buffer before and after, and any warnings. Nothing is applied
  until you press Apply. A file that cancels something or takes time from a work block needs the typed wall of 75 characters (IM6).
- Importing the same file twice changes nothing the second time (IM8).

## The columns

Every column must be in the header (any order). Leave a cell empty when it is not used.

| Column | Used by | Meaning |
| --- | --- | --- |
| kind | all | `event`, `deadline` or `period` |
| date | all | `2026-11-02` or `02/11/2026`. For a period, the first day. |
| end_date | period | The last day. A period is 1 to 14 days. |
| start | event, deadline | `09:00`, 24-hour UK time. For a deadline, the time it is due. |
| end | event | `10:00`. After the start, on the same day. |
| title | all | Up to 120 characters. Required for event and deadline. |
| type | event, period | Event: lecture, tutorial, mytutor, training, match, gym, church, errand, travel. Period: `assignment_week` or `catch_up_week`. |
| venue | event | The building and room, as on the timetable. Spark matches it to its venue directory (L4). |
| heaviness | deadline | A whole number 1 to 10. Empty means 5 (D2). |
| study | event | `standard`, `light` or `none`. Empty means standard for an added lecture or tutorial, otherwise none (D1). |
| action | event | Empty or `add`, or `cancel`. |
| notes | event | Free text. |

## What a file can and cannot do

- **Add** events, deadlines and periods freely. Adding only tightens.
- **Cancel** a fixed commitment, or add an event that takes time from a study or code block, and Spark asks for the typed wall once for the
  whole file (IM6).
- A file **cannot** create, shorten or remove a study block, a meal, sleep or a day off. Those types are refused. A day off and a library
  period are planned in the app (PW1, H1).
- An event on top of sleep, a meal, a walk or another commitment is an error that names it. To move a lecture, cancel the old row and its
  walk, then add the new rows (the same file).
- A lecture or tutorial that is added brings its study block with it (D1), unless `study` is `none`.
- Rows dated before today are skipped with a warning. Dates more than a year ahead are treated as a typo.
- Up to 1 MB and 2000 rows, UTF-8, commas, semicolons or tabs between cells. Nothing in the file is ever calculated.

## Give this to an AI

Copy the text below, or press **Copy the format for an AI** in Spark's import screen, paste it, then paste your timetable underneath.

```
Build a CSV file for the Spark app from the timetable or dates I give you below. Reply with the CSV only.

Rules
- The first line is exactly: kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes
- One row per line, with every cell present. Leave a cell empty if it is not used. Put a cell in double quotes if it has a comma in it.
- Dates are year-month-day, like 2026-11-02. Times are 24-hour, like 09:00. All times are UK time.
- kind is event, deadline or period.

event: a fixed commitment on one date
- Needs: date, start, end, title, type. Optional: venue, study, action, notes.
- type is one of: lecture, tutorial, mytutor, training, match, gym, church, errand, travel.
- action is empty or add to add it, or cancel to remove the commitment of that type that starts at that time on that date.
- A moved lecture is two rows: cancel at the old time, add at the new time. Moving a lecture also moves its walk, so cancel and add the travel rows too.
- study is standard, light or none. Empty means standard for a lecture or tutorial that is added, and none for everything else. Use none when the lecture only replaces one that was already there.

deadline: work that is due
- Needs: date, start (the time it is due), title. Optional: heaviness, a whole number from 1 (tiny) to 10 (huge). Empty means 5.

period: an assignment week or a catch-up week, 1 to 14 days
- Needs: date (first day), end_date (last day), type (assignment_week or catch_up_week). Optional: title.

Do not guess. If a date, time or type is not in what I give you, leave that row out.
```

## Example

```
kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes
event,2026-11-03,,16:30,17:30,Cryptography lecture,lecture,Watson LT A,,standard,,
event,2026-11-02,,09:00,10:00,Randomness and Computation lecture,lecture,,,,cancel,Moved to Wednesday
event,2026-11-04,,09:00,10:00,Randomness and Computation lecture,lecture,Murray LC-UG05,,none,,
deadline,2026-11-06,,12:00,,CS Project report,,,8,,,
period,2026-11-02,2026-11-06,,,Assignment week,assignment_week,,,,,
```

The files used to test every platform are in `core/src/test/resources/csv/`. A `good-` file must be accepted. A `bad-LINE-COLUMN-` file
must be rejected with an error on that line and column (`file` means the whole file).
