package tech.figure.aggregate.common.snowflake

import org.slf4j.Logger
import tech.figure.aggregate.common.snowflake.model.TransientTableInfo
import java.sql.Connection
import java.util.*

fun createTransientTable(con: Connection, tableName: String, log: Logger): TransientTableInfo? {
    val uniqueId = UUID.randomUUID().toString().split('-').joinToString("")
    val transientTableName = tableName + "_" + uniqueId
    var columns = ""
    when (tableName) {
        "COIN_TRANSFER" -> {
            columns = """(
                hash VARCHAR,
                event_type VARCHAR,
                block_height DOUBLE,
                block_timestamp TIMESTAMP_TZ,
                tx_hash VARCHAR,
                recipient VARCHAR,
                sender VARCHAR,
                amount VARCHAR,
                denom VARCHAR
            );"""
        }
        "ATTRIBUTES" -> {
            columns = """(
                hash VARCHAR,
                event_type VARCHAR,
                block_height DOUBLE,
                block_timestamp TIMESTAMP_TZ,
                name VARCHAR,
                value STRING,
                type VARCHAR,
                account VARCHAR,
                owner VARCHAR
            );"""
        }
        "MARKER_SUPPLY" -> {
            columns = """(
                hash VARCHAR,
                event_type VARCHAR,
                block_height DOUBLE,
                block_timestamp TIMESTAMP_TZ,
                coins VARCHAR,
                denom VARCHAR,
                amount VARCHAR,
                administrator VARCHAR,
                to_address VARCHAR,
                from_address VARCHAR,
                metadata_base VARCHAR,
                metadata_description VARCHAR,
                metadata_display VARCHAR,
                metadata_denom_units VARCHAR,
                metadata_name VARCHAR,
                metadata_symbol VARCHAR
            );"""
        }
        "MARKER_TRANSFER" -> {
            columns = """(
                hash VARCHAR,
                event_type VARCHAR,
                block_height DOUBLE,
                block_timestamp TIMESTAMP_TZ,
                amount DOUBLE,
                denom VARCHAR,
                administrator VARCHAR,
                to_address VARCHAR,
                from_address VARCHAR
            );"""
        }
        "ERRORS" -> {
            columns = """(
                hash VARCHAR,
                tx_hash VARCHAR,
                block_height DOUBLE,
                block_timestamp TIMESTAMP_TZ,
                error_code INT,
                signer_addr VARCHAR,
                fee VARCHAR,
                fee_denom VARCHAR
            );"""
        }
        "FEES" -> {
            columns = """(
                hash VARCHAR,
                tx_hash VARCHAR,
                block_height DOUBLE,
                block_timestamp TIMESTAMP_TZ,
                fee VARCHAR,
                fee_denom VARCHAR,
                sender VARCHAR
            );"""
        }
        "MEMORIALIZE_CONTRACT" -> {
            columns = """(
                hash VARCHAR,
                event_action_type VARCHAR,
                block_height DOUBLE,
                block_timestamp TIMESTAMP_TZ
            );"""
        }

        "NYCB_USDF_BALANCES" -> {
            columns = """(
                account VARCHAR,
                date VARCHAR,
                balance DOUBLE,
                timestamp TIMESTAMP_TZ,
                height DOUBLE
            );"""
        }
        else -> {
            log.error("invalid table name: $tableName")
        }
    }
    val createTransientTableStmt = "CREATE TRANSIENT TABLE $transientTableName$columns"

    return try {
        val success = con.createStatement().executeUpdate(createTransientTableStmt)
        TransientTableInfo(transientTableName, success)
    } catch (t: Throwable) {
        log.error(t.message)
        null
    }
}

fun insertIntoTransient(con: Connection, transientTableName: String, values: MutableList<String>, log: Logger): Int? {
    val insertStmt = "INSERT INTO $transientTableName VALUES ${values.joinToString(",")}"

    return try {
        con.createStatement().executeUpdate(insertStmt)
    } catch (t: Throwable) {
        log.error(t.message)
        null
    }
}

