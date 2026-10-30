package app.efa

import efa.Calibration

/** B5: the estimates in use right now. Read when a task is added; refreshed whenever the app loads. */
object CalibrationState {
    @Volatile var cal: Calibration = Calibration()
}
