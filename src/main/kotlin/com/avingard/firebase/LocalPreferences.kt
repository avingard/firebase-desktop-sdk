package com.avingard.firebase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

class LocalPreferences private constructor() {
    private val properties = Properties()
    private val mutex = Mutex()

    suspend fun getPreference(name: String): String? = mutex.withLock {
        tryImport()
        return properties.getProperty(name)
    }

    suspend fun setPreference(name: String, value: String) = mutex.withLock {
        properties.setProperty(name, value)
        export(name)
    }

    suspend fun clear() = mutex.withLock {
        if (properties.isNotEmpty()) {
            properties.clear()
            export("clear")
        }
    }

    private suspend fun tryImport()  {
        if (!properties.containsKey("load")) {
            withContext(Dispatchers.IO) {
                try {
                    properties.loadFromXML(FileInputStream("firebase_prefs"))
                } catch (e: Exception) {
                    LOG.error("LocalPreferences: Couldn't import xml file", e)
                } finally {
                    properties.setProperty("load", "")
                }
            }
        }
    }

    private suspend fun export(name: String) {
        withContext(Dispatchers.IO) {
            try {
                properties.storeToXML(FileOutputStream("firebase_prefs"), "Firebase Preferences")
            } catch (e: Exception) {
                LOG.error("LocalPreferences: Couldn't export $name to xml file", e)
            }
        }
    }

    companion object {
        val instance by lazy { LocalPreferences() }
    }
}