fun removeTransientTable(con: Connection, transientTableName: String, log: Logger): Int? {
    return try {
        con.createStatement().executeUpdate("DROP TABLE $transientTableName")
    } catch (t: Throwable) {
        log.error(t.message)
        null
    }
}

fun insertIntoFromTransient(con: Connection, transientTableName: String, tableName: String, log: Logger): Int? {
    val insertStmt = "INSERT INTO $tableName (SELECT * FROM $transientTableName)"

    return try {
        con.createStatement().executeUpdate(insertStmt)
    } catch (t: Throwable) {
        log.error(t.message)
        null
    }
}

fun mergeTransientTable(con: Connection, transientTableName: String, tableName: String, log: Logger): Int? {
    var matchOn = ""
    var updateColumnSet = ""
    var insert = ""

    when (tableName) {
        "FEES" -> {
            matchOn = """
                FEES.hash = ${transientTableName}.hash
            """.trimIndent()

            updateColumnSet = """
                FEES.tx_hash = ${transientTableName}.tx_hash,
                FEES.block_height = ${transientTableName}.block_height,
                FEES.block_timestamp = ${transientTableName}.block_timestamp,
                FEES.fee = ${transientTableName}.fee,
                FEES.fee_denom = ${transientTableName}.fee_denom,
                FEES.sender = ${transientTableName}.sender
            """.trimIndent()

            insert = """
                INSERT (hash, tx_hash, block_height, block_timestamp, fee, fee_denom, sender)
                VALUES (
                ${transientTableName}.hash,
                ${transientTableName}.tx_hash,
                ${transientTableName}.block_height,
                ${transientTableName}.block_timestamp,
                ${transientTableName}.fee,
                ${transientTableName}.fee_denom,
                ${transientTableName}.sender
                );
            """.trimIndent()
        }
        "ATTRIBUTES" -> {
            matchOn = """
            ATTRIBUTES.hash = ${transientTableName}.hash
            """

            updateColumnSet = """
            ATTRIBUTES.event_type = ${transientTableName}.event_type,
            ATTRIBUTES.block_height = ${transientTableName}.block_height,
            ATTRIBUTES.block_timestamp = ${transientTableName}.block_timestamp,
            ATTRIBUTES.name = ${transientTableName}.name,
            ATTRIBUTES.value = ${transientTableName}.value,
            ATTRIBUTES.type = ${transientTableName}.type,
            ATTRIBUTES.account = ${transientTableName}.account,
            ATTRIBUTES.owner = ${transientTableName}.owner
            """;

            insert = """
            INSERT (hash, event_type, block_height, block_timestamp, name, value, type, account, owner)
            VALUES (
            ${transientTableName}.hash,
            ${transientTableName}.event_type,
            ${transientTableName}.block_height,
            ${transientTableName}.block_timestamp,
            ${transientTableName}.name,
            ${transientTableName}.value,
            ${transientTableName}.type,
            ${transientTableName}.account,
            ${transientTableName}.owner
            );
            """
        }
        "COIN_TRANSFER" -> {
            matchOn = """
            COIN_TRANSFER.hash = ${transientTableName}.hash
            """

            updateColumnSet = """
            COIN_TRANSFER.event_type = ${transientTableName}.event_type,
            COIN_TRANSFER.block_height = ${transientTableName}.block_height,
            COIN_TRANSFER.block_timestamp = ${transientTableName}.block_timestamp,
            COIN_TRANSFER.tx_hash = ${transientTableName}.tx_hash,
            COIN_TRANSFER.recipient = ${transientTableName}.recipient,
            COIN_TRANSFER.sender = ${transientTableName}.sender,
            COIN_TRANSFER.amount = ${transientTableName}.amount,
            COIN_TRANSFER.denom = ${transientTableName}.denom
            """;

            insert = """
            INSERT (hash, event_type, block_height, block_timestamp, tx_hash, recipient, sender, amount, denom)
            VALUES (
                ${transientTableName}.hash,
            ${transientTableName}.event_type,
            ${transientTableName}.block_height,
            ${transientTableName}.block_timestamp,
            ${transientTableName}.tx_hash,
            ${transientTableName}.recipient,
            ${transientTableName}.sender,
            ${transientTableName}.amount,
            ${transientTableName}.denom
            );
            """
        }
        "MARKER_SUPPLY" -> {
            matchOn = """
            MARKER_SUPPLY.hash = ${transientTableName}.hash
            """

            updateColumnSet = """
            MARKER_SUPPLY.event_type = ${transientTableName}.event_type,
            MARKER_SUPPLY.block_height = ${transientTableName}.block_height,
            MARKER_SUPPLY.block_timestamp = ${transientTableName}.block_timestamp,
            MARKER_SUPPLY.coins = ${transientTableName}.coins,
            MARKER_SUPPLY.denom = ${transientTableName}.denom,
            MARKER_SUPPLY.amount = ${transientTableName}.amount,
            MARKER_SUPPLY.administrator = ${transientTableName}.administrator,
            MARKER_SUPPLY.to_address = ${transientTableName}.to_address,
            MARKER_SUPPLY.from_address = ${transientTableName}.from_address,
            MARKER_SUPPLY.metadata_base = ${transientTableName}.metadata_base,
            MARKER_SUPPLY.metadata_description = ${transientTableName}.metadata_description,
            MARKER_SUPPLY.metadata_display = ${transientTableName}.metadata_display,
            MARKER_SUPPLY.metadata_denom_units = ${transientTableName}.metadata_denom_units,
            MARKER_SUPPLY.metadata_name = ${transientTableName}.metadata_name,
            MARKER_SUPPLY.metadata_symbol = ${transientTableName}.metadata_symbol
            """;

            insert = """
            INSERT (
                hash,
                event_type,
                block_height,
                block_timestamp,
                coins,
                denom,
                amount,
                administrator,
                to_address,
                from_address,
                metadata_base,
                metadata_description,
                metadata_display,
                metadata_denom_units,
                metadata_name,
                metadata_symbol
            ) VALUES (
            ${transientTableName}.hash,
            ${transientTableName}.event_type,
            ${transientTableName}.block_height,
            ${transientTableName}.block_timestamp,
            ${transientTableName}.coins,
            ${transientTableName}.denom,
            ${transientTableName}.amount,
            ${transientTableName}.administrator,
            ${transientTableName}.to_address,
            ${transientTableName}.from_address,
            ${transientTableName}.metadata_base,
            ${transientTableName}.metadata_description,
            ${transientTableName}.metadata_display,
            ${transientTableName}.metadata_denom_units,
            ${transientTableName}.metadata_name,
            ${transientTableName}.metadata_symbol
            );
            """
        }
        "MARKER_TRANSFER" -> {
            matchOn = """
            MARKER_TRANSFER.hash = ${transientTableName}.hash
            """

            updateColumnSet = """
            MARKER_TRANSFER.event_type = ${transientTableName}.event_type,
            MARKER_TRANSFER.block_height = ${transientTableName}.block_height,
            MARKER_TRANSFER.block_timestamp = ${transientTableName}.block_timestamp,
            MARKER_TRANSFER.amount = ${transientTableName}.amount,
            MARKER_TRANSFER.denom = ${transientTableName}.denom,
            MARKER_TRANSFER.administrator = ${transientTableName}.administrator,
            MARKER_TRANSFER.to_address = ${transientTableName}.to_address,
            MARKER_TRANSFER.from_address = ${transientTableName}.from_address
            """;

            insert = """
            INSERT (hash, event_type, block_height, block_timestamp, amount, denom, administrator, to_address, from_address)
            VALUES (
                ${transientTableName}.hash,
            ${transientTableName}.event_type,
            ${transientTableName}.block_height,
            ${transientTableName}.block_timestamp,
            ${transientTableName}.amount,
            ${transientTableName}.denom,
            ${transientTableName}.administrator,
            ${transientTableName}.to_address,
            ${transientTableName}.from_address
            );
            """
        }
        "NYCB_USDF_BALANCES" -> {
            matchOn = """
            NYCB_USDF_BALANCES
            """

            updateColumnSet = """
            NYCB_USDF_BALANCES.balance = ${transientTableName}.balance,
            NYCB_USDF_BALANCES.timestamp = ${transientTableName}.timestamp,
            NYCB_USDF_BALANCES.height = ${transientTableName}.height
            """;

            insert = """
            INSERT (account, date, balance, timestamp, height)
            VALUES (
                ${transientTableName}.account,
            ${transientTableName}.date,
            ${transientTableName}.balance,
            ${transientTableName}.timestamp,
            ${transientTableName}.height
            );
            """
        }
        else -> {
            log.error("Invalid table name: $tableName")
            return null
        }
    }

    val mergeStmt = """
        MERGE INTO $tableName USING $transientTableName ON
        $matchOn WHEN MATCHED THEN UPDATE SET $updateColumnSet WHEN NOT MATCHED THEN $insert
    """.trimIndent().trim(' ')

    return try {
        con.createStatement().executeUpdate(mergeStmt)
    } catch (t: Throwable) {
        log.error(t.message)
        null
    }
}

