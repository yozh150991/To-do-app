// Workaround for KT-87116 (Kotlin/Wasm 2.4.20+): the bundle keeps a bare `import.meta` in the
// Node-only helper `kotlinx.io.node.getRequire` (kotlinx-io, pulled in by Ktor for supabase-kt).
// index.html loads webApp.js as a classic script, where `import.meta` is a SyntaxError, so the
// whole bundle would fail to parse in every browser. The bare expression (never `import.meta.url`,
// which webpack already replaces) becomes `(void 0)` padded to the same length, so source maps stay
// aligned. That helper only runs under Node, never in a browser. Remove once Kotlin fixes KT-87116.
;(function () {
    const webpack = require('webpack');
    const BARE_IMPORT_META = /import\.meta(?![\w$.])/g;
    const REPLACEMENT = '(void 0)   '; // same length as "import.meta"

    config.plugins = config.plugins || [];
    config.plugins.push({
        apply(compiler) {
            compiler.hooks.thisCompilation.tap('StripBareImportMeta', (compilation) => {
                compilation.hooks.processAssets.tap(
                    { name: 'StripBareImportMeta', stage: webpack.Compilation.PROCESS_ASSETS_STAGE_ANALYSE },
                    (assets) => {
                        for (const name of Object.keys(assets)) {
                            if (!name.endsWith('.js')) continue;
                            const source = assets[name].source().toString();
                            if (source.search(BARE_IMPORT_META) === -1) continue;
                            const patched = source.replace(BARE_IMPORT_META, REPLACEMENT);
                            compilation.updateAsset(name, new webpack.sources.RawSource(patched));
                        }
                    },
                );
            });
        },
    });
})();
