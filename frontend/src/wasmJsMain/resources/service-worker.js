const CACHE_PREFIX = "kassette-offline-";
const VERSION_CACHE_PREFIX = `${CACHE_PREFIX}version-`;
const METADATA_CACHE = `${CACHE_PREFIX}metadata`;
const CACHE_STATE_KEY = new URL("__cache_state__", self.registration.scope).href;
const APP_SHELL = [
    "./",
    "index.html",
    "kassette.js",
    "manifest.webmanifest",
    "favicon.ico",
    "icons/icon-192.png",
    "icons/icon-512.png",
    "icons/icon-192-maskable.png",
    "icons/icon-512-maskable.png",
    "icons/apple-touch-icon.png",
    "nes20db.csv",
    "shaders/crt.sksl",
    "shaders/palette.sksl",
];

const scopedUrl = (path) => new URL(path, self.registration.scope).href;

async function cacheState() {
    const metadata = await caches.open(METADATA_CACHE);
    const response = await metadata.match(CACHE_STATE_KEY);
    return response ? response.json() : {};
}

async function setCacheState(state) {
    const metadata = await caches.open(METADATA_CACHE);
    await metadata.put(CACHE_STATE_KEY, new Response(JSON.stringify(state), {
        headers: { "Content-Type": "application/json" },
    }));
}

async function versionCacheName(assets) {
    const chunks = await Promise.all(APP_SHELL.map(async (path) => {
        const pathBytes = new TextEncoder().encode(path);
        const contentBytes = new Uint8Array(await assets.get(path).response.clone().arrayBuffer());
        const chunk = new Uint8Array(4 + pathBytes.length + contentBytes.length);
        new DataView(chunk.buffer).setUint32(0, pathBytes.length);
        chunk.set(pathBytes, 4);
        chunk.set(contentBytes, 4 + pathBytes.length);
        return chunk;
    }));
    const bytes = new Uint8Array(chunks.reduce((size, chunk) => size + chunk.length, 0));
    let offset = 0;
    chunks.forEach((chunk) => {
        bytes.set(chunk, offset);
        offset += chunk.length;
    });
    const digest = await crypto.subtle.digest("SHA-256", bytes);
    const hash = [...new Uint8Array(digest)]
        .map((byte) => byte.toString(16).padStart(2, "0"))
        .join("")
        .slice(0, 16);
    return `${VERSION_CACHE_PREFIX}${hash}`;
}

async function deleteObsoleteCaches(keepNames) {
    const namesToKeep = keepNames || Object.values(await cacheState());
    const cacheNames = await caches.keys();
    await Promise.all(cacheNames
        .filter((name) => name.startsWith(CACHE_PREFIX)
            && name !== METADATA_CACHE
            && !namesToKeep.includes(name))
        .map((name) => caches.delete(name)));
}

async function fetchFresh(path) {
    const request = new Request(scopedUrl(path), { cache: "reload" });
    const response = await fetch(request);
    if (!response.ok) {
        throw new Error(`Unable to download ${path}: ${response.status}`);
    }
    return { request, response };
}

async function downloadCurrentVersion(activate = true) {
    const shellAssets = await Promise.all(APP_SHELL.map(async (path) => [path, await fetchFresh(path)]));
    const assets = new Map(shellAssets);
    const cacheName = await versionCacheName(assets);
    const state = await cacheState();
    const currentName = state.active;
    if (cacheName === currentName) {
        if (!activate && state.pending) {
            await setCacheState({ active: state.active, previous: state.previous });
        }
        return false;
    }

    const bundle = await assets.get("kassette.js").response.clone().text();
    const wasmPaths = [...new Set([...bundle.matchAll(/[a-f0-9]{20}\.wasm/g)].map((match) => match[0]))];
    if (wasmPaths.length === 0) {
        throw new Error("No WebAssembly assets found in kassette.js");
    }

    const wasmAssets = await Promise.all(wasmPaths.map(async (path) => [path, await fetchFresh(path)]));
    wasmAssets.forEach(([path, asset]) => assets.set(path, asset));

    const confirmationAssets = new Map(await Promise.all(
        APP_SHELL.map(async (path) => [path, await fetchFresh(path)])
    ));
    if (await versionCacheName(confirmationAssets) !== cacheName) {
        throw new Error("A newer release was published while downloading; retrying on the next check");
    }

    await caches.delete(cacheName);
    const cache = await caches.open(cacheName);
    try {
        await Promise.all([...assets.values()].map(({ request, response }) => cache.put(request, response)));
    } catch (error) {
        await caches.delete(cacheName);
        throw error;
    }

    if (activate) {
        await deleteObsoleteCaches([cacheName, currentName].filter(Boolean));
        await setCacheState({ active: cacheName, previous: currentName });
    } else {
        await setCacheState({ ...state, pending: cacheName });
    }
    return true;
}

let updatePromise;
function checkForUpdate() {
    if (!updatePromise) {
        updatePromise = downloadCurrentVersion().finally(() => {
            updatePromise = undefined;
        });
    }
    return updatePromise;
}

self.addEventListener("install", (event) => {
    event.waitUntil((async () => {
        await downloadCurrentVersion(false);
        await self.skipWaiting();
    })());
});

self.addEventListener("activate", (event) => {
    event.waitUntil((async () => {
        const state = await cacheState();
        if (state.pending) {
            await deleteObsoleteCaches([state.pending, state.active].filter(Boolean));
            await setCacheState({ active: state.pending, previous: state.active });
        }
        await deleteObsoleteCaches();
        await self.clients.claim();
    })());
});

self.addEventListener("message", (event) => {
    if (event.data !== "CHECK_FOR_UPDATE") {
        return;
    }

    event.waitUntil((async () => {
        try {
            const updated = await checkForUpdate();
            event.ports[0]?.postMessage({ updated });
            if (updated) {
                const clients = await self.clients.matchAll({ type: "window" });
                clients
                    .filter((client) => client.id !== event.source?.id)
                    .forEach((client) => client.postMessage("VERSION_UPDATED"));
            }
        } catch (error) {
            event.ports[0]?.postMessage({ updated: false, error: String(error) });
        }
    })());
});

self.addEventListener("fetch", (event) => {
    const request = event.request;
    const url = new URL(request.url);

    if (request.method !== "GET" || url.origin !== self.location.origin) {
        return;
    }

    event.respondWith((async () => {
        const { active: cacheName } = await cacheState();
        if (cacheName) {
            const cache = await caches.open(cacheName);
            const cached = await cache.match(request);
            if (cached) {
                return cached;
            }
        }
        return fetch(request);
    })());
});
