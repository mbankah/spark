package efa

const val TIMETABLE_FORMAT_VERSION = 1

/**
 * The text an AI is given to build a file (IM3). The app copies it from here, docs/TIMETABLE_CSV.md holds the same text, and a test fails
 * if the two differ, so the table the AI reads is always the table the reader enforces.
 */
val TIMETABLE_AI_PROMPT: String = listOf(
    "Build a CSV file for the Spark app from the timetable or dates I give you below. Reply with the CSV only.",
    "",
    "Rules",
    "- The first line is exactly: ${CSV_COLUMNS.joinToString(",")}",
    "- One row per line, with every cell present. Leave a cell empty if it is not used. Put a cell in double quotes if it has a comma in it.",
    "- Dates are year-month-day, like 2026-11-02. Times are 24-hour, like 09:00. All times are UK time.",
    "- kind is event, deadline or period.",
    "",
    "event: a fixed commitment on one date",
    "- Needs: date, start, end, title, type. Optional: venue, study, action, notes.",
    "- type is one of: ${EVENT_TYPES.joinToString(", ")}.",
    "- action is empty or add to add it, or cancel to remove the commitment of that type that starts at that time on that date.",
    "- A moved lecture is two rows: cancel at the old time, add at the new time. Moving a lecture also moves its walk, so cancel and add the travel rows too.",
    "- study is standard, light or none. Empty means standard for a lecture or tutorial that is added, and none for everything else. Use none when the lecture only replaces one that was already there.",
    "",
    "deadline: work that is due",
    "- Needs: date, start (the time it is due), title. Optional: heaviness, a whole number from 1 (tiny) to 10 (huge). Empty means 5.",
    "",
    "period: an assignment week or a catch-up week, 1 to 14 days",
    "- Needs: date (first day), end_date (last day), type (assignment_week or catch_up_week). Optional: title.",
    "",
    "Do not guess. If a date, time or type is not in what I give you, leave that row out.",
).joinToString("\n")

/** A small file that is valid on any day before its dates. Shown in docs/TIMETABLE_CSV.md and checked by a test. */
val TIMETABLE_EXAMPLE_CSV: String = listOf(
    CSV_COLUMNS.joinToString(","),
    "event,2026-11-03,,16:30,17:30,Cryptography lecture,lecture,Watson LT A,,standard,,",
    "event,2026-11-02,,09:00,10:00,Randomness and Computation lecture,lecture,,,,cancel,Moved to Wednesday",
    "event,2026-11-04,,09:00,10:00,Randomness and Computation lecture,lecture,Murray LC-UG05,,none,,",
    "deadline,2026-11-06,,12:00,,CS Project report,,,8,,,",
    "period,2026-11-02,2026-11-06,,,Assignment week,assignment_week,,,,,",
).joinToString("\n")
