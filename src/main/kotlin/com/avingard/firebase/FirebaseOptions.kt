package com.avingard.firebase

data class FirebaseOptions(
    val apiKey: String,
    val projectId: String,
    val storageBucket: String,
    val firestoreEmulator: Emulator,
    val firebaseAuthEmulator: Emulator,
    val firestorageEmulator: Emulator
)

class FirebaseOptionsBuilder {
    var apiKey = ""
    var projectId = ""
    var storageBucket = ""
    var firestoreEmulator = Emulator()
    var firebaseAuthEmulator = Emulator()
    var firestorageEmulator = Emulator()

    fun build(): FirebaseOptions {
        return FirebaseOptions(
            apiKey = apiKey,
            projectId = projectId,
            storageBucket = storageBucket,
            firebaseAuthEmulator = firebaseAuthEmulator,
            firestorageEmulator = firestorageEmulator,
            firestoreEmulator = firestoreEmulator
        )
    }
}



data class Emulator(
    val useEmulator: Boolean = false,
    val address: String = "",
    val port: Int = 0
)

class EmulatorBuilder {
    var useEmulator = false
    var address = ""
    var port = 0

    fun build(): Emulator {
        return Emulator(
            useEmulator = useEmulator,
            port = port,
            address = address
        )
    }
}



fun firebaseOptions(block: FirebaseOptionsBuilder.() -> Unit): FirebaseOptions {
    val builder = FirebaseOptionsBuilder()
    return builder.apply(block).build()
}

fun emulator(block: EmulatorBuilder.() -> Unit): Emulator {
    val builder = EmulatorBuilder()
    return builder.apply(block).build()
}