fun disableErrorOnDuplicateMerge(con: Connection, log: Logger): Int? {
    return try {
        con.createStatement().executeUpdate("ALTER SESSION SET ERROR_ON_NONDETERMINISTIC_MERGE=false;")
    } catch (t: Throwable) {
        log.error(t.message)
        null
    }
}

fun upsert(con: Connection, values: MutableList<String>, tableName: String, log: Logger) {
    val transientTableInfo = createTransientTable(con, tableName, log)
        ?: throw IllegalStateException("Failed to Create Transient Table")
    log.info("Created Transient: ${transientTableInfo.tableName} " + "Status: ${transientTableInfo.result}")

    val insertResult = insertIntoTransient(con, transientTableInfo.tableName, values, log)
    log.info("Insert Transient Status: ${insertResult ?: "Failed Insert into transient table Transaction"}")

    val disableResultSet = disableErrorOnDuplicateMerge(con, log)
    log.info("Disable Duplicate Error Status: ${disableResultSet ?: "Failed Duplicate Error Status Transaction"}")

    val mergeResultSet = mergeTransientTable(con, transientTableInfo.tableName, tableName, log)
    log.info("Merge Statement Status: ${mergeResultSet ?: "Failed Merge Transient Table into permanent table Transaction"}")

    val removeResultSet = removeTransientTable(con, transientTableInfo.tableName, log)
    log.info("Remove transient table status: ${removeResultSet ?: "Failed Remove Transient Table Transaction"}")

    removeResultSet
}

fun insert(con: Connection, values: MutableList<String>, tableName: String, log: Logger) {
    val transientTableInfo = createTransientTable(con, tableName, log)
        ?: throw IllegalStateException("Failed to Create Transient Table")
    log.info("Created Transient: ${transientTableInfo.tableName} \n" +
            "Status: ${transientTableInfo.result}")

    val insertResultSet = insertIntoTransient(con, transientTableInfo.tableName, values, log)
    log.info("Insert Transient Status: ${insertResultSet ?: "Failed Insert into transient table"}")

    val disableResultSet = disableErrorOnDuplicateMerge(con, log)
    log.info("Disable Duplicate Error Status: ${disableResultSet ?: "Failed Duplicate Error Status "}")

    val insertFromResultSet = insertIntoFromTransient(con, transientTableInfo.tableName, tableName, log)
    log.info("Merge Statement Status: ${insertFromResultSet ?: "Failed insert from Transient Table into permanent table"}")

    val removeResultSet = removeTransientTable(con, transientTableInfo.tableName, log)
    log.info("Remove transient table status: ${removeResultSet ?: "Failed Remove Transient Table"}")
}
