package app.efa

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/** Every device test starts with the first-open walkthrough off, as it does with the setup gate, so it never covers the screen a test is about. The walkthrough's own tests turn it on. */
class EfaTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) { WalkthroughGate.enforced = false; Shelves.startOpen = true; super.onCreate(arguments) }
}
