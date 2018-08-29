package de.intektor.auto.epaper

/**
 * @author Intektor
 */
fun launchEmulator(emulatorExecutable: String, emulatorName: String) {
    val process = ProcessBuilder()
            .command("$emulatorExecutable\\emulator.exe", "-avd", emulatorName)
            .start()

    process.waitFor()
}

fun main(args: Array<String>) {
    launchEmulator(args[0], args[1])
}