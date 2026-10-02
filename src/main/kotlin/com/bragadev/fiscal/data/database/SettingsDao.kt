package com.bragadev.fiscal.data.database

class SettingsDao(private val database: Database) {

    suspend fun getAll(): Map<String, String> = database.use {
        createStatement().use { statement ->
            statement.executeQuery("SELECT key, value FROM settings").use { rows ->
                generateSequence { if (rows.next()) rows.getString("key") to rows.getString("value") else null }.toMap()
            }
        }
    }

    suspend fun putAll(values: Map<String, String?>) = database.use {
        autoCommit = false
        try {
            values.forEach { (key, value) -> if (value == null) delete(key) else put(key, value) }
            commit()
        } catch (error: Exception) {
            rollback()
            throw error
        } finally {
            autoCommit = true
        }
    }

    private fun java.sql.Connection.put(key: String, value: String) {
        prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)").use { statement ->
            statement.setString(1, key)
            statement.setString(2, value)
            statement.executeUpdate()
        }
    }

    private fun java.sql.Connection.delete(key: String) {
        prepareStatement("DELETE FROM settings WHERE key = ?").use { statement ->
            statement.setString(1, key)
            statement.executeUpdate()
        }
    }
}
