/*
 * Web Worker for androidx.sqlite WebWorkerSQLiteDriver (spec v2.6.1 §3.3.1, ADR-004).
 *
 * Implements the driver's message protocol (open / prepare / step / close) on top of SQLite WASM,
 * like the reference worker in androidx (sqlite/sqlite-web-worker-test, Apache-2.0), with one change:
 * databases live in the Origin Private File System through the "opfs-sahpool" VFS instead of
 * oo1.OpfsDb. SAHPool needs no SharedArrayBuffer, so the page works WITHOUT cross-origin isolation
 * (no COOP/COEP headers) and can be hosted on any static host. It holds exclusive OPFS handles:
 * only one tab of the app can open the database at a time (a second tab gets a clear error).
 *
 * Protocol: request  {id, data: {cmd, ...}}
 *           response {id, data: {...}} or {id, error: "message"}; `close` is one-way.
 */
import sqlite3InitModule from '@sqlite.org/sqlite-wasm';

const VFS_NAME = 'todo-sahpool';
const VFS_DIRECTORY = '.todo-db';
const POOL_CAPACITY = 10;
const LOG_PREFIX = '[todo-sqlite-worker]';

let sqlite3 = null;
let pool = null;
let initError = null;

const databases = new Map(); // databaseId -> oo1 DB
const statements = new Map(); // statementId -> oo1 Stmt
let nextDatabaseId = 0;
let nextStatementId = 0;

const isSet = (value) => value !== undefined && value !== null;

function reply(id, data) {
    postMessage({ id: id, data: data });
}

function replyError(id, error) {
    const message = error && error.message ? error.message : String(error);
    postMessage({ id: id, error: message });
}

function openRequest(id, request) {
    if (initError) {
        replyError(id, initError);
        return;
    }
    try {
        // SAHPool stores files by absolute name inside its own pool directory.
        const name = request.fileName.startsWith('/') ? request.fileName : '/' + request.fileName;
        const database = new pool.OpfsSAHPoolDb(name);
        const databaseId = nextDatabaseId++;
        databases.set(databaseId, database);
        reply(id, { databaseId: databaseId });
    } catch (error) {
        replyError(id, error);
    }
}

function prepareRequest(id, request) {
    const database = databases.get(request.databaseId);
    if (!database) {
        replyError(id, 'Invalid database ID: ' + request.databaseId);
        return;
    }
    try {
        const statement = database.prepare(request.sql);
        const statementId = nextStatementId++;
        statements.set(statementId, statement);
        const columnNames = [];
        for (let i = 0; i < statement.columnCount; i++) {
            columnNames.push(sqlite3.capi.sqlite3_column_name(statement, i));
        }
        reply(id, {
            statementId: statementId,
            parameterCount: sqlite3.capi.sqlite3_bind_parameter_count(statement),
            columnNames: columnNames,
        });
    } catch (error) {
        replyError(id, error);
    }
}

function stepRequest(id, request) {
    const statement = statements.get(request.statementId);
    if (!statement) {
        replyError(id, 'Invalid statement ID: ' + request.statementId);
        return;
    }
    try {
        const rows = [];
        const columnTypes = [];
        statement.reset();
        statement.clearBindings();
        for (let i = 0; i < request.bindings.length; i++) {
            statement.bind(i + 1, request.bindings[i]);
        }
        while (statement.step()) {
            if (columnTypes.length === 0) {
                for (let i = 0; i < statement.columnCount; i++) {
                    columnTypes.push(sqlite3.capi.sqlite3_column_type(statement, i));
                }
            }
            rows.push(statement.get([]));
        }
        reply(id, { rows: rows, columnTypes: columnTypes });
    } catch (error) {
        replyError(id, error);
    }
}

function closeRequest(id, request) {
    // One-way command: errors are only logged, the driver does not wait for a reply.
    try {
        if (isSet(request.statementId)) {
            const statement = statements.get(request.statementId);
            if (statement) {
                statement.finalize();
                statements.delete(request.statementId);
            }
        }
        if (isSet(request.databaseId)) {
            const database = databases.get(request.databaseId);
            if (database) {
                database.close();
                databases.delete(request.databaseId);
            }
        }
    } catch (error) {
        console.warn(LOG_PREFIX, 'close failed:', error);
    }
}

const commands = {
    open: openRequest,
    prepare: prepareRequest,
    step: stepRequest,
    close: closeRequest,
};

function handleMessage(event) {
    const message = event.data;
    if (!message || !isSet(message.data)) {
        postMessage({ id: message && message.id, error: "Invalid request, missing 'data'." });
        return;
    }
    const handler = commands[message.data.cmd];
    if (!handler) {
        console.warn(LOG_PREFIX, 'unknown command', JSON.stringify(message.data));
        postMessage({ id: message.id, error: "Invalid request, unknown command: '" + message.data.cmd + "'." });
        return;
    }
    handler(message.id, message.data);
}

const pending = [];
let ready = false;
onmessage = (event) => {
    if (ready) {
        handleMessage(event);
    } else {
        pending.push(event);
    }
};

async function init() {
    try {
        sqlite3 = await sqlite3InitModule();
        // Each database file and its rollback journal take one slot, and the pool never grows by itself
        // ("SAH pool is full"). todo.db plus a VACUUM INTO snapshot before a migration (FIX-01) need four;
        // the rest is headroom. initialCapacity only applies to a new pool, so older pools are topped up.
        pool = await sqlite3.installOpfsSAHPoolVfs({
            name: VFS_NAME,
            directory: VFS_DIRECTORY,
            initialCapacity: POOL_CAPACITY,
        });
        await pool.reserveMinimumCapacity(POOL_CAPACITY);
        console.info(LOG_PREFIX, 'ready: SQLite', sqlite3.version.libVersion, 'VFS', VFS_NAME,
            'files', pool.getFileCount() + '/' + pool.getCapacity());
    } catch (error) {
        const reason = error && error.message ? error.message : String(error);
        initError = 'Local database storage is unavailable (OPFS SAHPool): ' + reason;
        console.error(LOG_PREFIX, initError);
    }
    ready = true;
    while (pending.length > 0) {
        handleMessage(pending.shift());
    }
}

init();
